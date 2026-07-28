package org.dhu.shiguang_market.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.dhu.shiguang_market.inventory.model.InventoryStock;

public interface InventoryStockMapper extends BaseMapper<InventoryStock> {
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
}
