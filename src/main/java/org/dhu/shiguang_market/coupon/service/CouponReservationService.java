package org.dhu.shiguang_market.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRedemptionStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.UserCouponStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.coupon.mapper.CouponRedemptionMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponRedemptionAllocationMapper;
import org.dhu.shiguang_market.coupon.mapper.UserCouponMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.Redemption;
import org.dhu.shiguang_market.coupon.model.CouponModels.RedemptionAllocation;
import org.dhu.shiguang_market.order.model.OrderItem;
import org.dhu.shiguang_market.coupon.model.CouponModels.UserCoupon;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponReservationService {
    private final UserCouponMapper coupons;
    private final CouponRedemptionMapper redemptions;
    private final NumberGenerator numbers;
    private final CouponRedemptionAllocationMapper allocations;
    private final CouponBudgetService budget;
    private final CouponAuditService audit;

    public CouponReservationService(UserCouponMapper coupons, CouponRedemptionMapper redemptions,
                                    NumberGenerator numbers, CouponRedemptionAllocationMapper allocations,
                                    CouponBudgetService budget, CouponAuditService audit) {
        this.coupons = coupons;
        this.redemptions = redemptions;
        this.numbers = numbers;
        this.allocations = allocations;
        this.budget = budget;
        this.audit = audit;
    }

    @Transactional
    public void reserve(long userId, long tradeId, CouponCalculator.Result result) {
        reserve(userId, tradeId, result, Map.of());
    }

    @Transactional
    public void reserve(long userId, long tradeId, CouponCalculator.Result result,
                        Map<Long, OrderItem> orderItems) {
        if (result == null) return;
        for (CouponCalculator.Applied applied : result.coupons()) {
            UserCoupon coupon = coupons.selectOne(new LambdaQueryWrapper<UserCoupon>()
                    .eq(UserCoupon::getId, applied.coupon().userCouponId())
                    .eq(UserCoupon::getUserId, userId).last("FOR UPDATE"));
            if (coupon == null || coupon.getStatus() != UserCouponStatus.AVAILABLE
                    || !LocalDateTime.now().isBefore(coupon.getValidTo())) {
                throw BusinessException.conflict("COUPON_CONCURRENTLY_USED", "优惠券已被其他交易占用");
            }
            coupon.setStatus(UserCouponStatus.LOCKED);
            coupon.setLockedTradeId(tradeId);
            coupons.updateById(coupon);
            Redemption redemption = new Redemption();
            redemption.setRedemptionNo(numbers.next("CR"));
            redemption.setUserCouponId(coupon.getId()); redemption.setTemplateId(applied.coupon().templateId());
            Redemption previous = redemptions.selectOne(new LambdaQueryWrapper<Redemption>()
                    .eq(Redemption::getUserCouponId, coupon.getId())
                    .orderByDesc(Redemption::getAttemptNo).last("LIMIT 1 FOR UPDATE"));
            redemption.setUserId(userId); redemption.setTradeId(tradeId);
            redemption.setAttemptNo(previous == null ? 1 : previous.getAttemptNo() + 1);
            redemption.setStatus(CouponRedemptionStatus.RESERVED);
            redemption.setDiscountAmount(BigDecimal.valueOf(applied.discountCents(), 2));
            redemption.setPlatformFundedAmount(BigDecimal.valueOf(applied.platformFundedCents(), 2));
            redemption.setShopFundedAmount(BigDecimal.valueOf(applied.shopFundedCents(), 2));
            redemption.setReservedAt(LocalDateTime.now()); redemption.setVersion(0);
            redemptions.insert(redemption);
            audit.log("USER_COUPON", coupon.getId(), "RESERVE", OperatorType.USER, userId, null,
                    UserCouponStatus.AVAILABLE.name(), UserCouponStatus.LOCKED.name(),
                    Map.of("tradeId", tradeId, "redemptionId", redemption.getId()), null);
            for (Map.Entry<Long, Long> entry : applied.allocation().entrySet()) {
                OrderItem item = orderItems.get(entry.getKey());
                if (item == null) continue;
                long discount = entry.getValue();
                long platform = applied.platformAllocation().getOrDefault(entry.getKey(), 0L);
                RedemptionAllocation allocation = new RedemptionAllocation();
                allocation.setRedemptionId(redemption.getId()); allocation.setTradeId(tradeId);
                allocation.setOrderId(item.getOrderId()); allocation.setOrderItemId(item.getId());
                allocation.setShopId(item.getShopId());
                allocation.setEligibleGrossAmount(cash(applied.eligibleGross().getOrDefault(entry.getKey(), 0L)));
                allocation.setCalculationBaseAmount(cash(applied.calculationBases().getOrDefault(entry.getKey(), 0L)));
                allocation.setDiscountAmount(cash(discount)); allocation.setPlatformFundedAmount(cash(platform));
                allocation.setShopFundedAmount(cash(discount - platform)); allocations.insert(allocation);
            }
        }
    }

    @Transactional
    public void consume(long tradeId) {
        List<Redemption> rows = redemptions.selectList(new LambdaQueryWrapper<Redemption>()
                .eq(Redemption::getTradeId, tradeId).eq(Redemption::getStatus, CouponRedemptionStatus.RESERVED)
                .orderByAsc(Redemption::getUserCouponId));
        for (Redemption row : rows) {
            Redemption locked = redemptions.selectOne(new LambdaQueryWrapper<Redemption>()
                    .eq(Redemption::getId, row.getId()).last("FOR UPDATE"));
            if (locked == null || locked.getStatus() != CouponRedemptionStatus.RESERVED) continue;
            UserCoupon coupon = coupons.selectById(locked.getUserCouponId());
            if (coupon == null || coupon.getStatus() != UserCouponStatus.LOCKED
                    || !Long.valueOf(tradeId).equals(coupon.getLockedTradeId())) continue;
            coupon.setStatus(UserCouponStatus.USED); coupon.setLockedTradeId(null); coupon.setUsedAt(LocalDateTime.now());
            coupons.updateById(coupon); locked.setStatus(CouponRedemptionStatus.CONSUMED); locked.setConsumedAt(LocalDateTime.now()); redemptions.updateById(locked);
            budget.consume(locked);
            audit.log("USER_COUPON", coupon.getId(), "CONSUME", OperatorType.SYSTEM, null, null,
                    UserCouponStatus.LOCKED.name(), UserCouponStatus.USED.name(),
                    Map.of("tradeId", tradeId, "redemptionId", locked.getId()), null);
        }
    }

    @Transactional
    public void release(long tradeId, String reason) {
        List<Redemption> rows = redemptions.selectList(new LambdaQueryWrapper<Redemption>()
                .eq(Redemption::getTradeId, tradeId).eq(Redemption::getStatus, CouponRedemptionStatus.RESERVED));
        for (Redemption row : rows) {
            UserCoupon coupon = coupons.selectById(row.getUserCouponId());
            if (coupon != null && coupon.getStatus() == UserCouponStatus.LOCKED) {
                coupon.setLockedTradeId(null);
                boolean expired = !LocalDateTime.now().isBefore(coupon.getValidTo());
                coupon.setStatus(expired ? UserCouponStatus.EXPIRED : UserCouponStatus.AVAILABLE);
                if (expired) coupon.setExpiredAt(LocalDateTime.now());
                coupons.updateById(coupon);
                if (expired) budget.release(coupon, "EXPIRE_RELEASE", "COUPON_EXPIRY", coupon.getCouponNo());
                audit.log("USER_COUPON", coupon.getId(), "RELEASE", OperatorType.SYSTEM, null, null,
                        UserCouponStatus.LOCKED.name(), coupon.getStatus().name(), Map.of("tradeId", tradeId), reason);
            }
            row.setStatus(CouponRedemptionStatus.RELEASED); row.setReleasedAt(LocalDateTime.now()); row.setReleaseReason(reason); redemptions.updateById(row);
        }
    }

    private BigDecimal cash(long cents) { return BigDecimal.valueOf(cents, 2); }
}
