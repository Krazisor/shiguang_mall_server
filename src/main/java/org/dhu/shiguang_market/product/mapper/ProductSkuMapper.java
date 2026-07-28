package org.dhu.shiguang_market.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.product.model.ProductSku;

public interface ProductSkuMapper extends BaseMapper<ProductSku> {
    @Select("""
            SELECT COUNT(*)
            FROM product_sku
            WHERE spu_id = #{spuId} AND spec_key = #{specKey}
            """)
    int countSpecIncludingDeleted(@Param("spuId") long spuId, @Param("specKey") String specKey);

    @Select("""
            SELECT ps.*
            FROM product_sku ps
            JOIN product_spu p ON p.id = ps.spu_id AND p.deleted_at IS NULL
            JOIN inventory_stock ist ON ist.sku_id = ps.id
            WHERE ps.deleted_at IS NULL
              AND ps.shop_id = #{shopId}
              AND (#{spuId} IS NULL OR ps.spu_id = #{spuId})
              AND (#{keyword} IS NULL
                   OR p.product_name LIKE CONCAT('%', #{keyword}, '%')
                   OR p.spu_no LIKE CONCAT('%', #{keyword}, '%')
                   OR ps.sku_no LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{stockState} IS NULL
                   OR (#{stockState} = 'OUT_OF_STOCK' AND ist.available_quantity = 0)
                   OR (#{stockState} = 'LOW_STOCK' AND ist.available_quantity BETWEEN 1 AND 10)
                   OR (#{stockState} = 'IN_STOCK' AND ist.available_quantity > 10))
            ORDER BY ps.updated_at DESC, ps.id DESC
            """)
    Page<ProductSku> selectInventoryPage(
            Page<ProductSku> page,
            @Param("shopId") long shopId,
            @Param("spuId") Long spuId,
            @Param("keyword") String keyword,
            @Param("stockState") String stockState);
}
