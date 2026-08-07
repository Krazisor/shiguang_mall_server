package org.dhu.shiguang_market.task.scheduler;

import org.dhu.shiguang_market.task.dto.TaskDtos.TaskRunRequest;
import org.dhu.shiguang_market.task.service.TaskExecutionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天凌晨两点执行库存和钱包只读对账，不自动修复数据。 */
@Component
public class ReconciliationTask {
    private final TaskExecutionService tasks;

    public ReconciliationTask(TaskExecutionService tasks) {
        this.tasks = tasks;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void execute() {
        TaskRunRequest request = new TaskRunRequest(true, 100);
        tasks.reconcileInventory(request);
        tasks.reconcileWallets(request);
    }
}
