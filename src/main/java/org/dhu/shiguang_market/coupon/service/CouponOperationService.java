package org.dhu.shiguang_market.coupon.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRedemptionStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.UserCouponStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.util.Formatters;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponBusinessTraceView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.OperationCouponRedemptionView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.OperationUserCouponView;
import org.dhu.shiguang_market.coupon.mapper.CouponBudgetLedgerMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponClaimRecordMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponActivityMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponOperationLogMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponRedemptionAllocationMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponRedemptionMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponRefundAllocationMapper;
import org.dhu.shiguang_market.coupon.mapper.CouponTemplateMapper;
import org.dhu.shiguang_market.coupon.mapper.UserCouponMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.BudgetLedger;
import org.dhu.shiguang_market.coupon.model.CouponModels.ClaimRecord;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponActivity;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponTemplate;
import org.dhu.shiguang_market.coupon.model.CouponModels.OperationLog;
import org.dhu.shiguang_market.coupon.model.CouponModels.Redemption;
import org.dhu.shiguang_market.coupon.model.CouponModels.RedemptionAllocation;
import org.dhu.shiguang_market.coupon.model.CouponModels.RefundAllocation;
import org.dhu.shiguang_market.coupon.model.CouponModels.UserCoupon;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.TradeOrderMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.TradeOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CouponOperationService {
    private final CurrentUserService currentUser;
    private final UserCouponMapper coupons;
    private final CouponTemplateMapper templates;
    private final CouponActivityMapper activities;
    private final CouponClaimRecordMapper claims;
    private final CouponOperationLogMapper operationLogs;
    private final CouponRedemptionMapper redemptions;
    private final CouponRedemptionAllocationMapper allocations;
    private final CouponRefundAllocationMapper refunds;
    private final CouponBudgetLedgerMapper budget;
    private final TradeOrderMapper trades;
    private final OrderInfoMapper orders;

    public CouponOperationService(CurrentUserService currentUser, UserCouponMapper coupons,
                                  CouponTemplateMapper templates, CouponActivityMapper activities,
                                  CouponClaimRecordMapper claims,
                                  CouponOperationLogMapper operationLogs,
                                  CouponRedemptionMapper redemptions,
                                  CouponRedemptionAllocationMapper allocations,
                                  CouponRefundAllocationMapper refunds,
                                  CouponBudgetLedgerMapper budget, TradeOrderMapper trades,
                                  OrderInfoMapper orders) {
        this.currentUser = currentUser;
        this.coupons = coupons;
        this.templates = templates;
        this.activities = activities;
        this.claims = claims;
        this.operationLogs = operationLogs;
        this.redemptions = redemptions;
        this.allocations = allocations;
        this.refunds = refunds;
        this.budget = budget;
        this.trades = trades;
        this.orders = orders;
    }

    public PageView<OperationUserCouponView> userCoupons(String couponNo, String templateNo,
                                                          Long userId, UserCouponStatus status,
                                                          long page, long pageSize) {
        authorize();
        validatePage(page, pageSize);
        Page<UserCoupon> result = coupons.selectCouponPage(Page.of(page, pageSize), userId,
                text(couponNo), text(templateNo), status, null, null, null, null, "createdAt,desc");
        return PageView.of(result, result.getRecords().stream().map(coupon -> {
            CouponTemplate template = templates.selectById(coupon.getTemplateId());
            return new OperationUserCouponView(id(coupon.getId()), coupon.getCouponNo(),
                    template == null ? null : template.getTemplateNo(), id(coupon.getUserId()),
                    displayStatus(coupon), time(coupon.getValidTo()));
        }).toList());
    }

    public PageView<OperationCouponRedemptionView> redemptions(String redemptionNo, String tradeNo,
                                                                String orderNo, Long shopId,
                                                                CouponRedemptionStatus status,
                                                                long page, long pageSize) {
        authorize();
        validatePage(page, pageSize);
        Page<Redemption> result = redemptions.selectOperationPage(Page.of(page, pageSize),
                text(redemptionNo), text(tradeNo), text(orderNo), shopId, status);
        return PageView.of(result, result.getRecords().stream().map(this::redemptionView).toList());
    }

    public CouponBusinessTraceView trace(String businessType, String businessNo) {
        authorize();
        String type = required(businessType).toUpperCase(java.util.Locale.ROOT);
        String number = required(businessNo);
        UserCoupon coupon = null;
        CouponTemplate template = null;
        CouponActivity activity = null;
        Redemption redemption = null;
        TradeOrder trade = null;
        OrderInfo order = null;
        RefundAllocation refund = null;
        switch (type) {
            case "USER_COUPON", "COUPON" -> coupon = coupons.selectOne(new LambdaQueryWrapper<UserCoupon>()
                    .eq(UserCoupon::getCouponNo, number));
            case "TEMPLATE", "COUPON_TEMPLATE" -> template = templates.selectOne(
                    new LambdaQueryWrapper<CouponTemplate>().eq(CouponTemplate::getTemplateNo, number));
            case "ACTIVITY", "COUPON_ACTIVITY" -> activity = activity(number);
            case "REDEMPTION" -> redemption = redemptions.selectOne(new LambdaQueryWrapper<Redemption>()
                    .eq(Redemption::getRedemptionNo, number));
            case "TRADE" -> trade = trades.selectOne(new LambdaQueryWrapper<TradeOrder>()
                    .eq(TradeOrder::getTradeNo, number));
            case "ORDER" -> order = orders.selectOne(new LambdaQueryWrapper<OrderInfo>()
                    .eq(OrderInfo::getOrderNo, number));
            case "REFUND" -> refund = refunds.selectOne(new LambdaQueryWrapper<RefundAllocation>()
                    .eq(RefundAllocation::getRefundNo, number).last("LIMIT 1"));
            default -> throw BusinessException.badRequest("VALIDATION_FAILED", "不支持的业务类型");
        }
        if (refund != null) {
            RedemptionAllocation allocation = allocations.selectById(refund.getRedemptionAllocationId());
            redemption = allocation == null ? null : redemptions.selectById(allocation.getRedemptionId());
        }
        if (order != null) trade = trades.selectById(order.getTradeId());
        if (redemption != null) {
            coupon = coupons.selectById(redemption.getUserCouponId());
            trade = trades.selectById(redemption.getTradeId());
        }
        if (coupon != null && redemption == null) {
            redemption = redemptions.selectOne(new LambdaQueryWrapper<Redemption>()
                    .eq(Redemption::getUserCouponId, coupon.getId()).orderByDesc(Redemption::getAttemptNo)
                    .last("LIMIT 1"));
            if (redemption != null) trade = trades.selectById(redemption.getTradeId());
        }
        if (coupon == null && redemption == null && trade == null && template == null && activity == null) {
            throw BusinessException.notFound("RESOURCE_NOT_FOUND", "业务记录不存在");
        }
        if (coupon != null) template = templates.selectById(coupon.getTemplateId());
        List<Map<String, Object>> events = new ArrayList<>();
        if (coupon != null) addCouponEvents(events, coupon);
        if (redemption != null) addRedemptionEvents(events, redemption);
        if (trade != null) addTradeEvents(events, trade);
        if (template != null) addTemplateEvents(events, template);
        if (template != null && template.getActivityId() != null) {
            activity = activities.selectById(template.getActivityId());
        }
        if (activity != null) addAuditEvents(events, "ACTIVITY", activity.getId());
        if (coupon != null) addAuditEvents(events, "USER_COUPON", coupon.getId());
        if (template != null) addAuditEvents(events, "TEMPLATE", template.getId());
        events.sort(java.util.Comparator.comparing(event -> (java.time.OffsetDateTime) event.get("createdAt"),
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return new CouponBusinessTraceView(type, number, events);
    }

    private OperationCouponRedemptionView redemptionView(Redemption redemption) {
        List<RedemptionAllocation> rows = allocations.selectList(new LambdaQueryWrapper<RedemptionAllocation>()
                .eq(RedemptionAllocation::getRedemptionId, redemption.getId())
                .orderByAsc(RedemptionAllocation::getOrderId));
        Long orderId = rows.stream().map(RedemptionAllocation::getOrderId).distinct().count() == 1
                ? rows.getFirst().getOrderId() : null;
        Long shopId = rows.stream().map(RedemptionAllocation::getShopId).distinct().count() == 1
                ? rows.getFirst().getShopId() : null;
        return new OperationCouponRedemptionView(id(redemption.getId()), redemption.getRedemptionNo(),
                id(redemption.getTradeId()), id(orderId), id(shopId), redemption.getStatus(),
                money(redemption.getDiscountAmount()), money(redemption.getPlatformFundedAmount()),
                money(redemption.getShopFundedAmount()), time(redemption.getCreatedAt()));
    }

    private void addCouponEvents(List<Map<String, Object>> events, UserCoupon coupon) {
        ClaimRecord claim = claims.selectOne(new LambdaQueryWrapper<ClaimRecord>()
                .eq(ClaimRecord::getUserCouponId, coupon.getId()));
        Map<String, Object> issued = event("USER_COUPON", coupon.getCouponNo(), coupon.getStatus().name(),
                coupon.getCreatedAt());
        issued.put("userCouponId", id(coupon.getId()));
        if (claim != null) {
            issued.put("claimNo", claim.getClaimNo());
            issued.put("claimSource", claim.getClaimSource().name());
            issued.put("businessNo", claim.getBusinessNo());
        }
        events.add(issued);
        budget.selectList(new LambdaQueryWrapper<BudgetLedger>()
                .eq(BudgetLedger::getUserCouponId, coupon.getId()).orderByAsc(BudgetLedger::getId))
                .forEach(row -> events.add(event("BUDGET", row.getLedgerNo(), row.getEntryType(), row.getCreatedAt())));
    }

    private void addRedemptionEvents(List<Map<String, Object>> events, Redemption redemption) {
        events.add(event("REDEMPTION", redemption.getRedemptionNo(), redemption.getStatus().name(),
                redemption.getCreatedAt()));
        allocations.selectList(new LambdaQueryWrapper<RedemptionAllocation>()
                        .eq(RedemptionAllocation::getRedemptionId, redemption.getId())
                        .orderByAsc(RedemptionAllocation::getId))
                .forEach(allocation -> {
                    Map<String, Object> value = event("ALLOCATION", id(allocation.getId()),
                            money(allocation.getDiscountAmount()), allocation.getCreatedAt());
                    value.put("orderId", id(allocation.getOrderId()));
                    value.put("orderItemId", id(allocation.getOrderItemId()));
                    events.add(value);
                    refunds.selectList(new LambdaQueryWrapper<RefundAllocation>()
                                    .eq(RefundAllocation::getRedemptionAllocationId, allocation.getId())
                                    .orderByAsc(RefundAllocation::getId))
                            .forEach(row -> events.add(event("REFUND", row.getRefundNo(),
                                    money(row.getCouponDiscountReversalAmount()), row.getCreatedAt())));
                });
    }

    private void addTradeEvents(List<Map<String, Object>> events, TradeOrder trade) {
        events.add(event("TRADE", trade.getTradeNo(), trade.getTradeStatus().name(), trade.getCreatedAt()));
        orders.selectList(new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getTradeId, trade.getId())
                        .orderByAsc(OrderInfo::getId))
                .forEach(order -> events.add(event("ORDER", order.getOrderNo(),
                        order.getOrderStatus().name(), order.getCreatedAt())));
    }

    private void addTemplateEvents(List<Map<String, Object>> events, CouponTemplate template) {
        events.add(event("TEMPLATE", template.getTemplateNo(), template.getStatus().name(),
                template.getCreatedAt()));
    }

    private void addAuditEvents(List<Map<String, Object>> events, String resourceType, long resourceId) {
        operationLogs.selectList(new LambdaQueryWrapper<OperationLog>()
                        .eq(OperationLog::getResourceType, resourceType)
                        .eq(OperationLog::getResourceId, resourceId)
                        .orderByAsc(OperationLog::getCreatedAt).orderByAsc(OperationLog::getId))
                .forEach(log -> {
                    Map<String, Object> value = event("AUDIT_" + resourceType, log.getOperationType(),
                            log.getToStatus(), log.getCreatedAt());
                    value.put("fromStatus", log.getFromStatus());
                    value.put("operatorType", log.getOperatorType());
                    value.put("operatorId", id(log.getOperatorId()));
                    value.put("shopId", id(log.getShopId()));
                    value.put("reason", log.getReason());
                    if (log.getChangeSummaryJson() != null) value.put("changes", log.getChangeSummaryJson());
                    events.add(value);
                });
    }

    private CouponActivity activity(String activityNo) {
        return activities.selectOne(new LambdaQueryWrapper<CouponActivity>()
                .eq(CouponActivity::getActivityNo, activityNo));
    }

    private Map<String, Object> event(String resourceType, String businessNo, String status,
                                      java.time.LocalDateTime createdAt) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("resourceType", resourceType);
        event.put("businessNo", businessNo);
        event.put("status", status);
        event.put("createdAt", time(createdAt));
        return event;
    }

    private UserCouponStatus displayStatus(UserCoupon coupon) {
        return coupon.getStatus() == UserCouponStatus.AVAILABLE
                && !java.time.LocalDateTime.now().isBefore(coupon.getValidTo())
                ? UserCouponStatus.EXPIRED : coupon.getStatus();
    }

    private void authorize() { currentUser.requirePermission("platform:coupon:read"); }
    private String text(String value) { return Formatters.trimToNull(value); }
    private String required(String value) {
        String result = text(value);
        if (result == null) throw BusinessException.badRequest("VALIDATION_FAILED", "业务类型和业务编号必填");
        return result;
    }
    private void validatePage(long page, long pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
        }
    }
}
