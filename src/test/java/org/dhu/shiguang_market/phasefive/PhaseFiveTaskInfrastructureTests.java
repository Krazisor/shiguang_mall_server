package org.dhu.shiguang_market.phasefive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
import java.time.Duration;
import java.util.List;
import org.dhu.shiguang_market.task.dto.TaskDtos.TaskRunRequest;
import org.dhu.shiguang_market.task.scheduler.CancelExpiredTradesTask;
import org.dhu.shiguang_market.task.scheduler.CompleteShippedOrdersTask;
import org.dhu.shiguang_market.task.scheduler.ExpirePaymentOrdersTask;
import org.dhu.shiguang_market.task.scheduler.ReconciliationTask;
import org.dhu.shiguang_market.task.scheduler.RetryFailedRefundsTask;
import org.dhu.shiguang_market.task.service.TaskLockService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;

/** 阶段五任务 DTO、Redis 锁和调度配置测试。 */
class PhaseFiveTaskInfrastructureTests {

    /** batchSize 只允许 1..500，避免一次任务扫描过多数据。 */
    @Test
    void validatesTaskBatchSizeRange() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(new TaskRunRequest(false, 100))).isEmpty();
            assertThat(validator.validate(new TaskRunRequest(false, 0))).isNotEmpty();
            assertThat(validator.validate(new TaskRunRequest(false, 501))).isNotEmpty();
        }
    }

    /** Redis SETNX 成功才表示当前实例获得任务执行权。 */
    @Test
    void distributedLockUsesSetIfAbsentWithExpiry() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("task:cancel-expired-trades", "token", Duration.ofSeconds(120)))
                .thenReturn(true);

        TaskLockService lock = new TaskLockService(redis);

        assertThat(lock.tryLock("cancel-expired-trades", "token")).isTrue();
    }

    /** 五个任务的 cron 必须与 B 线实施计划一致。 */
    @Test
    void schedulersUsePlannedCronExpressions() throws Exception {
        assertCron(CancelExpiredTradesTask.class, "0 * * * * *");
        assertCron(CompleteShippedOrdersTask.class, "0 0 * * * *");
        assertCron(ExpirePaymentOrdersTask.class, "0 * * * * *");
        assertCron(RetryFailedRefundsTask.class, "0 */5 * * * *");
        assertCron(ReconciliationTask.class, "0 0 2 * * *");
    }

    private static void assertCron(Class<?> taskType, String expected) throws Exception {
        Scheduled scheduled = taskType.getDeclaredMethod("execute").getAnnotation(Scheduled.class);
        assertThat(scheduled).as(taskType.getSimpleName()).isNotNull();
        assertThat(List.of(scheduled.cron())).containsExactly(expected);
    }
}
