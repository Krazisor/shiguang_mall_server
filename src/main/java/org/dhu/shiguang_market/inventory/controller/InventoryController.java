package org.dhu.shiguang_market.inventory.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.inventory.dto.InventoryDtos.InventoryInboundRequest;
import org.dhu.shiguang_market.inventory.dto.InventoryDtos.InventoryItemView;
import org.dhu.shiguang_market.inventory.dto.InventoryDtos.InventoryOperationView;
import org.dhu.shiguang_market.inventory.service.InventoryService;
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

@RestController
@RequestMapping("/api/shops/{shopId}/inventory")
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ApiResponse<PageView<InventoryItemView>> list(
            @PathVariable long shopId, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long spuId, @RequestParam(required = false) String stockState,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(inventoryService.list(shopId, keyword, spuId, stockState, page, pageSize));
    }

    @GetMapping("/{skuId}")
    public ApiResponse<InventoryItemView> detail(@PathVariable long shopId, @PathVariable long skuId) {
        return ApiResponse.success(inventoryService.detail(shopId, skuId));
    }

    @PostMapping("/{skuId}/inbounds")
    public ResponseEntity<ApiResponse<InventoryOperationView>> inbound(
            @PathVariable long shopId, @PathVariable long skuId,
            @Valid @RequestBody InventoryInboundRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(inventoryService.inbound(shopId, skuId, request, idempotencyKey)));
    }
}
