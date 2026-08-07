package org.dhu.shiguang_market.integration.product;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dhu.shiguang_market.integration.product.ProductAvailabilityPort.ProductAvailability;

/** 默认商品可购买，可按 SKU 预设下架、停用等失败原因。 */
public class ProductAvailabilityPortFake implements ProductAvailabilityPort {
    private final Map<Long, String> unavailableReasons = new HashMap<>();

    public void setUnavailable(long skuId, String reason) {
        unavailableReasons.put(skuId, reason == null ? "商品当前不可购买" : reason);
    }

    public void setAvailable(long skuId) {
        unavailableReasons.remove(skuId);
    }

    @Override
    public List<ProductAvailability> check(List<Long> skuIds) {
        if (skuIds == null) return List.of();
        return skuIds.stream().map(skuId -> {
            String reason = unavailableReasons.get(skuId);
            return new ProductAvailability(skuId, reason == null, reason);
        }).toList();
    }
}
