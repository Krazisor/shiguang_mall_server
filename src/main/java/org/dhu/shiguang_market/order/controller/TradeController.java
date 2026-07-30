package org.dhu.shiguang_market.order.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.cart.dto.CartDtos.CreateTradeRequest;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.order.dto.OrderDtos.CancelTradeRequest;
import org.dhu.shiguang_market.order.dto.OrderDtos.TradeDetailView;
import org.dhu.shiguang_market.order.service.TradeService;
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
@RequestMapping("/api/trades")
public class TradeController {
    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TradeDetailView>> create(
            @Valid @RequestBody CreateTradeRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(tradeService.create(request, idempotencyKey)));
    }

    @GetMapping("/{tradeId}")
    public ApiResponse<TradeDetailView> detail(@PathVariable long tradeId) {
        return ApiResponse.success(tradeService.detail(tradeId));
    }

    @PostMapping("/{tradeId}/cancel")
    public ApiResponse<TradeDetailView> cancel(@PathVariable long tradeId,
                                                @Valid @RequestBody CancelTradeRequest request) {
        return ApiResponse.success(tradeService.cancel(tradeId, request));
    }
}
