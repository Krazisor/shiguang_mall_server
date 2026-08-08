package org.dhu.shiguang_market.merchantwallet.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantTransactionDirection;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWalletBucket;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWalletStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWalletTransactionType;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWithdrawalStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.SettlementStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.WithdrawalDestinationType;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.Formatters;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.merchantwallet.dto.MerchantWalletDtos.CreateMerchantWithdrawalRequest;
import org.dhu.shiguang_market.merchantwallet.dto.MerchantWalletDtos.MerchantWalletTransactionView;
import org.dhu.shiguang_market.merchantwallet.dto.MerchantWalletDtos.MerchantWalletView;
import org.dhu.shiguang_market.merchantwallet.dto.MerchantWalletDtos.MerchantWithdrawalView;
import org.dhu.shiguang_market.merchantwallet.dto.MerchantWalletDtos.ShopSettlementView;
import org.dhu.shiguang_market.merchantwallet.mapper.MerchantWalletAccountMapper;
import org.dhu.shiguang_market.merchantwallet.mapper.MerchantWalletTransactionMapper;
import org.dhu.shiguang_market.merchantwallet.mapper.MerchantWithdrawalMapper;
import org.dhu.shiguang_market.merchantwallet.mapper.ShopSettlementMapper;
import org.dhu.shiguang_market.merchantwallet.model.MerchantWalletAccount;
import org.dhu.shiguang_market.merchantwallet.model.MerchantWalletTransaction;
import org.dhu.shiguang_market.merchantwallet.model.MerchantWithdrawal;
import org.dhu.shiguang_market.merchantwallet.model.ShopSettlement;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.TradeOrderMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.TradeOrder;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.identity.service.IdentityViewMapper;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.OperatorBrief;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantWalletService {
    private static final String MONEY_PATTERN = "^(0|[1-9][0-9]{0,15})\\.[0-9]{2}$";
    private final MerchantWalletAccountMapper walletMapper;
    private final MerchantWalletTransactionMapper transactionMapper;
    private final ShopSettlementMapper settlementMapper;
    private final MerchantWithdrawalMapper withdrawalMapper;
    private final OrderInfoMapper orderMapper;
    private final TradeOrderMapper tradeMapper;
    private final AfterSaleRequestMapper afterSaleMapper;
    private final CurrentUserService currentUser;
    private final ShopAccessService shopAccess;
    private final IdempotencyService idempotency;
    private final NumberGenerator numbers;
    private final SysUserMapper userMapper;

    public MerchantWalletService(MerchantWalletAccountMapper walletMapper,
                                 MerchantWalletTransactionMapper transactionMapper,
                                 ShopSettlementMapper settlementMapper, MerchantWithdrawalMapper withdrawalMapper,
                                 OrderInfoMapper orderMapper, TradeOrderMapper tradeMapper,
                                 AfterSaleRequestMapper afterSaleMapper,
                                 CurrentUserService currentUser, ShopAccessService shopAccess,
                                 IdempotencyService idempotency, NumberGenerator numbers,
                                 SysUserMapper userMapper) {
        this.walletMapper = walletMapper; this.transactionMapper = transactionMapper;
        this.settlementMapper = settlementMapper; this.withdrawalMapper = withdrawalMapper;
        this.orderMapper = orderMapper; this.tradeMapper = tradeMapper; this.afterSaleMapper = afterSaleMapper; this.currentUser = currentUser;
        this.shopAccess = shopAccess; this.idempotency = idempotency; this.numbers = numbers; this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public MerchantWalletView wallet(long shopId) {
        shopAccess.require(shopId, "shop:wallet:read");
        MerchantWalletAccount wallet = walletMapper.selectOne(new LambdaQueryWrapper<MerchantWalletAccount>().eq(MerchantWalletAccount::getShopId, shopId));
        if (wallet == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "钱包不存在");
        return walletView(wallet);
    }

    @Transactional(readOnly = true)
    public PageView<MerchantWalletView> platformWallets(Long shopId, long page, long pageSize) {
        validatePage(page, pageSize);
        Page<MerchantWalletAccount> result = walletMapper.selectPage(Page.of(page, pageSize),
                new LambdaQueryWrapper<MerchantWalletAccount>().eq(shopId != null, MerchantWalletAccount::getShopId, shopId)
                        .orderByDesc(MerchantWalletAccount::getUpdatedAt).orderByDesc(MerchantWalletAccount::getId));
        return PageView.of(result, result.getRecords().stream().map(this::walletView).toList());
    }

    @Transactional(readOnly = true)
    public PageView<MerchantWalletTransactionView> platformTransactions(Long shopId, long page, long pageSize) {
        validatePage(page, pageSize);
        Page<MerchantWalletTransaction> result = transactionMapper.selectPage(Page.of(page, pageSize),
                new LambdaQueryWrapper<MerchantWalletTransaction>().eq(shopId != null, MerchantWalletTransaction::getShopId, shopId)
                        .orderByDesc(MerchantWalletTransaction::getCreatedAt).orderByDesc(MerchantWalletTransaction::getId));
        return PageView.of(result, result.getRecords().stream().map(this::transactionView).toList());
    }

    @Transactional(readOnly = true)
    public PageView<ShopSettlementView> platformSettlements(Long shopId, long page, long pageSize) {
        validatePage(page, pageSize);
        Page<ShopSettlement> result = settlementMapper.selectPage(Page.of(page, pageSize),
                new LambdaQueryWrapper<ShopSettlement>().eq(shopId != null, ShopSettlement::getShopId, shopId)
                        .orderByDesc(ShopSettlement::getCreatedAt).orderByDesc(ShopSettlement::getId));
        return PageView.of(result, result.getRecords().stream().map(this::settlementView).toList());
    }

    @Transactional(readOnly = true)
    public PageView<MerchantWithdrawalView> platformWithdrawals(Long shopId, long page, long pageSize) {
        validatePage(page, pageSize);
        Page<MerchantWithdrawal> result = withdrawalMapper.selectPage(Page.of(page, pageSize),
                new LambdaQueryWrapper<MerchantWithdrawal>().eq(shopId != null, MerchantWithdrawal::getShopId, shopId)
                        .orderByDesc(MerchantWithdrawal::getRequestedAt).orderByDesc(MerchantWithdrawal::getId));
        return PageView.of(result, result.getRecords().stream().map(this::withdrawalView).toList());
    }

    @Transactional(readOnly = true)
    public PageView<MerchantWalletTransactionView> transactions(long shopId, MerchantWalletTransactionType type,
                                                                 MerchantWalletBucket bucket, String businessType,
                                                                 String businessNo, LocalDateTime createdFrom,
                                                                 LocalDateTime createdTo, long page, long pageSize) {
        shopAccess.require(shopId, "shop:wallet:read"); validatePage(page, pageSize);
        Page<MerchantWalletTransaction> result = transactionMapper.selectPage(Page.of(page, pageSize),
                new LambdaQueryWrapper<MerchantWalletTransaction>().eq(MerchantWalletTransaction::getShopId, shopId)
                        .eq(type != null, MerchantWalletTransaction::getTransactionType, type)
                        .and(bucket != null, q -> q.eq(MerchantWalletTransaction::getTargetBucket, bucket)
                                .or().eq(MerchantWalletTransaction::getSourceBucket, bucket))
                        .eq(businessType != null && !businessType.isBlank(), MerchantWalletTransaction::getBusinessType, businessType.trim())
                        .eq(businessNo != null && !businessNo.isBlank(), MerchantWalletTransaction::getBusinessNo, businessNo.trim())
                        .ge(createdFrom != null, MerchantWalletTransaction::getCreatedAt, createdFrom)
                        .lt(createdTo != null, MerchantWalletTransaction::getCreatedAt, createdTo)
                        .orderByDesc(MerchantWalletTransaction::getCreatedAt).orderByDesc(MerchantWalletTransaction::getId));
        return PageView.of(result, result.getRecords().stream().map(this::transactionView).toList());
    }

    @Transactional(readOnly = true)
    public PageView<ShopSettlementView> settlements(long shopId, SettlementStatus status, String orderNo,
                                                      LocalDateTime createdFrom, LocalDateTime createdTo,
                                                      long page, long pageSize) {
        shopAccess.require(shopId, "shop:wallet:read"); validatePage(page, pageSize);
        Page<ShopSettlement> result = settlementMapper.selectShopPage(Page.of(page, pageSize), shopId, status,
                orderNo == null ? null : orderNo.trim(), createdFrom, createdTo);
        return PageView.of(result, result.getRecords().stream().map(this::settlementView).toList());
    }

    @Transactional(readOnly = true)
    public PageView<MerchantWithdrawalView> withdrawals(long shopId, MerchantWithdrawalStatus status,
                                                          LocalDateTime createdFrom, LocalDateTime createdTo,
                                                          long page, long pageSize) {
        shopAccess.require(shopId, "shop:wallet:read"); validatePage(page, pageSize);
        Page<MerchantWithdrawal> result = withdrawalMapper.selectPage(Page.of(page, pageSize),
                new LambdaQueryWrapper<MerchantWithdrawal>().eq(MerchantWithdrawal::getShopId, shopId)
                        .eq(status != null, MerchantWithdrawal::getStatus, status)
                        .ge(createdFrom != null, MerchantWithdrawal::getRequestedAt, createdFrom)
                        .lt(createdTo != null, MerchantWithdrawal::getRequestedAt, createdTo)
                        .orderByDesc(MerchantWithdrawal::getRequestedAt).orderByDesc(MerchantWithdrawal::getId));
        return PageView.of(result, result.getRecords().stream().map(this::withdrawalView).toList());
    }

    @Transactional
    public MerchantWithdrawalView withdraw(long shopId, CreateMerchantWithdrawalRequest request, String key) {
        shopAccess.require(shopId, "shop:wallet:withdraw");
        long userId = currentUser.id();
        return idempotency.execute(userId, "POST", "/api/shops/" + shopId + "/merchant-wallet/withdrawals", key,
                request, MerchantWithdrawalView.class, () -> withdrawInternal(shopId, request, userId, key));
    }

    private MerchantWithdrawalView withdrawInternal(long shopId, CreateMerchantWithdrawalRequest request,
                                                     long userId, String key) {
        BigDecimal amount = parseAmount(request.amount());
        if (request.destinationType() != WithdrawalDestinationType.VIRTUAL_ACCOUNT) {
            throw BusinessException.unprocessable("WITHDRAWAL_DESTINATION_INVALID", "仅支持虚拟账户");
        }
        String destination = requireText(request.destinationAccount(), 128);
        MerchantWalletAccount wallet = walletMapper.selectByShopIdForUpdate(shopId);
        if (wallet == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "钱包不存在");
        if (wallet.getStatus() != MerchantWalletStatus.ACTIVE) throw BusinessException.conflict("MERCHANT_WALLET_FROZEN", "钱包不可用");
        if (wallet.getAvailableBalance().compareTo(amount) < 0) throw BusinessException.unprocessable("MERCHANT_WALLET_INSUFFICIENT", "可用余额不足");
        MerchantWithdrawal withdrawal = new MerchantWithdrawal();
        withdrawal.setWithdrawalNo(numbers.next("MWD")); withdrawal.setWalletId(wallet.getId()); withdrawal.setShopId(shopId);
        withdrawal.setStatus(MerchantWithdrawalStatus.PROCESSING); withdrawal.setAmount(amount); withdrawal.setFeeAmount(BigDecimal.ZERO.setScale(2));
        withdrawal.setNetAmount(amount); withdrawal.setDestinationType(request.destinationType()); withdrawal.setDestinationAccount(destination);
        withdrawal.setRemark(request.remark() == null ? null : Formatters.trimToNull(request.remark())); withdrawal.setRequestedBy(userId);
        withdrawal.setBusinessNo(idempotency.businessNo("MWD", userId, key)); withdrawalMapper.insert(withdrawal);
        move(wallet, MerchantWalletTransactionType.WITHDRAW_FREEZE, MerchantTransactionDirection.TRANSFER,
                MerchantWalletBucket.AVAILABLE, MerchantWalletBucket.FROZEN, amount, "MERCHANT_WITHDRAWAL", withdrawal.getBusinessNo(), null, withdrawal.getId(), userId, "虚拟提现冻结");
        return withdrawalView(withdrawalMapper.selectById(withdrawal.getId()));
    }

    @Transactional
    public int releaseSettlements(int batchSize, boolean dryRun) {
        List<ShopSettlement> records = settlementMapper.selectList(new LambdaQueryWrapper<ShopSettlement>()
                .in(ShopSettlement::getStatus, List.of(SettlementStatus.READY, SettlementStatus.PENDING))
                .le(ShopSettlement::getAvailableAt, LocalDateTime.now()).orderByAsc(ShopSettlement::getShopId).orderByAsc(ShopSettlement::getId)
                .last("LIMIT " + Math.min(Math.max(batchSize, 1), 500)));
        if (dryRun) return records.size();
        int processed = 0;
        for (ShopSettlement settlement : records) {
            MerchantWalletAccount wallet = walletMapper.selectByShopIdForUpdate(settlement.getShopId());
            ShopSettlement locked = settlementMapper.selectById(settlement.getId());
            if (wallet == null || locked == null || locked.getStatus() == SettlementStatus.SETTLED || locked.getPendingAmount().signum() <= 0) continue;
            OrderInfo order = orderMapper.selectById(locked.getOrderId());
            if (order == null || order.getOrderStatus() != OrderStatus.COMPLETED
                    || afterSaleMapper.existsActiveByOrderId(order.getId())
                    || afterSaleMapper.existsPendingAppealByOrderId(order.getId())) continue;
            BigDecimal amount = locked.getPendingAmount();
            move(wallet, MerchantWalletTransactionType.SETTLEMENT_RELEASE, MerchantTransactionDirection.TRANSFER,
                    MerchantWalletBucket.PENDING, MerchantWalletBucket.AVAILABLE, amount, "ORDER_SETTLEMENT", locked.getSettlementNo(), locked.getId(), null, null, "结算观察期结束");
            locked.setReleasedAmount(locked.getReleasedAmount().add(amount)); locked.setPendingAmount(BigDecimal.ZERO); locked.setStatus(SettlementStatus.SETTLED); locked.setSettledAt(LocalDateTime.now()); settlementMapper.updateById(locked); processed++;
        }
        return processed;
    }

    @Transactional
    public int processWithdrawals(int batchSize, boolean dryRun) {
        return processWithdrawals(batchSize, dryRun, MerchantWithdrawalStatus.SUCCESS);
    }

    @Transactional
    public int processWithdrawals(int batchSize, boolean dryRun, MerchantWithdrawalStatus outcome) {
        List<MerchantWithdrawal> records = withdrawalMapper.selectList(new LambdaQueryWrapper<MerchantWithdrawal>()
                .eq(MerchantWithdrawal::getStatus, MerchantWithdrawalStatus.PROCESSING).orderByAsc(MerchantWithdrawal::getShopId).orderByAsc(MerchantWithdrawal::getId)
                .last("LIMIT " + Math.min(Math.max(batchSize, 1), 500)));
        if (dryRun) return records.size();
        int processed = 0;
        for (MerchantWithdrawal withdrawal : records) {
            MerchantWalletAccount wallet = walletMapper.selectByShopIdForUpdate(withdrawal.getShopId());
            MerchantWithdrawal locked = withdrawalMapper.selectScopedForUpdate(withdrawal.getId(), withdrawal.getShopId());
            if (wallet == null || locked == null || locked.getStatus() != MerchantWithdrawalStatus.PROCESSING) continue;
            if (outcome == null || outcome == MerchantWithdrawalStatus.SUCCESS) {
                move(wallet, MerchantWalletTransactionType.WITHDRAW_SUCCESS, MerchantTransactionDirection.DEBIT,
                        MerchantWalletBucket.FROZEN, null, locked.getAmount(), "MERCHANT_WITHDRAWAL",
                        locked.getBusinessNo() + "-SUCCESS", null, locked.getId(), null, "虚拟提现成功");
                locked.setStatus(MerchantWithdrawalStatus.SUCCESS);
            } else if (outcome == MerchantWithdrawalStatus.FAILED || outcome == MerchantWithdrawalStatus.REJECTED) {
                MerchantWalletTransactionType type = outcome == MerchantWithdrawalStatus.FAILED
                        ? MerchantWalletTransactionType.WITHDRAW_FAILED : MerchantWalletTransactionType.WITHDRAW_REJECT;
                move(wallet, type, MerchantTransactionDirection.TRANSFER, MerchantWalletBucket.FROZEN,
                        MerchantWalletBucket.AVAILABLE, locked.getAmount(), "MERCHANT_WITHDRAWAL",
                        locked.getBusinessNo() + "-" + outcome.name(), null, locked.getId(), null,
                        outcome == MerchantWithdrawalStatus.FAILED ? "虚拟提现失败，释放冻结" : "虚拟提现驳回，释放冻结");
                locked.setStatus(outcome);
                locked.setFailureReason(outcome == MerchantWithdrawalStatus.FAILED ? "虚拟出款失败" : "平台驳回提现");
            } else {
                throw BusinessException.badRequest("WITHDRAWAL_NOT_PROCESSABLE", "提现结果无效");
            }
            locked.setCompletedAt(LocalDateTime.now()); withdrawalMapper.updateById(locked); processed++;
        }
        return processed;
    }

    private void move(MerchantWalletAccount wallet, MerchantWalletTransactionType type, MerchantTransactionDirection direction,
                       MerchantWalletBucket source, MerchantWalletBucket target, BigDecimal amount, String businessType,
                       String businessNo, Long settlementId, Long withdrawalId, Long operatorId, String remark) {
        MerchantWalletTransaction existing = transactionMapper.selectOne(new LambdaQueryWrapper<MerchantWalletTransaction>().eq(MerchantWalletTransaction::getBusinessType, businessType).eq(MerchantWalletTransaction::getBusinessNo, businessNo));
        if (existing != null) return;
        BigDecimal pendingBefore = nz(wallet.getPendingBalance()), availableBefore = nz(wallet.getAvailableBalance()), frozenBefore = nz(wallet.getFrozenBalance());
        if (source == MerchantWalletBucket.PENDING && pendingBefore.compareTo(amount) < 0 || source == MerchantWalletBucket.AVAILABLE && availableBefore.compareTo(amount) < 0 || source == MerchantWalletBucket.FROZEN && frozenBefore.compareTo(amount) < 0) throw BusinessException.unprocessable("MERCHANT_WALLET_INSUFFICIENT", "商家余额不足");
        BigDecimal pendingAfter = bucketAfter(pendingBefore, source, target, MerchantWalletBucket.PENDING, amount), availableAfter = bucketAfter(availableBefore, source, target, MerchantWalletBucket.AVAILABLE, amount), frozenAfter = bucketAfter(frozenBefore, source, target, MerchantWalletBucket.FROZEN, amount);
        wallet.setPendingBalance(pendingAfter); wallet.setAvailableBalance(availableAfter); wallet.setFrozenBalance(frozenAfter); walletMapper.updateById(wallet);
        MerchantWalletTransaction tx = new MerchantWalletTransaction(); tx.setTransactionNo(numbers.next("MWT")); tx.setWalletId(wallet.getId()); tx.setShopId(wallet.getShopId()); tx.setTransactionType(type); tx.setDirection(direction); tx.setSourceBucket(source); tx.setTargetBucket(target); tx.setAmount(amount); tx.setPendingBefore(pendingBefore); tx.setPendingAfter(pendingAfter); tx.setAvailableBefore(availableBefore); tx.setAvailableAfter(availableAfter); tx.setFrozenBefore(frozenBefore); tx.setFrozenAfter(frozenAfter); tx.setBusinessType(businessType); tx.setBusinessNo(businessNo); tx.setSettlementId(settlementId); tx.setWithdrawalId(withdrawalId); tx.setOperatorId(operatorId); tx.setRemark(remark); transactionMapper.insert(tx);
    }

    private BigDecimal bucketAfter(BigDecimal value, MerchantWalletBucket source, MerchantWalletBucket target, MerchantWalletBucket bucket, BigDecimal amount) { return value.add(target == bucket ? amount : BigDecimal.ZERO).subtract(source == bucket ? amount : BigDecimal.ZERO); }
    private BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO.setScale(2) : value; }
    private BigDecimal parseAmount(String value) { if (value == null || !value.matches(MONEY_PATTERN) || new BigDecimal(value).signum() <= 0) throw BusinessException.unprocessable("WITHDRAWAL_AMOUNT_INVALID", "提现金额无效"); return new BigDecimal(value); }
    private String requireText(String value, int max) { String v = Formatters.trimToNull(value); if (v == null || v.length() > max) throw BusinessException.unprocessable("WITHDRAWAL_DESTINATION_INVALID", "提现账户无效"); return v; }
    private MerchantWalletView walletView(MerchantWalletAccount w) { return new MerchantWalletView(id(w.getId()), id(w.getShopId()), w.getCurrency(), w.getStatus(), money(nz(w.getPendingBalance())), money(nz(w.getAvailableBalance())), money(nz(w.getFrozenBalance())), money(nz(w.getLifetimeGrossIncome())), money(nz(w.getLifetimeCommission())), money(nz(w.getLifetimeRefund())), w.getVersion() == null ? 0 : w.getVersion(), time(w.getUpdatedAt())); }
    private MerchantWalletTransactionView transactionView(MerchantWalletTransaction tx) {
        OrderInfo order = tx.getOrderId() == null ? null : orderMapper.selectById(tx.getOrderId());
        SysUser operator = tx.getOperatorId() == null ? null : userMapper.selectById(tx.getOperatorId());
        OperatorBrief operatorView = operator == null ? null
                : new OperatorBrief(id(operator.getId()), operator.getUsername(), operator.getNickname());
        return new MerchantWalletTransactionView(id(tx.getId()), tx.getTransactionNo(), tx.getTransactionType(),
                tx.getDirection(), tx.getSourceBucket(), tx.getTargetBucket(), tx.getTargetBucket(), money(tx.getAmount()),
                money(tx.getPendingBefore()), money(tx.getPendingAfter()), money(tx.getAvailableBefore()),
                money(tx.getAvailableAfter()), money(tx.getFrozenBefore()), money(tx.getFrozenAfter()),
                tx.getBusinessType(), tx.getBusinessNo(), id(tx.getOrderId()), order == null ? null : order.getOrderNo(),
                id(tx.getWithdrawalId()), operatorView, tx.getRemark(), time(tx.getCreatedAt()));
    }
    private ShopSettlementView settlementView(ShopSettlement s) { OrderInfo o = orderMapper.selectById(s.getOrderId()); TradeOrder t = tradeMapper.selectById(s.getTradeId()); return new ShopSettlementView(id(s.getId()), id(s.getShopId()), id(s.getOrderId()), o == null ? null : o.getOrderNo(), id(s.getTradeId()), t == null ? null : t.getTradeNo(), s.getStatus(), money(s.getGrossAmount()), s.getCommissionRate() == null ? "0.0000" : s.getCommissionRate().setScale(4, RoundingMode.HALF_UP).toPlainString(), Boolean.TRUE.equals(s.getCommissionRefundable()), money(s.getCommissionAmount()), money(s.getBuyerRefundAmount()), money(s.getCommissionRefundAmount()), money(s.getMerchantRefundAmount()), money(s.getNetAmount()), money(s.getPendingAmount()), money(s.getReleasedAmount()), time(s.getAvailableAt()), time(s.getSettledAt()), time(s.getCreatedAt()), time(s.getUpdatedAt())); }
    private MerchantWithdrawalView withdrawalView(MerchantWithdrawal w) { String account = w.getDestinationAccount(); String masked = account == null ? null : account.length() <= 4 ? "****" : account.substring(0, Math.min(8, account.length())) + "****" + account.substring(Math.max(0, account.length() - 4)); return new MerchantWithdrawalView(id(w.getId()), w.getWithdrawalNo(), id(w.getShopId()), w.getStatus(), money(w.getAmount()), money(w.getFeeAmount()), money(w.getNetAmount()), w.getDestinationType(), masked, w.getFailureReason(), time(w.getRequestedAt()), time(w.getCompletedAt())); }
    private void validatePage(long page, long size) { if (page < 1 || size < 1 || size > 100) throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围"); }
}
