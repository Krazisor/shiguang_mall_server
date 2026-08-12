package org.dhu.shiguang_market.coupon.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponAudienceType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponDistributionType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponOwnerType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRedeemCodeStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponTemplateStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponType;
import org.dhu.shiguang_market.common.model.MarketEnums.UserCouponStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.Formatters;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.BatchCouponGrantView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.ClaimableActivityDetailView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.ClaimableActivitySummaryView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.ClaimableTemplateView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponCodeBatchCreatedView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponCodeBatchSummaryView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponGrantResult;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CreateRedeemCodeBatchRequest;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.GrantCouponsRequest;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.RedeemCouponCodeRequest;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.UserCouponDetailView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.UserCouponSummaryView;
import org.dhu.shiguang_market.coupon.mapper.CouponActivityMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponClaimRecordMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponRedeemCodeMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponTemplateMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponScopeMappers;
import org.dhu.shiguang_market.coupon.mapper.UserCouponMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.ClaimRecord;
import org.dhu.shiguang_market.coupon.model.CouponModels.CodeBatchSummaryRow;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponActivity;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponTemplate;
import org.dhu.shiguang_market.coupon.model.CouponModels.RedeemCode;
import org.dhu.shiguang_market.coupon.model.CouponModels.UserCoupon;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.dhu.shiguang_market.product.service.PublicCatalogService;
import org.dhu.shiguang_market.product.dto.ProductDtos.ProductCardView;
import org.dhu.shiguang_market.coupon.model.CouponModels.ShopScope;
import org.dhu.shiguang_market.coupon.model.CouponModels.CategoryScope;
import org.dhu.shiguang_market.coupon.model.CouponModels.SpuScope;
import org.dhu.shiguang_market.coupon.model.CouponModels.SkuScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final CouponActivityMapper activityMapper;
    private final CouponTemplateMapper templateMapper;
    private final UserCouponMapper userCouponMapper;
    private final CouponClaimRecordMapper claimMapper;
    private final CouponRedeemCodeMapper redeemMapper;
    private final CurrentUserService currentUser;
    private final ShopAccessService shopAccess;
    private final ShopMapper shopMapper;
    private final IdempotencyService idempotency;
    private final NumberGenerator numbers;
    private final CouponViewMapper views;
    private final CouponScopeMappers.Shop shopScopes;
    private final CouponScopeMappers.Category categoryScopes;
    private final CouponScopeMappers.Spu spuScopes;
    private final CouponScopeMappers.Sku skuScopes;
    private final PublicCatalogService catalog;
    private final CouponBudgetService budget;
    private final CouponEligibilityService eligibility;
    private final CouponRateLimitService rateLimits;
    private final CouponAuditService audit;
    private final String redeemSecret;
    private final int redeemKeyVersion;

    public CouponService(CouponActivityMapper activityMapper, CouponTemplateMapper templateMapper,
                         UserCouponMapper userCouponMapper, CouponClaimRecordMapper claimMapper,
                         CouponRedeemCodeMapper redeemMapper, CurrentUserService currentUser,
                         ShopAccessService shopAccess, ShopMapper shopMapper, IdempotencyService idempotency,
                         NumberGenerator numbers, CouponViewMapper views, CouponScopeMappers.Shop shopScopes,
                         CouponScopeMappers.Category categoryScopes, CouponScopeMappers.Spu spuScopes,
                         CouponScopeMappers.Sku skuScopes, PublicCatalogService catalog, CouponBudgetService budget,
                         CouponEligibilityService eligibility, CouponRateLimitService rateLimits,
                         CouponAuditService audit,
                         @Value("${market.coupon.redeem-secret:development-only-change-me}") String redeemSecret,
                         @Value("${market.coupon.redeem-key-version:1}") int redeemKeyVersion) {
        this.activityMapper = activityMapper; this.templateMapper = templateMapper;
        this.userCouponMapper = userCouponMapper; this.claimMapper = claimMapper;
        this.redeemMapper = redeemMapper; this.currentUser = currentUser; this.shopAccess = shopAccess;
        this.shopMapper = shopMapper; this.idempotency = idempotency; this.numbers = numbers;
        this.views = views; this.redeemSecret = redeemSecret; this.redeemKeyVersion = redeemKeyVersion;
        this.shopScopes = shopScopes; this.categoryScopes = categoryScopes; this.spuScopes = spuScopes;
        this.skuScopes = skuScopes; this.catalog = catalog;
        this.budget = budget;
        this.eligibility = eligibility;
        this.rateLimits = rateLimits;
        this.audit = audit;
    }

    public PageView<ClaimableActivitySummaryView> center(CouponActivityType type, Long shopId,
                                                          CouponActivityStatus status, long page, long pageSize,
                                                          String sort) {
        require("coupon:read:self");
        validatePage(page, pageSize);
        if (status != null && !List.of(CouponActivityStatus.SCHEDULED, CouponActivityStatus.RUNNING,
                CouponActivityStatus.PAUSED).contains(status)) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "买家侧不支持该活动状态");
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<CouponActivity> query = new LambdaQueryWrapper<CouponActivity>()
                .eq(type != null, CouponActivity::getActivityType, type)
                .eq(shopId != null, CouponActivity::getShopId, shopId)
                .in(status == null, CouponActivity::getStatus,
                        List.of(CouponActivityStatus.SCHEDULED, CouponActivityStatus.RUNNING, CouponActivityStatus.PAUSED))
                .eq(status != null, CouponActivity::getStatus, status)
                .gt(CouponActivity::getEndsAt, now)
                .exists("SELECT 1 FROM coupon_template ct WHERE ct.activity_id = coupon_activity.id "
                        + "AND ct.distribution_type IN ('PUBLIC_CLAIM','FLASH_CLAIM') "
                        + "AND ct.status IN ('ACTIVE','PAUSED')");
        switch (sort == null ? "startsAt,asc" : sort) {
            case "startsAt,asc" -> query.orderByAsc(CouponActivity::getStartsAt).orderByAsc(CouponActivity::getId);
            case "startsAt,desc" -> query.orderByDesc(CouponActivity::getStartsAt).orderByDesc(CouponActivity::getId);
            default -> throw BusinessException.badRequest("BAD_REQUEST", "不支持的排序字段");
        }
        Page<CouponActivity> result = activityMapper.selectPage(Page.of(page, pageSize), query);
        return PageView.of(result, result.getRecords().stream().map(this::summary).toList());
    }

    public ClaimableActivityDetailView centerDetail(long activityId) {
        require("coupon:read:self");
        CouponActivity activity = activityMapper.selectById(activityId);
        if (activity == null || !visible(activity)) throw notFound();
        List<CouponTemplate> templates = templateMapper.selectList(new LambdaQueryWrapper<CouponTemplate>()
                .eq(CouponTemplate::getActivityId, activityId)
                .in(CouponTemplate::getDistributionType, CouponDistributionType.PUBLIC_CLAIM,
                        CouponDistributionType.FLASH_CLAIM)
                .in(CouponTemplate::getStatus, CouponTemplateStatus.ACTIVE, CouponTemplateStatus.PAUSED)
                .orderByAsc(CouponTemplate::getSortOrder).orderByAsc(CouponTemplate::getId));
        if (templates.isEmpty()) throw notFound();
        return new ClaimableActivityDetailView(summary(activity), templates.stream()
                .map(template -> claimableTemplate(activity, template, currentUser.id())).toList());
    }

    @Transactional
    public UserCouponDetailView claim(long activityId, long templateId, String key) {
        long userId = require("coupon:claim");
        String path = "/api/coupon-center/activities/" + activityId + "/templates/" + templateId + "/claim";
        return idempotency.execute(userId, "POST", path, key, "claim", UserCouponDetailView.class,
                () -> {
                    CouponTemplate template = template(templateId);
                    if (template.getDistributionType() != CouponDistributionType.PUBLIC_CLAIM
                            && template.getDistributionType() != CouponDistributionType.FLASH_CLAIM) {
                        throw BusinessException.unprocessable("COUPON_TEMPLATE_INVALID", "模板不支持公开领取");
                    }
                    rateLimits.claim(userId, templateId,
                            template.getDistributionType() == CouponDistributionType.FLASH_CLAIM);
                    return detail(issue(userId, activityId, templateId, template.getDistributionType(),
                            null, null, key));
                });
    }

    public PageView<UserCouponSummaryView> mine(UserCouponStatus status, CouponType couponType,
                                                 CouponOwnerType ownerType, LocalDateTime expiringBefore,
                                                 String keyword, long page, long pageSize, String sort) {
        long userId = require("coupon:read:self");
        validatePage(page, pageSize);
        validateCouponSort(sort);
        Page<UserCoupon> coupons = userCouponMapper.selectCouponPage(Page.of(page, pageSize), userId,
                null, null, status, couponType, ownerType, expiringBefore,
                Formatters.trimToNull(keyword), sort);
        LocalDateTime now = LocalDateTime.now();
        List<UserCouponSummaryView> result = coupons.getRecords().stream()
                .map(coupon -> summary(coupon, now)).toList();
        return PageView.of(coupons, result);
    }

    public UserCouponDetailView mineDetail(long userCouponId) {
        long userId = require("coupon:read:self");
        UserCoupon coupon = ownedCoupon(userCouponId, userId);
        return detail(coupon);
    }

    public PageView<ProductCardView> eligibleProducts(long userCouponId, String keyword,
                                                      long page, long pageSize, String sort) {
        long userId = require("coupon:read:self");
        UserCoupon coupon = ownedCoupon(userCouponId, userId);
        CouponTemplate template = template(coupon.getTemplateId());
        List<Long> targets = switch (template.getScopeType()) {
            case ALL -> List.of();
            case SHOP -> shopScopes.selectList(new LambdaQueryWrapper<ShopScope>()
                    .eq(ShopScope::getTemplateId, template.getId())).stream().map(ShopScope::getShopId).toList();
            case CATEGORY -> categoryScopes.selectList(new LambdaQueryWrapper<CategoryScope>()
                    .eq(CategoryScope::getTemplateId, template.getId())).stream().map(CategoryScope::getCategoryId).toList();
            case SPU -> spuScopes.selectList(new LambdaQueryWrapper<SpuScope>()
                    .eq(SpuScope::getTemplateId, template.getId())).stream().map(SpuScope::getSpuId).toList();
            case SKU -> skuScopes.selectList(new LambdaQueryWrapper<SkuScope>()
                    .eq(SkuScope::getTemplateId, template.getId())).stream().map(SkuScope::getSkuId).toList();
        };
        return catalog.couponEligibleProducts(template.getScopeType(), targets,
                template.getOwnerShopId(), keyword, page, pageSize, sort);
    }

    @Transactional
    public UserCouponDetailView redeem(RedeemCouponCodeRequest request, String key) {
        long userId = require("coupon:claim");
        String path = "/api/coupons/redeem";
        return idempotency.execute(userId, "POST", path, key, request,
                UserCouponDetailView.class, () -> {
                    rateLimits.redeem(userId);
                    return redeemInternal(userId, request.code(), key);
                });
    }

    @Transactional
    public BatchCouponGrantView grant(long templateId, Long shopId, GrantCouponsRequest request, String key) {
        long operator = authorizeGrant(shopId);
        CouponTemplate template = template(templateId);
        if (shopId == null && template.getOwnerType() != CouponOwnerType.PLATFORM) throw notFound();
        if (shopId != null && (template.getOwnerType() != CouponOwnerType.SHOP
                || !Objects.equals(template.getOwnerShopId(), shopId))) throw notFound();
        if (template.getDistributionType() != CouponDistributionType.DIRECT_GRANT) {
            throw BusinessException.unprocessable("COUPON_TEMPLATE_INVALID", "模板不是定向发券模板");
        }
        String path = shopId == null ? "/api/platform/coupon-templates/" + templateId + "/grants"
                : "/api/shops/" + shopId + "/coupon-templates/" + templateId + "/grants";
        return idempotency.execute(operator, "POST", path, key, request, BatchCouponGrantView.class,
                () -> {
                    rateLimits.managementGrant(operator, templateId);
                    return grantInternal(template, request, operator, key);
                });
    }

    @Transactional
    public CouponCodeBatchCreatedView createCodeBatch(long templateId, Long shopId,
                                                       CreateRedeemCodeBatchRequest request, String key) {
        long operator = authorizeGrant(shopId);
        CouponTemplate template = template(templateId);
        boolean ownerMismatch = shopId == null ? template.getOwnerType() != CouponOwnerType.PLATFORM
                : template.getOwnerType() != CouponOwnerType.SHOP
                || !Objects.equals(template.getOwnerShopId(), shopId);
        if (template.getDistributionType() != CouponDistributionType.REDEEM_CODE || ownerMismatch) {
            throw BusinessException.unprocessable("COUPON_TEMPLATE_INVALID", "模板不是兑换码模板");
        }
        if (template.getStatus() != CouponTemplateStatus.ACTIVE) {
            throw BusinessException.unprocessable("COUPON_TEMPLATE_NOT_CLAIMABLE", "优惠券模板当前不可发放");
        }
        if (template.getIssuedCount() >= template.getTotalIssueLimit()
                || remainingResponsibility(template).compareTo(maxLiability(template)) < 0) {
            throw BusinessException.unprocessable("COUPON_SOLD_OUT", "优惠券已无可用发行责任容量");
        }
        String path = shopId == null ? "/api/platform/coupon-templates/" + templateId + "/redeem-code-batches"
                : "/api/shops/" + shopId + "/coupon-templates/" + templateId + "/redeem-code-batches";
        return idempotency.execute(operator, "POST", path, key, request, CouponCodeBatchCreatedView.class,
                () -> {
                    rateLimits.managementGrant(operator, templateId);
                    return codeBatch(template, request, operator);
                });
    }

    public PageView<CouponCodeBatchSummaryView> codeBatches(Long shopId, Long templateId, String batchNo,
                                                            CouponRedeemCodeStatus status,
                                                            LocalDateTime createdFrom,
                                                            LocalDateTime createdTo,
                                                            long page, long pageSize) {
        authorizeGrant(shopId);
        validatePage(page, pageSize);
        if (createdFrom != null && createdTo != null && !createdFrom.isBefore(createdTo)) {
            throw BusinessException.badRequest("INVALID_TIME_RANGE", "createdFrom 必须早于 createdTo");
        }
        String normalizedBatchNo = batchNo == null || batchNo.isBlank() ? null : batchNo.trim();
        Page<CodeBatchSummaryRow> resultPage = redeemMapper.selectBatchPage(Page.of(page, pageSize), shopId,
                templateId, normalizedBatchNo, status, createdFrom, createdTo);
        List<CouponCodeBatchSummaryView> result = resultPage.getRecords().stream()
                .map(row -> new CouponCodeBatchSummaryView(row.getBatchNo(), id(row.getTemplateId()),
                        row.getStatus(), row.getTotal(), row.getActive(), row.getRedeemed(), row.getRevoked(),
                        time(row.getCreatedAt())))
                .toList();
        return PageView.of(resultPage, result);
    }

    @Transactional
    public UserCouponDetailView revoke(long userCouponId, String reason, int version, String key) {
        long operator = require("platform:coupon:governance");
        String path = "/api/platform/coupon-governance/user-coupons/" + userCouponId + "/revoke";
        return idempotency.execute(operator, "POST", path, key, Map.of("reason", reason, "version", version),
                UserCouponDetailView.class, () -> {
                    UserCoupon coupon = userCouponMapper.selectById(userCouponId);
                    if (coupon == null) throw notFound();
                    if (coupon.getStatus() == UserCouponStatus.LOCKED) throw BusinessException.conflict("COUPON_LOCKED_BY_TRADE", "优惠券已被交易锁定");
                    if (coupon.getStatus() == UserCouponStatus.USED) throw BusinessException.conflict("COUPON_ALREADY_USED", "优惠券已核销");
                    if (coupon.getStatus() != UserCouponStatus.AVAILABLE) throw BusinessException.unprocessable("COUPON_REVOKED", "优惠券不可撤销");
                    if (!LocalDateTime.now().isBefore(coupon.getValidTo())) throw BusinessException.unprocessable("COUPON_EXPIRED", "优惠券已过期");
                    if ((coupon.getVersion() == null ? 0 : coupon.getVersion()) != version) {
                        throw BusinessException.conflict("VERSION_CONFLICT", "资源版本冲突");
                    }
                    if (Formatters.trimToNull(reason) == null) {
                        throw BusinessException.badRequest("VALIDATION_FAILED", "撤销原因不能为空");
                    }
                    coupon.setStatus(UserCouponStatus.REVOKED); coupon.setRevokedBy(operator); coupon.setRevokedReason(reason.trim()); coupon.setRevokedAt(LocalDateTime.now());
                    userCouponMapper.updateById(coupon);
                    budget.release(coupon, "REVOKE_RELEASE", "COUPON_REVOKE", coupon.getCouponNo());
                    audit.log("USER_COUPON", coupon.getId(), "REVOKE", OperatorType.PLATFORM,
                            operator, null, UserCouponStatus.AVAILABLE.name(), UserCouponStatus.REVOKED.name(),
                            null, reason.trim());
                    return detail(coupon);
                });
    }

    private BatchCouponGrantView grantInternal(CouponTemplate template, GrantCouponsRequest request, long operator, String key) {
        List<Long> ids = request.userIds().stream().map(this::parseId).distinct().toList();
        List<CouponGrantResult> results = new ArrayList<>();
        for (Long userId : ids) {
            try {
                UserCoupon coupon = issue(userId, null, template.getId(), CouponDistributionType.DIRECT_GRANT, operator, null,
                        key + ":" + userId, false);
                results.add(new CouponGrantResult(id(userId), true, id(coupon.getId()), coupon.getCouponNo(), null));
            } catch (BusinessException ex) {
                results.add(new CouponGrantResult(id(userId), false, null, null, ex.getCode()));
            }
        }
        long succeeded = results.stream().filter(CouponGrantResult::success).count();
        audit.log("TEMPLATE", template.getId(), "GRANT",
                template.getOwnerShopId() == null ? OperatorType.PLATFORM : OperatorType.SHOP,
                operator, template.getOwnerShopId(), null, null,
                Map.of("requested", ids.size(), "succeeded", succeeded), request.reason().trim());
        return new BatchCouponGrantView(id(template.getId()), ids.size(), (int) succeeded,
                results.size() - (int) succeeded, results);
    }

    private CouponCodeBatchCreatedView codeBatch(CouponTemplate template, CreateRedeemCodeBatchRequest request, long operator) {
        String batchNo = numbers.next("RCB");
        String prefix = Formatters.trimToNull(request.codePrefix());
        if (prefix == null) prefix = "SGM";
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < request.quantity(); i++) {
            String code = prefix + "-" + randomCode(4) + "-" + randomCode(4) + "-" + randomCode(4);
            RedeemCode row = new RedeemCode(); row.setBatchNo(batchNo); row.setTemplateId(template.getId());
            row.setCodeHash(hash(code)); row.setHashKeyVersion(redeemKeyVersion); row.setStatus(CouponRedeemCodeStatus.ACTIVE); row.setCreatedBy(operator);
            redeemMapper.insert(row); codes.add(code);
        }
        audit.log("REDEEM_CODE_BATCH", template.getId(), "CREATE",
                template.getOwnerShopId() == null ? OperatorType.PLATFORM : OperatorType.SHOP,
                operator, template.getOwnerShopId(), null, null,
                Map.of("batchNo", batchNo, "quantity", request.quantity()), request.reason().trim());
        return new CouponCodeBatchCreatedView(batchNo, id(template.getId()), request.quantity(), codes);
    }

    private BigDecimal remainingResponsibility(CouponTemplate template) {
        BigDecimal reserved = template.getBudgetReservedAmount() == null ? BigDecimal.ZERO : template.getBudgetReservedAmount();
        BigDecimal consumed = template.getBudgetConsumedAmount() == null ? BigDecimal.ZERO : template.getBudgetConsumedAmount();
        BigDecimal reversed = template.getBudgetReversedAmount() == null ? BigDecimal.ZERO : template.getBudgetReversedAmount();
        return template.getBudgetAmount().subtract(reserved).subtract(consumed).add(reversed);
    }

    private BigDecimal maxLiability(CouponTemplate template) {
        return template.getCouponType() == CouponType.PERCENTAGE
                ? template.getMaximumDiscountAmount() : template.getDiscountAmount();
    }

    private UserCouponDetailView redeemInternal(long userId, String rawCode, String key) {
        String code = rawCode.trim();
        if (!code.chars().allMatch(c -> c >= 32 && c <= 126)) throw BusinessException.unprocessable("COUPON_CODE_INVALID", "兑换码无效");
        RedeemCode redeem = redeemMapper.selectOne(new LambdaQueryWrapper<RedeemCode>().eq(RedeemCode::getCodeHash, hash(code)).last("FOR UPDATE"));
        if (redeem == null || redeem.getStatus() == CouponRedeemCodeStatus.REVOKED) throw BusinessException.unprocessable("COUPON_CODE_INVALID", "兑换码无效");
        if (redeem.getStatus() == CouponRedeemCodeStatus.REDEEMED) {
            UserCoupon mine = redeem.getRedeemedBy() != null && redeem.getRedeemedBy().equals(userId) ? userCouponMapper.selectById(redeem.getUserCouponId()) : null;
            if (mine != null) throw BusinessException.conflict("COUPON_CODE_ALREADY_REDEEMED_BY_SELF", "兑换码已由本人使用");
            throw BusinessException.unprocessable("COUPON_CODE_INVALID", "兑换码无效");
        }
        CouponTemplate template = template(redeem.getTemplateId());
        UserCoupon coupon = issue(userId, null, template.getId(), CouponDistributionType.REDEEM_CODE, null, redeem.getId(), key, false);
        redeem.setStatus(CouponRedeemCodeStatus.REDEEMED); redeem.setUserCouponId(coupon.getId()); redeem.setRedeemedBy(userId); redeem.setRedeemedAt(LocalDateTime.now()); redeemMapper.updateById(redeem);
        return detail(coupon);
    }

    private UserCoupon issue(long userId, Long activityId, long templateId, CouponDistributionType source,
                             Long grantedBy, Long redeemCodeId, String businessKey) {
        return issue(userId, activityId, templateId, source, grantedBy, redeemCodeId, businessKey, true);
    }

    private UserCoupon issue(long userId, Long activityId, long templateId, CouponDistributionType source,
                             Long grantedBy, Long redeemCodeId, String businessKey, boolean checkActivity) {
        CouponActivity claimActivity = checkActivity && activityId != null
                ? claimableActivityForUpdate(activityId) : null;
        CouponTemplate template = templateForUpdate(templateId);
        if (source == CouponDistributionType.SYSTEM_GRANT) {
            ClaimRecord existingClaim = claimMapper.selectOne(new LambdaQueryWrapper<ClaimRecord>()
                    .eq(ClaimRecord::getBusinessNo, businessKey));
            if (existingClaim != null) return userCouponMapper.selectById(existingClaim.getUserCouponId());
        }
        validateIssuance(template, activityId, source, checkActivity);
        eligibility.requireIssueEligibility(userId, template, source);
        if (claimActivity != null && !Objects.equals(template.getActivityId(), claimActivity.getId())) throw notFound();
        List<UserCoupon> existing = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getTemplateId, templateId).eq(UserCoupon::getUserId, userId)
                .orderByAsc(UserCoupon::getId).last("FOR UPDATE"));
        if (existing.size() >= template.getPerUserLimit()) throw BusinessException.conflict("COUPON_USER_LIMIT_REACHED", "已达到个人领取上限");
        BigDecimal liability = template.getCouponType() == org.dhu.shiguang_market.common.model.MarketEnums.CouponType.PERCENTAGE
                ? template.getMaximumDiscountAmount() : template.getDiscountAmount();
        if (templateMapper.reserveIssue(templateId, liability) != 1) {
            throw BusinessException.unprocessable("COUPON_SOLD_OUT", "优惠券已售罄");
        }
        LocalDateTime now = LocalDateTime.now();
        UserCoupon coupon = new UserCoupon(); coupon.setCouponNo(numbers.next("UC")); coupon.setTemplateId(templateId);
        coupon.setTemplateVersion(template.getVersion() == null ? 0 : template.getVersion()); coupon.setUserId(userId); coupon.setStatus(UserCouponStatus.AVAILABLE);
        if (template.getValidityType() == org.dhu.shiguang_market.common.model.MarketEnums.CouponValidityType.FIXED_RANGE) { coupon.setValidFrom(template.getValidFrom()); coupon.setValidTo(template.getValidTo()); }
        else { coupon.setValidFrom(now.plusMinutes(template.getEffectiveDelayMinutes())); coupon.setValidTo(now.plusMinutes(template.getEffectiveDelayMinutes()).plusHours(template.getValidForHours())); }
        coupon.setRestoreCount(0); userCouponMapper.insert(coupon);
        ClaimRecord claim = new ClaimRecord(); claim.setClaimNo(numbers.next("CC")); claim.setUserCouponId(coupon.getId()); claim.setTemplateId(templateId); claim.setUserId(userId); claim.setActivityId(activityId); claim.setClaimSource(source); claim.setRedeemCodeId(redeemCodeId); claim.setGrantedBy(grantedBy); claim.setBusinessNo(source == CouponDistributionType.SYSTEM_GRANT ? businessKey : idempotency.businessNo("CL", userId, businessKey)); claimMapper.insert(claim);
        budget.recordClaim(template, coupon, claim);
        OperatorType operatorType = source == CouponDistributionType.SYSTEM_GRANT ? OperatorType.SYSTEM
                : source == CouponDistributionType.DIRECT_GRANT
                ? (template.getOwnerShopId() == null ? OperatorType.PLATFORM : OperatorType.SHOP)
                : OperatorType.USER;
        Long operatorId = source == CouponDistributionType.SYSTEM_GRANT ? null
                : source == CouponDistributionType.DIRECT_GRANT ? grantedBy : userId;
        audit.log("USER_COUPON", coupon.getId(), "ISSUE", operatorType, operatorId,
                template.getOwnerShopId(), null, UserCouponStatus.AVAILABLE.name(),
                Map.of("templateId", templateId, "claimSource", source.name()), null);
        return coupon;
    }

    @Transactional
    public UserCoupon grantSystemCoupon(long userId, long templateId) {
        CouponTemplate template = template(templateId);
        String businessNo = "SYSTEM_GRANT:USER_REGISTERED:" + userId + ":" + templateId;
        return issue(userId, template.getActivityId(), templateId, CouponDistributionType.SYSTEM_GRANT,
                null, null, businessNo, false);
    }

    private ClaimableActivitySummaryView summary(CouponActivity activity) {
        Shop shop = activity.getShopId() == null ? null : shopMapper.selectById(activity.getShopId());
        int count = Math.toIntExact(templateMapper.selectCount(new LambdaQueryWrapper<CouponTemplate>()
                .eq(CouponTemplate::getActivityId, activity.getId())
                .in(CouponTemplate::getDistributionType, CouponDistributionType.PUBLIC_CLAIM,
                        CouponDistributionType.FLASH_CLAIM)
                .in(CouponTemplate::getStatus, CouponTemplateStatus.ACTIVE, CouponTemplateStatus.PAUSED)));
        return new ClaimableActivitySummaryView(id(activity.getId()), activity.getActivityNo(), activity.getActivityType(), activity.getActivityName(), activity.getSubtitle(), activity.getBannerUrl(), activity.getOwnerType(), shop == null ? null : org.dhu.shiguang_market.identity.service.IdentityViewMapper.shop(shop), activity.getStatus(), time(activity.getStartsAt()), time(activity.getEndsAt()), Formatters.time(LocalDateTime.now()), count);
    }

    private ClaimableTemplateView claimableTemplate(CouponActivity activity, CouponTemplate template, long userId) {
        int claimed = Math.toIntExact(userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>().eq(UserCoupon::getTemplateId, template.getId()).eq(UserCoupon::getUserId, userId)));
        LocalDateTime now = LocalDateTime.now();
        String reason = claimabilityReason(activity, template, userId, claimed, now);
        Integer remaining = template.getTotalIssueLimit() - template.getIssuedCount();
        return new ClaimableTemplateView(views.template(template), template.getDistributionType(), time(template.getClaimStartsAt()), time(template.getClaimEndsAt()), remaining <= 0 ? "SOLD_OUT" : remaining < 20 ? "LIMITED" : "AVAILABLE", remaining <= 20 ? Math.max(remaining, 0) : null, claimed, template.getPerUserLimit(), reason == null, reason);
    }

    private String claimabilityReason(CouponActivity activity, CouponTemplate template, long userId,
                                      int claimed, LocalDateTime now) {
        if (now.isBefore(activity.getStartsAt()) || now.isBefore(template.getClaimStartsAt())) return "NOT_STARTED";
        if (activity.getStatus() == CouponActivityStatus.PAUSED || template.getStatus() == CouponTemplateStatus.PAUSED) return "ACTIVITY_PAUSED";
        if (activity.getStatus() == CouponActivityStatus.ENDED || activity.getStatus() == CouponActivityStatus.CANCELLED
                || !now.isBefore(activity.getEndsAt()) || !now.isBefore(template.getClaimEndsAt())) return "ACTIVITY_ENDED";
        if (template.getStatus() != CouponTemplateStatus.ACTIVE) return "ACTIVITY_PAUSED";
        String audience = eligibility.issueIneligibilityReason(userId, template, template.getDistributionType());
        if (audience != null) return audience;
        if (claimed >= template.getPerUserLimit()) return "USER_LIMIT_REACHED";
        if (template.getIssuedCount() >= template.getTotalIssueLimit()
                || remainingResponsibility(template).compareTo(maxLiability(template)) < 0) return "SOLD_OUT";
        return null;
    }

    private UserCouponSummaryView summary(UserCoupon c, LocalDateTime now) {
        CouponTemplate t = template(c.getTemplateId()); UserCouponStatus display = c.getStatus() == UserCouponStatus.AVAILABLE && !now.isBefore(c.getValidTo()) ? UserCouponStatus.EXPIRED : c.getStatus();
        return new UserCouponSummaryView(id(c.getId()), c.getCouponNo(), views.template(t), c.getStatus(), display, time(c.getValidFrom()), time(c.getValidTo()), claimSource(c.getId()), display == UserCouponStatus.AVAILABLE ? List.of("VIEW_ELIGIBLE_PRODUCTS", "USE") : List.of());
    }

    private UserCouponDetailView detail(UserCoupon c) {
        CouponTemplate template = template(c.getTemplateId());
        return views.detail(c, template, LocalDateTime.now(), claimSource(c.getId()), claimedAt(c.getId()),
                eligibility.useIneligibilityReason(c.getUserId(), template));
    }
    private UserCoupon ownedCoupon(long id, long userId) { UserCoupon c = userCouponMapper.selectOne(new LambdaQueryWrapper<UserCoupon>().eq(UserCoupon::getId, id).eq(UserCoupon::getUserId, userId)); if (c == null) throw notFound(); return c; }
    private CouponTemplate template(long id) { CouponTemplate t = templateMapper.selectById(id); if (t == null) throw notFound(); return t; }
    private CouponTemplate templateForUpdate(long id) { CouponTemplate t = templateMapper.selectOne(new LambdaQueryWrapper<CouponTemplate>().eq(CouponTemplate::getId,id).last("FOR UPDATE")); if (t == null) throw notFound(); return t; }
    private void validateIssuance(CouponTemplate template, Long activityId,
                                  CouponDistributionType source, boolean checkActivity) {
        if (template.getStatus() != CouponTemplateStatus.ACTIVE) {
            throw BusinessException.unprocessable("COUPON_TEMPLATE_NOT_CLAIMABLE", "优惠券模板当前不可发放");
        }
        LocalDateTime now = LocalDateTime.now();
        if ((source == CouponDistributionType.PUBLIC_CLAIM || source == CouponDistributionType.FLASH_CLAIM)
                && (template.getClaimStartsAt() == null || template.getClaimEndsAt() == null
                || now.isBefore(template.getClaimStartsAt()) || !now.isBefore(template.getClaimEndsAt()))) {
            throw BusinessException.unprocessable("COUPON_TEMPLATE_NOT_CLAIMABLE", "不在模板领取时间窗内");
        }
        if (activityId != null && !Objects.equals(template.getActivityId(), activityId)) throw notFound();
        Long linkedActivityId = activityId == null ? template.getActivityId() : activityId;
        if (linkedActivityId != null && !checkActivity) {
            CouponActivity activity = activityMapper.selectById(linkedActivityId);
            if (activity == null || activity.getStatus() != CouponActivityStatus.RUNNING
                    || now.isBefore(activity.getStartsAt()) || !now.isBefore(activity.getEndsAt())) {
                throw BusinessException.unprocessable("COUPON_ACTIVITY_NOT_CLAIMABLE", "关联活动当前不可发券");
            }
        }
    }
    private CouponActivity claimableActivityForUpdate(long activityId) {
        CouponActivity activity = activityMapper.selectOne(new LambdaQueryWrapper<CouponActivity>()
                .eq(CouponActivity::getId, activityId).last("FOR UPDATE"));
        if (activity == null || !visible(activity)) throw notFound();
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStatus() == CouponActivityStatus.PAUSED) {
            throw BusinessException.unprocessable("COUPON_ACTIVITY_NOT_CLAIMABLE", "活动已暂停");
        }
        if (activity.getStatus() == CouponActivityStatus.ENDED
                || activity.getStatus() == CouponActivityStatus.CANCELLED
                || !now.isBefore(activity.getEndsAt())) {
            throw BusinessException.unprocessable("COUPON_ACTIVITY_NOT_CLAIMABLE", "活动已结束");
        }
        if (now.isBefore(activity.getStartsAt())) {
            throw BusinessException.unprocessable("COUPON_ACTIVITY_NOT_CLAIMABLE", "活动尚未开始");
        }
        if (activity.getStatus() == CouponActivityStatus.SCHEDULED) {
            activity.setStatus(CouponActivityStatus.RUNNING);
            if (activityMapper.updateById(activity) != 1) {
                throw BusinessException.conflict("VERSION_CONFLICT", "活动状态已变化");
            }
        } else if (activity.getStatus() != CouponActivityStatus.RUNNING) {
            throw BusinessException.unprocessable("COUPON_ACTIVITY_NOT_CLAIMABLE", "活动当前不可领取");
        }
        if (activity.getShopId() != null) {
            Shop shop = shopMapper.selectById(activity.getShopId());
            if (shop == null || shop.getStatus() != org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus.ACTIVE) {
                throw BusinessException.unprocessable("COUPON_ACTIVITY_NOT_CLAIMABLE", "店铺状态不允许领取");
            }
        }
        return activity;
    }
    private boolean visible(CouponActivity a) {
        return (a.getStatus() == CouponActivityStatus.SCHEDULED
                || a.getStatus() == CouponActivityStatus.RUNNING
                || a.getStatus() == CouponActivityStatus.PAUSED)
                && LocalDateTime.now().isBefore(a.getEndsAt());
    }
    private long require(String permission) { currentUser.requirePermission(permission); return currentUser.id(); }
    private long authorizeGrant(Long shopId) {
        if (shopId == null) return require("platform:coupon:grant");
        shopAccess.require(shopId, "shop:coupon:grant");
        return currentUser.id();
    }
    private BusinessException notFound() { return BusinessException.notFound("RESOURCE_NOT_FOUND", "资源不存在"); }
    private long parseId(String v) { try { return Long.parseLong(v); } catch (RuntimeException ex) { throw BusinessException.badRequest("VALIDATION_FAILED", "ID 格式错误"); } }
    private String claimSource(long id) { ClaimRecord c = claimMapper.selectOne(new LambdaQueryWrapper<ClaimRecord>().eq(ClaimRecord::getUserCouponId, id)); return c == null ? null : c.getClaimSource().name(); }
    private LocalDateTime claimedAt(long id) { ClaimRecord c = claimMapper.selectOne(new LambdaQueryWrapper<ClaimRecord>().eq(ClaimRecord::getUserCouponId, id)); return c == null ? null : c.getCreatedAt(); }
    private String randomCode(int length) { StringBuilder b = new StringBuilder(length); java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current(); for (int i = 0; i < length; i++) b.append(ALPHABET.charAt(r.nextInt(ALPHABET.length()))); return b.toString(); }
    private String hash(String code) { try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(redeemSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")); return java.util.HexFormat.of().formatHex(mac.doFinal(code.trim().getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private int count(List<RedeemCode> rows, CouponRedeemCodeStatus status) { return (int) rows.stream().filter(r -> r.getStatus() == status).count(); }
    private void validatePage(long page, long pageSize) { if (page < 1 || pageSize < 1 || pageSize > 100) throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围"); }
    private void validateCouponSort(String sort) { if (sort != null && !List.of("validTo,asc","validTo,desc","createdAt,desc").contains(sort)) throw BusinessException.badRequest("BAD_REQUEST","不支持的排序字段"); }
}
