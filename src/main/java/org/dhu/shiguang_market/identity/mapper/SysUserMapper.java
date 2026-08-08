package org.dhu.shiguang_market.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;

public interface SysUserMapper extends BaseMapper<SysUser> {
    /** 平台用户分页查询，角色条件使用 EXISTS，避免一名用户多个角色造成重复行。 */
    @Select("""
            <script>
            SELECT u.* FROM sys_user u
            WHERE u.deleted_at IS NULL
            <if test="keyword != null and keyword != ''">
              AND (u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR u.nickname LIKE CONCAT('%', #{keyword}, '%')
                   OR u.phone LIKE CONCAT('%', #{keyword}, '%')
                   OR u.email LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">AND u.status = #{status}</if>
            <if test="roleCode != null and roleCode != ''">
              AND EXISTS (
                SELECT 1 FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id
                WHERE ur.user_id = u.id
                  AND r.scope_type = 'PLATFORM'
                  AND r.role_code = #{roleCode}
              )
            </if>
            ORDER BY u.created_at DESC, u.id DESC
            </script>
            """)
    Page<SysUser> selectPlatformUserPage(
            Page<SysUser> page,
            @Param("keyword") String keyword,
            @Param("status") UserStatus status,
            @Param("roleCode") String roleCode);

    /** 判断指定 ACTIVE 用户是否通过有效平台角色拥有目标权限。 */
    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id AND ur.role_scope = 'PLATFORM'
                JOIN sys_role r ON r.id = ur.role_id
                    AND r.scope_type = 'PLATFORM' AND r.status = 'ACTIVE'
                JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.scope_type = 'PLATFORM'
                JOIN sys_permission p ON p.id = rp.permission_id
                    AND p.scope_type = 'PLATFORM' AND p.status = 'ACTIVE'
                WHERE u.id = #{userId} AND u.status = 'ACTIVE'
                  AND u.deleted_at IS NULL AND p.permission_code = #{permissionCode}
            )
            """)
    boolean userHasActivePermission(@Param("userId") long userId,
                                    @Param("permissionCode") String permissionCode);

    /** 统计当前仍可管理 RBAC 的 ACTIVE 用户数量，用于最后管理员保护。 */
    @Select("""
            SELECT COUNT(DISTINCT u.id) FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id AND ur.role_scope = 'PLATFORM'
            JOIN sys_role r ON r.id = ur.role_id
                AND r.scope_type = 'PLATFORM' AND r.status = 'ACTIVE'
            JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.scope_type = 'PLATFORM'
            JOIN sys_permission p ON p.id = rp.permission_id
                AND p.scope_type = 'PLATFORM' AND p.status = 'ACTIVE'
            WHERE u.status = 'ACTIVE' AND u.deleted_at IS NULL
              AND p.permission_code = #{permissionCode}
            """)
    int countActiveUsersWithPermission(@Param("permissionCode") String permissionCode);

    @Select("""
            SELECT DISTINCT r.role_code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.scope_type = 'PLATFORM' AND r.status = 'ACTIVE'
            WHERE ur.user_id = #{userId}
            ORDER BY r.role_code
            """)
    List<String> selectPlatformRoles(@Param("userId") long userId);

    @Select("""
            SELECT DISTINCT p.permission_code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.scope_type = 'PLATFORM' AND r.status = 'ACTIVE'
            JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.scope_type = 'PLATFORM'
            JOIN sys_permission p ON p.id = rp.permission_id AND p.scope_type = 'PLATFORM' AND p.status = 'ACTIVE'
            WHERE ur.user_id = #{userId}
            ORDER BY p.permission_code
            """)
    List<String> selectPlatformPermissions(@Param("userId") long userId);
}
