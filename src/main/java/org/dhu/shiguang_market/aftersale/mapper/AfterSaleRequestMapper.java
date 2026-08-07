package org.dhu.shiguang_market.aftersale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dhu.shiguang_market.aftersale.model.AfterSaleRequest;

public interface AfterSaleRequestMapper extends BaseMapper<AfterSaleRequest> {
    /** 查询订单是否存在会阻断发货或确认收货的活跃售后。 */
    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM after_sale_request
                WHERE order_id = #{orderId}
                  AND status IN ('PENDING', 'WAITING_RETURN', 'REFUNDING')
            )
            """)
    boolean existsActiveByOrderId(@Param("orderId") long orderId);

    /** 交易取消时，仅联动撤销尚未进入审核流程的 PENDING 售后。 */
    @Update("""
            UPDATE after_sale_request
            SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP(3), version = version + 1
            WHERE order_id = #{orderId} AND status = 'PENDING'
            """)
    int cancelPendingByOrderId(@Param("orderId") long orderId);
}
