package org.dhu.shiguang_market.coupon.event;

import java.time.LocalDateTime;

public record UserRegisteredEvent(String eventId, long userId, LocalDateTime registeredAt) {
}
