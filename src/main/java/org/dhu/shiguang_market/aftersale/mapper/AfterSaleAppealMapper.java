package org.dhu.shiguang_market.aftersale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleAppealStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleAppealTriggerType;
import org.dhu.shiguang_market.aftersale.model.AfterSaleAppeal;

public interface AfterSaleAppealMapper extends BaseMapper<AfterSaleAppeal> {
    @Select("""
            <script>
            SELECT a.*
            FROM after_sale_appeal a
            JOIN after_sale_request r ON r.id = a.after_sale_id
            WHERE 1 = 1
              <if test='status != null'>AND a.status = #{status}</if>
              <if test='triggerType != null'>AND a.trigger_type = #{triggerType}</if>
              <if test='shopId != null'>AND a.shop_id = #{shopId}</if>
              <if test='afterSaleNo != null and afterSaleNo != ""'>
                AND r.after_sale_no LIKE CONCAT('%', #{afterSaleNo}, '%')
              </if>
              <if test='createdFrom != null'>AND a.created_at &gt;= #{createdFrom}</if>
              <if test='createdTo != null'>AND a.created_at &lt; #{createdTo}</if>
            ORDER BY a.created_at DESC, a.id DESC
            </script>
            """)
    Page<AfterSaleAppeal> selectPlatformPage(Page<AfterSaleAppeal> page,
                                               @Param("status") AfterSaleAppealStatus status,
                                               @Param("triggerType") AfterSaleAppealTriggerType triggerType,
                                               @Param("shopId") Long shopId,
                                               @Param("afterSaleNo") String afterSaleNo,
                                               @Param("createdFrom") LocalDateTime createdFrom,
                                               @Param("createdTo") LocalDateTime createdTo);
}
