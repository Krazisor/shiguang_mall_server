package org.dhu.shiguang_market.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dhu.shiguang_market.inventory.model.InventoryStock;

public interface InventoryStockMapper extends BaseMapper<InventoryStock> {
    /** 对比库存锁定量与仍处于 LOCKED 状态的订单明细汇总，只返回差异数量。 */
    @Select("""
            SELECT COUNT(*) FROM inventory_stock stock
            WHERE stock.locked_quantity <> COALESCE((
                SELECT SUM(item.quantity) FROM order_item item
                WHERE item.sku_id = stock.sku_id AND item.reservation_status = 'LOCKED'
            ), 0)
            """)
    int countReconciliationMismatches();

    @Update("""
            UPDATE inventory_stock
            SET available_quantity = available_quantity - #{quantity},
                locked_quantity = locked_quantity + #{quantity}, version = version + 1
            WHERE sku_id = #{skuId} AND available_quantity >= #{quantity}
            """)
    int reserve(@Param("skuId") long skuId, @Param("quantity") int quantity);

    @Update("""
            UPDATE inventory_stock
            SET available_quantity = available_quantity + #{quantity},
                locked_quantity = locked_quantity - #{quantity}, version = version + 1
            WHERE sku_id = #{skuId} AND locked_quantity >= #{quantity}
            """)
    int release(@Param("skuId") long skuId, @Param("quantity") int quantity);

    @Update("""
            UPDATE inventory_stock
            SET locked_quantity = locked_quantity - #{quantity}, version = version + 1
            WHERE sku_id = #{skuId} AND locked_quantity >= #{quantity}
            """)
    int deduct(@Param("skuId") long skuId, @Param("quantity") int quantity);

    @Update("""
            UPDATE inventory_stock
            SET available_quantity = available_quantity + #{quantity}, version = version + 1
            WHERE sku_id = #{skuId}
            """)
    int returnStock(@Param("skuId") long skuId, @Param("quantity") int quantity);
}
