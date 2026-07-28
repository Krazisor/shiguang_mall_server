package org.dhu.shiguang_market.common.util;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

public final class RequestContext {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private static Clock clock = Clock.system(ZONE);

    private RequestContext() {
    }

    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    public static String requestId() {
        String value = REQUEST_ID.get();
        return value == null ? UUID.randomUUID().toString() : value;
    }

    public static OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public static void clear() {
        REQUEST_ID.remove();
    }
}
