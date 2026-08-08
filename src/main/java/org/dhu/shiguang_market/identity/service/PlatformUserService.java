package org.dhu.shiguang_market.identity.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.util.Formatters;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.AssignPlatformRolesRequest;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.ChangeUserStatusRequest;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.PlatformUserDetailView;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.PlatformUserView;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.ReasonRequest;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.RoleView;
import org.dhu.shiguang_market.identity.mapper.SysRoleMapper;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.mapper.SysUserRoleMapper;
import org.dhu.shiguang_market.identity.model.SysRole;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.identity.model.SysUserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台用户查询和管理服务。 */
@Service
public class PlatformUserService {
    private static final Logger log = LoggerFactory.getLogger(PlatformUserService.class);
    private static final String MANAGE_PERMISSION = "platform:rbac:manage";
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final CurrentUserService currentUser;
    private final PlatformUserSessionService sessions;

    public PlatformUserService(SysUserMapper userMapper, SysRoleMapper roleMapper,
                               SysUserRoleMapper userRoleMapper, CurrentUserService currentUser,
                               PlatformUserSessionService sessions) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.currentUser = currentUser;
        this.sessions = sessions;
    }

    /** 按用户名、联系方式、状态和平台角色分页查询用户。 */
    @Transactional(readOnly = true)
    public PageView<PlatformUserView> list(String keyword, UserStatus status, String roleCode,
                                           long page, long pageSize) {
        authorize();
        checkPage(page, pageSize);
        Page<SysUser> result = userMapper.selectPlatformUserPage(
                Page.of(page, pageSize), text(keyword), status, text(roleCode));
        return PageView.of(result, result.getRecords().stream().map(this::summary).toList());
    }

    /** 查询单个用户详情，联系方式仍然只返回脱敏值。 */
    @Transactional(readOnly = true)
    public PlatformUserDetailView detail(long userId) {
        authorize();
        return detailView(requireUser(userId));
    }

    /** 修改用户状态；变为非 ACTIVE 后立即踢下线全部终端。 */
    @Transactional
    public PlatformUserDetailView changeStatus(long userId, ChangeUserStatusRequest request) {
        authorize();
        String reason = requireReason(request == null ? null : request.reason());
        if (request.targetStatus() == null) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "targetStatus 不能为空");
        }
        SysUser user = requireUser(userId, true);
        if (user.getStatus() == request.targetStatus()) return detailView(user);

        // 停用或锁定最后一个 RBAC 管理员会导致平台无法继续维护权限，因此必须阻止。
        if (user.getStatus() == UserStatus.ACTIVE && request.targetStatus() != UserStatus.ACTIVE
                && userMapper.userHasActivePermission(userId, MANAGE_PERMISSION)
                && userMapper.countActiveUsersWithPermission(MANAGE_PERMISSION) <= 1) {
            throw BusinessException.conflict("LAST_RBAC_ADMIN_REQUIRED", "系统必须保留至少一名可用的 RBAC 管理员");
        }

        user.setStatus(request.targetStatus());
        userMapper.updateById(user);
        if (request.targetStatus() != UserStatus.ACTIVE) {
            sessions.kickout(userId);
        }
        log.info("Platform user {} status changed to {}, reason={}", userId, request.targetStatus(), reason);
        return detailView(user);
    }

    /**
     * 全量替换用户的平台角色。空数组表示清空平台角色，但不会影响店铺作用域角色。
     */
    @Transactional
    public PlatformUserDetailView assignRoles(long userId, AssignPlatformRolesRequest request) {
        authorize();
        if (request == null || request.roleIds() == null) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "roleIds 不能为空");
        }
        SysUser user = requireUser(userId, true);
        List<Long> roleIds = parseRoleIds(request.roleIds());
        List<SysRole> roles = roleIds.isEmpty() ? List.of() : roleMapper.selectRolesByIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw BusinessException.notFound("ROLE_NOT_FOUND", "平台角色不存在");
        }
        if (roles.stream().anyMatch(role -> role.getScopeType() != ScopeType.PLATFORM)) {
            throw BusinessException.badRequest("ROLE_SCOPE_MISMATCH", "不能给平台用户分配店铺角色");
        }

        boolean currentlyManagesRbac = userMapper.userHasActivePermission(userId, MANAGE_PERMISSION);
        boolean requestedCanManageRbac = !roleIds.isEmpty()
                && roleMapper.countRolesWithPermission(roleIds, MANAGE_PERMISSION) > 0;
        if (currentlyManagesRbac && !requestedCanManageRbac) {
            if (currentUser.id() == userId) {
                throw BusinessException.conflict(
                        "CANNOT_REMOVE_OWN_ADMIN_ACCESS", "不能移除自己最后一个 RBAC 管理角色");
            }
            if (user.getStatus() == UserStatus.ACTIVE
                    && userMapper.countActiveUsersWithPermission(MANAGE_PERMISSION) <= 1) {
                throw BusinessException.conflict(
                        "LAST_RBAC_ADMIN_REQUIRED", "系统必须保留至少一名可用的 RBAC 管理员");
            }
        }

        userRoleMapper.deletePlatformRolesByUserId(userId);
        for (Long roleId : roleIds) {
            SysUserRole assignment = new SysUserRole();
            assignment.setUserId(userId);
            assignment.setRoleId(roleId);
            assignment.setRoleScope(ScopeType.PLATFORM);
            userRoleMapper.insert(assignment);
        }
        log.info("Platform user {} roles replaced with {}", userId, roleIds);
        return detailView(user);
    }

    /**
     * 强制指定用户的所有登录会话下线。先校验用户存在，避免对无效用户 ID 执行无意义操作。
     */
    public void kickout(long userId, ReasonRequest request) {
        authorize();
        String reason = requireReason(request == null ? null : request.reason());
        requireUser(userId);
        sessions.kickout(userId);
        log.info("Platform user {} was kicked out, reason={}", userId, reason);
    }

    private PlatformUserView summary(SysUser user) {
        return new PlatformUserView(id(user.getId()), user.getUsername(), user.getNickname(),
                maskPhone(user.getPhone()), maskEmail(user.getEmail()), user.getStatus(), roles(user.getId()),
                time(user.getLastLoginAt()), time(user.getCreatedAt()));
    }

    private PlatformUserDetailView detailView(SysUser user) {
        return new PlatformUserDetailView(id(user.getId()), user.getUsername(), user.getNickname(),
                maskPhone(user.getPhone()), maskEmail(user.getEmail()), user.getStatus(), roles(user.getId()),
                time(user.getLastLoginAt()), time(user.getCreatedAt()), user.getAvatarUrl(), time(user.getUpdatedAt()));
    }

    private List<RoleView> roles(long userId) {
        return roleMapper.selectPlatformRolesByUserId(userId).stream().map(this::roleView).toList();
    }

    private RoleView roleView(SysRole role) {
        return new RoleView(id(role.getId()), role.getRoleCode(), role.getRoleName(), role.getScopeType(),
                role.getDescription(), role.getStatus(), time(role.getCreatedAt()), time(role.getUpdatedAt()));
    }

    private SysUser requireUser(long userId) {
        return requireUser(userId, false);
    }

    private SysUser requireUser(long userId, boolean lock) {
        SysUser user;
        if (lock) {
            user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getId, userId).last("FOR UPDATE"));
        } else {
            user = userMapper.selectById(userId);
        }
        if (user == null) {
            throw BusinessException.notFound("RESOURCE_NOT_FOUND", "平台用户不存在");
        }
        return user;
    }

    private List<Long> parseRoleIds(List<String> rawRoleIds) {
        Set<Long> values = new LinkedHashSet<>();
        for (String raw : rawRoleIds) {
            try {
                long roleId = Long.parseLong(raw);
                if (roleId <= 0) throw new NumberFormatException();
                values.add(roleId);
            } catch (NumberFormatException ex) {
                throw BusinessException.badRequest("VALIDATION_FAILED", "角色 ID 格式错误");
            }
        }
        return List.copyOf(values);
    }

    private String requireReason(String reason) {
        String value = text(reason);
        if (value == null || value.length() > 500) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "reason 长度必须为 1..500");
        }
        return value;
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

    /** 手机号只保留前三位和后四位，短号码使用首尾字符展示。 */
    private String maskPhone(String phone) {
        String value = text(phone);
        if (value == null) return null;
        if (value.length() >= 7) {
            return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
        }
        if (value.length() <= 2) return "*".repeat(value.length());
        return value.charAt(0) + "***" + value.charAt(value.length() - 1);
    }

    /** 邮箱只保留用户名首字符和完整域名。 */
    private String maskEmail(String email) {
        String value = text(email);
        if (value == null) return null;
        int separator = value.indexOf('@');
        if (separator <= 0) return value.charAt(0) + "***";
        return value.charAt(0) + "***" + value.substring(separator);
    }
}
