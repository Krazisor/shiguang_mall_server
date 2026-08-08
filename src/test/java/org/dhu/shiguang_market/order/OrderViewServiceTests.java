package org.dhu.shiguang_market.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.math.BigDecimal;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderDisplayStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.mapper.OrderStatusHistoryMapper;
import org.dhu.shiguang_market.order.mapper.TradeOrderMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.model.TradeOrder;
import org.dhu.shiguang_market.order.service.OrderViewService;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.junit.jupiter.api.Test;

class OrderViewServiceTests {
    @Test
    void activeAfterSaleIsReflectedByDisplayStatusWithoutChangingFulfillmentStatus() {
        AfterSaleRequestMapper afterSaleMapper = mock(AfterSaleRequestMapper.class);
        OrderInfo order = order(OrderStatus.PENDING_SHIPMENT);
        when(afterSaleMapper.existsActiveByOrderId(11L)).thenReturn(true);
        when(afterSaleMapper.existsPendingAppealByOrderId(11L)).thenReturn(false);

        OrderViewService service = service(afterSaleMapper);

        var view = service.summary(order, trade());

        assertThat(view.orderStatus()).isEqualTo(OrderStatus.PENDING_SHIPMENT);
        assertThat(view.displayStatus()).isEqualTo(OrderDisplayStatus.AFTER_SALE);
    }

    @Test
    void finishedAfterSaleFallsBackToOriginalOrderDisplayStatus() {
        AfterSaleRequestMapper afterSaleMapper = mock(AfterSaleRequestMapper.class);
        OrderInfo order = order(OrderStatus.PENDING_SHIPMENT);
        when(afterSaleMapper.existsActiveByOrderId(11L)).thenReturn(false);
        when(afterSaleMapper.existsPendingAppealByOrderId(11L)).thenReturn(false);

        var view = service(afterSaleMapper).summary(order, trade());

        assertThat(view.displayStatus()).isEqualTo(OrderDisplayStatus.PENDING_SHIPMENT);
    }

    private OrderViewService service(AfterSaleRequestMapper afterSaleMapper) {
        ShopMapper shopMapper = mock(ShopMapper.class);
        when(shopMapper.selectById(21L)).thenReturn(shop());
        OrderItemMapper itemMapper = mock(OrderItemMapper.class);
        when(itemMapper.selectList(org.mockito.ArgumentMatchers.any(Wrapper.class))).thenReturn(java.util.List.of());
        return new OrderViewService(mock(TradeOrderMapper.class), mock(OrderInfoMapper.class), itemMapper,
                mock(OrderStatusHistoryMapper.class), shopMapper, afterSaleMapper);
    }

    private OrderInfo order(OrderStatus status) {
        OrderInfo value = new OrderInfo();
        value.setId(11L);
        value.setTradeId(31L);
        value.setShopId(21L);
        value.setOrderNo("OR001");
        value.setOrderStatus(status);
        value.setPaymentStatus(OrderPaymentStatus.PAID);
        value.setPayableAmount(new BigDecimal("10.00"));
        value.setRefundAmount(BigDecimal.ZERO);
        return value;
    }

    private TradeOrder trade() {
        TradeOrder value = new TradeOrder();
        value.setId(31L);
        value.setTradeNo("TR001");
        return value;
    }

    private Shop shop() {
        Shop value = new Shop();
        value.setId(21L);
        value.setShopNo("SHOP001");
        value.setShopName("Test Shop");
        return value;
    }
}
