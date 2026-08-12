package org.dhu.shiguang_market.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponAudienceType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponDistributionType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponFundingType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponOwnerType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRestorePolicy;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponStackMode;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponTemplateStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponValidityType;
import org.dhu.shiguang_market.common.model.MarketEnums.UserCouponStatus;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponTemplate;
import org.dhu.shiguang_market.coupon.model.CouponModels.UserCoupon;
import org.dhu.shiguang_market.coupon.service.CouponViewMapper;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.junit.jupiter.api.Test;

class CouponViewMapperTests {
    @Test
    void exposesStableReasonsForNonAvailableCoupons() {
        CouponViewMapper mapper = new CouponViewMapper(mock(ShopMapper.class));
        CouponTemplate template = template();
        UserCoupon coupon = coupon(UserCouponStatus.USED);
        assertThat(mapper.detail(coupon, template, LocalDateTime.now(), "PUBLIC_CLAIM", LocalDateTime.now(), null)
                .unavailableReason()).isEqualTo("COUPON_ALREADY_USED");
        coupon.setStatus(UserCouponStatus.AVAILABLE);
        coupon.setValidFrom(LocalDateTime.now().plusHours(1));
        assertThat(mapper.detail(coupon, template, LocalDateTime.now(), "PUBLIC_CLAIM", LocalDateTime.now(), null)
                .unavailableReason()).isEqualTo("NOT_EFFECTIVE");
        coupon.setValidFrom(LocalDateTime.now().minusHours(1));
        assertThat(mapper.detail(coupon, template, LocalDateTime.now(), "PUBLIC_CLAIM", LocalDateTime.now(), "ACCOUNT_UNAVAILABLE")
                .unavailableReason()).isEqualTo("ACCOUNT_UNAVAILABLE");
    }

    @Test
    void buyerTemplateScopeDoesNotExposeManagementTargets() {
        CouponViewMapper mapper = new CouponViewMapper(mock(ShopMapper.class));
        assertThat(mapper.template(template()).scope().shopIds()).isNull();
        assertThat(mapper.template(template()).scope().categoryIds()).isNull();
        assertThat(mapper.template(template()).scope().spuIds()).isNull();
        assertThat(mapper.template(template()).scope().skuIds()).isNull();
    }

    private CouponTemplate template() {
        CouponTemplate t = new CouponTemplate();
        t.setId(1L); t.setTemplateNo("CT1"); t.setOwnerType(CouponOwnerType.PLATFORM); t.setCouponName("coupon");
        t.setCouponType(CouponType.CASH_RED_PACKET); t.setThresholdAmount(java.math.BigDecimal.ZERO);
        t.setDiscountAmount(new java.math.BigDecimal("5.00")); t.setFundingType(CouponFundingType.PLATFORM);
        t.setPlatformShareRate(new java.math.BigDecimal("100.0000")); t.setScopeType(CouponScopeType.ALL);
        t.setValidityType(CouponValidityType.RELATIVE_AFTER_CLAIM); t.setValidForHours(24);
        t.setStatus(CouponTemplateStatus.ACTIVE); t.setDistributionType(CouponDistributionType.PUBLIC_CLAIM);
        t.setAudienceType(CouponAudienceType.ALL_USERS); t.setStackMode(CouponStackMode.CROSS_OWNER);
        t.setRefundRestorePolicy(CouponRestorePolicy.NEVER); return t;
    }

    private UserCoupon coupon(UserCouponStatus status) {
        UserCoupon c = new UserCoupon(); c.setId(2L); c.setCouponNo("UC2"); c.setTemplateId(1L); c.setUserId(3L);
        c.setStatus(status); c.setValidFrom(LocalDateTime.now().minusHours(2)); c.setValidTo(LocalDateTime.now().plusDays(1));
        c.setRestoreCount(0); return c;
    }
}
