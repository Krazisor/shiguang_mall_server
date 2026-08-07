package org.dhu.shiguang_market.aftersale.controller;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleDetailView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleEligibilityView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.AfterSaleSummaryView;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.CreateAfterSaleRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.ReturnShipmentRequest;
import org.dhu.shiguang_market.aftersale.dto.AfterSaleDtos.UpdateReturnShipmentRequest;
import org.dhu.shiguang_market.aftersale.service.AfterSaleService;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 买家端售后接口。
 *
 * <p>Controller 只负责 HTTP 参数绑定、请求校验和统一响应包装；本人资源校验、
 * 状态机和幂等处理均由 {@link AfterSaleService} 完成。</p>
 */
@RestController
@RequestMapping("/api")
public class AfterSaleController {
    private final AfterSaleService afterSaleService;

    public AfterSaleController(AfterSaleService afterSaleService) {
        this.afterSaleService = afterSaleService;
    }

    /** 查询指定订单明细当前可申请的售后类型、数量和金额。 */
    @GetMapping("/orders/{orderId}/items/{orderItemId}/after-sale-eligibility")
    public ApiResponse<AfterSaleEligibilityView> eligibility(
            @PathVariable long orderId, @PathVariable long orderItemId) {
        return ApiResponse.success(afterSaleService.eligibility(orderId, orderItemId));
    }

    /** 创建售后申请；Idempotency-Key 必填，创建成功返回 HTTP 201。 */
    @PostMapping("/after-sales")
    public ResponseEntity<ApiResponse<AfterSaleDetailView>> create(
            @Valid @RequestBody CreateAfterSaleRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(afterSaleService.create(request, idempotencyKey)));
    }

    /** 按状态、类型、订单号和创建时间分页查询当前买家的售后申请。 */
    @GetMapping("/after-sales")
    public ApiResponse<PageView<AfterSaleSummaryView>> list(
            @RequestParam(required = false) AfterSaleStatus status,
            @RequestParam(required = false) AfterSaleType requestType,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(afterSaleService.list(status, requestType, orderNo,
                createdFrom, createdTo, page, pageSize));
    }

    /** 查询当前买家拥有的售后详情。 */
    @GetMapping("/after-sales/{afterSaleId}")
    public ApiResponse<AfterSaleDetailView> detail(@PathVariable long afterSaleId) {
        return ApiResponse.success(afterSaleService.detail(afterSaleId));
    }

    /** 撤销仍处于待审核状态的售后申请。 */
    @PostMapping("/after-sales/{afterSaleId}/cancel")
    public ApiResponse<AfterSaleDetailView> cancel(@PathVariable long afterSaleId) {
        return ApiResponse.success(afterSaleService.cancel(afterSaleId));
    }

    /** 首次提交退货物流；幂等键用于避免重复提交。 */
    @PostMapping("/after-sales/{afterSaleId}/return-shipment")
    public ApiResponse<AfterSaleDetailView> submitReturnShipment(
            @PathVariable long afterSaleId,
            @Valid @RequestBody ReturnShipmentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(
                afterSaleService.submitReturnShipment(afterSaleId, request, idempotencyKey));
    }

    /** 商家确认收货前，买家可携带当前 version 更正完整物流信息。 */
    @PutMapping("/after-sales/{afterSaleId}/return-shipment")
    public ApiResponse<AfterSaleDetailView> updateReturnShipment(
            @PathVariable long afterSaleId,
            @Valid @RequestBody UpdateReturnShipmentRequest request) {
        return ApiResponse.success(afterSaleService.updateReturnShipment(afterSaleId, request));
    }
}
