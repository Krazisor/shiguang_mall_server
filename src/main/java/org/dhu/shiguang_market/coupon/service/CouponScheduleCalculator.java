package org.dhu.shiguang_market.coupon.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponRecurrenceType;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponClaimWindowPeriodView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.RecurringCouponSchedule;
import org.springframework.stereotype.Component;

@Component
public class CouponScheduleCalculator {
    public static final String TIMEZONE = "Asia/Shanghai";
    public static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public CampaignBounds validateAndCalculateBounds(RecurringCouponSchedule schedule) {
        validate(schedule);
        List<CouponClaimWindowPeriodView> windows = windows(schedule);
        if (windows.isEmpty()) invalid("周期生效范围内必须至少包含一个完整抢券窗口");
        return new CampaignBounds(windows.getFirst().startsAt(), windows.getLast().endsAt());
    }

    public WindowResult window(RecurringCouponSchedule schedule, OffsetDateTime now) {
        validate(schedule);
        OffsetDateTime businessNow = now.withOffsetSameInstant(OFFSET);
        CouponClaimWindowPeriodView current = null;
        CouponClaimWindowPeriodView next = null;
        for (CouponClaimWindowPeriodView candidate : windows(schedule)) {
            if (!businessNow.isBefore(candidate.startsAt()) && businessNow.isBefore(candidate.endsAt())) {
                current = candidate;
            } else if (candidate.startsAt().isAfter(businessNow)) {
                next = candidate;
                break;
            }
        }
        return new WindowResult(current, next);
    }

    public boolean isOpen(RecurringCouponSchedule schedule, OffsetDateTime now) {
        return window(schedule, now).currentWindow() != null;
    }

    public RecurringCouponSchedule normalize(RecurringCouponSchedule schedule) {
        validate(schedule);
        return new RecurringCouponSchedule(schedule.recurrenceType(), sorted(schedule.weekdays()),
                sorted(schedule.monthDays()), parseTime(schedule.dailyStartsAt()).format(TIME_FORMAT),
                schedule.windowDurationMinutes(), schedule.recurrenceStartsAt().withOffsetSameInstant(OFFSET),
                schedule.recurrenceEndsAt().withOffsetSameInstant(OFFSET), TIMEZONE);
    }

    private List<CouponClaimWindowPeriodView> windows(RecurringCouponSchedule schedule) {
        LocalTime startsAt = parseTime(schedule.dailyStartsAt());
        OffsetDateTime lower = schedule.recurrenceStartsAt().withOffsetSameInstant(OFFSET);
        OffsetDateTime upper = schedule.recurrenceEndsAt().withOffsetSameInstant(OFFSET);
        List<CouponClaimWindowPeriodView> result = new ArrayList<>();
        for (LocalDate date = lower.toLocalDate(); !date.isAfter(upper.toLocalDate()); date = date.plusDays(1)) {
            if (!matches(schedule, date)) continue;
            OffsetDateTime start = date.atTime(startsAt).atOffset(OFFSET);
            OffsetDateTime end = start.plusMinutes(schedule.windowDurationMinutes());
            if (!start.isBefore(lower) && !end.isAfter(upper)) {
                result.add(new CouponClaimWindowPeriodView(start, end));
            }
        }
        return result;
    }

    private boolean matches(RecurringCouponSchedule schedule, LocalDate date) {
        return switch (schedule.recurrenceType()) {
            case DAILY -> true;
            case WEEKLY -> schedule.weekdays().contains(date.getDayOfWeek().getValue());
            case MONTHLY -> schedule.monthDays().contains(date.getDayOfMonth());
        };
    }

    private void validate(RecurringCouponSchedule schedule) {
        if (schedule == null || schedule.recurrenceType() == null || schedule.windowDurationMinutes() == null
                || schedule.recurrenceStartsAt() == null || schedule.recurrenceEndsAt() == null) {
            invalid("周期规则必填字段不完整");
        }
        if (!TIMEZONE.equals(schedule.timezone())
                || !OFFSET.equals(schedule.recurrenceStartsAt().getOffset())
                || !OFFSET.equals(schedule.recurrenceEndsAt().getOffset())) {
            invalid("时区必须为 Asia/Shanghai 且时间偏移必须为 +08:00");
        }
        if (!schedule.recurrenceEndsAt().isAfter(schedule.recurrenceStartsAt())
                || Duration.between(schedule.recurrenceStartsAt(), schedule.recurrenceEndsAt())
                .compareTo(Duration.ofDays(366)) > 0) {
            invalid("周期生效范围必须有效且最长为 366 天");
        }
        if (schedule.windowDurationMinutes() < 1 || schedule.windowDurationMinutes() > 1440) {
            invalid("窗口时长必须为 1..1440 分钟");
        }
        parseTime(schedule.dailyStartsAt());
        validateDates(schedule.recurrenceType(), schedule.weekdays(), schedule.monthDays());
    }

    private void validateDates(CouponRecurrenceType type, List<Integer> weekdays, List<Integer> monthDays) {
        boolean hasWeekdays = weekdays != null && !weekdays.isEmpty();
        boolean hasMonthDays = monthDays != null && !monthDays.isEmpty();
        if (type == CouponRecurrenceType.DAILY && (hasWeekdays || hasMonthDays)
                || type == CouponRecurrenceType.WEEKLY && (!hasWeekdays || hasMonthDays)
                || type == CouponRecurrenceType.MONTHLY && (hasWeekdays || !hasMonthDays)) {
            invalid("重复类型与日期数组组合不合法");
        }
        if (hasWeekdays) validateSortedRange(weekdays, 1, 7, "weekdays");
        if (hasMonthDays) validateSortedRange(monthDays, 1, 31, "monthDays");
    }

    private void validateSortedRange(List<Integer> values, int minimum, int maximum, String field) {
        if (values.stream().anyMatch(value -> value == null || value < minimum || value > maximum)
                || !values.equals(sorted(values)) || values.stream().distinct().count() != values.size()) {
            invalid(field + " 必须去重、升序且位于允许范围");
        }
    }

    private LocalTime parseTime(String value) {
        try {
            LocalTime time = LocalTime.parse(value, TIME_FORMAT);
            if (value == null || value.length() != 8 || time.getSecond() != 0 || time.getNano() != 0) {
                invalid("dailyStartsAt 必须使用 HH:mm:ss 且秒为 00");
            }
            return time.truncatedTo(ChronoUnit.SECONDS);
        } catch (DateTimeParseException | NullPointerException ex) {
            invalid("dailyStartsAt 必须使用 HH:mm:ss 且秒为 00");
            throw new IllegalStateException(ex);
        }
    }

    private List<Integer> sorted(List<Integer> values) {
        return values == null ? null : values.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private void invalid(String message) {
        throw BusinessException.badRequest("VALIDATION_FAILED", message);
    }

    public record CampaignBounds(OffsetDateTime startsAt, OffsetDateTime endsAt) {
    }

    public record WindowResult(CouponClaimWindowPeriodView currentWindow,
                               CouponClaimWindowPeriodView nextWindow) {
    }
}
