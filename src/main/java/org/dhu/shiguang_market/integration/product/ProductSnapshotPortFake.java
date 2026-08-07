package org.dhu.shiguang_market.integration.product;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dhu.shiguang_market.integration.product.ProductSnapshotPort.ProductSnapshot;

/** 使用内存快照模拟 A 线商品查询，严格保持输入 SKU 顺序。 */
public class ProductSnapshotPortFake implements ProductSnapshotPort {
    private final Map<Long, ProductSnapshot> values = new LinkedHashMap<>();

    public void put(ProductSnapshot snapshot) {
        values.put(snapshot.skuId(), snapshot);
    }

    @Override
    public List<ProductSnapshot> findBySkuIds(List<Long> skuIds) {
        if (skuIds == null) return List.of();
        return skuIds.stream().map(values::get).filter(value -> value != null).toList();
    }
}
