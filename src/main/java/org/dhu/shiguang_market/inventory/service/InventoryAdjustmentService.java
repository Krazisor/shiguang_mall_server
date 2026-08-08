package org.dhu.shiguang_market.inventory.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.InventoryTransactionType;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.inventory.dto.InventoryDtos.InventoryAdjustmentRequest;
import org.dhu.shiguang_market.inventory.dto.InventoryDtos.InventoryTransactionView;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryTransactionMapper;
import org.dhu.shiguang_market.inventory.model.InventoryStock;
import org.dhu.shiguang_market.inventory.model.InventoryTransaction;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.OperatorBrief;
import org.dhu.shiguang_market.product.mapper.ProductSkuMapper;
import org.dhu.shiguang_market.product.model.ProductSku;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** phase-2 库存调整与流水业务。 */
@Service
public class InventoryAdjustmentService {
    private final InventoryStockMapper stockMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final ProductSkuMapper skuMapper;
    private final OrderItemMapper orderItemMapper;
    private final SysUserMapper userMapper;
    private final ShopAccessService shopAccess;
    private final CurrentUserService currentUser;
    private final IdempotencyService idempotency;
    private final NumberGenerator numbers;

    public InventoryAdjustmentService(InventoryStockMapper stockMapper,
                                      InventoryTransactionMapper transactionMapper,
                                      ProductSkuMapper skuMapper, OrderItemMapper orderItemMapper,
                                      SysUserMapper userMapper, ShopAccessService shopAccess,
                                      CurrentUserService currentUser, IdempotencyService idempotency,
                                      NumberGenerator numbers) {
        this.stockMapper = stockMapper;
        this.transactionMapper = transactionMapper;
        this.skuMapper = skuMapper;
        this.orderItemMapper = orderItemMapper;
        this.userMapper = userMapper;
        this.shopAccess = shopAccess;
        this.currentUser = currentUser;
        this.idempotency = idempotency;
        this.numbers = numbers;
    }

