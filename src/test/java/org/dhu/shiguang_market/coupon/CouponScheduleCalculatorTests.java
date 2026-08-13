package org.dhu.shiguang_market.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRecurrenceType;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.RecurringCouponSchedule;
import org.dhu.shiguang_market.coupon.service.CouponScheduleCalculator;
import org.junit.jupiter.api.Test;

class CouponScheduleCalculatorTests {
    private final CouponScheduleCalculator calculator = new CouponScheduleCalculator();

    @Test
    void calculatesWeeklyCampaignBoundariesAndCurrentWindow() {
        RecurringCouponSchedule schedule = weekly(List.of(5, 6, 7),
                "2026-08-14T00:00:00+08:00", "2026-10-01T00:00:00+08:00", 30);

        var bounds = calculator.validateAndCalculateBounds(schedule);
        var window = calculator.window(schedule, OffsetDateTime.parse("2026-08-15T20:10:00+08:00"));

        assertThat(bounds.startsAt()).isEqualTo(OffsetDateTime.parse("2026-08-14T20:00:00+08:00"));
        assertThat(bounds.endsAt()).isEqualTo(OffsetDateTime.parse("2026-09-27T20:30:00+08:00"));
        assertThat(window.currentWindow().startsAt())
                .isEqualTo(OffsetDateTime.parse("2026-08-15T20:00:00+08:00"));
        assertThat(window.nextWindow().startsAt())
                .isEqualTo(OffsetDateTime.parse("2026-08-16T20:00:00+08:00"));
    }

    @Test
    void skipsMissingMonthDaysWithoutFallingBackToMonthEnd() {
        RecurringCouponSchedule schedule = new RecurringCouponSchedule(CouponRecurrenceType.MONTHLY, null,
                List.of(29, 30, 31), "20:00:00", 30,
                OffsetDateTime.parse("2027-02-01T00:00:00+08:00"),
                OffsetDateTime.parse("2027-05-01T00:00:00+08:00"), "Asia/Shanghai");

        var bounds = calculator.validateAndCalculateBounds(schedule);

        assertThat(bounds.startsAt()).isEqualTo(OffsetDateTime.parse("2027-03-29T20:00:00+08:00"));
        assertThat(bounds.endsAt()).isEqualTo(OffsetDateTime.parse("2027-04-30T20:30:00+08:00"));
    }

    @Test
    void treatsWindowAsLeftClosedAndRightOpenAcrossMidnight() {
        RecurringCouponSchedule schedule = weekly(List.of(7),
                "2026-08-16T00:00:00+08:00", "2026-08-24T00:00:00+08:00", 120,
                "23:30:00");

        assertThat(calculator.isOpen(schedule, OffsetDateTime.parse("2026-08-16T23:30:00+08:00"))).isTrue();
        assertThat(calculator.isOpen(schedule, OffsetDateTime.parse("2026-08-17T01:29:59+08:00"))).isTrue();
        assertThat(calculator.isOpen(schedule, OffsetDateTime.parse("2026-08-17T01:30:00+08:00"))).isFalse();
    }

    @Test
    void rejectsWrongTimezoneAndSchedulesWithoutACompleteWindow() {
        RecurringCouponSchedule wrongZone = weekly(List.of(5),
                "2026-08-14T00:00:00Z", "2026-08-15T00:00:00Z", 30);
        RecurringCouponSchedule noWindow = weekly(List.of(7),
                "2026-08-14T21:00:00+08:00", "2026-08-15T00:00:00+08:00", 30);

        assertThatThrownBy(() -> calculator.validateAndCalculateBounds(wrongZone))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode()).isEqualTo("VALIDATION_FAILED");
        assertThatThrownBy(() -> calculator.validateAndCalculateBounds(noWindow))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode()).isEqualTo("VALIDATION_FAILED");
    }

    private RecurringCouponSchedule weekly(List<Integer> weekdays, String startsAt, String endsAt, int duration) {
        return weekly(weekdays, startsAt, endsAt, duration, "20:00:00");
    }

    private RecurringCouponSchedule weekly(List<Integer> weekdays, String startsAt, String endsAt,
                                            int duration, String dailyStartsAt) {
        return new RecurringCouponSchedule(CouponRecurrenceType.WEEKLY, weekdays, null,
                dailyStartsAt, duration, OffsetDateTime.parse(startsAt),
                OffsetDateTime.parse(endsAt), "Asia/Shanghai");
    }
}
