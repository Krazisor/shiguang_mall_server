package org.dhu.shiguang_market.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import org.dhu.shiguang_market.common.model.MarketEnums.InventoryTransactionType;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ShopSkuView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.OperatorBrief;

public final class InventoryDtos {
    private InventoryDtos() {
    }

    public record InventoryInboundRequest(
            @Min(1) @Max(Integer.MAX_VALUE) int quantity, @Size(max = 500) String remark) {
    }

    /** 人工调整请求，两个变化量至少一个非零的组合校验由 Service 完成。 */
    public record InventoryAdjustmentRequest(
            int availableChange, int lockedChange, @Min(0) int version,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record InventoryItemView(String spuId, String spuNo, String productName, ShopSkuView sku) {
    }

    public record InventoryOperationView(
            String transactionNo, String skuId, InventoryTransactionType transactionType,
            int availableChange, int lockedChange, int availableAfter, int lockedAfter,
            String businessType, String businessNo, String remark, OffsetDateTime createdAt) {
    }

    /** 库存流水分页项，变化前库存由变化后库存减去本次变化量得到。 */
    public record InventoryTransactionView(
            String id, String transactionNo, String skuId, InventoryTransactionType transactionType,
            int availableChange, int lockedChange, int availableBefore, int lockedBefore,
            int availableAfter, int lockedAfter, int version, String businessType, String businessNo,
            OperatorBrief operator, String remark, OffsetDateTime createdAt) {
    }
}
