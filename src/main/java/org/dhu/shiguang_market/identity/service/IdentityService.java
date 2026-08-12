package org.dhu.shiguang_market.identity.service;

import static org.dhu.shiguang_market.identity.service.IdentityViewMapper.shop;
import static org.dhu.shiguang_market.identity.service.IdentityViewMapper.user;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.ActiveStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.ScopeType;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.common.security.PasswordService;
import org.dhu.shiguang_market.common.util.ContentSafety;
import org.dhu.shiguang_market.common.util.Formatters;
import org.dhu.shiguang_market.identity.dto.IdentityDtos.CurrentUserView;
import org.dhu.shiguang_market.identity.dto.IdentityDtos.LoginRequest;
import org.dhu.shiguang_market.identity.dto.IdentityDtos.LoginView;
import org.dhu.shiguang_market.identity.dto.IdentityDtos.RegisterRequest;
import org.dhu.shiguang_market.identity.dto.IdentityDtos.ShopContextView;
import org.dhu.shiguang_market.identity.dto.IdentityDtos.UpdateProfileRequest;
import org.dhu.shiguang_market.identity.mapper.SysRoleMapper;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.mapper.SysUserRoleMapper;
import org.dhu.shiguang_market.identity.model.SysRole;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.dhu.shiguang_market.identity.model.SysUserRole;
import org.dhu.shiguang_market.integration.payment.WalletProvisionPort;
import org.dhu.shiguang_market.shop.mapper.ShopMapper;
import org.dhu.shiguang_market.shop.mapper.ShopUserMapper;
import org.dhu.shiguang_market.shop.model.Shop;
import org.dhu.shiguang_market.shop.model.ShopUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.dhu.shiguang_market.coupon.event.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class IdentityService {
    private static final Logger log = LoggerFactory.getLogger(IdentityService.class);
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final WalletProvisionPort walletProvision;
    private final ShopMapper shopMapper;
    private final ShopUserMapper shopUserMapper;
    private final CurrentUserService currentUser;
    private final PasswordService passwordService;
    private final ContentSafety contentSafety;
    private final ApplicationEventPublisher events;

    @org.springframework.beans.factory.annotation.Autowired
    public IdentityService(SysUserMapper userMapper, SysRoleMapper roleMapper,
                           SysUserRoleMapper userRoleMapper, WalletProvisionPort walletProvision,
                           ShopMapper shopMapper, ShopUserMapper shopUserMapper,
                           CurrentUserService currentUser, PasswordService passwordService,
                           ContentSafety contentSafety, ApplicationEventPublisher events) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.walletProvision = walletProvision;
        this.shopMapper = shopMapper;
        this.shopUserMapper = shopUserMapper;
        this.currentUser = currentUser;
        this.passwordService = passwordService;
        this.contentSafety = contentSafety;
        this.events = events;
    }

    public IdentityService(SysUserMapper userMapper, SysRoleMapper roleMapper,
                           SysUserRoleMapper userRoleMapper, WalletProvisionPort walletProvision,
                           ShopMapper shopMapper, ShopUserMapper shopUserMapper,
                           CurrentUserService currentUser, PasswordService passwordService,
                           ContentSafety contentSafety) {
        this(userMapper, roleMapper, userRoleMapper, walletProvision, shopMapper, shopUserMapper,
                currentUser, passwordService, contentSafety, event -> { });
    }

    @Transactional
    public org.dhu.shiguang_market.common.api.CommonViews.UserSummary register(RegisterRequest request) {
        String username = request.username().trim();
        ensureUnique(username, Formatters.trimToNull(request.phone()), Formatters.trimToNull(request.email()));
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordService.hash(request.password()));
        user.setNickname(request.nickname().trim());
        user.setPhone(Formatters.trimToNull(request.phone()));
        user.setEmail(Formatters.trimToNull(request.email()));
        user.setStatus(UserStatus.ACTIVE);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            ensureUnique(username, user.getPhone(), user.getEmail());
            throw BusinessException.conflict("RESOURCE_CONFLICT", "用户唯一字段冲突");
        }

        SysRole customer = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, "CUSTOMER")
                .eq(SysRole::getScopeType, ScopeType.PLATFORM)
                .eq(SysRole::getStatus, ActiveStatus.ACTIVE));
        if (customer == null) {
            throw new IllegalStateException("CUSTOMER role is missing");
        }
        SysUserRole assignment = new SysUserRole();
        assignment.setUserId(user.getId());
        assignment.setRoleId(customer.getId());
        assignment.setRoleScope(ScopeType.PLATFORM);
        userRoleMapper.insert(assignment);

        // 通过 B 线端口创建钱包，避免身份模块直接操作 wallet_account 表。
        walletProvision.provision(user.getId());
        events.publishEvent(new UserRegisteredEvent(java.util.UUID.randomUUID().toString(), user.getId(), LocalDateTime.now()));
        log.info("Registered user userId={}", user.getId());
        return user(user);
    }

    public LoginView login(LoginRequest request) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username().trim()));
        if (user == null || !passwordService.matches(request.password(), user.getPasswordHash())) {
            log.warn("User login failed due to invalid credentials");
            throw new BusinessException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "AUTH_INVALID_CREDENTIALS", "用户名或密码错误");
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            throw BusinessException.forbidden("AUTH_ACCOUNT_LOCKED", "账号已锁定");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw BusinessException.forbidden("AUTH_ACCOUNT_DISABLED", "账号不可用");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        StpUtil.login(user.getId());
        log.info("User login succeeded userId={}", user.getId());
        return new LoginView(StpUtil.getTokenName(), StpUtil.getTokenValue(), StpUtil.getTokenTimeout(),
                StpUtil.getTokenActiveTimeout(), user(user));
    }

    public void logout() {
        long userId = currentUser.id();
        StpUtil.logout();
        log.info("User logout succeeded userId={}", userId);
    }

    public CurrentUserView me() {
        SysUser user = currentUser.user();
        List<ShopContextView> shops = shopUserMapper.selectList(new LambdaQueryWrapper<ShopUser>()
                        .eq(ShopUser::getUserId, user.getId())
                        .eq(ShopUser::getStatus, ActiveStatus.ACTIVE))
                .stream().map(member -> toContext(member)).toList();
        return new CurrentUserView(user(user), user.getPhone(), user.getEmail(),
                userMapper.selectPlatformRoles(user.getId()),
                userMapper.selectPlatformPermissions(user.getId()), shops);
    }

    @Transactional
    public CurrentUserView update(UpdateProfileRequest request) {
        if (!request.hasNickname() && !request.hasPhone()
                && !request.hasEmail() && !request.hasAvatarUrl()) {
            throw BusinessException.badRequest("VALIDATION_FAILED", "至少提交一个可修改字段");
        }
        SysUser user = currentUser.user();
        if (request.hasNickname()) {
            if (request.nickname() == null) {
                throw BusinessException.badRequest("VALIDATION_FAILED", "nickname 不允许为 null");
            }
            String nickname = request.nickname().trim();
            if (nickname.isEmpty() || nickname.length() > 64) {
                throw BusinessException.badRequest("VALIDATION_FAILED", "昵称长度必须为 1..64");
            }
            user.setNickname(nickname);
        }
        if (request.hasPhone()) {
            String phone = Formatters.trimToNull(request.phone());
            if (phone != null && (phone.length() < 6 || phone.length() > 32)) {
                throw BusinessException.badRequest("VALIDATION_FAILED", "手机号长度必须为 6..32");
            }
            user.setPhone(phone);
        }
        if (request.hasEmail()) {
            String email = Formatters.trimToNull(request.email());
            if (email != null && (email.length() > 128 || !EMAIL.matcher(email).matches())) {
                throw BusinessException.badRequest("VALIDATION_FAILED", "邮箱格式错误");
            }
            user.setEmail(email);
        }
        if (request.hasAvatarUrl()) {
            user.setAvatarUrl(contentSafety.imageUrl("avatarUrl", request.avatarUrl()));
        }
        ensureUnique(user.getUsername(), user.getPhone(), user.getEmail(), user.getId());
        userMapper.updateById(user);
        return me();
    }

    private ShopContextView toContext(ShopUser member) {
        Shop memberShop = shopMapper.selectById(member.getShopId());
        SysRole role = roleMapper.selectById(member.getRoleId());
        return new ShopContextView(shop(memberShop), role.getRoleCode(),
                shopUserMapper.selectPermissions(member.getShopId(), member.getUserId()));
    }

    private void ensureUnique(String username, String phone, String email) {
        ensureUnique(username, phone, email, null);
    }

    private void ensureUnique(String username, String phone, String email, Long excludedId) {
        checkUnique(SysUser::getUsername, username, excludedId, "USERNAME_ALREADY_EXISTS", "用户名已存在");
        checkUnique(SysUser::getPhone, phone, excludedId, "PHONE_ALREADY_EXISTS", "手机号已存在");
        checkUnique(SysUser::getEmail, email, excludedId, "EMAIL_ALREADY_EXISTS", "邮箱已存在");
    }

    private <T> void checkUnique(com.baomidou.mybatisplus.core.toolkit.support.SFunction<SysUser, T> column,
                                 T value, Long excludedId, String code, String message) {
        if (value == null) {
            return;
        }
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>().eq(column, value);
        if (excludedId != null) {
            query.ne(SysUser::getId, excludedId);
        }
        if (userMapper.exists(query)) {
            throw BusinessException.conflict(code, message);
        }
    }
}
