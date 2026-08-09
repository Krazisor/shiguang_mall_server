package org.dhu.shiguang_market.shop.service;

import static org.dhu.shiguang_market.common.util.Formatters.time;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Map;
import java.util.Set;
import org.dhu.shiguang_market.common.api.PageView;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.ShopStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.util.ContentSafety;
import org.dhu.shiguang_market.common.util.NumberGenerator;
import org.dhu.shiguang_market.identity.mapper.SysRoleMapper;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.model.SysRole;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.identity.service.IdentityViewMapper;
import org.dhu.shiguang_market.integration.merchantwallet.MerchantWalletProvisionPort;
import org.dhu.shiguang_market.integration.order.ActiveShopBusinessPort;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.ChangeShopStatusRequest;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.CreateShopRequest;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.PlatformShopView;
import org.dhu.shiguang_market.shop.dto.PlatformDtos.UpdateShopRequest;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.mapper.ShopUserMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.dhu.shiguang_market.shop.model.ShopUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PlatformShopService {
    private static final Logger log = LoggerFactory.getLogger(PlatformShopService.class);
    private static final Map<ShopStatus, Set<ShopStatus>> TRANSITIONS = Map.of(
            ShopStatus.PENDING, Set.of(ShopStatus.ACTIVE, ShopStatus.CLOSED),
            ShopStatus.ACTIVE, Set.of(ShopStatus.SUSPENDED, ShopStatus.CLOSED),
            ShopStatus.SUSPENDED, Set.of(ShopStatus.ACTIVE, ShopStatus.CLOSED),
            ShopStatus.CLOSED, Set.of());
    private final ShopMapper shopMapper;
    private final ShopUserMapper shopUserMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final ActiveShopBusinessPort activeShopBusiness;
    private final CurrentUserService currentUser;
    private final NumberGenerator numbers;
    private final ContentSafety contentSafety;
    private final MerchantWalletProvisionPort merchantWalletProvision;

    @Autowired
    public PlatformShopService(ShopMapper shopMapper, ShopUserMapper shopUserMapper,
                               SysUserMapper userMapper, SysRoleMapper roleMapper,
                               ActiveShopBusinessPort activeShopBusiness,
                               CurrentUserService currentUser, NumberGenerator numbers,
                               ContentSafety contentSafety, MerchantWalletProvisionPort merchantWalletProvision) {
        this.shopMapper = shopMapper;
        this.shopUserMapper = shopUserMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.activeShopBusiness = activeShopBusiness;
        this.currentUser = currentUser;
        this.numbers = numbers;
        this.contentSafety = contentSafety;
        this.merchantWalletProvision = merchantWalletProvision;
    }

    /** Kept for focused tests and older callers; the Spring-managed constructor supplies the mapper. */
    public PlatformShopService(ShopMapper shopMapper, ShopUserMapper shopUserMapper,
                               SysUserMapper userMapper, SysRoleMapper roleMapper,
                               ActiveShopBusinessPort activeShopBusiness,
                               CurrentUserService currentUser, NumberGenerator numbers,
                               ContentSafety contentSafety) {
        this(shopMapper, shopUserMapper, userMapper, roleMapper, activeShopBusiness,
                currentUser, numbers, contentSafety, null);
    }

    public PageView<PlatformShopView> list(ShopStatus status, String keyword,
                                           long page, long pageSize, String sort) {
        currentUser.requirePermission("platform:shop:manage");
        if (page < 1 || pageSize < 1 || pageSize > 100) throw BusinessException.badRequest("BAD_REQUEST", "分页参数超出范围");
        LambdaQueryWrapper<Shop> query = new LambdaQueryWrapper<>();
        if (status != null) query.eq(Shop::getStatus, status);
        if (keyword != null && !keyword.trim().isEmpty()) query.and(q -> q.like(Shop::getShopName, keyword.trim())
                .or().like(Shop::getShopNo, keyword.trim()));
        switch (sort == null ? "createdAt,desc" : sort) {
            case "createdAt,desc" -> query.orderByDesc(Shop::getCreatedAt);
            case "updatedAt,desc" -> query.orderByDesc(Shop::getUpdatedAt);
            case "shopName,asc" -> query.orderByAsc(Shop::getShopName);
            case "status,asc" -> query.orderByAsc(Shop::getStatus);
            default -> throw BusinessException.badRequest("BAD_REQUEST", "不支持的排序字段");
        }
        query.orderByDesc(Shop::getId);
        Page<Shop> result = shopMapper.selectPage(Page.of(page, pageSize), query);
        return PageView.of(result, result.getRecords().stream().map(this::view).toList());
    }

    @Transactional
    public PlatformShopView create(CreateShopRequest request) {
        currentUser.requirePermission("platform:shop:manage");
        SysUser admin = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.adminUsername().trim()).eq(SysUser::getStatus, UserStatus.ACTIVE));
        if (admin == null) throw BusinessException.notFound("RESOURCE_NOT_FOUND", "店铺管理员账号不存在");
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, "SHOP_ADMIN").eq(SysRole::getScopeType, ScopeType.SHOP)
                .eq(SysRole::getStatus, ActiveStatus.ACTIVE));
        if (role == null) throw new IllegalStateException("SHOP_ADMIN role is missing");
        Shop shop = new Shop();
        shop.setShopNo(numbers.next("SHOP"));
        apply(shop, request.shopName(), request.logoUrl(), request.description(), request.contactName(), request.contactPhone());
        shop.setStatus(ShopStatus.PENDING);
        shopMapper.insert(shop);
        ShopUser member = new ShopUser();
        member.setShopId(shop.getId());
        member.setUserId(admin.getId());
        member.setRoleId(role.getId());
        member.setRoleScope(ScopeType.SHOP);
        member.setStatus(ActiveStatus.ACTIVE);
        shopUserMapper.insert(member);
        if (merchantWalletProvision != null) {
            merchantWalletProvision.provision(shop.getId());
        }
        log.info("Created shop shopId={} shopNo={}", shop.getId(), shop.getShopNo());
        return view(shopMapper.selectById(shop.getId()));
    }

    public PlatformShopView detail(long shopId) {
        currentUser.requirePermission("platform:shop:manage");
        return view(require(shopId));
    }

    @Transactional
    public PlatformShopView update(long shopId, UpdateShopRequest request) {
        currentUser.requirePermission("platform:shop:manage");
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getId, shopId).last("FOR UPDATE"));
        if (shop == null) throw BusinessException.notFound("SHOP_NOT_FOUND", "店铺不存在");
        apply(shop, request.shopName(), request.logoUrl(), request.description(), request.contactName(), request.contactPhone());
        shopMapper.updateById(shop);
        log.info("Updated shop shopId={}", shopId);
        return view(shopMapper.selectById(shopId));
    }

    @Transactional
    public PlatformShopView changeStatus(long shopId, ChangeShopStatusRequest request) {
        currentUser.requirePermission("platform:shop:manage");
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getId, shopId).last("FOR UPDATE"));
        if (shop == null) throw BusinessException.notFound("SHOP_NOT_FOUND", "店铺不存在");
        if (!TRANSITIONS.getOrDefault(shop.getStatus(), Set.of()).contains(request.targetStatus())) {
            throw BusinessException.conflict("STATE_CONFLICT", "店铺状态不允许该迁移");
        }
        if (request.targetStatus() == ShopStatus.CLOSED
                && activeShopBusiness.hasActiveBusiness(shopId)) {
            throw BusinessException.unprocessable("SHOP_HAS_ACTIVE_BUSINESS", "店铺存在未完结业务");
        }
        shop.setStatus(request.targetStatus());
        shopMapper.updateById(shop);
        log.info("Changed shop status shopId={} status={}", shopId, request.targetStatus());
        return view(shopMapper.selectById(shopId));
    }

    private PlatformShopView view(Shop shop) {
        int total = Math.toIntExact(shopUserMapper.selectCount(new LambdaQueryWrapper<ShopUser>()
                .eq(ShopUser::getShopId, shop.getId())));
        int active = Math.toIntExact(shopUserMapper.selectCount(new LambdaQueryWrapper<ShopUser>()
                .eq(ShopUser::getShopId, shop.getId()).eq(ShopUser::getStatus, ActiveStatus.ACTIVE)));
        return new PlatformShopView(IdentityViewMapper.shop(shop), shop.getDescription(), shop.getContactName(),
                shop.getContactPhone(), total, active, time(shop.getCreatedAt()), time(shop.getUpdatedAt()));
    }

    private Shop require(long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) throw BusinessException.notFound("SHOP_NOT_FOUND", "店铺不存在");
        return shop;
    }

    private void apply(Shop shop, String name, String logo, String description, String contact, String phone) {
        shop.setShopName(name.trim());
        shop.setLogoUrl(contentSafety.imageUrl("logoUrl", logo));
        shop.setDescription(description);
        shop.setContactName(contact);
        shop.setContactPhone(phone);
    }
}
