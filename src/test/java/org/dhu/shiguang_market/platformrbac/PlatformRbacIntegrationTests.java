package org.dhu.shiguang_market.platformrbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.UUID;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.identity.controller.PlatformRbacController;
import org.dhu.shiguang_market.identity.dto.PlatformRbacDtos.AssignPermissionsRequest;
import org.dhu.shiguang_market.identity.dto.PlatformRbacDtos.CreateRoleRequest;
import org.dhu.shiguang_market.identity.dto.PlatformRbacDtos.StatusRequest;
import org.dhu.shiguang_market.identity.dto.PlatformRbacDtos.UpdateRoleRequest;
import org.dhu.shiguang_market.identity.mapper.SysPermissionMapper;
import org.dhu.shiguang_market.identity.mapper.SysRoleMapper;
import org.dhu.shiguang_market.identity.mapper.SysRolePermissionMapper;
import org.dhu.shiguang_market.identity.model.SysPermission;
import org.dhu.shiguang_market.identity.model.SysRole;
import org.dhu.shiguang_market.identity.model.SysRolePermission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台角色与权限集成测试。
 *
 * <p>测试使用真实 Controller、Service、Mapper 和 MySQL；所有写入均在测试事务中，
 * 用例结束后自动回滚，不会污染开发数据库。</p>
 */
@SpringBootTest
@Transactional
class PlatformRbacIntegrationTests {
    @Autowired private PlatformRbacController controller;
    @Autowired private SysRoleMapper roleMapper;
    @Autowired private SysPermissionMapper permissionMapper;
    @Autowired private SysRolePermissionMapper rolePermissionMapper;

    /** 鉴权本身已有统一测试，这里只替换当前用户服务，以便专注验证三层业务链路。 */
    @MockitoBean private CurrentUserService currentUser;

    /** 角色列表、详情和权限列表应通过真实 Mapper 读到测试事务内的数据。 */
    @Test
    void queryFlowConnectsControllerServiceAndMapper() {
        String marker = suffix();
        SysRole role = insertRole("IT_QUERY_" + marker, ScopeType.PLATFORM);
        SysPermission permission = insertPermission(
                "platform:it_query_" + marker.toLowerCase(), ScopeType.PLATFORM);
        insertRelation(role.getId(), permission.getId(), ScopeType.PLATFORM);

        var roles = controller.roles(
                ScopeType.PLATFORM, ActiveStatus.ACTIVE, marker, 1, 20).data();
        var detail = controller.roleDetail(role.getId()).data();
        var permissions = controller.permissions(
                ScopeType.PLATFORM, ActiveStatus.ACTIVE, marker.toLowerCase(), 1, 20).data();

        assertThat(roles.items()).extracting("roleCode").containsExactly(role.getRoleCode());
        assertThat(detail.permissions()).extracting("permissionCode")
                .containsExactly(permission.getPermissionCode());
        assertThat(permissions.items()).extracting("permissionCode")
                .containsExactly(permission.getPermissionCode());
    }

    /** 创建角色时写入初始权限，随后只允许修改角色名称和说明。 */
    @Test
    void createAndUpdateRoleFlowIsConnected() {
        String marker = suffix();
        SysPermission permission = insertPermission(
                "platform:it_create_" + marker.toLowerCase(), ScopeType.PLATFORM);

        var response = controller.createRole(new CreateRoleRequest(
                "IT_CREATE_" + marker, "测试操作员", ScopeType.PLATFORM,
                "用于集成测试", List.of(permission.getId().toString())));
        assertThat(response.getBody()).isNotNull();
        var created = response.getBody().data();
        long roleId = Long.parseLong(created.id());

        var updated = controller.updateRole(roleId,
                new UpdateRoleRequest("测试操作员（已修改）", "修改后的说明")).data();

        SysRole saved = roleMapper.selectById(roleId);
        assertThat(created.permissions()).hasSize(1);
        assertThat(updated.roleName()).isEqualTo("测试操作员（已修改）");
        assertThat(saved.getRoleCode()).isEqualTo("IT_CREATE_" + marker);
        assertThat(saved.getRoleName()).isEqualTo("测试操作员（已修改）");
        assertThat(countRelations(roleId)).isOne();
    }

