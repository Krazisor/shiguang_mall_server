package org.dhu.shiguang_market.phasefour;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dhu.shiguang_market.address.service.AddressService;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.cart.service.CartService;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.TradeStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.service.IdempotencyService;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryTransactionMapper;
import org.dhu.shiguang_market.order.dto.OrderDtos.CancelTradeRequest;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.mapper.OrderStatusHistoryMapper;
import org.dhu.shiguang_market.order.mapper.TradeOrderMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.OrderItem;
import org.dhu.shiguang_market.order.model.TradeOrder;
import org.dhu.shiguang_market.order.service.OrderViewService;
import org.dhu.shiguang_market.order.service.TradeService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 阶段四交易取消与售后联动测试。 */
class TradeAfterSaleCancellationTests {

    @BeforeAll
    static void initializeLambdaMetadata() {
        // cancel() 会通过 Lambda 条件查询交易、子订单和明细，测试中需手工初始化元数据。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "phase-four-test");
        TableInfoHelper.initTableInfo(assistant, TradeOrder.class);
        TableInfoHelper.initTableInfo(assistant, OrderInfo.class);
        TableInfoHelper.initTableInfo(assistant, OrderItem.class);
    }

    /** 取消待支付交易时，应逐个子订单撤销其 PENDING 售后。 */
    @Test
    void cancellingTradeCancelsPendingAfterSalesOfEveryOrder() {
        TradeOrderMapper tradeMapper = mock(TradeOrderMapper.class);
        OrderInfoMapper orderMapper = mock(OrderInfoMapper.class);
        OrderItemMapper itemMapper = mock(OrderItemMapper.class);
        AfterSaleRequestMapper afterSaleMapper = mock(AfterSaleRequestMapper.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);

        TradeOrder trade = new TradeOrder();
        trade.setId(10L);
        trade.setUserId(101L);
        trade.setTradeNo("TR10001");
        trade.setTradeStatus(TradeStatus.PENDING_PAYMENT);
        OrderInfo firstOrder = order(21L);
        OrderInfo secondOrder = order(22L);

        when(currentUser.id()).thenReturn(101L);
        when(tradeMapper.selectOne(any())).thenReturn(trade);
        when(tradeMapper.selectById(10L)).thenReturn(trade);
        when(orderMapper.selectList(any())).thenReturn(List.of(firstOrder, secondOrder));
        when(itemMapper.selectList(any())).thenReturn(List.of());

        TradeService service = new TradeService(tradeMapper, orderMapper, itemMapper,
                mock(OrderStatusHistoryMapper.class), afterSaleMapper,
                mock(InventoryStockMapper.class), mock(InventoryTransactionMapper.class),
                mock(CartService.class), mock(AddressService.class), currentUser,
                mock(IdempotencyService.class), mock(NumberGenerator.class),
                mock(OrderViewService.class), 30);

        service.cancel(10L, new CancelTradeRequest("买家主动取消"));

        verify(afterSaleMapper).cancelPendingByOrderId(21L);
        verify(afterSaleMapper).cancelPendingByOrderId(22L);
    }

    private static OrderInfo order(long id) {
        OrderInfo order = new OrderInfo();
        order.setId(id);
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        return order;
    }
}
