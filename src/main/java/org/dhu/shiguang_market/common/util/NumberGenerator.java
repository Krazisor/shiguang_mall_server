package org.dhu.shiguang_market.common.util;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class NumberGenerator {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicLong sequence = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    public String next(String prefix) {
        long suffix = sequence.updateAndGet(value -> (value + 1) % 1_000_000);
        return prefix + RequestContext.now().format(DAY) + String.format("%06d", suffix);
    }
}
