package org.dhu.shiguang_market.task.dto;

import java.time.OffsetDateTime;
import java.util.List;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleItemSnapshot;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleOrderSnapshot;
import org.dhu.shiguang_market.common.api.CommonViews.ShopSummary;
import org.dhu.shiguang_market.common.api.CommonViews.UserSummary;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderDisplayStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.PaymentOrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.TradeStatus;
import org.dhu.shiguang_market.order.dto.OrderDtos.OrderItemView;
import org.dhu.shiguang_market.order.dto.OrderDtos.OrderItemSummaryView;
import org.dhu.shiguang_market.order.dto.OrderDtos.OrderStatusHistoryView;
import org.dhu.shiguang_market.order.dto.OrderDtos.ShippingView;

/** 阶段五平台运营只读 DTO，字段与 API DTO 清单保持一致。 */
public final class OperationDtos {
    private OperationDtos() {
    }

    public record OperationTradeView(
            String id, String tradeNo, UserSummary user, TradeStatus tradeStatus,
            String payableAmount, int orderCount, OffsetDateTime createdAt) {
    }

    public record OperationOrderView(
            String id, String orderNo, String tradeId, String tradeNo, ShopSummary shop,
            OrderStatus orderStatus, OrderPaymentStatus paymentStatus, String payableAmount,
            String refundAmount, List<OrderItemSummaryView> itemSummary, int itemKinds,
            int totalQuantity, OffsetDateTime createdAt, List<String> availableActions,
            UserSummary buyer) {
    }

    /** 平台订单只读详情，不包含收货地址、买家备注或任何可执行动作。 */
    public record OperationOrderDetailView(
            String id, String orderNo, String tradeId, String tradeNo,
            ShopSummary shop, UserSummary buyer,
            OrderStatus orderStatus, OrderDisplayStatus displayStatus, OrderPaymentStatus paymentStatus,
            String itemAmount, String freightAmount, String payableAmount, String refundAmount,
            OffsetDateTime createdAt, OffsetDateTime payExpireAt, OffsetDateTime paidAt,
            OffsetDateTime completedAt, OffsetDateTime cancelledAt,
            ShippingView shipping, List<OrderItemView> items, List<OrderStatusHistoryView> history) {
    }

    public record OperationPaymentView(
            String id, String paymentNo, String tradeId, String amount, PaymentOrderStatus status,
            String failureReason, OffsetDateTime paidAt, OffsetDateTime expiresAt,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, String tradeNo, UserSummary user) {
    }

    public record OperationAfterSaleView(
            String id, String afterSaleNo, AfterSaleType requestType, AfterSaleStatus status,
            RefundStatus refundStatus, AfterSaleOrderSnapshot order, ShopSummary shop,
            AfterSaleItemSnapshot item, int quantity, String requestedAmount, String approvedAmount,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, UserSummary buyer) {
    }

    /** 业务追踪只返回定位所需摘要，不暴露手机号、地址等敏感字段。 */
    public record BusinessTraceLink(
            String resourceType, String resourceId, String businessNo,
            String status, OffsetDateTime createdAt) {
    }

    public record BusinessTraceView(
            String businessType, String businessNo, BusinessTraceLink trade,
            List<BusinessTraceLink> orders, List<BusinessTraceLink> payments,
            List<BusinessTraceLink> afterSales, List<BusinessTraceLink> inventoryTransactions,
            List<BusinessTraceLink> walletTransactions) {
    }
}
