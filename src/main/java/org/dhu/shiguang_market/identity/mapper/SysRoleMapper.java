package org.dhu.shiguang_market.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;
import org.dhu.shiguang_market.identity.model.SysRole;

public interface SysRoleMapper extends BaseMapper<SysRole> {
    /** 按作用域、状态和关键词分页查询角色。 */
    @Select("""
            <script>
            SELECT * FROM sys_role
            WHERE 1 = 1
            <if test="scopeType != null">AND scope_type = #{scopeType}</if>
            <if test="status != null">AND status = #{status}</if>
            <if test="keyword != null and keyword != ''">
              AND (role_code LIKE CONCAT('%', #{keyword}, '%')
                   OR role_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY created_at DESC, id DESC
            </script>
            """)
    Page<SysRole> selectRolePage(
            Page<SysRole> page,
            @Param("scopeType") ScopeType scopeType,
            @Param("status") ActiveStatus status,
            @Param("keyword") String keyword);

    /** 查询商家可分配的有效店铺角色，供店铺成员管理页面选择。 */
    @Select("""
            <script>
            SELECT * FROM sys_role
            WHERE scope_type = 'SHOP' AND status = 'ACTIVE'
            <if test="keyword != null and keyword != ''">
              AND (role_code LIKE CONCAT('%', #{keyword}, '%')
                   OR role_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY role_code ASC, id ASC
            </script>
            """)
    Page<SysRole> selectAssignableShopRolePage(
            Page<SysRole> page,
            @Param("keyword") String keyword);

    /** 创建角色前检查角色代码是否已经存在。 */
    @Select("SELECT EXISTS(SELECT 1 FROM sys_role WHERE role_code = #{roleCode})")
    boolean existsByRoleCode(@Param("roleCode") String roleCode);

    /** 写操作读取角色时加行锁，避免状态和权限替换互相覆盖。 */
    @Select("SELECT * FROM sys_role WHERE id = #{roleId} FOR UPDATE")
    SysRole selectRoleForUpdate(@Param("roleId") long roleId);

    /** 判断有效角色是否直接拥有指定的有效权限。 */
    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM sys_role r
                JOIN sys_role_permission rp ON rp.role_id = r.id
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE r.id = #{roleId} AND r.status = 'ACTIVE'
                  AND p.status = 'ACTIVE' AND p.permission_code = #{permissionCode}
            )
            """)
    boolean roleHasActivePermission(@Param("roleId") long roleId,
                                    @Param("permissionCode") String permissionCode);

    /** 查询用户当前拥有的平台角色，用于用户列表和详情展示。 */
    @Select("""
            SELECT r.* FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND ur.role_scope = 'PLATFORM'
            ORDER BY r.role_code, r.id
            """)
    List<SysRole> selectPlatformRolesByUserId(@Param("userId") long userId);

    /** 按 ID 批量读取角色；Service 负责检查数量和作用域。 */
    @Select("""
            <script>
            SELECT * FROM sys_role WHERE id IN
            <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
              #{roleId}
            </foreach>
            ORDER BY id
            </script>
            """)
    List<SysRole> selectRolesByIds(@Param("roleIds") List<Long> roleIds);

    /** 判断一组有效平台角色是否至少有一个能够管理 RBAC。 */
    @Select("""
            <script>
            SELECT COUNT(DISTINCT r.id) FROM sys_role r
            JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.scope_type = 'PLATFORM'
            JOIN sys_permission p ON p.id = rp.permission_id
                AND p.scope_type = 'PLATFORM' AND p.status = 'ACTIVE'
            WHERE r.scope_type = 'PLATFORM' AND r.status = 'ACTIVE'
              AND p.permission_code = #{permissionCode}
              AND r.id IN
              <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
                #{roleId}
              </foreach>
            </script>
            """)
    int countRolesWithPermission(@Param("roleIds") List<Long> roleIds,
                                 @Param("permissionCode") String permissionCode);
}
