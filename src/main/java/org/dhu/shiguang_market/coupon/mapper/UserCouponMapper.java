package org.dhu.shiguang_market.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponOwnerType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponType;
import org.dhu.shiguang_market.common.model.MarketEnums.UserCouponStatus;
import org.dhu.shiguang_market.coupon.model.CouponModels.UserCoupon;

public interface UserCouponMapper extends BaseMapper<UserCoupon> {
    @Select("""
            <script>
            SELECT uc.* FROM user_coupon uc
            JOIN coupon_template ct ON ct.id=uc.template_id
            WHERE (#{userId} IS NULL OR uc.user_id=#{userId})
              AND (#{couponNo} IS NULL OR uc.coupon_no=#{couponNo})
              AND (#{templateNo} IS NULL OR ct.template_no=#{templateNo})
              AND (#{couponType} IS NULL OR ct.coupon_type=#{couponType})
              AND (#{ownerType} IS NULL OR ct.owner_type=#{ownerType})
              AND (#{expiringBefore} IS NULL OR uc.valid_to &lt;= #{expiringBefore})
              AND (#{keyword} IS NULL OR uc.coupon_no LIKE CONCAT('%',#{keyword},'%')
                   OR ct.template_no LIKE CONCAT('%',#{keyword},'%')
                   OR ct.coupon_name LIKE CONCAT('%',#{keyword},'%'))
              AND (#{status} IS NULL
                   OR (#{status}='EXPIRED' AND (uc.status='EXPIRED'
                       OR (uc.status='AVAILABLE' AND uc.valid_to &lt;= CURRENT_TIMESTAMP(3))))
                   OR (#{status}='AVAILABLE' AND uc.status='AVAILABLE'
                       AND uc.valid_to &gt; CURRENT_TIMESTAMP(3))
                   OR (#{status} NOT IN ('EXPIRED','AVAILABLE') AND uc.status=#{status}))
            <choose>
              <when test="sort == 'validTo,desc'">ORDER BY uc.valid_to DESC,uc.id ASC</when>
              <when test="sort == 'createdAt,desc'">ORDER BY uc.created_at DESC,uc.id DESC</when>
              <otherwise>ORDER BY uc.valid_to ASC,uc.id ASC</otherwise>
            </choose>
            </script>
            """)
    Page<UserCoupon> selectCouponPage(Page<UserCoupon> page,
                                      @Param("userId") Long userId,
                                      @Param("couponNo") String couponNo,
                                      @Param("templateNo") String templateNo,
                                      @Param("status") UserCouponStatus status,
                                      @Param("couponType") CouponType couponType,
                                      @Param("ownerType") CouponOwnerType ownerType,
                                      @Param("expiringBefore") LocalDateTime expiringBefore,
                                      @Param("keyword") String keyword,
                                      @Param("sort") String sort);
}
