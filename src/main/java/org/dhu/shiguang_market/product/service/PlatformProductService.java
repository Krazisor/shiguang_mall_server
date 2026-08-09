package org.dhu.shiguang_market.product.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.service.IdentityViewMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.model.InventoryStock;
import org.dhu.shiguang_market.product.dto.ProductDtos.BrandView;
import org.dhu.shiguang_market.product.dto.ProductDtos.CategoryBrief;
import org.dhu.shiguang_market.product.dto.ProductDtos.ProductAttributeDisplayView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.OperatorBrief;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.PlatformProductDetailView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.PlatformProductSummaryView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.PlatformSkuView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ProductStatusHistoryView;
import org.dhu.shiguang_market.product.mapper.ProductAttributeValueMapper;
import org.dhu.shiguang_market.product.mapper.ProductBrandMapper;
import org.dhu.shiguang_market.product.mapper.ProductCategoryAttributeMapper;
import org.dhu.shiguang_market.product.mapper.ProductCategoryMapper;
import org.dhu.shiguang_market.product.mapper.ProductSkuMapper;
import org.dhu.shiguang_market.product.mapper.ProductSpuMapper;
import org.dhu.shiguang_market.product.mapper.ProductStatusHistoryMapper;
import org.dhu.shiguang_market.product.model.ProductAttributeValue;
import org.dhu.shiguang_market.product.model.ProductBrand;
import org.dhu.shiguang_market.product.model.ProductCategory;
import org.dhu.shiguang_market.product.model.ProductCategoryAttribute;
import org.dhu.shiguang_market.product.model.ProductSku;
import org.dhu.shiguang_market.product.model.ProductSpu;
import org.dhu.shiguang_market.product.model.ProductStatusHistory;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.springframework.stereotype.Service;

@Service
public class PlatformProductService {
    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductCategoryAttributeMapper attributeMapper;
    private final ProductAttributeValueMapper valueMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductStatusHistoryMapper historyMapper;
    private final ShopMapper shopMapper;
    private final SysUserMapper userMapper;
    private final InventoryStockMapper stockMapper;
    private final CurrentUserService currentUser;

    public PlatformProductService(ProductSpuMapper spuMapper, ProductSkuMapper skuMapper,
                                  ProductCategoryMapper categoryMapper,
                                  ProductCategoryAttributeMapper attributeMapper,
                                  ProductAttributeValueMapper valueMapper, ProductBrandMapper brandMapper,
                                  ProductStatusHistoryMapper historyMapper, ShopMapper shopMapper,
                                  SysUserMapper userMapper, InventoryStockMapper stockMapper,
                                  CurrentUserService currentUser) {
        this.spuMapper = spuMapper;
        this.skuMapper = skuMapper;
        this.categoryMapper = categoryMapper;
        this.attributeMapper = attributeMapper;
        this.valueMapper = valueMapper;
        this.brandMapper = brandMapper;
        this.historyMapper = historyMapper;
        this.shopMapper = shopMapper;
        this.userMapper = userMapper;
        this.stockMapper = stockMapper;
        this.currentUser = currentUser;
    }

