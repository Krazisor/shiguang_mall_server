package org.dhu.shiguang_market.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class IdempotencyServiceTests {
    private final Map<String, String> redisState = new ConcurrentHashMap<>();
    private IdempotencyService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(invocation ->
                redisState.putIfAbsent(invocation.getArgument(0), invocation.getArgument(1)) == null);
        when(values.get(anyString())).thenAnswer(invocation -> redisState.get(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
            redisState.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.execute(any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            var keys = (java.util.List<String>) invocation.getArgument(1);
            redisState.remove(keys.getFirst());
            return 1L;
        });
        service = new IdempotencyService(redis, new ObjectMapper(), 24);
    }

    @Test
    void replaysTheFirstSuccessfulResultWithoutExecutingTwice() {
        AtomicInteger executions = new AtomicInteger();

        TestResponse first = service.execute(7, "POST", "/api/test", "same-key", Map.of("value", 1),
                TestResponse.class, () -> new TestResponse(executions.incrementAndGet(), "created"));
        TestResponse replay = service.execute(7, "POST", "/api/test", "same-key", Map.of("value", 1),
                TestResponse.class, () -> new TestResponse(executions.incrementAndGet(), "duplicate"));

        assertThat(first).isEqualTo(new TestResponse(1, "created"));
        assertThat(replay).isEqualTo(first);
        assertThat(executions).hasValue(1);
    }

    @Test
    void rejectsTheSameKeyForADifferentRequest() {
        service.execute(7, "POST", "/api/test", "same-key", Map.of("value", 1),
                TestResponse.class, () -> new TestResponse(1, "created"));

        assertThatThrownBy(() -> service.execute(7, "POST", "/api/test", "same-key", Map.of("value", 2),
                TestResponse.class, () -> new TestResponse(2, "duplicate")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void replaysDeterministicBusinessFailures() {
        AtomicInteger executions = new AtomicInteger();

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> service.execute(7, "POST", "/api/test/failure", "failure-key",
                    "request", TestResponse.class, () -> {
                        executions.incrementAndGet();
                        throw BusinessException.unprocessable("DETERMINISTIC_FAILURE", "fixed result");
                    })).isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getCode()).isEqualTo("DETERMINISTIC_FAILURE");
                        assertThat(ex.getMessage()).isEqualTo("fixed result");
                    });
        }

        assertThat(executions).hasValue(1);
    }

    @Test
    void doesNotCacheTemporaryServerFailures() {
        AtomicInteger executions = new AtomicInteger();

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> service.execute(7, "POST", "/api/test/temporary", "temporary-key",
                    "request", TestResponse.class, () -> {
                        executions.incrementAndGet();
                        throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                                "DEPENDENCY_UNAVAILABLE", "retry later");
                    })).isInstanceOfSatisfying(BusinessException.class,
                    ex -> assertThat(ex.getCode()).isEqualTo("DEPENDENCY_UNAVAILABLE"));
        }

        assertThat(executions).hasValue(2);
    }

    private record TestResponse(int id, String value) {
    }
}
