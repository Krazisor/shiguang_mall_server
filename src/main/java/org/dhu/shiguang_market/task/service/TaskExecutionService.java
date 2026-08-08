package org.dhu.shiguang_market.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.aftersale.service.ShopAfterSaleService;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.InventoryTransactionType;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderOperationType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.PaymentOrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ReservationStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.TradeStatus;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.common.util.RequestContext;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryTransactionMapper;
import org.dhu.shiguang_market.inventory.model.InventoryStock;
import org.dhu.shiguang_market.inventory.model.InventoryTransaction;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.mapper.OrderStatusHistoryMapper;
import org.dhu.shiguang_market.order.mapper.TradeOrderMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.OrderItem;
import org.dhu.shiguang_market.order.model.OrderStatusHistory;
import org.dhu.shiguang_market.order.model.TradeOrder;
import org.dhu.shiguang_market.payment.mapper.PaymentOrderMapper;
import org.dhu.shiguang_market.payment.mapper.WalletAccountMapper;
import org.dhu.shiguang_market.payment.model.PaymentOrder;
import org.dhu.shiguang_market.task.dto.TaskDtos.TaskRunRequest;
import org.dhu.shiguang_market.task.dto.TaskDtos.TaskRunView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 阶段五任务统一执行入口。
 *
 * <p>每个任务统一使用 Redis 锁、批次上限和运行统计。写任务在一个事务内完成当前批次，
 * 任意技术异常都会回滚，优先保证数据一致性。</p>
 */
