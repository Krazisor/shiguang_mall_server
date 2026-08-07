package org.dhu.shiguang_market.task.scheduler;

import org.dhu.shiguang_market.task.dto.TaskDtos.TaskRunRequest;
import org.dhu.shiguang_market.task.service.TaskExecutionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每五分钟重试仍处于 REFUNDING/FAILED 的退款。 */
@Component
public class RetryFailedRefundsTask {
    private final TaskExecutionService tasks;

    public RetryFailedRefundsTask(TaskExecutionService tasks) {
        this.tasks = tasks;
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void execute() {
        tasks.retryRefunds(new TaskRunRequest(false, 100));
    }
}
