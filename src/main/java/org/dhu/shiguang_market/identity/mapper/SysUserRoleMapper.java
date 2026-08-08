package org.dhu.shiguang_market.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.dhu.shiguang_market.identity.model.SysUserRole;

public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
    /** 全量替换角色前只删除平台作用域关系，不影响用户的店铺角色。 */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId} AND role_scope = 'PLATFORM'")
    int deletePlatformRolesByUserId(@Param("userId") long userId);
}