@Service
public class TaskExecutionService {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);
    private final TaskLockService locks;
    private final TradeOrderMapper tradeMapper;
    private final OrderInfoMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderStatusHistoryMapper historyMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final PaymentOrderMapper paymentMapper;
    private final AfterSaleRequestMapper afterSaleMapper;
    private final ShopAfterSaleService afterSaleService;
    private final WalletAccountMapper walletMapper;
    private final NumberGenerator numbers;

    public TaskExecutionService(TaskLockService locks, TradeOrderMapper tradeMapper,
                                OrderInfoMapper orderMapper, OrderItemMapper itemMapper,
                                OrderStatusHistoryMapper historyMapper, InventoryStockMapper stockMapper,
                                InventoryTransactionMapper inventoryTransactionMapper,
                                PaymentOrderMapper paymentMapper, AfterSaleRequestMapper afterSaleMapper,
                                ShopAfterSaleService afterSaleService, WalletAccountMapper walletMapper,
                                NumberGenerator numbers) {
        this.locks = locks;
        this.tradeMapper = tradeMapper;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.historyMapper = historyMapper;
        this.stockMapper = stockMapper;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.paymentMapper = paymentMapper;
        this.afterSaleMapper = afterSaleMapper;
        this.afterSaleService = afterSaleService;
        this.walletMapper = walletMapper;
        this.numbers = numbers;
    }

    /** 取消超过 payExpireAt 的待支付交易，并释放尚未消费的锁定库存。 */
    @Transactional
    public TaskRunView cancelExpiredTrades(TaskRunRequest request) {
        validate(request);
        return run("cancel-expired-trades", request.dryRun(), () -> {
            List<TradeOrder> trades = tradeMapper.selectPage(Page.of(1, request.batchSize()),
                    new LambdaQueryWrapper<TradeOrder>()
                            .eq(TradeOrder::getTradeStatus, TradeStatus.PENDING_PAYMENT)
                            .lt(TradeOrder::getPayExpireAt, LocalDateTime.now())
                            .orderByAsc(TradeOrder::getPayExpireAt).orderByAsc(TradeOrder::getId))
                    .getRecords();
            if (request.dryRun()) return TaskCounts.dryRun(trades.size());
            int succeeded = 0;
            for (TradeOrder candidate : trades) {
                if (cancelExpiredTrade(candidate.getId())) succeeded++;
            }
            return TaskCounts.success(trades.size(), succeeded);
        });
    }

    /** 自动完成发货满七天且不存在活跃售后的订单。 */
    @Transactional
    public TaskRunView completeShippedOrders(TaskRunRequest request) {
        validate(request);
        return run("complete-shipped-orders", request.dryRun(), () -> {
            List<OrderInfo> orders = orderMapper.selectPage(Page.of(1, request.batchSize()),
                    new LambdaQueryWrapper<OrderInfo>()
                            .eq(OrderInfo::getOrderStatus, OrderStatus.PENDING_RECEIPT)
                            .lt(OrderInfo::getShippedAt, LocalDateTime.now().minusDays(7))
                            .orderByAsc(OrderInfo::getShippedAt).orderByAsc(OrderInfo::getId))
                    .getRecords();
            if (request.dryRun()) return TaskCounts.dryRun(orders.size());
            int succeeded = 0;
            for (OrderInfo order : orders) {
                // 与人工确认收货使用相同的活跃售后口径。
                if (!afterSaleMapper.existsActiveByOrderId(order.getId())
                        && !afterSaleMapper.existsPendingAppealByOrderId(order.getId())
                        && completeShippedOrder(order.getId())) {
                    succeeded++;
                }
            }
            return TaskCounts.success(orders.size(), succeeded);
        });
    }

    /** 将超过 expiresAt 的 PENDING 支付单关闭为 CANCELLED。 */
    @Transactional
    public TaskRunView expirePaymentOrders(TaskRunRequest request) {
        validate(request);
        return run("expire-payment-orders", request.dryRun(), () -> {
            List<PaymentOrder> payments = paymentMapper.selectPage(Page.of(1, request.batchSize()),
                    new LambdaQueryWrapper<PaymentOrder>()
                            .eq(PaymentOrder::getStatus, PaymentOrderStatus.PENDING)
                            .lt(PaymentOrder::getExpiresAt, LocalDateTime.now())
                            .orderByAsc(PaymentOrder::getExpiresAt).orderByAsc(PaymentOrder::getId))
                    .getRecords();
            if (request.dryRun()) return TaskCounts.dryRun(payments.size());
            for (PaymentOrder payment : payments) {
                payment.setStatus(PaymentOrderStatus.CANCELLED);
                payment.setFailureReason("支付单已过期");
                paymentMapper.updateById(payment);
            }
            return TaskCounts.success(payments.size(), payments.size());
        });
    }

    /** 扫描 REFUNDING/FAILED 售后，并复用原 refundNo 重试退款。 */
    @Transactional
    public TaskRunView retryRefunds(TaskRunRequest request) {
        validate(request);
        return run("retry-refunds", request.dryRun(), () -> {
            List<AfterSaleRequest> records = afterSaleMapper.selectPage(Page.of(1, request.batchSize()),
                    new LambdaQueryWrapper<AfterSaleRequest>()
                            .eq(AfterSaleRequest::getStatus, AfterSaleStatus.REFUNDING)
                            .eq(AfterSaleRequest::getRefundStatus, RefundStatus.FAILED)
                            .orderByAsc(AfterSaleRequest::getUpdatedAt).orderByAsc(AfterSaleRequest::getId))
                    .getRecords();
            if (request.dryRun()) return TaskCounts.dryRun(records.size());
            int succeeded = 0;
            for (AfterSaleRequest record : records) {
                if (afterSaleService.retryFailedRefund(record.getId())) succeeded++;
            }
            return TaskCounts.success(records.size(), succeeded);
        });
    }

    /** 库存对账只统计并告警，不自动修改库存。 */
    @Transactional(readOnly = true)
    public TaskRunView reconcileInventory(TaskRunRequest request) {
        validate(request);
        return run("reconcile-inventory", true, () -> reconciliation(
                "inventory", stockMapper.countReconciliationMismatches()));
    }

    /** 钱包对账只统计并告警，不自动修改余额。 */
    @Transactional(readOnly = true)
    public TaskRunView reconcileWallets(TaskRunRequest request) {
        validate(request);
        return run("reconcile-wallets", true, () -> reconciliation(
                "wallet", walletMapper.countReconciliationMismatches()));
    }

    private boolean cancelExpiredTrade(long tradeId) {
        TradeOrder trade = tradeMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getId, tradeId).last("FOR UPDATE"));
        if (trade == null || trade.getTradeStatus() != TradeStatus.PENDING_PAYMENT
                || !trade.getPayExpireAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        List<OrderInfo> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getTradeId, tradeId).orderByAsc(OrderInfo::getId));
        for (OrderInfo order : orders) {
            afterSaleMapper.cancelPendingByOrderId(order.getId());
            releaseLockedItems(trade, order);
            OrderStatus from = order.getOrderStatus();
            order.setOrderStatus(OrderStatus.CANCELLED);
            order.setCancelReason("支付超时自动取消");
            order.setCancelledAt(now);
            orderMapper.updateById(order);
            historyMapper.insert(history(order.getId(), from, OrderStatus.CANCELLED,
                    OrderOperationType.CANCEL, "支付超时自动取消"));
        }
        trade.setTradeStatus(TradeStatus.CANCELLED);
        trade.setCancelledAt(now);
        tradeMapper.updateById(trade);
        return true;
    }

    private void releaseLockedItems(TradeOrder trade, OrderInfo order) {
        List<OrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId()).orderByAsc(OrderItem::getSkuId));
        for (OrderItem item : items) {
            if (item.getReservationStatus() != ReservationStatus.LOCKED) continue;
            InventoryStock before = stockMapper.selectOne(new LambdaQueryWrapper<InventoryStock>()
                    .eq(InventoryStock::getSkuId, item.getSkuId()));
            if (before == null || stockMapper.release(item.getSkuId(), item.getQuantity()) != 1) {
                int lockedReservationQuantity = itemMapper.sumLockedQuantityBySkuId(item.getSkuId());
                log.error("释放超时交易库存失败: tradeNo={}, orderNo={}, orderItemId={}, skuId={}, "
                                + "itemQuantity={}, stockLockedQuantity={}, stockAvailableQuantity={}, "
                                + "lockedReservationQuantity={}",
                        trade.getTradeNo(), order.getOrderNo(), item.getId(), item.getSkuId(),
                        item.getQuantity(), before == null ? null : before.getLockedQuantity(),
                        before == null ? null : before.getAvailableQuantity(), lockedReservationQuantity);
                throw BusinessException.conflict("LOCKED_INVENTORY_INCONSISTENT", "锁定库存不一致");
            }
            item.setReservationStatus(ReservationStatus.RELEASED);
            itemMapper.updateById(item);
            InventoryStock after = stockMapper.selectById(before.getId());
            InventoryTransaction transaction = new InventoryTransaction();
            transaction.setTransactionNo(numbers.next("IT"));
            transaction.setSkuId(item.getSkuId());
            transaction.setTransactionType(InventoryTransactionType.RELEASE);
            transaction.setAvailableChange(item.getQuantity());
            transaction.setLockedChange(-item.getQuantity());
            transaction.setAvailableAfter(after.getAvailableQuantity());
            transaction.setLockedAfter(after.getLockedQuantity());
            transaction.setBusinessType("TRADE_TIMEOUT");
            transaction.setBusinessNo(trade.getTradeNo() + "-RELEASE-" + item.getId());
            transaction.setRemark("支付超时自动释放库存");
            inventoryTransactionMapper.insert(transaction);
        }
    }

    private boolean completeShippedOrder(long orderId) {
        OrderInfo order = orderMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getId, orderId).last("FOR UPDATE"));
        if (order == null || order.getOrderStatus() != OrderStatus.PENDING_RECEIPT
                || order.getShippedAt() == null
                || !order.getShippedAt().isBefore(LocalDateTime.now().minusDays(7))
                || afterSaleMapper.existsActiveByOrderId(orderId)
                || afterSaleMapper.existsPendingAppealByOrderId(orderId)) {
            return false;
        }
        order.setOrderStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        historyMapper.insert(history(orderId, OrderStatus.PENDING_RECEIPT, OrderStatus.COMPLETED,
                OrderOperationType.COMPLETE, "发货满七天自动确认收货"));
        return true;
    }

    private OrderStatusHistory history(long orderId, OrderStatus from, OrderStatus to,
                                       OrderOperationType operation, String remark) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(orderId);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setOperationType(operation);
        history.setOperatorType(OperatorType.SYSTEM);
        history.setRemark(remark);
        return history;
    }

    private TaskCounts reconciliation(String target, int mismatches) {
        if (mismatches > 0) {
            log.warn("{} reconciliation found {} mismatches; no data was modified", target, mismatches);
        }
        return new TaskCounts(0, 0, 0, 0, mismatches);
    }

    private TaskRunView run(String taskName, boolean dryRun, Supplier<TaskCounts> action) {
        String token = UUID.randomUUID().toString();
        if (!locks.tryLock(taskName, token)) {
            throw BusinessException.conflict("TASK_ALREADY_RUNNING", "任务正在执行中");
        }
        boolean unlockAfterTransaction = TransactionSynchronizationManager.isSynchronizationActive();
        if (unlockAfterTransaction) {
            // 锁必须覆盖数据库提交过程，避免另一实例在事务提交前读到旧状态并重复处理。
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    locks.unlock(taskName, token);
                }
            });
        }
        OffsetDateTime startedAt = RequestContext.now();
        try {
            TaskCounts counts = action.get();
            return new TaskRunView(taskName, dryRun, counts.scanned(), counts.processed(),
                    counts.succeeded(), counts.failed(), counts.mismatches(), startedAt,
                    RequestContext.now(), RequestContext.requestId());
        } finally {
            // 非事务调用（例如普通单元测试）仍在方法退出时及时释放。
            if (!unlockAfterTransaction) locks.unlock(taskName, token);
        }
    }

    private void validate(TaskRunRequest request) {
        if (request == null || request.batchSize() < 1 || request.batchSize() > 500) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "batchSize 必须为 1..500");
        }
    }

    private record TaskCounts(int scanned, int processed, int succeeded, int failed, int mismatches) {
        private static TaskCounts dryRun(int scanned) {
            return new TaskCounts(scanned, 0, 0, 0, 0);
        }

        private static TaskCounts success(int scanned, int succeeded) {
            return new TaskCounts(scanned, succeeded, succeeded, 0, 0);
        }
    }
}
