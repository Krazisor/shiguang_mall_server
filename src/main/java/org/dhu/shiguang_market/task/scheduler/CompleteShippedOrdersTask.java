package org.dhu.shiguang_market.task.scheduler;

import org.dhu.shiguang_market.task.dto.TaskDtos.TaskRunRequest;
import org.dhu.shiguang_market.task.service.TaskExecutionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每小时自动完成已发货满七天且不存在活跃售后的订单。 */
@Component
public class CompleteShippedOrdersTask {
    private final TaskExecutionService tasks;

    public CompleteShippedOrdersTask(TaskExecutionService tasks) {
        this.tasks = tasks;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void execute() {
        tasks.completeShippedOrders(new TaskRunRequest(false, 100));
    }
}
