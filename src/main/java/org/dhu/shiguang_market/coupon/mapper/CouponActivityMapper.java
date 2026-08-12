package org.dhu.shiguang_market.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponActivity;

public interface CouponActivityMapper extends BaseMapper<CouponActivity> {
    @Select("""
            SELECT
                (SELECT COUNT(*) FROM user_coupon uc
                  JOIN coupon_template ct ON ct.id = uc.template_id
                 WHERE ct.activity_id = #{activityId}) AS issued_count,
                (SELECT COUNT(*) FROM coupon_redemption cr
                  JOIN coupon_template ct ON ct.id = cr.template_id
                 WHERE ct.activity_id = #{activityId} AND cr.consumed_at IS NOT NULL) AS consumed_count,
                (SELECT COALESCE(SUM(cr.discount_amount), 0.00) FROM coupon_redemption cr
                  JOIN coupon_template ct ON ct.id = cr.template_id
                 WHERE ct.activity_id = #{activityId} AND cr.consumed_at IS NOT NULL) AS coupon_discount_amount
            """)
    ActivityMetric selectMetrics(@Param("activityId") long activityId);

    record ActivityMetric(long issuedCount, long consumedCount, java.math.BigDecimal couponDiscountAmount) {
    }
}
