package org.dhu.shiguang_market.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRedeemCodeStatus;
import org.dhu.shiguang_market.coupon.model.CouponModels.CodeBatchSummaryRow;
import org.dhu.shiguang_market.coupon.model.CouponModels.RedeemCode;

public interface CouponRedeemCodeMapper extends BaseMapper<RedeemCode> {
    @Select("""
            <script>
            SELECT rc.batch_no,rc.template_id,
                   CASE WHEN SUM(rc.status='ACTIVE')&gt;0 THEN 'ACTIVE'
                        WHEN SUM(rc.status='REDEEMED')&gt;0 THEN 'REDEEMED' ELSE 'REVOKED' END status,
                   COUNT(*) total,SUM(rc.status='ACTIVE') active,SUM(rc.status='REDEEMED') redeemed,
                   SUM(rc.status='REVOKED') revoked,MIN(rc.created_at) created_at
            FROM coupon_redeem_code rc
            JOIN coupon_template ct ON ct.id=rc.template_id
            WHERE (#{shopId} IS NULL AND ct.owner_type='PLATFORM'
                   OR #{shopId} IS NOT NULL AND ct.owner_type='SHOP' AND ct.owner_shop_id=#{shopId})
              AND (#{templateId} IS NULL OR rc.template_id=#{templateId})
              AND (#{batchNo} IS NULL OR rc.batch_no=#{batchNo})
              AND (#{status} IS NULL OR rc.status=#{status})
              AND (#{createdFrom} IS NULL OR rc.created_at&gt;=#{createdFrom})
              AND (#{createdTo} IS NULL OR rc.created_at&lt;#{createdTo})
            GROUP BY rc.batch_no,rc.template_id
            ORDER BY MIN(rc.created_at) DESC,rc.batch_no DESC
            </script>
            """)
    Page<CodeBatchSummaryRow> selectBatchPage(Page<CodeBatchSummaryRow> page,
                                               @Param("shopId") Long shopId,
                                               @Param("templateId") Long templateId,
                                               @Param("batchNo") String batchNo,
                                               @Param("status") CouponRedeemCodeStatus status,
                                               @Param("createdFrom") LocalDateTime createdFrom,
                                               @Param("createdTo") LocalDateTime createdTo);
}
