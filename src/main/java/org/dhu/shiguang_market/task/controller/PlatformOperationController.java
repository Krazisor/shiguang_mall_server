package org.dhu.shiguang_market.task.controller;

import java.time.LocalDateTime;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.AfterSaleStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.PaymentOrderStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.RefundStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.TradeStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.task.dto.OperationDtos.BusinessTraceView;
import org.dhu.shiguang_market.task.dto.OperationDtos.OperationAfterSaleView;
import org.dhu.shiguang_market.task.dto.OperationDtos.OperationOrderView;
import org.dhu.shiguang_market.task.dto.OperationDtos.OperationPaymentView;
import org.dhu.shiguang_market.task.dto.OperationDtos.OperationTradeView;
import org.dhu.shiguang_market.task.service.PlatformOperationService;
import org.dhu.shiguang_market.merchantwallet.dto.MerchantWalletDtos.MerchantWalletTransactionView;
import org.dhu.shiguang_market.merchantwallet.dto.MerchantWalletDtos.MerchantWalletView;
import org.dhu.shiguang_market.merchantwallet.dto.MerchantWalletDtos.MerchantWithdrawalView;
import org.dhu.shiguang_market.merchantwallet.dto.MerchantWalletDtos.ShopSettlementView;
import org.dhu.shiguang_market.merchantwallet.service.MerchantWalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 平台运营只读接口；权限统一在 Controller 入口校验，查询服务只负责数据组装。 */
@RestController
@RequestMapping("/api/platform/operations")
public class PlatformOperationController {
    private static final String READ_PERMISSION = "platform:operation:read";
    private final PlatformOperationService service;
    private final CurrentUserService currentUser;
    private final MerchantWalletService merchantWallet;

    @Autowired
    public PlatformOperationController(PlatformOperationService service, CurrentUserService currentUser,
                                       MerchantWalletService merchantWallet) {
        this.service = service;
        this.currentUser = currentUser;
        this.merchantWallet = merchantWallet;
    }

    public PlatformOperationController(PlatformOperationService service, CurrentUserService currentUser) {
        this(service, currentUser, null);
    }

    @GetMapping("/trades")
    public ApiResponse<PageView<OperationTradeView>> trades(
            @RequestParam(required = false) String tradeNo,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) TradeStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime createdTo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        authorize();
        return ApiResponse.success(service.trades(
                tradeNo, userId, status, createdFrom, createdTo, page, pageSize));
    }

    @GetMapping("/orders")
    public ApiResponse<PageView<OperationOrderView>> orders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) OrderStatus orderStatus,
            @RequestParam(required = false) OrderPaymentStatus paymentStatus,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        authorize();
        return ApiResponse.success(service.orders(
                orderNo, shopId, userId, orderStatus, paymentStatus, page, pageSize));
    }

    @GetMapping("/payments")
    public ApiResponse<PageView<OperationPaymentView>> payments(
            @RequestParam(required = false) String paymentNo,
            @RequestParam(required = false) String tradeNo,
            @RequestParam(required = false) PaymentOrderStatus status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        authorize();
        return ApiResponse.success(service.payments(paymentNo, tradeNo, status, page, pageSize));
    }

    @GetMapping("/after-sales")
    public ApiResponse<PageView<OperationAfterSaleView>> afterSales(
            @RequestParam(required = false) String afterSaleNo,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) AfterSaleStatus status,
            @RequestParam(required = false) RefundStatus refundStatus,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        authorize();
        return ApiResponse.success(service.afterSales(
                afterSaleNo, shopId, userId, status, refundStatus, page, pageSize));
    }

    @GetMapping("/business/{businessType}/{businessNo}")
    public ApiResponse<BusinessTraceView> trace(@PathVariable String businessType,
                                                @PathVariable String businessNo) {
        authorize();
        return ApiResponse.success(service.trace(businessType, businessNo));
    }

    @GetMapping("/merchant-wallets")
    public ApiResponse<PageView<MerchantWalletView>> merchantWallets(@RequestParam(required = false) Long shopId,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        authorize(); return ApiResponse.success(merchantWallet.platformWallets(shopId, page, pageSize));
    }

    @GetMapping("/merchant-wallet-transactions")
    public ApiResponse<PageView<MerchantWalletTransactionView>> merchantWalletTransactions(@RequestParam(required = false) Long shopId,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        authorize(); return ApiResponse.success(merchantWallet.platformTransactions(shopId, page, pageSize));
    }

    @GetMapping("/settlements")
    public ApiResponse<PageView<ShopSettlementView>> settlements(@RequestParam(required = false) Long shopId,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        authorize(); return ApiResponse.success(merchantWallet.platformSettlements(shopId, page, pageSize));
    }

    @GetMapping("/withdrawals")
    public ApiResponse<PageView<MerchantWithdrawalView>> withdrawals(@RequestParam(required = false) Long shopId,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize) {
        authorize(); return ApiResponse.success(merchantWallet.platformWithdrawals(shopId, page, pageSize));
    }

    /** 每个入口显式执行同一权限检查，便于独立测试，也与现有平台接口保持一致。 */
    private void authorize() {
        currentUser.requirePermission(READ_PERMISSION);
    }
}
