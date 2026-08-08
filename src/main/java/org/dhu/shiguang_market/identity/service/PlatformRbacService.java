package org.dhu.shiguang_market.identity.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.util.Formatters;
import org.dhu.shiguang_market.identity.dto.PlatformRbacDtos.AssignPermissionsRequest;
import org.dhu.shiguang_market.identity.dto.PlatformRbacDtos.CreateRoleRequest;
import org.dhu.shiguang_market.identity.dto.PlatformRbacDtos.PermissionView;
import org.dhu.shiguang_market.identity.dto.PlatformRbacDtos.RoleDetailView;
import org.dhu.shiguang_market.identity.dto.PlatformRbacDtos.StatusRequest;
import org.dhu.shiguang_market.identity.dto.PlatformRbacDtos.UpdateRoleRequest;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.RoleView;
import org.dhu.shiguang_market.identity.mapper.SysPermissionMapper;
import org.dhu.shiguang_market.identity.mapper.SysRoleMapper;
import org.dhu.shiguang_market.identity.mapper.SysRolePermissionMapper;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.model.SysPermission;
import org.dhu.shiguang_market.identity.model.SysRole;
import org.dhu.shiguang_market.identity.model.SysRolePermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台角色与权限管理服务。 */
@Service
public class PlatformRbacService {
    private static final String MANAGE_PERMISSION = "platform:rbac:manage";
    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{2,63}$");
    private static final Set<String> CRITICAL_ROLES = Set.of("CUSTOMER", "SUPER_ADMIN", "SHOP_ADMIN");
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserMapper userMapper;
    private final CurrentUserService currentUser;

