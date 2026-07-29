package org.dhu.shiguang_market.product.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.CreateProductRequest;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.CreateSkuRequest;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ReasonRequest;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ShopProductDetailView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ShopProductSummaryView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ShopSkuView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.UpdateProductContentRequest;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.UpdateSkuRequest;
import org.dhu.shiguang_market.product.service.ShopProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shops/{shopId}/products")
public class ShopProductController {
    private final ShopProductService service;

    public ShopProductController(ShopProductService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageView<ShopProductSummaryView>> list(
            @PathVariable long shopId, @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(defaultValue = "updatedAt,desc") String sort) {
        return ApiResponse.success(service.list(shopId, status, keyword, categoryId, page, pageSize, sort));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ShopProductDetailView>> create(
            @PathVariable long shopId, @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(shopId, request)));
    }

    @GetMapping("/{spuId}")
    public ApiResponse<ShopProductDetailView> detail(@PathVariable long shopId, @PathVariable long spuId) {
        return ApiResponse.success(service.detail(shopId, spuId));
    }

    @PutMapping("/{spuId}/content")
    public ApiResponse<ShopProductDetailView> updateContent(
            @PathVariable long shopId, @PathVariable long spuId,
            @Valid @RequestBody UpdateProductContentRequest request) {
        return ApiResponse.success(service.updateContent(shopId, spuId, request));
    }

    @PostMapping("/{spuId}/skus")
    public ResponseEntity<ApiResponse<ShopProductDetailView>> createSku(
            @PathVariable long shopId, @PathVariable long spuId,
            @Valid @RequestBody CreateSkuRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createSku(shopId, spuId, request)));
    }

    @PatchMapping("/{spuId}/skus/{skuId}")
    public ApiResponse<ShopSkuView> updateSku(
            @PathVariable long shopId, @PathVariable long spuId, @PathVariable long skuId,
            @Valid @RequestBody UpdateSkuRequest request) {
        return ApiResponse.success(service.updateSku(shopId, spuId, skuId, request));
    }

    @PostMapping("/{spuId}/submit-review")
    public ApiResponse<ShopProductDetailView> submitReview(@PathVariable long shopId, @PathVariable long spuId) {
        return ApiResponse.success(service.submitReview(shopId, spuId));
    }

    @PostMapping("/{spuId}/put-on-shelf")
    public ApiResponse<ShopProductDetailView> putOnShelf(@PathVariable long shopId, @PathVariable long spuId) {
        return ApiResponse.success(service.putOnShelf(shopId, spuId));
    }

    @PostMapping("/{spuId}/take-off-shelf")
    public ApiResponse<ShopProductDetailView> takeOffShelf(
            @PathVariable long shopId, @PathVariable long spuId,
            @RequestBody(required = false) ReasonRequest request) {
        return ApiResponse.success(service.takeOffShelf(shopId, spuId, request));
    }
}
