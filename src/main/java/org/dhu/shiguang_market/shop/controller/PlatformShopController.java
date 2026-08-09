package org.dhu.shiguang_market.shop.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.ChangeShopStatusRequest;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.CreateShopRequest;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.PlatformShopView;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.UpdateShopRequest;
import org.dhu.shiguang_market.shop.dto.ShopMemberDtos.AddShopMemberRequest;
import org.dhu.shiguang_market.shop.dto.ShopMemberDtos.ChangeShopMemberRoleRequest;
import org.dhu.shiguang_market.shop.dto.ShopMemberDtos.ShopMemberView;
import org.dhu.shiguang_market.shop.dto.ShopMemberDtos.StatusRequest;
import org.dhu.shiguang_market.shop.service.ShopMemberService;
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
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/api/platform/shops")
public class PlatformShopController {
    private final PlatformShopService service;
    private final ShopMemberService memberService;

    public PlatformShopController(PlatformShopService service, ShopMemberService memberService) {
        this.service = service;
        this.memberService = memberService;
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

    @GetMapping("/{shopId}/members")
    public ApiResponse<PageView<ShopMemberView>> members(
            @PathVariable long shopId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) ActiveStatus status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(memberService.listForPlatform(shopId, keyword, roleId, status, page, pageSize));
    }

    @PostMapping("/{shopId}/members")
    public ResponseEntity<ApiResponse<ShopMemberView>> addMember(
            @PathVariable long shopId, @Valid @RequestBody AddShopMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(memberService.addForPlatform(shopId, request)));
    }

    @PutMapping("/{shopId}/members/{userId}/role")
    public ApiResponse<ShopMemberView> changeMemberRole(
            @PathVariable long shopId, @PathVariable long userId,
            @Valid @RequestBody ChangeShopMemberRoleRequest request) {
        return ApiResponse.success(memberService.changeRoleForPlatform(shopId, userId, request));
    }

    @PostMapping("/{shopId}/members/{userId}/status")
    public ApiResponse<ShopMemberView> changeMemberStatus(
            @PathVariable long shopId, @PathVariable long userId,
            @Valid @RequestBody StatusRequest request) {
        return ApiResponse.success(memberService.changeStatusForPlatform(shopId, userId, request));
    }

    @DeleteMapping("/{shopId}/members/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable long shopId, @PathVariable long userId) {
        memberService.removeForPlatform(shopId, userId);
        return ApiResponse.success(null);
    }
}
