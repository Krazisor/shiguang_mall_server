package org.dhu.shiguang_market.merchantwallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.OperatorBrief;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantTransactionDirection;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWalletBucket;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWalletStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWalletTransactionType;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWithdrawalStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.SettlementStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.WithdrawalDestinationType;

public final class MerchantWalletDtos {
    private MerchantWalletDtos() {}
    public record CreateMerchantWithdrawalRequest(
            @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,15})\\.[0-9]{2}$") String amount,
            @NotNull WithdrawalDestinationType destinationType,
            @NotBlank @Size(max = 128) String destinationAccount,
            @Size(max = 500) String remark) {}

    public record MerchantWalletView(String walletId, String shopId, String currency,
            MerchantWalletStatus status, String pendingBalance, String availableBalance,
            String frozenBalance, String lifetimeGrossIncome, String lifetimeCommission,
            String lifetimeRefund, int version, OffsetDateTime updatedAt) {}

    public record MerchantWalletTransactionView(String id, String transactionNo,
            MerchantWalletTransactionType transactionType, MerchantTransactionDirection direction,
            MerchantWalletBucket sourceBucket, MerchantWalletBucket targetBucket, MerchantWalletBucket bucket,
            String amount, String pendingBefore, String pendingAfter,
            String availableBefore, String availableAfter, String frozenBefore, String frozenAfter,
            String businessType, String businessNo, String orderId, String orderNo, String withdrawalId,
            OperatorBrief operator,
            String remark, OffsetDateTime createdAt) {}

    public record ShopSettlementView(String settlementId, String shopId, String orderId,
            String orderNo, String tradeId, String tradeNo, SettlementStatus status,
            String grossAmount, String buyerPaidAmount, String platformCouponSubsidyAmount,
            String shopCouponDiscountAmount, String commissionRate, boolean commissionRefundable,
            String commissionAmount, String buyerRefundAmount, String platformSubsidyRefundAmount,
            String commissionRefundAmount,
            String merchantRefundAmount, String netAmount, String pendingAmount,
            String releasedAmount, OffsetDateTime availableAt, OffsetDateTime settledAt,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    public record MerchantWithdrawalView(String withdrawalId, String withdrawalNo, String shopId,
            MerchantWithdrawalStatus status, String amount, String feeAmount, String netAmount,
            WithdrawalDestinationType destinationType, String destinationAccountMasked,
            String failureReason, OffsetDateTime requestedAt, OffsetDateTime completedAt) {}
}
