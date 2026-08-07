package org.dhu.shiguang_market.integration.shop;

import java.util.List;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;

/** 为 B 线响应组装批量提供稳定店铺摘要。 */
public interface ShopSummaryPort {
    List<ShopSummary> findByIds(List<Long> shopIds);

    record ShopSummary(
            long shopId, String shopNo, String shopName, String logoUrl, ShopStatus status) {
    }
}
