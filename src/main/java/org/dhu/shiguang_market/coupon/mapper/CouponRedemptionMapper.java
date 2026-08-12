package org.dhu.shiguang_market.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRedemptionStatus;
import org.dhu.shiguang_market.coupon.model.CouponModels.Redemption;

public interface CouponRedemptionMapper extends BaseMapper<Redemption> {
    @Select("""
            <script>
            SELECT DISTINCT cr.* FROM coupon_redemption cr
            JOIN trade_order t ON t.id=cr.trade_id
            LEFT JOIN coupon_redemption_allocation ca ON ca.redemption_id=cr.id
            LEFT JOIN order_info oi ON oi.id=ca.order_id
            WHERE (#{redemptionNo} IS NULL OR cr.redemption_no=#{redemptionNo})
              AND (#{tradeNo} IS NULL OR t.trade_no=#{tradeNo})
              AND (#{orderNo} IS NULL OR oi.order_no=#{orderNo})
              AND (#{shopId} IS NULL OR ca.shop_id=#{shopId})
              AND (#{status} IS NULL OR cr.status=#{status})
            ORDER BY cr.created_at DESC,cr.id DESC
            </script>
            """)
    Page<Redemption> selectOperationPage(Page<Redemption> page,
                                         @Param("redemptionNo") String redemptionNo,
                                         @Param("tradeNo") String tradeNo,
                                         @Param("orderNo") String orderNo,
                                         @Param("shopId") Long shopId,
                                         @Param("status") CouponRedemptionStatus status);
}
