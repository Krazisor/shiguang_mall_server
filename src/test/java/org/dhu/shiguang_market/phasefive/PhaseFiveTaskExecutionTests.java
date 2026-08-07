package org.dhu.shiguang_market.phasefive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.aftersale.service.ShopAfterSaleService;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.PaymentOrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ReservationStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.TradeStatus;
import org.dhu.shiguang_market.common.util.NumberGenerator;
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
import org.dhu.shiguang_market.order.model.TradeOrder;
import org.dhu.shiguang_market.payment.mapper.PaymentOrderMapper;
import org.dhu.shiguang_market.payment.mapper.WalletAccountMapper;
import org.dhu.shiguang_market.payment.model.PaymentOrder;
import org.dhu.shiguang_market.task.dto.TaskDtos.TaskRunRequest;
import org.dhu.shiguang_market.task.service.TaskExecutionService;
import org.dhu.shiguang_market.task.service.TaskLockService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 阶段五任务核心处理测试，全部依赖使用 Mockito，不需要连接数据库和 Redis。 */
class PhaseFiveTaskExecutionTests {
    private final TaskLockService locks = mock(TaskLockService.class);
    private final TradeOrderMapper tradeMapper = mock(TradeOrderMapper.class);
    private final OrderInfoMapper orderMapper = mock(OrderInfoMapper.class);
    private final OrderItemMapper itemMapper = mock(OrderItemMapper.class);
    private final OrderStatusHistoryMapper historyMapper = mock(OrderStatusHistoryMapper.class);
    private final InventoryStockMapper stockMapper = mock(InventoryStockMapper.class);
    private final InventoryTransactionMapper inventoryTransactionMapper = mock(InventoryTransactionMapper.class);
    private final PaymentOrderMapper paymentMapper = mock(PaymentOrderMapper.class);
    private final AfterSaleRequestMapper afterSaleMapper = mock(AfterSaleRequestMapper.class);
    private final ShopAfterSaleService afterSaleService = mock(ShopAfterSaleService.class);
    private final WalletAccountMapper walletMapper = mock(WalletAccountMapper.class);
    private final NumberGenerator numbers = mock(NumberGenerator.class);
    private TaskExecutionService service;

    @BeforeAll
    static void initializeLambdaMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "phase-five-test");
        for (Class<?> entity : List.of(TradeOrder.class, OrderInfo.class, OrderItem.class,
                PaymentOrder.class, AfterSaleRequest.class, InventoryStock.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @BeforeEach
    void setUp() {
        when(locks.tryLock(any(), any())).thenReturn(true);
        service = new TaskExecutionService(locks, tradeMapper, orderMapper, itemMapper,
                historyMapper, stockMapper, inventoryTransactionMapper, paymentMapper,
                afterSaleMapper, afterSaleService, walletMapper, numbers);
    }

    /** 超时交易应释放仍锁定的库存，并取消子订单、交易和待处理售后。 */
    @Test
    void cancelsExpiredTradeAndReleasesLockedInventory() {
        TradeOrder trade = trade(10L);
        OrderInfo order = order(20L, OrderStatus.PENDING_PAYMENT);
        OrderItem item = new OrderItem();
        item.setId(30L);
        item.setOrderId(20L);
        item.setSkuId(40L);
        item.setQuantity(2);
        item.setReservationStatus(ReservationStatus.LOCKED);
        InventoryStock before = stock(50L, 40L, 8, 2);
        InventoryStock after = stock(50L, 40L, 10, 0);

        when(tradeMapper.selectPage(any(), any())).thenReturn(page(trade));
        when(tradeMapper.selectOne(any())).thenReturn(trade);
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(stockMapper.selectOne(any())).thenReturn(before);
        when(stockMapper.release(40L, 2)).thenReturn(1);
        when(stockMapper.selectById(50L)).thenReturn(after);
        when(numbers.next("IT")).thenReturn("IT10001");

        var result = service.cancelExpiredTrades(new TaskRunRequest(false, 100));

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.CANCELLED);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(item.getReservationStatus()).isEqualTo(ReservationStatus.RELEASED);
        verify(afterSaleMapper).cancelPendingByOrderId(20L);
        verify(inventoryTransactionMapper).insert(any(InventoryTransaction.class));
    }

    /** 自动收货必须跳过存在活跃售后的订单。 */
    @Test
    void automaticCompletionSkipsOrderWithActiveAfterSale() {
        OrderInfo order = order(20L, OrderStatus.PENDING_RECEIPT);
        when(orderMapper.selectPage(any(), any())).thenReturn(page(order));
        when(afterSaleMapper.existsActiveByOrderId(20L)).thenReturn(true);

        var result = service.completeShippedOrders(new TaskRunRequest(false, 100));

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.processed()).isZero();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING_RECEIPT);
    }

    /** 超过 expiresAt 的 PENDING 支付单应标记为 CANCELLED。 */
    @Test
    void expiresPendingPaymentOrder() {
        PaymentOrder payment = new PaymentOrder();
        payment.setId(60L);
        payment.setStatus(PaymentOrderStatus.PENDING);
        payment.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(paymentMapper.selectPage(any(), any())).thenReturn(page(payment));

        var result = service.expirePaymentOrders(new TaskRunRequest(false, 100));

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(payment.getStatus()).isEqualTo(PaymentOrderStatus.CANCELLED);
        verify(paymentMapper).updateById(payment);
    }

    /** 退款重试任务只扫描 REFUNDING/FAILED，并调用售后系统重试入口。 */
    @Test
    void retriesFailedRefund() {
        AfterSaleRequest afterSale = new AfterSaleRequest();
        afterSale.setId(70L);
        afterSale.setStatus(AfterSaleStatus.REFUNDING);
        afterSale.setRefundStatus(RefundStatus.FAILED);
        when(afterSaleMapper.selectPage(any(), any())).thenReturn(page(afterSale));
        when(afterSaleService.retryFailedRefund(70L)).thenReturn(true);

        var result = service.retryRefunds(new TaskRunRequest(false, 100));

        assertThat(result.succeeded()).isEqualTo(1);
        verify(afterSaleService).retryFailedRefund(70L);
    }

    private static TradeOrder trade(long id) {
        TradeOrder trade = new TradeOrder();
        trade.setId(id);
        trade.setTradeNo("TR10001");
        trade.setTradeStatus(TradeStatus.PENDING_PAYMENT);
        trade.setPayExpireAt(LocalDateTime.now().minusMinutes(1));
        return trade;
    }

    private static OrderInfo order(long id, OrderStatus status) {
        OrderInfo order = new OrderInfo();
        order.setId(id);
        order.setOrderStatus(status);
        order.setPaymentStatus(OrderPaymentStatus.UNPAID);
        return order;
    }

    private static InventoryStock stock(long id, long skuId, int available, int locked) {
        InventoryStock stock = new InventoryStock();
        stock.setId(id);
        stock.setSkuId(skuId);
        stock.setAvailableQuantity(available);
        stock.setLockedQuantity(locked);
        return stock;
    }

    private static <T> Page<T> page(T value) {
        Page<T> page = Page.of(1, 100, 1);
        page.setRecords(List.of(value));
        return page;
    }
}
