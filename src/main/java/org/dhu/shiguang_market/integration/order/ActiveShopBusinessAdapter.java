package org.dhu.shiguang_market.integration.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dhu.shiguang_market.aftersale.mapper.AfterSaleRequestMapper;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.order.mapper.OrderInfoMapper;
import org.dhu.shiguang_market.order.model.OrderInfo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 使用 B 线订单和售后表实现店铺活跃业务检查。 */
@Component
public class ActiveShopBusinessAdapter implements ActiveShopBusinessPort {
    private final OrderInfoMapper orderMapper;
    private final AfterSaleRequestMapper afterSaleMapper;

    public ActiveShopBusinessAdapter(OrderInfoMapper orderMapper, AfterSaleRequestMapper afterSaleMapper) {
        this.orderMapper = orderMapper;
        this.afterSaleMapper = afterSaleMapper;
    }

    /** 先检查订单，命中后直接返回；只有无活跃订单时才继续查询售后。 */
    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveBusiness(long shopId) {
        boolean activeOrders = orderMapper.exists(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getShopId, shopId)
                .in(OrderInfo::getOrderStatus, OrderStatus.PENDING_PAYMENT,
                        OrderStatus.PENDING_SHIPMENT, OrderStatus.PENDING_RECEIPT));
        if (activeOrders) return true;

        return afterSaleMapper.selectCount(new LambdaQueryWrapper<AfterSaleRequest>()
                .in(AfterSaleRequest::getStatus, AfterSaleStatus.PENDING,
                        AfterSaleStatus.WAITING_RETURN, AfterSaleStatus.REFUNDING)
                .inSql(AfterSaleRequest::getOrderId,
                        "SELECT id FROM order_info WHERE shop_id = " + shopId)) > 0;
    }
}
