package org.dhu.shiguang_market.phasefive;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import org.dhu.shiguang_market.common.exception.GlobalExceptionHandler;
import org.dhu.shiguang_market.common.security.CurrentUserService;
import org.dhu.shiguang_market.task.controller.InternalTaskController;
import org.dhu.shiguang_market.task.dto.TaskDtos.TaskRunRequest;
import org.dhu.shiguang_market.task.dto.TaskDtos.TaskRunView;
import org.dhu.shiguang_market.task.service.TaskExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 阶段五内部任务 HTTP 接口测试。 */
class InternalTaskControllerTests {
    private final TaskExecutionService tasks = mock(TaskExecutionService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private MockMvc enabledMvc;

    @BeforeEach
    void setUp() {
        TaskRunView result = new TaskRunView("test", false, 1, 1, 1, 0, 0,
                OffsetDateTime.now(), OffsetDateTime.now(), "request-id");
        when(tasks.cancelExpiredTrades(any())).thenReturn(result);
        when(tasks.completeShippedOrders(any())).thenReturn(result);
        when(tasks.retryRefunds(any())).thenReturn(result);
        when(tasks.reconcileInventory(any())).thenReturn(result);
        when(tasks.reconcileWallets(any())).thenReturn(result);
        enabledMvc = mvc(new InternalTaskController(tasks, currentUser, true));
    }

    /** 显式开关关闭时接口按资源不存在处理，并且不进入权限和任务逻辑。 */
    @Test
    void disabledInternalApiReturns404() throws Exception {
        MockMvc disabledMvc = mvc(new InternalTaskController(tasks, currentUser, false));

        disabledMvc.perform(post("/api/internal/tasks/cancel-expired-trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dryRun\":false,\"batchSize\":100}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        verify(currentUser, never()).requirePermission(any());
    }

    /** 开关开启后五个接口均校验 platform:task:execute 并调用对应任务。 */
    @Test
    void enabledInternalApiDelegatesAllFiveTasks() throws Exception {
        perform("cancel-expired-trades");
        perform("complete-shipped-orders");
        perform("retry-refunds");
        perform("reconcile-inventory");
        perform("reconcile-wallets");

        verify(currentUser, org.mockito.Mockito.times(5))
                .requirePermission("platform:task:execute");
        verify(tasks).cancelExpiredTrades(any(TaskRunRequest.class));
        verify(tasks).completeShippedOrders(any(TaskRunRequest.class));
        verify(tasks).retryRefunds(any(TaskRunRequest.class));
        verify(tasks).reconcileInventory(any(TaskRunRequest.class));
        verify(tasks).reconcileWallets(any(TaskRunRequest.class));
    }

    /** 请求批次超出 1..500 时由 Bean Validation 返回统一 400。 */
    @Test
    void invalidBatchSizeReturns400() throws Exception {
        enabledMvc.perform(post("/api/internal/tasks/retry-refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dryRun\":false,\"batchSize\":501}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private void perform(String task) throws Exception {
        enabledMvc.perform(post("/api/internal/tasks/" + task)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dryRun\":false,\"batchSize\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    private static MockMvc mvc(InternalTaskController controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
