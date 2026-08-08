package org.dhu.shiguang_market.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.dhu.shiguang_market.identity.model.SysRolePermission;

public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {
    /** 权限全量替换前删除角色原有的权限关系。 */
    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") long roleId);
}
