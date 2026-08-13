package org.dhu.shiguang_market.coupon.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponAudienceType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponDistributionType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponFundingParticipationStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponFundingType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponOwnerType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponTemplateStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponValidityType;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.Formatters;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponActivityAdminView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponActivityScheduleView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponFundingParticipationView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponFundingInvitationBatchView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponTemplateAdminDetailView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponTemplateAdminSummaryView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponScopeTargetView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponAdminScopeView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.ScopeView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.TemplateView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CreateCouponActivityRequest;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CreateRecurringCouponActivityRequest;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CreateCouponTemplateRequest;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.DecideCouponFundingRequest;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.ScopeRequest;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.SendFundingInvitationRequest;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.UpdateCouponActivityRequest;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.UpdateCouponActivityScheduleRequest;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.UpdateCouponPresentationRequest;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CopyCouponTemplateRequest;
import org.dhu.shiguang_market.coupon.mapper.CouponActivityMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponFundingParticipationMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponScopeMappers;
import org.dhu.shiguang_market.coupon.mapper.CouponTemplateMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.CategoryScope;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponActivity;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponTemplate;
import org.dhu.shiguang_market.coupon.model.CouponModels.FundingParticipation;
import org.dhu.shiguang_market.coupon.model.CouponModels.ShopScope;
import org.dhu.shiguang_market.coupon.model.CouponModels.SkuScope;
import org.dhu.shiguang_market.coupon.model.CouponModels.SpuScope;
import org.dhu.shiguang_market.identity.service.IdentityViewMapper;
import org.dhu.shiguang_market.product.mapper.ProductCategoryMapper;
import org.dhu.shiguang_market.product.mapper.ProductSkuMapper;
import org.dhu.shiguang_market.product.mapper.ProductSpuMapper;
import org.dhu.shiguang_market.product.model.ProductSku;
import org.dhu.shiguang_market.product.model.ProductSpu;
import org.dhu.shiguang_market.product.model.ProductCategory;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponAdminService {
    private final CouponActivityMapper activityMapper;
    private final CouponTemplateMapper templateMapper;
    private final CouponScopeMappers.Shop shopScopeMapper;
    private final CouponScopeMappers.Category categoryScopeMapper;
    private final CouponScopeMappers.Spu spuScopeMapper;
    private final CouponScopeMappers.Sku skuScopeMapper;
    private final CouponFundingParticipationMapper fundingMapper;
    private final ShopMapper shopMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final CurrentUserService currentUser;
    private final ShopAccessService shopAccess;
    private final IdempotencyService idempotency;
    private final NumberGenerator numbers;
    private final CouponViewMapper views;
    private final CouponAuditService audit;
    private final CouponScheduleService schedules;

    public CouponAdminService(CouponActivityMapper activityMapper, CouponTemplateMapper templateMapper,
                              CouponScopeMappers.Shop shopScopeMapper, CouponScopeMappers.Category categoryScopeMapper,
                              CouponScopeMappers.Spu spuScopeMapper, CouponScopeMappers.Sku skuScopeMapper,
                              CouponFundingParticipationMapper fundingMapper, ShopMapper shopMapper,
                              ProductCategoryMapper categoryMapper, ProductSpuMapper spuMapper,
                              ProductSkuMapper skuMapper, CurrentUserService currentUser,
                              ShopAccessService shopAccess, IdempotencyService idempotency,
                              NumberGenerator numbers, CouponViewMapper views, CouponAuditService audit,
                              CouponScheduleService schedules) {
        this.activityMapper = activityMapper;
        this.templateMapper = templateMapper;
        this.shopScopeMapper = shopScopeMapper;
        this.categoryScopeMapper = categoryScopeMapper;
        this.spuScopeMapper = spuScopeMapper;
        this.skuScopeMapper = skuScopeMapper;
        this.fundingMapper = fundingMapper;
        this.shopMapper = shopMapper;
        this.categoryMapper = categoryMapper;
        this.spuMapper = spuMapper;
        this.skuMapper = skuMapper;
        this.currentUser = currentUser;
        this.shopAccess = shopAccess;
        this.idempotency = idempotency;
        this.numbers = numbers;
        this.views = views;
        this.audit = audit;
        this.schedules = schedules;
    }

    public PageView<CouponActivityAdminView> activities(Long shopId, CouponActivityStatus status,
                                                        CouponActivityType activityType, String keyword,
                                                        LocalDateTime createdFrom, LocalDateTime createdTo,
                                                        long page, long pageSize, String sort) {
        authorize(shopId, false);
        validatePage(page, pageSize);
        LambdaQueryWrapper<CouponActivity> query = activityQuery(status, activityType, keyword,
                createdFrom, createdTo);
        query.eq(shopId != null, CouponActivity::getShopId, shopId)
                .eq(shopId == null, CouponActivity::getOwnerType, CouponOwnerType.PLATFORM);
        applyActivitySort(query, sort);
        Page<CouponActivity> result = activityMapper.selectPage(Page.of(page, pageSize), query);
        return PageView.of(result, result.getRecords().stream().map(this::activityView).toList());
    }

    public PageView<CouponActivityAdminView> operationActivities(CouponOwnerType ownerType, Long shopId,
                                                                 CouponActivityStatus status, String keyword,
                                                                 long page, long pageSize) {
        platform("platform:coupon:read");
        validatePage(page, pageSize);
        LambdaQueryWrapper<CouponActivity> query = activityQuery(status, null, keyword, null, null)
                .eq(ownerType != null, CouponActivity::getOwnerType, ownerType)
                .eq(shopId != null, CouponActivity::getShopId, shopId)
                .orderByDesc(CouponActivity::getCreatedAt)
                .orderByDesc(CouponActivity::getId);
        Page<CouponActivity> result = activityMapper.selectPage(Page.of(page, pageSize), query);
        return PageView.of(result, result.getRecords().stream().map(this::activityView).toList());
    }

    @Transactional
    public CouponActivityAdminView createActivity(Long shopId, CreateCouponActivityRequest request, String key) {
        long operator = authorize(shopId, true);
        String path = activityPath(shopId);
        return idempotency.execute(operator, "POST", path, key, request, CouponActivityAdminView.class, () -> {
            validateTime(request.startsAt().toLocalDateTime(), request.endsAt().toLocalDateTime());
            CouponActivity a = new CouponActivity();
            a.setActivityNo(numbers.next("CA"));
            a.setOwnerType(shopId == null ? CouponOwnerType.PLATFORM : CouponOwnerType.SHOP);
            a.setShopId(shopId);
            a.setActivityType(request.activityType());
            a.setActivityName(request.activityName().trim());
            a.setSubtitle(Formatters.trimToNull(request.subtitle()));
            a.setBannerUrl(Formatters.trimToNull(request.bannerUrl()));
            a.setStartsAt(request.startsAt().toLocalDateTime());
            a.setEndsAt(request.endsAt().toLocalDateTime());
            a.setStatus(CouponActivityStatus.DRAFT);
            a.setCreatedBy(operator);
            a.setUpdatedBy(operator);
            a.setVersion(0);
            activityMapper.insert(a);
            audit("ACTIVITY", a.getId(), "CREATE", shopId, operator,
                    null, a.getStatus().name(), null, null);
            return activityView(a);
        });
    }

    public CouponActivityAdminView activity(Long shopId, long id) {
        authorize(shopId, false);
        return activityView(activityRecord(shopId, id));
    }

    @Transactional
    public CouponActivityAdminView createRecurringActivity(Long shopId,
                                                            CreateRecurringCouponActivityRequest request,
                                                            String key) {
        long operator = authorize(shopId, true);
        String path = activityPath(shopId) + "/recurring";
        return idempotency.execute(operator, "POST", path, key, request, CouponActivityAdminView.class, () -> {
            var bounds = schedules.validate(request.recurrence());
            CouponActivity activity = new CouponActivity();
            activity.setActivityNo(numbers.next("CA"));
            activity.setOwnerType(shopId == null ? CouponOwnerType.PLATFORM : CouponOwnerType.SHOP);
            activity.setShopId(shopId);
            activity.setActivityType(CouponActivityType.FLASH_CLAIM);
            activity.setActivityName(request.activityName().trim());
            activity.setSubtitle(Formatters.trimToNull(request.subtitle()));
            activity.setBannerUrl(Formatters.trimToNull(request.bannerUrl()));
            activity.setStartsAt(bounds.startsAt().toLocalDateTime());
            activity.setEndsAt(bounds.endsAt().toLocalDateTime());
            activity.setStatus(CouponActivityStatus.DRAFT);
            activity.setCreatedBy(operator);
            activity.setUpdatedBy(operator);
            activity.setVersion(0);
            activityMapper.insert(activity);
            schedules.create(activity.getId(), request.recurrence());
            audit("ACTIVITY", activity.getId(), "CREATE_RECURRING", shopId, operator,
                    null, activity.getStatus().name(), java.util.Map.of("scheduleType", "RECURRING"), null);
            return activityView(activity);
        });
    }

    public CouponActivityScheduleView activitySchedule(Long shopId, long id) {
        authorize(shopId, false);
        return schedules.view(activityRecord(shopId, id));
    }

    @Transactional
    public CouponActivityScheduleView updateActivitySchedule(Long shopId, long id,
                                                              UpdateCouponActivityScheduleRequest request) {
        long operator = authorize(shopId, true);
        CouponActivity activity = activityRecord(shopId, id);
        version(activity.getVersion(), request.version());
        if (activity.getStatus() != CouponActivityStatus.DRAFT
                || activity.getActivityType() != CouponActivityType.FLASH_CLAIM
                || !schedules.isRecurring(id)
                || request.scheduleType() != org.dhu.shiguang_market.common.model.MarketEnums.CouponScheduleType.RECURRING) {
            state("COUPON_ACTIVITY_STATE_CONFLICT");
        }
        var bounds = schedules.validate(request.recurrence());
        var before = schedules.recurrence(id);
        int beforeVersion = nvl(activity.getVersion());
        activity.setStartsAt(bounds.startsAt().toLocalDateTime());
        activity.setEndsAt(bounds.endsAt().toLocalDateTime());
        activity.setUpdatedBy(operator);
        if (activityMapper.updateById(activity) != 1) versionConflict();
        schedules.replace(id, request.recurrence());
        audit("ACTIVITY", id, "UPDATE_SCHEDULE", shopId, operator,
                activity.getStatus().name(), activity.getStatus().name(),
                java.util.Map.of("beforeSchedule", before, "afterSchedule", request.recurrence(),
                        "beforeVersion", beforeVersion, "afterVersion", nvl(activity.getVersion())), null);
        return schedules.view(activityMapper.selectById(id));
    }

    @Transactional
    public CouponActivityAdminView updateActivity(Long shopId, long id, UpdateCouponActivityRequest request) {
        long operator = authorize(shopId, true);
        CouponActivity a = activityRecord(shopId, id);
        version(a.getVersion(), request.version());
        if (a.getStatus() != CouponActivityStatus.DRAFT) state("COUPON_ACTIVITY_STATE_CONFLICT");
        boolean recurring = schedules.isRecurring(id);
        if (recurring && (request.activityType() != a.getActivityType()
                || !request.startsAt().toLocalDateTime().equals(a.getStartsAt())
                || !request.endsAt().toLocalDateTime().equals(a.getEndsAt()))) {
            state("COUPON_ACTIVITY_STATE_CONFLICT");
        }
        validateTime(request.startsAt().toLocalDateTime(), request.endsAt().toLocalDateTime());
        a.setActivityName(request.activityName().trim());
        a.setSubtitle(Formatters.trimToNull(request.subtitle()));
        a.setBannerUrl(Formatters.trimToNull(request.bannerUrl()));
        if (!recurring) {
            a.setActivityType(request.activityType());
            a.setStartsAt(request.startsAt().toLocalDateTime());
            a.setEndsAt(request.endsAt().toLocalDateTime());
        }
        a.setUpdatedBy(operator);
        if (activityMapper.updateById(a) != 1) versionConflict();
        audit("ACTIVITY", id, "UPDATE", shopId, operator, null, a.getStatus().name(),
                java.util.Map.of("fields", "presentation,time,type"), null);
        return activityView(activityMapper.selectById(id));
    }

    @Transactional
    public CouponActivityAdminView activityAction(Long shopId, long id, String action, String reason, int version, String key, boolean governance) {
        long operator = governance ? platform("platform:coupon:governance") : authorize(shopId, true);
        String path = governance ? "/api/platform/coupon-governance/activities/" + id + "/" + action
                : activityPath(shopId) + "/" + id + "/" + action;
        return idempotency.execute(operator, "POST", path, key, List.of(action, reason == null ? "" : reason, version), CouponActivityAdminView.class, () -> {
            CouponActivity a = governance ? activityMapper.selectById(id) : activityRecord(shopId, id);
            if (a == null || (governance && a.getOwnerType() != CouponOwnerType.SHOP)) throw notFound();
            version(a.getVersion(), version);
            CouponActivityStatus from = a.getStatus();
            CouponActivityStatus to = switch (action) {
                case "publish" ->
                        from == CouponActivityStatus.DRAFT ? (LocalDateTime.now().isBefore(a.getStartsAt()) ? CouponActivityStatus.SCHEDULED : CouponActivityStatus.RUNNING) : null;
                case "pause" -> from == CouponActivityStatus.RUNNING ? CouponActivityStatus.PAUSED : null;
                case "resume" ->
                        from == CouponActivityStatus.PAUSED
                                ? (!LocalDateTime.now().isBefore(a.getEndsAt()) ? CouponActivityStatus.ENDED
                                : LocalDateTime.now().isBefore(a.getStartsAt())
                                ? CouponActivityStatus.SCHEDULED : CouponActivityStatus.RUNNING) : null;
                case "end" ->
                        Set.of(CouponActivityStatus.SCHEDULED, CouponActivityStatus.RUNNING, CouponActivityStatus.PAUSED).contains(from) ? CouponActivityStatus.ENDED : null;
                case "cancel" ->
                        from == CouponActivityStatus.DRAFT || from == CouponActivityStatus.SCHEDULED ? CouponActivityStatus.CANCELLED : null;
                default -> null;
            };
            if (to == null) state("COUPON_ACTIVITY_STATE_CONFLICT");
            if (action.equals("publish")) validateActivityPublish(a);
            if (Set.of("pause", "end", "cancel").contains(action)) requireReason(reason);
            if (governance && action.equals("resume")
                    && !"PLATFORM_GOVERNANCE".equals(a.getPauseSource())) {
                state("COUPON_ACTIVITY_STATE_CONFLICT");
            }
            if (!governance && action.equals("resume") && "PLATFORM_GOVERNANCE".equals(a.getPauseSource()))
                throw BusinessException.forbidden("COUPON_GOVERNANCE_REQUIRED", "需要平台治理解除暂停");
            a.setStatus(to);
            a.setUpdatedBy(operator);
            if (to == CouponActivityStatus.PAUSED) {
                a.setPauseSource(governance ? "PLATFORM_GOVERNANCE" : "OWNER");
                a.setPauseReason(requireReason(reason));
            } else {
                a.setPauseSource(null);
                a.setPauseReason(null);
            }
            if (activityMapper.updateById(a) != 1) versionConflict();
            audit("ACTIVITY", id, governance ? "GOVERNANCE_" + action.toUpperCase() : action.toUpperCase(),
                    a.getShopId(), operator, from.name(), to.name(), null, reason);
            return activityView(activityMapper.selectById(id));
        });
    }

    public PageView<CouponTemplateAdminSummaryView> templates(Long shopId, Long activityId,
                                                              CouponTemplateStatus status, CouponType couponType,
                                                              CouponDistributionType distributionType, String keyword,
                                                              long page, long pageSize, String sort) {
        authorize(shopId, false);
        validatePage(page, pageSize);
        LambdaQueryWrapper<CouponTemplate> query = templateQuery(status, couponType, keyword)
                .eq(activityId != null, CouponTemplate::getActivityId, activityId)
                .eq(distributionType != null, CouponTemplate::getDistributionType, distributionType)
                .eq(shopId != null, CouponTemplate::getOwnerShopId, shopId)
                .eq(shopId == null, CouponTemplate::getOwnerType, CouponOwnerType.PLATFORM);
        applyTemplateSort(query, sort);
        Page<CouponTemplate> result = templateMapper.selectPage(Page.of(page, pageSize), query);
        return PageView.of(result, result.getRecords().stream().map(this::templateSummary).toList());
    }

    public PageView<CouponTemplateAdminSummaryView> operationTemplates(CouponOwnerType ownerType, Long shopId,
                                                                       CouponTemplateStatus status,
                                                                       CouponType couponType, String keyword,
                                                                       long page, long pageSize) {
        platform("platform:coupon:read");
        validatePage(page, pageSize);
        LambdaQueryWrapper<CouponTemplate> query = templateQuery(status, couponType, keyword)
                .eq(ownerType != null, CouponTemplate::getOwnerType, ownerType)
                .eq(shopId != null, CouponTemplate::getOwnerShopId, shopId)
                .orderByDesc(CouponTemplate::getCreatedAt)
                .orderByDesc(CouponTemplate::getId);
        Page<CouponTemplate> result = templateMapper.selectPage(Page.of(page, pageSize), query);
        return PageView.of(result, result.getRecords().stream().map(this::templateSummary).toList());
    }

    @Transactional
    public CouponTemplateAdminDetailView createTemplate(Long shopId, CreateCouponTemplateRequest request, String key) {
        long operator = authorize(shopId, true);
        String path = templatePath(shopId);
        return idempotency.execute(operator, "POST", path, key, request, CouponTemplateAdminDetailView.class, () -> {
            CouponTemplate t = fromRequest(shopId, request, operator);
            validateTemplate(t, request.scope(), true);
            templateMapper.insert(t);
            replaceScope(t, request.scope());
            audit("TEMPLATE", t.getId(), "CREATE", shopId, operator, null, t.getStatus().name(),
                    java.util.Map.of("scopeType", t.getScopeType().name()), null);
            return templateView(t);
        });
    }

    public CouponTemplateAdminDetailView template(Long shopId, long id) {
        authorize(shopId, false);
        return templateView(templateRecord(shopId, id));
    }

    public PageView<CouponScopeTargetView> scopeTargets(Long shopId, long id, long page, long pageSize) {
        authorize(shopId, false);
        validatePage(page, pageSize);
        CouponTemplate template = templateRecord(shopId, id);
        return switch (template.getScopeType()) {
            case ALL -> new PageView<>(List.of(), page, pageSize, 0, 0);
            case SHOP -> {
                Page<ShopScope> result = shopScopeMapper.selectPage(Page.of(page, pageSize),
                        new LambdaQueryWrapper<ShopScope>().eq(ShopScope::getTemplateId, id)
                                .orderByAsc(ShopScope::getShopId));
                yield PageView.of(result, result.getRecords().stream().map(value -> {
                    Shop target = shopMapper.selectById(value.getShopId());
                    return new CouponScopeTargetView(CouponScopeType.SHOP, id(value.getShopId()),
                            target == null ? null : target.getShopNo(), target == null ? null : target.getShopName(),
                            id(value.getShopId()));
                }).toList());
            }
            case CATEGORY -> {
                Page<CategoryScope> result = categoryScopeMapper.selectPage(Page.of(page, pageSize),
                        new LambdaQueryWrapper<CategoryScope>().eq(CategoryScope::getTemplateId, id)
                                .orderByAsc(CategoryScope::getCategoryId));
                yield PageView.of(result, result.getRecords().stream().map(value -> {
                    ProductCategory target = categoryMapper.selectById(value.getCategoryId());
                    return new CouponScopeTargetView(CouponScopeType.CATEGORY, id(value.getCategoryId()),
                            target == null ? null : target.getCategoryCode(), target == null ? null : target.getCategoryName(), null);
                }).toList());
            }
            case SPU -> {
                Page<SpuScope> result = spuScopeMapper.selectPage(Page.of(page, pageSize),
                        new LambdaQueryWrapper<SpuScope>().eq(SpuScope::getTemplateId, id)
                                .orderByAsc(SpuScope::getSpuId));
                yield PageView.of(result, result.getRecords().stream().map(value -> {
                    ProductSpu target = spuMapper.selectById(value.getSpuId());
                    return new CouponScopeTargetView(CouponScopeType.SPU, id(value.getSpuId()),
                            target == null ? null : target.getSpuNo(), target == null ? null : target.getProductName(),
                            id(value.getShopId()));
                }).toList());
            }
            case SKU -> {
                Page<SkuScope> result = skuScopeMapper.selectPage(Page.of(page, pageSize),
                        new LambdaQueryWrapper<SkuScope>().eq(SkuScope::getTemplateId, id)
                                .orderByAsc(SkuScope::getSkuId));
                yield PageView.of(result, result.getRecords().stream().map(value -> {
                    ProductSku target = skuMapper.selectById(value.getSkuId());
                    return new CouponScopeTargetView(CouponScopeType.SKU, id(value.getSkuId()),
                            target == null ? null : target.getSkuNo(), target == null ? null : target.getSkuName(),
                            id(value.getShopId()));
                }).toList());
            }
        };
    }

    @Transactional
    public CouponTemplateAdminDetailView updateTemplate(Long shopId, long id,
                                                        org.dhu.shiguang_market.coupon.dto.CouponDtos.UpdateCouponTemplateRequest request) {
        long operator = authorize(shopId, true);
        CouponTemplate existing = templateRecord(shopId, id);
        version(existing.getVersion(), request.version());
        if (existing.getStatus() != CouponTemplateStatus.DRAFT) state("COUPON_TEMPLATE_STATE_CONFLICT");
        if (existing.getFirstIssuedAt() != null) {
            throw BusinessException.conflict("COUPON_TEMPLATE_RULES_IMMUTABLE", "已发行模板经济规则不可修改");
        }
        CreateCouponTemplateRequest create = new CreateCouponTemplateRequest(request.activityId(),
                request.couponName(), request.description(), request.couponType(), request.thresholdAmount(),
                request.discountAmount(), request.percentageOff(), request.maximumDiscountAmount(),
                request.ownerType(), request.fundingType(), request.platformShareRate(), request.scope(),
                request.distributionType(), request.audienceType(), request.newUserWithinDays(),
                request.claimStartsAt(), request.claimEndsAt(), request.validity(), request.totalIssueLimit(),
                request.perUserLimit(), request.stackMode(), request.refundRestorePolicy(),
                request.budgetAmount(), request.sortOrder());
        CouponTemplate replacement = fromRequest(shopId, create, operator);
        replacement.setId(existing.getId());
        replacement.setTemplateNo(existing.getTemplateNo());
        replacement.setCreatedBy(existing.getCreatedBy());
        replacement.setCreatedAt(existing.getCreatedAt());
        replacement.setVersion(existing.getVersion());
        validateTemplate(replacement, request.scope(), true);
        if (templateMapper.updateById(replacement) != 1) versionConflict();
        replaceScope(replacement, request.scope());
        audit("TEMPLATE", id, "UPDATE", shopId, operator, existing.getStatus().name(),
                replacement.getStatus().name(), java.util.Map.of("scopeType", replacement.getScopeType().name()), null);
        return templateView(templateMapper.selectById(id));
    }

    @Transactional
    public CouponTemplateAdminDetailView copyTemplate(Long shopId, long id, CopyCouponTemplateRequest request, String key) {
        long operator = authorize(shopId, true);
        CouponTemplate source = templateRecord(shopId, id);
        String path = templatePath(shopId) + "/" + id + "/copy";
        return idempotency.execute(operator, "POST", path, key, request, CouponTemplateAdminDetailView.class, () -> {
            version(source.getVersion(), request.version());
            CouponTemplate copy = new CouponTemplate();
            org.springframework.beans.BeanUtils.copyProperties(source, copy, "id", "templateNo", "status",
                    "issuedCount", "firstIssuedAt", "createdBy", "updatedBy", "createdAt", "updatedAt", "version");
            copy.setTemplateNo(numbers.next("CT"));
            copy.setCouponName(requireText(request.couponName(), 128));
            copy.setActivityId(request.hasActivityId() ? parseNullableId(request.activityId()) : source.getActivityId());
            copy.setStatus(CouponTemplateStatus.DRAFT);
            copy.setIssuedCount(0);
            copy.setBudgetReservedAmount(BigDecimal.ZERO.setScale(2));
            copy.setBudgetConsumedAmount(BigDecimal.ZERO.setScale(2));
            copy.setBudgetReversedAmount(BigDecimal.ZERO.setScale(2));
            copy.setCreatedBy(operator);
            copy.setUpdatedBy(operator);
            copy.setVersion(0);
            validateActivityRelation(copy);
            templateMapper.insert(copy);
            if (request.copyScope()) replaceScope(copy, currentScope(source));
            audit("TEMPLATE", copy.getId(), "COPY", shopId, operator, null, copy.getStatus().name(),
                    java.util.Map.of("sourceTemplateId", id), null);
            return templateView(copy);
        });
    }

    @Transactional
    public CouponTemplateAdminDetailView replaceScope(Long shopId, long id, ScopeRequest scope) {
        authorize(shopId, true);
        CouponTemplate t = templateRecord(shopId, id);
        version(t.getVersion(), scope.version());
        if (t.getFirstIssuedAt() != null)
            throw BusinessException.conflict("COUPON_TEMPLATE_RULES_IMMUTABLE", "已发行模板的范围不可修改");
        t.setScopeType(scope.scopeType());
        validateScope(t, scope);
        validateFunding(t);
        if (templateMapper.updateById(t) != 1) versionConflict();
        replaceScope(t, scope);
        invalidateFunding(t.getId());
        audit("TEMPLATE", id, "SCOPE_UPDATE", shopId, currentUser.id(), t.getStatus().name(),
                t.getStatus().name(), java.util.Map.of("scopeType", scope.scopeType().name()), null);
        return templateView(templateMapper.selectById(id));
    }

    @Transactional
    public CouponTemplateAdminDetailView presentation(Long shopId, long id, UpdateCouponPresentationRequest request) {
        authorize(shopId, true);
        CouponTemplate t = templateRecord(shopId, id);
        version(t.getVersion(), request.version());
        if (!request.hasCouponName() && !request.hasDescription() && !request.hasSortOrder())
            throw BusinessException.badRequest("VALIDATION_FAILED", "至少提交一个展示字段");
        if (request.hasCouponName()) t.setCouponName(requireText(request.couponName(), 128));
        if (request.hasDescription()) t.setDescription(Formatters.trimToNull(request.description()));
        if (request.hasSortOrder()) {
            if (request.sortOrder() == null)
                throw BusinessException.badRequest("VALIDATION_FAILED", "sortOrder 不允许为 null");
            t.setSortOrder(request.sortOrder());
        }
        if (templateMapper.updateById(t) != 1) versionConflict();
        audit("TEMPLATE", id, "PRESENTATION_UPDATE", shopId, currentUser.id(), t.getStatus().name(),
                t.getStatus().name(), java.util.Map.of("couponName", request.hasCouponName(),
                        "description", request.hasDescription(), "sortOrder", request.hasSortOrder()), null);
        return templateView(templateMapper.selectById(id));
    }

    @Transactional
    public CouponTemplateAdminDetailView templateAction(Long shopId, long id, String action, String reason, int version, String key) {
        long operator = authorize(shopId, true);
        String path = templatePath(shopId) + "/" + id + "/" + action;
        return idempotency.execute(operator, "POST", path, key, List.of(action, reason == null ? "" : reason, version), CouponTemplateAdminDetailView.class, () -> {
            CouponTemplate t = templateRecord(shopId, id);
            version(t.getVersion(), version);
            CouponTemplateStatus from = t.getStatus();
            CouponTemplateStatus to = switch (action) {
                case "activate", "resume" ->
                        (from == CouponTemplateStatus.DRAFT || from == CouponTemplateStatus.PAUSED) ? CouponTemplateStatus.ACTIVE : null;
                case "pause" -> from == CouponTemplateStatus.ACTIVE ? CouponTemplateStatus.PAUSED : null;
                case "end" ->
                        (from == CouponTemplateStatus.ACTIVE || from == CouponTemplateStatus.PAUSED) ? CouponTemplateStatus.ENDED : null;
                default -> null;
            };
            if (to == null) state("COUPON_TEMPLATE_STATE_CONFLICT");
            if ((action.equals("pause") || action.equals("end"))) requireReason(reason);
            if (to == CouponTemplateStatus.ACTIVE) validateTemplate(t, currentScope(t), false);
            t.setStatus(to);
            t.setUpdatedBy(operator);
            if (templateMapper.updateById(t) != 1) versionConflict();
            audit("TEMPLATE", id, action.toUpperCase(), shopId, operator, from.name(), to.name(), null, reason);
            return templateView(templateMapper.selectById(id));
        });
    }

    @Transactional
    public List<CouponFundingParticipationView> invite(long templateId, SendFundingInvitationRequest request, String key) {
        long operator = platform("platform:coupon:manage");
        String path = "/api/platform/coupon-templates/" + templateId + "/funding-invitations";
        return idempotency.execute(operator, "POST", path, key, request, CouponFundingInvitationBatchView.class, () -> {
            CouponTemplate t = templateRecord(null, templateId);
            version(t.getVersion(), request.version());
            if (t.getFundingType() != CouponFundingType.SHARED || t.getFirstIssuedAt() != null)
                throw BusinessException.conflict("COUPON_FUNDING_ALREADY_FROZEN", "联合承担关系不可修改");
            if (t.getStatus() != CouponTemplateStatus.DRAFT) state("COUPON_TEMPLATE_STATE_CONFLICT");
            Set<Long> expected = targetShops(t);
            Set<Long> submitted = new HashSet<>(request.shopIds().stream().map(this::parseId).toList());
            if (!expected.equals(submitted))
                throw BusinessException.unprocessable("COUPON_FUNDING_PARTICIPATION_INCOMPLETE", "邀请店铺集合与范围不一致");
            List<CouponFundingParticipationView> result = new ArrayList<>();
            for (Long shopId : expected.stream().sorted().toList()) {
                FundingParticipation p = fundingMapper.selectOne(new LambdaQueryWrapper<FundingParticipation>().eq(FundingParticipation::getTemplateId, templateId).eq(FundingParticipation::getShopId, shopId));
                if (p == null) {
                    p = new FundingParticipation();
                    p.setTemplateId(templateId);
                    p.setShopId(shopId);
                    p.setCreatedAt(LocalDateTime.now());
                }
                p.setPlatformShareRate(t.getPlatformShareRate());
                p.setStatus(CouponFundingParticipationStatus.PENDING);
                p.setInvitedBy(operator);
                p.setInvitedAt(LocalDateTime.now());
                p.setDecidedBy(null);
                p.setDecidedAt(null);
                p.setDecisionReason(null);
                p.setVersion(0);
                if (p.getId() == null) fundingMapper.insert(p);
                else fundingMapper.updateById(p);
                result.add(fundingView(p, t));
                audit("FUNDING_PARTICIPATION", p.getId(), "INVITE", shopId, operator, null,
                        p.getStatus().name(), java.util.Map.of("templateId", templateId), null);
            }
            return new CouponFundingInvitationBatchView(result);
        }).items();
    }

    public PageView<CouponFundingParticipationView> invitations(long shopId,
                                                                CouponFundingParticipationStatus status,
                                                                long page, long pageSize) {
        shopAccess.require(shopId, "shop:coupon:funding:approve");
        validatePage(page, pageSize);
        Page<FundingParticipation> result = fundingMapper.selectPage(Page.of(page, pageSize),
                new LambdaQueryWrapper<FundingParticipation>()
                        .eq(FundingParticipation::getShopId, shopId)
                        .eq(status != null, FundingParticipation::getStatus, status)
                        .orderByDesc(FundingParticipation::getInvitedAt)
                        .orderByDesc(FundingParticipation::getId));
        return PageView.of(result, result.getRecords().stream()
                .map(participation -> fundingView(participation,
                        templateMapper.selectById(participation.getTemplateId())))
                .toList());
    }

    @Transactional
    public CouponFundingParticipationView decide(long shopId, long id, DecideCouponFundingRequest request, String key) {
        shopAccess.require(shopId, "shop:coupon:funding:approve");
        long operator = currentUser.id();
        String path = "/api/shops/" + shopId + "/coupon-funding-invitations/" + id + "/decide";
        return idempotency.execute(operator, "POST", path, key, request, CouponFundingParticipationView.class, () -> {
            FundingParticipation p = fundingMapper.selectOne(new LambdaQueryWrapper<FundingParticipation>().eq(FundingParticipation::getId, id).eq(FundingParticipation::getShopId, shopId));
            if (p == null) throw notFound();
            version(p.getVersion(), request.version());
            CouponTemplate t = templateMapper.selectById(p.getTemplateId());
            if (t.getFirstIssuedAt() != null)
                throw BusinessException.conflict("COUPON_FUNDING_ALREADY_FROZEN", "联合承担已冻结");
            if (p.getStatus() != CouponFundingParticipationStatus.PENDING) state("COUPON_TEMPLATE_STATE_CONFLICT");
            if (request.decision() == org.dhu.shiguang_market.common.model.MarketEnums.CouponFundingDecision.REJECT) {
                p.setDecisionReason(requireReason(request.reason()));
                p.setStatus(CouponFundingParticipationStatus.REJECTED);
            } else {
                if (Formatters.trimToNull(request.reason()) != null)
                    throw BusinessException.badRequest("VALIDATION_FAILED", "接受时 reason 必须为空");
                p.setDecisionReason(null);
                p.setStatus(CouponFundingParticipationStatus.ACCEPTED);
            }
            p.setDecidedBy(operator);
            p.setDecidedAt(LocalDateTime.now());
            fundingMapper.updateById(p);
            audit("FUNDING_PARTICIPATION", p.getId(), "DECIDE", shopId, operator,
                    CouponFundingParticipationStatus.PENDING.name(), p.getStatus().name(), null, request.reason());
            return fundingView(p, t);
        });
    }

    private CouponTemplate fromRequest(Long shopId, CreateCouponTemplateRequest r, long operator) {
        validatePathControlledFields(shopId, r);
        CouponTemplate t = new CouponTemplate();
        t.setTemplateNo(numbers.next("CT"));
        t.setActivityId(parseNullableId(r.activityId()));
        t.setOwnerType(shopId == null ? CouponOwnerType.PLATFORM : CouponOwnerType.SHOP);
        t.setOwnerShopId(shopId);
        t.setCouponName(requireText(r.couponName(), 128));
        t.setDescription(Formatters.trimToNull(r.description()));
        t.setCouponType(r.couponType());
        t.setThresholdAmount(decimal(r.thresholdAmount(), 2));
        t.setDiscountAmount(decimalNullable(r.discountAmount(), 2));
        t.setPercentageOff(decimalNullable(r.percentageOff(), 2));
        t.setMaximumDiscountAmount(decimalNullable(r.maximumDiscountAmount(), 2));
        t.setFundingType(shopId == null ? r.fundingType() : CouponFundingType.SHOP);
        t.setPlatformShareRate(shopId == null ? decimal(r.platformShareRate(), 4) : new BigDecimal("0.0000"));
        t.setScopeType(r.scope().scopeType());
        t.setDistributionType(r.distributionType());
        t.setAudienceType(r.audienceType());
        t.setNewUserWithinDays(r.newUserWithinDays());
        t.setClaimStartsAt(local(r.claimStartsAt()));
        t.setClaimEndsAt(local(r.claimEndsAt()));
        t.setValidityType(r.validity().validityType());
        t.setValidFrom(local(r.validity().validFrom()));
        t.setValidTo(local(r.validity().validTo()));
        t.setEffectiveDelayMinutes(r.validity().effectiveDelayMinutes());
        t.setValidForHours(r.validity().validForHours());
        t.setTotalIssueLimit(r.totalIssueLimit());
        t.setIssuedCount(0);
        t.setPerUserLimit(r.perUserLimit());
        t.setStackMode(r.stackMode());
        t.setRefundRestorePolicy(r.refundRestorePolicy());
        t.setBudgetAmount(decimal(r.budgetAmount(), 2));
        t.setBudgetReservedAmount(BigDecimal.ZERO.setScale(2));
        t.setBudgetConsumedAmount(BigDecimal.ZERO.setScale(2));
        t.setBudgetReversedAmount(BigDecimal.ZERO.setScale(2));
        t.setStatus(CouponTemplateStatus.DRAFT);
        t.setSortOrder(r.sortOrder());
        t.setCreatedBy(operator);
        t.setUpdatedBy(operator);
        t.setVersion(0);
        return t;
    }

    private void validatePathControlledFields(Long shopId, CreateCouponTemplateRequest request) {
        if (shopId != null) {
            if (request.ownerType() != null || request.fundingType() != null
                    || request.platformShareRate() != null) {
                templateInvalid("店铺路径不接受归属或资金承担字段");
            }
            return;
        }
        if (request.ownerType() != CouponOwnerType.PLATFORM
                || request.fundingType() == null || request.platformShareRate() == null) {
            templateInvalid("平台模板必须明确提交平台归属和资金承担字段");
        }
    }

    private void validateTemplate(CouponTemplate template, ScopeRequest scope, boolean creating) {
        validateBenefit(template);
        validateFunding(template);
        validateDistributionAndAudience(template);
        validateValidity(template);
        validateActivityRelation(template);
        validateScope(template, scope);
        if (template.getTotalIssueLimit() == null || template.getTotalIssueLimit() < 1
                || template.getPerUserLimit() == null || template.getPerUserLimit() < 1
                || template.getPerUserLimit() > 99) {
            templateInvalid("发行量或个人限领范围无效");
        }
        BigDecimal liability = template.getCouponType() == CouponType.PERCENTAGE
                ? template.getMaximumDiscountAmount() : template.getDiscountAmount();
        if (template.getBudgetAmount() == null || template.getBudgetAmount().signum() <= 0
                || template.getBudgetAmount().compareTo(liability.multiply(
                BigDecimal.valueOf(template.getTotalIssueLimit()))) < 0) {
            throw BusinessException.unprocessable("COUPON_BUDGET_INSUFFICIENT", "预算小于最大责任");
        }
        if (!creating) validateActivationDependencies(template);
    }

    private void validateBenefit(CouponTemplate template) {
        BigDecimal threshold = template.getThresholdAmount();
        BigDecimal discount = template.getDiscountAmount();
        BigDecimal percentage = template.getPercentageOff();
        BigDecimal maximum = template.getMaximumDiscountAmount();
        boolean valid = switch (template.getCouponType()) {
            case PERCENTAGE -> threshold != null && threshold.signum() >= 0 && discount == null
                    && percentage != null && percentage.compareTo(new BigDecimal("0.01")) >= 0
                    && percentage.compareTo(new BigDecimal("99.99")) <= 0
                    && maximum != null && maximum.signum() > 0;
            case THRESHOLD_REDUCTION -> threshold != null && threshold.signum() > 0
                    && discount != null && discount.signum() > 0 && discount.compareTo(threshold) < 0
                    && percentage == null && maximum == null;
            case CASH_RED_PACKET -> threshold != null && threshold.signum() == 0
                    && discount != null && discount.signum() > 0 && percentage == null && maximum == null;
        };
        if (!valid) templateInvalid("券种金额字段组合无效");
    }

    private void validateFunding(CouponTemplate template) {
        BigDecimal rate = template.getPlatformShareRate();
        boolean valid = template.getOwnerType() == CouponOwnerType.SHOP
                ? template.getOwnerShopId() != null && template.getFundingType() == CouponFundingType.SHOP
                && BigDecimal.ZERO.compareTo(rate) == 0
                : template.getOwnerShopId() == null && switch (template.getFundingType()) {
            case PLATFORM -> new BigDecimal("100.0000").compareTo(rate) == 0;
            case SHOP -> BigDecimal.ZERO.compareTo(rate) == 0;
            case SHARED -> rate.compareTo(new BigDecimal("0.0001")) >= 0
                    && rate.compareTo(new BigDecimal("99.9999")) <= 0
                    && Set.of(CouponScopeType.SHOP, CouponScopeType.SPU, CouponScopeType.SKU)
                    .contains(template.getScopeType());
        };
        if (!valid) templateInvalid("归属、资金承担或平台比例组合无效");
    }

    private void validateDistributionAndAudience(CouponTemplate template) {
        boolean publicClaim = template.getDistributionType() == CouponDistributionType.PUBLIC_CLAIM
                || template.getDistributionType() == CouponDistributionType.FLASH_CLAIM;
        if (publicClaim) {
            if (template.getActivityId() == null || template.getClaimStartsAt() == null
                    || template.getClaimEndsAt() == null
                    || !template.getClaimEndsAt().isAfter(template.getClaimStartsAt())) {
                templateInvalid("公开领取模板必须关联活动并配置有效领取窗");
            }
        } else if (template.getClaimStartsAt() != null || template.getClaimEndsAt() != null) {
            templateInvalid("非公开发放模板不得配置领取窗");
        }
        if (template.getAudienceType() == CouponAudienceType.NEW_USERS) {
            if (template.getNewUserWithinDays() == null || template.getNewUserWithinDays() < 1
                    || template.getNewUserWithinDays() > 365) templateInvalid("新用户窗口必须为 1..365 天");
        } else if (template.getNewUserWithinDays() != null) {
            templateInvalid("非新用户人群不得配置新用户窗口");
        }
        if (template.getAudienceType() == CouponAudienceType.SPECIFIED_USERS
                && template.getDistributionType() != CouponDistributionType.DIRECT_GRANT) {
            templateInvalid("指定用户人群只允许定向发券");
        }
        if (template.getDistributionType() == CouponDistributionType.SYSTEM_GRANT
                && (template.getOwnerType() != CouponOwnerType.PLATFORM
                || template.getAudienceType() != CouponAudienceType.NEW_USERS)) {
            templateInvalid("系统发券只允许平台新用户模板");
        }
    }

    private void validateValidity(CouponTemplate template) {
        if (template.getValidityType() == CouponValidityType.FIXED_RANGE) {
            if (template.getValidFrom() == null || template.getValidTo() == null
                    || !template.getValidTo().isAfter(template.getValidFrom())
                    || template.getEffectiveDelayMinutes() != null || template.getValidForHours() != null) {
                templateInvalid("固定有效期字段组合无效");
            }
            if (template.getClaimEndsAt() != null
                    && template.getValidTo().isBefore(template.getClaimEndsAt().plusHours(1))) {
                templateInvalid("固定有效期结束时间必须至少晚于领取结束一小时");
            }
        } else if (template.getValidFrom() != null || template.getValidTo() != null
                || template.getEffectiveDelayMinutes() == null || template.getEffectiveDelayMinutes() < 0
                || template.getEffectiveDelayMinutes() > 10080 || template.getValidForHours() == null
                || template.getValidForHours() < 1 || template.getValidForHours() > 8760) {
            templateInvalid("相对有效期字段组合无效");
        }
    }

    private void validateActivityRelation(CouponTemplate template) {
        if (template.getActivityId() == null) return;
        CouponActivity activity = activityMapper.selectById(template.getActivityId());
        if (activity == null || activity.getOwnerType() != template.getOwnerType()
                || !Objects.equals(activity.getShopId(), template.getOwnerShopId())) {
            templateInvalid("活动不存在或与模板归属不一致");
        }
        if (activity.getStatus() == CouponActivityStatus.ENDED
                || activity.getStatus() == CouponActivityStatus.CANCELLED) {
            templateInvalid("结束或取消的活动不能关联模板");
        }
        if (template.getDistributionType() == CouponDistributionType.FLASH_CLAIM
                && activity.getActivityType() != CouponActivityType.FLASH_CLAIM) {
            templateInvalid("限时抢券模板必须关联限时抢券活动");
        }
        if (template.getDistributionType() == CouponDistributionType.SYSTEM_GRANT
                && activity.getActivityType() != CouponActivityType.NEW_USER_WELCOME) {
            templateInvalid("系统新客券只能关联新用户欢迎活动");
        }
        if (template.getClaimStartsAt() != null) {
            boolean invalidWindow = schedules.isRecurring(activity.getId())
                    ? template.getClaimStartsAt().isAfter(activity.getStartsAt())
                    || template.getClaimEndsAt().isBefore(activity.getEndsAt())
                    : template.getClaimStartsAt().isBefore(activity.getStartsAt())
                    || template.getClaimEndsAt().isAfter(activity.getEndsAt());
            if (invalidWindow) {
                templateInvalid(schedules.isRecurring(activity.getId())
                        ? "周期活动模板领取窗必须覆盖整个活动生命周期"
                        : "模板领取窗必须位于活动时间窗内");
            }
        }
    }

    private void validateActivationDependencies(CouponTemplate template) {
        if (template.getOwnerShopId() != null) requireActiveShop(template.getOwnerShopId());
        if (template.getFundingType() != CouponFundingType.SHARED) return;
        Set<Long> targets = targetShops(template);
        List<FundingParticipation> rows = fundingMapper.selectList(
                new LambdaQueryWrapper<FundingParticipation>()
                        .eq(FundingParticipation::getTemplateId, template.getId())
                        .orderByAsc(FundingParticipation::getShopId));
        Set<Long> accepted = new HashSet<>();
        for (FundingParticipation row : rows) {
            if (row.getStatus() == CouponFundingParticipationStatus.ACCEPTED
                    && row.getPlatformShareRate().compareTo(template.getPlatformShareRate()) == 0) {
                accepted.add(row.getShopId());
            }
        }
        if (!targets.equals(accepted)) {
            throw BusinessException.unprocessable("COUPON_FUNDING_PARTICIPATION_INCOMPLETE", "联合承担未全部接受");
        }
        targets.forEach(this::requireActiveShop);
    }

    private void validateActivityPublish(CouponActivity activity) {
        if (!LocalDateTime.now().isBefore(activity.getEndsAt())) {
            throw BusinessException.unprocessable("COUPON_TEMPLATE_INVALID", "活动结束时间已过");
        }
        if (activity.getShopId() != null) requireActiveShop(activity.getShopId());
        List<CouponTemplate> templates = templateMapper.selectList(new LambdaQueryWrapper<CouponTemplate>()
                .eq(CouponTemplate::getActivityId, activity.getId())
                .orderByAsc(CouponTemplate::getId));
        if (templates.isEmpty()) {
            throw BusinessException.unprocessable("COUPON_TEMPLATE_INVALID", "活动至少需要关联一张优惠券模板");
        }
        boolean recurring = schedules.isRecurring(activity.getId());
        if (activity.getActivityType() == CouponActivityType.FLASH_CLAIM && recurring) {
            var recurrence = schedules.recurrence(activity.getId());
            var bounds = recurrence == null ? null : schedules.validate(recurrence);
            if (bounds == null || !bounds.startsAt().toLocalDateTime().equals(activity.getStartsAt())
                    || !bounds.endsAt().toLocalDateTime().equals(activity.getEndsAt())) {
                throw BusinessException.unprocessable("COUPON_TEMPLATE_INVALID", "周期规则与活动生命周期不一致");
            }
        }
        for (CouponTemplate template : templates) {
            if (template.getStatus() != CouponTemplateStatus.ACTIVE) {
                throw BusinessException.unprocessable("COUPON_TEMPLATE_INVALID", "活动关联模板必须先激活");
            }
            if (recurring && (template.getDistributionType() != CouponDistributionType.FLASH_CLAIM
                    || template.getClaimStartsAt() == null || template.getClaimEndsAt() == null
                    || template.getClaimStartsAt().isAfter(activity.getStartsAt())
                    || template.getClaimEndsAt().isBefore(activity.getEndsAt()))) {
                throw BusinessException.unprocessable("COUPON_TEMPLATE_INVALID",
                        "周期抢券活动只能关联领取窗覆盖活动生命周期的限时抢券模板");
            }
        }
    }

    private void requireActiveShop(long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null || shop.getStatus() != ShopStatus.ACTIVE) {
            throw BusinessException.unprocessable("SHOP_COUPON_PUBLISH_NOT_ALLOWED", "店铺状态不允许发布");
        }
    }

    private void invalidateFunding(long templateId) {
        List<FundingParticipation> rows = fundingMapper.selectList(
                new LambdaQueryWrapper<FundingParticipation>()
                        .eq(FundingParticipation::getTemplateId, templateId));
        for (FundingParticipation row : rows) {
            row.setStatus(CouponFundingParticipationStatus.CANCELLED);
            row.setDecidedBy(null);
            row.setDecidedAt(null);
            row.setDecisionReason(null);
            fundingMapper.updateById(row);
        }
    }

    private void validateScope(CouponTemplate template, ScopeRequest scope) {
        if (scope == null || scope.scopeType() == null) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "scope 必填");
        }
        List<String> shops = safe(scope.shopIds());
        List<String> categories = safe(scope.categoryIds());
        List<String> spus = safe(scope.spuIds());
        List<String> skus = safe(scope.skuIds());
        List<String> selected = switch (scope.scopeType()) {
            case ALL -> List.of();
            case SHOP -> shops;
            case CATEGORY -> categories;
            case SPU -> spus;
            case SKU -> skus;
        };
        int suppliedGroups = (shops.isEmpty() ? 0 : 1) + (categories.isEmpty() ? 0 : 1)
                + (spus.isEmpty() ? 0 : 1) + (skus.isEmpty() ? 0 : 1);
        if ((scope.scopeType() == CouponScopeType.ALL && suppliedGroups != 0)
                || (scope.scopeType() != CouponScopeType.ALL && suppliedGroups != 1)) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "只能提交与 scopeType 对应的范围数组");
        }
        if (scope.scopeType() != CouponScopeType.ALL
                && (selected.isEmpty() || selected.size() > 1000)) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "范围目标必须为 1..1000 个");
        }
        if (scope.scopeType() == CouponScopeType.SHOP && template.getOwnerType() != CouponOwnerType.PLATFORM) {
            scopeInvalid();
        }
        for (Long targetId : selected.stream().map(this::parseId).distinct().toList()) {
            switch (scope.scopeType()) {
                case ALL -> {
                }
                case SHOP -> {
                    if (shopMapper.selectById(targetId) == null) scopeInvalid();
                }
                case CATEGORY -> {
                    if (categoryMapper.selectById(targetId) == null) scopeInvalid();
                }
                case SPU -> {
                    ProductSpu target = spuMapper.selectById(targetId);
                    if (target == null || template.getOwnerShopId() != null
                            && !template.getOwnerShopId().equals(target.getShopId())) scopeInvalid();
                }
                case SKU -> {
                    ProductSku target = skuMapper.selectById(targetId);
                    if (target == null || template.getOwnerShopId() != null
                            && !template.getOwnerShopId().equals(target.getShopId())) scopeInvalid();
                }
            }
        }
    }

    private void templateInvalid(String message) {
        throw BusinessException.unprocessable("COUPON_TEMPLATE_INVALID", message);
    }

    private void versionConflict() {
        throw BusinessException.conflict("VERSION_CONFLICT", "资源版本冲突");
    }

    private void audit(String resourceType, long resourceId, String operation, Long shopId,
                       long operator, String from, String to, java.util.Map<String, Object> changes,
                       String reason) {
        audit.log(resourceType, resourceId, operation,
                shopId == null ? OperatorType.PLATFORM : OperatorType.SHOP,
                operator, shopId, from, to, changes, Formatters.trimToNull(reason));
    }

    private void replaceScope(CouponTemplate t, ScopeRequest s) {
        shopScopeMapper.delete(new LambdaQueryWrapper<ShopScope>().eq(ShopScope::getTemplateId, t.getId()));
        categoryScopeMapper.delete(new LambdaQueryWrapper<CategoryScope>().eq(CategoryScope::getTemplateId, t.getId()));
        spuScopeMapper.delete(new LambdaQueryWrapper<SpuScope>().eq(SpuScope::getTemplateId, t.getId()));
        skuScopeMapper.delete(new LambdaQueryWrapper<SkuScope>().eq(SkuScope::getTemplateId, t.getId()));
        switch (s.scopeType()) {
            case ALL -> {
            }
            case SHOP -> safe(s.shopIds()).stream().map(this::parseId).distinct().forEach(x -> {
                if (shopMapper.selectById(x) == null) scopeInvalid();
                ShopScope v = new ShopScope();
                v.setTemplateId(t.getId());
                v.setShopId(x);
                shopScopeMapper.insert(v);
            });
            case CATEGORY -> safe(s.categoryIds()).stream().map(this::parseId).distinct().forEach(x -> {
                if (categoryMapper.selectById(x) == null) scopeInvalid();
                CategoryScope v = new CategoryScope();
                v.setTemplateId(t.getId());
                v.setCategoryId(x);
                categoryScopeMapper.insert(v);
            });
            case SPU -> safe(s.spuIds()).stream().map(this::parseId).distinct().forEach(x -> {
                ProductSpu p = spuMapper.selectById(x);
                if (p == null || (t.getOwnerShopId() != null && !t.getOwnerShopId().equals(p.getShopId())))
                    scopeInvalid();
                SpuScope v = new SpuScope();
                v.setTemplateId(t.getId());
                v.setSpuId(x);
                v.setShopId(p.getShopId());
                spuScopeMapper.insert(v);
            });
            case SKU -> safe(s.skuIds()).stream().map(this::parseId).distinct().forEach(x -> {
                ProductSku p = skuMapper.selectById(x);
                if (p == null || (t.getOwnerShopId() != null && !t.getOwnerShopId().equals(p.getShopId())))
                    scopeInvalid();
                SkuScope v = new SkuScope();
                v.setTemplateId(t.getId());
                v.setSkuId(x);
                v.setSpuId(p.getSpuId());
                v.setShopId(p.getShopId());
                skuScopeMapper.insert(v);
            });
        }
    }

    private ScopeRequest currentScope(CouponTemplate t) {
        return new ScopeRequest(t.getScopeType(), shopScopeMapper.selectList(new LambdaQueryWrapper<ShopScope>().eq(ShopScope::getTemplateId, t.getId())).stream().map(x -> id(x.getShopId())).toList(), categoryScopeMapper.selectList(new LambdaQueryWrapper<CategoryScope>().eq(CategoryScope::getTemplateId, t.getId())).stream().map(x -> id(x.getCategoryId())).toList(), spuScopeMapper.selectList(new LambdaQueryWrapper<SpuScope>().eq(SpuScope::getTemplateId, t.getId())).stream().map(x -> id(x.getSpuId())).toList(), skuScopeMapper.selectList(new LambdaQueryWrapper<SkuScope>().eq(SkuScope::getTemplateId, t.getId())).stream().map(x -> id(x.getSkuId())).toList(), t.getVersion());
    }

    private Set<Long> targetShops(CouponTemplate t) {
        return switch (t.getScopeType()) {
            case SHOP ->
                    new HashSet<>(shopScopeMapper.selectList(new LambdaQueryWrapper<ShopScope>().eq(ShopScope::getTemplateId, t.getId())).stream().map(ShopScope::getShopId).toList());
            case SPU ->
                    new HashSet<>(spuScopeMapper.selectList(new LambdaQueryWrapper<SpuScope>().eq(SpuScope::getTemplateId, t.getId())).stream().map(SpuScope::getShopId).toList());
            case SKU ->
                    new HashSet<>(skuScopeMapper.selectList(new LambdaQueryWrapper<SkuScope>().eq(SkuScope::getTemplateId, t.getId())).stream().map(SkuScope::getShopId).toList());
            default -> Set.of();
        };
    }

    private LambdaQueryWrapper<CouponActivity> activityQuery(CouponActivityStatus status,
                                                             CouponActivityType activityType, String keyword,
                                                             LocalDateTime createdFrom, LocalDateTime createdTo) {
        validateOptionalTimeRange(createdFrom, createdTo);
        String text = Formatters.trimToNull(keyword);
        LambdaQueryWrapper<CouponActivity> query = new LambdaQueryWrapper<CouponActivity>()
                .eq(status != null, CouponActivity::getStatus, status)
                .eq(activityType != null, CouponActivity::getActivityType, activityType)
                .ge(createdFrom != null, CouponActivity::getCreatedAt, createdFrom)
                .lt(createdTo != null, CouponActivity::getCreatedAt, createdTo);
        if (text != null) {
            query.and(value -> value.like(CouponActivity::getActivityNo, text)
                    .or().like(CouponActivity::getActivityName, text)
                    .or().like(CouponActivity::getSubtitle, text));
        }
        return query;
    }

    private LambdaQueryWrapper<CouponTemplate> templateQuery(CouponTemplateStatus status,
                                                             CouponType couponType, String keyword) {
        String text = Formatters.trimToNull(keyword);
        LambdaQueryWrapper<CouponTemplate> query = new LambdaQueryWrapper<CouponTemplate>()
                .eq(status != null, CouponTemplate::getStatus, status)
                .eq(couponType != null, CouponTemplate::getCouponType, couponType);
        if (text != null) {
            query.and(value -> value.like(CouponTemplate::getTemplateNo, text)
                    .or().like(CouponTemplate::getCouponName, text)
                    .or().like(CouponTemplate::getDescription, text));
        }
        return query;
    }

    private void applyActivitySort(LambdaQueryWrapper<CouponActivity> query, String sort) {
        switch (sort == null ? "createdAt,desc" : sort) {
            case "createdAt,desc" -> query.orderByDesc(CouponActivity::getCreatedAt).orderByDesc(CouponActivity::getId);
            case "createdAt,asc" -> query.orderByAsc(CouponActivity::getCreatedAt).orderByAsc(CouponActivity::getId);
            case "startsAt,asc" -> query.orderByAsc(CouponActivity::getStartsAt).orderByAsc(CouponActivity::getId);
            case "startsAt,desc" -> query.orderByDesc(CouponActivity::getStartsAt).orderByDesc(CouponActivity::getId);
            case "activityName,asc" ->
                    query.orderByAsc(CouponActivity::getActivityName).orderByAsc(CouponActivity::getId);
            default -> invalidSort();
        }
    }

    private void applyTemplateSort(LambdaQueryWrapper<CouponTemplate> query, String sort) {
        switch (sort == null ? "createdAt,desc" : sort) {
            case "createdAt,desc" -> query.orderByDesc(CouponTemplate::getCreatedAt).orderByDesc(CouponTemplate::getId);
            case "createdAt,asc" -> query.orderByAsc(CouponTemplate::getCreatedAt).orderByAsc(CouponTemplate::getId);
            case "sortOrder,asc" -> query.orderByAsc(CouponTemplate::getSortOrder).orderByAsc(CouponTemplate::getId);
            case "couponName,asc" -> query.orderByAsc(CouponTemplate::getCouponName).orderByAsc(CouponTemplate::getId);
            case "status,asc" -> query.orderByAsc(CouponTemplate::getStatus).orderByDesc(CouponTemplate::getId);
            default -> invalidSort();
        }
    }

    private void validateOptionalTimeRange(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw BusinessException.badRequest("INVALID_TIME_RANGE", "createdFrom 必须早于 createdTo");
        }
    }

    private void invalidSort() {
        throw BusinessException.badRequest("BAD_REQUEST", "不支持的排序字段");
    }

    private CouponActivityAdminView activityView(CouponActivity a) {
        Shop s = a.getShopId() == null ? null : shopMapper.selectById(a.getShopId());
        int count = Math.toIntExact(templateMapper.selectCount(new LambdaQueryWrapper<CouponTemplate>().eq(CouponTemplate::getActivityId, a.getId())));
        CouponActivityMapper.ActivityMetric metrics = activityMapper.selectMetrics(a.getId());
        long issued = metrics == null ? 0 : metrics.issuedCount();
        long consumed = metrics == null ? 0 : metrics.consumedCount();
        BigDecimal discount = metrics == null || metrics.couponDiscountAmount() == null ? BigDecimal.ZERO : metrics.couponDiscountAmount();
        return new CouponActivityAdminView(id(a.getId()), a.getActivityNo(), a.getOwnerType(), s == null ? null : IdentityViewMapper.shop(s), a.getActivityType(), a.getActivityName(), a.getSubtitle(), a.getBannerUrl(), time(a.getStartsAt()), time(a.getEndsAt()), a.getStatus(), a.getPauseSource(), a.getPauseReason(), count, Math.toIntExact(issued), Math.toIntExact(consumed), money(discount), nvl(a.getVersion()), id(a.getCreatedBy()), id(a.getUpdatedBy()), time(a.getCreatedAt()), time(a.getUpdatedAt()), activityActions(a));
    }

    private CouponTemplateAdminSummaryView templateSummary(CouponTemplate t) {
        Shop s = t.getOwnerShopId() == null ? null : shopMapper.selectById(t.getOwnerShopId());
        return new CouponTemplateAdminSummaryView(id(t.getId()), t.getTemplateNo(), t.getCouponName(), t.getOwnerType(), s == null ? null : IdentityViewMapper.shop(s), t.getCouponType(), t.getDistributionType(), t.getStatus(), t.getIssuedCount(), t.getTotalIssueLimit(), money(t.getBudgetAmount()), nvl(t.getVersion()));
    }

    private CouponTemplateAdminDetailView templateView(CouponTemplate t) {
        CouponTemplateAdminSummaryView summary = templateSummary(t);
        TemplateView template = views.template(t);
        CouponAdminScopeView scope = adminScope(t, template.scope());
        CouponActivity activity = t.getActivityId() == null ? null : activityMapper.selectById(t.getActivityId());
        List<CouponFundingParticipationView> funding = fundingMapper.selectList(
                new LambdaQueryWrapper<FundingParticipation>().eq(FundingParticipation::getTemplateId, t.getId())
                        .orderByAsc(FundingParticipation::getShopId)).stream().map(p -> fundingView(p, t)).toList();
        Shop ownerShop = t.getOwnerShopId() == null ? null : shopMapper.selectById(t.getOwnerShopId());
        return new CouponTemplateAdminDetailView(id(t.getId()), t.getTemplateNo(),
                activity == null ? null : activityView(activity), t.getOwnerType(),
                ownerShop == null ? null : IdentityViewMapper.shop(ownerShop), t.getCouponName(),
                t.getDescription(), t.getCouponType(), template.benefit(), t.getFundingType(),
                rate(t.getPlatformShareRate()), funding, scope, t.getDistributionType(),
                t.getAudienceType(), t.getNewUserWithinDays(), time(t.getClaimStartsAt()),
                time(t.getClaimEndsAt()), template.validity(), t.getTotalIssueLimit(),
                t.getIssuedCount(), t.getTotalIssueLimit() - t.getIssuedCount(), t.getPerUserLimit(),
                t.getStackMode(), t.getRefundRestorePolicy(), money(t.getBudgetAmount()),
                money(t.getBudgetReservedAmount()), money(t.getBudgetConsumedAmount()),
                money(t.getBudgetReversedAmount()), t.getStatus(), time(t.getFirstIssuedAt()),
                t.getSortOrder(), nvl(t.getVersion()), id(t.getCreatedBy()), id(t.getUpdatedBy()),
                time(t.getCreatedAt()), time(t.getUpdatedAt()), templateActions(t));
    }

    private CouponAdminScopeView adminScope(CouponTemplate t, ScopeView base) {
        int limit = 100;
        List<CouponScopeTargetView> targets;
        int count;
        switch (t.getScopeType()) {
            case ALL -> {
                count = 0;
                targets = List.of();
            }
            case SHOP -> {
                List<ShopScope> rows = shopScopeMapper.selectList(new LambdaQueryWrapper<ShopScope>()
                        .eq(ShopScope::getTemplateId, t.getId()).orderByAsc(ShopScope::getShopId));
                count = rows.size();
                targets = rows.stream().limit(limit).map(row -> {
                    Shop target = shopMapper.selectById(row.getShopId());
                    return new CouponScopeTargetView(CouponScopeType.SHOP, id(row.getShopId()),
                            target == null ? null : target.getShopNo(),
                            target == null ? null : target.getShopName(), id(row.getShopId()));
                }).toList();
            }
            case CATEGORY -> {
                List<CategoryScope> rows = categoryScopeMapper.selectList(new LambdaQueryWrapper<CategoryScope>()
                        .eq(CategoryScope::getTemplateId, t.getId()).orderByAsc(CategoryScope::getCategoryId));
                count = rows.size();
                targets = rows.stream().limit(limit).map(row -> {
                    ProductCategory target = categoryMapper.selectById(row.getCategoryId());
                    return new CouponScopeTargetView(CouponScopeType.CATEGORY, id(row.getCategoryId()),
                            target == null ? null : target.getCategoryCode(),
                            target == null ? null : target.getCategoryName(), null);
                }).toList();
            }
            case SPU -> {
                List<SpuScope> rows = spuScopeMapper.selectList(new LambdaQueryWrapper<SpuScope>()
                        .eq(SpuScope::getTemplateId, t.getId()).orderByAsc(SpuScope::getSpuId));
                count = rows.size();
                targets = rows.stream().limit(limit).map(row -> {
                    ProductSpu target = spuMapper.selectById(row.getSpuId());
                    return new CouponScopeTargetView(CouponScopeType.SPU, id(row.getSpuId()),
                            target == null ? null : target.getSpuNo(),
                            target == null ? null : target.getProductName(), id(row.getShopId()));
                }).toList();
            }
            case SKU -> {
                List<SkuScope> rows = skuScopeMapper.selectList(new LambdaQueryWrapper<SkuScope>()
                        .eq(SkuScope::getTemplateId, t.getId()).orderByAsc(SkuScope::getSkuId));
                count = rows.size();
                targets = rows.stream().limit(limit).map(row -> {
                    ProductSku target = skuMapper.selectById(row.getSkuId());
                    return new CouponScopeTargetView(CouponScopeType.SKU, id(row.getSkuId()),
                            target == null ? null : target.getSkuNo(),
                            target == null ? null : target.getSkuName(), id(row.getShopId()));
                }).toList();
            }
            default -> throw new IllegalStateException("unsupported scope type");
        }
        return new CouponAdminScopeView(base.scopeType(), base.summary(), targets, count);
    }

    private CouponFundingParticipationView fundingView(FundingParticipation p, CouponTemplate t) {
        Shop s = shopMapper.selectById(p.getShopId());
        return new CouponFundingParticipationView(id(p.getId()), id(t.getId()), t.getTemplateNo(), id(p.getShopId()), IdentityViewMapper.shop(s), rate(p.getPlatformShareRate()), rate(new BigDecimal("100.0000").subtract(p.getPlatformShareRate())), p.getStatus(), id(p.getInvitedBy()), time(p.getInvitedAt()), id(p.getDecidedBy()), time(p.getDecidedAt()), p.getDecisionReason(), nvl(p.getVersion()), p.getStatus() == CouponFundingParticipationStatus.PENDING ? List.of("ACCEPT", "REJECT") : List.of());
    }

    private long authorize(Long shopId, boolean write) {
        if (shopId == null) return platform(write ? "platform:coupon:manage" : "platform:coupon:read");
        shopAccess.require(shopId, write ? "shop:coupon:manage" : "shop:coupon:read");
        return currentUser.id();
    }

    private long platform(String p) {
        currentUser.requirePermission(p);
        return currentUser.id();
    }

    private CouponActivity activityRecord(Long shopId, long id) {
        CouponActivity a = activityMapper.selectOne(new LambdaQueryWrapper<CouponActivity>().eq(CouponActivity::getId, id).eq(shopId != null, CouponActivity::getShopId, shopId).eq(shopId == null, CouponActivity::getOwnerType, CouponOwnerType.PLATFORM));
        if (a == null) throw notFound();
        return a;
    }

    private CouponTemplate templateRecord(Long shopId, long id) {
        CouponTemplate t = templateMapper.selectOne(new LambdaQueryWrapper<CouponTemplate>().eq(CouponTemplate::getId, id).eq(shopId != null, CouponTemplate::getOwnerShopId, shopId).eq(shopId == null, CouponTemplate::getOwnerType, CouponOwnerType.PLATFORM));
        if (t == null) throw notFound();
        return t;
    }

    private List<String> activityActions(CouponActivity a) {
        return switch (a.getStatus()) {
            case DRAFT -> List.of("EDIT", "PUBLISH", "CANCEL");
            case SCHEDULED -> List.of("END", "CANCEL");
            case RUNNING -> List.of("PAUSE", "END");
            case PAUSED -> List.of("RESUME", "END");
            default -> List.of();
        };
    }

    private List<String> templateActions(CouponTemplate t) {
        return switch (t.getStatus()) {
            case DRAFT -> List.of("EDIT", "ACTIVATE", "COPY");
            case ACTIVE -> List.of("PAUSE", "END", "COPY");
            case PAUSED -> List.of("RESUME", "END", "COPY");
            case ENDED -> List.of("COPY");
        };
    }

    private String activityPath(Long id) {
        return id == null ? "/api/platform/coupon-activities" : "/api/shops/" + id + "/coupon-activities";
    }

    private String templatePath(Long id) {
        return id == null ? "/api/platform/coupon-templates" : "/api/shops/" + id + "/coupon-templates";
    }

    private <T> PageView<T> page(List<T> x, long p, long s) {
        return new PageView<>(x, p, s, x.size(), x.isEmpty() ? 0 : 1);
    }

    private void validatePage(long p, long s) {
        if (p < 1 || s < 1 || s > 100) throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
    }

    private void validateTime(LocalDateTime a, LocalDateTime b) {
        if (a == null || b == null || !b.isAfter(a))
            throw BusinessException.badRequest("VALIDATION_FAILED", "结束时间必须晚于开始时间");
    }

    private void version(Integer actual, int expected) {
        if (nvl(actual) != expected) throw BusinessException.conflict("VERSION_CONFLICT", "资源版本冲突");
    }

    private int nvl(Integer x) {
        return x == null ? 0 : x;
    }

    private String requireReason(String x) {
        return requireText(x, 500);
    }

    private String requireText(String x, int max) {
        String v = Formatters.trimToNull(x);
        if (v == null || v.length() > max) throw BusinessException.badRequest("VALIDATION_FAILED", "文本字段长度无效");
        return v;
    }

    private BigDecimal decimal(String x, int scale) {
        try {
            BigDecimal v = new BigDecimal(x);
            if (v.scale() > scale) throw new NumberFormatException();
            return v.setScale(scale);
        } catch (RuntimeException e) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "数值格式错误");
        }
    }

    private BigDecimal decimalNullable(String x, int scale) {
        return x == null ? null : decimal(x, scale);
    }

    private LocalDateTime local(java.time.OffsetDateTime x) {
        return x == null ? null : x.toLocalDateTime();
    }

    private Long parseNullableId(String x) {
        return x == null ? null : parseId(x);
    }

    private long parseId(String x) {
        try {
            return Long.parseLong(x);
        } catch (RuntimeException e) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "ID 格式错误");
        }
    }

    private List<String> safe(List<String> x) {
        return x == null ? List.of() : x;
    }

    private String rate(BigDecimal x) {
        return x == null ? null : x.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private void scopeInvalid() {
        throw BusinessException.unprocessable("COUPON_SCOPE_TARGET_INVALID", "范围目标无效");
    }

    private void state(String code) {
        throw BusinessException.conflict(code, "当前状态不允许该操作");
    }

    private BusinessException notFound() {
        return BusinessException.notFound("RESOURCE_NOT_FOUND", "资源不存在");
    }
}
