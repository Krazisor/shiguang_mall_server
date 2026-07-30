package org.dhu.shiguang_market.payment.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.money;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderOperationType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.PaymentOrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.TradeStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.TransactionDirection;
import org.dhu.shiguang_market.common.model.MarketEnums.WalletStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.WalletTransactionType;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderStatusHistoryMapper;
import org.dhu.shiguang_market.order.mapper.TradeOrderMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.OrderStatusHistory;
import org.dhu.shiguang_market.order.model.TradeOrder;
import org.dhu.shiguang_market.payment.dto.PaymentDtos.CreatePaymentRequest;
import org.dhu.shiguang_market.payment.dto.PaymentDtos.PaymentResultView;
import org.dhu.shiguang_market.payment.dto.PaymentDtos.PaymentView;
import org.dhu.shiguang_market.payment.mapper.PaymentOrderMapper;
import org.dhu.shiguang_market.payment.mapper.WalletAccountMapper;
import org.dhu.shiguang_market.payment.mapper.WalletTransactionMapper;
import org.dhu.shiguang_market.payment.model.PaymentOrder;
import org.dhu.shiguang_market.payment.model.WalletAccount;
import org.dhu.shiguang_market.payment.model.WalletTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final PaymentOrderMapper paymentMapper;
    private final TradeOrderMapper tradeMapper;
    private final OrderInfoMapper orderMapper;
    private final OrderStatusHistoryMapper historyMapper;
    private final WalletAccountMapper walletMapper;
    private final WalletTransactionMapper walletTransactionMapper;
    private final CurrentUserService currentUser;
    private final IdempotencyService idempotency;
    private final NumberGenerator numbers;

    public PaymentService(PaymentOrderMapper paymentMapper, TradeOrderMapper tradeMapper,
                          OrderInfoMapper orderMapper, OrderStatusHistoryMapper historyMapper,
                          WalletAccountMapper walletMapper, WalletTransactionMapper walletTransactionMapper,
                          CurrentUserService currentUser, IdempotencyService idempotency,
                          NumberGenerator numbers) {
        this.paymentMapper = paymentMapper;
        this.tradeMapper = tradeMapper;
        this.orderMapper = orderMapper;
        this.historyMapper = historyMapper;
        this.walletMapper = walletMapper;
        this.walletTransactionMapper = walletTransactionMapper;
        this.currentUser = currentUser;
        this.idempotency = idempotency;
        this.numbers = numbers;
    }

    @Transactional
    public PaymentView create(long tradeId, CreatePaymentRequest request, String key) {
        long userId = currentUser.id();
        String path = "/api/trades/" + tradeId + "/payments";
        return idempotency.execute(userId, "POST", path, key, request,
                PaymentView.class, () -> createPayment(tradeId, userId, request, key));
    }

    private PaymentView createPayment(long tradeId, long userId, CreatePaymentRequest request, String key) {
        String paymentNo = idempotency.businessNo("PAY", userId, key);
        PaymentOrder existing = paymentMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getPaymentNo, paymentNo));
        if (existing != null) return view(existing);
        TradeOrder trade = ownedTrade(tradeId, true);
        if (trade.getTradeStatus() != TradeStatus.PENDING_PAYMENT) {
            throw BusinessException.conflict("TRADE_NOT_PAYABLE", "交易当前不可支付");
        }
        if (trade.getPayExpireAt().isBefore(LocalDateTime.now())) {
            throw BusinessException.conflict("TRADE_EXPIRED", "交易已过期");
        }
        PaymentOrder payment = new PaymentOrder();
        payment.setPaymentNo(paymentNo);
        payment.setTradeId(tradeId);
        payment.setAmount(trade.getPayableAmount());
        payment.setStatus(PaymentOrderStatus.PENDING);
        payment.setExpiresAt(trade.getPayExpireAt());
        paymentMapper.insert(payment);
        return view(paymentMapper.selectById(payment.getId()));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public PaymentResultView confirm(long paymentId, String key) {
        long userId = currentUser.id();
        String path = "/api/payments/" + paymentId + "/confirm";
        return idempotency.execute(userId, "POST", path, key, "confirm",
                PaymentResultView.class, () -> confirmPayment(paymentId, userId));
    }

    private PaymentResultView confirmPayment(long paymentId, long userId) {
        PaymentOrder reference = paymentMapper.selectById(paymentId);
        if (reference == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "支付单不存在");
        TradeOrder trade = ownedTrade(reference.getTradeId(), true);
        PaymentOrder payment = paymentMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getId, paymentId).last("FOR UPDATE"));
        if (payment.getStatus() == PaymentOrderStatus.SUCCESS) {
            return result(payment, trade, wallet(userId, false));
        }
        if (payment.getStatus() != PaymentOrderStatus.PENDING) {
            throw BusinessException.conflict("PAYMENT_NOT_PENDING", "支付单当前不可确认");
        }
        if (LocalDateTime.now().isAfter(payment.getExpiresAt()) || LocalDateTime.now().isAfter(trade.getPayExpireAt())) {
            payment.setStatus(PaymentOrderStatus.CANCELLED);
            paymentMapper.updateById(payment);
            throw BusinessException.conflict("PAYMENT_EXPIRED", "支付单已过期");
        }
        if (trade.getTradeStatus() != TradeStatus.PENDING_PAYMENT) {
            fail(payment, "TRADE_NOT_PAYABLE");
            throw BusinessException.conflict("TRADE_NOT_PAYABLE", "交易当前不可支付");
        }
        if (payment.getAmount().compareTo(trade.getPayableAmount()) != 0) {
            fail(payment, "PAYMENT_AMOUNT_MISMATCH");
            throw BusinessException.unprocessable("PAYMENT_AMOUNT_MISMATCH", "支付金额不一致");
        }
        WalletAccount wallet = wallet(userId, true);
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            fail(payment, "WALLET_UNAVAILABLE");
            throw BusinessException.unprocessable("WALLET_UNAVAILABLE", "钱包不可用");
        }
        BigDecimal before = wallet.getBalance();
        if (walletMapper.debit(userId, payment.getAmount()) != 1) {
            fail(payment, "WALLET_INSUFFICIENT_BALANCE");
            throw BusinessException.unprocessable("WALLET_INSUFFICIENT_BALANCE", "钱包余额不足");
        }
        WalletAccount updatedWallet = wallet(userId, false);
        WalletTransaction transaction = new WalletTransaction();
        transaction.setTransactionNo(numbers.next("WT"));
        transaction.setWalletId(wallet.getId());
        transaction.setTransactionType(WalletTransactionType.CONSUME);
        transaction.setDirection(TransactionDirection.DEBIT);
        transaction.setAmount(payment.getAmount());
        transaction.setBalanceBefore(before);
        transaction.setBalanceAfter(updatedWallet.getBalance());
        transaction.setBusinessType("TRADE_PAYMENT");
        transaction.setBusinessNo(payment.getPaymentNo());
        transaction.setOperatorId(userId);
        walletTransactionMapper.insert(transaction);

        LocalDateTime now = LocalDateTime.now();
        payment.setStatus(PaymentOrderStatus.SUCCESS);
        payment.setPaidAt(now);
        paymentMapper.updateById(payment);
        trade.setTradeStatus(TradeStatus.PAID);
        trade.setPaidAt(now);
        tradeMapper.updateById(trade);
        List<OrderInfo> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getTradeId, trade.getId()).orderByAsc(OrderInfo::getId));
        for (OrderInfo order : orders) {
            order.setOrderStatus(OrderStatus.PENDING_SHIPMENT);
            order.setPaymentStatus(OrderPaymentStatus.PAID);
            orderMapper.updateById(order);
            OrderStatusHistory history = new OrderStatusHistory();
            history.setOrderId(order.getId());
            history.setFromStatus(OrderStatus.PENDING_PAYMENT);
            history.setToStatus(OrderStatus.PENDING_SHIPMENT);
            history.setOperationType(OrderOperationType.PAY);
            history.setOperatorType(OperatorType.USER);
            history.setOperatorId(userId);
            historyMapper.insert(history);
        }
        return result(payment, trade, updatedWallet);
    }

    public PaymentView detail(long paymentId) {
        PaymentOrder payment = paymentMapper.selectById(paymentId);
        if (payment == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "支付单不存在");
        ownedTrade(payment.getTradeId(), false);
        return view(payment);
    }

    private void fail(PaymentOrder payment, String reason) {
        payment.setStatus(PaymentOrderStatus.FAILED);
        payment.setFailureReason(reason);
        paymentMapper.updateById(payment);
    }

    private TradeOrder ownedTrade(long tradeId, boolean lock) {
        LambdaQueryWrapper<TradeOrder> query = new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getId, tradeId).eq(TradeOrder::getUserId, currentUser.id());
        if (lock) query.last("FOR UPDATE");
        TradeOrder trade = tradeMapper.selectOne(query);
        if (trade == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "交易不存在");
        return trade;
    }

    private WalletAccount wallet(long userId, boolean lock) {
        LambdaQueryWrapper<WalletAccount> query = new LambdaQueryWrapper<WalletAccount>()
                .eq(WalletAccount::getUserId, userId);
        if (lock) query.last("FOR UPDATE");
        WalletAccount wallet = walletMapper.selectOne(query);
        if (wallet == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "钱包不存在");
        return wallet;
    }

    private PaymentView view(PaymentOrder payment) {
        return new PaymentView(id(payment.getId()), payment.getPaymentNo(), id(payment.getTradeId()),
                money(payment.getAmount()), payment.getStatus(), payment.getFailureReason(), time(payment.getPaidAt()),
                time(payment.getExpiresAt()), time(payment.getCreatedAt()), time(payment.getUpdatedAt()));
    }

    private PaymentResultView result(PaymentOrder payment, TradeOrder trade, WalletAccount wallet) {
        return new PaymentResultView(id(payment.getId()), payment.getPaymentNo(), payment.getStatus(),
                money(payment.getAmount()), time(payment.getPaidAt()), id(trade.getId()), trade.getTradeStatus(),
                money(wallet.getBalance()));
    }
}
