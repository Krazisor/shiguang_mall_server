package org.dhu.shiguang_market.integration.identity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dhu.shiguang_market.integration.identity.IdentitySummaryPort.IdentitySummary;

/** 按测试预设返回脱敏用户摘要，结果顺序与输入用户 ID 一致。 */
public class IdentitySummaryPortFake implements IdentitySummaryPort {
    private final Map<Long, IdentitySummary> values = new LinkedHashMap<>();

    public void put(IdentitySummary summary) {
        values.put(summary.userId(), summary);
    }

    @Override
    public List<IdentitySummary> findByIds(List<Long> userIds) {
        if (userIds == null) return List.of();
        return userIds.stream().map(values::get).filter(value -> value != null).toList();
    }
}