    public PlatformRbacService(SysRoleMapper roleMapper, SysPermissionMapper permissionMapper,
                               SysRolePermissionMapper rolePermissionMapper,
                               SysUserMapper userMapper,
                               CurrentUserService currentUser) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userMapper = userMapper;
        this.currentUser = currentUser;
    }

    /** 分页查询角色，关键词同时匹配角色代码和名称。 */
    @Transactional(readOnly = true)
    public PageView<RoleView> roles(ScopeType scopeType, ActiveStatus status, String keyword,
                                    long page, long pageSize) {
        authorize();
        checkPage(page, pageSize);
        Page<SysRole> result = roleMapper.selectRolePage(
                Page.of(page, pageSize), scopeType, status, text(keyword));
        return PageView.of(result, result.getRecords().stream().map(this::roleView).toList());
    }

    /** 查询角色及其当前权限列表。 */
    @Transactional(readOnly = true)
    public RoleDetailView roleDetail(long roleId) {
        authorize();
        return detailView(requireRole(roleId));
    }

    /** 分页查询只读权限字典。 */
    @Transactional(readOnly = true)
    public PageView<PermissionView> permissions(ScopeType scopeType, ActiveStatus status, String keyword,
                                                long page, long pageSize) {
        authorize();
        checkPage(page, pageSize);
        Page<SysPermission> result = permissionMapper.selectPermissionPage(
                Page.of(page, pageSize), scopeType, status, text(keyword));
        return PageView.of(result, result.getRecords().stream().map(this::permissionView).toList());
    }

    /** 创建角色并一次性写入同作用域权限。 */
    @Transactional
    public RoleDetailView createRole(CreateRoleRequest request) {
        authorize();
        validateCreateRequest(request);
        String roleCode = request.roleCode().trim();
        if (roleMapper.existsByRoleCode(roleCode)) {
            throw BusinessException.conflict("ROLE_CODE_ALREADY_EXISTS", "角色代码已存在");
        }
        List<Long> permissionIds = parseIds(request.permissionIds(), "权限");
        List<SysPermission> permissions = requirePermissions(permissionIds);
        checkPermissionScope(request.scopeType(), permissions);

        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
        role.setRoleName(request.roleName().trim());
        role.setScopeType(request.scopeType());
        role.setDescription(text(request.description()));
        role.setStatus(ActiveStatus.ACTIVE);
        roleMapper.insert(role);
        insertPermissionRelations(role.getId(), role.getScopeType(), permissionIds);
        SysRole saved = roleMapper.selectById(role.getId());
        return detailView(saved == null ? role : saved);
    }

    /** roleCode 和 scopeType 创建后保持不变，只修改展示名称和说明。 */
    @Transactional
    public RoleDetailView updateRole(long roleId, UpdateRoleRequest request) {
        authorize();
        if (request == null || text(request.roleName()) == null || request.roleName().trim().length() > 64) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "roleName 长度必须为 1..64");
        }
        if (request.description() != null && request.description().length() > 255) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "description 长度不能超过 255");
        }
        SysRole role = requireRole(roleId);
        role.setRoleName(request.roleName().trim());
        role.setDescription(text(request.description()));
        roleMapper.updateById(role);
        return detailView(role);
    }

    /** 修改角色状态；关键角色及最后管理员依赖的角色不能停用。 */
    @Transactional
    public RoleDetailView changeRoleStatus(long roleId, StatusRequest request) {
        authorize();
        if (request == null || request.targetStatus() == null) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "targetStatus 不能为空");
        }
        SysRole role = requireLockedRole(roleId);
        if (role.getStatus() == request.targetStatus()) return detailView(role);
        if (request.targetStatus() == ActiveStatus.DISABLED) {
            if (CRITICAL_ROLES.contains(role.getRoleCode())) {
                throw BusinessException.conflict(
                        "CANNOT_DISABLE_CRITICAL_ROLE", "关键角色不允许停用");
            }
            protectLastRbacAdmin(role);
        }
        role.setStatus(request.targetStatus());
        roleMapper.updateById(role);
        return detailView(role);
    }

    /** 全量替换角色权限；空数组表示移除该角色的全部权限。 */
    @Transactional
    public RoleDetailView assignPermissions(long roleId, AssignPermissionsRequest request) {
        authorize();
        if (request == null || request.permissionIds() == null) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "permissionIds 不能为空");
        }
        SysRole role = requireLockedRole(roleId);
        List<Long> permissionIds = parseIds(request.permissionIds(), "权限");
        List<SysPermission> permissions = requirePermissions(permissionIds);
        checkPermissionScope(role.getScopeType(), permissions);

        // 如果当前角色承担最后管理员权限，替换后必须继续包含有效的管理权限。
        boolean keepsManagePermission = permissions.stream().anyMatch(permission ->
                permission.getStatus() == ActiveStatus.ACTIVE
                        && MANAGE_PERMISSION.equals(permission.getPermissionCode()));
        if (role.getScopeType() == ScopeType.PLATFORM && role.getStatus() == ActiveStatus.ACTIVE
                && roleMapper.roleHasActivePermission(roleId, MANAGE_PERMISSION)
                && !keepsManagePermission) {
            protectLastRbacAdmin(role);
        }

        rolePermissionMapper.deleteByRoleId(roleId);
        insertPermissionRelations(roleId, role.getScopeType(), permissionIds);
        return detailView(role);
    }

    private void validateCreateRequest(CreateRoleRequest request) {
        if (request == null || text(request.roleCode()) == null
                || !ROLE_CODE_PATTERN.matcher(request.roleCode().trim()).matches()) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "roleCode 格式错误");
        }
        if (text(request.roleName()) == null || request.roleName().trim().length() > 64
                || request.scopeType() == null || request.permissionIds() == null) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "角色创建参数不完整");
        }
        if (request.description() != null && request.description().length() > 255) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "description 长度不能超过 255");
        }
    }

    private List<SysPermission> requirePermissions(List<Long> permissionIds) {
        if (permissionIds.isEmpty()) return List.of();
        List<SysPermission> permissions = permissionMapper.selectPermissionsByIds(permissionIds);
        if (permissions.size() != permissionIds.size()) {
            throw BusinessException.notFound("RESOURCE_NOT_FOUND", "权限不存在");
        }
        return permissions;
    }

    private void checkPermissionScope(ScopeType roleScope, List<SysPermission> permissions) {
        if (permissions.stream().anyMatch(permission -> permission.getScopeType() != roleScope)) {
            throw BusinessException.badRequest(
                    "PERMISSION_SCOPE_MISMATCH", "角色与权限作用域必须一致");
        }
    }

    private void insertPermissionRelations(long roleId, ScopeType scopeType, List<Long> permissionIds) {
        for (Long permissionId : permissionIds) {
            SysRolePermission relation = new SysRolePermission();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            relation.setScopeType(scopeType);
            rolePermissionMapper.insert(relation);
        }
    }

    private void protectLastRbacAdmin(SysRole role) {
        if (role.getScopeType() == ScopeType.PLATFORM
                && roleMapper.roleHasActivePermission(role.getId(), MANAGE_PERMISSION)
                && userMapper.countActiveUsersWithPermissionExcludingRole(
                        MANAGE_PERMISSION, role.getId()) == 0) {
            throw BusinessException.conflict(
                    "LAST_RBAC_ADMIN_REQUIRED", "系统必须保留至少一名可用的 RBAC 管理员");
        }
    }

    private RoleDetailView detailView(SysRole role) {
        return new RoleDetailView(id(role.getId()), role.getRoleCode(), role.getRoleName(),
                role.getScopeType(), role.getDescription(), role.getStatus(),
                time(role.getCreatedAt()), time(role.getUpdatedAt()),
                permissionMapper.selectByRoleId(role.getId()).stream().map(this::permissionView).toList());
    }

    private RoleView roleView(SysRole role) {
        return new RoleView(id(role.getId()), role.getRoleCode(), role.getRoleName(),
                role.getScopeType(), role.getDescription(), role.getStatus(),
                time(role.getCreatedAt()), time(role.getUpdatedAt()));
    }

    private PermissionView permissionView(SysPermission permission) {
        return new PermissionView(id(permission.getId()), permission.getPermissionCode(),
                permission.getPermissionName(), permission.getScopeType(), permission.getResource(),
                permission.getHttpMethod(), permission.getStatus());
    }

    private SysRole requireRole(long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw BusinessException.notFound("ROLE_NOT_FOUND", "角色不存在");
        }
        return role;
    }

    private SysRole requireLockedRole(long roleId) {
        SysRole role = roleMapper.selectRoleForUpdate(roleId);
        if (role == null) {
            throw BusinessException.notFound("ROLE_NOT_FOUND", "角色不存在");
        }
        return role;
    }

    private List<Long> parseIds(List<String> rawIds, String name) {
        Set<Long> values = new LinkedHashSet<>();
        for (String raw : rawIds) {
            try {
                long value = Long.parseLong(raw);
                if (value <= 0) throw new NumberFormatException();
                values.add(value);
            } catch (NumberFormatException ex) {
                throw BusinessException.badRequest("VALIDATION_FAILED", name + " ID 格式错误");
            }
        }
        return List.copyOf(values);
    }

    private void authorize() {
        currentUser.requirePermission(MANAGE_PERMISSION);
    }

    private void checkPage(long page, long pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
        }
    }

    private String text(String value) {
        return Formatters.trimToNull(value);
    }
}
