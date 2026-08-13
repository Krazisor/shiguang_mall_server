package org.dhu.shiguang_market.coupon.model;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponAudienceType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponDistributionType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponFundingParticipationStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponFundingType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponOwnerType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRedeemCodeStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRecurrenceType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRedemptionStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRestorePolicy;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponStackMode;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponTemplateStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponValidityType;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.model.MarketEnums.UserCouponStatus;

public final class CouponModels {
    private CouponModels() {
    }

    @Data
    @TableName("coupon_activity")
    public static class CouponActivity {
        @TableId(type = IdType.AUTO) private Long id;
        private String activityNo;
        private CouponOwnerType ownerType;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long shopId;
        private CouponActivityType activityType;
        private String activityName;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private String subtitle;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private String bannerUrl;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private CouponActivityStatus status;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private String pauseSource;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private String pauseReason;
        private Long createdBy;
        private Long updatedBy;
        @Version private Integer version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @TableName(value = "coupon_activity_recurrence", autoResultMap = true)
    public static class CouponActivityRecurrence {
        @TableId private Long activityId;
        private CouponRecurrenceType recurrenceType;
        @TableField(typeHandler = Jackson3TypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
        private List<Integer> weekdaysJson;
        @TableField(typeHandler = Jackson3TypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
        private List<Integer> monthDaysJson;
        private LocalTime dailyStartsAt;
        private Integer windowDurationMinutes;
        private LocalDateTime recurrenceStartsAt;
        private LocalDateTime recurrenceEndsAt;
        private String timezone;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @TableName("coupon_template")
    public static class CouponTemplate {
        @TableId(type = IdType.AUTO) private Long id;
        private String templateNo;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long activityId;
        private CouponOwnerType ownerType;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long ownerShopId;
        private String couponName;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private String description;
        private CouponType couponType;
        private BigDecimal thresholdAmount;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private BigDecimal discountAmount;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private BigDecimal percentageOff;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private BigDecimal maximumDiscountAmount;
        private CouponFundingType fundingType;
        private BigDecimal platformShareRate;
        private CouponScopeType scopeType;
        private CouponDistributionType distributionType;
        private CouponAudienceType audienceType;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Integer newUserWithinDays;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime claimStartsAt;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime claimEndsAt;
        private CouponValidityType validityType;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime validFrom;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime validTo;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Integer effectiveDelayMinutes;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Integer validForHours;
        private Integer totalIssueLimit;
        private Integer issuedCount;
        private Integer perUserLimit;
        private CouponStackMode stackMode;
        private CouponRestorePolicy refundRestorePolicy;
        private BigDecimal budgetAmount;
        private BigDecimal budgetReservedAmount;
        private BigDecimal budgetConsumedAmount;
        private BigDecimal budgetReversedAmount;
        private CouponTemplateStatus status;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime firstIssuedAt;
        private Integer sortOrder;
        private Long createdBy;
        private Long updatedBy;
        @Version private Integer version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @TableName("coupon_template_shop_scope")
    public static class ShopScope {
        @TableId private Long templateId;
        private Long shopId;
        private LocalDateTime createdAt;
    }

    @Data @TableName("coupon_template_category_scope")
    public static class CategoryScope {
        @TableId private Long templateId;
        private Long categoryId;
        private LocalDateTime createdAt;
    }

    @Data @TableName("coupon_template_spu_scope")
    public static class SpuScope {
        @TableId private Long templateId;
        private Long spuId;
        private Long shopId;
        private LocalDateTime createdAt;
    }

    @Data @TableName("coupon_template_sku_scope")
    public static class SkuScope {
        @TableId private Long templateId;
        private Long skuId;
        private Long spuId;
        private Long shopId;
        private LocalDateTime createdAt;
    }

    @Data @TableName("coupon_funding_participation")
    public static class FundingParticipation {
        @TableId(type = IdType.AUTO) private Long id;
        private Long templateId;
        private Long shopId;
        private BigDecimal platformShareRate;
        private CouponFundingParticipationStatus status;
        private Long invitedBy;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long decidedBy;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private String decisionReason;
        private LocalDateTime invitedAt;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime decidedAt;
        @Version private Integer version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @TableName("user_coupon")
    public static class UserCoupon {
        @TableId(type = IdType.AUTO) private Long id;
        private String couponNo;
        private Long templateId;
        private Integer templateVersion;
        private Long userId;
        private UserCouponStatus status;
        private LocalDateTime validFrom;
        private LocalDateTime validTo;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long lockedTradeId;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime usedAt;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime expiredAt;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long revokedBy;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private String revokedReason;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime revokedAt;
        private Integer restoreCount;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime lastRestoredAt;
        @Version private Integer version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @TableName("coupon_claim_record")
    public static class ClaimRecord {
        @TableId(type = IdType.AUTO) private Long id;
        private String claimNo;
        private Long userCouponId;
        private Long templateId;
        private Long userId;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long activityId;
        private CouponDistributionType claimSource;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long redeemCodeId;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long grantedBy;
        private String businessNo;
        private LocalDateTime createdAt;
    }

    @Data @TableName("coupon_redeem_code")
    public static class RedeemCode {
        @TableId(type = IdType.AUTO) private Long id;
        private String batchNo;
        private Long templateId;
        private String codeHash;
        private Integer hashKeyVersion;
        private CouponRedeemCodeStatus status;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long userCouponId;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long redeemedBy;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime redeemedAt;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long revokedBy;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime revokedAt;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private String revokeReason;
        private Long createdBy;
        private LocalDateTime createdAt;
    }

    @Data
    public static class CodeBatchSummaryRow {
        private String batchNo;
        private Long templateId;
        private CouponRedeemCodeStatus status;
        private Integer total;
        private Integer active;
        private Integer redeemed;
        private Integer revoked;
        private LocalDateTime createdAt;
    }

    @Data @TableName("coupon_redemption")
    public static class Redemption {
        @TableId(type = IdType.AUTO) private Long id;
        private String redemptionNo;
        private Long userCouponId;
        private Long templateId;
        private Long userId;
        private Long tradeId;
        private Integer attemptNo;
        private CouponRedemptionStatus status;
        private BigDecimal discountAmount;
        private BigDecimal platformFundedAmount;
        private BigDecimal shopFundedAmount;
        private LocalDateTime reservedAt;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime consumedAt;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime releasedAt;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime restoredAt;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private String releaseReason;
        @Version private Integer version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @TableName("coupon_redemption_allocation")
    public static class RedemptionAllocation {
        @TableId(type = IdType.AUTO) private Long id;
        private Long redemptionId;
        private Long tradeId;
        private Long orderId;
        private Long orderItemId;
        private Long shopId;
        private BigDecimal eligibleGrossAmount;
        private BigDecimal calculationBaseAmount;
        private BigDecimal discountAmount;
        private BigDecimal platformFundedAmount;
        private BigDecimal shopFundedAmount;
        private LocalDateTime createdAt;
    }

    @Data @TableName("coupon_refund_allocation")
    public static class RefundAllocation {
        @TableId(type = IdType.AUTO) private Long id;
        private Long redemptionAllocationId;
        private Long afterSaleId;
        private String refundNo;
        private Integer refundedQuantity;
        private BigDecimal couponDiscountReversalAmount;
        private BigDecimal platformFundingReversalAmount;
        private BigDecimal shopFundingReversalAmount;
        private LocalDateTime createdAt;
    }

    @Data @TableName("coupon_budget_ledger")
    public static class BudgetLedger {
        @TableId(type = IdType.AUTO) private Long id;
        private String ledgerNo;
        private Long templateId;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long userCouponId;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long redemptionId;
        private String entryType;
        private BigDecimal reservedChange;
        private BigDecimal consumedChange;
        private BigDecimal reversedChange;
        private BigDecimal reservedAfter;
        private BigDecimal consumedAfter;
        private BigDecimal reversedAfter;
        private BigDecimal platformAmount;
        private BigDecimal shopAmount;
        private String businessType;
        private String businessNo;
        private LocalDateTime createdAt;
    }

    @Data @TableName(value = "coupon_operation_log", autoResultMap = true)
    public static class OperationLog {
        @TableId(type = IdType.AUTO) private Long id;
        private String resourceType;
        private Long resourceId;
        private String operationType;
        private OperatorType operatorType;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long operatorId;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long shopId;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private String fromStatus;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private String toStatus;
        @TableField(typeHandler = Jackson3TypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
        private Map<String, Object> changeSummaryJson;
        @TableField(updateStrategy = FieldStrategy.ALWAYS) private String reason;
        private String requestId;
        private LocalDateTime createdAt;
    }
}