    /** 普通角色可以停用，但 CUSTOMER、SUPER_ADMIN、SHOP_ADMIN 三个关键角色不可停用。 */
    @Test
    void roleStatusChangeProtectsCriticalRoles() {
        SysRole ordinaryRole = insertRole("IT_STATUS_" + suffix(), ScopeType.PLATFORM);
        var disabled = controller.changeRoleStatus(
                ordinaryRole.getId(), new StatusRequest(ActiveStatus.DISABLED)).data();
        assertThat(disabled.status()).isEqualTo(ActiveStatus.DISABLED);
        assertThat(roleMapper.selectById(ordinaryRole.getId()).getStatus())
                .isEqualTo(ActiveStatus.DISABLED);

        SysRole criticalRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, "SUPER_ADMIN"));
        assertThat(criticalRole).as("schema.sql 应初始化 SUPER_ADMIN 角色").isNotNull();

        assertThatThrownBy(() -> controller.changeRoleStatus(
                criticalRole.getId(), new StatusRequest(ActiveStatus.DISABLED)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("CANNOT_DISABLE_CRITICAL_ROLE"));

        assertThat(roleMapper.selectById(criticalRole.getId()).getStatus())
                .isEqualTo(ActiveStatus.ACTIVE);
    }

    /** 权限替换采用全量语义，并拒绝把 SHOP 权限分配给 PLATFORM 角色。 */
    @Test
    void permissionReplacementChecksScopeAndReplacesRelations() {
        String marker = suffix();
        SysRole role = insertRole("IT_ASSIGN_" + marker, ScopeType.PLATFORM);
        SysPermission platformPermission = insertPermission(
                "platform:it_assign_" + marker.toLowerCase(), ScopeType.PLATFORM);
        SysPermission shopPermission = insertPermission(
                "shop:it_assign_" + marker.toLowerCase(), ScopeType.SHOP);

        var result = controller.assignPermissions(role.getId(),
                new AssignPermissionsRequest(List.of(platformPermission.getId().toString()))).data();

        assertThat(result.permissions()).extracting("permissionCode")
                .containsExactly(platformPermission.getPermissionCode());
        assertThat(countRelations(role.getId())).isOne();

        assertThatThrownBy(() -> controller.assignPermissions(role.getId(),
                new AssignPermissionsRequest(List.of(shopPermission.getId().toString()))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("PERMISSION_SCOPE_MISMATCH"));
        assertThat(countRelations(role.getId())).isOne();
    }

    private SysRole insertRole(String code, ScopeType scopeType) {
        SysRole role = new SysRole();
        role.setRoleCode(code);
        role.setRoleName("集成测试角色");
        role.setScopeType(scopeType);
        role.setDescription("测试事务结束后自动回滚");
        role.setStatus(ActiveStatus.ACTIVE);
        assertThat(roleMapper.insert(role)).isOne();
        return role;
    }

    private SysPermission insertPermission(String code, ScopeType scopeType) {
        SysPermission permission = new SysPermission();
        permission.setPermissionCode(code);
        permission.setPermissionName("集成测试权限");
        permission.setScopeType(scopeType);
        permission.setResource("/api/integration-test/**");
        permission.setHttpMethod("GET");
        permission.setStatus(ActiveStatus.ACTIVE);
        assertThat(permissionMapper.insert(permission)).isOne();
        return permission;
    }

    private void insertRelation(long roleId, long permissionId, ScopeType scopeType) {
        SysRolePermission relation = new SysRolePermission();
        relation.setRoleId(roleId);
        relation.setPermissionId(permissionId);
        relation.setScopeType(scopeType);
        assertThat(rolePermissionMapper.insert(relation)).isOne();
    }

    private long countRelations(long roleId) {
        return rolePermissionMapper.selectCount(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase();
    }
}
