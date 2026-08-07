package org.dhu.shiguang_market.integration.inventory;

import java.time.OffsetDateTime;
import java.util.List;
import org.dhu.shiguang_market.common.model.MarketEnums.InventoryTransactionType;

/** 平台运营根据业务键读取库存流水摘要的只读端口。 */
public interface InventoryTracePort {
    List<InventoryTrace> findByBusiness(String businessType, String businessNo);

    record InventoryTrace(
            long transactionId, String transactionNo, long skuId,
            InventoryTransactionType transactionType, int availableChange, int lockedChange,
            String businessType, String businessNo, OffsetDateTime createdAt) {
    }
}
