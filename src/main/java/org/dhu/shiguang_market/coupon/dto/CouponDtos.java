package org.dhu.shiguang_market.coupon.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.dhu.shiguang_market.common.api.CommonViews.ShopSummary;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponAudienceType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponDistributionType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponFundingDecision;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponFundingParticipationStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponFundingType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponOwnerType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRedeemCodeStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRedemptionStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRestorePolicy;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponSelectionMode;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponStackMode;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponTemplateStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponValidityType;
import org.dhu.shiguang_market.common.model.MarketEnums.UserCouponStatus;

public final class CouponDtos {
    private CouponDtos() {
    }

    public record VersionRequest(@NotNull @Min(0) Integer version) {
    }

    public record ReasonVersionRequest(@NotBlank @Size(max = 500) String reason,
                                       @NotNull @Min(0) Integer version) {
    }

    public record RedeemCouponCodeRequest(@NotBlank @Size(min = 8, max = 64) String code) {
    }

    public static final class CopyCouponTemplateRequest {
        @NotBlank
        @Size(max = 128)
        private String couponName;
        private String activityId;
        private boolean copyScope;
        @NotNull
        @Min(0)
        private Integer version;
        private boolean activityIdPresent;

        @JsonSetter("couponName")
        public void setCouponName(String value) {
            couponName = value;
        }

        @JsonSetter("activityId")
        public void setActivityId(String value) {
            activityId = value;
            activityIdPresent = true;
        }

        @JsonSetter("copyScope")
        public void setCopyScope(boolean value) {
            copyScope = value;
        }

        @JsonSetter("version")
        public void setVersion(Integer value) {
            version = value;
        }

        public String couponName() {
            return couponName;
        }

        public String activityId() {
            return activityId;
        }

        public boolean copyScope() {
            return copyScope;
        }

        public Integer version() {
            return version;
        }

        public boolean hasActivityId() {
            return activityIdPresent;
        }
    }

    public static final class UpdateCouponPresentationRequest {
        private String couponName;
        private String description;
        private Integer sortOrder;
        @NotNull
        @Min(0)
        private Integer version;
        private boolean couponNamePresent;
        private boolean descriptionPresent;
        private boolean sortOrderPresent;

        @JsonSetter("couponName")
        public void setCouponName(String value) {
            couponName = value;
            couponNamePresent = true;
        }

        @JsonSetter("description")
        public void setDescription(String value) {
            description = value;
            descriptionPresent = true;
        }

        @JsonSetter("sortOrder")
        public void setSortOrder(Integer value) {
            sortOrder = value;
            sortOrderPresent = true;
        }

        @JsonSetter("version")
        public void setVersion(Integer value) {
            version = value;
        }

        public String couponName() {
            return couponName;
        }

        public String description() {
            return description;
        }

        public Integer sortOrder() {
            return sortOrder;
        }

        public Integer version() {
            return version;
        }

        public boolean hasCouponName() {
            return couponNamePresent;
        }

        public boolean hasDescription() {
            return descriptionPresent;
        }

        public boolean hasSortOrder() {
            return sortOrderPresent;
        }
    }

    public record CreateCouponActivityRequest(
            @NotBlank @Size(max = 128) String activityName, @Size(max = 255) String subtitle,
            @Size(max = 1024) String bannerUrl, @NotNull CouponActivityType activityType,
            @NotNull OffsetDateTime startsAt, @NotNull OffsetDateTime endsAt) {
    }

    public record UpdateCouponActivityRequest(
            @NotBlank @Size(max = 128) String activityName, @Size(max = 255) String subtitle,
            @Size(max = 1024) String bannerUrl, @NotNull CouponActivityType activityType,
            @NotNull OffsetDateTime startsAt, @NotNull OffsetDateTime endsAt,
            @NotNull @Min(0) Integer version) {
    }

    public record ScopeRequest(
            @NotNull CouponScopeType scopeType, List<String> shopIds, List<String> categoryIds,
            List<String> spuIds, List<String> skuIds, @NotNull @Min(0) Integer version) {
    }

    public record ValidityRequest(@NotNull CouponValidityType validityType, OffsetDateTime validFrom,
                                  OffsetDateTime validTo, @Min(0) Integer effectiveDelayMinutes,
                                  @Min(1) Integer validForHours) {
    }

    public record CreateCouponTemplateRequest(
            String activityId, @NotBlank @Size(max = 128) String couponName,
            @Size(max = 500) String description, @NotNull CouponType couponType,
            @NotNull @DecimalMin("0.00") String thresholdAmount, String discountAmount,
            String percentageOff, String maximumDiscountAmount, CouponOwnerType ownerType,
            CouponFundingType fundingType, String platformShareRate, @Valid @NotNull ScopeRequest scope,
            @NotNull CouponDistributionType distributionType, @NotNull CouponAudienceType audienceType,
            @Min(1) Integer newUserWithinDays, OffsetDateTime claimStartsAt, OffsetDateTime claimEndsAt,
            @Valid @NotNull ValidityRequest validity, @Min(1) int totalIssueLimit,
            @Min(1) @Max(99) int perUserLimit, @NotNull CouponStackMode stackMode,
            @NotNull CouponRestorePolicy refundRestorePolicy, @NotBlank String budgetAmount,
            int sortOrder) {
    }

