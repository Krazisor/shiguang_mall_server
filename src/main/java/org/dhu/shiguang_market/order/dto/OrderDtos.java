package org.dhu.shiguang_market.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.dhu.shiguang_market.common.api.CommonViews.AddressSnapshot;
import org.dhu.shiguang_market.common.api.CommonViews.ShopSummary;
import org.dhu.shiguang_market.common.api.CommonViews.UserSummary;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderOperationType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderDisplayStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ReservationStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.TradeStatus;

public final class OrderDtos {
    private OrderDtos() {
    }

    public record CancelTradeRequest(@NotBlank @Size(max = 255) String reason) {
    }

    public record ShipOrderRequest(
            @NotBlank @Size(max = 64) String carrierCode,
            @NotBlank @Size(max = 128) String carrierName,
            @NotBlank @Size(max = 128) String trackingNo) {
    }

    public record OrderItemSummaryView(String productName, String skuName, String imageUrl, int quantity) {
    }

    public record OrderSummaryView(
            String id, String orderNo, String tradeId, String tradeNo, ShopSummary shop,
            OrderStatus orderStatus, OrderDisplayStatus displayStatus, OrderPaymentStatus paymentStatus, String payableAmount,
            String refundAmount, List<OrderItemSummaryView> itemSummary, int itemKinds,
            int totalQuantity, OffsetDateTime createdAt, List<String> availableActions) {
    }

    public record ShopOrderSummaryView(
            String id, String orderNo, String tradeId, String tradeNo, ShopSummary shop,
            OrderStatus orderStatus, OrderDisplayStatus displayStatus, OrderPaymentStatus paymentStatus, String payableAmount,
            String refundAmount, List<OrderItemSummaryView> itemSummary, int itemKinds,
            int totalQuantity, OffsetDateTime createdAt, List<String> availableActions,
            UserSummary buyer) {
    }

    public record ShippingView(
            String carrierCode, String carrierName, String trackingNo, OffsetDateTime shippedAt) {
    }

    public record OrderItemView(
            String id, String spuId, String skuId, String spuNo, String skuNo,
            String productName, String skuName, Map<String, String> spec, String imageUrl,
            String unitPrice, int quantity, String originalAmount, String freightAmount,
            String payableAmount, int refundedQuantity, String refundedAmount,
            ReservationStatus reservationStatus) {
    }

    public record OrderStatusHistoryView(
            OrderStatus fromStatus, OrderStatus toStatus, OrderOperationType operationType,
            OperatorType operatorType, String remark, OffsetDateTime createdAt) {
    }

    public record OrderDetailView(
            String id, String orderNo, String tradeId, String tradeNo, ShopSummary shop,
            OrderStatus orderStatus, OrderDisplayStatus displayStatus, OrderPaymentStatus paymentStatus, String itemAmount,
            String freightAmount, String payableAmount, String refundAmount, String buyerRemark,
            AddressSnapshot address, ShippingView shipping, List<OrderItemView> items,
            List<OrderStatusHistoryView> history, List<String> availableActions) {
    }

    public record TradeDetailView(
            String id, String tradeNo, TradeStatus tradeStatus, String payableAmount,
            AddressSnapshot address, OffsetDateTime payExpireAt, OffsetDateTime paidAt,
            OffsetDateTime cancelledAt, List<OrderSummaryView> orders, List<String> availableActions) {
    }
}
