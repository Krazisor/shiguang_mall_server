package org.dhu.shiguang_market.aftersale.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonSetter;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;

public final class AfterSaleDtos {
    private AfterSaleDtos() {
    }

    // ─── 请求 DTO ───

    public record CreateAfterSaleRequest(
            @NotBlank String orderId,
            @NotBlank String orderItemId,
            @NotNull AfterSaleType requestType,
            @Min(1) int quantity,
            @NotBlank @Size(max = 50) String reasonCode,
            @Size(max = 500) String reasonDescription,
            @Size(max = 9) List<String> evidenceUrls,
            @NotBlank String requestedAmount) {
    }

    public record ReturnShipmentRequest(
            @NotBlank @Size(max = 32) String carrierCode,
            @NotBlank @Size(max = 128) String carrierName,
            @NotBlank @Size(max = 128) String trackingNo) {
    }

    public static final class UpdateReturnShipmentRequest {
        private String carrierCode;
        private String carrierName;
        private String trackingNo;
        private Integer version;
        private boolean carrierCodePresent;
        private boolean carrierNamePresent;
        private boolean trackingNoPresent;

        @JsonSetter("carrierCode")
        public void setCarrierCode(String value) { this.carrierCode = value; this.carrierCodePresent = true; }

        @JsonSetter("carrierName")
        public void setCarrierName(String value) { this.carrierName = value; this.carrierNamePresent = true; }

        @JsonSetter("trackingNo")
        public void setTrackingNo(String value) { this.trackingNo = value; this.trackingNoPresent = true; }

        @JsonSetter("version")
        public void setVersion(Integer value) { this.version = value; }

        public String carrierCode() { return carrierCode; }
        public String carrierName() { return carrierName; }
        public String trackingNo() { return trackingNo; }
        public Integer version() { return version; }
        public boolean hasCarrierCode() { return carrierCodePresent; }
        public boolean hasCarrierName() { return carrierNamePresent; }
        public boolean hasTrackingNo() { return trackingNoPresent; }
    }

    public record ApproveAfterSaleRequest(
            @Min(1) int approvedQuantity,
            @NotBlank String approvedAmount,
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
            @Size(max = 500) String remark,
            @NotNull Integer version) {
    }

    // ─── 嵌套视图 ───

    public record AfterSaleOrderSnapshot(
            String id, String orderNo, OrderStatus orderStatus) {
    }

    public record AfterSaleShopSnapshot(
            String id, String shopNo, String shopName, String logoUrl, String status) {
    }

    public record AfterSaleItemSnapshot(
            String id, String productName, String skuName, Map<String, String> spec,
            String imageUrl, String unitPrice, int purchasedQuantity) {
    }

    public record AfterSaleReviewView(
            String reviewerId, String comment, OffsetDateTime reviewedAt) {
    }

    public record ReturnShipmentSnapshot(
            String carrierCode, String carrierName, String trackingNo, OffsetDateTime returnedAt) {
    }

    // ─── 买家端视图 ───

    public record AfterSaleEligibilityView(
            String orderId, String orderItemId, OrderStatus orderStatus,
            int purchasedQuantity, int refundedQuantity, int occupiedQuantity,
            int maximumRequestQuantity, String itemPayableAmount, String refundedAmount,
            String occupiedAmount, String maximumRequestAmount,
            List<AfterSaleType> supportedTypes, OffsetDateTime eligibleUntil,
            boolean eligible, String ineligibleReason) {
    }

    public record AfterSaleSummaryView(
            String id, String afterSaleNo, AfterSaleType requestType, AfterSaleStatus status,
            RefundStatus refundStatus, AfterSaleOrderSnapshot order, int quantity,
            String requestedAmount, Integer approvedQuantity, String approvedAmount,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record AfterSaleDetailView(
            String id, String afterSaleNo, AfterSaleType requestType, AfterSaleStatus status,
            RefundStatus refundStatus, AfterSaleOrderSnapshot order, AfterSaleShopSnapshot shop,
            AfterSaleItemSnapshot item, int quantity, String reasonCode, String reasonDescription,
            List<String> evidenceUrls, String requestedAmount, Integer approvedQuantity,
            String approvedAmount, AfterSaleReviewView review,
            ReturnShipmentSnapshot returnShipment, String refundNo,
            String refundFailureReason, OffsetDateTime refundedAt, OffsetDateTime completedAt,
            OffsetDateTime cancelledAt, Integer version, OffsetDateTime createdAt,
            OffsetDateTime updatedAt, List<String> availableActions) {
    }

    // ─── 商家端视图 ───

    public record ShopAfterSaleSummaryView(
            String id, String afterSaleNo, AfterSaleType requestType, AfterSaleStatus status,
            RefundStatus refundStatus, AfterSaleOrderSnapshot order,
            AfterSaleItemSnapshot item, int quantity, String requestedAmount,
            Integer approvedQuantity, String approvedAmount,
            String buyerId, String buyerName,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record ShopAfterSaleDetailView(
            String id, String afterSaleNo, AfterSaleType requestType, AfterSaleStatus status,
            RefundStatus refundStatus, AfterSaleOrderSnapshot order, AfterSaleShopSnapshot shop,
            AfterSaleItemSnapshot item, int quantity, String reasonCode, String reasonDescription,
            List<String> evidenceUrls, String requestedAmount, Integer approvedQuantity,
            String approvedAmount, AfterSaleReviewView review,
            ReturnShipmentSnapshot returnShipment, String refundNo,
            String refundFailureReason, OffsetDateTime refundedAt, OffsetDateTime completedAt,
            OffsetDateTime cancelledAt, Integer version, String buyerId, String buyerName,
            OffsetDateTime createdAt, OffsetDateTime updatedAt,
            List<String> availableActions) {
    }
}
