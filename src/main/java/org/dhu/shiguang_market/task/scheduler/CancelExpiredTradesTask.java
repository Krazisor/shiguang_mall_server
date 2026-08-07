package org.dhu.shiguang_market.task.scheduler;

import org.dhu.shiguang_market.task.dto.TaskDtos.TaskRunRequest;
import org.dhu.shiguang_market.task.service.TaskExecutionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每分钟取消已超过支付期限的待支付交易。 */
@Component
public class CancelExpiredTradesTask {
    private final TaskExecutionService tasks;

    public CancelExpiredTradesTask(TaskExecutionService tasks) {
        this.tasks = tasks;
    }

    @Scheduled(cron = "0 * * * * *")
    public void execute() {
        tasks.cancelExpiredTrades(new TaskRunRequest(false, 100));
    }
}
