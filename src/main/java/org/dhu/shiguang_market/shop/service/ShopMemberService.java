package org.dhu.shiguang_market.shop.service;

import static org.dhu.shiguang_market.common.util.Formatters.id;
import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.ShopAccessService;
import org.dhu.shiguang_market.common.util.Formatters;
import org.dhu.shiguang_market.identity.dto.PlatformUserDtos.RoleView;
import org.dhu.shiguang_market.identity.mapper.SysRoleMapper;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.model.SysRole;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.identity.service.IdentityViewMapper;
import org.dhu.shiguang_market.shop.dto.ShopMemberDtos.AddShopMemberRequest;
import org.dhu.shiguang_market.shop.dto.ShopMemberDtos.ChangeShopMemberRoleRequest;
import org.dhu.shiguang_market.shop.dto.ShopMemberDtos.ShopMemberView;
import org.dhu.shiguang_market.shop.dto.ShopMemberDtos.StatusRequest;
import org.dhu.shiguang_market.shop.mapper.ShopUserMapper;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.model.ShopUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 店铺成员查询和管理服务。 */
@Service
public class ShopMemberService {
    private static final String MANAGE_PERMISSION = "shop:member:manage";
    private static final String PLATFORM_MANAGE_PERMISSION = "platform:shop:member:manage";
    private final ShopUserMapper shopUserMapper;
    private final ShopMapper shopMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final ShopAccessService shopAccess;
    private final CurrentUserService currentUser;

    public ShopMemberService(ShopUserMapper shopUserMapper, ShopMapper shopMapper, SysUserMapper userMapper,
                             SysRoleMapper roleMapper, ShopAccessService shopAccess,
                             CurrentUserService currentUser) {
        this.shopUserMapper = shopUserMapper;
        this.shopMapper = shopMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.shopAccess = shopAccess;
        this.currentUser = currentUser;
    }

    /** 分页查询本店成员，所有筛选条件均交由 Mapper 参数化执行。 */
    @Transactional(readOnly = true)
    public PageView<ShopMemberView> list(long shopId, String keyword, Long roleId,
                                         ActiveStatus status, long page, long pageSize) {
        shopAccess.require(shopId, MANAGE_PERMISSION);
        checkPage(page, pageSize);
        Page<ShopUser> result = shopUserMapper.selectMemberPage(
                Page.of(page, pageSize), shopId, Formatters.trimToNull(keyword), roleId, status);
        return PageView.of(result, result.getRecords().stream().map(this::view).toList());
    }

    /** 查询当前商家可分配的有效店铺角色。 */
    @Transactional(readOnly = true)
    public PageView<RoleView> roles(long shopId, String keyword, long page, long pageSize) {
        shopAccess.require(shopId, MANAGE_PERMISSION);
        checkPage(page, pageSize);
        Page<SysRole> result = roleMapper.selectAssignableShopRolePage(
                Page.of(page, pageSize), Formatters.trimToNull(keyword));
        return PageView.of(result, result.getRecords().stream().map(this::roleView).toList());
    }

    @Transactional(readOnly = true)
    public PageView<ShopMemberView> listForPlatform(long shopId, String keyword, Long roleId,
                                                    ActiveStatus status, long page, long pageSize) {
        requirePlatformAccess(shopId);
        return listMembers(shopId, keyword, roleId, status, page, pageSize);
    }

    /** 按精确用户名新增 ACTIVE 用户，并分配一个有效的 SHOP 角色。 */
    @Transactional
    public ShopMemberView add(long shopId, AddShopMemberRequest request) {
        shopAccess.require(shopId, MANAGE_PERMISSION);
        return addMember(shopId, request);
    }

    @Transactional
    public ShopMemberView addForPlatform(long shopId, AddShopMemberRequest request) {
        requirePlatformAccess(shopId);
        return addMember(shopId, request);
    }

