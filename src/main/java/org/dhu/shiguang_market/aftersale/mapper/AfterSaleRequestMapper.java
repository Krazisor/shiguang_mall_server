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

    /** 待裁决申诉继续保护订单，即使原售后已经被商家拒绝。 */
    @Select("""
            SELECT EXISTS(
                SELECT 1
                FROM after_sale_appeal appeal
                JOIN after_sale_request request ON request.id = appeal.after_sale_id
                WHERE request.order_id = #{orderId} AND appeal.status = 'PENDING'
            )
            """)
    boolean existsPendingAppealByOrderId(@Param("orderId") long orderId);

    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM after_sale_appeal
                WHERE after_sale_id = #{afterSaleId} AND status = 'PENDING'
            )
            """)
    boolean existsPendingAppeal(@Param("afterSaleId") long afterSaleId);

    @Select("SELECT EXISTS(SELECT 1 FROM after_sale_appeal WHERE shop_id = #{shopId} AND status = 'PENDING')")
    boolean existsPendingAppealByShopId(@Param("shopId") long shopId);

    /** 交易取消时，仅联动撤销尚未进入审核流程的 PENDING 售后。 */
    @Update("""
            UPDATE after_sale_request
            SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP(3), version = version + 1
            WHERE order_id = #{orderId} AND status = 'PENDING'
            """)
    int cancelPendingByOrderId(@Param("orderId") long orderId);
}
