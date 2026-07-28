package org.dhu.shiguang_market.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dhu.shiguang_market.shop.model.ShopUser;

public interface ShopUserMapper extends BaseMapper<ShopUser> {
    @Select("""
            SELECT DISTINCT p.permission_code
            FROM shop_user su
            JOIN shop s ON s.id = su.shop_id
            JOIN sys_role r ON r.id = su.role_id AND r.scope_type = 'SHOP' AND r.status = 'ACTIVE'
            JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.scope_type = 'SHOP'
            JOIN sys_permission p ON p.id = rp.permission_id AND p.scope_type = 'SHOP' AND p.status = 'ACTIVE'
            WHERE su.shop_id = #{shopId} AND su.user_id = #{userId} AND su.status = 'ACTIVE'
            """)
    List<String> selectPermissions(@Param("shopId") long shopId, @Param("userId") long userId);
}
