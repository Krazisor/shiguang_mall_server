package org.dhu.shiguang_market.integration.identity;

import java.util.List;
import org.dhu.shiguang_market.common.model.MarketEnums.UserStatus;

/** 为运营列表批量提供不含联系方式的用户摘要。 */
public interface IdentitySummaryPort {
    List<IdentitySummary> findByIds(List<Long> userIds);

    record IdentitySummary(
            long userId, String username, String nickname, String avatarUrl, UserStatus status) {
    }
}