    public record UpdateCouponTemplateRequest(
            String activityId, @NotBlank @Size(max = 128) String couponName,
            @Size(max = 500) String description, @NotNull CouponType couponType,
            String thresholdAmount, String discountAmount, String percentageOff,
            String maximumDiscountAmount, CouponOwnerType ownerType, CouponFundingType fundingType,
            String platformShareRate, @Valid @NotNull ScopeRequest scope,
            @NotNull CouponDistributionType distributionType, @NotNull CouponAudienceType audienceType,
            Integer newUserWithinDays, OffsetDateTime claimStartsAt, OffsetDateTime claimEndsAt,
            @Valid @NotNull ValidityRequest validity, @Min(1) int totalIssueLimit,
            @Min(1) @Max(99) int perUserLimit, @NotNull CouponStackMode stackMode,
            @NotNull CouponRestorePolicy refundRestorePolicy, @NotBlank String budgetAmount,
            int sortOrder, @NotNull @Min(0) Integer version) {
    }

    public record ClaimableActivitySummaryView(String id, String activityNo, CouponActivityType activityType,
                                               String activityName, String subtitle, String bannerUrl,
                                               CouponOwnerType ownerType, ShopSummary shop,
                                               CouponActivityStatus status, OffsetDateTime startsAt,
                                               OffsetDateTime endsAt, OffsetDateTime serverTime, int templateCount) {
    }

    public record ClaimableActivityDetailView(ClaimableActivitySummaryView activity,
                                              List<ClaimableTemplateView> templates) {
    }

    public record ClaimableTemplateView(TemplateView template, CouponDistributionType distributionType,
                                        OffsetDateTime claimStartsAt, OffsetDateTime claimEndsAt,
                                        String remainingState, Integer remainingQuantity, int claimedCountByUser,
                                        int perUserLimit, boolean claimable, String unclaimableReason) {
    }

    public record BenefitView(String thresholdAmount, String discountAmount, String percentageOff,
                              String maximumDiscountAmount, String displayText) {
    }

    public record ScopeView(CouponScopeType scopeType, String summary, List<String> shopIds,
                            List<String> categoryIds, List<String> spuIds, List<String> skuIds) {
    }

    public record CouponAdminScopeView(CouponScopeType scopeType, String summary,
                                       List<CouponScopeTargetView> targets, int targetCount) {
    }

    public record ValidityView(CouponValidityType validityType, OffsetDateTime validFrom,
                               OffsetDateTime validTo, Integer effectiveDelayMinutes, Integer validForHours,
                               String summary) {
    }

    public record TemplateView(String id, String templateNo, String couponName, CouponOwnerType ownerType,
                               ShopSummary shop, CouponType couponType, BenefitView benefit, ScopeView scope,
                               ValidityView validity, CouponStackMode stackMode, String description) {
    }

    public record UserCouponSummaryView(String id, String couponNo, TemplateView template,
                                        UserCouponStatus status, UserCouponStatus displayStatus,
                                        OffsetDateTime validFrom, OffsetDateTime validTo,
                                        String claimSource, List<String> availableActions) {
    }

    public record UserCouponDetailView(String id, String couponNo, TemplateView template,
                                       UserCouponStatus status, UserCouponStatus displayStatus,
                                       OffsetDateTime validFrom, OffsetDateTime validTo,
                                       String lockedTradeId, String claimSource, OffsetDateTime claimedAt,
                                       OffsetDateTime usedAt, int restoreCount, OffsetDateTime lastRestoredAt,
                                       String unavailableReason, List<String> availableActions) {
    }

    public record CouponScopeTargetView(CouponScopeType scopeType, String targetId,
                                        String targetNo, String targetName, String shopId) {
    }

    public record CouponActivityAdminView(String id, String activityNo, CouponOwnerType ownerType,
                                          ShopSummary shop, CouponActivityType activityType,
                                          String activityName, String subtitle, String bannerUrl,
                                          OffsetDateTime startsAt, OffsetDateTime endsAt,
                                          CouponActivityStatus status, String pauseSource, String pauseReason,
                                          int templateCount, int issuedCount, int consumedCount,
                                          String couponDiscountAmount, int version, String createdBy,
                                          String updatedBy, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                                          List<String> availableActions) {
    }

    public record CouponTemplateAdminSummaryView(String id, String templateNo, String couponName,
                                                 CouponOwnerType ownerType, ShopSummary ownerShop,
                                                 CouponType couponType, CouponDistributionType distributionType,
                                                 CouponTemplateStatus status, int issuedCount,
                                                 int totalIssueLimit, String budgetAmount, int version) {
    }

