package org.dhu.shiguang_market.product.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.identity.service.IdentityViewMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.model.InventoryStock;
import org.dhu.shiguang_market.product.dto.ProductDtos.BrandView;
import org.dhu.shiguang_market.product.dto.ProductDtos.CategoryAttributeView;
import org.dhu.shiguang_market.product.dto.ProductDtos.CategoryBrief;
import org.dhu.shiguang_market.product.dto.ProductDtos.CategoryNode;
import org.dhu.shiguang_market.product.dto.ProductDtos.ProductAttributeDisplayView;
import org.dhu.shiguang_market.product.dto.ProductDtos.ProductCardView;
import org.dhu.shiguang_market.product.dto.ProductDtos.ProductDetailView;
import org.dhu.shiguang_market.product.dto.ProductDtos.PublicShopView;
import org.dhu.shiguang_market.product.dto.ProductDtos.PublicSkuView;
import org.dhu.shiguang_market.product.mapper.ProductAttributeValueMapper;
import org.dhu.shiguang_market.product.mapper.ProductBrandMapper;
import org.dhu.shiguang_market.product.mapper.ProductCategoryAttributeMapper;
import org.dhu.shiguang_market.product.mapper.ProductCategoryMapper;
import org.dhu.shiguang_market.product.mapper.ProductSkuMapper;
import org.dhu.shiguang_market.product.mapper.ProductSpuMapper;
import org.dhu.shiguang_market.product.model.ProductAttributeValue;
import org.dhu.shiguang_market.product.model.ProductBrand;
import org.dhu.shiguang_market.product.model.ProductCategory;
import org.dhu.shiguang_market.product.model.ProductCategoryAttribute;
import org.dhu.shiguang_market.product.model.ProductSku;
import org.dhu.shiguang_market.product.model.ProductSpu;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.springframework.stereotype.Service;

@Service
public class PublicCatalogService {
    private final ProductCategoryMapper categoryMapper;
    private final ProductCategoryAttributeMapper categoryAttributeMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductAttributeValueMapper attributeValueMapper;
    private final InventoryStockMapper stockMapper;
    private final ShopMapper shopMapper;

    public PublicCatalogService(ProductCategoryMapper categoryMapper,
                                ProductCategoryAttributeMapper categoryAttributeMapper,
                                ProductBrandMapper brandMapper, ProductSpuMapper spuMapper,
                                ProductSkuMapper skuMapper, ProductAttributeValueMapper attributeValueMapper,
                                InventoryStockMapper stockMapper, ShopMapper shopMapper) {
        this.categoryMapper = categoryMapper;
        this.categoryAttributeMapper = categoryAttributeMapper;
        this.brandMapper = brandMapper;
        this.spuMapper = spuMapper;
        this.skuMapper = skuMapper;
        this.attributeValueMapper = attributeValueMapper;
        this.stockMapper = stockMapper;
        this.shopMapper = shopMapper;
    }

