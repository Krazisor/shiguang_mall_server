package org.dhu.shiguang_market.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.coupon.model.CouponModels.ClaimRecord;

public interface CouponClaimRecordMapper extends BaseMapper<ClaimRecord> {
    @Select("""
            SELECT u.id AS user_id, t.id AS template_id
            FROM sys_user u
            JOIN coupon_template t
              ON t.owner_type = 'PLATFORM'
             AND t.distribution_type = 'SYSTEM_GRANT'
             AND t.audience_type = 'NEW_USERS'
             AND t.status = 'ACTIVE'
            LEFT JOIN coupon_activity a ON a.id = t.activity_id
            WHERE u.status = 'ACTIVE'
              AND u.deleted_at IS NULL
              AND (t.activity_id IS NULL OR (a.status = 'RUNNING'
                   AND a.starts_at <= CURRENT_TIMESTAMP(3) AND a.ends_at > CURRENT_TIMESTAMP(3)))
              AND NOT EXISTS (
                  SELECT 1
                  FROM coupon_claim_record c
                  WHERE c.business_no = CONCAT('SYSTEM_GRANT:USER_REGISTERED:', u.id, ':', t.id)
              )
            ORDER BY u.id ASC, t.id ASC
            LIMIT #{size}
            """)
    List<SystemGrantCandidate> selectMissingSystemGrantCandidates(@Param("size") int size);

    record SystemGrantCandidate(long userId, long templateId) {
    }
}
