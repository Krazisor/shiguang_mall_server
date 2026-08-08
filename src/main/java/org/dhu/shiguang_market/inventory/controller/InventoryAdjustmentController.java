package org.dhu.shiguang_market.inventory.controller;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.InventoryTransactionType;
import org.dhu.shiguang_market.inventory.dto.InventoryDtos.InventoryAdjustmentRequest;
import org.dhu.shiguang_market.inventory.dto.InventoryDtos.InventoryTransactionView;
import org.dhu.shiguang_market.inventory.service.InventoryAdjustmentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** phase-2 库存调整与流水接口。 */
@RestController
@RequestMapping("/api/shops/{shopId}/inventory")
public class InventoryAdjustmentController {
    private final InventoryAdjustmentService service;

    public InventoryAdjustmentController(InventoryAdjustmentService service) {
        this.service = service;
    }

    /** 分页查询本店库存流水。 */
    @GetMapping("/transactions")
    public ApiResponse<PageView<InventoryTransactionView>> transactions(
            @PathVariable long shopId,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) InventoryTransactionType transactionType,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(service.transactions(shopId, skuId, transactionType,
                businessType, businessNo, createdFrom, createdTo, page, pageSize));
    }

    /** 人工调整指定 SKU 的可用库存和锁定库存，成功时返回 201。 */
    @PostMapping("/{skuId}/adjustments")
    public ResponseEntity<ApiResponse<InventoryTransactionView>> adjust(
            @PathVariable long shopId,
            @PathVariable long skuId,
            @Valid @RequestBody InventoryAdjustmentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.adjust(shopId, skuId, request, idempotencyKey)));
    }
}