    public List<CategoryNode> categoryTree() {
        List<ProductCategory> categories = categoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getStatus, EnabledStatus.ENABLED)
                .orderByAsc(ProductCategory::getSortOrder).orderByAsc(ProductCategory::getId));
        Map<Long, List<ProductCategory>> children = categories.stream()
                .filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(ProductCategory::getParentId));
        return categories.stream().filter(category -> category.getParentId() == null)
                .map(category -> categoryNode(category, children)).toList();
    }

    public List<CategoryAttributeView> attributes(long categoryId, boolean publicOnly) {
        LambdaQueryWrapper<ProductCategoryAttribute> query = new LambdaQueryWrapper<ProductCategoryAttribute>()
                .eq(ProductCategoryAttribute::getCategoryId, categoryId)
                .orderByAsc(ProductCategoryAttribute::getSortOrder).orderByAsc(ProductCategoryAttribute::getId);
        if (publicOnly) {
            query.eq(ProductCategoryAttribute::getStatus, EnabledStatus.ENABLED);
        }
        return categoryAttributeMapper.selectList(query).stream().map(this::attributeView).toList();
    }

    public PageView<BrandView> brands(String keyword, EnabledStatus status, long page, long pageSize,
                                      String sort, boolean publicOnly) {
        validatePage(page, pageSize);
        LambdaQueryWrapper<ProductBrand> query = new LambdaQueryWrapper<>();
        if (publicOnly) {
            query.eq(ProductBrand::getStatus, EnabledStatus.ENABLED);
        } else if (status != null) {
            query.eq(ProductBrand::getStatus, status);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            query.and(q -> q.like(ProductBrand::getBrandName, keyword.trim())
                    .or().like(ProductBrand::getBrandCode, keyword.trim()));
        }
        switch (sort == null ? "brandName,asc" : sort) {
            case "brandName,asc" -> query.orderByAsc(ProductBrand::getBrandName);
            case "brandCode,asc" -> query.orderByAsc(ProductBrand::getBrandCode);
            default -> throw BusinessException.badRequest("BAD_REQUEST", "不支持的排序字段");
        }
        query.orderByDesc(ProductBrand::getId);
        Page<ProductBrand> result = brandMapper.selectPage(Page.of(page, pageSize), query);
        return PageView.of(result, result.getRecords().stream().map(this::brandView).toList());
    }

    public PublicShopView publicShop(long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null || shop.getStatus() != ShopStatus.ACTIVE) {
            throw BusinessException.notFound("SHOP_NOT_FOUND", "店铺不存在");
        }
        return new PublicShopView(IdentityViewMapper.shop(shop), shop.getDescription(),
                shop.getContactName(), shop.getContactPhone());
    }

    public PageView<ProductCardView> products(String keyword, Long categoryId, Long brandId, Long shopId,
                                              BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock,
                                              long page, long pageSize, String sort) {
        validatePage(page, pageSize);
        LambdaQueryWrapper<ProductSpu> query = new LambdaQueryWrapper<ProductSpu>()
                .eq(ProductSpu::getStatus, ProductStatus.ON_SHELF)
                .inSql(ProductSpu::getShopId, "SELECT id FROM shop WHERE status = 'ACTIVE'");
        if (keyword != null && !keyword.trim().isEmpty()) {
            query.and(q -> q.like(ProductSpu::getProductName, keyword.trim())
                    .or().like(ProductSpu::getSpuNo, keyword.trim()));
        }
        if (brandId != null) query.eq(ProductSpu::getBrandId, brandId);
        if (shopId != null) query.eq(ProductSpu::getShopId, shopId);
        if (categoryId != null) query.in(ProductSpu::getCategoryId, descendantIds(categoryId));
        StringBuilder skuFilter = new StringBuilder("SELECT ps.spu_id FROM product_sku ps ")
                .append("LEFT JOIN inventory_stock ist ON ist.sku_id = ps.id ")
                .append("WHERE ps.deleted_at IS NULL AND ps.status = 'ENABLED'");
        if (minPrice != null) skuFilter.append(" AND ps.sale_price >= ").append(minPrice.toPlainString());
        if (maxPrice != null) skuFilter.append(" AND ps.sale_price <= ").append(maxPrice.toPlainString());
        skuFilter.append(" GROUP BY ps.spu_id");
        if (Boolean.TRUE.equals(inStock)) skuFilter.append(" HAVING MAX(COALESCE(ist.available_quantity, 0)) > 0");
        if (Boolean.FALSE.equals(inStock)) skuFilter.append(" HAVING MAX(COALESCE(ist.available_quantity, 0)) = 0");
        query.inSql(ProductSpu::getId, skuFilter.toString());
        switch (sort == null ? "createdAt,desc" : sort) {
            case "createdAt,desc" -> query.orderByDesc(ProductSpu::getCreatedAt);
            case "productName,asc" -> query.orderByAsc(ProductSpu::getProductName);
            case "salePrice,asc" -> query.last("ORDER BY (SELECT MIN(sale_price) FROM product_sku WHERE spu_id = product_spu.id AND deleted_at IS NULL AND status = 'ENABLED') ASC, id DESC");
            case "salePrice,desc" -> query.last("ORDER BY (SELECT MIN(sale_price) FROM product_sku WHERE spu_id = product_spu.id AND deleted_at IS NULL AND status = 'ENABLED') DESC, id DESC");
            default -> throw BusinessException.badRequest("BAD_REQUEST", "不支持的排序字段");
        }
        if (!"salePrice,asc".equals(sort) && !"salePrice,desc".equals(sort)) query.orderByDesc(ProductSpu::getId);
        Page<ProductSpu> result = spuMapper.selectPage(Page.of(page, pageSize), query);
        List<ProductCardView> cards = result.getRecords().stream().map(this::cardView)
                .filter(java.util.Objects::nonNull)
                .toList();
        return PageView.of(result, cards);
    }

    public PageView<ProductCardView> couponEligibleProducts(CouponScopeType scopeType,
                                                            List<Long> targetIds,
                                                            Long ownerShopId,
                                                            String keyword,
                                                            long page,
                                                            long pageSize,
                                                            String sort) {
        validatePage(page, pageSize);
        LambdaQueryWrapper<ProductSpu> query = new LambdaQueryWrapper<ProductSpu>()
                .eq(ProductSpu::getStatus, ProductStatus.ON_SHELF)
                .inSql(ProductSpu::getShopId, "SELECT id FROM shop WHERE status = 'ACTIVE'")
                .inSql(ProductSpu::getId, "SELECT spu_id FROM product_sku WHERE deleted_at IS NULL AND status = 'ENABLED'");
        if (keyword != null && !keyword.trim().isEmpty()) {
            query.and(value -> value.like(ProductSpu::getProductName, keyword.trim())
                    .or().like(ProductSpu::getSpuNo, keyword.trim()));
        }
        if (ownerShopId != null) query.eq(ProductSpu::getShopId, ownerShopId);
        switch (scopeType) {
            case ALL -> { }
            case SHOP -> query.in(ProductSpu::getShopId, targetIds);
            case CATEGORY -> {
                Set<Long> categories = new java.util.HashSet<>();
                targetIds.forEach(id -> categories.addAll(descendantIds(id)));
                query.in(ProductSpu::getCategoryId, categories);
            }
            case SPU -> query.in(ProductSpu::getId, targetIds);
            case SKU -> query.inSql(ProductSpu::getId, "SELECT spu_id FROM product_sku WHERE id IN ("
                    + targetIds.stream().map(String::valueOf).collect(Collectors.joining(","))
                    + ") AND deleted_at IS NULL AND status = 'ENABLED'");
        }
        switch (sort == null ? "createdAt,desc" : sort) {
            case "createdAt,desc" -> query.orderByDesc(ProductSpu::getCreatedAt);
            case "productName,asc" -> query.orderByAsc(ProductSpu::getProductName);
            default -> throw BusinessException.badRequest("BAD_REQUEST", "不支持的排序字段");
        }
        query.orderByDesc(ProductSpu::getId);
        Page<ProductSpu> result = spuMapper.selectPage(Page.of(page, pageSize), query);
        return PageView.of(result, result.getRecords().stream().map(this::cardView)
                .filter(java.util.Objects::nonNull).toList());
    }

    public ProductDetailView product(long spuId) {
        ProductSpu spu = spuMapper.selectById(spuId);
        if (spu == null || spu.getStatus() != ProductStatus.ON_SHELF) {
            throw BusinessException.notFound("PRODUCT_NOT_FOUND", "商品不存在");
        }
        Shop shop = shopMapper.selectById(spu.getShopId());
        if (shop == null || shop.getStatus() != ShopStatus.ACTIVE) {
            throw BusinessException.notFound("PRODUCT_NOT_FOUND", "商品不存在");
        }
        ProductCategory category = categoryMapper.selectById(spu.getCategoryId());
        ProductBrand brand = spu.getBrandId() == null ? null : brandMapper.selectById(spu.getBrandId());
        List<PublicSkuView> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                        .eq(ProductSku::getSpuId, spuId).orderByAsc(ProductSku::getId))
                .stream().map(sku -> publicSku(sku, shop)).toList();
        List<ProductAttributeDisplayView> attributes = attributeValueMapper.selectList(
                        new LambdaQueryWrapper<ProductAttributeValue>()
                                .eq(ProductAttributeValue::getSpuId, spuId))
                .stream().map(value -> attributeDisplay(value)).toList();
        return new ProductDetailView(id(spu.getId()), spu.getSpuNo(), spu.getProductName(), spu.getSubtitle(),
                spu.getCoverUrl(), spu.getGalleryJson() == null ? List.of() : spu.getGalleryJson(),
                spu.getDetailHtml(), spu.getPackingList(), spu.getServiceNote(), IdentityViewMapper.shop(shop),
                new CategoryBrief(id(category.getId()), category.getCategoryCode(), category.getCategoryName()),
                brand == null ? null : brandView(brand), attributes, skus);
    }

    public PublicSkuView publicSku(ProductSku sku, Shop shop) {
        InventoryStock stock = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                .eq(InventoryStock::getSkuId, sku.getId()));
        int available = stock == null ? 0 : stock.getAvailableQuantity();
        boolean purchasable = shop.getStatus() == ShopStatus.ACTIVE
                && sku.getStatus() == EnabledStatus.ENABLED && available > 0;
        String reason = purchasable ? null : sku.getStatus() != EnabledStatus.ENABLED
                ? "SKU_DISABLED" : available <= 0 ? "OUT_OF_STOCK" : "SHOP_UNAVAILABLE";
        return new PublicSkuView(id(sku.getId()), sku.getSkuNo(), sku.getSkuName(), sku.getSpecJson(),
                money(sku.getSalePrice()), money(sku.getMarketPrice()), sku.getImageUrl(), sku.getStatus(),
                available, available > 0, purchasable, reason);
    }

    private ProductCardView cardView(ProductSpu spu) {
        Shop shop = shopMapper.selectById(spu.getShopId());
        if (shop == null || shop.getStatus() != ShopStatus.ACTIVE) return null;
        List<ProductSku> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSpuId, spu.getId()).eq(ProductSku::getStatus, EnabledStatus.ENABLED));
        if (skus.isEmpty()) return null;
        BigDecimal min = skus.stream().map(ProductSku::getSalePrice).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal max = skus.stream().map(ProductSku::getSalePrice).max(BigDecimal::compareTo).orElseThrow();
        boolean hasStock = skus.stream().anyMatch(sku -> {
            InventoryStock stock = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                    .eq(InventoryStock::getSkuId, sku.getId()));
            return stock != null && stock.getAvailableQuantity() > 0;
        });
        ProductBrand brand = spu.getBrandId() == null ? null : brandMapper.selectById(spu.getBrandId());
        return new ProductCardView(id(spu.getId()), spu.getSpuNo(), spu.getProductName(), spu.getSubtitle(),
                spu.getCoverUrl(), IdentityViewMapper.shop(shop), id(spu.getCategoryId()),
                brand == null ? null : brandView(brand), money(min), money(max), hasStock);
    }

    private Comparator<ProductCardView> priceComparator(String sort) {
        if ("salePrice,asc".equals(sort)) {
            return Comparator.comparing(card -> new BigDecimal(card.minimumSalePrice()));
        }
        if ("salePrice,desc".equals(sort)) {
            return Comparator.comparing((ProductCardView card) -> new BigDecimal(card.minimumSalePrice())).reversed();
        }
        return (left, right) -> 0;
    }

    private ProductAttributeDisplayView attributeDisplay(ProductAttributeValue value) {
        ProductCategoryAttribute attribute = categoryAttributeMapper.selectById(value.getAttributeId());
        return new ProductAttributeDisplayView(id(value.getAttributeId()), attribute.getAttributeName(),
                value.getAttributeValue(), attribute.getUnit());
    }

    private CategoryNode categoryNode(ProductCategory category, Map<Long, List<ProductCategory>> children) {
        List<CategoryNode> nodes = children.getOrDefault(category.getId(), List.of()).stream()
                .map(child -> categoryNode(child, children)).toList();
        return new CategoryNode(id(category.getId()), id(category.getParentId()), category.getCategoryCode(),
                category.getCategoryName(), category.getSortOrder(), nodes.isEmpty(), nodes);
    }

    private CategoryAttributeView attributeView(ProductCategoryAttribute attribute) {
        return new CategoryAttributeView(id(attribute.getId()), id(attribute.getCategoryId()),
                attribute.getAttributeName(), attribute.getValueType(), attribute.getUnit(),
                Boolean.TRUE.equals(attribute.getIsRequired()), Boolean.TRUE.equals(attribute.getIsFilterable()),
                attribute.getOptionsJson(), attribute.getSortOrder(), attribute.getStatus());
    }

    private BrandView brandView(ProductBrand brand) {
        return new BrandView(id(brand.getId()), brand.getBrandCode(), brand.getBrandName(),
                brand.getLogoUrl(), brand.getStatus());
    }

    private Set<Long> descendantIds(long rootId) {
        List<ProductCategory> enabled = categoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getStatus, EnabledStatus.ENABLED));
        Set<Long> result = new java.util.HashSet<>();
        result.add(rootId);
        boolean changed;
        do {
            changed = false;
            for (ProductCategory category : enabled) {
                if (category.getParentId() != null && result.contains(category.getParentId())) {
                    changed |= result.add(category.getId());
                }
            }
        } while (changed);
        return result;
    }

    private void validatePage(long page, long pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
        }
    }
}
