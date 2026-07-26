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
}
