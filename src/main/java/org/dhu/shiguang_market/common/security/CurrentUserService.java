package org.dhu.shiguang_market.common.security;

import cn.dev33.satoken.stp.StpUtil;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.dhu.shiguang_market.identity.model.SysUser;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Service
public class CurrentUserService {
    private static final Logger log = LoggerFactory.getLogger(CurrentUserService.class);
    private final SysUserMapper userMapper;

    public CurrentUserService(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public long id() {
        StpUtil.checkLogin();
        long id = StpUtil.getLoginIdAsLong();
        MDC.put("userId", Long.toString(id));
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            StpUtil.logout();
            throw BusinessException.forbidden("AUTH_ACCOUNT_DISABLED", "账号不可用");
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            throw BusinessException.forbidden("AUTH_ACCOUNT_LOCKED", "账号已锁定");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw BusinessException.forbidden("AUTH_ACCOUNT_DISABLED", "账号不可用");
        }
        return id;
    }

    public SysUser user() {
        return userMapper.selectById(id());
    }

    public void requirePermission(String permission) {
        id();
        log.debug("Checking permission permission={}", permission);
        StpUtil.checkPermission(permission);
    }
}
