package org.dhu.shiguang_market.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.order.model.OrderInfo;

public interface OrderInfoMapper extends BaseMapper<OrderInfo> {
    @Select("""
            SELECT o.*
            FROM order_info o
            WHERE (#{userId} IS NULL OR o.user_id = #{userId})
              AND (#{shopId} IS NULL OR o.shop_id = #{shopId})
              AND (#{orderStatus} IS NULL OR o.order_status = #{orderStatus})
              AND (#{paymentStatus} IS NULL OR o.payment_status = #{paymentStatus})
              AND (#{createdFrom} IS NULL OR o.created_at >= #{createdFrom})
              AND (#{createdTo} IS NULL OR o.created_at < #{createdTo})
              AND (#{keyword} IS NULL
                   OR o.order_no LIKE CONCAT('%', #{keyword}, '%')
                   OR EXISTS (
                       SELECT 1 FROM trade_order t
                       WHERE t.id = o.trade_id
                         AND t.trade_no LIKE CONCAT('%', #{keyword}, '%')
                   )
                   OR EXISTS (
                       SELECT 1 FROM order_item oi
                       WHERE oi.order_id = o.id
                         AND oi.product_name LIKE CONCAT('%', #{keyword}, '%')
                   ))
            ORDER BY o.created_at DESC, o.id DESC
            """)
    Page<OrderInfo> selectOrderPage(
            Page<OrderInfo> page,
            @Param("userId") Long userId,
            @Param("shopId") Long shopId,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("paymentStatus") OrderPaymentStatus paymentStatus,
            @Param("keyword") String keyword,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo);
}
