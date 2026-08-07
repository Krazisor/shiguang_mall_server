package org.dhu.shiguang_market.integration.product;

import java.util.List;

/** 下单前重新校验商品与 SKU 可购买状态的端口。 */
public interface ProductAvailabilityPort {
    List<ProductAvailability> check(List<Long> skuIds);

    record ProductAvailability(long skuId, boolean purchasable, String reason) {
    }
}
