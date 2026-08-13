package org.dhu.shiguang_market.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.ClaimableActivitySummaryView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponActivityAdminView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponActivityScheduleView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CreateRecurringCouponActivityRequest;
import org.junit.jupiter.api.Test;

class CouponScheduleDtoContractTests {
    @Test
    void scheduleUsesAnIndependentViewWithoutChangingExistingActivityViews() {
        assertThat(fields(CouponActivityScheduleView.class))
                .containsExactly("scheduleType", "campaignStartsAt", "campaignEndsAt", "recurrence",
                        "window", "serverTime", "version");
        assertThat(fields(CouponActivityAdminView.class))
                .doesNotContain("schedule", "scheduleType", "window", "serverTime");
        assertThat(fields(ClaimableActivitySummaryView.class))
                .doesNotContain("scheduleType", "window");
    }

    @Test
    void recurringCreateRequestDoesNotAcceptServerControlledActivityFields() {
        assertThat(fields(CreateRecurringCouponActivityRequest.class))
                .containsExactly("activityName", "subtitle", "bannerUrl", "recurrence")
                .doesNotContain("activityType", "ownerType", "shopId", "startsAt", "endsAt", "status", "version");
    }

    private static java.util.List<String> fields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(component -> component.getName()).toList();
    }
}
