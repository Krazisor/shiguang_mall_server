package org.dhu.shiguang_market.payment.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.payment.dto.PaymentDtos.CreatePaymentRequest;
import org.dhu.shiguang_market.payment.dto.PaymentDtos.PaymentResultView;
import org.dhu.shiguang_market.payment.dto.PaymentDtos.PaymentView;
import org.dhu.shiguang_market.payment.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/trades/{tradeId}/payments")
    public ResponseEntity<ApiResponse<PaymentView>> create(
            @PathVariable long tradeId, @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(paymentService.create(tradeId, request, idempotencyKey)));
    }

    @PostMapping("/payments/{paymentId}/confirm")
    public ApiResponse<PaymentResultView> confirm(
            @PathVariable long paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(paymentService.confirm(paymentId, idempotencyKey));
    }

    @GetMapping("/payments/{paymentId}")
    public ApiResponse<PaymentView> detail(@PathVariable long paymentId) {
        return ApiResponse.success(paymentService.detail(paymentId));
    }
}
