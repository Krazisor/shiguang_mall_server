package org.dhu.shiguang_market.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponTemplateStatus;
import org.junit.jupiter.api.Test;

class CouponArchiveMigrationTests {
    @Test
    void addsArchivedTemplateStatusAfterSchemeEight() throws IOException {
        Path migration = Path.of("sql", "scheme9.sql");

        assertThat(CouponTemplateStatus.valueOf("ARCHIVED")).isNotNull();
        assertThat(migration).isRegularFile();
        assertThat(Files.readString(migration))
                .contains("DROP CHECK chk_coupon_template_status")
                .contains("'DRAFT', 'ACTIVE', 'PAUSED', 'ENDED', 'ARCHIVED'");
    }
}
