package org.dhu.shiguang_market.shop.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.shop.dto.ShopMemberDtos.AddShopMemberRequest;
import org.dhu.shiguang_market.shop.dto.ShopMemberDtos.ChangeShopMemberRoleRequest;
import org.dhu.shiguang_market.shop.dto.ShopMemberDtos.ShopMemberView;
import org.dhu.shiguang_market.shop.dto.ShopMemberDtos.StatusRequest;
import org.dhu.shiguang_market.shop.service.ShopMemberService;
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

/** phase-2 店铺成员管理接口。 */
@RestController
@RequestMapping("/api/shops/{shopId}/members")
public class ShopMemberController {
    private final ShopMemberService service;

    public ShopMemberController(ShopMemberService service) {
        this.service = service;
    }

    /** 分页查询本店成员。 */
    @GetMapping
    public ApiResponse<PageView<ShopMemberView>> list(
            @PathVariable long shopId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) ActiveStatus status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(service.list(shopId, keyword, roleId, status, page, pageSize));
    }

    /** 新增店铺成员，成功时返回 201。 */
    @PostMapping
    public ResponseEntity<ApiResponse<ShopMemberView>> add(
            @PathVariable long shopId, @Valid @RequestBody AddShopMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.add(shopId, request)));
    }

    /** 修改成员的店铺角色。 */
    @PutMapping("/{userId}/role")
    public ApiResponse<ShopMemberView> changeRole(
            @PathVariable long shopId, @PathVariable long userId,
            @Valid @RequestBody ChangeShopMemberRoleRequest request) {
        return ApiResponse.success(service.changeRole(shopId, userId, request));
    }

    /** 启用或停用店铺成员。 */
    @PostMapping("/{userId}/status")
    public ApiResponse<ShopMemberView> changeStatus(
            @PathVariable long shopId, @PathVariable long userId,
            @Valid @RequestBody StatusRequest request) {
        return ApiResponse.success(service.changeStatus(shopId, userId, request));
    }
}
