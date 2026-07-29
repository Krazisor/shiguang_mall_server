package org.dhu.shiguang_market.shop.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.ChangeShopStatusRequest;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.CreateShopRequest;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.PlatformShopView;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.UpdateShopRequest;
import org.dhu.shiguang_market.shop.service.PlatformShopService;
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
@RequestMapping("/api/platform/shops")
public class PlatformShopController {
    private final PlatformShopService service;

    public PlatformShopController(PlatformShopService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageView<PlatformShopView>> list(
            @RequestParam(required = false) ShopStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ApiResponse.success(service.list(status, keyword, page, pageSize, sort));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PlatformShopView>> create(@Valid @RequestBody CreateShopRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(request)));
    }

    @GetMapping("/{shopId}")
    public ApiResponse<PlatformShopView> detail(@PathVariable long shopId) {
        return ApiResponse.success(service.detail(shopId));
    }

    @PutMapping("/{shopId}")
    public ApiResponse<PlatformShopView> update(@PathVariable long shopId,
                                                @Valid @RequestBody UpdateShopRequest request) {
        return ApiResponse.success(service.update(shopId, request));
    }

    @PostMapping("/{shopId}/status")
    public ApiResponse<PlatformShopView> status(@PathVariable long shopId,
                                                @Valid @RequestBody ChangeShopStatusRequest request) {
        return ApiResponse.success(service.changeStatus(shopId, request));
    }
}
