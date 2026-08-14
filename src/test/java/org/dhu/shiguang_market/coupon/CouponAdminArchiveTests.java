package org.dhu.shiguang_market.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponAudienceType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponDistributionType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponFundingType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponOwnerType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRestorePolicy;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponStackMode;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponTemplateStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponValidityType;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponTemplateAdminDetailView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.ScopeRequest;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.UpdateCouponPresentationRequest;
import org.dhu.shiguang_market.coupon.mapper.CouponActivityMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponFundingParticipationMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponScopeMappers;
import org.dhu.shiguang_market.coupon.mapper.CouponTemplateMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponTemplate;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponActivity;
import org.dhu.shiguang_market.coupon.service.CouponAdminService;
import org.dhu.shiguang_market.coupon.service.CouponAuditService;
import org.dhu.shiguang_market.coupon.service.CouponScheduleService;
import org.dhu.shiguang_market.coupon.service.CouponViewMapper;
import org.dhu.shiguang_market.product.mapper.ProductCategoryMapper;
import org.dhu.shiguang_market.product.mapper.ProductSkuMapper;
import org.dhu.shiguang_market.product.mapper.ProductSpuMapper;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;

class CouponAdminArchiveTests {
    private CouponActivityMapper activities;
    private CouponTemplateMapper templates;
    private CouponFundingParticipationMapper funding;
    private CurrentUserService currentUser;
    private CouponAdminService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(
                new MybatisConfiguration(), "coupon-admin-archive-test"), CouponTemplate.class);
        activities = mock(CouponActivityMapper.class);
        templates = mock(CouponTemplateMapper.class);
        CouponScopeMappers.Shop shopScopes = mock(CouponScopeMappers.Shop.class);
        CouponScopeMappers.Category categoryScopes = mock(CouponScopeMappers.Category.class);
        CouponScopeMappers.Spu spuScopes = mock(CouponScopeMappers.Spu.class);
        CouponScopeMappers.Sku skuScopes = mock(CouponScopeMappers.Sku.class);
        funding = mock(CouponFundingParticipationMapper.class);
        ShopMapper shops = mock(ShopMapper.class);
        ProductCategoryMapper categories = mock(ProductCategoryMapper.class);
        ProductSpuMapper spus = mock(ProductSpuMapper.class);
        ProductSkuMapper skus = mock(ProductSkuMapper.class);
        currentUser = mock(CurrentUserService.class);
        ShopAccessService shopAccess = mock(ShopAccessService.class);
        IdempotencyService idempotency = new IdempotencyService(null, null, 24) {
            @Override
            public <T> T execute(long userId, String method, String path, String key, Object request,
                                 Class<T> responseType, Supplier<T> action) {
                return action.get();
            }
        };
        CouponAuditService audit = mock(CouponAuditService.class);
        CouponScheduleService schedules = mock(CouponScheduleService.class);
        when(currentUser.id()).thenReturn(99L);
        when(funding.selectList(any())).thenReturn(List.of());
        service = new CouponAdminService(activities, templates, shopScopes, categoryScopes, spuScopes,
                skuScopes, funding, shops, categories, spus, skus, currentUser, shopAccess, idempotency,
                mock(org.dhu.shiguang_market.common.util.NumberGenerator.class), new CouponViewMapper(shops),
                audit, schedules);
    }

    @ParameterizedTest
    @EnumSource(value = CouponTemplateStatus.class, names = {"DRAFT", "ENDED"})
    void archivesAllowedTerminalSources(CouponTemplateStatus sourceStatus) {
        CouponTemplate template = template(sourceStatus);
        when(templates.selectOne(any())).thenReturn(template);
        when(templates.updateById(template)).thenReturn(1);
        when(templates.selectById(template.getId())).thenReturn(template);

        CouponTemplateAdminDetailView result = service.templateAction(
                null, template.getId(), "archive", "不再使用", template.getVersion(), "archive-1");

        assertThat(result.status()).isEqualTo(CouponTemplateStatus.valueOf("ARCHIVED"));
        assertThat(result.availableActions()).containsExactly("COPY");
        verify(templates).updateById(template);
    }

    @ParameterizedTest
    @EnumSource(value = CouponTemplateStatus.class, names = {"ACTIVE", "PAUSED", "ARCHIVED"})
    void rejectsArchiveFromOperationalOrArchivedState(CouponTemplateStatus sourceStatus) {
        CouponTemplate template = template(sourceStatus);
        when(templates.selectOne(any())).thenReturn(template);

        assertThatThrownBy(() -> service.templateAction(
                null, template.getId(), "archive", "不再使用", template.getVersion(), "archive-2"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("COUPON_TEMPLATE_STATE_CONFLICT"));
    }

    @Test
    void excludesArchivedTemplatesFromDefaultManagementList() {
        when(templates.selectPage(any(Page.class), any())).thenReturn(Page.of(1, 20));

        service.templates(null, null, null, null, null, null, 1, 20, null);

        ArgumentCaptor<LambdaQueryWrapper<CouponTemplate>> query = wrapperCaptor();
        verify(templates).selectPage(any(Page.class), query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("status <>");
        assertThat(query.getValue().getParamNameValuePairs()).containsValue(
                CouponTemplateStatus.valueOf("ARCHIVED"));
    }

    @Test
    void returnsArchivedTemplatesWhenExplicitlyRequested() {
        when(templates.selectPage(any(Page.class), any())).thenReturn(Page.of(1, 20));

        service.templates(null, null, CouponTemplateStatus.valueOf("ARCHIVED"),
                null, null, null, 1, 20, null);

        ArgumentCaptor<LambdaQueryWrapper<CouponTemplate>> query = wrapperCaptor();
        verify(templates).selectPage(any(Page.class), query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("status =").doesNotContain("status <>");
        assertThat(query.getValue().getParamNameValuePairs()).containsValue(
                CouponTemplateStatus.valueOf("ARCHIVED"));
    }

    @Test
    void excludesArchivedTemplatesOnlyFromActivityTemplateCount() {
        CouponActivity activity = activity();
        when(activities.selectOne(any())).thenReturn(activity);
        when(activities.selectMetrics(activity.getId())).thenReturn(
                new CouponActivityMapper.ActivityMetric(12, 7, new BigDecimal("35.00")));
        when(templates.selectCount(any())).thenReturn(3L);

        var result = service.activity(null, activity.getId());

        ArgumentCaptor<LambdaQueryWrapper<CouponTemplate>> query = wrapperCaptor();
        verify(templates).selectCount(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("status <>");
        assertThat(query.getValue().getParamNameValuePairs()).containsValue(
                CouponTemplateStatus.valueOf("ARCHIVED"));
        assertThat(result.templateCount()).isEqualTo(3);
        assertThat(result.issuedCount()).isEqualTo(12);
        assertThat(result.consumedCount()).isEqualTo(7);
        assertThat(result.couponDiscountAmount()).isEqualTo("35.00");
    }

    @Test
    void rejectsPresentationChangesAfterArchive() {
        CouponTemplate template = template(CouponTemplateStatus.valueOf("ARCHIVED"));
        when(templates.selectOne(any())).thenReturn(template);
        UpdateCouponPresentationRequest request = new UpdateCouponPresentationRequest();
        request.setCouponName("归档后改名");
        request.setVersion(template.getVersion());

        assertThatThrownBy(() -> service.presentation(null, template.getId(), request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("COUPON_TEMPLATE_STATE_CONFLICT"));
    }

    @Test
    void rejectsScopeChangesAfterArchive() {
        CouponTemplate template = template(CouponTemplateStatus.valueOf("ARCHIVED"));
        when(templates.selectOne(any())).thenReturn(template);
        ScopeRequest request = new ScopeRequest(CouponScopeType.ALL, null, null, null, null,
                template.getVersion());

        assertThatThrownBy(() -> service.replaceScope(null, template.getId(), request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("COUPON_TEMPLATE_STATE_CONFLICT"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<LambdaQueryWrapper<CouponTemplate>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }

    private static CouponTemplate template(CouponTemplateStatus status) {
        CouponTemplate template = new CouponTemplate();
        template.setId(3001L);
        template.setTemplateNo("CT202608140001");
        template.setOwnerType(CouponOwnerType.PLATFORM);
        template.setCouponName("测试优惠券");
        template.setCouponType(CouponType.CASH_RED_PACKET);
        template.setThresholdAmount(new BigDecimal("0.00"));
        template.setDiscountAmount(new BigDecimal("5.00"));
        template.setFundingType(CouponFundingType.PLATFORM);
        template.setPlatformShareRate(new BigDecimal("100.0000"));
        template.setScopeType(CouponScopeType.ALL);
        template.setDistributionType(CouponDistributionType.DIRECT_GRANT);
        template.setAudienceType(CouponAudienceType.ALL_USERS);
        template.setValidityType(CouponValidityType.RELATIVE_AFTER_CLAIM);
        template.setEffectiveDelayMinutes(0);
        template.setValidForHours(24);
        template.setTotalIssueLimit(100);
        template.setIssuedCount(0);
        template.setPerUserLimit(1);
        template.setStackMode(CouponStackMode.CROSS_OWNER);
        template.setRefundRestorePolicy(CouponRestorePolicy.FULL_TRADE_ONLY);
        template.setBudgetAmount(new BigDecimal("500.00"));
        template.setBudgetReservedAmount(new BigDecimal("0.00"));
        template.setBudgetConsumedAmount(new BigDecimal("0.00"));
        template.setBudgetReversedAmount(new BigDecimal("0.00"));
        template.setStatus(status);
        template.setSortOrder(0);
        template.setCreatedBy(99L);
        template.setUpdatedBy(99L);
        template.setVersion(2);
        template.setCreatedAt(LocalDateTime.now().minusDays(1));
        template.setUpdatedAt(LocalDateTime.now());
        return template;
    }

    private static CouponActivity activity() {
        CouponActivity activity = new CouponActivity();
        activity.setId(2001L);
        activity.setActivityNo("CA202608140001");
        activity.setOwnerType(CouponOwnerType.PLATFORM);
        activity.setActivityType(CouponActivityType.COUPON_CENTER);
        activity.setActivityName("测试活动");
        activity.setStartsAt(LocalDateTime.now().plusDays(1));
        activity.setEndsAt(LocalDateTime.now().plusDays(2));
        activity.setStatus(CouponActivityStatus.DRAFT);
        activity.setCreatedBy(99L);
        activity.setUpdatedBy(99L);
        activity.setVersion(1);
        activity.setCreatedAt(LocalDateTime.now().minusDays(1));
        activity.setUpdatedAt(LocalDateTime.now());
        return activity;
    }
}
