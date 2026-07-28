package org.dhu.shiguang_market.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class Formatters {
    public static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);

    private Formatters() {
    }

    public static String id(Long value) {
        return value == null ? null : value.toString();
    }

    public static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static OffsetDateTime time(LocalDateTime value) {
        return value == null ? null : value.atOffset(OFFSET);
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
