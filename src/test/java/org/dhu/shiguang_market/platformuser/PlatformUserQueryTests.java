package org.dhu.shiguang_market.platformuser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.identity.mapper.SysRoleMapper;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.mapper.SysUserRoleMapper;
import org.dhu.shiguang_market.identity.model.SysRole;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.identity.service.PlatformUserService;
import org.dhu.shiguang_market.identity.service.PlatformUserSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 平台用户列表与详情测试，重点验证筛选参数和联系方式脱敏。 */
class PlatformUserQueryTests {
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final PlatformUserSessionService sessions = mock(PlatformUserSessionService.class);
    private PlatformUserService service;

    @BeforeEach
    void setUp() {
        service = new PlatformUserService(userMapper, roleMapper, userRoleMapper, currentUser, sessions);
    }

    /** 列表查询应传递筛选条件，并返回脱敏手机号、邮箱和平台角色。 */
    @Test
    void listReturnsMaskedPlatformUserView() {
        SysUser user = user();
        SysRole role = role();
        when(userMapper.selectPlatformUserPage(any(Page.class), eq("alice"),
                eq(UserStatus.ACTIVE), eq("CUSTOMER"))).thenAnswer(invocation -> {
            Page<SysUser> page = invocation.getArgument(0);
            page.setRecords(List.of(user));
            page.setTotal(1);
            return page;
        });
        when(roleMapper.selectPlatformRolesByUserId(101L)).thenReturn(List.of(role));

        var result = service.list(" alice ", UserStatus.ACTIVE, " CUSTOMER ", 1, 20);

        verify(currentUser).requirePermission("platform:rbac:manage");
        assertEquals(1, result.total());
        assertEquals("138****0000", result.items().getFirst().phoneMasked());
        assertEquals("a***@example.com", result.items().getFirst().emailMasked());
        assertEquals("CUSTOMER", result.items().getFirst().platformRoles().getFirst().roleCode());
    }

    /** 详情查询用户不存在时返回统一 RESOURCE_NOT_FOUND。 */
    @Test
    void detailRejectsMissingUser() {
        when(userMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.detail(999L));

        assertEquals("RESOURCE_NOT_FOUND", exception.getCode());
        verify(currentUser).requirePermission("platform:rbac:manage");
    }

    private SysUser user() {
        SysUser value = new SysUser();
        value.setId(101L);
        value.setUsername("alice_01");
        value.setNickname("Alice");
        value.setPhone("13812340000");
        value.setEmail("alice@example.com");
        value.setAvatarUrl("https://example.com/avatar.png");
        value.setStatus(UserStatus.ACTIVE);
        value.setLastLoginAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        value.setCreatedAt(LocalDateTime.of(2026, 7, 20, 10, 0));
        value.setUpdatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        return value;
    }

    private SysRole role() {
        SysRole value = new SysRole();
        value.setId(1001L);
        value.setRoleCode("CUSTOMER");
        value.setRoleName("普通用户");
        value.setScopeType(ScopeType.PLATFORM);
        value.setDescription("普通用户");
        value.setStatus(ActiveStatus.ACTIVE);
        value.setCreatedAt(LocalDateTime.of(2026, 7, 20, 10, 0));
        value.setUpdatedAt(LocalDateTime.of(2026, 7, 20, 10, 0));
        return value;
    }
}
