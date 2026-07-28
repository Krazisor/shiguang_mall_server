package org.dhu.shiguang_market.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.identity.model.SysUser;

public interface SysUserMapper extends BaseMapper<SysUser> {
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