    /** 分页查询当前店铺的库存流水，并支持按 SKU、类型、业务号和时间筛选。 */
    public PageView<InventoryTransactionView> transactions(
            long shopId, Long skuId, InventoryTransactionType transactionType,
            String businessType, String businessNo,
            LocalDateTime createdFrom, LocalDateTime createdTo,
            long page, long pageSize) {
        shopAccess.require(shopId, "shop:inventory:manage");
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
        }
        if (createdFrom != null && createdTo != null && !createdFrom.isBefore(createdTo)) {
            throw BusinessException.badRequest("BAD_REQUEST", "createdFrom 必须早于 createdTo");
        }
        Page<InventoryTransaction> result = transactionMapper.selectTransactionPage(
                Page.of(page, pageSize), shopId, skuId,
                transactionType == null ? null : transactionType.name(),
                normalize(businessType), normalize(businessNo), createdFrom, createdTo);
        return PageView.of(result, result.getRecords().stream().map(this::transactionView).toList());
    }

    /**
     * 人工调整库存。幂等校验包裹实际操作，相同请求不会重复写入库存流水。
     */
    @Transactional
    public InventoryTransactionView adjust(long shopId, long skuId,
                                         InventoryAdjustmentRequest request, String key) {
        var shop = shopAccess.require(shopId, "shop:inventory:manage");
        if (shop.getStatus() == ShopStatus.CLOSED) {
            throw BusinessException.conflict("STATE_CONFLICT", "已关闭店铺仅允许读取历史数据");
        }
        validateAdjustmentRequest(request);
        long userId = currentUser.id();
        String path = "/api/shops/" + shopId + "/inventory/" + skuId + "/adjustments";
        return idempotency.execute(userId, "POST", path, key, request,
                InventoryTransactionView.class,
                () -> adjustInventory(shopId, skuId, request, key, userId));
    }

    /** 锁定库存聚合行，完成数量、订单预占和乐观锁校验后写入调整流水。 */
    private InventoryTransactionView adjustInventory(long shopId, long skuId,
                                                   InventoryAdjustmentRequest request,
                                                   String key, long userId) {
        // 必须先校验 SKU 店铺归属，再读取幂等业务流水，避免跨店返回其他 SKU 的调整结果。
        requireScopedSku(shopId, skuId);
        String businessNo = idempotency.businessNo("IA", userId, key);
        InventoryTransaction existing = transactionMapper.selectOne(
                new LambdaQueryWrapper<InventoryTransaction>()
                        .eq(InventoryTransaction::getSkuId, skuId)
                        .eq(InventoryTransaction::getTransactionType, InventoryTransactionType.ADJUST)
                        .eq(InventoryTransaction::getBusinessType, "MANUAL_ADJUSTMENT")
                        .eq(InventoryTransaction::getBusinessNo, businessNo));
        if (existing != null) {
            return transactionView(existing);
        }

        InventoryStock stock = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                .eq(InventoryStock::getSkuId, skuId)
                .last("FOR UPDATE"));
        if (stock == null) {
            throw BusinessException.notFound("INVENTORY_NOT_FOUND", "库存记录不存在");
        }
        if (stock.getVersion() != request.version()) {
            throw BusinessException.conflict("VERSION_CONFLICT", "库存版本已变化");
        }

        int availableAfter;
        int lockedAfter;
        try {
            availableAfter = Math.addExact(stock.getAvailableQuantity(), request.availableChange());
            lockedAfter = Math.addExact(stock.getLockedQuantity(), request.lockedChange());
        } catch (ArithmeticException exception) {
            throw BusinessException.unprocessable("INVENTORY_OPERATION_INVALID", "库存数量溢出");
        }
        if (availableAfter < 0 || lockedAfter < 0) {
            throw BusinessException.unprocessable(
                    "INVENTORY_ADJUSTMENT_NEGATIVE_RESULT", "调整后的库存数量不能为负");
        }
        int reservedQuantity = orderItemMapper.sumLockedQuantityBySkuId(skuId);
        if (lockedAfter < reservedQuantity) {
            throw BusinessException.unprocessable(
                    "INVENTORY_LOCKED_BELOW_RESERVATIONS", "锁定库存不能低于订单预占数量");
        }

        stock.setAvailableQuantity(availableAfter);
        stock.setLockedQuantity(lockedAfter);
        if (stockMapper.updateById(stock) != 1) {
            throw BusinessException.conflict("VERSION_CONFLICT", "库存版本已变化");
        }

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionNo(numbers.next("IT"));
        transaction.setSkuId(skuId);
        transaction.setTransactionType(InventoryTransactionType.ADJUST);
        transaction.setAvailableChange(request.availableChange());
        transaction.setLockedChange(request.lockedChange());
        transaction.setAvailableAfter(availableAfter);
        transaction.setLockedAfter(lockedAfter);
        transaction.setBusinessType("MANUAL_ADJUSTMENT");
        transaction.setBusinessNo(businessNo);
        transaction.setOperatorId(userId);
        transaction.setRemark(request.reason().trim());
        transactionMapper.insert(transaction);
        return transactionView(transactionMapper.selectById(transaction.getId()));
    }

    /** Service 层保留组合校验，保证直接调用时也不会接受无效调整。 */
    private void validateAdjustmentRequest(InventoryAdjustmentRequest request) {
        if (request == null || request.version() < 0
                || (request.availableChange() == 0 && request.lockedChange() == 0)
                || request.reason() == null || request.reason().trim().isEmpty()
                || request.reason().trim().length() > 500) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "库存变化量或调整原因无效");
        }
    }

    /** 校验 SKU 确实属于当前店铺。 */
    private ProductSku requireScopedSku(long shopId, long skuId) {
        ProductSku sku = skuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getId, skuId)
                .eq(ProductSku::getShopId, shopId));
        if (sku == null) {
            throw BusinessException.notFound("RESOURCE_NOT_FOUND", "SKU 不存在");
        }
        return sku;
    }

    /** 将流水实体转换为接口视图，不向接口暴露数据库实体。 */
    private InventoryTransactionView transactionView(InventoryTransaction value) {
        var operator = value.getOperatorId() == null ? null : userMapper.selectById(value.getOperatorId());
        return new InventoryTransactionView(id(value.getId()), value.getTransactionNo(), id(value.getSkuId()),
                value.getTransactionType(), value.getAvailableChange(), value.getLockedChange(),
                value.getAvailableAfter() - value.getAvailableChange(),
                value.getLockedAfter() - value.getLockedChange(),
                value.getAvailableAfter(), value.getLockedAfter(), stockVersion(value),
                value.getBusinessType(), value.getBusinessNo(),
                operator == null ? null : new OperatorBrief(id(operator.getId()), operator.getUsername(),
                        operator.getNickname()), value.getRemark(), time(value.getCreatedAt()));
    }

    private int stockVersion(InventoryTransaction value) {
        InventoryStock stock = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                .eq(InventoryStock::getSkuId, value.getSkuId()));
        return stock == null || stock.getVersion() == null ? 0 : stock.getVersion();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
