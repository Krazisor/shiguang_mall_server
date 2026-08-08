package org.dhu.shiguang_market.identity.service;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Service;

/** 封装 Sa-Token 指定账号踢下线操作，便于平台用户服务独立测试。 */
@Service
public class PlatformUserSessionService {
    /** 将指定账号的全部终端标记为已被踢下线。 */
    public void kickout(long userId) {
        StpUtil.kickout(userId);
    }
}
