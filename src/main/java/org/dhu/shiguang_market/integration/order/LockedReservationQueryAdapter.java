package org.dhu.shiguang_market.integration.order;

import org.dhu.shiguang_market.order.mapper.OrderItemMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 通过数据库聚合查询实现有效锁定库存统计。 */
@Component
public class LockedReservationQueryAdapter implements LockedReservationQueryPort {
    private final OrderItemMapper itemMapper;

    public LockedReservationQueryAdapter(OrderItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public int lockedQuantity(long skuId) {
        return itemMapper.sumLockedQuantityBySkuId(skuId);
    }
}
