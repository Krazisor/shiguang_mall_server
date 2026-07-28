package org.dhu.shiguang_market.common.security;

import cn.dev33.satoken.stp.StpInterface;
import java.util.List;
import org.dhu.shiguang_market.identity.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

@Component
public class MarketStpInterface implements StpInterface {
    private final SysUserMapper userMapper;

    public MarketStpInterface(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return userMapper.selectPlatformPermissions(Long.parseLong(loginId.toString()));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return userMapper.selectPlatformRoles(Long.parseLong(loginId.toString()));
    }
}
