package org.dhu.shiguang_market.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.identity.model.SysRole;

public interface SysRoleMapper extends BaseMapper<SysRole> {
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
