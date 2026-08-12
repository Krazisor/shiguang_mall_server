-- Shiguang Market after-sale appeal time constraint fix
-- MySQL 8.0.16+ only. The normal migration order runs this after scheme6.sql;
-- this fix itself only depends on the after_sale_appeal table from scheme3.sql.
-- Safe to run repeatedly: the named constraint is replaced on every execution.

USE `market`;

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- merchant_reviewed_at is copied from the earlier merchant rejection, so it
-- must not be required to occur after the appeal's own creation time.
SET @appeal_time_check_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'after_sale_appeal'
      AND constraint_name = 'chk_after_sale_appeal_times'
      AND constraint_type = 'CHECK'
);

SET @drop_appeal_time_check_sql = IF(
    @appeal_time_check_exists > 0,
    'ALTER TABLE `after_sale_appeal` DROP CHECK `chk_after_sale_appeal_times`',
    'SELECT 1'
);
PREPARE drop_appeal_time_check FROM @drop_appeal_time_check_sql;
EXECUTE drop_appeal_time_check;
DEALLOCATE PREPARE drop_appeal_time_check;

ALTER TABLE `after_sale_appeal`
    ADD CONSTRAINT `chk_after_sale_appeal_times`
        CHECK (decided_at IS NULL OR decided_at >= created_at);
