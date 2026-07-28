package org.dhu.shiguang_market.common.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@Service
public class IdempotencyService {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    private static final Duration LOCK_TTL = Duration.ofMinutes(2);
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public IdempotencyService(StringRedisTemplate redis, ObjectMapper objectMapper,
                              @Value("${market.idempotency.ttl-hours:24}") long ttlHours) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofHours(ttlHours);
    }

    public <T> T execute(long userId, String method, String path, String key, Object request,
                         Class<T> responseType, Supplier<T> action) {
        Scope scope = verify(userId, method, path, key, request);
        String lockToken = acquire(scope);
        try {
            T replay;
            try {
                replay = replay(scope, responseType);
            } catch (BusinessException ex) {
                release(scope, lockToken);
                throw ex;
            }
            if (replay != null) {
                release(scope, lockToken);
                return replay;
            }
            try {
                T result = action.get();
                completeAfterCommit(scope, lockToken, StoredResponse.success(serialize(result)));
                return result;
            } catch (BusinessException ex) {
                if (ex.getStatus().is5xxServerError()) {
                    releaseAfterCompletion(scope, lockToken);
                } else {
                    completeFailureAfterCompletion(scope, lockToken, StoredResponse.failure(
                            ex.getStatus().value(), ex.getCode(), ex.getMessage()));
                }
                throw ex;
            } catch (RuntimeException ex) {
                releaseAfterCompletion(scope, lockToken);
                throw ex;
            }
        } catch (RuntimeException ex) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                release(scope, lockToken);
            }
            throw ex;
        }
    }

    private Scope verify(long userId, String method, String path, String key, Object request) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw BusinessException.badRequest("BAD_REQUEST", "Idempotency-Key 格式错误");
        }
        String baseKey = "market:idem:" + userId + ":" + method + ":" + path + ":" + key;
        Scope scope = new Scope(baseKey + ":request", baseKey + ":lock", baseKey + ":response");
        String digest = digest(request);
        try {
            Boolean created = redis.opsForValue().setIfAbsent(scope.requestKey(), digest, ttl);
            if (Boolean.FALSE.equals(created)) {
                String previous = redis.opsForValue().get(scope.requestKey());
                if (previous != null && !previous.equals(digest)) {
                    throw BusinessException.conflict("IDEMPOTENCY_KEY_REUSED", "幂等键已用于不同请求");
                }
            }
        } catch (RedisConnectionFailureException ex) {
            throw unavailable();
        }
        return scope;
    }

    public String businessNo(String prefix, long userId, String key) {
        return prefix + digest(userId + ":" + key).substring(0, 30).toUpperCase();
    }

    private String acquire(Scope scope) {
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + WAIT_TIMEOUT.toNanos();
        try {
            while (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(scope.lockKey(), token, LOCK_TTL))) {
                if (redis.opsForValue().get(scope.responseKey()) != null) {
                    return token;
                }
                if (System.nanoTime() >= deadline) {
                    throw unavailable();
                }
                Thread.sleep(50);
            }
            return token;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (RedisConnectionFailureException ex) {
            throw unavailable();
        }
    }

    private <T> T replay(Scope scope, Class<T> responseType) {
        try {
            String value = redis.opsForValue().get(scope.responseKey());
            if (value == null) {
                return null;
            }
            StoredResponse stored = objectMapper.readValue(value, StoredResponse.class);
            if (!stored.success()) {
                throw new BusinessException(HttpStatus.valueOf(stored.httpStatus()), stored.code(), stored.message());
            }
            return stored.body() == null ? null : objectMapper.readValue(stored.body(), responseType);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RedisConnectionFailureException ex) {
            throw unavailable();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot restore idempotency response", ex);
        }
    }

    private void completeAfterCommit(Scope scope, String token, StoredResponse response) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            save(scope, response);
            release(scope, token);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                save(scope, response);
            }

            @Override
            public void afterCompletion(int status) {
                release(scope, token);
            }
        });
    }

    private void releaseAfterCompletion(Scope scope, String token) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            release(scope, token);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                release(scope, token);
            }
        });
    }

    private void completeFailureAfterCompletion(Scope scope, String token, StoredResponse response) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            save(scope, response);
            release(scope, token);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                save(scope, response);
                release(scope, token);
            }
        });
    }

    private void save(Scope scope, StoredResponse response) {
        try {
            redis.opsForValue().set(scope.responseKey(), objectMapper.writeValueAsString(response), ttl);
        } catch (Exception ex) {
            log.warn("Unable to cache final idempotency response for key {}", scope.responseKey(), ex);
        }
    }

    private void release(Scope scope, String token) {
        try {
            redis.execute(RELEASE_LOCK, List.of(scope.lockKey()), token);
        } catch (RuntimeException ex) {
            log.warn("Unable to release idempotency lock for key {}", scope.lockKey(), ex);
        }
    }

    private String serialize(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot serialize idempotency response", ex);
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "DEPENDENCY_UNAVAILABLE", "幂等服务暂时不可用");
    }

    private String digest(Object value) {
        try {
            byte[] serialized = value instanceof String text
                    ? text.getBytes(StandardCharsets.UTF_8)
                    : objectMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot hash idempotency request", ex);
        }
    }

    private record Scope(String requestKey, String lockKey, String responseKey) {
    }

    private record StoredResponse(boolean success, int httpStatus, String code, String message, String body) {
        private static StoredResponse success(String body) {
            return new StoredResponse(true, HttpStatus.OK.value(), null, null, body);
        }

        private static StoredResponse failure(int status, String code, String message) {
            return new StoredResponse(false, status, code, message, null);
        }
    }
}
