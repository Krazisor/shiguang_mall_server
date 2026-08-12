package org.dhu.shiguang_market.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponOwnerType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponStackMode;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponType;
import org.dhu.shiguang_market.coupon.service.CouponCalculator;
import org.dhu.shiguang_market.coupon.service.CouponCalculator.Candidate;
import org.dhu.shiguang_market.coupon.service.CouponCalculator.Line;
import org.junit.jupiter.api.Test;

class CouponCalculatorTests {
    private final CouponCalculator calculator = new CouponCalculator();

    @Test
    void appliesShopCouponBeforePlatformCouponAndAllocatesExactly() {
        List<Line> lines = List.of(
                new Line(1L, 10L, 101L, 1001L, 24000),
                new Line(2L, 20L, 201L, 2001L, 10000));
        Candidate shop = coupon(11L, CouponOwnerType.SHOP, 10L,
                CouponType.THRESHOLD_REDUCTION, 20000, 3000L, null, null);
        Candidate platform = coupon(12L, CouponOwnerType.PLATFORM, null,
                CouponType.PERCENTAGE, 0, null, new BigDecimal("10.00"), 5000L);

        var result = calculator.calculate(lines, List.of(shop, platform));

        assertThat(result.totalDiscountCents()).isEqualTo(6100);
        assertThat(result.payableCents()).isEqualTo(27900);
        assertThat(result.coupons()).extracting(CouponCalculator.Applied::discountCents)
                .containsExactly(3000L, 3100L);
        assertThat(result.lineDiscounts()).containsEntry(1L, 5100L).containsEntry(2L, 1000L);
    }

    @Test
    void capsCashCouponSoEveryLineKeepsOneCent() {
        Candidate cash = coupon(20L, CouponOwnerType.PLATFORM, null,
                CouponType.CASH_RED_PACKET, 0, 500L, null, null);

        var result = calculator.calculate(List.of(new Line(1L, 10L, 101L, 1001L, 300)), List.of(cash));

        assertThat(result.totalDiscountCents()).isEqualTo(299);
        assertThat(result.payableCents()).isEqualTo(1);
        assertThat(result.warnings()).contains("PARTIAL_FACE_VALUE_USED");
    }

    @Test
    void largestRemainderAllocationIsDeterministic() {
        Candidate cash = coupon(30L, CouponOwnerType.PLATFORM, null,
                CouponType.CASH_RED_PACKET, 0, 100L, null, null);
        List<Line> lines = List.of(
                new Line(3L, 20L, 1L, 1L, 100),
                new Line(2L, 10L, 2L, 2L, 100),
                new Line(1L, 10L, 1L, 1L, 100));

        var result = calculator.calculate(lines, List.of(cash));

        assertThat(result.lineDiscounts()).isEqualTo(Map.of(1L, 34L, 2L, 33L, 3L, 33L));
    }

    @Test
    void fundingAllocationUsesLargestRemainderAndBalancesExactly() {
        Candidate shared = new Candidate(40L, "UC40", 40L, CouponOwnerType.PLATFORM, null,
                CouponType.CASH_RED_PACKET, 0, 100L, null, null,
                new BigDecimal("33.0000"), CouponStackMode.CROSS_OWNER,
                LocalDateTime.now().plusDays(7), LocalDateTime.now(), line -> true);

        var applied = calculator.calculate(List.of(
                new Line(3L, 20L, 1L, 3L, 100),
                new Line(2L, 10L, 1L, 2L, 100),
                new Line(1L, 10L, 1L, 1L, 100)), List.of(shared)).coupons().getFirst();

        assertThat(applied.platformFundedCents()).isEqualTo(33);
        assertThat(applied.platformAllocation().values()).containsExactlyInAnyOrder(11L, 11L, 11L);
        assertThat(applied.shopAllocation().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(67);
        assertThat(applied.allocation().keySet()).allSatisfy(lineId ->
                assertThat(applied.platformAllocation().get(lineId) + applied.shopAllocation().get(lineId))
                        .isEqualTo(applied.allocation().get(lineId)));
    }

    private Candidate coupon(long id, CouponOwnerType owner, Long shopId, CouponType type,
                             long threshold, Long discount, BigDecimal percentage, Long maximum) {
        return new Candidate(id, "UC" + id, id, owner, shopId, type, threshold, discount,
                percentage, maximum, new BigDecimal("100.0000"), CouponStackMode.CROSS_OWNER,
                LocalDateTime.now().plusDays(7), LocalDateTime.now(),
                line -> owner == CouponOwnerType.PLATFORM || line.shopId() == shopId);
    }
}