    private ShopMemberView addMember(long shopId, AddShopMemberRequest request) {
        if (request == null || Formatters.trimToNull(request.username()) == null) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "username 不能为空");
        }
        long roleId = parseId(request.roleId(), "角色");
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username().trim())
                .eq(SysUser::getStatus, UserStatus.ACTIVE));
        if (user == null) {
            throw BusinessException.notFound("RESOURCE_NOT_FOUND", "ACTIVE 用户不存在");
        }
        requireShopRole(roleId);
        if (shopUserMapper.existsMember(shopId, user.getId())) {
            throw BusinessException.conflict("SHOP_MEMBER_ALREADY_EXISTS", "用户已经是本店成员");
        }

        ShopUser member = new ShopUser();
        member.setShopId(shopId);
        member.setUserId(user.getId());
        member.setRoleId(roleId);
        member.setRoleScope(ScopeType.SHOP);
        member.setStatus(ActiveStatus.ACTIVE);
        try {
            shopUserMapper.insert(member);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("SHOP_MEMBER_ALREADY_EXISTS", "用户已经是本店成员");
        }
        // ShopAccessService 每次直接查询数据库，事务提交后新权限立即生效，无额外缓存需要维护。
        return view(requireMember(shopId, user.getId()));
    }

    /** 修改成员角色；移除最后一个成员管理员的管理权限时必须拒绝。 */
    @Transactional
    public ShopMemberView changeRole(long shopId, long userId, ChangeShopMemberRoleRequest request) {
        shopAccess.require(shopId, MANAGE_PERMISSION);
        return changeRoleMember(shopId, userId, request);
    }

    @Transactional
    public ShopMemberView changeRoleForPlatform(long shopId, long userId,
                                                ChangeShopMemberRoleRequest request) {
        requirePlatformAccess(shopId);
        return changeRoleMember(shopId, userId, request);
    }

    private ShopMemberView changeRoleMember(long shopId, long userId,
                                            ChangeShopMemberRoleRequest request) {
        long roleId = parseId(request == null ? null : request.roleId(), "角色");
        SysRole newRole = requireShopRole(roleId);
        ShopUser member = requireMember(shopId, userId);
        if (member.getRoleId() == roleId) return view(member);

        boolean currentlyManages = roleMapper.roleHasActivePermission(
                member.getRoleId(), MANAGE_PERMISSION);
        boolean newRoleManages = roleMapper.roleHasActivePermission(roleId, MANAGE_PERMISSION);
        if (member.getStatus() == ActiveStatus.ACTIVE && currentlyManages && !newRoleManages) {
            protectLastManager(shopId, userId, "LAST_SHOP_ADMIN_REQUIRED");
        }
        shopUserMapper.updateMemberRole(shopId, userId, newRole.getId());
        return view(requireMember(shopId, userId));
    }

    /** 修改成员状态；调用者不能在没有其他管理员时停用自己。 */
    @Transactional
    public ShopMemberView changeStatus(long shopId, long userId, StatusRequest request) {
        shopAccess.require(shopId, MANAGE_PERMISSION);
        return changeStatusMember(shopId, userId, request, true);
    }

    @Transactional
    public ShopMemberView changeStatusForPlatform(long shopId, long userId, StatusRequest request) {
        requirePlatformAccess(shopId);
        return changeStatusMember(shopId, userId, request, false);
    }

    private ShopMemberView changeStatusMember(long shopId, long userId, StatusRequest request,
                                              boolean protectSelf) {
        if (request == null || request.targetStatus() == null) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "targetStatus 不能为空");
        }
        ShopUser member = requireMember(shopId, userId);
        if (member.getStatus() == request.targetStatus()) return view(member);
        if (request.targetStatus() == ActiveStatus.ACTIVE) {
            SysUser user = userMapper.selectById(userId);
            if (user == null || user.getStatus() != UserStatus.ACTIVE) {
                throw BusinessException.conflict("STATE_CONFLICT", "非 ACTIVE 用户不能启用店铺成员身份");
            }
        } else if (roleMapper.roleHasActivePermission(member.getRoleId(), MANAGE_PERMISSION)) {
            String code = protectSelf && currentUser.id() == userId
                    ? "CANNOT_DISABLE_SELF_WITHOUT_OTHER_ADMIN" : "LAST_SHOP_ADMIN_REQUIRED";
            protectLastManager(shopId, userId, code);
        }
        shopUserMapper.updateMemberStatus(shopId, userId, request.targetStatus());
        return view(requireMember(shopId, userId));
    }

    @Transactional
    public void removeForPlatform(long shopId, long userId) {
        requirePlatformAccess(shopId);
        ShopUser member = requireMember(shopId, userId);
        if (member.getStatus() == ActiveStatus.ACTIVE
                && roleMapper.roleHasActivePermission(member.getRoleId(), MANAGE_PERMISSION)) {
            protectLastManager(shopId, userId, "LAST_SHOP_ADMIN_REQUIRED");
        }
        if (shopUserMapper.deleteMember(shopId, userId) != 1) {
            throw BusinessException.notFound("SHOP_MEMBER_NOT_FOUND", "店铺成员不存在");
        }
    }

    private PageView<ShopMemberView> listMembers(long shopId, String keyword, Long roleId,
                                                 ActiveStatus status, long page, long pageSize) {
        checkPage(page, pageSize);
        Page<ShopUser> result = shopUserMapper.selectMemberPage(
                Page.of(page, pageSize), shopId, Formatters.trimToNull(keyword), roleId, status);
        return PageView.of(result, result.getRecords().stream().map(this::view).toList());
    }

    private void requirePlatformAccess(long shopId) {
        currentUser.requirePermission(PLATFORM_MANAGE_PERMISSION);
        if (shopMapper.selectById(shopId) == null) {
            throw BusinessException.notFound("SHOP_NOT_FOUND", "店铺不存在");
        }
    }

    private SysRole requireShopRole(long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null || role.getScopeType() != ScopeType.SHOP
                || role.getStatus() != ActiveStatus.ACTIVE) {
            throw BusinessException.badRequest("SHOP_ROLE_REQUIRED", "必须选择有效的 SHOP 角色");
        }
        return role;
    }

    private ShopUser requireMember(long shopId, long userId) {
        ShopUser member = shopUserMapper.selectMemberForUpdate(shopId, userId);
        if (member == null) {
            throw BusinessException.notFound("SHOP_MEMBER_NOT_FOUND", "店铺成员不存在");
        }
        return member;
    }

    private void protectLastManager(long shopId, long userId, String errorCode) {
        if (shopUserMapper.countActiveManagersExcludingUser(
                shopId, MANAGE_PERMISSION, userId) == 0) {
            throw BusinessException.conflict(errorCode, "店铺必须保留至少一名可用的成员管理员");
        }
    }

    private long parseId(String rawId, String name) {
        try {
            long value = Long.parseLong(rawId);
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) {
            throw BusinessException.badRequest("VALIDATION_FAILED", name + " ID 格式错误");
        }
    }

    private ShopMemberView view(ShopUser member) {
        SysUser user = userMapper.selectById(member.getUserId());
        SysRole role = roleMapper.selectById(member.getRoleId());
        if (user == null || role == null) {
            throw new IllegalStateException("店铺成员关联的用户或角色不存在");
        }
        return new ShopMemberView(id(member.getShopId()), IdentityViewMapper.user(user), roleView(role),
                member.getStatus(), time(member.getCreatedAt()), time(member.getUpdatedAt()));
    }

    private RoleView roleView(SysRole role) {
        return new RoleView(id(role.getId()), role.getRoleCode(), role.getRoleName(),
                role.getScopeType(), role.getDescription(), role.getStatus(),
                time(role.getCreatedAt()), time(role.getUpdatedAt()));
    }

    private void checkPage(long page, long pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
        }
    }
}
