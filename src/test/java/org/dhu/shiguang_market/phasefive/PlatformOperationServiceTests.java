package org.dhu.shiguang_market.phasefive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.aftersale.service.AfterSaleService;
import org.dhu.shiguang_market.common.api.CommonViews.ShopSummary;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.InventoryTransactionType;
import org.dhu.shiguang_market.common.model.MarketEnums.OperatorType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderDisplayStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderOperationType;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.PaymentOrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ReservationStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.TradeStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.TransactionDirection;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.WalletTransactionType;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.inventory.mapper.InventoryTransactionMapper;
import org.dhu.shiguang_market.inventory.model.InventoryTransaction;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.TradeOrderMapper;
import org.dhu.shiguang_market.order.dto.OrderDtos.OrderItemView;
import org.dhu.shiguang_market.order.dto.OrderDtos.OrderStatusHistoryView;
import org.dhu.shiguang_market.order.dto.OrderDtos.ShippingView;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.TradeOrder;
import org.dhu.shiguang_market.order.service.OrderViewService;
import org.dhu.shiguang_market.payment.mapper.PaymentOrderMapper;
import org.dhu.shiguang_market.payment.mapper.WalletTransactionMapper;
import org.dhu.shiguang_market.payment.model.PaymentOrder;
import org.dhu.shiguang_market.payment.model.WalletTransaction;
import org.dhu.shiguang_market.task.service.PlatformOperationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 阶段五平台运营查询服务测试，不依赖真实数据库，可直接快速运行。 */
@ExtendWith(MockitoExtension.class)
class PlatformOperationServiceTests {
    @Mock private TradeOrderMapper tradeMapper;
    @Mock private OrderInfoMapper orderMapper;
    @Mock private PaymentOrderMapper paymentMapper;
    @Mock private AfterSaleRequestMapper afterSaleMapper;
    @Mock private InventoryTransactionMapper inventoryTransactionMapper;
    @Mock private WalletTransactionMapper walletTransactionMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private OrderViewService orderViews;
    @Mock private AfterSaleService afterSaleService;
    private PlatformOperationService service;

