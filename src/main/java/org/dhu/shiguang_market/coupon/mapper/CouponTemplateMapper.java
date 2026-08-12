package org.dhu.shiguang_market.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponTemplate;

public interface CouponTemplateMapper extends BaseMapper<CouponTemplate> {
    @Update("""
            UPDATE coupon_template
            SET issued_count=issued_count+1,
                budget_reserved_amount=budget_reserved_amount+#{liability},
                first_issued_at=COALESCE(first_issued_at, CURRENT_TIMESTAMP(3)),
                version=version+1
            WHERE id=#{templateId} AND status='ACTIVE'
              AND issued_count < total_issue_limit
              AND budget_reserved_amount+budget_consumed_amount-budget_reversed_amount+#{liability} <= budget_amount
            """)
    int reserveIssue(@Param("templateId") long templateId, @Param("liability") java.math.BigDecimal liability);

    @Update("""
            UPDATE coupon_template
            SET budget_reserved_amount=budget_reserved_amount-#{liability},
                budget_consumed_amount=budget_consumed_amount+#{actual},
                version=version+1
            WHERE id=#{templateId} AND budget_reserved_amount >= #{liability}
            """)
    int consumeBudget(@Param("templateId") long templateId,
                      @Param("liability") java.math.BigDecimal liability,
                      @Param("actual") java.math.BigDecimal actual);

    @Update("""
            UPDATE coupon_template
            SET budget_reserved_amount=budget_reserved_amount-#{liability}, version=version+1
            WHERE id=#{templateId} AND budget_reserved_amount >= #{liability}
            """)
    int releaseBudget(@Param("templateId") long templateId,
                      @Param("liability") java.math.BigDecimal liability);

    @Update("""
            UPDATE coupon_template
            SET budget_reversed_amount=budget_reversed_amount+#{amount}, version=version+1
            WHERE id=#{templateId}
              AND budget_consumed_amount >= budget_reversed_amount+#{amount}
            """)
    int reverseBudget(@Param("templateId") long templateId,
                      @Param("amount") java.math.BigDecimal amount);

    @Update("""
            UPDATE coupon_template
            SET budget_reserved_amount=budget_reserved_amount+#{liability}, version=version+1
            WHERE id=#{templateId}
              AND budget_reserved_amount+budget_consumed_amount-budget_reversed_amount+#{liability}
                  <= budget_amount
            """)
    int restoreBudget(@Param("templateId") long templateId,
                      @Param("liability") java.math.BigDecimal liability);
}
