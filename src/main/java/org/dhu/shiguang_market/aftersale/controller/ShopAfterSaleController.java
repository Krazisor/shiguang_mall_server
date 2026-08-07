package org.dhu.shiguang_market.aftersale.controller;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ApproveAfterSaleRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ConfirmReturnReceivedRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.RejectAfterSaleRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.RetryRefundRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ShopAfterSaleDetailView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ShopAfterSaleSummaryView;
import org.dhu.shiguang_market.aftersale.service.ShopAfterSaleService;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleType;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家端售后接口。
 *
 * <p>所有方法都将 shopId 传入 Service，由 Service 统一校验
 * {@code shop:after-sale:manage} 权限和售后单的店铺归属。</p>
 */
@RestController
@RequestMapping("/api/shops/{shopId}/after-sales")
public class ShopAfterSaleController {
    private final ShopAfterSaleService shopAfterSaleService;

    public ShopAfterSaleController(ShopAfterSaleService shopAfterSaleService) {
        this.shopAfterSaleService = shopAfterSaleService;
    }

    /** 分页查询本店售后申请，可按状态、退款状态、类型和关键词筛选。 */
    @GetMapping
    public ApiResponse<PageView<ShopAfterSaleSummaryView>> list(
            @PathVariable long shopId,
            @RequestParam(required = false) AfterSaleStatus status,
            @RequestParam(required = false) RefundStatus refundStatus,
            @RequestParam(required = false) AfterSaleType requestType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(shopAfterSaleService.list(shopId, status, refundStatus,
                requestType, keyword, createdFrom, createdTo, page, pageSize));
    }

    /** 查询属于本店的售后详情。 */
    @GetMapping("/{afterSaleId}")
    public ApiResponse<ShopAfterSaleDetailView> detail(
            @PathVariable long shopId, @PathVariable long afterSaleId) {
        return ApiResponse.success(shopAfterSaleService.detail(shopId, afterSaleId));
    }

    /** 批准售后；仅退款分支会立即执行退款，因此幂等键必须提供。 */
    @PostMapping("/{afterSaleId}/approve")
    public ApiResponse<ShopAfterSaleDetailView> approve(
            @PathVariable long shopId, @PathVariable long afterSaleId,
            @Valid @RequestBody ApproveAfterSaleRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(
                shopAfterSaleService.approve(shopId, afterSaleId, request, idempotencyKey));
    }

    /** 拒绝待处理的售后申请。 */
    @PostMapping("/{afterSaleId}/reject")
    public ApiResponse<ShopAfterSaleDetailView> reject(
            @PathVariable long shopId, @PathVariable long afterSaleId,
            @Valid @RequestBody RejectAfterSaleRequest request) {
        return ApiResponse.success(shopAfterSaleService.reject(shopId, afterSaleId, request));
    }

    /** 确认收到买家退货并启动退款；幂等键避免重复回库和入账。 */
    @PostMapping("/{afterSaleId}/confirm-return-received")
    public ApiResponse<ShopAfterSaleDetailView> confirmReturnReceived(
            @PathVariable long shopId, @PathVariable long afterSaleId,
            @Valid @RequestBody ConfirmReturnReceivedRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(shopAfterSaleService.confirmReturnReceived(
                shopId, afterSaleId, request, idempotencyKey));
    }

    /** 对 REFUNDING/FAILED 的售后单重试退款，继续复用原退款编号。 */
    @PostMapping("/{afterSaleId}/refund/retry")
    public ApiResponse<ShopAfterSaleDetailView> retryRefund(
            @PathVariable long shopId, @PathVariable long afterSaleId,
            @Valid @RequestBody RetryRefundRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(
                shopAfterSaleService.retryRefund(shopId, afterSaleId, request, idempotencyKey));
    }
}
