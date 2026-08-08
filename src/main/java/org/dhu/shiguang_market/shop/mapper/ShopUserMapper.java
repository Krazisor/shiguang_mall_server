package org.dhu.shiguang_market.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.shop.model.ShopUser;

public interface ShopUserMapper extends BaseMapper<ShopUser> {
    /** 按用户名、昵称、角色和成员状态分页查询指定店铺成员。 */
    @Select("""
            <script>
            SELECT su.* FROM shop_user su
            JOIN sys_user u ON u.id = su.user_id AND u.deleted_at IS NULL
            WHERE su.shop_id = #{shopId}
            <if test="keyword != null and keyword != ''">
              AND (u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR u.nickname LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="roleId != null">AND su.role_id = #{roleId}</if>
            <if test="status != null">AND su.status = #{status}</if>
            ORDER BY su.created_at DESC, su.user_id DESC
            </script>
            """)
    Page<ShopUser> selectMemberPage(
            Page<ShopUser> page,
            @Param("shopId") long shopId,
            @Param("keyword") String keyword,
            @Param("roleId") Long roleId,
            @Param("status") ActiveStatus status);

    /** 锁定一个店铺成员，防止角色和状态修改相互覆盖。 */
    @Select("""
            SELECT * FROM shop_user
            WHERE shop_id = #{shopId} AND user_id = #{userId}
            FOR UPDATE
            """)
    ShopUser selectMemberForUpdate(@Param("shopId") long shopId,
                                   @Param("userId") long userId);

    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM shop_user
                WHERE shop_id = #{shopId} AND user_id = #{userId}
            )
            """)
    boolean existsMember(@Param("shopId") long shopId, @Param("userId") long userId);

    /** 复合主键实体不能使用 updateById，因此只更新明确的角色字段。 */
    @Update("""
            UPDATE shop_user SET role_id = #{roleId}, role_scope = 'SHOP'
            WHERE shop_id = #{shopId} AND user_id = #{userId}
            """)
    int updateMemberRole(@Param("shopId") long shopId, @Param("userId") long userId,
                         @Param("roleId") long roleId);

    /** 按店铺和用户复合主键更新成员状态。 */
    @Update("""
            UPDATE shop_user SET status = #{status}
            WHERE shop_id = #{shopId} AND user_id = #{userId}
            """)
    int updateMemberStatus(@Param("shopId") long shopId, @Param("userId") long userId,
                           @Param("status") ActiveStatus status);

    /** 统计除目标用户外仍拥有成员管理权限的 ACTIVE 成员。 */
    @Select("""
            SELECT COUNT(DISTINCT su.user_id) FROM shop_user su
            JOIN sys_user u ON u.id = su.user_id
                AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
            JOIN sys_role r ON r.id = su.role_id
                AND r.scope_type = 'SHOP' AND r.status = 'ACTIVE'
            JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.scope_type = 'SHOP'
            JOIN sys_permission p ON p.id = rp.permission_id
                AND p.scope_type = 'SHOP' AND p.status = 'ACTIVE'
            WHERE su.shop_id = #{shopId} AND su.status = 'ACTIVE'
              AND su.user_id <> #{excludedUserId}
              AND p.permission_code = #{permissionCode}
            """)
    int countActiveManagersExcludingUser(
            @Param("shopId") long shopId,
            @Param("permissionCode") String permissionCode,
            @Param("excludedUserId") long excludedUserId);

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
