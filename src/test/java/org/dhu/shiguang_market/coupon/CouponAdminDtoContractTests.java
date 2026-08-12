package org.dhu.shiguang_market.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponAdminScopeView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponTemplateAdminDetailView;
import org.junit.jupiter.api.Test;

class CouponAdminDtoContractTests {
    @Test
    void managementTemplateDetailUsesDocumentedFlatFields() {
        assertThat(Arrays.stream(CouponTemplateAdminDetailView.class.getRecordComponents())
                .map(component -> component.getName()).toList())
                .contains("id", "templateNo", "activity", "scope", "status", "version", "availableActions")
                .doesNotContain("summary", "template");
    }

    @Test
    void managementScopeIncludesBoundedTargetCount() {
        assertThat(Arrays.stream(CouponAdminScopeView.class.getRecordComponents())
                .map(component -> component.getName()).toList())
                .contains("targets", "targetCount");
    }
}
