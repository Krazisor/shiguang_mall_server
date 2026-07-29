package org.dhu.shiguang_market.product.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.AttributeValueType;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductOperationType;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.util.ContentSafety;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.common.util.SpecNormalizer;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.identity.service.IdentityViewMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.model.InventoryStock;
import org.dhu.shiguang_market.product.dto.ProductDtos.BrandView;
import org.dhu.shiguang_market.product.dto.ProductDtos.CategoryBrief;
import org.dhu.shiguang_market.product.dto.ProductDtos.ProductAttributeDisplayView;
import org.dhu.shiguang_market.product.dto.ProductDtos.ProductDetailView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.CreateProductRequest;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.CreateSkuRequest;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.OperatorBrief;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ProductAttributeInput;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ProductStatusHistoryView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ReasonRequest;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ShopProductDetailView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ShopProductSummaryView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ShopSkuView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.SkuCreateInput;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.StockView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.UpdateProductContentRequest;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.UpdateSkuRequest;
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
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopProductService {
    private static final Set<ProductStatus> EDITABLE = Set.of(
            ProductStatus.DRAFT, ProductStatus.REJECTED, ProductStatus.OFF_SHELF);
    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductCategoryAttributeMapper templateMapper;
    private final ProductAttributeValueMapper valueMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductStatusHistoryMapper historyMapper;
    private final InventoryStockMapper stockMapper;
    private final ShopMapper shopMapper;
    private final SysUserMapper userMapper;
    private final ShopAccessService shopAccess;
    private final CurrentUserService currentUser;
    private final NumberGenerator numbers;
    private final ContentSafety contentSafety;

    public ShopProductService(ProductSpuMapper spuMapper, ProductSkuMapper skuMapper,
                              ProductCategoryMapper categoryMapper,
                              ProductCategoryAttributeMapper templateMapper,
                              ProductAttributeValueMapper valueMapper, ProductBrandMapper brandMapper,
                              ProductStatusHistoryMapper historyMapper, InventoryStockMapper stockMapper,
                              ShopMapper shopMapper, SysUserMapper userMapper,
                              ShopAccessService shopAccess, CurrentUserService currentUser,
                              NumberGenerator numbers, ContentSafety contentSafety) {
        this.spuMapper = spuMapper;
        this.skuMapper = skuMapper;
        this.categoryMapper = categoryMapper;
        this.templateMapper = templateMapper;
        this.valueMapper = valueMapper;
        this.brandMapper = brandMapper;
        this.historyMapper = historyMapper;
        this.stockMapper = stockMapper;
        this.shopMapper = shopMapper;
        this.userMapper = userMapper;
        this.shopAccess = shopAccess;
        this.currentUser = currentUser;
        this.numbers = numbers;
        this.contentSafety = contentSafety;
    }

    public PageView<ShopProductSummaryView> list(long shopId, ProductStatus status, String keyword,
                                                 Long categoryId, long page, long pageSize, String sort) {
        shopAccess.require(shopId, "shop:product:manage");
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
        }
        LambdaQueryWrapper<ProductSpu> query = new LambdaQueryWrapper<ProductSpu>()
                .eq(ProductSpu::getShopId, shopId);
        if (status != null) query.eq(ProductSpu::getStatus, status);
        if (categoryId != null) query.eq(ProductSpu::getCategoryId, categoryId);
        if (keyword != null && !keyword.trim().isEmpty()) query.and(q -> q
                .like(ProductSpu::getProductName, keyword.trim()).or().like(ProductSpu::getSpuNo, keyword.trim()));
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

    @Transactional
    public ShopProductDetailView create(long shopId, CreateProductRequest request) {
        Shop shop = shopAccess.require(shopId, "shop:product:manage");
        requireWritableShop(shop);
        long userId = currentUser.id();
        validateCatalog(parseId(request.categoryId()), parseNullableId(request.brandId()), request.attributes());
        ProductSpu spu = new ProductSpu();
        spu.setShopId(shopId);
        spu.setSpuNo(numbers.next("SPU"));
        applyContent(spu, request.categoryId(), request.brandId(), request.productName(), request.subtitle(),
                request.coverUrl(), request.galleryUrls(), request.detailHtml(), request.packingList(), request.serviceNote());
        spu.setStatus(ProductStatus.DRAFT);
        spu.setContentVersion(1);
        spu.setCreatedBy(userId);
        spu.setUpdatedBy(userId);
        spuMapper.insert(spu);
        replaceAttributes(spu, request.attributes());
        for (SkuCreateInput input : request.skus()) createSkuEntity(spu, input);
        historyMapper.insert(history(spu, null, ProductStatus.DRAFT, ProductOperationType.CREATE, null));
        return detailInternal(spuMapper.selectById(spu.getId()));
    }

    public ShopProductDetailView detail(long shopId, long spuId) {
        shopAccess.require(shopId, "shop:product:manage");
        return detailInternal(scoped(shopId, spuId));
    }

    @Transactional
    public ShopProductDetailView updateContent(long shopId, long spuId, UpdateProductContentRequest request) {
        requireWritableShop(shopAccess.require(shopId, "shop:product:manage"));
        ProductSpu spu = scoped(shopId, spuId, true);
        requireEditable(spu);
        if (spu.getContentVersion() != request.contentVersion()) {
            throw BusinessException.conflict("VERSION_CONFLICT", "商品内容版本已变化");
        }
        validateCatalog(parseId(request.categoryId()), parseNullableId(request.brandId()), request.attributes());
        for (var content : request.skuContents()) {
            ProductSku sku = scopedSku(shopId, spuId, parseId(content.skuId()));
            if (sku.getVersion() != content.version()) {
                throw BusinessException.conflict("VERSION_CONFLICT", "SKU 版本已变化");
            }
            sku.setSkuName(content.skuName().trim());
            sku.setImageUrl(contentSafety.imageUrl("imageUrl", content.imageUrl()));
            if (skuMapper.updateById(sku) != 1) {
                throw BusinessException.conflict("VERSION_CONFLICT", "SKU 版本已变化");
            }
        }
        ProductStatus from = spu.getStatus();
        applyContent(spu, request.categoryId(), request.brandId(), request.productName(), request.subtitle(),
                request.coverUrl(), request.galleryUrls(), request.detailHtml(), request.packingList(), request.serviceNote());
        spu.setContentVersion(spu.getContentVersion() + 1);
        spu.setStatus(ProductStatus.DRAFT);
        spu.setUpdatedBy(currentUser.id());
        spuMapper.updateById(spu);
        replaceAttributes(spu, request.attributes());
        historyMapper.insert(history(spu, from, ProductStatus.DRAFT, ProductOperationType.CONTENT_CHANGED, null));
        return detailInternal(spuMapper.selectById(spuId));
    }

    @Transactional
    public ShopProductDetailView createSku(long shopId, long spuId, CreateSkuRequest request) {
        requireWritableShop(shopAccess.require(shopId, "shop:product:manage"));
        ProductSpu spu = scoped(shopId, spuId, true);
        requireEditable(spu);
        if (spu.getContentVersion() != request.contentVersion()) {
            throw BusinessException.conflict("VERSION_CONFLICT", "商品内容版本已变化");
        }
        ProductStatus from = spu.getStatus();
        createSkuEntity(spu, request.sku());
        spu.setContentVersion(spu.getContentVersion() + 1);
        spu.setStatus(ProductStatus.DRAFT);
        spu.setUpdatedBy(currentUser.id());
        spuMapper.updateById(spu);
        historyMapper.insert(history(spu, from, ProductStatus.DRAFT, ProductOperationType.CONTENT_CHANGED, null));
        return detailInternal(spuMapper.selectById(spuId));
    }

    @Transactional
    public ShopSkuView updateSku(long shopId, long spuId, long skuId, UpdateSkuRequest request) {
        requireWritableShop(shopAccess.require(shopId, "shop:product:manage"));
        ProductSku sku = scopedSku(shopId, spuId, skuId);
        if (sku.getVersion() != request.version()) {
            throw BusinessException.conflict("VERSION_CONFLICT", "SKU 版本已变化");
        }
        if (!request.hasSalePrice() && !request.hasMarketPrice()
                && !request.hasBarcode() && !request.hasStatus()) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "至少提交一个 SKU 业务字段");
        }
        if (request.hasSalePrice()) {
            if (request.salePrice() == null) throw BusinessException.badRequest("VALIDATION_FAILED", "salePrice 不允许为 null");
            sku.setSalePrice(parseMoney(request.salePrice()));
        }
        if (request.hasMarketPrice()) sku.setMarketPrice(request.marketPrice() == null ? null : parseMoney(request.marketPrice()));
        if (request.hasBarcode()) sku.setBarcode(request.barcode() == null || request.barcode().trim().isEmpty() ? null : request.barcode().trim());
        if (request.hasStatus()) {
            if (request.status() == null) throw BusinessException.badRequest("VALIDATION_FAILED", "status 不允许为 null");
            sku.setStatus(request.status());
        }
        validatePrice(sku.getSalePrice(), sku.getMarketPrice());
        if (skuMapper.updateById(sku) != 1) {
            throw BusinessException.conflict("VERSION_CONFLICT", "SKU 版本已变化");
        }
        return skuView(skuMapper.selectById(skuId));
    }

    @Transactional
    public ShopProductDetailView submitReview(long shopId, long spuId) {
        Shop shop = shopAccess.require(shopId, "shop:product:manage");
        ProductSpu spu = scoped(shopId, spuId, true);
        if (spu.getStatus() != ProductStatus.DRAFT || shop.getStatus() == ShopStatus.SUSPENDED
                || shop.getStatus() == ShopStatus.CLOSED) {
            throw BusinessException.conflict("PRODUCT_NOT_EDITABLE", "商品当前不可提交审核");
        }
        validateReady(spu);
        transition(spu, ProductStatus.PENDING_REVIEW, ProductOperationType.SUBMIT_REVIEW, null);
        return detailInternal(spuMapper.selectById(spuId));
    }

    @Transactional
    public ShopProductDetailView putOnShelf(long shopId, long spuId) {
        Shop shop = shopAccess.require(shopId, "shop:product:manage");
        ProductSpu spu = scoped(shopId, spuId, true);
        if (spu.getStatus() != ProductStatus.OFF_SHELF || shop.getStatus() != ShopStatus.ACTIVE) {
            throw BusinessException.conflict("PRODUCT_NOT_PURCHASABLE", "商品当前不可上架");
        }
        boolean hasStock = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                        .eq(ProductSku::getSpuId, spuId).eq(ProductSku::getStatus, EnabledStatus.ENABLED))
                .stream().anyMatch(sku -> {
                    InventoryStock stock = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                            .eq(InventoryStock::getSkuId, sku.getId()));
                    return stock != null && stock.getAvailableQuantity() > 0;
                });
        if (!hasStock) throw BusinessException.unprocessable("INVENTORY_INSUFFICIENT", "没有可售库存");
        transition(spu, ProductStatus.ON_SHELF, ProductOperationType.PUT_ON_SHELF, null);
        return detailInternal(spuMapper.selectById(spuId));
    }

    @Transactional
    public ShopProductDetailView takeOffShelf(long shopId, long spuId, ReasonRequest request) {
        shopAccess.require(shopId, "shop:product:manage");
        ProductSpu spu = scoped(shopId, spuId, true);
        if (spu.getStatus() != ProductStatus.ON_SHELF) {
            throw BusinessException.conflict("STATE_CONFLICT", "商品当前不可下架");
        }
        transition(spu, ProductStatus.OFF_SHELF, ProductOperationType.TAKE_OFF_SHELF,
                request == null ? null : request.reason());
        return detailInternal(spuMapper.selectById(spuId));
    }

    public ShopProductDetailView detailInternal(ProductSpu spu) {
        Shop shop = shopMapper.selectById(spu.getShopId());
        ProductCategory category = categoryMapper.selectById(spu.getCategoryId());
        ProductBrand brand = spu.getBrandId() == null ? null : brandMapper.selectById(spu.getBrandId());
        List<ProductAttributeDisplayView> attributes = valueMapper.selectList(
                        new LambdaQueryWrapper<ProductAttributeValue>().eq(ProductAttributeValue::getSpuId, spu.getId()))
                .stream().map(value -> {
                    ProductCategoryAttribute template = templateMapper.selectById(value.getAttributeId());
                    return new ProductAttributeDisplayView(id(value.getAttributeId()), template.getAttributeName(),
                            value.getAttributeValue(), template.getUnit());
                }).toList();
        ProductDetailView product = new ProductDetailView(id(spu.getId()), spu.getSpuNo(), spu.getProductName(),
                spu.getSubtitle(), spu.getCoverUrl(), spu.getGalleryJson() == null ? List.of() : spu.getGalleryJson(),
                spu.getDetailHtml(), spu.getPackingList(), spu.getServiceNote(), IdentityViewMapper.shop(shop),
                new CategoryBrief(id(category.getId()), category.getCategoryCode(), category.getCategoryName()),
                brand == null ? null : brandView(brand), attributes, List.of());
        List<ShopSkuView> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                        .eq(ProductSku::getSpuId, spu.getId()).orderByAsc(ProductSku::getId))
                .stream().map(this::skuView).toList();
        List<ProductStatusHistoryView> history = historyMapper.selectList(
                        new LambdaQueryWrapper<ProductStatusHistory>()
                                .eq(ProductStatusHistory::getSpuId, spu.getId())
                                .orderByAsc(ProductStatusHistory::getCreatedAt).orderByAsc(ProductStatusHistory::getId))
                .stream().map(this::historyView).toList();
        return new ShopProductDetailView(product.id(), product.spuNo(), product.productName(), product.subtitle(),
                product.coverUrl(), product.galleryUrls(), product.detailHtml(), product.packingList(),
                product.serviceNote(), product.shop(), product.category(), product.brand(), product.attributes(),
                spu.getStatus(), spu.getContentVersion(),
                IdentityViewMapper.user(userMapper.selectById(spu.getCreatedBy())),
                IdentityViewMapper.user(userMapper.selectById(spu.getUpdatedBy())),
                skus, history, time(spu.getCreatedAt()), time(spu.getUpdatedAt()));
    }

    public ShopSkuView skuView(ProductSku sku) {
        InventoryStock stock = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                .eq(InventoryStock::getSkuId, sku.getId()));
        StockView stockView = new StockView(id(sku.getId()), stock == null ? 0 : stock.getAvailableQuantity(),
                stock == null ? 0 : stock.getLockedQuantity(), stock == null ? 0 : stock.getVersion(),
                stock == null ? null : time(stock.getUpdatedAt()));
        return new ShopSkuView(id(sku.getId()), sku.getSkuNo(), sku.getSkuName(), sku.getSpecJson(),
                money(sku.getSalePrice()), money(sku.getMarketPrice()), sku.getBarcode(), sku.getImageUrl(),
                sku.getStatus(), sku.getVersion(), stockView, time(sku.getCreatedAt()), time(sku.getUpdatedAt()));
    }

    private ShopProductSummaryView summary(ProductSpu spu) {
        ProductCategory category = categoryMapper.selectById(spu.getCategoryId());
        ProductBrand brand = spu.getBrandId() == null ? null : brandMapper.selectById(spu.getBrandId());
        List<ProductSku> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSpuId, spu.getId()));
        int available = 0;
        int locked = 0;
        for (ProductSku sku : skus) {
            InventoryStock stock = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                    .eq(InventoryStock::getSkuId, sku.getId()));
            if (stock != null) {
                available += stock.getAvailableQuantity();
                locked += stock.getLockedQuantity();
            }
        }
        return new ShopProductSummaryView(id(spu.getId()), spu.getSpuNo(), spu.getProductName(), spu.getCoverUrl(),
                new CategoryBrief(id(category.getId()), category.getCategoryCode(), category.getCategoryName()),
                brand == null ? null : brandView(brand), spu.getStatus(), spu.getContentVersion(), skus.size(),
                (int) skus.stream().filter(sku -> sku.getStatus() == EnabledStatus.ENABLED).count(), available, locked,
                time(spu.getCreatedAt()), time(spu.getUpdatedAt()));
    }

    private void validateCatalog(long categoryId, Long brandId, List<ProductAttributeInput> inputs) {
        ProductCategory category = categoryMapper.selectById(categoryId);
        if (category == null) throw BusinessException.notFound("CATEGORY_NOT_FOUND", "类目不存在");
        if (category.getStatus() != EnabledStatus.ENABLED) {
            throw BusinessException.unprocessable("CATEGORY_DISABLED", "类目已停用");
        }
        if (categoryMapper.exists(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getParentId, categoryId))) {
            throw BusinessException.unprocessable("CATEGORY_NOT_LEAF", "商品必须使用叶子类目");
        }
        if (brandId != null) {
            ProductBrand brand = brandMapper.selectById(brandId);
            if (brand == null) throw BusinessException.notFound("BRAND_NOT_FOUND", "品牌不存在");
            if (brand.getStatus() != EnabledStatus.ENABLED) {
                throw BusinessException.unprocessable("BRAND_DISABLED", "品牌已停用");
            }
        }
        Map<Long, ProductAttributeInput> submitted = inputs.stream().collect(Collectors.toMap(
                input -> parseId(input.attributeId()), Function.identity(), (a, b) -> {
                    throw BusinessException.badRequest("VALIDATION_FAILED", "属性不可重复");
                }));
        List<ProductCategoryAttribute> templates = templateMapper.selectList(
                new LambdaQueryWrapper<ProductCategoryAttribute>()
                        .eq(ProductCategoryAttribute::getCategoryId, categoryId)
                        .eq(ProductCategoryAttribute::getStatus, EnabledStatus.ENABLED));
        for (ProductCategoryAttribute template : templates) {
            ProductAttributeInput input = submitted.remove(template.getId());
            if (Boolean.TRUE.equals(template.getIsRequired()) && input == null) {
                throw BusinessException.unprocessable("PRODUCT_REQUIRED_ATTRIBUTE_MISSING", "缺少必填属性");
            }
            if (input != null) validateAttribute(template, input.value());
        }
        if (!submitted.isEmpty()) {
            throw BusinessException.unprocessable("PRODUCT_ATTRIBUTE_INVALID", "属性模板不属于商品类目");
        }
    }

    private void validateAttribute(ProductCategoryAttribute template, String value) {
        String trimmed = value.trim();
        boolean valid = switch (template.getValueType()) {
            case TEXT -> !trimmed.isEmpty();
            case NUMBER -> {
                try { new BigDecimal(trimmed); yield true; } catch (NumberFormatException ex) { yield false; }
            }
            case BOOLEAN -> "true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed);
            case OPTION -> template.getOptionsJson() != null && template.getOptionsJson().contains(trimmed);
        };
        if (!valid) throw BusinessException.unprocessable("PRODUCT_ATTRIBUTE_INVALID", "商品属性值无效");
    }

    private void replaceAttributes(ProductSpu spu, List<ProductAttributeInput> inputs) {
        valueMapper.delete(new LambdaQueryWrapper<ProductAttributeValue>().eq(ProductAttributeValue::getSpuId, spu.getId()));
        for (ProductAttributeInput input : inputs) {
            ProductAttributeValue value = new ProductAttributeValue();
            value.setSpuId(spu.getId());
            value.setCategoryId(spu.getCategoryId());
            value.setAttributeId(parseId(input.attributeId()));
            value.setAttributeValue(input.value().trim());
            valueMapper.insert(value);
        }
    }

    private ProductSku createSkuEntity(ProductSpu spu, SkuCreateInput input) {
        Map<String, String> spec;
        try {
            spec = SpecNormalizer.normalize(input.spec());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "SKU spec 格式无效");
        }
        String key = SpecNormalizer.key(spec);
        if (skuMapper.countSpecIncludingDeleted(spu.getId(), key) > 0) {
            throw BusinessException.conflict("SKU_SPEC_DUPLICATED", "商品规格已存在");
        }
        ProductSku sku = new ProductSku();
        sku.setSpuId(spu.getId());
        sku.setShopId(spu.getShopId());
        sku.setSkuNo(numbers.next("SKU"));
        sku.setSkuName(input.skuName().trim());
        sku.setSpecJson(spec);
        sku.setSpecKey(key);
        sku.setSalePrice(new BigDecimal(input.salePrice()));
        sku.setMarketPrice(input.marketPrice() == null ? null : new BigDecimal(input.marketPrice()));
        validatePrice(sku.getSalePrice(), sku.getMarketPrice());
        sku.setBarcode(org.dhu.shiguang_market.common.util.Formatters.trimToNull(input.barcode()));
        sku.setImageUrl(contentSafety.imageUrl("imageUrl", input.imageUrl()));
        sku.setStatus(EnabledStatus.ENABLED);
        sku.setVersion(0);
        try {
            skuMapper.insert(sku);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw BusinessException.conflict("SKU_SPEC_DUPLICATED", "商品规格已存在");
        }
        InventoryStock stock = new InventoryStock();
        stock.setSkuId(sku.getId());
        stock.setAvailableQuantity(0);
        stock.setLockedQuantity(0);
        stock.setVersion(0);
        stockMapper.insert(stock);
        return sku;
    }

    private void validateReady(ProductSpu spu) {
        if (spu.getProductName() == null || spu.getProductName().isBlank()
                || !skuMapper.exists(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSpuId, spu.getId()).eq(ProductSku::getStatus, EnabledStatus.ENABLED))) {
            throw BusinessException.unprocessable("BUSINESS_RULE_VIOLATION", "商品内容或 SKU 不完整");
        }
    }

    private void requireEditable(ProductSpu spu) {
        if (!EDITABLE.contains(spu.getStatus())) {
            throw BusinessException.conflict("PRODUCT_NOT_EDITABLE", "商品当前不可编辑");
        }
    }

    private void transition(ProductSpu spu, ProductStatus target, ProductOperationType operation, String reason) {
        ProductStatus from = spu.getStatus();
        spu.setStatus(target);
        spu.setUpdatedBy(currentUser.id());
        spuMapper.updateById(spu);
        historyMapper.insert(history(spu, from, target, operation, reason));
    }

    private ProductStatusHistory history(ProductSpu spu, ProductStatus from, ProductStatus to,
                                         ProductOperationType operation, String reason) {
        ProductStatusHistory history = new ProductStatusHistory();
        history.setSpuId(spu.getId());
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setOperationType(operation);
        history.setContentVersion(spu.getContentVersion());
        history.setOperatorType(OperatorType.SHOP);
        history.setOperatorId(currentUser.id());
        history.setReason(reason);
        return history;
    }

    private ProductStatusHistoryView historyView(ProductStatusHistory value) {
        SysUser operator = value.getOperatorId() == null ? null : userMapper.selectById(value.getOperatorId());
        return new ProductStatusHistoryView(id(value.getId()), id(value.getSpuId()), value.getFromStatus(),
                value.getToStatus(), value.getOperationType(), value.getContentVersion(), value.getOperatorType(),
                operator == null ? null : new OperatorBrief(id(operator.getId()), operator.getUsername(), operator.getNickname()),
                value.getReason(), time(value.getCreatedAt()));
    }

    private ProductSpu scoped(long shopId, long spuId) {
        return scoped(shopId, spuId, false);
    }

    private ProductSpu scoped(long shopId, long spuId, boolean lock) {
        LambdaQueryWrapper<ProductSpu> query = new LambdaQueryWrapper<ProductSpu>()
                .eq(ProductSpu::getId, spuId).eq(ProductSpu::getShopId, shopId);
        if (lock) query.last("FOR UPDATE");
        ProductSpu spu = spuMapper.selectOne(query);
        if (spu == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "商品不存在");
        return spu;
    }

    private void requireWritableShop(Shop shop) {
        if (shop.getStatus() == ShopStatus.CLOSED) {
            throw BusinessException.conflict("STATE_CONFLICT", "已关闭店铺仅允许读取历史数据");
        }
    }

    private ProductSku scopedSku(long shopId, long spuId, long skuId) {
        ProductSku sku = skuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getId, skuId).eq(ProductSku::getSpuId, spuId).eq(ProductSku::getShopId, shopId));
        if (sku == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "SKU 不存在");
        return sku;
    }

    private void applyContent(ProductSpu spu, String categoryId, String brandId, String productName,
                              String subtitle, String coverUrl, List<String> gallery, String detailHtml,
                              String packingList, String serviceNote) {
        spu.setCategoryId(parseId(categoryId));
        spu.setBrandId(parseNullableId(brandId));
        spu.setProductName(productName.trim());
        spu.setSubtitle(org.dhu.shiguang_market.common.util.Formatters.trimToNull(subtitle));
        spu.setCoverUrl(contentSafety.imageUrl("coverUrl", coverUrl));
        spu.setGalleryJson(contentSafety.imageUrls("galleryUrls", gallery, 10));
        spu.setDetailHtml(contentSafety.detailHtml(detailHtml));
        spu.setPackingList(packingList);
        spu.setServiceNote(serviceNote);
    }

    private void validatePrice(BigDecimal sale, BigDecimal market) {
        if (sale == null || sale.signum() <= 0 || (market != null && market.compareTo(sale) < 0)) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "SKU 价格关系无效");
        }
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || !value.matches("^(0|[1-9][0-9]{0,15})\\.[0-9]{2}$")) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "金额格式必须为两位小数字符串");
        }
        return new BigDecimal(value);
    }

    private BrandView brandView(ProductBrand brand) {
        return new BrandView(id(brand.getId()), brand.getBrandCode(), brand.getBrandName(),
                brand.getLogoUrl(), brand.getStatus());
    }

    private Long parseNullableId(String value) {
        return value == null ? null : parseId(value);
    }

    private long parseId(String value) {
        try {
            long result = Long.parseLong(value);
            if (result <= 0) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException ex) {
            throw BusinessException.badRequest("BAD_REQUEST", "ID 格式错误");
        }
    }
}
