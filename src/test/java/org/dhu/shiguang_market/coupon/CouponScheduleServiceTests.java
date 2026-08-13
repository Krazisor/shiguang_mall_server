package org.dhu.shiguang_market.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponClaimWindowStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRecurrenceType;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponScheduleType;
import org.dhu.shiguang_market.coupon.mapper.CouponActivityRecurrenceMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponActivity;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponActivityRecurrence;
import org.dhu.shiguang_market.coupon.service.CouponScheduleCalculator;
import org.dhu.shiguang_market.coupon.service.CouponScheduleService;
import org.junit.jupiter.api.Test;

class CouponScheduleServiceTests {
    private final CouponActivityRecurrenceMapper mapper = mock(CouponActivityRecurrenceMapper.class);
    private final CouponScheduleService service = new CouponScheduleService(mapper, new CouponScheduleCalculator());

    @Test
    void returnsOnceScheduleForExistingActivitiesWithoutARecurrenceRow() {
        CouponActivity activity = activity(LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2),
                CouponActivityStatus.SCHEDULED);
        when(mapper.selectById(activity.getId())).thenReturn(null);

        var view = service.view(activity);

        assertThat(view.scheduleType()).isEqualTo(CouponScheduleType.ONCE);
        assertThat(view.recurrence()).isNull();
        assertThat(view.window().status()).isEqualTo(CouponClaimWindowStatus.WAITING);
        assertThat(view.window().nextWindow()).isNotNull();
    }

    @Test
    void pausedRecurringActivityNeverReportsAnOpenWindow() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        CouponActivity activity = activity(now.minusMinutes(5), now.plusDays(1), CouponActivityStatus.PAUSED);
        CouponActivityRecurrence recurrence = recurrence(activity.getId(), now.minusDays(1), now.plusDays(1),
                now.toLocalTime());
        when(mapper.selectById(activity.getId())).thenReturn(recurrence);

        var view = service.view(activity);

        assertThat(view.scheduleType()).isEqualTo(CouponScheduleType.RECURRING);
        assertThat(view.window().status()).isEqualTo(CouponClaimWindowStatus.PAUSED);
        assertThat(view.window().currentWindow()).isNotNull();
    }

    @Test
    void endedRecurringActivityHidesAllWindows() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        CouponActivity activity = activity(now.minusDays(2), now.minusDays(1), CouponActivityStatus.ENDED);
        CouponActivityRecurrence recurrence = recurrence(activity.getId(), now.minusDays(3), now.plusDays(1),
                now.toLocalTime());
        when(mapper.selectById(activity.getId())).thenReturn(recurrence);

        var view = service.view(activity);

        assertThat(view.window().status()).isEqualTo(CouponClaimWindowStatus.ENDED);
        assertThat(view.window().currentWindow()).isNull();
        assertThat(view.window().nextWindow()).isNull();
    }

    @Test
    void draftActivityDoesNotReportAnOpenWindow() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        CouponActivity activity = activity(now.minusMinutes(5), now.plusDays(1), CouponActivityStatus.DRAFT);
        CouponActivityRecurrence recurrence = recurrence(activity.getId(), now.minusDays(1), now.plusDays(1),
                now.toLocalTime());
        when(mapper.selectById(activity.getId())).thenReturn(recurrence);

        var view = service.view(activity);

        assertThat(view.window().status()).isEqualTo(CouponClaimWindowStatus.WAITING);
        assertThat(view.window().currentWindow()).isNull();
    }

    private CouponActivity activity(LocalDateTime startsAt, LocalDateTime endsAt, CouponActivityStatus status) {
        CouponActivity activity = new CouponActivity();
        activity.setId(3001L);
        activity.setStartsAt(startsAt);
        activity.setEndsAt(endsAt);
        activity.setStatus(status);
        activity.setVersion(2);
        return activity;
    }

    private CouponActivityRecurrence recurrence(long activityId, LocalDateTime startsAt,
                                                LocalDateTime endsAt, LocalTime dailyStartsAt) {
        CouponActivityRecurrence recurrence = new CouponActivityRecurrence();
        recurrence.setActivityId(activityId);
        recurrence.setRecurrenceType(CouponRecurrenceType.DAILY);
        recurrence.setWeekdaysJson(null);
        recurrence.setMonthDaysJson(null);
        recurrence.setDailyStartsAt(dailyStartsAt);
        recurrence.setWindowDurationMinutes(30);
        recurrence.setRecurrenceStartsAt(startsAt);
        recurrence.setRecurrenceEndsAt(endsAt);
        recurrence.setTimezone("Asia/Shanghai");
        return recurrence;
    }
}