    @BeforeEach
    void setUp() {
        // Mockito 单元测试没有启动 MyBatis，上层查询构造器所需的实体元数据在此显式初始化。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "phase-five-operation-test");
        for (Class<?> entity : List.of(TradeOrder.class, OrderInfo.class, PaymentOrder.class,
                AfterSaleRequest.class, InventoryTransaction.class, WalletTransaction.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
        service = new PlatformOperationService(tradeMapper, orderMapper, paymentMapper, afterSaleMapper,
                inventoryTransactionMapper, walletTransactionMapper, userMapper, orderViews, afterSaleService);
    }

    /** 交易分页应返回订单数量、两位小数金额和不含联系方式的买家摘要。 */
    @Test
    void tradeListBuildsSafeOperationSummary() {
        TradeOrder trade = trade();
        SysUser user = new SysUser();
        user.setId(8L);
        user.setUsername("buyer");
        user.setNickname("买家");
        user.setPhone("13800000000");
        user.setEmail("buyer@example.com");
        user.setStatus(UserStatus.ACTIVE);

        when(tradeMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<TradeOrder> page = invocation.getArgument(0);
            page.setRecords(List.of(trade));
            page.setTotal(1);
            return page;
        });
        when(orderMapper.selectCount(any())).thenReturn(2L);
        when(userMapper.selectById(8L)).thenReturn(user);

        var result = service.trades("T001", 8L, TradeStatus.PAID, null, null, 1, 20);

        assertEquals(1, result.total());
        assertEquals("100.00", result.items().getFirst().payableAmount());
        assertEquals(2, result.items().getFirst().orderCount());
        assertEquals("buyer", result.items().getFirst().user().username());
        // UserSummary 本身不包含 phone/email/address，运营列表不会意外暴露联系方式。
        assertEquals(5, result.items().getFirst().user().getClass().getRecordComponents().length);
    }

    /** 平台详情应组合完整履约数据，同时固定排除地址、备注和可执行动作。 */
    @Test
    void orderDetailBuildsSafeCompleteView() {
        OrderInfo order = order();
        order.setUserId(8L);
        order.setShopId(21L);
        order.setOrderStatus(OrderStatus.COMPLETED);
        order.setItemAmount(new BigDecimal("899"));
        order.setFreightAmount(new BigDecimal("10"));
        order.setPayableAmount(new BigDecimal("909"));
        order.setRefundAmount(BigDecimal.ZERO);
        order.setCompletedAt(LocalDateTime.of(2026, 8, 10, 15, 20));

        TradeOrder trade = trade();
        trade.setPayExpireAt(LocalDateTime.of(2026, 8, 7, 10, 30));
        trade.setPaidAt(LocalDateTime.of(2026, 8, 7, 10, 2));

        SysUser buyer = new SysUser();
        buyer.setId(8L);
        buyer.setUsername("buyer");
        buyer.setNickname("买家");
        buyer.setStatus(UserStatus.ACTIVE);

        OffsetDateTime shippedAt = OffsetDateTime.of(
                2026, 8, 8, 9, 0, 0, 0, ZoneOffset.ofHours(8));
        ShippingView shipping = new ShippingView("SF", "顺丰速运", "SF001", shippedAt);
        OrderItemView item = new OrderItemView("30", "40", "50", "SPU001", "SKU001",
                "时光降噪耳机 Pro", "曜石黑", java.util.Map.of("color", "曜石黑"), null,
                "899.00", 1, "899.00", "10.00", "909.00", 0, "0.00",
                ReservationStatus.DEDUCTED);
        OrderStatusHistoryView history = new OrderStatusHistoryView(
                OrderStatus.PENDING_RECEIPT, OrderStatus.COMPLETED, OrderOperationType.COMPLETE,
                OperatorType.USER, null, order.getCompletedAt().atOffset(ZoneOffset.ofHours(8)));

        when(orderMapper.selectById(2L)).thenReturn(order);
        when(tradeMapper.selectById(1L)).thenReturn(trade);
        when(userMapper.selectById(8L)).thenReturn(buyer);
        when(orderViews.shop(order)).thenReturn(
                new ShopSummary("21", "SHOP001", "测试店铺", null, ShopStatus.ACTIVE));
        when(orderViews.displayStatus(order)).thenReturn(OrderDisplayStatus.COMPLETED);
        when(orderViews.shipping(order)).thenReturn(shipping);
        when(orderViews.items(2L)).thenReturn(List.of(item));
        when(orderViews.history(2L)).thenReturn(List.of(history));

        var result = service.orderDetail(2L);

        assertEquals("O001", result.orderNo());
        assertEquals("T001", result.tradeNo());
        assertEquals("buyer", result.buyer().username());
        assertEquals(OrderDisplayStatus.COMPLETED, result.displayStatus());
        assertEquals("899.00", result.itemAmount());
        assertEquals("10.00", result.freightAmount());
        assertEquals("909.00", result.payableAmount());
        assertEquals("SF001", result.shipping().trackingNo());
        assertEquals("时光降噪耳机 Pro", result.items().getFirst().productName());
        assertEquals(OrderOperationType.COMPLETE, result.history().getFirst().operationType());
        assertNull(result.cancelledAt());

        Set<String> fields = Arrays.stream(result.getClass().getRecordComponents())
                .map(component -> component.getName()).collect(Collectors.toSet());
        assertFalse(fields.contains("address"));
        assertFalse(fields.contains("buyerRemark"));
        assertFalse(fields.contains("availableActions"));
    }

    @Test
    void orderDetailRejectsInvalidOrMissingOrder() {
        BusinessException invalid = assertThrows(BusinessException.class,
                () -> service.orderDetail(0L));
        assertEquals("BAD_REQUEST", invalid.getCode());

        when(orderMapper.selectById(999L)).thenReturn(null);
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.orderDetail(999L));
        assertEquals("RESOURCE_NOT_FOUND", missing.getCode());
    }

