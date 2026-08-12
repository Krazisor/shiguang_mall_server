package org.dhu.shiguang_market.aftersale;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AfterSaleAppealMigrationTests {

    @Test
    void followUpMigrationReplacesInvalidAppealTimeConstraint() throws IOException {
        Path migration = Path.of("sql", "scheme7.sql");

        assertThat(migration).isRegularFile();
        String sql = Files.readString(migration);
        assertThat(sql)
                .contains("DROP CHECK `chk_after_sale_appeal_times`")
                .contains("CHECK (decided_at IS NULL OR decided_at >= created_at)")
                .doesNotContain("merchant_reviewed_at >= created_at")
                .doesNotContain("merchant_reviewed_at <= created_at");
    }
}
