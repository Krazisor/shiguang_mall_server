package org.dhu.shiguang_market.identity.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.AssignPlatformRolesRequest;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.ChangeUserStatusRequest;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.PlatformUserDetailView;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.PlatformUserView;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.ReasonRequest;
import org.dhu.shiguang_market.identity.service.PlatformUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** phase-2 平台用户查询、状态、角色和会话管理接口。 */
@RestController
@RequestMapping("/api/platform/rbac/users")
public class PlatformUserController {
    private final PlatformUserService service;

    public PlatformUserController(PlatformUserService service) {
        this.service = service;
    }

    /** 按关键词、状态和平台角色分页查询用户。 */
    @GetMapping
    public ApiResponse<PageView<PlatformUserView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String roleCode,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(service.list(keyword, status, roleCode, page, pageSize));
    }

    /** 查询用户详情，手机号和邮箱由服务层统一脱敏。 */
    @GetMapping("/{userId}")
    public ApiResponse<PlatformUserDetailView> detail(@PathVariable long userId) {
        return ApiResponse.success(service.detail(userId));
    }

    /** 修改用户状态；停用或锁定时服务层会同步清理登录会话。 */
    @PostMapping("/{userId}/status")
    public ApiResponse<PlatformUserDetailView> changeStatus(
            @PathVariable long userId, @Valid @RequestBody ChangeUserStatusRequest request) {
        return ApiResponse.success(service.changeStatus(userId, request));
    }

    /** 使用传入的平台角色 ID 全量替换用户现有的平台角色。 */
    @PutMapping("/{userId}/roles")
    public ApiResponse<PlatformUserDetailView> assignRoles(
            @PathVariable long userId, @Valid @RequestBody AssignPlatformRolesRequest request) {
        return ApiResponse.success(service.assignRoles(userId, request));
    }

    /** 强制目标用户的全部登录终端下线。 */
    @PostMapping("/{userId}/kickout")
    public ApiResponse<Void> kickout(
            @PathVariable long userId, @Valid @RequestBody ReasonRequest request) {
        service.kickout(userId, request);
        return ApiResponse.success(null);
    }
}
