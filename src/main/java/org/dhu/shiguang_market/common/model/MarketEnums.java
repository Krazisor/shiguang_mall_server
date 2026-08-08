package org.dhu.shiguang_market.common.model;

import com.baomidou.mybatisplus.annotation.IEnum;

public final class MarketEnums {

    private MarketEnums() {
    }

    public interface StringCodeEnum extends IEnum<String> {
        @Override
        default String getValue() {
            return ((Enum<?>) this).name();
        }
    }

    public enum UserStatus implements StringCodeEnum { ACTIVE, DISABLED, LOCKED }

    public enum ActiveStatus implements StringCodeEnum { ACTIVE, DISABLED }

    public enum ScopeType implements StringCodeEnum { PLATFORM, SHOP }

    public enum ShopStatus implements StringCodeEnum { PENDING, ACTIVE, SUSPENDED, CLOSED }

    public enum EnabledStatus implements StringCodeEnum { ENABLED, DISABLED }

    public enum AttributeValueType implements StringCodeEnum { TEXT, NUMBER, BOOLEAN, OPTION }

    public enum ProductStatus implements StringCodeEnum {
        DRAFT, PENDING_REVIEW, REJECTED, OFF_SHELF, ON_SHELF, BANNED
    }

    public enum ProductOperationType implements StringCodeEnum {
        CREATE, SUBMIT_REVIEW, APPROVE, REJECT, PUT_ON_SHELF,
        TAKE_OFF_SHELF, BAN, UNBAN, CONTENT_CHANGED
    }

    public enum OperatorType implements StringCodeEnum { USER, SHOP, PLATFORM, SYSTEM }

    public enum InventoryTransactionType implements StringCodeEnum {
        INBOUND, LOCK, RELEASE, DEDUCT, RETURN, ADJUST
    }

    public enum TradeStatus implements StringCodeEnum { PENDING_PAYMENT, PAID, CANCELLED }

    public enum OrderStatus implements StringCodeEnum {
        PENDING_PAYMENT, PENDING_SHIPMENT, PENDING_RECEIPT, COMPLETED, CANCELLED
    }

    public enum OrderPaymentStatus implements StringCodeEnum {
        UNPAID, PAID, PARTIALLY_REFUNDED, REFUNDED
    }

    public enum OrderOperationType implements StringCodeEnum { CREATE, PAY, CANCEL, SHIP, COMPLETE }

    public enum ReservationStatus implements StringCodeEnum { LOCKED, RELEASED, DEDUCTED }

    public enum WalletStatus implements StringCodeEnum { ACTIVE, FROZEN, CLOSED }

    public enum PaymentOrderStatus implements StringCodeEnum { PENDING, SUCCESS, FAILED, CANCELLED }

    public enum WalletTransactionType implements StringCodeEnum { RECHARGE, CONSUME, REFUND, ADJUST }

    public enum TransactionDirection implements StringCodeEnum { CREDIT, DEBIT }

    public enum AfterSaleType implements StringCodeEnum { REFUND_ONLY, RETURN_REFUND }

    public enum AfterSaleStatus implements StringCodeEnum {
        PENDING, REJECTED, WAITING_RETURN, REFUNDING, COMPLETED, CANCELLED
    }

    public enum RefundStatus implements StringCodeEnum { NOT_STARTED, PROCESSING, SUCCESS, FAILED }

    public enum AssetPurpose implements StringCodeEnum {
        AVATAR, SHOP_LOGO, BRAND_LOGO, PRODUCT_COVER, PRODUCT_GALLERY,
        SKU_IMAGE, RICH_TEXT_IMAGE, AFTER_SALE_EVIDENCE, APPEAL_EVIDENCE
    }

    public enum AssetStatus implements StringCodeEnum { ACTIVE, DELETED }

    public enum AfterSaleAppealTriggerType implements StringCodeEnum {
        MERCHANT_REJECTED, MERCHANT_TIMEOUT
    }

    public enum AfterSaleAppealStatus implements StringCodeEnum { PENDING, APPROVED, REJECTED }

    public enum AfterSaleAppealDecision implements StringCodeEnum { APPROVE, REJECT }

    public enum MerchantNotificationType implements StringCodeEnum {
        AFTER_SALE_APPEAL_SUBMITTED, AFTER_SALE_APPEAL_DECIDED
    }

    public enum MerchantWalletStatus implements StringCodeEnum { ACTIVE, FROZEN, CLOSED }

    public enum MerchantWalletTransactionType implements StringCodeEnum {
        ORDER_PENDING_CREDIT, SETTLEMENT_RELEASE, COMMISSION_DEBIT, REFUND_DEBIT,
        WITHDRAW_FREEZE, WITHDRAW_SUCCESS, WITHDRAW_FAILED, WITHDRAW_REJECT, PLATFORM_ADJUST
    }

    public enum MerchantTransactionDirection implements StringCodeEnum { CREDIT, DEBIT, TRANSFER }

    public enum MerchantWalletBucket implements StringCodeEnum { PENDING, AVAILABLE, FROZEN }

    public enum SettlementStatus implements StringCodeEnum {
        PENDING, READY, SETTLED, REFUNDED, RECOVERY_REQUIRED
    }

    public enum MerchantWithdrawalStatus implements StringCodeEnum {
        PROCESSING, SUCCESS, FAILED, REJECTED
    }

    public enum WithdrawalDestinationType implements StringCodeEnum { VIRTUAL_ACCOUNT }
}
