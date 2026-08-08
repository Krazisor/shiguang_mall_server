package org.dhu.shiguang_market.merchantwallet.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWalletBucket;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWalletTransactionType;
import org.dhu.shiguang_market.common.model.MarketEnums.MerchantWithdrawalStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.SettlementStatus;
import org.dhu.shiguang_market.merchantwallet.dto.MerchantWalletDtos.CreateMerchantWithdrawalRequest;
import org.dhu.shiguang_market.merchantwallet.dto.MerchantWalletDtos.MerchantWalletTransactionView;
import org.dhu.shiguang_market.merchantwallet.dto.MerchantWalletDtos.MerchantWalletView;
import org.dhu.shiguang_market.merchantwallet.dto.MerchantWalletDtos.MerchantWithdrawalView;
import org.dhu.shiguang_market.merchantwallet.dto.MerchantWalletDtos.ShopSettlementView;
import org.dhu.shiguang_market.merchantwallet.service.MerchantWalletService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/shops/{shopId}/merchant-wallet")
public class MerchantWalletController {
    private final MerchantWalletService service;
    public MerchantWalletController(MerchantWalletService service) { this.service = service; }

    @GetMapping
    public ApiResponse<MerchantWalletView> wallet(@PathVariable long shopId) { return ApiResponse.success(service.wallet(shopId)); }

    @GetMapping("/transactions")
    public ApiResponse<PageView<MerchantWalletTransactionView>> transactions(@PathVariable long shopId,
            @RequestParam(required = false) MerchantWalletTransactionType transactionType,
            @RequestParam(required = false) MerchantWalletBucket bucket,
            @RequestParam(required = false) String businessType, @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(service.transactions(shopId, transactionType, bucket, businessType, businessNo,
                createdFrom, createdTo, page, pageSize));
    }

    @GetMapping("/settlements")
    public ApiResponse<PageView<ShopSettlementView>> settlements(@PathVariable long shopId,
            @RequestParam(required = false) String orderNo, @RequestParam(required = false) SettlementStatus settlementStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(service.settlements(shopId, settlementStatus, orderNo, createdFrom, createdTo, page, pageSize));
    }

    @PostMapping("/withdrawals")
    public ResponseEntity<ApiResponse<MerchantWithdrawalView>> withdraw(@PathVariable long shopId,
            @Valid @RequestBody CreateMerchantWithdrawalRequest request, @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.withdraw(shopId, request, key)));
    }

    @GetMapping("/withdrawals")
    public ApiResponse<PageView<MerchantWithdrawalView>> withdrawals(@PathVariable long shopId,
            @RequestParam(required = false) MerchantWithdrawalStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(service.withdrawals(shopId, status, createdFrom, createdTo, page, pageSize));
    }
}
