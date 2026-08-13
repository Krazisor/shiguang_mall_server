package org.dhu.shiguang_market.coupon.service;

import static org.dhu.shiguang_market.common.util.Formatters.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.dhu.shiguang_market.common.exception.BusinessException;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponActivityStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponClaimWindowStatus;
import org.dhu.shiguang_market.common.model.MarketEnums.CouponScheduleType;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponActivityScheduleView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponClaimWindowPeriodView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.CouponClaimWindowView;
import org.dhu.shiguang_market.coupon.dto.CouponDtos.RecurringCouponSchedule;
import org.dhu.shiguang_market.coupon.mapper.CouponActivityRecurrenceMapper;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponActivity;
import org.dhu.shiguang_market.coupon.model.CouponModels.CouponActivityRecurrence;
import org.springframework.stereotype.Service;

@Service
public class CouponScheduleService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final CouponActivityRecurrenceMapper recurrenceMapper;
    private final CouponScheduleCalculator calculator;

    public CouponScheduleService(CouponActivityRecurrenceMapper recurrenceMapper,
                                 CouponScheduleCalculator calculator) {
        this.recurrenceMapper = recurrenceMapper;
        this.calculator = calculator;
    }

    public CouponScheduleCalculator.CampaignBounds validate(RecurringCouponSchedule schedule) {
        return calculator.validateAndCalculateBounds(schedule);
    }

    public void create(long activityId, RecurringCouponSchedule schedule) {
        recurrenceMapper.insert(entity(activityId, calculator.normalize(schedule)));
    }

    public void replace(long activityId, RecurringCouponSchedule schedule) {
        CouponActivityRecurrence replacement = entity(activityId, calculator.normalize(schedule));
        if (recurrenceMapper.updateById(replacement) != 1) {
            throw BusinessException.notFound("RESOURCE_NOT_FOUND", "周期规则不存在");
        }
    }

    public boolean isRecurring(long activityId) {
        return recurrenceMapper.selectById(activityId) != null;
    }

    public boolean isOpen(long activityId, LocalDateTime now) {
        CouponActivityRecurrence recurrence = recurrenceMapper.selectById(activityId);
        return recurrence == null || calculator.isOpen(dto(recurrence), time(now));
    }

    public CouponActivityScheduleView view(CouponActivity activity) {
        OffsetDateTime now = time(LocalDateTime.now());
        CouponActivityRecurrence recurrence = recurrenceMapper.selectById(activity.getId());
        if (recurrence == null) return onceView(activity, now);
        RecurringCouponSchedule schedule = dto(recurrence);
        CouponScheduleCalculator.WindowResult calculated = calculator.window(schedule, now);
        CouponClaimWindowStatus status = status(activity, now, calculated.currentWindow());
        CouponClaimWindowView window = new CouponClaimWindowView(status,
                status == CouponClaimWindowStatus.OPEN || status == CouponClaimWindowStatus.PAUSED
                        ? calculated.currentWindow() : null,
                status == CouponClaimWindowStatus.ENDED ? null : calculated.nextWindow());
        return new CouponActivityScheduleView(CouponScheduleType.RECURRING, time(activity.getStartsAt()),
                time(activity.getEndsAt()), schedule, window, now, version(activity));
    }

    public RecurringCouponSchedule recurrence(long activityId) {
        CouponActivityRecurrence recurrence = recurrenceMapper.selectById(activityId);
        return recurrence == null ? null : dto(recurrence);
    }

    private CouponActivityScheduleView onceView(CouponActivity activity, OffsetDateTime now) {
        CouponClaimWindowPeriodView period = new CouponClaimWindowPeriodView(time(activity.getStartsAt()),
                time(activity.getEndsAt()));
        boolean open = !now.isBefore(period.startsAt()) && now.isBefore(period.endsAt());
        CouponClaimWindowStatus status = status(activity, now, open ? period : null);
        CouponClaimWindowPeriodView current = open && (status == CouponClaimWindowStatus.OPEN
                || status == CouponClaimWindowStatus.PAUSED) ? period : null;
        CouponClaimWindowPeriodView next = status == CouponClaimWindowStatus.WAITING
                && now.isBefore(period.startsAt()) ? period : null;
        return new CouponActivityScheduleView(CouponScheduleType.ONCE, period.startsAt(), period.endsAt(),
                null, new CouponClaimWindowView(status, current, next), now, version(activity));
    }

    private CouponClaimWindowStatus status(CouponActivity activity, OffsetDateTime now,
                                           CouponClaimWindowPeriodView current) {
        if (activity.getStatus() == CouponActivityStatus.ENDED
                || activity.getStatus() == CouponActivityStatus.CANCELLED
                || !now.isBefore(time(activity.getEndsAt()))) return CouponClaimWindowStatus.ENDED;
        if (activity.getStatus() == CouponActivityStatus.PAUSED) return CouponClaimWindowStatus.PAUSED;
        if (activity.getStatus() == CouponActivityStatus.DRAFT) return CouponClaimWindowStatus.WAITING;
        return current == null ? CouponClaimWindowStatus.WAITING : CouponClaimWindowStatus.OPEN;
    }

    private CouponActivityRecurrence entity(long activityId, RecurringCouponSchedule schedule) {
        CouponActivityRecurrence entity = new CouponActivityRecurrence();
        entity.setActivityId(activityId);
        entity.setRecurrenceType(schedule.recurrenceType());
        entity.setWeekdaysJson(schedule.weekdays());
        entity.setMonthDaysJson(schedule.monthDays());
        entity.setDailyStartsAt(java.time.LocalTime.parse(schedule.dailyStartsAt(), TIME_FORMAT));
        entity.setWindowDurationMinutes(schedule.windowDurationMinutes());
        entity.setRecurrenceStartsAt(schedule.recurrenceStartsAt().toLocalDateTime());
        entity.setRecurrenceEndsAt(schedule.recurrenceEndsAt().toLocalDateTime());
        entity.setTimezone(schedule.timezone());
        return entity;
    }

    private RecurringCouponSchedule dto(CouponActivityRecurrence entity) {
        return new RecurringCouponSchedule(entity.getRecurrenceType(), entity.getWeekdaysJson(),
                entity.getMonthDaysJson(), entity.getDailyStartsAt().format(TIME_FORMAT),
                entity.getWindowDurationMinutes(), time(entity.getRecurrenceStartsAt()),
                time(entity.getRecurrenceEndsAt()), entity.getTimezone());
    }

    private int version(CouponActivity activity) {
        return activity.getVersion() == null ? 0 : activity.getVersion();
    }
}
