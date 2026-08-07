package org.dhu.shiguang_market.integration.shop;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dhu.shiguang_market.integration.shop.ShopSummaryPort.ShopSummary;

/** 按测试预设返回店铺摘要，结果顺序与输入店铺 ID 一致。 */
public class ShopSummaryPortFake implements ShopSummaryPort {
    private final Map<Long, ShopSummary> values = new LinkedHashMap<>();

    public void put(ShopSummary summary) {
        values.put(summary.shopId(), summary);
    }

    @Override
    public List<ShopSummary> findByIds(List<Long> shopIds) {
        if (shopIds == null) return List.of();
        return shopIds.stream().map(values::get).filter(value -> value != null).toList();
    }
}
