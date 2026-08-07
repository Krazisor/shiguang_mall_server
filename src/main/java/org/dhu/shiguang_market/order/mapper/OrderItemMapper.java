package org.dhu.shiguang_market.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.order.model.OrderItem;

public interface OrderItemMapper extends BaseMapper<OrderItem> {
    /** 汇总指定 SKU 仍处于 LOCKED 状态的订单明细数量。 */
    @Select("""
            SELECT COALESCE(SUM(quantity), 0)
            FROM order_item
            WHERE sku_id = #{skuId} AND reservation_status = 'LOCKED'
            """)
    int sumLockedQuantityBySkuId(@Param("skuId") long skuId);
}
