package org.dhu.shiguang_market.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.inventory.model.InventoryTransaction;

public interface InventoryTransactionMapper extends BaseMapper<InventoryTransaction> {
    /**
     * 按店铺分页查询库存流水。
     *
     * <p>通过 product_sku 关联限定 shop_id，确保店铺只能读取自己的 SKU 流水。</p>
     */
    @Select("""
            <script>
            SELECT tx.*
            FROM inventory_transaction tx
            JOIN product_sku sku ON sku.id = tx.sku_id AND sku.deleted_at IS NULL
            WHERE sku.shop_id = #{shopId}
            <if test="skuId != null">
              AND tx.sku_id = #{skuId}
            </if>
            <if test="transactionType != null">
              AND tx.transaction_type = #{transactionType}
            </if>
            <if test="businessType != null">
              AND tx.business_type = #{businessType}
            </if>
            <if test="businessNo != null">
              AND tx.business_no = #{businessNo}
            </if>
            <if test="createdFrom != null">
              AND tx.created_at &gt;= #{createdFrom}
            </if>
            <if test="createdTo != null">
              AND tx.created_at &lt; #{createdTo}
            </if>
            ORDER BY tx.created_at DESC, tx.id DESC
            </script>
            """)
    Page<InventoryTransaction> selectTransactionPage(
            Page<InventoryTransaction> page,
            @Param("shopId") long shopId,
            @Param("skuId") Long skuId,
            @Param("transactionType") String transactionType,
            @Param("businessType") String businessType,
            @Param("businessNo") String businessNo,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo);
}
