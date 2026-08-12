package org.dhu.shiguang_market.integration.merchantwallet;

import java.math.BigDecimal;
import org.dhu.shiguang_market.order.model.OrderInfo;

/** Payment/order use-cases use this port without depending on merchant-wallet persistence. */
public interface MerchantSettlementPort {
    void recordPaidOrder(OrderInfo order, BigDecimal grossAmount);
    void markOrderCompleted(OrderInfo order);

    /**
     * 冲回商家结算收入。返回 false 表示商家资金不足，调用方应按售后失败/人工追缴策略处理。
     */
    boolean recordMerchantRefund(OrderInfo order, BigDecimal amount, String refundNo, long operatorId);

    default boolean recordMerchantRefund(OrderInfo order, BigDecimal buyerRefundAmount,
                                         BigDecimal platformSubsidyReversal, String refundNo,
                                         long operatorId) {
        return recordMerchantRefund(order, buyerRefundAmount.add(platformSubsidyReversal),
                refundNo, operatorId);
    }
}
