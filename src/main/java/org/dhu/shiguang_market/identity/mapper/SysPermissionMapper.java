package org.dhu.shiguang_market.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;
import org.dhu.shiguang_market.identity.model.SysPermission;

public interface SysPermissionMapper extends BaseMapper<SysPermission> {
    /** 按作用域、状态及代码或名称关键词分页查询权限字典。 */
    @Select("""
            <script>
            SELECT * FROM sys_permission
            WHERE 1 = 1
            <if test="scopeType != null">AND scope_type = #{scopeType}</if>
            <if test="status != null">AND status = #{status}</if>
            <if test="keyword != null and keyword != ''">
              AND (permission_code LIKE CONCAT('%', #{keyword}, '%')
                   OR permission_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY created_at DESC, id DESC
            </script>
            """)
    Page<SysPermission> selectPermissionPage(
            Page<SysPermission> page,
            @Param("scopeType") ScopeType scopeType,
            @Param("status") ActiveStatus status,
            @Param("keyword") String keyword);

    /** 查询角色当前关联的权限，详情接口按权限代码稳定排序。 */
    @Select("""
            SELECT p.* FROM sys_permission p
            JOIN sys_role_permission rp ON rp.permission_id = p.id
            WHERE rp.role_id = #{roleId}
            ORDER BY p.permission_code, p.id
            """)
    List<SysPermission> selectByRoleId(@Param("roleId") long roleId);

    /** 按 ID 批量读取权限；Service 负责检查数量与作用域。 */
    @Select("""
            <script>
            SELECT * FROM sys_permission WHERE id IN
            <foreach collection="permissionIds" item="permissionId" open="(" separator="," close=")">
              #{permissionId}
            </foreach>
            ORDER BY id
            </script>
            """)
    List<SysPermission> selectPermissionsByIds(@Param("permissionIds") List<Long> permissionIds);
}
