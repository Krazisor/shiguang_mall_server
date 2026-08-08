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
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ProductGovernanceRequest;
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

    /**
     * 分页查询指定商品的全部状态历史。
     *
     * <p>历史记录按最新操作优先返回，便于平台人员直接查看最近一次治理结果。</p>
     */
    public PageView<ProductStatusHistoryView> history(long spuId, long page, long pageSize) {
        currentUser.requirePermission("platform:product:audit");
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
        }
        if (spuMapper.selectById(spuId) == null) {
            throw BusinessException.notFound("PRODUCT_NOT_FOUND", "商品不存在");
        }
        Page<ProductStatusHistory> result = historyMapper.selectPage(Page.of(page, pageSize),
                new LambdaQueryWrapper<ProductStatusHistory>()
                        .eq(ProductStatusHistory::getSpuId, spuId)
                        .orderByDesc(ProductStatusHistory::getCreatedAt)
                        .orderByDesc(ProductStatusHistory::getId));
        return PageView.of(result, result.getRecords().stream().map(this::history).toList());
    }

    /**
     * 禁售上架或已下架商品。
     */
    @Transactional
    public ProductReviewDetailView ban(long spuId, ProductGovernanceRequest request) {
        currentUser.requirePermission("platform:product:ban");
        long operatorId = currentUser.id();
        ProductSpu spu = requireProductForUpdate(spuId);
        String reason = validateGovernanceRequest(spu, request);
        if (spu.getStatus() != ProductStatus.OFF_SHELF && spu.getStatus() != ProductStatus.ON_SHELF) {
            throw BusinessException.conflict("PRODUCT_NOT_BANNABLE", "当前商品状态不允许禁售");
        }
        return changeGovernanceStatus(spu, ProductStatus.BANNED,
                ProductOperationType.BAN, operatorId, reason);
    }

    /**
     * 解除商品禁售。解禁后统一回到下架状态，避免商品未经店铺确认直接恢复销售。
     */
    @Transactional
    public ProductReviewDetailView revokeBan(long spuId, ProductGovernanceRequest request) {
        currentUser.requirePermission("platform:product:ban");
        long operatorId = currentUser.id();
        ProductSpu spu = requireProductForUpdate(spuId);
        String reason = validateGovernanceRequest(spu, request);
        if (spu.getStatus() != ProductStatus.BANNED) {
            throw BusinessException.conflict("PRODUCT_NOT_BANNED", "商品不在禁售状态");
        }
        return changeGovernanceStatus(spu, ProductStatus.OFF_SHELF,
                ProductOperationType.UNBAN, operatorId, reason);
    }

    /**
     * 平台强制下架正在销售的商品，并保留治理原因供后续追溯。
     */
    @Transactional
    public ProductReviewDetailView takeOffShelf(long spuId, ProductGovernanceRequest request) {
        currentUser.requirePermission("platform:product:ban");
        long operatorId = currentUser.id();
        ProductSpu spu = requireProductForUpdate(spuId);
        String reason = validateGovernanceRequest(spu, request);
        if (spu.getStatus() != ProductStatus.ON_SHELF) {
            throw BusinessException.conflict("PRODUCT_NOT_ON_SHELF", "商品不在上架状态");
        }
        return changeGovernanceStatus(spu, ProductStatus.OFF_SHELF,
                ProductOperationType.TAKE_OFF_SHELF, operatorId, reason);
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
        // 状态已发生变化，直接按当前 SPU 快照组装响应，不再要求商品仍处于待审核状态。
        return detailWithHistory(spu);
    }

    private ProductReviewDetailView buildDetail(ProductSpu spu) {
        // 直接按当前商品快照组装详情，使审核、禁售等状态变更后仍能返回统一结构。
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

    /** 写入一次平台治理状态变化，并返回包含最新历史的商品详情。 */
    private ProductReviewDetailView changeGovernanceStatus(ProductSpu spu, ProductStatus target,
                                                            ProductOperationType operation,
                                                            long operatorId, String reason) {
        ProductStatus source = spu.getStatus();
        spu.setStatus(target);
        spu.setUpdatedBy(operatorId);
        spuMapper.updateById(spu);

        ProductStatusHistory history = new ProductStatusHistory();
        history.setSpuId(spu.getId());
        history.setFromStatus(source);
        history.setToStatus(target);
        history.setOperationType(operation);
        history.setContentVersion(spu.getContentVersion());
        history.setOperatorType(OperatorType.PLATFORM);
        history.setOperatorId(operatorId);
        history.setReason(reason);
        historyMapper.insert(history);
        return detailWithHistory(spu);
    }

    /** 校验治理请求使用的是当前商品内容版本，并统一清理原因两端空白。 */
    private String validateGovernanceRequest(ProductSpu spu, ProductGovernanceRequest request) {
        if (request == null || request.contentVersion() < 0
                || request.reason() == null || request.reason().trim().isEmpty()
                || request.reason().trim().length() > 500) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "治理原因必填且长度不能超过 500");
        }
        if (spu.getContentVersion() != request.contentVersion()) {
            throw BusinessException.conflict("VERSION_CONFLICT", "商品内容版本已变化");
        }
        return request.reason().trim();
    }

    /** 查询并锁定商品，防止两个治理操作并发修改同一状态。 */
    private ProductSpu requireProductForUpdate(long spuId) {
        ProductSpu spu = spuMapper.selectOne(new LambdaQueryWrapper<ProductSpu>()
                .eq(ProductSpu::getId, spuId)
                .last("FOR UPDATE"));
        if (spu == null) {
            throw BusinessException.notFound("PRODUCT_NOT_FOUND", "商品不存在");
        }
        return spu;
    }

    /** 在基础详情中补齐该商品的完整状态历史。 */
    private ProductReviewDetailView detailWithHistory(ProductSpu spu) {
        ProductReviewDetailView response = buildDetail(spu);
        List<ProductStatusHistoryView> history = historyMapper.selectList(
                        new LambdaQueryWrapper<ProductStatusHistory>()
                                .eq(ProductStatusHistory::getSpuId, spu.getId())
                                .orderByAsc(ProductStatusHistory::getCreatedAt)
                                .orderByAsc(ProductStatusHistory::getId))
                .stream().map(this::history).toList();
        return new ProductReviewDetailView(response.id(), response.spuNo(), response.productName(), response.subtitle(),
                response.coverUrl(), response.galleryUrls(), response.detailHtml(), response.packingList(),
                response.serviceNote(), response.shop(), response.category(), response.brand(), response.attributes(),
                response.skus(), spu.getStatus(), response.contentVersion(), response.createdBy(), response.updatedBy(),
                history, response.createdAt(), response.updatedAt());
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
