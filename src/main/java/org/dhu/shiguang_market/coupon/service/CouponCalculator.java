package org.dhu.shiguang_market.coupon.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponOwnerType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponStackMode;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponType;
import org.springframework.stereotype.Component;

@Component
public class CouponCalculator {
    public Result calculate(List<Line> lines, List<Candidate> selected) {
        if (lines == null || lines.isEmpty()) {
            throw BusinessException.unprocessable("CHECKOUT_ITEMS_INVALID", "没有可结算的商品");
        }
        validateSelection(lines, selected);
        List<Candidate> ordered = selected.stream()
                .sorted(Comparator.comparing((Candidate value) -> value.ownerType() == CouponOwnerType.SHOP ? 0 : 1)
                        .thenComparing(value -> value.shopId() == null ? Long.MAX_VALUE : value.shopId())
                        .thenComparing(Candidate::userCouponId))
                .toList();
        Map<Long, Long> lineDiscounts = new HashMap<>();
        List<Applied> applied = new ArrayList<>();
        Set<String> warnings = new LinkedHashSet<>();
        for (Candidate coupon : ordered) {
            List<Line> eligible = lines.stream().filter(coupon.scopeMatcher()).toList();
            if (eligible.isEmpty()) {
                throw BusinessException.unprocessable("COUPON_SCOPE_MISMATCH", "优惠券未命中任何商品");
            }
            long eligibleGross = eligible.stream().mapToLong(Line::grossCents).sum();
            if (eligibleGross < coupon.thresholdCents()) {
                throw BusinessException.unprocessable("COUPON_THRESHOLD_NOT_MET", "优惠券使用门槛未满足");
            }
            Map<Long, Long> bases = new LinkedHashMap<>();
            for (Line line : eligible) {
                long prior = lineDiscounts.getOrDefault(line.cartItemId(), 0L);
                bases.put(line.cartItemId(), Math.max(0, line.grossCents() - prior));
            }
            long calculationBase = bases.values().stream().mapToLong(Long::longValue).sum();
            long cap = eligible.stream().mapToLong(line -> Math.max(0,
                    line.grossCents() - lineDiscounts.getOrDefault(line.cartItemId(), 0L) - 1)).sum();
            long requested = discount(coupon, calculationBase);
            long actual = Math.min(requested, cap);
            if (actual <= 0) {
                throw BusinessException.unprocessable("COUPON_SCOPE_MISMATCH", "优惠券没有可抵扣金额");
            }
            if (actual < requested && coupon.couponType() == CouponType.CASH_RED_PACKET) {
                warnings.add("PARTIAL_FACE_VALUE_USED");
            }
            Map<Long, Long> allocation = allocate(actual, eligible, bases);
            allocation.forEach((id, cents) -> lineDiscounts.merge(id, cents, Long::sum));
            long platform = rate(actual, coupon.platformShareRate());
            Map<Long, Long> platformAllocation = allocate(platform, eligible, allocation);
            Map<Long, Long> shopAllocation = new LinkedHashMap<>();
            allocation.forEach((lineId, discount) -> shopAllocation.put(lineId,
                    discount - platformAllocation.getOrDefault(lineId, 0L)));
            applied.add(new Applied(coupon, actual, platform, actual - platform, allocation,
                    Map.copyOf(platformAllocation), Map.copyOf(shopAllocation),
                    eligible.stream().collect(java.util.stream.Collectors.toMap(Line::cartItemId,
                            Line::grossCents, (left, right) -> left, LinkedHashMap::new)),
                    bases));
        }
        Map<Long, Long> stableDiscounts = new LinkedHashMap<>();
        lines.stream().sorted(LINE_ORDER).forEach(line ->
                stableDiscounts.put(line.cartItemId(), lineDiscounts.getOrDefault(line.cartItemId(), 0L)));
        long gross = lines.stream().mapToLong(Line::grossCents).sum();
        long totalDiscount = stableDiscounts.values().stream().mapToLong(Long::longValue).sum();
        return new Result(gross, totalDiscount, gross - totalDiscount,
                Map.copyOf(stableDiscounts), List.copyOf(applied), List.copyOf(warnings));
    }

