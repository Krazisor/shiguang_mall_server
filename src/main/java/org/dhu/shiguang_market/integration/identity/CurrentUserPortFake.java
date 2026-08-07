package org.dhu.shiguang_market.integration.identity;

import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;
import org.springframework.http.HttpStatus;

/** 可由测试预设当前用户的轻量 Fake。 */
public class CurrentUserPortFake implements CurrentUserPort {
    private CurrentUser current;

    public void setCurrent(CurrentUser current) {
        this.current = current;
    }

    /** 未配置或账号不可用时，按照项目统一认证错误返回。 */
    @Override
    public CurrentUser current() {
        if (current == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "请先登录");
        }
        if (current.status() == UserStatus.LOCKED) {
            throw BusinessException.forbidden("AUTH_ACCOUNT_LOCKED", "账号已锁定");
        }
        if (current.status() != UserStatus.ACTIVE) {
            throw BusinessException.forbidden("AUTH_ACCOUNT_DISABLED", "账号不可用");
        }
        return current;
    }
}
