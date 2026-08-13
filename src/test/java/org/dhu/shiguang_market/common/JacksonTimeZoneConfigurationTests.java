package org.dhu.shiguang_market.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.RecurringCouponSchedule;
import tools.jackson.databind.ObjectMapper;

@JsonTest
class JacksonTimeZoneConfigurationTests {
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void usesShanghaiAsContextTimeZone() {
        assertThat(objectMapper.deserializationConfig().getTimeZone().getID()).isEqualTo("Asia/Shanghai");
    }

    @Test
    void preservesOffsetFromOffsetDateTimeJsonValues() throws Exception {
        OffsetDateTime shanghai = objectMapper.readValue("\"2026-08-13T20:37:00+08:00\"", OffsetDateTime.class);
        OffsetDateTime utc = objectMapper.readValue("\"2026-08-13T12:37:00Z\"", OffsetDateTime.class);

        assertThat(shanghai.getOffset()).isEqualTo(ZoneOffset.ofHours(8));
        assertThat(utc.getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void preservesOffsetsInRecurringCouponSchedule() throws Exception {
        RecurringCouponSchedule schedule = objectMapper.readValue("""
                {
                  "recurrenceType":"WEEKLY",
                  "weekdays":[5,6,7],
                  "dailyStartsAt":"20:00:00",
                  "windowDurationMinutes":30,
                  "recurrenceStartsAt":"2026-08-13T20:37:00+08:00",
                  "recurrenceEndsAt":"2026-08-31T00:00:00+08:00",
                  "timezone":"Asia/Shanghai"
                }
                """, RecurringCouponSchedule.class);

        assertThat(schedule.recurrenceStartsAt().getOffset()).isEqualTo(ZoneOffset.ofHours(8));
        assertThat(schedule.recurrenceEndsAt().getOffset()).isEqualTo(ZoneOffset.ofHours(8));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
