package org.dhu.shiguang_market.integration.inventory;

/**
 * B 线订单和售后调用的库存变更端口。
 * 每个操作都携带业务键，A 线实现需据此保证幂等。
 */
public interface InventoryReservationPort {
    InventoryBalance lock(String businessKey, long skuId, int quantity);

    InventoryBalance release(String businessKey, long skuId, int quantity);

    InventoryBalance deduct(String businessKey, long skuId, int quantity);

    InventoryBalance returnStock(String businessKey, long skuId, int quantity);

    record InventoryBalance(long skuId, int availableQuantity, int lockedQuantity) {
    }
}
