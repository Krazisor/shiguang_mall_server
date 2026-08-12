package org.dhu.shiguang_market.coupon.service;

import java.time.Duration;
import java.util.List;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Redis-backed request limits. MySQL remains authoritative for issuance and budget capacity. */
@Service
public class CouponRateLimitService {
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
            return current
            """, Long.class);

    private final StringRedisTemplate redis;

    public CouponRateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void claim(long userId, long templateId, boolean flash) {
        if (flash) {
            require("flash:second:" + userId + ":" + templateId, 3, Duration.ofSeconds(1));
            require("flash:minute:" + userId + ":" + templateId, 20, Duration.ofMinutes(1));
        } else {
            require("claim:" + userId + ":" + templateId, 5, Duration.ofSeconds(10));
        }
    }

    public void redeem(long userId) {
        require("redeem:" + userId, 10, Duration.ofMinutes(10));
    }

    public void quote(long userId) {
        require("quote:" + userId, 30, Duration.ofMinutes(1));
    }

    public void managementGrant(long operatorId, long templateId) {
        require("grant:" + operatorId + ":" + templateId, 5, Duration.ofMinutes(1));
    }

    private void require(String suffix, long limit, Duration window) {
        try {
            Long count = redis.execute(INCREMENT, List.of("market:coupon:rate:" + suffix),
                    Long.toString(limit), Long.toString(window.toMillis()));
            if (count == null) {
                throw BusinessException.unavailable("DEPENDENCY_UNAVAILABLE", "优惠券风控服务暂时不可用");
            }
            if (count > limit) {
                throw BusinessException.tooManyRequests("TOO_MANY_REQUESTS", "请求过于频繁，请稍后重试");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw BusinessException.unavailable("DEPENDENCY_UNAVAILABLE", "优惠券风控服务暂时不可用");
        }
    }
}
