package org.dhu.shiguang_market.integration.merchantwallet;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWalletStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.SettlementStatus;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.merchantwallet.mapper.MerchantWalletAccountMapper;
import org.dhu.shiguang_market.merchantwallet.mapper.ShopSettlementMapper;
import org.dhu.shiguang_market.merchantwallet.mapper.MerchantWalletTransactionMapper;
import org.dhu.shiguang_market.merchantwallet.model.MerchantWalletAccount;
import org.dhu.shiguang_market.merchantwallet.model.MerchantWalletTransaction;
import org.dhu.shiguang_market.merchantwallet.model.ShopSettlement;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantTransactionDirection;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWalletBucket;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWalletTransactionType;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MerchantSettlementAdapter implements MerchantSettlementPort {
    private final MerchantWalletAccountMapper walletMapper;
    private final ShopSettlementMapper settlementMapper;
    private final NumberGenerator numbers;
    private final MerchantWalletTransactionMapper transactionMapper;

    public MerchantSettlementAdapter(MerchantWalletAccountMapper walletMapper,
                                     ShopSettlementMapper settlementMapper, NumberGenerator numbers,
                                     MerchantWalletTransactionMapper transactionMapper) {
        this.walletMapper = walletMapper; this.settlementMapper = settlementMapper; this.numbers = numbers;
        this.transactionMapper = transactionMapper;
    }

    @Override
    @Transactional
    public void recordPaidOrder(OrderInfo order, BigDecimal grossAmount) {
        walletMapper.ensureByShopId(order.getShopId());
        MerchantWalletAccount wallet = walletMapper.selectByShopIdForUpdate(order.getShopId());
        if (settlementMapper.selectByOrderAndShopForUpdate(order.getId(), order.getShopId()) != null) return;
        BigDecimal commission = grossAmount.multiply(BigDecimal.ZERO).setScale(2);
        BigDecimal pendingBefore = wallet.getPendingBalance();
        wallet.setPendingBalance(wallet.getPendingBalance().add(grossAmount.subtract(commission))); wallet.setLifetimeGrossIncome(wallet.getLifetimeGrossIncome().add(grossAmount)); wallet.setLifetimeCommission(wallet.getLifetimeCommission().add(commission)); walletMapper.updateById(wallet);
        ShopSettlement settlement = new ShopSettlement(); settlement.setSettlementNo(numbers.next("ST")); settlement.setShopId(order.getShopId()); settlement.setWalletId(wallet.getId()); settlement.setTradeId(order.getTradeId()); settlement.setOrderId(order.getId()); settlement.setStatus(SettlementStatus.PENDING); settlement.setGrossAmount(grossAmount); settlement.setCommissionRate(BigDecimal.ZERO.setScale(4)); settlement.setCommissionRefundable(true); settlement.setCommissionAmount(commission); settlement.setBuyerRefundAmount(BigDecimal.ZERO.setScale(2)); settlement.setCommissionRefundAmount(BigDecimal.ZERO.setScale(2)); settlement.setMerchantRefundAmount(BigDecimal.ZERO.setScale(2)); settlement.setNetAmount(grossAmount.subtract(commission)); settlement.setPendingAmount(grossAmount.subtract(commission)); settlement.setReleasedAmount(BigDecimal.ZERO.setScale(2)); settlement.setAvailableAt(null); settlementMapper.insert(settlement);
        MerchantWalletTransaction tx = new MerchantWalletTransaction();
        tx.setTransactionNo(numbers.next("MWT")); tx.setWalletId(wallet.getId()); tx.setShopId(wallet.getShopId()); tx.setTransactionType(MerchantWalletTransactionType.ORDER_PENDING_CREDIT); tx.setDirection(MerchantTransactionDirection.CREDIT); tx.setTargetBucket(MerchantWalletBucket.PENDING); tx.setAmount(settlement.getNetAmount()); tx.setPendingBefore(pendingBefore); tx.setPendingAfter(wallet.getPendingBalance()); tx.setAvailableBefore(wallet.getAvailableBalance()); tx.setAvailableAfter(wallet.getAvailableBalance()); tx.setFrozenBefore(wallet.getFrozenBalance()); tx.setFrozenAfter(wallet.getFrozenBalance()); tx.setBusinessType("ORDER_SETTLEMENT"); tx.setBusinessNo(settlement.getSettlementNo()); tx.setSettlementId(settlement.getId()); tx.setOrderId(order.getId()); tx.setRemark("支付成功，进入待结算余额"); transactionMapper.insert(tx);
    }

    @Override
    @Transactional
    public void markOrderCompleted(OrderInfo order) {
        ShopSettlement settlement = settlementMapper.selectByOrderAndShopForUpdate(order.getId(), order.getShopId());
        if (settlement == null || settlement.getStatus() == SettlementStatus.SETTLED) return;
        settlement.setStatus(SettlementStatus.READY); settlement.setAvailableAt(LocalDateTime.now().plusDays(7)); settlementMapper.updateById(settlement);
    }

    @Override
    @Transactional
    public boolean recordMerchantRefund(OrderInfo order, BigDecimal amount, String refundNo, long operatorId) {
        ShopSettlement settlement = settlementMapper.selectByOrderAndShopForUpdate(order.getId(), order.getShopId());
        if (settlement == null || amount == null || amount.signum() <= 0) return true;
        MerchantWalletAccount wallet = walletMapper.selectByShopIdForUpdate(order.getShopId());
        if (wallet == null || wallet.getStatus() != MerchantWalletStatus.ACTIVE) return false;
        BigDecimal remaining = amount.setScale(2);
        BigDecimal settlementPending = nz(settlement.getPendingAmount());
        BigDecimal settlementReleased = nz(settlement.getReleasedAmount());
        if (settlementPending.add(settlementReleased).compareTo(remaining) < 0) {
            settlement.setStatus(SettlementStatus.RECOVERY_REQUIRED);
            settlementMapper.updateById(settlement);
            return false;
        }
        BigDecimal pendingDebit = settlementPending.min(remaining);
        BigDecimal availableDebit = remaining.subtract(pendingDebit);
        if (settlementReleased.compareTo(availableDebit) < 0) {
            settlement.setStatus(SettlementStatus.RECOVERY_REQUIRED);
            settlementMapper.updateById(settlement);
            return false;
        }
        if (pendingDebit.signum() > 0) {
            move(wallet, settlement, MerchantWalletTransactionType.REFUND_DEBIT,
                    MerchantTransactionDirection.DEBIT, MerchantWalletBucket.PENDING, null,
                    pendingDebit, "AFTER_SALE_MERCHANT_REFUND", refundNo + "-PENDING", operatorId,
                    "售后退款冲回待结算收入");
            remaining = remaining.subtract(pendingDebit);
        }
        if (remaining.signum() > 0) {
            move(wallet, settlement, MerchantWalletTransactionType.REFUND_DEBIT,
                    MerchantTransactionDirection.DEBIT, MerchantWalletBucket.AVAILABLE, null,
                    remaining, "AFTER_SALE_MERCHANT_REFUND", refundNo + "-AVAILABLE", operatorId,
                    "售后退款冲回可用收入");
        }
        settlement.setBuyerRefundAmount(nz(settlement.getBuyerRefundAmount()).add(amount));
        settlement.setMerchantRefundAmount(nz(settlement.getMerchantRefundAmount()).add(amount));
        settlement.setPendingAmount(nz(settlement.getPendingAmount()).subtract(pendingDebit));
        settlement.setReleasedAmount(settlementReleased.subtract(amount.subtract(pendingDebit)));
        if (settlement.getPendingAmount().signum() == 0 && settlement.getReleasedAmount().signum() == 0) {
            settlement.setStatus(SettlementStatus.REFUNDED);
            settlement.setSettledAt(LocalDateTime.now());
        }
        settlementMapper.updateById(settlement);
        return true;
    }

    private void move(MerchantWalletAccount wallet, ShopSettlement settlement,
                      MerchantWalletTransactionType type, MerchantTransactionDirection direction,
                      MerchantWalletBucket source, MerchantWalletBucket target, BigDecimal amount,
                      String businessType, String businessNo, long operatorId, String remark) {
        if (transactionMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MerchantWalletTransaction>()
                .eq(MerchantWalletTransaction::getBusinessType, businessType)
                .eq(MerchantWalletTransaction::getBusinessNo, businessNo)) != null) return;
        BigDecimal pendingBefore = nz(wallet.getPendingBalance());
        BigDecimal availableBefore = nz(wallet.getAvailableBalance());
        BigDecimal frozenBefore = nz(wallet.getFrozenBalance());
        if (source == MerchantWalletBucket.PENDING && pendingBefore.compareTo(amount) < 0
                || source == MerchantWalletBucket.AVAILABLE && availableBefore.compareTo(amount) < 0
                || source == MerchantWalletBucket.FROZEN && frozenBefore.compareTo(amount) < 0) {
            throw new IllegalStateException("merchant wallet balance changed during refund");
        }
        BigDecimal pendingAfter = bucketAfter(pendingBefore, source, target, MerchantWalletBucket.PENDING, amount);
        BigDecimal availableAfter = bucketAfter(availableBefore, source, target, MerchantWalletBucket.AVAILABLE, amount);
        BigDecimal frozenAfter = bucketAfter(frozenBefore, source, target, MerchantWalletBucket.FROZEN, amount);
        wallet.setPendingBalance(pendingAfter);
        wallet.setAvailableBalance(availableAfter);
        wallet.setFrozenBalance(frozenAfter);
        wallet.setLifetimeRefund(nz(wallet.getLifetimeRefund()).add(amount));
        walletMapper.updateById(wallet);
        MerchantWalletTransaction tx = new MerchantWalletTransaction();
        tx.setTransactionNo(numbers.next("MWT")); tx.setWalletId(wallet.getId()); tx.setShopId(wallet.getShopId());
        tx.setTransactionType(type); tx.setDirection(direction); tx.setSourceBucket(source); tx.setTargetBucket(target);
        tx.setAmount(amount); tx.setPendingBefore(pendingBefore); tx.setPendingAfter(pendingAfter);
        tx.setAvailableBefore(availableBefore); tx.setAvailableAfter(availableAfter);
        tx.setFrozenBefore(frozenBefore); tx.setFrozenAfter(frozenAfter);
        tx.setBusinessType(businessType); tx.setBusinessNo(businessNo); tx.setSettlementId(settlement.getId());
        tx.setOrderId(settlement.getOrderId()); tx.setOperatorId(operatorId); tx.setRemark(remark);
        transactionMapper.insert(tx);
    }

    private BigDecimal bucketAfter(BigDecimal value, MerchantWalletBucket source, MerchantWalletBucket target,
                                   MerchantWalletBucket bucket, BigDecimal amount) {
        return value.add(target == bucket ? amount : BigDecimal.ZERO)
                .subtract(source == bucket ? amount : BigDecimal.ZERO);
    }

    private BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO.setScale(2) : value; }
}
