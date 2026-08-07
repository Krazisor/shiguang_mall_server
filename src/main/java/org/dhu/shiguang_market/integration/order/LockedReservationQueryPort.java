package org.dhu.shiguang_market.integration.order;

/** A 线调整库存时查询指定 SKU 当前有效订单预占总量的只读端口。 */
public interface LockedReservationQueryPort {
    int lockedQuantity(long skuId);
}
