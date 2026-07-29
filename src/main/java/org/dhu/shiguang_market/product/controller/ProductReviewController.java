package org.dhu.shiguang_market.product.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ProductReviewDetailView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ProductReviewSummaryView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ReviewDecisionRequest;
import org.dhu.shiguang_market.product.service.ProductReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/products/reviews")
public class ProductReviewController {
    private final ProductReviewService service;

    public ProductReviewController(ProductReviewService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageView<ProductReviewSummaryView>> list(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(service.list(shopId, categoryId, keyword, page, pageSize));
    }

    @GetMapping("/{spuId}")
    public ApiResponse<ProductReviewDetailView> detail(@PathVariable long spuId) {
        return ApiResponse.success(service.detail(spuId));
    }

    @PostMapping("/{spuId}/approve")
    public ApiResponse<ProductReviewDetailView> approve(
            @PathVariable long spuId, @Valid @RequestBody ReviewDecisionRequest request) {
        return ApiResponse.success(service.approve(spuId, request));
    }

    @PostMapping("/{spuId}/reject")
    public ApiResponse<ProductReviewDetailView> reject(
            @PathVariable long spuId, @Valid @RequestBody ReviewDecisionRequest request) {
        return ApiResponse.success(service.reject(spuId, request));
    }
}
