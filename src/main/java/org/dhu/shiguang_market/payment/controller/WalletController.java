package org.dhu.shiguang_market.payment.controller;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.WalletTransactionType;
import org.dhu.shiguang_market.payment.dto.PaymentDtos.RechargeRequest;
import org.dhu.shiguang_market.payment.dto.PaymentDtos.WalletOperationView;
import org.dhu.shiguang_market.payment.dto.PaymentDtos.WalletTransactionView;
import org.dhu.shiguang_market.payment.dto.PaymentDtos.WalletView;
import org.dhu.shiguang_market.payment.service.WalletService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {
    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public ApiResponse<WalletView> wallet() {
        return ApiResponse.success(walletService.wallet());
    }

    @GetMapping("/transactions")
    public ApiResponse<PageView<WalletTransactionView>> transactions(
            @RequestParam(required = false) WalletTransactionType transactionType,
            @RequestParam(required = false) LocalDateTime createdFrom,
            @RequestParam(required = false) LocalDateTime createdTo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(walletService.transactions(transactionType, createdFrom, createdTo, page, pageSize));
    }

    @PostMapping("/recharges")
    public ResponseEntity<ApiResponse<WalletOperationView>> recharge(
            @Valid @RequestBody RechargeRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(walletService.recharge(request, idempotencyKey)));
    }
}
