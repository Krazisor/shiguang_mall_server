package org.dhu.shiguang_market.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryStockMapper;
import org.dhu.shiguang_market.inventory.mapper.InventoryTransactionMapper;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.dhu.shiguang_market.order.mapper.OrderStatusHistoryMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.dhu.shiguang_market.order.service.OrderService;
import org.dhu.shiguang_market.order.service.OrderViewService;
import org.junit.jupiter.api.Test;

class OrderServiceTests {
    @Test
    void buyerOrderKeywordIsAppliedInsideTheDatabasePage() {
        OrderInfoMapper orderMapper = mock(OrderInfoMapper.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        Page<OrderInfo> databasePage = Page.of(3, 20, 41);
        when(currentUser.id()).thenReturn(101L);
        when(orderMapper.selectOrderPage(any(), eq(101L), isNull(), isNull(), isNull(),
                eq("example phone"), isNull(), isNull())).thenReturn(databasePage);
        OrderService service = new OrderService(
                orderMapper, mock(OrderItemMapper.class), mock(OrderStatusHistoryMapper.class),
                mock(InventoryStockMapper.class), mock(InventoryTransactionMapper.class),
                mock(SysUserMapper.class), currentUser, mock(ShopAccessService.class),
                mock(OrderViewService.class), mock(NumberGenerator.class));

        var result = service.buyerOrders(null, null, " example phone ", null, null, 3, 20);

        assertThat(result.total()).isEqualTo(41);
        assertThat(result.items()).isEmpty();
        verify(orderMapper).selectOrderPage(any(), eq(101L), isNull(), isNull(), isNull(),
                eq("example phone"), isNull(), isNull());
    }
}
