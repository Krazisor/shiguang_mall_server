package org.dhu.shiguang_market.platformuser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.AssignPlatformRolesRequest;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.ChangeUserStatusRequest;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.ReasonRequest;
import org.dhu.shiguang_market.identity.mapper.SysRoleMapper;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.mapper.SysUserRoleMapper;
import org.dhu.shiguang_market.identity.model.SysRole;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.identity.model.SysUserRole;
import org.dhu.shiguang_market.identity.service.PlatformUserService;
import org.dhu.shiguang_market.identity.service.PlatformUserSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 平台用户状态修改和角色全量替换测试。 */
class PlatformUserManagementTests {
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final PlatformUserSessionService sessions = mock(PlatformUserSessionService.class);
    private PlatformUserService service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "platform-user-management-test");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        service = new PlatformUserService(userMapper, roleMapper, userRoleMapper, currentUser, sessions);
    }

    /** 用户停用后保存新状态，并踢下线该账号全部终端。 */
    @Test
    void disablingUserUpdatesStatusAndKicksOutSessions() {
        SysUser user = user(101L, UserStatus.ACTIVE);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(roleMapper.selectPlatformRolesByUserId(101L)).thenReturn(List.of());
        when(userMapper.userHasActivePermission(101L, "platform:rbac:manage")).thenReturn(false);

        var result = service.changeStatus(101L,
                new ChangeUserStatusRequest(UserStatus.DISABLED, "测试账号停用"));

        assertEquals(UserStatus.DISABLED, result.status());
        verify(userMapper).updateById(user);
        verify(sessions).kickout(101L);
    }

    /** 系统只剩一名可用 RBAC 管理员时，不允许将其停用。 */
    @Test
    void lastActiveRbacAdminCannotBeDisabled() {
        SysUser user = user(101L, UserStatus.ACTIVE);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userMapper.userHasActivePermission(101L, "platform:rbac:manage")).thenReturn(true);
        when(userMapper.countActiveUsersWithPermission("platform:rbac:manage")).thenReturn(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.changeStatus(101L,
                        new ChangeUserStatusRequest(UserStatus.DISABLED, "误操作保护测试")));

        assertEquals("LAST_RBAC_ADMIN_REQUIRED", exception.getCode());
        verify(userMapper, never()).updateById(user);
        verify(sessions, never()).kickout(101L);
    }

    /** 角色全量替换只接受 PLATFORM 角色，SHOP 角色必须拒绝。 */
    @Test
    void assigningShopRoleIsRejected() {
        SysRole shopRole = role(2001L, ScopeType.SHOP);
        when(userMapper.selectOne(any())).thenReturn(user(101L, UserStatus.ACTIVE));
        when(roleMapper.selectRolesByIds(List.of(2001L))).thenReturn(List.of(shopRole));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.assignRoles(101L, new AssignPlatformRolesRequest(List.of("2001"))));

        assertEquals("ROLE_SCOPE_MISMATCH", exception.getCode());
        verify(userRoleMapper, never()).deletePlatformRolesByUserId(101L);
    }

    /** 合法的平台角色采用“先删除旧平台角色、再写入新角色”的全量替换语义。 */
    @Test
    void assigningPlatformRolesReplacesExistingAssignments() {
        SysRole platformRole = role(1001L, ScopeType.PLATFORM);
        when(userMapper.selectOne(any())).thenReturn(user(101L, UserStatus.ACTIVE));
        when(roleMapper.selectRolesByIds(List.of(1001L))).thenReturn(List.of(platformRole));
        when(roleMapper.selectPlatformRolesByUserId(101L)).thenReturn(List.of(platformRole));
        when(currentUser.id()).thenReturn(999L);

        var result = service.assignRoles(101L,
                new AssignPlatformRolesRequest(List.of("1001")));

        verify(userRoleMapper).deletePlatformRolesByUserId(101L);
        ArgumentCaptor<SysUserRole> assignment = ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoleMapper).insert(assignment.capture());
        assertEquals(ScopeType.PLATFORM, assignment.getValue().getRoleScope());
        assertEquals("CUSTOMER", result.platformRoles().getFirst().roleCode());
    }

    /** 调用者不能移除自己最后一个拥有 RBAC 管理权限的角色。 */
    @Test
    void callerCannotRemoveOwnRbacAccess() {
        when(userMapper.selectOne(any())).thenReturn(user(101L, UserStatus.ACTIVE));
        when(currentUser.id()).thenReturn(101L);
        when(userMapper.userHasActivePermission(101L, "platform:rbac:manage")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.assignRoles(101L, new AssignPlatformRolesRequest(List.of())));

        assertEquals("CANNOT_REMOVE_OWN_ADMIN_ACCESS", exception.getCode());
        verify(userRoleMapper, never()).deletePlatformRolesByUserId(101L);
    }

    /** 强制下线前先确认用户存在，再让会话服务清理该用户的全部登录终端。 */
    @Test
    void kickoutExistingUserClearsAllSessions() {
        when(userMapper.selectById(101L)).thenReturn(user(101L, UserStatus.ACTIVE));

        service.kickout(101L, new ReasonRequest("账号安全检查"));

        verify(sessions).kickout(101L);
    }

    private SysUser user(long id, UserStatus status) {
        SysUser value = new SysUser();
        value.setId(id);
        value.setUsername("user" + id);
        value.setNickname("用户" + id);
        value.setStatus(status);
        return value;
    }

    private SysRole role(long id, ScopeType scopeType) {
        SysRole value = new SysRole();
        value.setId(id);
        value.setRoleCode(scopeType == ScopeType.PLATFORM ? "CUSTOMER" : "SHOP_ADMIN");
        value.setRoleName("测试角色");
        value.setScopeType(scopeType);
        value.setStatus(ActiveStatus.ACTIVE);
        return value;
    }
}
