package org.dhu.shiguang_market.task.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.task.dto.TaskDtos.TaskRunRequest;
import org.dhu.shiguang_market.task.dto.TaskDtos.TaskRunView;
import org.dhu.shiguang_market.task.service.TaskExecutionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部任务手动触发接口。
 *
 * <p>接口默认关闭。开启后仍要求 {@code platform:task:execute}，避免普通登录用户调用运维任务。</p>
 */
@RestController
@RequestMapping("/api/internal/tasks")
public class InternalTaskController {
    private final TaskExecutionService tasks;
    private final CurrentUserService currentUser;
    private final boolean enabled;

    public InternalTaskController(TaskExecutionService tasks, CurrentUserService currentUser,
                                  @Value("${market.internal-task-api-enabled:false}") boolean enabled) {
        this.tasks = tasks;
        this.currentUser = currentUser;
        this.enabled = enabled;
    }

    @PostMapping("/cancel-expired-trades")
    public ApiResponse<TaskRunView> cancelExpiredTrades(@Valid @RequestBody TaskRunRequest request) {
        authorize();
        return ApiResponse.success(tasks.cancelExpiredTrades(request));
    }

    @PostMapping("/complete-shipped-orders")
    public ApiResponse<TaskRunView> completeShippedOrders(@Valid @RequestBody TaskRunRequest request) {
        authorize();
        return ApiResponse.success(tasks.completeShippedOrders(request));
    }

    @PostMapping("/retry-refunds")
    public ApiResponse<TaskRunView> retryRefunds(@Valid @RequestBody TaskRunRequest request) {
        authorize();
        return ApiResponse.success(tasks.retryRefunds(request));
    }

    @PostMapping("/reconcile-inventory")
    public ApiResponse<TaskRunView> reconcileInventory(@Valid @RequestBody TaskRunRequest request) {
        authorize();
        // 当前版本对账固定为只读，忽略客户端传入的 dryRun=false。
        return ApiResponse.success(tasks.reconcileInventory(asDryRun(request)));
    }

    @PostMapping("/reconcile-wallets")
    public ApiResponse<TaskRunView> reconcileWallets(@Valid @RequestBody TaskRunRequest request) {
        authorize();
        return ApiResponse.success(tasks.reconcileWallets(asDryRun(request)));
    }

    private void authorize() {
        if (!enabled) {
            throw BusinessException.notFound("RESOURCE_NOT_FOUND", "请求的资源不存在");
        }
        currentUser.requirePermission("platform:task:execute");
    }

    private TaskRunRequest asDryRun(TaskRunRequest request) {
        return new TaskRunRequest(true, request.batchSize());
    }
}
