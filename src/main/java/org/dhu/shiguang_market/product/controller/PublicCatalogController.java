package org.dhu.shiguang_market.product.controller;

import java.math.BigDecimal;
import java.util.List;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.product.dto.ProductDtos.BrandView;
import org.dhu.shiguang_market.product.dto.ProductDtos.CategoryAttributeView;
import org.dhu.shiguang_market.product.dto.ProductDtos.CategoryNode;
import org.dhu.shiguang_market.product.dto.ProductDtos.ProductCardView;
import org.dhu.shiguang_market.product.dto.ProductDtos.ProductDetailView;
import org.dhu.shiguang_market.product.dto.ProductDtos.PublicShopView;
import org.dhu.shiguang_market.product.service.PublicCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PublicCatalogController {
    private final PublicCatalogService catalogService;

    public PublicCatalogController(PublicCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories/tree")
    public ApiResponse<List<CategoryNode>> categories() {
        return ApiResponse.success(catalogService.categoryTree());
    }

    @GetMapping("/categories/{categoryId}/attributes")
    public ApiResponse<List<CategoryAttributeView>> attributes(@PathVariable long categoryId) {
        return ApiResponse.success(catalogService.attributes(categoryId, true));
    }

    @GetMapping("/brands")
    public ApiResponse<PageView<BrandView>> brands(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(defaultValue = "brandName,asc") String sort) {
        return ApiResponse.success(catalogService.brands(keyword, null, page, pageSize, sort, true));
    }

    @GetMapping("/shops/{shopId}")
    public ApiResponse<PublicShopView> shop(@PathVariable long shopId) {
        return ApiResponse.success(catalogService.publicShop(shopId));
    }

    @GetMapping("/products")
    public ApiResponse<PageView<ProductCardView>> products(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ApiResponse.success(catalogService.products(keyword, categoryId, brandId, shopId,
                minPrice, maxPrice, inStock, page, pageSize, sort));
    }

    @GetMapping("/products/{spuId}")
    public ApiResponse<ProductDetailView> product(@PathVariable long spuId) {
        return ApiResponse.success(catalogService.product(spuId));
    }
}
