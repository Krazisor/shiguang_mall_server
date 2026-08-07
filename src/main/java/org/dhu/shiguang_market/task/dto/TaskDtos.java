package org.dhu.shiguang_market.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;

/** 阶段五内部任务请求与运行结果。 */
public final class TaskDtos {
    private TaskDtos() {
    }

    public record TaskRunRequest(boolean dryRun, @Min(1) @Max(500) int batchSize) {
    }

    public record TaskRunView(
            String taskName, boolean dryRun, int scanned, int processed,
            int succeeded, int failed, int mismatches,
            OffsetDateTime startedAt, OffsetDateTime finishedAt, String requestId) {
    }
}