    /** 输入交易号后，应串联返回交易、订单、支付、库存流水和钱包流水摘要。 */
    @Test
    void businessTraceConnectsRelatedResources() {
        TradeOrder trade = trade();
        OrderInfo order = order();
        PaymentOrder payment = payment();
        InventoryTransaction inventory = inventory();
        WalletTransaction wallet = wallet();

        when(orderMapper.selectOne(any())).thenReturn(order);
        when(tradeMapper.selectById(1L)).thenReturn(trade);
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(paymentMapper.selectList(any())).thenReturn(List.of(payment));
        when(afterSaleMapper.selectList(any())).thenReturn(List.of());
        when(inventoryTransactionMapper.selectList(any())).thenReturn(List.of(inventory));
        when(walletTransactionMapper.selectList(any())).thenReturn(List.of(wallet));

        var result = service.trace("order_shipment", "O001-SHIP-10");

        assertEquals("ORDER_SHIPMENT", result.businessType());
        assertEquals("T001", result.trade().businessNo());
        assertEquals("O001", result.orders().getFirst().businessNo());
        assertEquals("P001", result.payments().getFirst().businessNo());
        assertEquals("IT001", result.inventoryTransactions().getFirst().businessNo());
        assertEquals("WT001", result.walletTransactions().getFirst().businessNo());
        assertEquals(List.of(), result.afterSales());
    }

    /** 分页参数不合法时应在访问 Mapper 前直接返回统一业务异常。 */
    @Test
    void invalidPageIsRejected() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.orders(null, null, null, null, null, 0, 20));
        assertEquals("BAD_REQUEST", exception.getCode());
    }

    private TradeOrder trade() {
        TradeOrder value = new TradeOrder();
        value.setId(1L);
        value.setTradeNo("T001");
        value.setUserId(8L);
        value.setTradeStatus(TradeStatus.PAID);
        value.setPayableAmount(new BigDecimal("100"));
        value.setCreatedAt(LocalDateTime.of(2026, 8, 7, 10, 0));
        return value;
    }

    private OrderInfo order() {
        OrderInfo value = new OrderInfo();
        value.setId(2L);
        value.setOrderNo("O001");
        value.setTradeId(1L);
        value.setOrderStatus(OrderStatus.PENDING_SHIPMENT);
        value.setPaymentStatus(OrderPaymentStatus.PAID);
        value.setCreatedAt(LocalDateTime.of(2026, 8, 7, 10, 1));
        return value;
    }

    private PaymentOrder payment() {
        PaymentOrder value = new PaymentOrder();
        value.setId(3L);
        value.setPaymentNo("P001");
        value.setTradeId(1L);
        value.setStatus(PaymentOrderStatus.SUCCESS);
        value.setCreatedAt(LocalDateTime.of(2026, 8, 7, 10, 2));
        return value;
    }

    private InventoryTransaction inventory() {
        InventoryTransaction value = new InventoryTransaction();
        value.setId(4L);
        value.setTransactionNo("IT001");
        value.setTransactionType(InventoryTransactionType.DEDUCT);
        value.setCreatedAt(LocalDateTime.of(2026, 8, 7, 10, 3));
        return value;
    }

    private WalletTransaction wallet() {
        WalletTransaction value = new WalletTransaction();
        value.setId(5L);
        value.setTransactionNo("WT001");
        value.setTransactionType(WalletTransactionType.CONSUME);
        value.setDirection(TransactionDirection.DEBIT);
        value.setCreatedAt(LocalDateTime.of(2026, 8, 7, 10, 4));
        return value;
    }
}
