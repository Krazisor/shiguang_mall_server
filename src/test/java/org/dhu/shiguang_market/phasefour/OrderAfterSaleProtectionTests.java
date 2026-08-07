package org.dhu.shiguang_market.phasefour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryTransactionMapper;
import org.dhu.shiguang_market.order.dto.OrderDtos.ShipOrderRequest;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.mapper.OrderStatusHistoryMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.service.OrderService;
import org.dhu.shiguang_market.order.service.OrderViewService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 阶段四订单售后保护测试。
 *
 * <p>验证存在 PENDING、WAITING_RETURN 或 REFUNDING 售后时，订单不能发货或确认收货。</p>
 */
class OrderAfterSaleProtectionTests {
    private final OrderInfoMapper orderMapper = mock(OrderInfoMapper.class);
    private final OrderItemMapper itemMapper = mock(OrderItemMapper.class);
    private final OrderStatusHistoryMapper historyMapper = mock(OrderStatusHistoryMapper.class);
    private final AfterSaleRequestMapper afterSaleMapper = mock(AfterSaleRequestMapper.class);
    private final InventoryStockMapper stockMapper = mock(InventoryStockMapper.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final ShopAccessService shopAccess = mock(ShopAccessService.class);
    private OrderService service;

    @BeforeAll
    static void initializeLambdaMetadata() {
        // 纯 Mockito 测试不启动 MyBatis，需要初始化订单实体的 Lambda 字段缓存。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "phase-four-test");
        TableInfoHelper.initTableInfo(assistant, OrderInfo.class);
    }

    @BeforeEach
    void setUp() {
        when(currentUser.id()).thenReturn(101L);
        service = new OrderService(orderMapper, itemMapper, historyMapper, afterSaleMapper,
                stockMapper, mock(InventoryTransactionMapper.class), mock(SysUserMapper.class),
                currentUser, shopAccess, mock(OrderViewService.class), mock(NumberGenerator.class));
    }

    /** 买家确认收货前，必须先确认订单不存在活跃售后。 */
    @Test
    void activeAfterSaleBlocksOrderCompletion() {
        OrderInfo order = order(11L, 101L, 21L, OrderStatus.PENDING_RECEIPT);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(afterSaleMapper.existsActiveByOrderId(11L)).thenReturn(true);

        assertThatThrownBy(() -> service.complete(11L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ORDER_HAS_ACTIVE_AFTER_SALE"));

        verify(orderMapper, never()).updateById(any(OrderInfo.class));
    }

    /** 商家发货前执行相同保护，拦截后不允许扣减锁定库存。 */
    @Test
    void activeAfterSaleBlocksOrderShipment() {
        OrderInfo order = order(11L, 101L, 21L, OrderStatus.PENDING_SHIPMENT);
        order.setPaymentStatus(OrderPaymentStatus.PAID);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(afterSaleMapper.existsActiveByOrderId(11L)).thenReturn(true);

        ShipOrderRequest request = new ShipOrderRequest("SF", "顺丰速运", "SF10001");

        assertThatThrownBy(() -> service.ship(21L, 11L, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ORDER_HAS_ACTIVE_AFTER_SALE"));

        verify(stockMapper, never()).deduct(any(Long.class), any(Integer.class));
    }

    private static OrderInfo order(long id, long userId, long shopId, OrderStatus status) {
        OrderInfo order = new OrderInfo();
        order.setId(id);
        order.setUserId(userId);
        order.setShopId(shopId);
        order.setOrderStatus(status);
        return order;
    }
}
