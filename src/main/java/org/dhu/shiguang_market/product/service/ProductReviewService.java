package org.dhu.shiguang_market.product.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductOperationType;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.util.Formatters;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.service.IdentityViewMapper;
import org.dhu.shiguang_market.product.dto.ProductDtos.BrandView;
import org.dhu.shiguang_market.product.dto.ProductDtos.CategoryBrief;
import org.dhu.shiguang_market.product.dto.ProductDtos.ProductAttributeDisplayView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.OperatorBrief;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ProductReviewDetailView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ProductReviewSkuView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ProductReviewSummaryView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ProductStatusHistoryView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ReviewDecisionRequest;
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
import org.dhu.shiguang_market.product.model.ProductSku;
import org.dhu.shiguang_market.product.model.ProductSpu;
import org.dhu.shiguang_market.product.model.ProductStatusHistory;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductReviewService {
    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductCategoryAttributeMapper templateMapper;
    private final ProductAttributeValueMapper valueMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductStatusHistoryMapper historyMapper;
    private final ShopMapper shopMapper;
    private final SysUserMapper userMapper;
    private final CurrentUserService currentUser;

    public ProductReviewService(ProductSpuMapper spuMapper, ProductSkuMapper skuMapper,
                                ProductCategoryMapper categoryMapper, ProductCategoryAttributeMapper templateMapper,
                                ProductAttributeValueMapper valueMapper, ProductBrandMapper brandMapper,
                                ProductStatusHistoryMapper historyMapper, ShopMapper shopMapper,
                                SysUserMapper userMapper, CurrentUserService currentUser) {
        this.spuMapper = spuMapper;
        this.skuMapper = skuMapper;
        this.categoryMapper = categoryMapper;
        this.templateMapper = templateMapper;
        this.valueMapper = valueMapper;
        this.brandMapper = brandMapper;
        this.historyMapper = historyMapper;
        this.shopMapper = shopMapper;
        this.userMapper = userMapper;
        this.currentUser = currentUser;
    }

    public PageView<ProductReviewSummaryView> list(Long shopId, Long categoryId, String keyword,
                                                   long page, long pageSize) {
        currentUser.requirePermission("platform:product:audit");
        if (page < 1 || pageSize < 1 || pageSize > 100) throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
        LambdaQueryWrapper<ProductSpu> query = new LambdaQueryWrapper<ProductSpu>()
                .eq(ProductSpu::getStatus, ProductStatus.PENDING_REVIEW);
        if (shopId != null) query.eq(ProductSpu::getShopId, shopId);
        if (categoryId != null) query.eq(ProductSpu::getCategoryId, categoryId);
        if (keyword != null && !keyword.trim().isEmpty()) query.and(q -> q.like(ProductSpu::getProductName, keyword.trim())
                .or().like(ProductSpu::getSpuNo, keyword.trim()));
        query.orderByAsc(ProductSpu::getUpdatedAt).orderByAsc(ProductSpu::getId);
        Page<ProductSpu> result = spuMapper.selectPage(Page.of(page, pageSize), query);
        return PageView.of(result, result.getRecords().stream().map(this::summary).toList());
    }

    public ProductReviewDetailView detail(long spuId) {
        currentUser.requirePermission("platform:product:audit");
        ProductSpu spu = requirePending(spuId);
        Shop shop = shopMapper.selectById(spu.getShopId());
        ProductCategory category = categoryMapper.selectById(spu.getCategoryId());
        ProductBrand brand = spu.getBrandId() == null ? null : brandMapper.selectById(spu.getBrandId());
        List<ProductAttributeDisplayView> attributes = valueMapper.selectList(new LambdaQueryWrapper<ProductAttributeValue>()
                        .eq(ProductAttributeValue::getSpuId, spuId)).stream().map(value -> {
                    var template = templateMapper.selectById(value.getAttributeId());
                    return new ProductAttributeDisplayView(id(value.getAttributeId()), template.getAttributeName(),
                            value.getAttributeValue(), template.getUnit());
                }).toList();
        List<ProductReviewSkuView> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                        .eq(ProductSku::getSpuId, spuId).orderByAsc(ProductSku::getId)).stream()
                .map(sku -> new ProductReviewSkuView(id(sku.getId()), sku.getSkuNo(), sku.getSkuName(), sku.getSpecJson(),
                        money(sku.getSalePrice()), money(sku.getMarketPrice()), sku.getBarcode(), sku.getImageUrl(),
                        sku.getStatus(), sku.getVersion(), time(sku.getCreatedAt()), time(sku.getUpdatedAt()))).toList();
        List<ProductStatusHistoryView> history = historyMapper.selectList(new LambdaQueryWrapper<ProductStatusHistory>()
                        .eq(ProductStatusHistory::getSpuId, spuId).orderByAsc(ProductStatusHistory::getCreatedAt))
                .stream().map(this::history).toList();
        return new ProductReviewDetailView(id(spu.getId()), spu.getSpuNo(), spu.getProductName(), spu.getSubtitle(),
                spu.getCoverUrl(), spu.getGalleryJson() == null ? List.of() : spu.getGalleryJson(), spu.getDetailHtml(),
                spu.getPackingList(), spu.getServiceNote(), IdentityViewMapper.shop(shop),
                new CategoryBrief(id(category.getId()), category.getCategoryCode(), category.getCategoryName()),
                brand == null ? null : new BrandView(id(brand.getId()), brand.getBrandCode(), brand.getBrandName(),
                        brand.getLogoUrl(), brand.getStatus()), attributes, skus, spu.getStatus(), spu.getContentVersion(),
                IdentityViewMapper.user(userMapper.selectById(spu.getCreatedBy())),
                IdentityViewMapper.user(userMapper.selectById(spu.getUpdatedBy())), history,
                time(spu.getCreatedAt()), time(spu.getUpdatedAt()));
    }

    @Transactional
    public ProductReviewDetailView approve(long spuId, ReviewDecisionRequest request) {
        return decide(spuId, request, true);
    }

    @Transactional
    public ProductReviewDetailView reject(long spuId, ReviewDecisionRequest request) {
        if (request.reason() == null || request.reason().trim().isEmpty()) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "拒绝原因必填");
        }
        return decide(spuId, request, false);
    }

    private ProductReviewDetailView decide(long spuId, ReviewDecisionRequest request, boolean approved) {
        currentUser.requirePermission("platform:product:audit");
        ProductSpu spu = requirePending(spuId, true);
        if (spu.getContentVersion() != request.contentVersion()) {
            throw BusinessException.conflict("PRODUCT_REVIEW_VERSION_CHANGED", "待审内容版本已变化");
        }
        ProductStatus target = approved ? ProductStatus.OFF_SHELF : ProductStatus.REJECTED;
        ProductOperationType operation = approved ? ProductOperationType.APPROVE : ProductOperationType.REJECT;
        spu.setStatus(target);
        spu.setUpdatedBy(currentUser.id());
        spuMapper.updateById(spu);
        ProductStatusHistory history = new ProductStatusHistory();
        history.setSpuId(spuId);
        history.setFromStatus(ProductStatus.PENDING_REVIEW);
        history.setToStatus(target);
        history.setOperationType(operation);
        history.setContentVersion(spu.getContentVersion());
        history.setOperatorType(OperatorType.PLATFORM);
        history.setOperatorId(currentUser.id());
        history.setReason(request.reason());
        historyMapper.insert(history);
        // The review detail contract is for pending content; build the same shape after transition.
        ProductReviewDetailView response = detailSnapshot(spu);
        return new ProductReviewDetailView(response.id(), response.spuNo(), response.productName(), response.subtitle(),
                response.coverUrl(), response.galleryUrls(), response.detailHtml(), response.packingList(), response.serviceNote(),
                response.shop(), response.category(), response.brand(), response.attributes(), response.skus(), target,
                response.contentVersion(), response.createdBy(), response.updatedBy(),
                historyMapper.selectList(new LambdaQueryWrapper<ProductStatusHistory>()
                        .eq(ProductStatusHistory::getSpuId, spuId).orderByAsc(ProductStatusHistory::getCreatedAt)).stream()
                        .map(this::history).toList(), response.createdAt(), response.updatedAt());
    }

    private ProductReviewDetailView detailSnapshot(ProductSpu spu) {
        return buildDetail(spu);
    }

    private ProductReviewDetailView buildDetail(ProductSpu spu) {
        // Temporarily use direct builder so the response remains available after the state transition.
        Shop shop = shopMapper.selectById(spu.getShopId());
        ProductCategory category = categoryMapper.selectById(spu.getCategoryId());
        ProductBrand brand = spu.getBrandId() == null ? null : brandMapper.selectById(spu.getBrandId());
        List<ProductAttributeDisplayView> attributes = valueMapper.selectList(new LambdaQueryWrapper<ProductAttributeValue>()
                .eq(ProductAttributeValue::getSpuId, spu.getId())).stream().map(value -> {
            var template = templateMapper.selectById(value.getAttributeId());
            return new ProductAttributeDisplayView(id(value.getAttributeId()), template.getAttributeName(), value.getAttributeValue(), template.getUnit());
        }).toList();
        List<ProductReviewSkuView> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSpuId, spu.getId())).stream().map(sku -> new ProductReviewSkuView(id(sku.getId()),
                sku.getSkuNo(), sku.getSkuName(), sku.getSpecJson(), money(sku.getSalePrice()), money(sku.getMarketPrice()),
                sku.getBarcode(), sku.getImageUrl(), sku.getStatus(), sku.getVersion(), time(sku.getCreatedAt()), time(sku.getUpdatedAt()))).toList();
        return new ProductReviewDetailView(id(spu.getId()), spu.getSpuNo(), spu.getProductName(), spu.getSubtitle(),
                spu.getCoverUrl(), spu.getGalleryJson() == null ? List.of() : spu.getGalleryJson(), spu.getDetailHtml(),
                spu.getPackingList(), spu.getServiceNote(), IdentityViewMapper.shop(shop),
                new CategoryBrief(id(category.getId()), category.getCategoryCode(), category.getCategoryName()),
                brand == null ? null : new BrandView(id(brand.getId()), brand.getBrandCode(), brand.getBrandName(), brand.getLogoUrl(), brand.getStatus()),
                attributes, skus, spu.getStatus(), spu.getContentVersion(), IdentityViewMapper.user(userMapper.selectById(spu.getCreatedBy())),
                IdentityViewMapper.user(userMapper.selectById(spu.getUpdatedBy())), List.of(), time(spu.getCreatedAt()), time(spu.getUpdatedAt()));
    }

    private ProductReviewSummaryView summary(ProductSpu spu) {
        Shop shop = shopMapper.selectById(spu.getShopId());
        ProductCategory category = categoryMapper.selectById(spu.getCategoryId());
        ProductStatusHistory submitted = historyMapper.selectOne(new LambdaQueryWrapper<ProductStatusHistory>()
                .eq(ProductStatusHistory::getSpuId, spu.getId())
                .eq(ProductStatusHistory::getOperationType, ProductOperationType.SUBMIT_REVIEW)
                .orderByDesc(ProductStatusHistory::getId).last("LIMIT 1"));
        return new ProductReviewSummaryView(id(spu.getId()), spu.getSpuNo(), spu.getProductName(), spu.getCoverUrl(),
                IdentityViewMapper.shop(shop), new CategoryBrief(id(category.getId()), category.getCategoryCode(), category.getCategoryName()),
                spu.getContentVersion(), submitted == null ? time(spu.getUpdatedAt()) : time(submitted.getCreatedAt()));
    }

    private ProductStatusHistoryView history(ProductStatusHistory value) {
        var operator = value.getOperatorId() == null ? null : userMapper.selectById(value.getOperatorId());
        return new ProductStatusHistoryView(id(value.getId()), id(value.getSpuId()), value.getFromStatus(), value.getToStatus(),
                value.getOperationType(), value.getContentVersion(), value.getOperatorType(), operator == null ? null
                : new OperatorBrief(id(operator.getId()), operator.getUsername(), operator.getNickname()), value.getReason(), time(value.getCreatedAt()));
    }

    private ProductSpu requirePending(long spuId) {
        return requirePending(spuId, false);
    }

    private ProductSpu requirePending(long spuId, boolean lock) {
        LambdaQueryWrapper<ProductSpu> query = new LambdaQueryWrapper<ProductSpu>()
                .eq(ProductSpu::getId, spuId);
        if (lock) query.last("FOR UPDATE");
        ProductSpu spu = spuMapper.selectOne(query);
        if (spu == null) throw BusinessException.notFound("PRODUCT_NOT_FOUND", "商品不存在");
        if (spu.getStatus() != ProductStatus.PENDING_REVIEW) {
            throw BusinessException.conflict("PRODUCT_NOT_PENDING_REVIEW", "商品不在待审核状态");
        }
        return spu;
    }
}
