package org.dhu.shiguang_market.integration.identity;

import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;

/** B 线获取当前用户稳定身份信息的跨线端口。 */
public interface CurrentUserPort {
    CurrentUser current();

    record CurrentUser(long userId, String username, String nickname, UserStatus status) {
    }
}
