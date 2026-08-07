package org.dhu.shiguang_market.payment.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.TransactionDirection;
import org.dhu.shiguang_market.common.model.MarketEnums.WalletStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.WalletTransactionType;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.payment.dto.PaymentDtos.RechargeRequest;
import org.dhu.shiguang_market.payment.dto.PaymentDtos.WalletOperationView;
import org.dhu.shiguang_market.payment.dto.PaymentDtos.WalletTransactionView;
import org.dhu.shiguang_market.payment.dto.PaymentDtos.WalletView;
import org.dhu.shiguang_market.payment.mapper.WalletAccountMapper;
import org.dhu.shiguang_market.payment.mapper.WalletTransactionMapper;
import org.dhu.shiguang_market.payment.model.WalletAccount;
import org.dhu.shiguang_market.payment.model.WalletTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
    private static final BigDecimal MAX_RECHARGE_AMOUNT = new BigDecimal("100000.00");
    private final WalletAccountMapper walletMapper;
    private final WalletTransactionMapper transactionMapper;
    private final CurrentUserService currentUser;
    private final IdempotencyService idempotency;
    private final NumberGenerator numbers;

    public WalletService(WalletAccountMapper walletMapper, WalletTransactionMapper transactionMapper,
                         CurrentUserService currentUser, IdempotencyService idempotency,
                         NumberGenerator numbers) {
        this.walletMapper = walletMapper;
        this.transactionMapper = transactionMapper;
        this.currentUser = currentUser;
        this.idempotency = idempotency;
        this.numbers = numbers;
    }

    public WalletView wallet() {
        currentUser.requirePermission("wallet:read:self");
        return view(requireWallet(currentUser.id(), false));
    }

    public PageView<WalletTransactionView> transactions(
            WalletTransactionType type, LocalDateTime createdFrom, LocalDateTime createdTo,
            long page, long pageSize) {
        currentUser.requirePermission("wallet:read:self");
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
        }
        WalletAccount wallet = requireWallet(currentUser.id(), false);
        LambdaQueryWrapper<WalletTransaction> query = new LambdaQueryWrapper<WalletTransaction>()
                .eq(WalletTransaction::getWalletId, wallet.getId());
        if (type != null) query.eq(WalletTransaction::getTransactionType, type);
        if (createdFrom != null) query.ge(WalletTransaction::getCreatedAt, createdFrom);
        if (createdTo != null) query.lt(WalletTransaction::getCreatedAt, createdTo);
        query.orderByDesc(WalletTransaction::getCreatedAt).orderByDesc(WalletTransaction::getId);
        Page<WalletTransaction> result = transactionMapper.selectPage(Page.of(page, pageSize), query);
        return PageView.of(result, result.getRecords().stream().map(this::transactionView).toList());
    }

    @Transactional
    public WalletOperationView recharge(RechargeRequest request, String key) {
        currentUser.requirePermission("wallet:recharge");
        long userId = currentUser.id();
        return idempotency.execute(userId, "POST", "/api/wallet/recharges", key, request,
                WalletOperationView.class, () -> rechargeWallet(userId, request, key));
    }

    private WalletOperationView rechargeWallet(long userId, RechargeRequest request, String key) {
        BigDecimal amount = new BigDecimal(request.amount());
        if (amount.signum() <= 0 || amount.compareTo(MAX_RECHARGE_AMOUNT) > 0) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "充值金额必须为 0.01..100000.00");
        }
        String businessNo = idempotency.businessNo("WR", userId, key);
        WalletTransaction existing = transactionMapper.selectOne(new LambdaQueryWrapper<WalletTransaction>()
                .eq(WalletTransaction::getBusinessType, "SIMULATED_RECHARGE")
                .eq(WalletTransaction::getBusinessNo, businessNo));
        if (existing != null) return operationView(existing);
        WalletAccount wallet = requireWallet(userId, true);
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw BusinessException.unprocessable("WALLET_UNAVAILABLE", "钱包不可用");
        }
        BigDecimal before = wallet.getBalance();
        wallet.setBalance(before.add(amount));
        if (walletMapper.updateById(wallet) != 1) {
            throw BusinessException.conflict("VERSION_CONFLICT", "钱包余额已变化，请重试");
        }
        WalletTransaction transaction = new WalletTransaction();
        transaction.setTransactionNo(numbers.next("WT"));
        transaction.setWalletId(wallet.getId());
        transaction.setTransactionType(WalletTransactionType.RECHARGE);
        transaction.setDirection(TransactionDirection.CREDIT);
        transaction.setAmount(amount);
        transaction.setBalanceBefore(before);
        transaction.setBalanceAfter(before.add(amount));
        transaction.setBusinessType("SIMULATED_RECHARGE");
        transaction.setBusinessNo(businessNo);
        transaction.setOperatorId(userId);
        transaction.setRemark(request.remark());
        transactionMapper.insert(transaction);
        return operationView(transactionMapper.selectById(transaction.getId()));
    }

    /**
     * 将退款金额入账到指定用户钱包，并记录退款流水。
     *
     * <p>业务类型和退款编号组成数据库唯一键。重复调用时如果流水已经存在则直接返回，
     * 从而避免相同退款重复增加余额。余额更新和流水写入处于同一事务。</p>
     *
     * @param userId       收款用户 ID
     * @param amount       退款金额，必须大于 0
     * @param refundNo     退款业务编号
     * @param businessType 退款业务类型，例如 AFTER_SALE_REFUND
     */
    @Transactional
    public void refund(long userId, BigDecimal amount, String refundNo, String businessType) {
        if (amount == null || amount.signum() <= 0
                || refundNo == null || refundNo.isBlank()
                || businessType == null || businessType.isBlank()) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "退款参数不完整或金额无效");
        }
        String normalizedRefundNo = refundNo.trim();
        String normalizedBusinessType = businessType.trim();

        // 先按业务唯一键查重；并发重复请求仍由数据库唯一约束和事务回滚兜底。
        WalletTransaction existing = transactionMapper.selectOne(new LambdaQueryWrapper<WalletTransaction>()
                .eq(WalletTransaction::getBusinessType, normalizedBusinessType)
                .eq(WalletTransaction::getBusinessNo, normalizedRefundNo));
        if (existing != null) return;

        WalletAccount wallet = requireWallet(userId, true);
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw BusinessException.unprocessable("WALLET_UNAVAILABLE", "钱包不可用");
        }
        BigDecimal before = wallet.getBalance();
        if (walletMapper.credit(userId, amount) != 1) {
            throw BusinessException.unprocessable("WALLET_UNAVAILABLE", "钱包不可用");
        }
        WalletAccount after = walletMapper.selectById(wallet.getId());

        // 流水保存退款前后余额，便于后续查询和对账。
        WalletTransaction transaction = new WalletTransaction();
        transaction.setTransactionNo(numbers.next("WT"));
        transaction.setWalletId(wallet.getId());
        transaction.setTransactionType(WalletTransactionType.REFUND);
        transaction.setDirection(TransactionDirection.CREDIT);
        transaction.setAmount(amount);
        transaction.setBalanceBefore(before);
        transaction.setBalanceAfter(after.getBalance());
        transaction.setBusinessType(normalizedBusinessType);
        transaction.setBusinessNo(normalizedRefundNo);
        if (transactionMapper.insert(transaction) != 1) {
            throw new IllegalStateException("退款钱包流水写入失败");
        }
    }

    public WalletAccount requireWallet(long userId, boolean lock) {
        LambdaQueryWrapper<WalletAccount> query = new LambdaQueryWrapper<WalletAccount>()
                .eq(WalletAccount::getUserId, userId);
        if (lock) query.last("FOR UPDATE");
        WalletAccount wallet = walletMapper.selectOne(query);
        if (wallet == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "钱包不存在");
        return wallet;
    }

    public WalletTransactionView transactionView(WalletTransaction value) {
        return new WalletTransactionView(id(value.getId()), value.getTransactionNo(),
                value.getTransactionType(), value.getDirection(), money(value.getAmount()),
                money(value.getBalanceBefore()), money(value.getBalanceAfter()), value.getBusinessType(),
                value.getBusinessNo(), value.getRemark(), time(value.getCreatedAt()));
    }

    private WalletOperationView operationView(WalletTransaction value) {
        return new WalletOperationView(value.getTransactionNo(), value.getTransactionType(),
                value.getDirection(), money(value.getAmount()), money(value.getBalanceBefore()),
                money(value.getBalanceAfter()), time(value.getCreatedAt()));
    }

    private WalletView view(WalletAccount wallet) {
        return new WalletView(id(wallet.getId()), money(wallet.getBalance()), wallet.getStatus(),
                wallet.getVersion(), time(wallet.getUpdatedAt()));
    }
}
