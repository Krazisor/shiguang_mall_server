package org.dhu.shiguang_market.cart.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.cart.dto.CartDtos.AddCartItemRequest;
import org.dhu.shiguang_market.cart.dto.CartDtos.CartItemView;
import org.dhu.shiguang_market.cart.dto.CartDtos.CartView;
import org.dhu.shiguang_market.cart.dto.CartDtos.CheckoutPreviewRequest;
import org.dhu.shiguang_market.cart.dto.CartDtos.CheckoutPreviewView;
import org.dhu.shiguang_market.cart.dto.CartDtos.UpdateCartItemRequest;
import org.dhu.shiguang_market.cart.dto.CartDtos.UpdateCartSelectionRequest;
import org.dhu.shiguang_market.cart.service.CartService;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/cart")
    public ApiResponse<CartView> cart() {
        return ApiResponse.success(cartService.view());
    }

    @PostMapping("/cart/items")
    public ResponseEntity<ApiResponse<CartItemView>> add(@Valid @RequestBody AddCartItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(cartService.add(request)));
    }

    @PatchMapping("/cart/items/{cartItemId}")
    public ApiResponse<CartItemView> update(@PathVariable long cartItemId,
                                            @RequestBody UpdateCartItemRequest request) {
        return ApiResponse.success(cartService.update(cartItemId, request));
    }

    @DeleteMapping("/cart/items/{cartItemId}")
    public ApiResponse<Void> delete(@PathVariable long cartItemId) {
        cartService.delete(cartItemId);
        return ApiResponse.success(null);
    }

    @PutMapping("/cart/selection")
    public ApiResponse<CartView> selection(@Valid @RequestBody UpdateCartSelectionRequest request) {
        return ApiResponse.success(cartService.updateSelection(request));
    }

    @PostMapping("/trades/preview")
    public ApiResponse<CheckoutPreviewView> preview(@RequestBody CheckoutPreviewRequest request) {
        return ApiResponse.success(cartService.preview(request));
    }
}