    public PageView<PlatformProductSummaryView> list(ProductStatus status, Long shopId, ShopStatus shopStatus,
                                                      Long categoryId, Long brandId, String keyword,
                                                      long page, long pageSize, String sort) {
        currentUser.requirePermission("platform:product:read");
        validatePage(page, pageSize);
        LambdaQueryWrapper<ProductSpu> query = new LambdaQueryWrapper<>();
        if (status != null) query.eq(ProductSpu::getStatus, status);
        if (shopId != null) query.eq(ProductSpu::getShopId, shopId);
        if (shopStatus != null) {
            query.inSql(ProductSpu::getShopId, "SELECT id FROM shop WHERE status = '" + shopStatus.name() + "'");
        }
        if (categoryId != null) query.in(ProductSpu::getCategoryId, descendantCategoryIds(categoryId));
        if (brandId != null) query.eq(ProductSpu::getBrandId, brandId);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String value = keyword.trim();
            query.and(q -> q.like(ProductSpu::getProductName, value).or().like(ProductSpu::getSpuNo, value));
        }
        switch (sort == null ? "updatedAt,desc" : sort) {
            case "updatedAt,desc" -> query.orderByDesc(ProductSpu::getUpdatedAt);
            case "createdAt,desc" -> query.orderByDesc(ProductSpu::getCreatedAt);
            case "productName,asc" -> query.orderByAsc(ProductSpu::getProductName);
            case "status,asc" -> query.orderByAsc(ProductSpu::getStatus);
            default -> throw BusinessException.badRequest("BAD_REQUEST", "不支持的排序字段");
        }
        query.orderByDesc(ProductSpu::getId);
        Page<ProductSpu> result = spuMapper.selectPage(Page.of(page, pageSize), query);
        return PageView.of(result, result.getRecords().stream().map(this::summary).toList());
    }

    public PlatformProductDetailView detail(long spuId) {
        currentUser.requirePermission("platform:product:read");
        ProductSpu spu = requireSpu(spuId);
        return detailView(spu);
    }

    public PageView<ProductStatusHistoryView> history(long spuId, long page, long pageSize) {
        currentUser.requirePermission("platform:product:read");
        validatePage(page, pageSize);
        requireSpu(spuId);
        Page<ProductStatusHistory> result = historyMapper.selectPage(Page.of(page, pageSize),
                new LambdaQueryWrapper<ProductStatusHistory>().eq(ProductStatusHistory::getSpuId, spuId)
                        .orderByDesc(ProductStatusHistory::getCreatedAt).orderByDesc(ProductStatusHistory::getId));
        return PageView.of(result, result.getRecords().stream().map(this::historyView).toList());
    }

    private PlatformProductSummaryView summary(ProductSpu spu) {
        Shop shop = requireShop(spu.getShopId());
        ProductCategory category = requireCategory(spu.getCategoryId());
        ProductBrand brand = spu.getBrandId() == null ? null : brandMapper.selectById(spu.getBrandId());
        List<ProductSku> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSpuId, spu.getId()));
        int available = 0;
        int locked = 0;
        int enabled = 0;
        for (ProductSku sku : skus) {
            if (sku.getStatus() == EnabledStatus.ENABLED) enabled++;
            InventoryStock stock = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                    .eq(InventoryStock::getSkuId, sku.getId()));
            if (stock != null) {
                available += stock.getAvailableQuantity();
                locked += stock.getLockedQuantity();
            }
        }
        return new PlatformProductSummaryView(id(spu.getId()), spu.getSpuNo(), spu.getProductName(), spu.getCoverUrl(),
                IdentityViewMapper.shop(shop), categoryBrief(category), brandView(brand), spu.getStatus(),
                spu.getContentVersion(), skus.size(), enabled, available, locked, time(spu.getCreatedAt()), time(spu.getUpdatedAt()));
    }

    private PlatformProductDetailView detailView(ProductSpu spu) {
        Shop shop = requireShop(spu.getShopId());
        ProductCategory category = requireCategory(spu.getCategoryId());
        ProductBrand brand = spu.getBrandId() == null ? null : brandMapper.selectById(spu.getBrandId());
        List<ProductAttributeDisplayView> attributes = valueMapper.selectList(new LambdaQueryWrapper<ProductAttributeValue>()
                .eq(ProductAttributeValue::getSpuId, spu.getId())).stream().map(this::attributeView).toList();
        List<PlatformSkuView> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSpuId, spu.getId()).orderByAsc(ProductSku::getId)).stream().map(this::skuView).toList();
        return new PlatformProductDetailView(id(spu.getId()), spu.getSpuNo(), spu.getProductName(), spu.getSubtitle(),
                spu.getCoverUrl(), spu.getGalleryJson() == null ? List.of() : spu.getGalleryJson(), spu.getDetailHtml(),
                spu.getPackingList(), spu.getServiceNote(), IdentityViewMapper.shop(shop), categoryBrief(category),
                brandView(brand), attributes, skus, spu.getStatus(), spu.getContentVersion(),
                IdentityViewMapper.user(userMapper.selectById(spu.getCreatedBy())),
                IdentityViewMapper.user(userMapper.selectById(spu.getUpdatedBy())), time(spu.getCreatedAt()), time(spu.getUpdatedAt()));
    }

    private PlatformSkuView skuView(ProductSku sku) {
        InventoryStock stock = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                .eq(InventoryStock::getSkuId, sku.getId()));
        int available = stock == null ? 0 : stock.getAvailableQuantity();
        int locked = stock == null ? 0 : stock.getLockedQuantity();
        return new PlatformSkuView(id(sku.getId()), sku.getSkuNo(), sku.getSkuName(), sku.getSpecJson(),
                money(sku.getSalePrice()), money(sku.getMarketPrice()), sku.getBarcode(), sku.getImageUrl(),
                sku.getStatus(), sku.getVersion(), available, locked, time(sku.getCreatedAt()), time(sku.getUpdatedAt()));
    }

    private ProductAttributeDisplayView attributeView(ProductAttributeValue value) {
        ProductCategoryAttribute attribute = attributeMapper.selectById(value.getAttributeId());
        return new ProductAttributeDisplayView(id(value.getAttributeId()), attribute == null ? null : attribute.getAttributeName(),
                value.getAttributeValue(), attribute == null ? null : attribute.getUnit());
    }

    private ProductStatusHistoryView historyView(ProductStatusHistory value) {
        var operator = value.getOperatorId() == null ? null : userMapper.selectById(value.getOperatorId());
        OperatorBrief brief = operator == null ? null : new OperatorBrief(id(operator.getId()), operator.getUsername(), operator.getNickname());
        return new ProductStatusHistoryView(id(value.getId()), id(value.getSpuId()), value.getFromStatus(), value.getToStatus(),
                value.getOperationType(), value.getContentVersion(), value.getOperatorType(), brief, value.getReason(), time(value.getCreatedAt()));
    }

    private ProductSpu requireSpu(long spuId) {
        ProductSpu spu = spuMapper.selectById(spuId);
        if (spu == null) throw BusinessException.notFound("PRODUCT_NOT_FOUND", "商品不存在");
        requireShop(spu.getShopId());
        return spu;
    }

    private Shop requireShop(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) throw BusinessException.notFound("PRODUCT_NOT_FOUND", "商品不存在");
        return shop;
    }

    private ProductCategory requireCategory(Long categoryId) {
        ProductCategory category = categoryMapper.selectById(categoryId);
        if (category == null) throw BusinessException.notFound("PRODUCT_NOT_FOUND", "商品不存在");
        return category;
    }

    private CategoryBrief categoryBrief(ProductCategory category) {
        return new CategoryBrief(id(category.getId()), category.getCategoryCode(), category.getCategoryName());
    }

    private BrandView brandView(ProductBrand brand) {
        return brand == null ? null : new BrandView(id(brand.getId()), brand.getBrandCode(), brand.getBrandName(), brand.getLogoUrl(), brand.getStatus());
    }

    private List<Long> descendantCategoryIds(long categoryId) {
        List<ProductCategory> all = categoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>());
        Set<Long> ids = new HashSet<>();
        if (all.stream().noneMatch(category -> category.getId().equals(categoryId))) return List.of(-1L);
        ids.add(categoryId);
        boolean changed;
        do {
            changed = false;
            for (ProductCategory category : all) {
                if (category.getParentId() != null && ids.contains(category.getParentId()) && ids.add(category.getId())) changed = true;
            }
        } while (changed);
        return new ArrayList<>(ids);
    }

    private void validatePage(long page, long pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
    }
}
