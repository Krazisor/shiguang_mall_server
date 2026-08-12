package org.dhu.shiguang_market.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRedemptionStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRestorePolicy;
import org.dhu.shiguang_market.common.model.MarketEnums.UserCouponStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.coupon.mapper.CouponRedemptionAllocationMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponRedemptionMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponRefundAllocationMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponTemplateMapper;
import org.dhu.shiguang_market.coupon.mapper.UserCouponMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponTemplate;
import org.dhu.shiguang_market.coupon.model.CouponModels.Redemption;
import org.dhu.shiguang_market.coupon.model.CouponModels.RedemptionAllocation;
import org.dhu.shiguang_market.coupon.model.CouponModels.RefundAllocation;
import org.dhu.shiguang_market.coupon.model.CouponModels.UserCoupon;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.OrderItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponRefundService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final CouponRedemptionAllocationMapper allocations;
    private final CouponRefundAllocationMapper refunds;
    private final CouponRedemptionMapper redemptions;
    private final UserCouponMapper coupons;
    private final CouponTemplateMapper templates;
    private final OrderInfoMapper orders;
    private final OrderItemMapper items;
    private final CouponBudgetService budget;
    private final CouponEligibilityService eligibility;
    private final CouponAuditService audit;

    public CouponRefundService(CouponRedemptionAllocationMapper allocations,
                               CouponRefundAllocationMapper refunds,
                               CouponRedemptionMapper redemptions, UserCouponMapper coupons,
                               CouponTemplateMapper templates, OrderInfoMapper orders,
                               OrderItemMapper items, CouponBudgetService budget,
                               CouponEligibilityService eligibility, CouponAuditService audit) {
        this.allocations = allocations;
        this.refunds = refunds;
        this.redemptions = redemptions;
        this.coupons = coupons;
        this.templates = templates;
        this.orders = orders;
        this.items = items;
        this.budget = budget;
        this.eligibility = eligibility;
        this.audit = audit;
    }

    public RefundImpact preview(OrderItem item, int refundQuantity) {
        BigDecimal platform = ZERO;
        for (RedemptionAllocation allocation : itemAllocations(item.getId())) {
            Reversal reversal = reversal(allocation, item, refundQuantity, false);
            platform = platform.add(reversal.platform());
        }
        return new RefundImpact(platform.setScale(2));
    }

    @Transactional
    public void recordSuccessfulRefund(AfterSaleRequest afterSale, OrderItem item) {
        if (afterSale.getRefundNo() == null) return;
        Map<Redemption, Reversal> byRedemption = new LinkedHashMap<>();
        for (RedemptionAllocation allocation : itemAllocations(item.getId())) {
            if (refunds.selectCount(new LambdaQueryWrapper<RefundAllocation>()
                    .eq(RefundAllocation::getRedemptionAllocationId, allocation.getId())
                    .eq(RefundAllocation::getRefundNo, afterSale.getRefundNo())) > 0) continue;
            Reversal reversal = reversal(allocation, item, afterSale.getApprovedQuantity(), true);
            if (reversal.total().signum() <= 0) continue;
            RefundAllocation row = new RefundAllocation();
            row.setRedemptionAllocationId(allocation.getId());
            row.setAfterSaleId(afterSale.getId());
            row.setRefundNo(afterSale.getRefundNo());
            row.setRefundedQuantity(afterSale.getApprovedQuantity());
            row.setCouponDiscountReversalAmount(reversal.total());
            row.setPlatformFundingReversalAmount(reversal.platform());
            row.setShopFundingReversalAmount(reversal.shop());
            refunds.insert(row);
            Redemption redemption = redemptions.selectById(allocation.getRedemptionId());
            if (redemption != null) {
                audit.log("USER_COUPON", redemption.getUserCouponId(), "REFUND_REVERSE",
                        OperatorType.SYSTEM, null, allocation.getShopId(), null, null,
                        Map.of("refundNo", afterSale.getRefundNo(), "amount", reversal.total()), null);
                byRedemption.merge(redemption, reversal, Reversal::add);
            }
        }
        byRedemption.forEach((redemption, reversal) -> budget.reverse(redemption, reversal.total(),
                reversal.platform(), reversal.shop(), afterSale.getRefundNo()));
        OrderInfo order = orders.selectById(afterSale.getOrderId());
        if (order != null && fullyRefunded(order.getTradeId())) restoreCoupons(order.getTradeId());
    }

    public RestoreHint restoreHint(OrderInfo order, OrderItem item) {
        List<RedemptionAllocation> rows = itemAllocations(item.getId());
        boolean restorable = rows.stream().map(RedemptionAllocation::getRedemptionId)
                .map(redemptions::selectById).filter(java.util.Objects::nonNull)
                .map(Redemption::getTemplateId).map(templates::selectById).filter(java.util.Objects::nonNull)
                .anyMatch(template -> template.getRefundRestorePolicy() == CouponRestorePolicy.FULL_TRADE_ONLY);
        if (!restorable) return null;
        return new RestoreHint(true, false, "PARTIAL_REFUND_DOES_NOT_RESTORE");
    }

    private Reversal reversal(RedemptionAllocation allocation, OrderItem item, int currentQuantity,
                              boolean currentAlreadyApplied) {
        int previousQuantity = item.getRefundedQuantity() == null ? 0 : item.getRefundedQuantity();
        int targetQuantity = Math.min(item.getQuantity(), currentAlreadyApplied
                ? previousQuantity : previousQuantity + currentQuantity);
        BigDecimal priorTotal = sumRefunded(allocation.getId(), RefundAllocation::getCouponDiscountReversalAmount);
        BigDecimal priorPlatform = sumRefunded(allocation.getId(), RefundAllocation::getPlatformFundingReversalAmount);
        BigDecimal targetTotal = proportional(allocation.getDiscountAmount(), targetQuantity, item.getQuantity());
        BigDecimal targetPlatform = proportional(allocation.getPlatformFundedAmount(), targetQuantity, item.getQuantity());
        BigDecimal total = targetTotal.subtract(priorTotal).max(ZERO);
        BigDecimal platform = targetPlatform.subtract(priorPlatform).max(ZERO).min(total);
        return new Reversal(total, platform, total.subtract(platform));
    }

    private BigDecimal proportional(BigDecimal amount, int quantity, int totalQuantity) {
        if (quantity >= totalQuantity) return amount.setScale(2);
        return amount.multiply(BigDecimal.valueOf(quantity))
                .divide(BigDecimal.valueOf(totalQuantity), 2, RoundingMode.DOWN);
    }

    private BigDecimal sumRefunded(long allocationId,
                                   java.util.function.Function<RefundAllocation, BigDecimal> getter) {
        return refunds.selectList(new LambdaQueryWrapper<RefundAllocation>()
                        .eq(RefundAllocation::getRedemptionAllocationId, allocationId))
                .stream().map(getter).reduce(ZERO, BigDecimal::add);
    }

    private List<RedemptionAllocation> itemAllocations(long itemId) {
        return allocations.selectList(new LambdaQueryWrapper<RedemptionAllocation>()
                .eq(RedemptionAllocation::getOrderItemId, itemId)
                .orderByAsc(RedemptionAllocation::getRedemptionId)
                .orderByAsc(RedemptionAllocation::getId));
    }

    private boolean fullyRefunded(long tradeId) {
        List<Long> orderIds = orders.selectList(new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getTradeId, tradeId).orderByAsc(OrderInfo::getId))
                .stream().map(OrderInfo::getId).toList();
        if (orderIds.isEmpty()) return false;
        List<OrderItem> tradeItems = items.selectList(new LambdaQueryWrapper<OrderItem>()
                .in(OrderItem::getOrderId, orderIds).orderByAsc(OrderItem::getId));
        return !tradeItems.isEmpty() && tradeItems.stream().allMatch(item ->
                item.getRefundedQuantity() != null && item.getRefundedQuantity().equals(item.getQuantity()));
    }

    private void restoreCoupons(long tradeId) {
        List<Redemption> rows = redemptions.selectList(new LambdaQueryWrapper<Redemption>()
                .eq(Redemption::getTradeId, tradeId)
                .eq(Redemption::getStatus, CouponRedemptionStatus.CONSUMED)
                .orderByAsc(Redemption::getUserCouponId).last("FOR UPDATE"));
        for (Redemption redemption : rows) {
            UserCoupon coupon = coupons.selectOne(new LambdaQueryWrapper<UserCoupon>()
                    .eq(UserCoupon::getId, redemption.getUserCouponId()).last("FOR UPDATE"));
            CouponTemplate template = templates.selectById(redemption.getTemplateId());
            if (coupon == null || template == null || coupon.getStatus() != UserCouponStatus.USED
                    || coupon.getRestoreCount() == null || coupon.getRestoreCount() >= 1
                    || template.getRefundRestorePolicy() != CouponRestorePolicy.FULL_TRADE_ONLY
                    || !eligibility.eligibleForRestore(coupon.getUserId(), template, tradeId)) continue;
            LocalDateTime now = LocalDateTime.now();
            coupon.setStatus(UserCouponStatus.AVAILABLE);
            coupon.setRestoreCount(coupon.getRestoreCount() + 1);
            coupon.setLastRestoredAt(now);
            if (coupon.getValidTo().isBefore(now.plusHours(72))) coupon.setValidTo(now.plusHours(72));
            coupons.updateById(coupon);
            redemption.setStatus(CouponRedemptionStatus.RESTORED);
            redemption.setRestoredAt(now);
            redemptions.updateById(redemption);
            budget.restore(coupon, redemption, redemption.getRedemptionNo());
            audit.log("USER_COUPON", coupon.getId(), "RESTORE", OperatorType.SYSTEM, null, null,
                    UserCouponStatus.USED.name(), UserCouponStatus.AVAILABLE.name(),
                    Map.of("tradeId", tradeId, "redemptionId", redemption.getId()), null);
        }
    }

    public record RefundImpact(BigDecimal platformSubsidyReversal) { }
    public record RestoreHint(boolean restorableOnlyAfterFullTradeRefund,
                              boolean currentRequestWillRestore, String reason) { }
    private record Reversal(BigDecimal total, BigDecimal platform, BigDecimal shop) {
        private Reversal add(Reversal other) {
            return new Reversal(total.add(other.total), platform.add(other.platform), shop.add(other.shop));
        }
    }
}
