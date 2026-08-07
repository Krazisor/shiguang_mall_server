package org.dhu.shiguang_market.integration.inventory;

import java.util.ArrayList;
import java.util.List;
import org.dhu.shiguang_market.integration.inventory.InventoryTracePort.InventoryTrace;

/** 使用内存列表模拟库存流水追踪，返回值使用不可变副本。 */
public class InventoryTracePortFake implements InventoryTracePort {
    private final List<InventoryTrace> values = new ArrayList<>();

    public void add(InventoryTrace trace) {
        values.add(trace);
    }

    @Override
    public List<InventoryTrace> findByBusiness(String businessType, String businessNo) {
        return values.stream()
                .filter(value -> value.businessType().equals(businessType)
                        && value.businessNo().equals(businessNo))
                .toList();
    }
}
