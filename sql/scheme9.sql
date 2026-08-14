-- Coupon template archive state
-- MySQL 8.0.16+ only. Run after scheme8.sql.

USE `market`;

SET NAMES utf8mb4;
SET time_zone = '+08:00';

ALTER TABLE coupon_template
    DROP CHECK chk_coupon_template_status,
    ADD CONSTRAINT chk_coupon_template_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'ENDED', 'ARCHIVED'));
