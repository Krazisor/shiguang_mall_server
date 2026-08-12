package org.dhu.shiguang_market.aftersale.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.dhu.shiguang_market.common.api.CommonViews.ShopSummary;
import org.dhu.shiguang_market.common.api.CommonViews.UserSummary;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleType;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleAppealDecision;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleAppealStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleAppealTriggerType;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantNotificationType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.OperatorBrief;

public final class AfterSaleDtos {
    private AfterSaleDtos() {
    }

    // ─── 请求 DTO ───

    public record CreateAfterSaleRequest(
            @NotBlank String orderId,
            @NotBlank String orderItemId,
            @NotNull AfterSaleType requestType,
            @Min(1) int quantity,
            @NotBlank @Size(max = 30) String reasonCode,
            @Size(max = 500) String reasonDescription,
            @Size(max = 9) List<String> evidenceUrls,
            @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,15})\\.[0-9]{2}$") String requestedAmount) {
    }

    public record ReturnShipmentRequest(
            @NotBlank @Size(max = 64) String carrierCode,
            @NotBlank @Size(max = 128) String carrierName,
            @NotBlank @Size(max = 128) String trackingNo) {
    }

    public record UpdateReturnShipmentRequest(
            @NotBlank @Size(max = 64) String carrierCode,
            @NotBlank @Size(max = 128) String carrierName,
            @NotBlank @Size(max = 128) String trackingNo,
            @NotNull Integer version) {
    }

    public record ApproveAfterSaleRequest(
            @Min(1) int approvedQuantity,
            @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,15})\\.[0-9]{2}$") String approvedAmount,
            @Size(max = 500) String reviewComment,
            @NotNull Integer version) {
    }

    public record RejectAfterSaleRequest(
            @NotBlank @Size(max = 500) String reviewComment,
            @NotNull Integer version) {
    }

    public record ConfirmReturnReceivedRequest(
            @Size(max = 500) String remark,
            @NotNull Integer version) {
    }

    public record RetryRefundRequest(
            @NotBlank @Size(max = 500) String remark,
            @NotNull Integer version) {
    }

    public record CreateAfterSaleAppealRequest(
            @NotBlank @Size(max = 30) String reasonCode,
            @NotBlank @Size(max = 500) String reasonDescription,
            @Size(max = 9) List<String> evidenceUrls,
            @NotNull @Min(0) Integer version) {
    }

    public record DecideAfterSaleAppealRequest(
            @NotNull AfterSaleAppealDecision decision,
            @Min(1) Integer approvedQuantity,
            @Pattern(regexp = "^(0|[1-9][0-9]{0,15})\\.[0-9]{2}$") String approvedAmount,
            @NotBlank @Size(max = 500) String reviewComment,
            @NotNull @Min(0) Integer version) {
    }

    // ─── 嵌套视图 ───

    public record AfterSaleOrderSnapshot(
            String id, String orderNo, OrderStatus orderStatus) {
    }

    public record AfterSaleItemSnapshot(
            String id, String productName, String skuName, Map<String, String> spec,
            String imageUrl, String unitPrice, int purchasedQuantity) {
    }

    public record AfterSaleReviewView(
            String reviewerId, String comment, OffsetDateTime reviewedAt) {
    }

    public record ReturnShipmentView(
            String carrierCode, String carrierName, String trackingNo,
            OffsetDateTime returnedAt, OffsetDateTime receivedAt) {
    }

    public record AfterSaleAppealAfterSaleView(
            String afterSaleId, String afterSaleNo, AfterSaleType requestType,
            AfterSaleStatus status, RefundStatus refundStatus,
            AfterSaleOrderSnapshot order, String requestedAmount, String approvedAmount) {
    }

    public record AfterSaleAppealSummaryView(
            String id, String appealNo, String afterSaleId, String afterSaleNo,
            AfterSaleAppealTriggerType triggerType, AfterSaleAppealStatus status,
            OffsetDateTime createdAt, OffsetDateTime decidedAt) {
    }

    public record AfterSaleAppealDetailView(
            String id, String appealNo, AfterSaleAppealAfterSaleView afterSale,
            AfterSaleAppealTriggerType triggerType, AfterSaleAppealStatus status,
            String reasonCode, String reasonDescription, List<String> evidenceUrls,
            AfterSaleReviewView merchantReview, AfterSaleAppealDecision decision,
            Integer approvedQuantity, String approvedAmount, OperatorBrief decidedBy,
            String decisionComment, OffsetDateTime decidedAt, Integer version,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record PlatformAfterSaleAppealSummaryView(
            String id, String appealNo, String afterSaleId, String afterSaleNo,
            AfterSaleAppealTriggerType triggerType, AfterSaleAppealStatus status,
            ShopSummary shop, UserSummary buyer, AfterSaleType requestType,
            String requestedAmount, OffsetDateTime createdAt, OffsetDateTime decidedAt) {
    }

    public record PlatformAfterSaleAppealDetailView(
            String id, String appealNo, AfterSaleAppealAfterSaleView afterSale,
            AfterSaleAppealTriggerType triggerType, AfterSaleAppealStatus status,
            String reasonCode, String reasonDescription, List<String> evidenceUrls,
            AfterSaleReviewView merchantReview, AfterSaleAppealDecision decision,
            Integer approvedQuantity, String approvedAmount, OperatorBrief decidedBy,
            String decisionComment, OffsetDateTime decidedAt, Integer version,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, ShopSummary shop,
            UserSummary buyer, AfterSaleOrderSnapshot order, AfterSaleItemSnapshot item) {
    }

    public record MerchantNotificationView(
            String id, MerchantNotificationType notificationType, String appealId,
            String appealNo, String afterSaleId, String afterSaleNo, String title,
            String content, OffsetDateTime readAt, OffsetDateTime createdAt) {
    }

    // ─── 买家端视图 ───

    public record CouponRestoreHintView(boolean restorableOnlyAfterFullTradeRefund,
                                        boolean currentRequestWillRestore, String reason) { }

    public record AfterSaleEligibilityView(
            String orderId, String orderItemId, OrderStatus orderStatus,
            int purchasedQuantity, int refundedQuantity, int occupiedQuantity,
            int maximumRequestQuantity, String itemGrossAmount, String couponDiscountAmount,
            String itemPayableAmount, String refundedAmount,
            String occupiedAmount, String maximumRequestAmount,
            CouponRestoreHintView couponRestoreHint,
            List<AfterSaleType> supportedTypes, OffsetDateTime eligibleUntil,
            boolean eligible, String ineligibleReason) {
    }

    public record AfterSaleSummaryView(
            String id, String afterSaleNo, AfterSaleType requestType, AfterSaleStatus status,
            RefundStatus refundStatus, AfterSaleOrderSnapshot order, ShopSummary shop,
            AfterSaleItemSnapshot item, int quantity,
            String requestedAmount, String approvedAmount,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record AfterSaleDetailView(
            String id, String afterSaleNo, AfterSaleType requestType, AfterSaleStatus status,
            RefundStatus refundStatus, AfterSaleOrderSnapshot order, ShopSummary shop,
            AfterSaleItemSnapshot item, int quantity, String reasonCode, String reasonDescription,
            List<String> evidenceUrls, String requestedAmount, Integer approvedQuantity,
            String approvedAmount, AfterSaleReviewView review,
            ReturnShipmentView returnShipment, AfterSaleAppealSummaryView appeal, String refundNo,
            String refundFailureReason, OffsetDateTime refundedAt, OffsetDateTime completedAt,
            OffsetDateTime cancelledAt, Integer version, OffsetDateTime createdAt,
            OffsetDateTime updatedAt, List<String> availableActions) {
        public AfterSaleDetailView(String id, String afterSaleNo, AfterSaleType requestType,
                                   AfterSaleStatus status, RefundStatus refundStatus,
                                   AfterSaleOrderSnapshot order, ShopSummary shop,
                                   AfterSaleItemSnapshot item, int quantity, String reasonCode,
                                   String reasonDescription, List<String> evidenceUrls,
                                   String requestedAmount, Integer approvedQuantity, String approvedAmount,
                                   AfterSaleReviewView review, ReturnShipmentView returnShipment,
                                   String refundNo, String refundFailureReason, OffsetDateTime refundedAt,
                                   OffsetDateTime completedAt, OffsetDateTime cancelledAt, Integer version,
                                   OffsetDateTime createdAt, OffsetDateTime updatedAt,
                                   List<String> availableActions) {
            this(id, afterSaleNo, requestType, status, refundStatus, order, shop, item, quantity,
                    reasonCode, reasonDescription, evidenceUrls, requestedAmount, approvedQuantity,
                    approvedAmount, review, returnShipment, null, refundNo, refundFailureReason,
                    refundedAt, completedAt, cancelledAt, version, createdAt, updatedAt, availableActions);
        }
    }

    // ─── 商家端视图 ───

    public record ShopAfterSaleSummaryView(
            String id, String afterSaleNo, AfterSaleType requestType, AfterSaleStatus status,
            RefundStatus refundStatus, AfterSaleOrderSnapshot order, ShopSummary shop,
            AfterSaleItemSnapshot item, int quantity, String requestedAmount,
            String approvedAmount, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            UserSummary buyer) {
    }

    public record ShopAfterSaleDetailView(
            String id, String afterSaleNo, AfterSaleType requestType, AfterSaleStatus status,
            RefundStatus refundStatus, AfterSaleOrderSnapshot order, ShopSummary shop,
            AfterSaleItemSnapshot item, int quantity, String reasonCode, String reasonDescription,
            List<String> evidenceUrls, String requestedAmount, Integer approvedQuantity,
            String approvedAmount, AfterSaleReviewView review,
            ReturnShipmentView returnShipment, AfterSaleAppealSummaryView appeal, String refundNo,
            String refundFailureReason, OffsetDateTime refundedAt, OffsetDateTime completedAt,
            OffsetDateTime cancelledAt, Integer version,
            OffsetDateTime createdAt, OffsetDateTime updatedAt,
            List<String> availableActions, UserSummary buyer,
            AfterSaleEligibilityView eligibilityAtReview) {
        public ShopAfterSaleDetailView(String id, String afterSaleNo, AfterSaleType requestType,
                                       AfterSaleStatus status, RefundStatus refundStatus,
                                       AfterSaleOrderSnapshot order, ShopSummary shop,
                                       AfterSaleItemSnapshot item, int quantity, String reasonCode,
                                       String reasonDescription, List<String> evidenceUrls,
                                       String requestedAmount, Integer approvedQuantity, String approvedAmount,
                                       AfterSaleReviewView review, ReturnShipmentView returnShipment,
                                       String refundNo, String refundFailureReason, OffsetDateTime refundedAt,
                                       OffsetDateTime completedAt, OffsetDateTime cancelledAt, Integer version,
                                       OffsetDateTime createdAt, OffsetDateTime updatedAt,
                                       List<String> availableActions, UserSummary buyer,
                                       AfterSaleEligibilityView eligibilityAtReview) {
            this(id, afterSaleNo, requestType, status, refundStatus, order, shop, item, quantity,
                    reasonCode, reasonDescription, evidenceUrls, requestedAmount, approvedQuantity,
                    approvedAmount, review, returnShipment, null, refundNo, refundFailureReason,
                    refundedAt, completedAt, cancelledAt, version, createdAt, updatedAt,
                    availableActions, buyer, eligibilityAtReview);
        }
    }
}
