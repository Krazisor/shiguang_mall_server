package org.dhu.shiguang_market.coupon.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.dhu.shiguang_market.cart.service.CartService.CheckoutLine;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponOwnerType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponSelectionMode;
import org.dhu.shiguang_market.common.model.MarketEnums.UserCouponStatus;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CheckoutCouponSelection;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponQuoteView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.SelectedCouponView;
import org.dhu.shiguang_market.coupon.mapper.CouponScopeMappers;
import org.dhu.shiguang_market.coupon.mapper.CouponTemplateMapper;
import org.dhu.shiguang_market.coupon.mapper.UserCouponMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.CategoryScope;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponTemplate;
import org.dhu.shiguang_market.coupon.model.CouponModels.ShopScope;
import org.dhu.shiguang_market.coupon.model.CouponModels.SkuScope;
import org.dhu.shiguang_market.coupon.model.CouponModels.SpuScope;
import org.dhu.shiguang_market.coupon.model.CouponModels.UserCoupon;
import org.dhu.shiguang_market.product.mapper.ProductCategoryMapper;
import org.dhu.shiguang_market.product.mapper.ProductSpuMapper;
import org.dhu.shiguang_market.product.model.ProductCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class CouponQuoteService {
    private static final Duration EXPIRES_SOON_WINDOW = Duration.ofHours(24);

    private final UserCouponMapper couponMapper;
    private final CouponTemplateMapper templateMapper;
    private final CouponScopeMappers.Shop shopScopes;
    private final CouponScopeMappers.Category categoryScopes;
    private final CouponScopeMappers.Spu spuScopes;
    private final CouponScopeMappers.Sku skuScopes;
    private final ProductCategoryMapper categoryMapper;
    private final ProductSpuMapper spuMapper;
    private final CouponCalculator calculator;
    private final CouponEligibilityService eligibility;
    private final CouponRateLimitService rateLimits;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public CouponQuoteService(UserCouponMapper couponMapper, CouponTemplateMapper templateMapper,
                              CouponScopeMappers.Shop shopScopes, CouponScopeMappers.Category categoryScopes,
                              CouponScopeMappers.Spu spuScopes, CouponScopeMappers.Sku skuScopes,
                              ProductCategoryMapper categoryMapper, ProductSpuMapper spuMapper,
                              CouponCalculator calculator, CouponEligibilityService eligibility,
                              CouponRateLimitService rateLimits, StringRedisTemplate redis, ObjectMapper objectMapper,
                              @Value("${market.coupon.quote-ttl-minutes:5}") long ttlMinutes) {
        this.couponMapper = couponMapper;
        this.templateMapper = templateMapper;
        this.shopScopes = shopScopes;
        this.categoryScopes = categoryScopes;
        this.spuScopes = spuScopes;
        this.skuScopes = skuScopes;
        this.categoryMapper = categoryMapper;
        this.spuMapper = spuMapper;
        this.calculator = calculator;
        this.eligibility = eligibility;
        this.rateLimits = rateLimits;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public QuoteResult quote(long userId, List<CheckoutLine> checkout, CheckoutCouponSelection selection) {
        if (selection != null && selection.mode() != null
                && selection.mode() != CouponSelectionMode.NONE) {
            rateLimits.quote(userId);
        }
        return buildQuote(userId, checkout, selection, true);
    }

    public QuoteResult verify(long userId, String token, List<CheckoutLine> lines) {
        if (token == null) return new QuoteResult(null, null, true);
        StoredQuote stored;
        try {
            String json = redis.opsForValue().get("market:coupon:quote:" + token);
            if (json == null) throw expired();
            stored = objectMapper.readValue(json, StoredQuote.class);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.unavailable("DEPENDENCY_UNAVAILABLE", "优惠券报价服务暂时不可用");
        }
        if (stored.userId() != userId || !LocalDateTime.now().isBefore(stored.expiresAt())) throw expired();

        List<CartSnapshot> current = lines.stream()
                .map(line -> new CartSnapshot(line.cart().getId(), line.cart().getQuantity(), cents(line.sku().getSalePrice())))
                .toList();
        if (!stored.cart().equals(current)) {
            throw BusinessException.conflict("COUPON_QUOTE_CHANGED", "购物车或价格已变化");
        }

        CheckoutCouponSelection selection = new CheckoutCouponSelection(CouponSelectionMode.MANUAL,
                stored.userCouponIds().stream().map(Object::toString).toList());
        QuoteResult recomputed = buildQuote(userId, lines, selection, false);
        if (!recomputed.submittable()
                || recomputed.result().totalDiscountCents() != stored.totalDiscountCents()
                || !recomputed.result().lineDiscounts().equals(stored.lineDiscounts())) {
            throw BusinessException.conflict("COUPON_QUOTE_CHANGED", "优惠结果已变化");
        }
        return recomputed;
    }

    private QuoteResult buildQuote(long userId, List<CheckoutLine> checkout,
                                   CheckoutCouponSelection selection, boolean persist) {
        CouponSelectionMode mode = selection == null || selection.mode() == null
                ? CouponSelectionMode.NONE : selection.mode();
        List<String> requestedIds = selection == null ? null : selection.userCouponIds();
        validate(mode, requestedIds);

        List<CouponCalculator.Line> lines = checkout.stream().filter(CheckoutLine::valid)
                .map(line -> new CouponCalculator.Line(line.cart().getId(), line.shop().getId(), line.spu().getId(),
                        line.sku().getId(), cents(line.amount())))
                .toList();
        long gross = lines.stream().mapToLong(CouponCalculator.Line::grossCents).sum();
        CouponCalculator.Result noDiscount = noDiscount(lines, gross);
        if (mode == CouponSelectionMode.NONE) return new QuoteResult(null, noDiscount, true);

        Set<Long> requested = mode == CouponSelectionMode.MANUAL
                ? requestedIds.stream().map(this::parseId).collect(Collectors.toSet())
                : Set.of();

        LocalDateTime now = LocalDateTime.now();
        List<CandidateEntry> entries = couponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .orderByAsc(UserCoupon::getValidTo)
                        .orderByAsc(UserCoupon::getCreatedAt)
                        .orderByAsc(UserCoupon::getId))
                .stream()
                .map(this::candidate)
                .toList();
        Map<Long, CandidateEntry> byCouponId = entries.stream()
                .collect(Collectors.toMap(entry -> entry.coupon().getId(), entry -> entry));

        List<Map<String, Object>> unavailable = new ArrayList<>();
        List<CandidateEntry> individuallyUsable = new ArrayList<>();
        for (CandidateEntry entry : entries) {
            String reason = unavailableReason(userId, entry, lines, now);
            if (reason == null) individuallyUsable.add(entry);
            else if (!"SCOPE_MISMATCH".equals(reason) || requested.contains(entry.coupon().getId())) {
                unavailable.add(unavailableView(entry, reason, lines));
            }
        }

        if (lines.isEmpty()) {
            requested.stream().filter(value -> entries.stream()
                            .anyMatch(entry -> entry.coupon().getId().equals(value)))
                    .filter(value -> unavailable.stream().noneMatch(item -> value.toString()
                            .equals(item.get("userCouponId"))))
                    .forEach(value -> unavailable.add(unknownCouponView(value)));
            return quoteResult(mode, noDiscount, List.of(), List.of(), unavailable,
                    mode != CouponSelectionMode.MANUAL, null, persist, userId, checkout, lines);
        }

        List<CandidateEntry> selected;
        CouponCalculator.Result result;
        boolean submittable = true;
        if (mode == CouponSelectionMode.MANUAL) {
            List<CandidateEntry> requestedEntries = requested.stream().map(byCouponId::get)
                    .filter(java.util.Objects::nonNull).toList();
            if (requestedEntries.size() != requested.size()) {
                requested.stream().filter(value -> !byCouponId.containsKey(value))
                        .forEach(value -> unavailable.add(unknownCouponView(value)));
                return quoteResult(mode, noDiscount, List.of(), exclude(individuallyUsable, requested), unavailable, false, null, persist,
                        userId, checkout, lines);
            }
            Set<Long> usableIds = individuallyUsable.stream().map(entry -> entry.coupon().getId()).collect(Collectors.toSet());
            if (!usableIds.containsAll(requested)) {
                return quoteResult(mode, noDiscount, List.of(), exclude(individuallyUsable, requested), unavailable, false, null, persist,
                        userId, checkout, lines);
            }
            selected = requestedEntries;
            try {
                result = calculator.calculate(lines, selected.stream().map(CandidateEntry::candidate).toList());
            } catch (BusinessException ex) {
                String reason = calculationReason(ex);
                selected.forEach(entry -> unavailable.add(unavailableView(entry, reason, lines)));
                return quoteResult(mode, noDiscount, List.of(), exclude(individuallyUsable, requested), unavailable, false, null, persist,
                        userId, checkout, lines);
            }
        } else {
            selected = best(lines, individuallyUsable);
            result = calculator.calculate(lines, selected.stream().map(CandidateEntry::candidate).toList());
        }

        List<CandidateEntry> available = individuallyUsable.stream()
                .filter(entry -> selected.stream().noneMatch(chosen -> chosen.coupon().getId().equals(entry.coupon().getId())))
                .toList();
        return quoteResult(mode, result, selected, available, unavailable, submittable, now, persist, userId, checkout, lines);
    }

    private List<CandidateEntry> exclude(List<CandidateEntry> entries, Set<Long> excludedIds) {
        return entries.stream().filter(entry -> !excludedIds.contains(entry.coupon().getId())).toList();
    }

    private QuoteResult quoteResult(CouponSelectionMode mode, CouponCalculator.Result result,
                                    List<CandidateEntry> selected, List<CandidateEntry> available,
                                    List<Map<String, Object>> unavailable, boolean submittable, LocalDateTime now,
                                    boolean persist, long userId, List<CheckoutLine> checkout,
                                    List<CouponCalculator.Line> lines) {
        boolean shouldPersist = persist && submittable && !selected.isEmpty();
        LocalDateTime expires = shouldPersist ? (now == null ? LocalDateTime.now() : now).plus(ttl) : null;
        String token = shouldPersist ? "cq_" + UUID.randomUUID().toString().replace("-", "") : null;
        CouponQuoteView view = new CouponQuoteView(token, time(expires), mode,
                selected.stream().map(entry -> selectedView(entry, result)).toList(),
                available.stream().map(entry -> availableView(entry, lines)).toList(),
                List.copyOf(unavailable), money(cash(result.totalDiscountCents())), warnings(result, selected, now));
        if (shouldPersist) {
            StoredQuote stored = new StoredQuote(userId, expires, checkout.stream()
                    .map(line -> new CartSnapshot(line.cart().getId(), line.cart().getQuantity(),
                            cents(line.sku().getSalePrice())))
                    .toList(), selected.stream().map(entry -> entry.coupon().getId()).toList(),
                    result.totalDiscountCents(), result.lineDiscounts());
            try {
                redis.opsForValue().set("market:coupon:quote:" + token, objectMapper.writeValueAsString(stored), ttl);
            } catch (Exception ex) {
                throw BusinessException.unavailable("DEPENDENCY_UNAVAILABLE", "优惠券报价服务暂时不可用");
            }
        }
        return new QuoteResult(view, result, submittable);
    }

    private CouponCalculator.Result noDiscount(List<CouponCalculator.Line> lines, long gross) {
        Map<Long, Long> discounts = new LinkedHashMap<>();
        lines.forEach(line -> discounts.put(line.cartItemId(), 0L));
        return new CouponCalculator.Result(gross, 0, gross, Map.copyOf(discounts), List.of(), List.of());
    }

    private List<CandidateEntry> best(List<CouponCalculator.Line> lines, List<CandidateEntry> all) {
        List<CandidateEntry> platforms = all.stream()
                .filter(entry -> entry.candidate().ownerType() == CouponOwnerType.PLATFORM).toList();
        Map<Long, List<CandidateEntry>> shops = all.stream()
                .filter(entry -> entry.candidate().ownerType() == CouponOwnerType.SHOP)
                .collect(Collectors.groupingBy(entry -> entry.candidate().shopId()));
        List<List<CandidateEntry>> combinations = new ArrayList<>();
        combinations.add(List.of());
        for (CandidateEntry platform : platforms) combinations.add(List.of(platform));
        for (List<CandidateEntry> shopCoupons : shops.values()) {
            List<List<CandidateEntry>> prior = new ArrayList<>(combinations);
            for (List<CandidateEntry> base : prior) {
                for (CandidateEntry shopCoupon : shopCoupons) {
                    List<CandidateEntry> next = new ArrayList<>(base);
                    next.add(shopCoupon);
                    combinations.add(next);
                }
            }
        }
        List<CandidateEntry> best = List.of();
        long bestDiscount = 0;
        for (List<CandidateEntry> combination : combinations) {
            try {
                long discount = calculator.calculate(lines, combination.stream().map(CandidateEntry::candidate).toList())
                        .totalDiscountCents();
                if (discount > bestDiscount || (discount == bestDiscount && tie(combination, best) < 0)) {
                    bestDiscount = discount;
                    best = combination;
                }
            } catch (BusinessException ignored) {
                // A conflicting combination remains an individually available option for manual selection.
            }
        }
        return best;
    }

    private int tie(List<CandidateEntry> left, List<CandidateEntry> right) {
        int count = Integer.compare(left.size(), right.size());
        if (count != 0) return count;
        Comparator<CandidateEntry> order = Comparator.comparing((CandidateEntry entry) -> entry.coupon().getValidTo())
                .thenComparing(entry -> entry.coupon().getCreatedAt())
                .thenComparing(entry -> entry.coupon().getId());
        List<CandidateEntry> orderedLeft = left.stream().sorted(order).toList();
        List<CandidateEntry> orderedRight = right.stream().sorted(order).toList();
        for (int index = 0; index < orderedLeft.size(); index++) {
            int comparison = order.compare(orderedLeft.get(index), orderedRight.get(index));
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private String unavailableReason(long userId, CandidateEntry entry, List<CouponCalculator.Line> lines,
                                     LocalDateTime now) {
        UserCoupon coupon = entry.coupon();
        if (coupon.getStatus() == UserCouponStatus.LOCKED) return "LOCKED_BY_OTHER_TRADE";
        if (coupon.getStatus() == UserCouponStatus.USED) return "COUPON_ALREADY_USED";
        if (coupon.getStatus() == UserCouponStatus.EXPIRED || !now.isBefore(coupon.getValidTo())) return "EXPIRED";
        if (coupon.getStatus() == UserCouponStatus.REVOKED) return "REVOKED";
        if (coupon.getStatus() != UserCouponStatus.AVAILABLE) return "QUOTE_CHANGED";
        if (now.isBefore(coupon.getValidFrom())) return "NOT_EFFECTIVE";
        String eligibilityReason = eligibility.useIneligibilityReason(userId, entry.template());
        if (eligibilityReason != null) return eligibilityReason;
        try {
            calculator.calculate(lines, List.of(entry.candidate()));
            return null;
        } catch (BusinessException ex) {
            return calculationReason(ex);
        }
    }

    private String calculationReason(BusinessException exception) {
        return switch (exception.getCode()) {
            case "COUPON_SCOPE_MISMATCH" -> "SCOPE_MISMATCH";
            case "COUPON_THRESHOLD_NOT_MET" -> "THRESHOLD_NOT_MET";
            case "COUPON_STACK_CONFLICT" -> "STACK_CONFLICT";
            case "COUPON_SELECTION_LIMIT_EXCEEDED" -> "STACK_CONFLICT";
            default -> "QUOTE_CHANGED";
        };
    }

    private SelectedCouponView selectedView(CandidateEntry entry, CouponCalculator.Result result) {
        CouponCalculator.Applied applied = result.coupons().stream()
                .filter(value -> value.coupon().userCouponId() == entry.coupon().getId())
                .findFirst().orElseThrow();
        return new SelectedCouponView(id(entry.coupon().getId()), entry.coupon().getCouponNo(), id(entry.template().getId()),
                entry.template().getCouponName(), applied.coupon().ownerType(), id(applied.coupon().shopId()),
                money(cash(applied.discountCents())), money(cash(applied.platformFundedCents())),
                money(cash(applied.shopFundedCents())), time(entry.coupon().getValidTo()));
    }

    private Map<String, Object> availableView(CandidateEntry entry, List<CouponCalculator.Line> lines) {
        CouponCalculator.Result expected = calculator.calculate(lines, List.of(entry.candidate()));
        Map<String, Object> value = couponBase(entry);
        value.put("expectedDiscountAmount", money(cash(expected.totalDiscountCents())));
        return value;
    }

    private Map<String, Object> unavailableView(CandidateEntry entry, String reason,
                                                List<CouponCalculator.Line> lines) {
        Map<String, Object> value = couponBase(entry);
        value.put("reason", reason);
        if ("THRESHOLD_NOT_MET".equals(reason)) {
            long eligible = lines.stream().filter(entry.candidate().scopeMatcher())
                    .mapToLong(CouponCalculator.Line::grossCents).sum();
            long threshold = entry.candidate().thresholdCents();
            value.put("thresholdAmount", money(cash(threshold)));
            value.put("eligibleAmount", money(cash(eligible)));
            value.put("amountNeeded", money(cash(Math.max(0, threshold - eligible))));
        }
        return value;
    }

    private Map<String, Object> unknownCouponView(long couponId) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("userCouponId", Long.toString(couponId));
        value.put("reason", "QUOTE_CHANGED");
        return value;
    }

    private Map<String, Object> couponBase(CandidateEntry entry) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("userCouponId", id(entry.coupon().getId()));
        value.put("couponNo", entry.coupon().getCouponNo());
        value.put("templateId", id(entry.template().getId()));
        value.put("couponName", entry.template().getCouponName());
        value.put("ownerType", entry.template().getOwnerType());
        value.put("shopId", id(entry.template().getOwnerShopId()));
        value.put("validTo", time(entry.coupon().getValidTo()));
        return value;
    }

    private List<String> warnings(CouponCalculator.Result result, List<CandidateEntry> selected, LocalDateTime now) {
        Set<String> warnings = new HashSet<>(result.warnings());
        LocalDateTime reference = now == null ? LocalDateTime.now() : now;
        if (selected.stream().anyMatch(entry -> !entry.coupon().getValidTo().isAfter(reference.plus(EXPIRES_SOON_WINDOW)))) {
            warnings.add("EXPIRES_SOON");
        }
        return warnings.stream().sorted().toList();
    }

    private CandidateEntry candidate(UserCoupon coupon) {
        CouponTemplate template = templateMapper.selectById(coupon.getTemplateId());
        if (template == null) {
            throw BusinessException.conflict("COUPON_QUOTE_CHANGED", "优惠券模板已不存在");
        }
        Predicate<CouponCalculator.Line> scope = scope(template);
        CouponCalculator.Candidate value = new CouponCalculator.Candidate(coupon.getId(), coupon.getCouponNo(),
                template.getId(), template.getOwnerType(), template.getOwnerShopId(), template.getCouponType(),
                cents(template.getThresholdAmount()), template.getDiscountAmount() == null ? null : cents(template.getDiscountAmount()),
                template.getPercentageOff(), template.getMaximumDiscountAmount() == null ? null
                : cents(template.getMaximumDiscountAmount()), template.getPlatformShareRate(), template.getStackMode(),
                coupon.getValidTo(), coupon.getCreatedAt(), scope);
        return new CandidateEntry(coupon, template, value);
    }

    private Predicate<CouponCalculator.Line> scope(CouponTemplate template) {
        return switch (template.getScopeType()) {
            case ALL -> line -> true;
            case SHOP -> {
                Set<Long> ids = new HashSet<>(shopScopes.selectList(new LambdaQueryWrapper<ShopScope>()
                        .eq(ShopScope::getTemplateId, template.getId())).stream().map(ShopScope::getShopId).toList());
                yield line -> ids.contains(line.shopId());
            }
            case SPU -> {
                Set<Long> ids = new HashSet<>(spuScopes.selectList(new LambdaQueryWrapper<SpuScope>()
                        .eq(SpuScope::getTemplateId, template.getId())).stream().map(SpuScope::getSpuId).toList());
                yield line -> ids.contains(line.spuId());
            }
            case SKU -> {
                Set<Long> ids = new HashSet<>(skuScopes.selectList(new LambdaQueryWrapper<SkuScope>()
                        .eq(SkuScope::getTemplateId, template.getId())).stream().map(SkuScope::getSkuId).toList());
                yield line -> ids.contains(line.skuId());
            }
            case CATEGORY -> {
                Set<Long> roots = new HashSet<>(categoryScopes.selectList(new LambdaQueryWrapper<CategoryScope>()
                        .eq(CategoryScope::getTemplateId, template.getId())).stream().map(CategoryScope::getCategoryId).toList());
                yield line -> belongs(line.spuId(), roots);
            }
        };
    }

    private boolean belongs(long spuId, Set<Long> roots) {
        var spu = spuMapper.selectById(spuId);
        if (spu == null) return false;
        Long categoryId = spu.getCategoryId();
        while (categoryId != null) {
            if (roots.contains(categoryId)) return true;
            ProductCategory category = categoryMapper.selectById(categoryId);
            if (category == null) break;
            categoryId = category.getParentId();
        }
        return false;
    }

    private void validate(CouponSelectionMode mode, List<String> ids) {
        boolean hasIds = ids != null && !ids.isEmpty();
        if (mode == CouponSelectionMode.MANUAL && !hasIds) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "手动选券必须提交 userCouponIds");
        }
        if (mode != CouponSelectionMode.MANUAL && hasIds) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "该模式不接受 userCouponIds");
        }
        if (ids != null && (ids.size() > 20 || new HashSet<>(ids).size() != ids.size())) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "userCouponIds 最多 20 个且不可重复");
        }
    }

    private long cents(BigDecimal value) {
        return value.movePointRight(2).longValueExact();
    }

    private BigDecimal cash(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    private long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException exception) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "ID 格式错误");
        }
    }

    private BusinessException expired() {
        return BusinessException.conflict("COUPON_QUOTE_EXPIRED", "优惠券报价已过期");
    }

    public record QuoteResult(CouponQuoteView view, CouponCalculator.Result result, boolean submittable) {
    }

    private record CandidateEntry(UserCoupon coupon, CouponTemplate template, CouponCalculator.Candidate candidate) {
    }

    private record CartSnapshot(long cartItemId, int quantity, long unitPriceCents) {
    }

    private record StoredQuote(long userId, LocalDateTime expiresAt, List<CartSnapshot> cart,
                               List<Long> userCouponIds, long totalDiscountCents, Map<Long, Long> lineDiscounts) {
    }
}
