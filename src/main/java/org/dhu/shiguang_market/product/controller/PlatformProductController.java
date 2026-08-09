package org.dhu.shiguang_market.product.controller;

import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.ProductStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.PlatformProductDetailView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.PlatformProductSummaryView;
import org.dhu.shiguang_market.product.dto.ShopProductDtos.ProductStatusHistoryView;
import org.dhu.shiguang_market.product.service.PlatformProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/products")
public class PlatformProductController {
    private final PlatformProductService service;

    public PlatformProductController(PlatformProductService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageView<PlatformProductSummaryView>> list(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) ShopStatus shopStatus,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(defaultValue = "updatedAt,desc") String sort) {
        return ApiResponse.success(service.list(status, shopId, shopStatus, categoryId, brandId, keyword, page, pageSize, sort));
    }

    @GetMapping("/{spuId}")
    public ApiResponse<PlatformProductDetailView> detail(@PathVariable long spuId) {
        return ApiResponse.success(service.detail(spuId));
    }

    @GetMapping("/{spuId}/history")
    public ApiResponse<PageView<ProductStatusHistoryView>> history(
            @PathVariable long spuId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(service.history(spuId, page, pageSize));
    }
}
