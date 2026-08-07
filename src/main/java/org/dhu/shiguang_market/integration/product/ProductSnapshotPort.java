package org.dhu.shiguang_market.integration.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;

/** B 线结算和下单使用的稳定商品快照端口。 */
public interface ProductSnapshotPort {
    List<ProductSnapshot> findBySkuIds(List<Long> skuIds);

    record ProductSnapshot(
            long skuId, long spuId, long shopId, String spuNo, String skuNo,
            String productName, String skuName, Map<String, String> specifications,
            String imageUrl, BigDecimal salePrice, ProductStatus productStatus,
            EnabledStatus skuStatus) {
        public ProductSnapshot {
            specifications = specifications == null ? Map.of() : Map.copyOf(specifications);
        }
    }
}
