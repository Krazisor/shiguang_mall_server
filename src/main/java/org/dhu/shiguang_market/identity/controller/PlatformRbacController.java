package org.dhu.shiguang_market.identity.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;
import org.dhu.shiguang_market.identity.dto.PlatformRbacDtos.AssignPermissionsRequest;
import org.dhu.shiguang_market.identity.dto.PlatformRbacDtos.CreateRoleRequest;
import org.dhu.shiguang_market.identity.dto.PlatformRbacDtos.PermissionView;
import org.dhu.shiguang_market.identity.dto.PlatformRbacDtos.RoleDetailView;
import org.dhu.shiguang_market.identity.dto.PlatformRbacDtos.StatusRequest;
import org.dhu.shiguang_market.identity.dto.PlatformRbacDtos.UpdateRoleRequest;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.RoleView;
import org.dhu.shiguang_market.identity.service.PlatformRbacService;
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

/** phase-2 平台角色与权限管理接口。 */
@RestController
@RequestMapping("/api/platform/rbac")
public class PlatformRbacController {
    private final PlatformRbacService service;

    public PlatformRbacController(PlatformRbacService service) {
        this.service = service;
    }

    /** 分页查询角色。 */
    @GetMapping("/roles")
    public ApiResponse<PageView<RoleView>> roles(
            @RequestParam(required = false) ScopeType scopeType,
            @RequestParam(required = false) ActiveStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(service.roles(scopeType, status, keyword, page, pageSize));
    }

    /** 查询角色详情及其权限列表。 */
    @GetMapping("/roles/{roleId}")
    public ApiResponse<RoleDetailView> roleDetail(@PathVariable long roleId) {
        return ApiResponse.success(service.roleDetail(roleId));
    }

    /** 创建角色，成功时遵循项目规范返回 201。 */
    @PostMapping("/roles")
    public ResponseEntity<ApiResponse<RoleDetailView>> createRole(
            @Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.createRole(request)));
    }

    /** 修改角色名称和说明。 */
    @PutMapping("/roles/{roleId}")
    public ApiResponse<RoleDetailView> updateRole(
            @PathVariable long roleId, @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResponse.success(service.updateRole(roleId, request));
    }

    /** 启用或停用角色。 */
    @PostMapping("/roles/{roleId}/status")
    public ApiResponse<RoleDetailView> changeRoleStatus(
            @PathVariable long roleId, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.success(service.changeRoleStatus(roleId, request));
    }

    /** 全量替换角色权限。 */
    @PutMapping("/roles/{roleId}/permissions")
    public ApiResponse<RoleDetailView> assignPermissions(
            @PathVariable long roleId, @Valid @RequestBody AssignPermissionsRequest request) {
        return ApiResponse.success(service.assignPermissions(roleId, request));
    }

    /** 分页查询只读权限字典。 */
    @GetMapping("/permissions")
    public ApiResponse<PageView<PermissionView>> permissions(
            @RequestParam(required = false) ScopeType scopeType,
            @RequestParam(required = false) ActiveStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(service.permissions(scopeType, status, keyword, page, pageSize));
    }
}
