package org.dhu.shiguang_market.inventory.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.InventoryTransactionType;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.inventory.dto.InventoryDtos.InventoryInboundRequest;
import org.dhu.shiguang_market.inventory.dto.InventoryDtos.InventoryItemView;
import org.dhu.shiguang_market.inventory.dto.InventoryDtos.InventoryOperationView;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryTransactionMapper;
import org.dhu.shiguang_market.inventory.model.InventoryStock;
import org.dhu.shiguang_market.inventory.model.InventoryTransaction;
import org.dhu.shiguang_market.product.mapper.ProductSkuMapper;
import org.dhu.shiguang_market.product.mapper.ProductSpuMapper;
import org.dhu.shiguang_market.product.model.ProductSku;
import org.dhu.shiguang_market.product.model.ProductSpu;
import org.dhu.shiguang_market.product.service.ShopProductService;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final InventoryStockMapper stockMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductSpuMapper spuMapper;
    private final ShopProductService productService;
    private final ShopAccessService shopAccess;
    private final CurrentUserService currentUser;
    private final IdempotencyService idempotency;
    private final NumberGenerator numbers;

    public InventoryService(InventoryStockMapper stockMapper, InventoryTransactionMapper transactionMapper,
                            ProductSkuMapper skuMapper, ProductSpuMapper spuMapper,
                            ShopProductService productService, ShopAccessService shopAccess,
                            CurrentUserService currentUser, IdempotencyService idempotency,
                            NumberGenerator numbers) {
        this.stockMapper = stockMapper;
        this.transactionMapper = transactionMapper;
        this.skuMapper = skuMapper;
        this.spuMapper = spuMapper;
        this.productService = productService;
        this.shopAccess = shopAccess;
        this.currentUser = currentUser;
        this.idempotency = idempotency;
        this.numbers = numbers;
    }

    public PageView<InventoryItemView> list(long shopId, String keyword, Long spuId,
                                            String stockState, long page, long pageSize) {
        shopAccess.require(shopId, "shop:inventory:manage");
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
        }
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String normalizedStockState = normalizeStockState(stockState);
        Page<ProductSku> result = skuMapper.selectInventoryPage(
                Page.of(page, pageSize), shopId, spuId, normalizedKeyword, normalizedStockState);
        var items = result.getRecords().stream().map(this::item).toList();
        return PageView.of(result, items);
    }

    public InventoryItemView detail(long shopId, long skuId) {
        shopAccess.require(shopId, "shop:inventory:manage");
        ProductSku sku = scopedSku(shopId, skuId);
        return item(sku);
    }

    @Transactional
    public InventoryOperationView inbound(long shopId, long skuId, InventoryInboundRequest request, String key) {
        var shop = shopAccess.require(shopId, "shop:inventory:manage");
        if (shop.getStatus() == ShopStatus.CLOSED) {
            throw BusinessException.conflict("STATE_CONFLICT", "已关闭店铺仅允许读取历史数据");
        }
        long userId = currentUser.id();
        String path = "/api/shops/" + shopId + "/inventory/" + skuId + "/inbounds";
        return idempotency.execute(userId, "POST", path, key, request,
                InventoryOperationView.class, () -> inboundInventory(shopId, skuId, request, key, userId));
    }

    private InventoryOperationView inboundInventory(long shopId, long skuId, InventoryInboundRequest request,
                                                      String key, long userId) {
        String businessNo = idempotency.businessNo("II", userId, key);
        InventoryTransaction existing = transactionMapper.selectOne(new LambdaQueryWrapper<InventoryTransaction>()
                .eq(InventoryTransaction::getBusinessType, "MANUAL_INBOUND")
                .eq(InventoryTransaction::getBusinessNo, businessNo));
        if (existing != null) return operation(existing);
        scopedSku(shopId, skuId);
        InventoryStock stock = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                .eq(InventoryStock::getSkuId, skuId).last("FOR UPDATE"));
        if (stock == null) throw BusinessException.notFound("INVENTORY_NOT_FOUND", "库存记录不存在");
        try {
            stock.setAvailableQuantity(Math.addExact(stock.getAvailableQuantity(), request.quantity()));
        } catch (ArithmeticException ex) {
            throw BusinessException.unprocessable("INVENTORY_OPERATION_INVALID", "库存数量溢出");
        }
        if (stockMapper.updateById(stock) != 1) {
            throw BusinessException.conflict("VERSION_CONFLICT", "库存版本已变化");
        }
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionNo(numbers.next("IT"));
        transaction.setSkuId(skuId);
        transaction.setTransactionType(InventoryTransactionType.INBOUND);
        transaction.setAvailableChange(request.quantity());
        transaction.setLockedChange(0);
        transaction.setAvailableAfter(stock.getAvailableQuantity());
        transaction.setLockedAfter(stock.getLockedQuantity());
        transaction.setBusinessType("MANUAL_INBOUND");
        transaction.setBusinessNo(businessNo);
        transaction.setOperatorId(userId);
        transaction.setRemark(request.remark());
        transactionMapper.insert(transaction);
        return operation(transactionMapper.selectById(transaction.getId()));
    }

    private InventoryItemView item(ProductSku sku) {
        ProductSpu spu = spuMapper.selectById(sku.getSpuId());
        return new InventoryItemView(id(spu.getId()), spu.getSpuNo(), spu.getProductName(), productService.skuView(sku));
    }

    private ProductSku scopedSku(long shopId, long skuId) {
        ProductSku sku = skuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getId, skuId).eq(ProductSku::getShopId, shopId));
        if (sku == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "SKU 不存在");
        return sku;
    }

    private String normalizeStockState(String state) {
        if (state == null || state.isBlank()) return null;
        String normalized = state.trim();
        if (!normalized.equals("OUT_OF_STOCK")
                && !normalized.equals("LOW_STOCK")
                && !normalized.equals("IN_STOCK")) {
            throw BusinessException.badRequest("BAD_REQUEST", "stockState 无效");
        }
        return normalized;
    }

    private InventoryOperationView operation(InventoryTransaction value) {
        return new InventoryOperationView(value.getTransactionNo(), id(value.getSkuId()),
                value.getTransactionType(), value.getAvailableChange(), value.getLockedChange(),
                value.getAvailableAfter(), value.getLockedAfter(), value.getBusinessType(),
                value.getBusinessNo(), value.getRemark(), time(value.getCreatedAt()));
    }
}