    private void validateSelection(List<Line> lines, List<Candidate> selected) {
        if (selected == null || selected.isEmpty()) return;
        long platformCount = selected.stream().filter(c -> c.ownerType() == CouponOwnerType.PLATFORM).count();
        if (platformCount > 1) {
            throw BusinessException.unprocessable("COUPON_SELECTION_LIMIT_EXCEEDED", "每笔交易最多使用一张平台券");
        }
        Map<Long, Long> shopCounts = selected.stream().filter(c -> c.ownerType() == CouponOwnerType.SHOP)
                .collect(java.util.stream.Collectors.groupingBy(Candidate::shopId,
                        java.util.stream.Collectors.counting()));
        if (shopCounts.values().stream().anyMatch(count -> count > 1)) {
            throw BusinessException.unprocessable("COUPON_SELECTION_LIMIT_EXCEEDED", "每个店铺最多使用一张店铺券");
        }
        for (int left = 0; left < selected.size(); left++) {
            for (int right = left + 1; right < selected.size(); right++) {
                Candidate a = selected.get(left);
                Candidate b = selected.get(right);
                boolean overlaps = lines.stream().anyMatch(line ->
                        a.scopeMatcher().test(line) && b.scopeMatcher().test(line));
                if (overlaps && (a.stackMode() == CouponStackMode.EXCLUSIVE
                        || b.stackMode() == CouponStackMode.EXCLUSIVE)) {
                    throw BusinessException.unprocessable("COUPON_STACK_CONFLICT", "所选优惠券不可叠加");
                }
            }
        }
    }

    private long discount(Candidate coupon, long base) {
        return switch (coupon.couponType()) {
            case PERCENTAGE -> {
                long calculated = BigDecimal.valueOf(base)
                        .multiply(coupon.percentageOff())
                        .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP).longValueExact();
                yield Math.min(calculated, coupon.maximumDiscountCents());
            }
            case THRESHOLD_REDUCTION, CASH_RED_PACKET -> coupon.discountCents();
        };
    }

    private Map<Long, Long> allocate(long total, List<Line> lines, Map<Long, Long> weights) {
        if (total == 0) {
            Map<Long, Long> empty = new LinkedHashMap<>();
            lines.stream().sorted(LINE_ORDER).forEach(line -> empty.put(line.cartItemId(), 0L));
            return empty;
        }
        long weightTotal = weights.values().stream().mapToLong(Long::longValue).sum();
        List<Share> shares = new ArrayList<>();
        long allocated = 0;
        for (Line line : lines) {
            long weight = weights.getOrDefault(line.cartItemId(), 0L);
            BigDecimal theoretical = BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(weight))
                    .divide(BigDecimal.valueOf(weightTotal), 16, RoundingMode.DOWN);
            long floor = theoretical.setScale(0, RoundingMode.DOWN).longValueExact();
            allocated += floor;
            shares.add(new Share(line, floor, theoretical.subtract(BigDecimal.valueOf(floor))));
        }
        shares.sort(Comparator.comparing(Share::remainder).reversed().thenComparing(Share::line, LINE_ORDER));
        for (long remaining = total - allocated, index = 0; index < remaining; index++) {
            Share share = shares.get((int) index);
            shares.set((int) index, new Share(share.line(), share.cents() + 1, share.remainder()));
        }
        Map<Long, Long> result = new LinkedHashMap<>();
        shares.stream().sorted(Comparator.comparing(Share::line, LINE_ORDER))
                .forEach(share -> result.put(share.line().cartItemId(), share.cents()));
        return result;
    }

    private long rate(long amount, BigDecimal percentage) {
        return BigDecimal.valueOf(amount).multiply(percentage)
                .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP).longValueExact();
    }

    private static final Comparator<Line> LINE_ORDER = Comparator.comparingLong(Line::shopId)
            .thenComparingLong(Line::skuId).thenComparingLong(Line::cartItemId);

    public record Line(long cartItemId, long shopId, long spuId, long skuId, long grossCents) {
    }

    public record Candidate(
            long userCouponId, String couponNo, long templateId, CouponOwnerType ownerType,
            Long shopId, CouponType couponType, long thresholdCents, Long discountCents,
            BigDecimal percentageOff, Long maximumDiscountCents, BigDecimal platformShareRate,
            CouponStackMode stackMode, LocalDateTime validTo, LocalDateTime claimedAt,
            Predicate<Line> scopeMatcher) {
    }

    public record Applied(Candidate coupon, long discountCents, long platformFundedCents,
                          long shopFundedCents, Map<Long, Long> allocation,
                          Map<Long, Long> platformAllocation, Map<Long, Long> shopAllocation,
                          Map<Long, Long> eligibleGross, Map<Long, Long> calculationBases) {
    }

    public record Result(long grossCents, long totalDiscountCents, long payableCents,
                         Map<Long, Long> lineDiscounts, List<Applied> coupons, List<String> warnings) {
    }

    private record Share(Line line, long cents, BigDecimal remainder) {
    }
}
