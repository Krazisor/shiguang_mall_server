-- Recurring flash coupon schedules
-- MySQL 8.0.16+ only. Run after scheme7.sql.

USE `market`;

SET NAMES utf8mb4;
SET time_zone = '+08:00';

CREATE TABLE coupon_activity_recurrence (
    activity_id BIGINT UNSIGNED NOT NULL,
    recurrence_type VARCHAR(16) NOT NULL,
    weekdays_json JSON NULL,
    month_days_json JSON NULL,
    daily_starts_at TIME NOT NULL,
    window_duration_minutes INT NOT NULL,
    recurrence_starts_at DATETIME(3) NOT NULL,
    recurrence_ends_at DATETIME(3) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (activity_id),
    CONSTRAINT fk_coupon_activity_recurrence_activity
        FOREIGN KEY (activity_id) REFERENCES coupon_activity(id),
    CONSTRAINT chk_coupon_activity_recurrence_type
        CHECK (recurrence_type IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    CONSTRAINT chk_coupon_activity_recurrence_duration
        CHECK (window_duration_minutes BETWEEN 1 AND 1440),
    CONSTRAINT chk_coupon_activity_recurrence_time
        CHECK (recurrence_ends_at > recurrence_starts_at),
    CONSTRAINT chk_coupon_activity_recurrence_timezone
        CHECK (timezone = 'Asia/Shanghai'),
    CONSTRAINT chk_coupon_activity_recurrence_arrays
        CHECK (
            (recurrence_type = 'DAILY' AND weekdays_json IS NULL AND month_days_json IS NULL)
            OR (recurrence_type = 'WEEKLY' AND weekdays_json IS NOT NULL AND month_days_json IS NULL)
            OR (recurrence_type = 'MONTHLY' AND weekdays_json IS NULL AND month_days_json IS NOT NULL)
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
