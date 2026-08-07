package org.dhu.shiguang_market.task.service;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** 使用 Redis SETNX 保证同一个任务同一时刻只由一个实例执行。 */
@Service
public class TaskLockService {
    private static final Duration LOCK_TTL = Duration.ofSeconds(120);
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private final StringRedisTemplate redis;

    public TaskLockService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 原子写入带过期时间的锁，防止应用异常后锁永久残留。 */
    public boolean tryLock(String taskName, String token) {
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(key(taskName), token, LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    /** 只允许持有相同 token 的执行者释放锁，避免误删其他实例的新锁。 */
    public void unlock(String taskName, String token) {
        redis.execute(UNLOCK_SCRIPT, List.of(key(taskName)), token);
    }

    private String key(String taskName) {
        return "task:" + taskName;
    }
}
