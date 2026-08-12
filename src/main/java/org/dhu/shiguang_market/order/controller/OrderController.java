package org.dhu.shiguang_market.order.controller;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderPaymentStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.OrderStatus;
import org.dhu.shiguang_market.order.dto.OrderDtos.OrderDetailView;
import org.dhu.shiguang_market.order.dto.OrderDtos.OrderSummaryView;
import org.dhu.shiguang_market.order.dto.OrderDtos.ShipOrderRequest;
import org.dhu.shiguang_market.order.dto.OrderDtos.ShopOrderSummaryView;
import org.dhu.shiguang_market.order.dto.OrderDtos.ShopOrderDetailView;
import org.dhu.shiguang_market.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders")
    public ApiResponse<PageView<OrderSummaryView>> buyerOrders(
            @RequestParam(required = false) OrderStatus orderStatus,
            @RequestParam(required = false) OrderPaymentStatus paymentStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) LocalDateTime createdFrom,
            @RequestParam(required = false) LocalDateTime createdTo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(orderService.buyerOrders(orderStatus, paymentStatus, keyword,
                createdFrom, createdTo, page, pageSize));
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<OrderDetailView> buyerDetail(@PathVariable long orderId) {
        return ApiResponse.success(orderService.buyerDetail(orderId));
    }

    @PostMapping("/orders/{orderId}/complete")
    public ApiResponse<OrderDetailView> complete(@PathVariable long orderId) {
        return ApiResponse.success(orderService.complete(orderId));
    }

    @GetMapping("/shops/{shopId}/orders")
    public ApiResponse<PageView<ShopOrderSummaryView>> shopOrders(
            @PathVariable long shopId,
            @RequestParam(required = false) OrderStatus orderStatus,
            @RequestParam(required = false) OrderPaymentStatus paymentStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) LocalDateTime createdFrom,
            @RequestParam(required = false) LocalDateTime createdTo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(orderService.shopOrders(shopId, orderStatus, paymentStatus, keyword,
                createdFrom, createdTo, page, pageSize));
    }

    @GetMapping("/shops/{shopId}/orders/{orderId}")
    public ApiResponse<ShopOrderDetailView> shopDetail(@PathVariable long shopId, @PathVariable long orderId) {
        return ApiResponse.success(orderService.shopDetail(shopId, orderId));
    }

    @PostMapping("/shops/{shopId}/orders/{orderId}/ship")
    public ApiResponse<ShopOrderDetailView> ship(@PathVariable long shopId, @PathVariable long orderId,
                                              @Valid @RequestBody ShipOrderRequest request) {
        return ApiResponse.success(orderService.ship(shopId, orderId, request));
    }
}
