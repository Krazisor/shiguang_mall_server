package org.dhu.shiguang_market.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import org.dhu.shiguang_market.common.model.MarketEnums.PaymentOrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.TradeStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.TransactionDirection;
import org.dhu.shiguang_market.common.model.MarketEnums.WalletStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.WalletTransactionType;

public final class PaymentDtos {
    private PaymentDtos() {
    }

    public record RechargeRequest(
            @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,15})\\.[0-9]{2}$") String amount,
            @Size(max = 500) String remark) {
    }

    public record CreatePaymentRequest(@NotBlank @Pattern(regexp = "WALLET") String paymentMethod) {
    }

    public record WalletView(
            String walletId, String balance, WalletStatus status, int version, OffsetDateTime updatedAt) {
    }

    public record WalletTransactionView(
            String id, String transactionNo, WalletTransactionType transactionType,
            TransactionDirection direction, String amount, String balanceBefore,
            String balanceAfter, String businessType, String businessNo,
            String remark, OffsetDateTime createdAt) {
    }

    public record WalletOperationView(
            String transactionNo, WalletTransactionType transactionType,
            TransactionDirection direction, String amount, String balanceBefore,
            String balanceAfter, OffsetDateTime createdAt) {
    }

    public record PaymentView(
            String id, String paymentNo, String tradeId, String amount,
            PaymentOrderStatus status, String failureReason, OffsetDateTime paidAt,
            OffsetDateTime expiresAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record PaymentResultView(
            String paymentId, String paymentNo, PaymentOrderStatus status, String amount,
            OffsetDateTime paidAt, String tradeId, TradeStatus tradeStatus, String walletBalance) {
    }
}
