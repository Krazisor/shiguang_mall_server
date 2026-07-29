package org.dhu.shiguang_market.product.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.EnabledStatus;
import org.dhu.shiguang_market.product.dto.ProductDtos.BrandView;
import org.dhu.shiguang_market.product.dto.ProductDtos.CategoryAttributeView;
import org.dhu.shiguang_market.product.service.PlatformCatalogService;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.BrandRequest;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.CategoryAttributeRequest;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.CategoryUpsertRequest;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.PlatformCategoryNode;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.PlatformCategoryView;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.StatusRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/catalog")
public class PlatformCatalogController {
    private final PlatformCatalogService service;

    public PlatformCatalogController(PlatformCatalogService service) {
        this.service = service;
    }

    @GetMapping("/categories/tree")
    public ApiResponse<List<PlatformCategoryNode>> categories(@RequestParam(required = false) EnabledStatus status) {
        return ApiResponse.success(service.categoryTree(status));
    }

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<PlatformCategoryView>> createCategory(
            @Valid @RequestBody CategoryUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createCategory(request)));
    }

    @PutMapping("/categories/{categoryId}")
    public ApiResponse<PlatformCategoryView> updateCategory(
            @PathVariable long categoryId, @Valid @RequestBody CategoryUpsertRequest request) {
        return ApiResponse.success(service.updateCategory(categoryId, request));
    }

    @PostMapping("/categories/{categoryId}/status")
    public ApiResponse<PlatformCategoryView> categoryStatus(
            @PathVariable long categoryId, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.success(service.categoryStatus(categoryId, request));
    }

    @GetMapping("/categories/{categoryId}/attributes")
    public ApiResponse<List<CategoryAttributeView>> attributes(@PathVariable long categoryId) {
        return ApiResponse.success(service.attributes(categoryId));
    }

    @PostMapping("/categories/{categoryId}/attributes")
    public ResponseEntity<ApiResponse<CategoryAttributeView>> createAttribute(
            @PathVariable long categoryId, @Valid @RequestBody CategoryAttributeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.createAttribute(categoryId, request)));
    }

    @PutMapping("/categories/{categoryId}/attributes/{attributeId}")
    public ApiResponse<CategoryAttributeView> updateAttribute(
            @PathVariable long categoryId, @PathVariable long attributeId,
            @Valid @RequestBody CategoryAttributeRequest request) {
        return ApiResponse.success(service.updateAttribute(categoryId, attributeId, request));
    }

    @PostMapping("/categories/{categoryId}/attributes/{attributeId}/status")
    public ApiResponse<CategoryAttributeView> attributeStatus(
            @PathVariable long categoryId, @PathVariable long attributeId,
            @Valid @RequestBody StatusRequest request) {
        return ApiResponse.success(service.attributeStatus(categoryId, attributeId, request));
    }

    @GetMapping("/brands")
    public ApiResponse<PageView<BrandView>> brands(
            @RequestParam(required = false) EnabledStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(service.brands(status, keyword, page, pageSize));
    }

    @PostMapping("/brands")
    public ResponseEntity<ApiResponse<BrandView>> createBrand(@Valid @RequestBody BrandRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createBrand(request)));
    }

    @PutMapping("/brands/{brandId}")
    public ApiResponse<BrandView> updateBrand(@PathVariable long brandId,
                                              @Valid @RequestBody BrandRequest request) {
        return ApiResponse.success(service.updateBrand(brandId, request));
    }

    @PostMapping("/brands/{brandId}/status")
    public ApiResponse<BrandView> brandStatus(@PathVariable long brandId,
                                              @Valid @RequestBody StatusRequest request) {
        return ApiResponse.success(service.brandStatus(brandId, request));
    }
}
