package org.dhu.shiguang_market.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import org.dhu.shiguang_market.common.model.MarketEnums.InventoryTransactionType;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ShopSkuView;

public final class InventoryDtos {
    private InventoryDtos() {
    }

    public record InventoryInboundRequest(
            @Min(1) @Max(Integer.MAX_VALUE) int quantity, @Size(max = 500) String remark) {
    }

    public record InventoryItemView(String spuId, String spuNo, String productName, ShopSkuView sku) {
    }

    public record InventoryOperationView(
            String transactionNo, String skuId, InventoryTransactionType transactionType,
            int availableChange, int lockedChange, int availableAfter, int lockedAfter,
            String businessType, String businessNo, String remark, OffsetDateTime createdAt) {
    }
}
