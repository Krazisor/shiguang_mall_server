package org.dhu.shiguang_market.merchantwallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import org.dhu.shiguang_market.common.model.MarketEnums.SettlementStatus;
import org.dhu.shiguang_market.merchantwallet.model.ShopSettlement;

public interface ShopSettlementMapper extends BaseMapper<ShopSettlement> {
    @Select("SELECT * FROM shop_settlement WHERE order_id = #{orderId} AND shop_id = #{shopId} FOR UPDATE")
    ShopSettlement selectByOrderAndShopForUpdate(long orderId, long shopId);

    @Select("SELECT * FROM shop_settlement WHERE id = #{id} FOR UPDATE")
    ShopSettlement selectByIdForUpdate(@Param("id") long id);

    @Select("""
            <script>
            SELECT s.*
            FROM shop_settlement s
            JOIN order_info o ON o.id = s.order_id AND o.shop_id = s.shop_id
            WHERE s.shop_id = #{shopId}
              <if test='status != null'>AND s.status = #{status}</if>
              <if test='orderNo != null and orderNo != ""'>AND o.order_no LIKE CONCAT('%', #{orderNo}, '%')</if>
              <if test='createdFrom != null'>AND s.created_at &gt;= #{createdFrom}</if>
              <if test='createdTo != null'>AND s.created_at &lt; #{createdTo}</if>
            ORDER BY s.created_at DESC, s.id DESC
            </script>
            """)
    Page<ShopSettlement> selectShopPage(Page<ShopSettlement> page,
                                         @Param("shopId") long shopId,
                                         @Param("status") SettlementStatus status,
                                         @Param("orderNo") String orderNo,
                                         @Param("createdFrom") LocalDateTime createdFrom,
                                         @Param("createdTo") LocalDateTime createdTo);
}
