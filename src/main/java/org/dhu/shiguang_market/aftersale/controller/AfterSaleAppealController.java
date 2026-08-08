package org.dhu.shiguang_market.aftersale.controller;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.CreateAfterSaleAppealRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.DecideAfterSaleAppealRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.MerchantNotificationView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.PlatformAfterSaleAppealDetailView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.PlatformAfterSaleAppealSummaryView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleAppealDetailView;
import org.dhu.shiguang_market.aftersale.service.AfterSaleAppealService;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleAppealStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleAppealTriggerType;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantNotificationType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AfterSaleAppealController {
    private final AfterSaleAppealService service;

    public AfterSaleAppealController(AfterSaleAppealService service) { this.service = service; }

    @PostMapping("/after-sales/{afterSaleId}/appeal")
    public ResponseEntity<ApiResponse<AfterSaleAppealDetailView>> create(
            @PathVariable long afterSaleId, @Valid @RequestBody CreateAfterSaleAppealRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(afterSaleId, request, key)));
    }

    @GetMapping("/after-sales/{afterSaleId}/appeal")
    public ApiResponse<AfterSaleAppealDetailView> buyerDetail(@PathVariable long afterSaleId) {
        return ApiResponse.success(service.buyerDetail(afterSaleId));
    }

    @GetMapping("/platform/after-sale-appeals")
    public ApiResponse<PageView<PlatformAfterSaleAppealSummaryView>> list(
            @RequestParam(required = false) AfterSaleAppealStatus status,
            @RequestParam(required = false) AfterSaleAppealTriggerType triggerType,
            @RequestParam(required = false) Long shopId, @RequestParam(required = false) String afterSaleNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(service.list(status, triggerType, shopId, afterSaleNo, createdFrom, createdTo, page, pageSize));
    }

    @GetMapping("/platform/after-sale-appeals/{appealId}")
    public ApiResponse<PlatformAfterSaleAppealDetailView> detail(@PathVariable long appealId) {
        return ApiResponse.success(service.platformDetail(appealId));
    }

    @PostMapping("/platform/after-sale-appeals/{appealId}/decide")
    public ApiResponse<PlatformAfterSaleAppealDetailView> decide(@PathVariable long appealId,
            @Valid @RequestBody DecideAfterSaleAppealRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(service.decide(appealId, request, key));
    }

    @GetMapping("/shops/{shopId}/notifications")
    public ApiResponse<PageView<MerchantNotificationView>> notifications(@PathVariable long shopId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) MerchantNotificationType notificationType,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(service.notifications(shopId, unreadOnly, notificationType, page, pageSize));
    }

    @PostMapping("/shops/{shopId}/notifications/{notificationId}/read")
    public ApiResponse<MerchantNotificationView> read(@PathVariable long shopId, @PathVariable long notificationId) {
        return ApiResponse.success(service.markRead(shopId, notificationId));
    }
}