    public record CouponTemplateAdminDetailView(
            String id, String templateNo, CouponActivityAdminView activity,
            CouponOwnerType ownerType, ShopSummary ownerShop, String couponName,
            String description, CouponType couponType, BenefitView benefit,
            CouponFundingType fundingType, String platformShareRate,
            List<CouponFundingParticipationView> fundingParticipations, CouponAdminScopeView scope,
            CouponDistributionType distributionType, CouponAudienceType audienceType,
            Integer newUserWithinDays, OffsetDateTime claimStartsAt, OffsetDateTime claimEndsAt,
            ValidityView validity, int totalIssueLimit, int issuedCount, int remainingIssueQuantity,
            int perUserLimit, CouponStackMode stackMode, CouponRestorePolicy refundRestorePolicy,
            String budgetAmount, String budgetReservedAmount, String budgetConsumedAmount,
            String budgetReversedAmount, CouponTemplateStatus status, OffsetDateTime firstIssuedAt,
            int sortOrder, int version, String createdBy, String updatedBy,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, List<String> availableActions) {
    }

    public record CouponFundingParticipationView(String id, String templateId, String templateNo, String shopId,
                                                 ShopSummary shop, String platformShareRate, String shopShareRate,
                                                 CouponFundingParticipationStatus status, String invitedBy,
                                                 OffsetDateTime invitedAt, String decidedBy, OffsetDateTime decidedAt,
                                                 String decisionReason, int version, List<String> availableActions) {
    }

    public record CouponFundingInvitationBatchView(List<CouponFundingParticipationView> items) {
    }

    public record DecideCouponFundingRequest(@NotNull CouponFundingDecision decision, String reason,
                                             @NotNull @Min(0) Integer version) {
    }

    public record SendFundingInvitationRequest(@NotEmpty @Size(max = 1000) List<String> shopIds,
                                               @NotNull @Min(0) Integer version) {
    }

    public record GrantCouponsRequest(@NotEmpty @Size(max = 100) List<String> userIds,
                                      @NotBlank @Size(max = 500) String reason,
                                      @Size(max = 128) String externalReference) {
    }

    public record CouponGrantResult(String userId, boolean success, String userCouponId,
                                    String couponNo, String errorCode) {
    }

    public record BatchCouponGrantView(String templateId, int requested, int succeeded, int failed,
                                       List<CouponGrantResult> results) {
    }

    public record CreateRedeemCodeBatchRequest(@Min(1) @Max(500) int quantity,
                                               @Size(max = 16) String codePrefix,
                                               @NotBlank @Size(max = 500) String reason) {
    }

    public record CouponCodeBatchCreatedView(String batchNo, String templateId, int quantity,
                                             List<String> codes) {
    }

    public record CouponCodeBatchSummaryView(String batchNo, String templateId, CouponRedeemCodeStatus status,
                                             int total, int active, int redeemed, int revoked,
                                             OffsetDateTime createdAt) {
    }

    public record OperationUserCouponView(String id, String couponNo, String templateNo, String userId,
                                          UserCouponStatus status, OffsetDateTime validTo) {
    }

    public record OperationCouponRedemptionView(String id, String redemptionNo, String tradeId,
                                                String orderId, String shopId, CouponRedemptionStatus status,
                                                String discountAmount, String platformFundedAmount,
                                                String shopFundedAmount, OffsetDateTime createdAt) {
    }

    public record CouponBusinessTraceView(String businessType, String businessNo,
                                          List<Map<String, Object>> events) {
    }

    public record TaskRunView(String taskName, boolean dryRun, int scanned, int processed,
                              int succeeded, int failed, int mismatches, OffsetDateTime startedAt,
                              OffsetDateTime finishedAt, String requestId) {
    }

    public record CouponTaskRunRequest(boolean dryRun, @Min(1) @Max(1000) int batchSize) {
    }

    public record CheckoutCouponSelection(CouponSelectionMode mode, List<String> userCouponIds) {
    }

    public record CouponQuoteView(String quoteToken, OffsetDateTime expiresAt, CouponSelectionMode selectionMode,
                                  List<SelectedCouponView> selectedCoupons, List<Map<String, Object>> availableCoupons,
                                  List<Map<String, Object>> unavailableCoupons, String totalDiscountAmount,
                                  List<String> warnings) {
    }

    public record SelectedCouponView(String userCouponId, String couponNo, String templateId,
                                     String couponName, CouponOwnerType ownerType, String shopId,
                                     String discountAmount, String platformFundedAmount, String shopFundedAmount,
                                     OffsetDateTime validTo) {
    }

    public record BuyerAppliedCouponView(String redemptionId, String couponNo, String couponName,
                                         CouponOwnerType ownerType, String discountAmount,
                                         CouponRedemptionStatus redemptionStatus) {
    }

    public record OrderAppliedCouponView(String redemptionId, String couponNo, String couponName,
                                         CouponOwnerType ownerType, String discountAmount,
                                         String platformFundedAmount, String shopFundedAmount,
                                         CouponRedemptionStatus redemptionStatus) {
    }
}
