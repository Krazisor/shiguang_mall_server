package org.dhu.shiguang_market.task.scheduler;

import org.dhu.shiguang_market.task.dto.TaskDtos.TaskRunRequest;
import org.dhu.shiguang_market.task.service.TaskExecutionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每分钟将超过有效期的待处理支付单标记为过期。 */
@Component
public class ExpirePaymentOrdersTask {
    private final TaskExecutionService tasks;

    public ExpirePaymentOrdersTask(TaskExecutionService tasks) {
        this.tasks = tasks;
    }

    @Scheduled(cron = "0 * * * * *")
    public void execute() {
        tasks.expirePaymentOrders(new TaskRunRequest(false, 100));
    }
}
