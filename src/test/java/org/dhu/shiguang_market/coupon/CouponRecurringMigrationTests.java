package org.dhu.shiguang_market.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CouponRecurringMigrationTests {
    @Test
    void createsDedicatedRecurringScheduleTableAfterSchemeSeven() throws IOException {
        Path migration = Path.of("sql", "scheme8.sql");

        assertThat(migration).isRegularFile();
        String sql = Files.readString(migration);
        assertThat(sql)
                .contains("CREATE TABLE coupon_activity_recurrence")
                .contains("PRIMARY KEY (activity_id)")
                .contains("FOREIGN KEY (activity_id) REFERENCES coupon_activity(id)")
                .contains("CHECK (timezone = 'Asia/Shanghai')")
                .contains("recurrence_type = 'WEEKLY' AND weekdays_json IS NOT NULL")
                .contains("recurrence_type = 'MONTHLY' AND weekdays_json IS NULL AND month_days_json IS NOT NULL");
    }
}
