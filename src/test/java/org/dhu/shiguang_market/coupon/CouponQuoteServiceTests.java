package org.dhu.shiguang_market.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.dhu.shiguang_market.cart.model.CartItem;
import org.dhu.shiguang_market.cart.service.CartService.CheckoutLine;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponAudienceType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponDistributionType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponFundingType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponOwnerType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRestorePolicy;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponStackMode;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponSelectionMode;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponTemplateStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponType;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.UserCouponStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponValidityType;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CheckoutCouponSelection;
import org.dhu.shiguang_market.coupon.mapper.CouponScopeMappers;
import org.dhu.shiguang_market.coupon.mapper.CouponTemplateMapper;
import org.dhu.shiguang_market.coupon.mapper.UserCouponMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponTemplate;
import org.dhu.shiguang_market.coupon.model.CouponModels.UserCoupon;
import org.dhu.shiguang_market.coupon.service.CouponCalculator;
import org.dhu.shiguang_market.coupon.service.CouponEligibilityService;
import org.dhu.shiguang_market.coupon.service.CouponQuoteService;
import org.dhu.shiguang_market.coupon.service.CouponRateLimitService;
import org.dhu.shiguang_market.product.mapper.ProductCategoryMapper;
import org.dhu.shiguang_market.product.mapper.ProductSpuMapper;
import org.dhu.shiguang_market.product.model.ProductSpu;
import org.dhu.shiguang_market.product.model.ProductSku;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CouponQuoteServiceTests {
    private UserCouponMapper coupons;
    private CouponTemplateMapper templates;
    private StringRedisTemplate redis;
    private CouponRateLimitService limits;
    private CouponQuoteService service;
    private CheckoutLine line;

    @BeforeEach
    void setUp() {
        coupons = mock(UserCouponMapper.class);
        templates = mock(CouponTemplateMapper.class);
        CouponScopeMappers.Shop shops = mock(CouponScopeMappers.Shop.class);
        CouponScopeMappers.Category categories = mock(CouponScopeMappers.Category.class);
        CouponScopeMappers.Spu spus = mock(CouponScopeMappers.Spu.class);
        CouponScopeMappers.Sku skus = mock(CouponScopeMappers.Sku.class);
        ProductCategoryMapper categoryMapper = mock(ProductCategoryMapper.class);
        ProductSpuMapper spuMapper = mock(ProductSpuMapper.class);
        CouponEligibilityService eligibility = mock(CouponEligibilityService.class);
        redis = mock(StringRedisTemplate.class);
        limits = mock(CouponRateLimitService.class);
        service = new CouponQuoteService(coupons, templates, shops, categories, spus, skus,
                categoryMapper, spuMapper, new CouponCalculator(), eligibility, limits, redis,
                new ObjectMapper(), 5);
        CartItem cart = new CartItem(); cart.setId(1L); cart.setQuantity(2);
        ProductSku sku = new ProductSku(); sku.setId(11L); sku.setSpuId(21L); sku.setShopId(31L);
        sku.setSalePrice(new BigDecimal("100.00")); sku.setStatus(EnabledStatus.ENABLED);
        ProductSpu spu = new ProductSpu(); spu.setId(21L); spu.setShopId(31L); spu.setStatus(ProductStatus.ON_SHELF);
        org.dhu.shiguang_market.shop.model.Shop shop = new org.dhu.shiguang_market.shop.model.Shop();
        shop.setId(31L); shop.setStatus(ShopStatus.ACTIVE);
        line = new CheckoutLine(cart, sku, spu, shop, null, null, new BigDecimal("200.00"), true, null, null);
    }

    @Test
    void noneModeDoesNotTouchRateLimiterOrRedis() {
        CouponQuoteService.QuoteResult result = service.quote(7L, List.of(line),
                new CheckoutCouponSelection(CouponSelectionMode.NONE, null));
        assertThat(result.view()).isNull();
        assertThat(result.result().totalDiscountCents()).isZero();
        verify(limits, never()).quote(7L);
        verify(redis, never()).opsForValue();
    }

    @Test
    void invalidManualSelectionIsExplainedWithoutPersistingToken() {
        UserCoupon coupon = coupon(101L, UserCouponStatus.AVAILABLE);
        CouponTemplate template = template(201L, CouponType.THRESHOLD_REDUCTION);
        when(coupons.selectList(any())).thenReturn(List.of(coupon));
        when(templates.selectById(201L)).thenReturn(template);
        CouponQuoteService.QuoteResult result = service.quote(7L, List.of(line),
                new CheckoutCouponSelection(CouponSelectionMode.MANUAL, List.of("999")));
        assertThat(result.submittable()).isFalse();
        assertThat(result.view().quoteToken()).isNull();
        verify(redis, never()).opsForValue();
    }

    private UserCoupon coupon(long id, UserCouponStatus status) {
        UserCoupon c = new UserCoupon(); c.setId(id); c.setCouponNo("UC" + id); c.setTemplateId(201L); c.setUserId(7L);
        c.setStatus(status); c.setValidFrom(LocalDateTime.now().minusHours(1)); c.setValidTo(LocalDateTime.now().plusDays(1));
        c.setCreatedAt(LocalDateTime.now().minusHours(2)); return c;
    }

    private CouponTemplate template(long id, CouponType type) {
        CouponTemplate t = new CouponTemplate(); t.setId(id); t.setTemplateNo("CT" + id); t.setCouponName("满减");
        t.setOwnerType(CouponOwnerType.PLATFORM); t.setOwnerShopId(null); t.setCouponType(type);
        t.setThresholdAmount(new BigDecimal("100.00")); t.setDiscountAmount(new BigDecimal("10.00"));
        t.setFundingType(CouponFundingType.PLATFORM); t.setPlatformShareRate(new BigDecimal("100.0000"));
        t.setScopeType(CouponScopeType.ALL); t.setDistributionType(CouponDistributionType.PUBLIC_CLAIM);
        t.setAudienceType(CouponAudienceType.ALL_USERS); t.setValidityType(CouponValidityType.RELATIVE_AFTER_CLAIM);
        t.setEffectiveDelayMinutes(0); t.setValidForHours(24); t.setStatus(CouponTemplateStatus.ACTIVE);
        t.setStackMode(CouponStackMode.CROSS_OWNER); t.setRefundRestorePolicy(CouponRestorePolicy.NEVER);
        return t;
    }
}
