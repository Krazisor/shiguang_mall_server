-- Shiguang Market coupon domain migration
-- MySQL 8.0.16+ only. Run after schema.sql, schema2.sql, scheme3.sql, scheme4.sql and scheme5.sql.
-- This file is intentionally additive; historical schema files remain immutable.

USE `market`;
SET NAMES utf8mb4;
SET time_zone = '+08:00';

CREATE TABLE coupon_activity (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    activity_no VARCHAR(64) NOT NULL,
    owner_type VARCHAR(16) NOT NULL,
    shop_id BIGINT UNSIGNED NULL,
    activity_type VARCHAR(32) NOT NULL,
    activity_name VARCHAR(128) NOT NULL,
    subtitle VARCHAR(255) NULL,
    banner_url VARCHAR(1024) NULL,
    starts_at DATETIME(3) NOT NULL,
    ends_at DATETIME(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    pause_source VARCHAR(32) NULL,
    pause_reason VARCHAR(500) NULL,
    created_by BIGINT UNSIGNED NOT NULL,
    updated_by BIGINT UNSIGNED NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_activity_no (activity_no),
    KEY idx_coupon_activity_shop (shop_id, status, starts_at),
    KEY idx_coupon_activity_owner (owner_type, status, starts_at),
    KEY idx_coupon_activity_end (status, ends_at),
    CONSTRAINT fk_coupon_activity_shop FOREIGN KEY (shop_id) REFERENCES shop(id),
    CONSTRAINT fk_coupon_activity_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
    CONSTRAINT fk_coupon_activity_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user(id),
    CONSTRAINT chk_coupon_activity_owner CHECK (
        (owner_type='SHOP' AND shop_id IS NOT NULL) OR (owner_type='PLATFORM' AND shop_id IS NULL)),
    CONSTRAINT chk_coupon_activity_time CHECK (ends_at > starts_at),
    CONSTRAINT chk_coupon_activity_status CHECK (
        status IN ('DRAFT','SCHEDULED','RUNNING','PAUSED','ENDED','CANCELLED')),
    CONSTRAINT chk_coupon_activity_pause CHECK (
        (status='PAUSED' AND pause_source IS NOT NULL AND pause_reason IS NOT NULL)
        OR (status<>'PAUSED' AND pause_source IS NULL AND pause_reason IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupon_template (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    template_no VARCHAR(64) NOT NULL,
    activity_id BIGINT UNSIGNED NULL,
    owner_type VARCHAR(16) NOT NULL,
    owner_shop_id BIGINT UNSIGNED NULL,
    coupon_name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    coupon_type VARCHAR(32) NOT NULL,
    threshold_amount DECIMAL(18,2) NOT NULL,
    discount_amount DECIMAL(18,2) NULL,
    percentage_off DECIMAL(5,2) NULL,
    maximum_discount_amount DECIMAL(18,2) NULL,
    funding_type VARCHAR(16) NOT NULL,
    platform_share_rate DECIMAL(7,4) NOT NULL DEFAULT 0.0000,
    scope_type VARCHAR(16) NOT NULL,
    distribution_type VARCHAR(24) NOT NULL,
    audience_type VARCHAR(24) NOT NULL,
    new_user_within_days INT UNSIGNED NULL,
    claim_starts_at DATETIME(3) NULL,
    claim_ends_at DATETIME(3) NULL,
    validity_type VARCHAR(24) NOT NULL,
    valid_from DATETIME(3) NULL,
    valid_to DATETIME(3) NULL,
    effective_delay_minutes INT UNSIGNED NULL,
    valid_for_hours INT UNSIGNED NULL,
    total_issue_limit INT UNSIGNED NOT NULL,
    issued_count INT UNSIGNED NOT NULL DEFAULT 0,
    per_user_limit TINYINT UNSIGNED NOT NULL DEFAULT 1,
    stack_mode VARCHAR(20) NOT NULL,
    refund_restore_policy VARCHAR(24) NOT NULL,
    budget_amount DECIMAL(18,2) NOT NULL,
    budget_reserved_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    budget_consumed_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    budget_reversed_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    first_issued_at DATETIME(3) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NOT NULL,
    updated_by BIGINT UNSIGNED NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_template_no (template_no),
    KEY idx_coupon_template_activity (activity_id, status, sort_order, id),
    KEY idx_coupon_template_shop (owner_shop_id, status, created_at),
    KEY idx_coupon_template_claim (owner_type, status, claim_starts_at, claim_ends_at),
    KEY idx_coupon_template_distribution (distribution_type, status, claim_ends_at),
    CONSTRAINT fk_coupon_template_activity FOREIGN KEY (activity_id) REFERENCES coupon_activity(id),
    CONSTRAINT fk_coupon_template_shop FOREIGN KEY (owner_shop_id) REFERENCES shop(id),
    CONSTRAINT fk_coupon_template_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
    CONSTRAINT fk_coupon_template_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user(id),
    CONSTRAINT chk_coupon_template_owner CHECK (
        (owner_type='SHOP' AND owner_shop_id IS NOT NULL AND funding_type='SHOP' AND platform_share_rate=0.0000)
        OR (owner_type='PLATFORM' AND owner_shop_id IS NULL AND funding_type IN ('PLATFORM','SHOP','SHARED'))),
    CONSTRAINT chk_coupon_template_benefit CHECK (
        (coupon_type='PERCENTAGE' AND threshold_amount>=0 AND discount_amount IS NULL
            AND percentage_off BETWEEN 0.01 AND 99.99 AND maximum_discount_amount>0)
        OR (coupon_type='THRESHOLD_REDUCTION' AND threshold_amount>0 AND discount_amount>0
            AND discount_amount<threshold_amount AND percentage_off IS NULL AND maximum_discount_amount IS NULL)
        OR (coupon_type='CASH_RED_PACKET' AND threshold_amount=0 AND discount_amount>0
            AND percentage_off IS NULL AND maximum_discount_amount IS NULL)),
    CONSTRAINT chk_coupon_template_funding CHECK (
        (funding_type='PLATFORM' AND platform_share_rate=100.0000)
        OR (funding_type='SHOP' AND platform_share_rate=0.0000)
        OR (funding_type='SHARED' AND platform_share_rate BETWEEN 0.0001 AND 99.9999
            AND owner_type='PLATFORM' AND scope_type IN ('SHOP','SPU','SKU'))),
    CONSTRAINT chk_coupon_template_claim_window CHECK (
        (distribution_type IN ('PUBLIC_CLAIM','FLASH_CLAIM') AND activity_id IS NOT NULL
            AND claim_starts_at IS NOT NULL AND claim_ends_at>claim_starts_at)
        OR (distribution_type IN ('REDEEM_CODE','DIRECT_GRANT','SYSTEM_GRANT')
            AND claim_starts_at IS NULL AND claim_ends_at IS NULL)),
    CONSTRAINT chk_coupon_template_validity CHECK (
        (validity_type='FIXED_RANGE' AND valid_from IS NOT NULL AND valid_to>valid_from
            AND effective_delay_minutes IS NULL AND valid_for_hours IS NULL)
        OR (validity_type='RELATIVE_AFTER_CLAIM' AND valid_from IS NULL AND valid_to IS NULL
            AND effective_delay_minutes BETWEEN 0 AND 10080 AND valid_for_hours BETWEEN 1 AND 8760)),
    CONSTRAINT chk_coupon_template_audience CHECK (
        (audience_type='NEW_USERS' AND new_user_within_days BETWEEN 1 AND 365)
        OR (audience_type<>'NEW_USERS' AND new_user_within_days IS NULL)),
    CONSTRAINT chk_coupon_template_system_grant CHECK (
        distribution_type<>'SYSTEM_GRANT' OR (owner_type='PLATFORM' AND audience_type='NEW_USERS')),
    CONSTRAINT chk_coupon_template_limits CHECK (
        total_issue_limit>0 AND issued_count<=total_issue_limit AND per_user_limit BETWEEN 1 AND 99),
    CONSTRAINT chk_coupon_template_budget CHECK (
        budget_amount>0 AND budget_reserved_amount>=0 AND budget_consumed_amount>=budget_reversed_amount
        AND budget_reversed_amount>=0
        AND budget_reserved_amount+budget_consumed_amount-budget_reversed_amount<=budget_amount),
    CONSTRAINT chk_coupon_template_status CHECK (status IN ('DRAFT','ACTIVE','PAUSED','ENDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupon_template_shop_scope (
    template_id BIGINT UNSIGNED NOT NULL, shop_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (template_id, shop_id),
    CONSTRAINT fk_coupon_shop_scope_template FOREIGN KEY (template_id) REFERENCES coupon_template(id),
    CONSTRAINT fk_coupon_shop_scope_shop FOREIGN KEY (shop_id) REFERENCES shop(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupon_template_category_scope (
    template_id BIGINT UNSIGNED NOT NULL, category_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (template_id, category_id),
    CONSTRAINT fk_coupon_category_scope_template FOREIGN KEY (template_id) REFERENCES coupon_template(id),
    CONSTRAINT fk_coupon_category_scope_category FOREIGN KEY (category_id) REFERENCES product_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupon_template_spu_scope (
    template_id BIGINT UNSIGNED NOT NULL, spu_id BIGINT UNSIGNED NOT NULL,
    shop_id BIGINT UNSIGNED NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (template_id, spu_id),
    CONSTRAINT fk_coupon_spu_scope_template FOREIGN KEY (template_id) REFERENCES coupon_template(id),
    CONSTRAINT fk_coupon_spu_scope_spu FOREIGN KEY (spu_id,shop_id) REFERENCES product_spu(id,shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupon_template_sku_scope (
    template_id BIGINT UNSIGNED NOT NULL, sku_id BIGINT UNSIGNED NOT NULL,
    spu_id BIGINT UNSIGNED NOT NULL, shop_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (template_id, sku_id),
    CONSTRAINT fk_coupon_sku_scope_template FOREIGN KEY (template_id) REFERENCES coupon_template(id),
    CONSTRAINT fk_coupon_sku_scope_sku FOREIGN KEY (sku_id,spu_id,shop_id) REFERENCES product_sku(id,spu_id,shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupon_funding_participation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    template_id BIGINT UNSIGNED NOT NULL, shop_id BIGINT UNSIGNED NOT NULL,
    platform_share_rate DECIMAL(7,4) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    invited_by BIGINT UNSIGNED NOT NULL, decided_by BIGINT UNSIGNED NULL,
    decision_reason VARCHAR(500) NULL, invited_at DATETIME(3) NOT NULL,
    decided_at DATETIME(3) NULL, version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY(id), UNIQUE KEY uk_coupon_funding_template_shop(template_id,shop_id),
    KEY idx_coupon_funding_shop(shop_id,status,invited_at),
    CONSTRAINT fk_coupon_funding_template FOREIGN KEY(template_id) REFERENCES coupon_template(id),
    CONSTRAINT fk_coupon_funding_shop FOREIGN KEY(shop_id) REFERENCES shop(id),
    CONSTRAINT fk_coupon_funding_inviter FOREIGN KEY(invited_by) REFERENCES sys_user(id),
    CONSTRAINT fk_coupon_funding_decider FOREIGN KEY(decided_by) REFERENCES sys_user(id),
    CONSTRAINT chk_coupon_funding_status CHECK(status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED')),
    CONSTRAINT chk_coupon_funding_decision CHECK(
        (status='PENDING' AND decided_by IS NULL AND decided_at IS NULL AND decision_reason IS NULL)
        OR (status='ACCEPTED' AND decided_by IS NOT NULL AND decided_at IS NOT NULL AND decision_reason IS NULL)
        OR (status='REJECTED' AND decided_by IS NOT NULL AND decided_at IS NOT NULL AND decision_reason IS NOT NULL)
        OR status='CANCELLED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_coupon (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, coupon_no VARCHAR(64) NOT NULL,
    template_id BIGINT UNSIGNED NOT NULL, template_version INT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    valid_from DATETIME(3) NOT NULL, valid_to DATETIME(3) NOT NULL,
    locked_trade_id BIGINT UNSIGNED NULL, used_at DATETIME(3) NULL, expired_at DATETIME(3) NULL,
    revoked_by BIGINT UNSIGNED NULL, revoked_reason VARCHAR(500) NULL, revoked_at DATETIME(3) NULL,
    restore_count TINYINT UNSIGNED NOT NULL DEFAULT 0, last_restored_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY(id), UNIQUE KEY uk_user_coupon_no(coupon_no),
    KEY idx_user_coupon_user(user_id,status,valid_to,id),
    KEY idx_user_coupon_template_user(template_id,user_id,created_at),
    CONSTRAINT fk_user_coupon_template FOREIGN KEY(template_id) REFERENCES coupon_template(id),
    CONSTRAINT fk_user_coupon_user FOREIGN KEY(user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_user_coupon_trade FOREIGN KEY(locked_trade_id) REFERENCES trade_order(id),
    CONSTRAINT fk_user_coupon_revoker FOREIGN KEY(revoked_by) REFERENCES sys_user(id),
    CONSTRAINT chk_user_coupon_status CHECK(status IN ('AVAILABLE','LOCKED','USED','EXPIRED','REVOKED')),
    CONSTRAINT chk_user_coupon_validity CHECK(valid_to>valid_from AND restore_count IN (0,1)),
    CONSTRAINT chk_user_coupon_lock CHECK((status='LOCKED')=(locked_trade_id IS NOT NULL)),
    CONSTRAINT chk_user_coupon_used CHECK(status<>'USED' OR used_at IS NOT NULL),
    CONSTRAINT chk_user_coupon_revoke CHECK(
        (status='REVOKED' AND revoked_by IS NOT NULL AND revoked_reason IS NOT NULL AND revoked_at IS NOT NULL)
        OR (status<>'REVOKED' AND revoked_by IS NULL AND revoked_reason IS NULL AND revoked_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupon_redeem_code (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, batch_no VARCHAR(64) NOT NULL,
    template_id BIGINT UNSIGNED NOT NULL,
    code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    hash_key_version SMALLINT UNSIGNED NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    user_coupon_id BIGINT UNSIGNED NULL, redeemed_by BIGINT UNSIGNED NULL, redeemed_at DATETIME(3) NULL,
    revoked_by BIGINT UNSIGNED NULL, revoked_at DATETIME(3) NULL, revoke_reason VARCHAR(500) NULL,
    created_by BIGINT UNSIGNED NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY(id), UNIQUE KEY uk_coupon_redeem_hash(code_hash),
    UNIQUE KEY uk_coupon_redeem_user_coupon(user_coupon_id),
    KEY idx_coupon_redeem_batch(batch_no,status,created_at),
    CONSTRAINT fk_coupon_redeem_template FOREIGN KEY(template_id) REFERENCES coupon_template(id),
    CONSTRAINT fk_coupon_redeem_user_coupon FOREIGN KEY(user_coupon_id) REFERENCES user_coupon(id),
    CONSTRAINT fk_coupon_redeem_user FOREIGN KEY(redeemed_by) REFERENCES sys_user(id),
    CONSTRAINT fk_coupon_redeem_revoker FOREIGN KEY(revoked_by) REFERENCES sys_user(id),
    CONSTRAINT fk_coupon_redeem_creator FOREIGN KEY(created_by) REFERENCES sys_user(id),
    CONSTRAINT chk_coupon_redeem_status CHECK(status IN ('ACTIVE','REDEEMED','REVOKED')),
    CONSTRAINT chk_coupon_redeem_result CHECK(
        (status='ACTIVE' AND user_coupon_id IS NULL AND redeemed_by IS NULL AND redeemed_at IS NULL
            AND revoked_by IS NULL AND revoked_at IS NULL AND revoke_reason IS NULL)
        OR (status='REDEEMED' AND user_coupon_id IS NOT NULL AND redeemed_by IS NOT NULL AND redeemed_at IS NOT NULL)
        OR (status='REVOKED' AND user_coupon_id IS NULL AND revoked_by IS NOT NULL
            AND revoked_at IS NOT NULL AND revoke_reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupon_claim_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, claim_no VARCHAR(64) NOT NULL,
    user_coupon_id BIGINT UNSIGNED NOT NULL, template_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL, activity_id BIGINT UNSIGNED NULL,
    claim_source VARCHAR(24) NOT NULL, redeem_code_id BIGINT UNSIGNED NULL,
    granted_by BIGINT UNSIGNED NULL, business_no VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY(id), UNIQUE KEY uk_coupon_claim_no(claim_no),
    UNIQUE KEY uk_coupon_claim_user_coupon(user_coupon_id), UNIQUE KEY uk_coupon_claim_business(business_no),
    KEY idx_coupon_claim_template_user(template_id,user_id,created_at),
    CONSTRAINT fk_coupon_claim_coupon FOREIGN KEY(user_coupon_id) REFERENCES user_coupon(id),
    CONSTRAINT fk_coupon_claim_template FOREIGN KEY(template_id) REFERENCES coupon_template(id),
    CONSTRAINT fk_coupon_claim_user FOREIGN KEY(user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_coupon_claim_activity FOREIGN KEY(activity_id) REFERENCES coupon_activity(id),
    CONSTRAINT fk_coupon_claim_code FOREIGN KEY(redeem_code_id) REFERENCES coupon_redeem_code(id),
    CONSTRAINT fk_coupon_claim_granter FOREIGN KEY(granted_by) REFERENCES sys_user(id),
    CONSTRAINT chk_coupon_claim_source CHECK(claim_source IN (
        'PUBLIC_CLAIM','FLASH_CLAIM','REDEEM_CODE','DIRECT_GRANT','SYSTEM_GRANT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupon_redemption (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, redemption_no VARCHAR(64) NOT NULL,
    user_coupon_id BIGINT UNSIGNED NOT NULL, template_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL, trade_id BIGINT UNSIGNED NOT NULL,
    attempt_no TINYINT UNSIGNED NOT NULL, status VARCHAR(16) NOT NULL,
    discount_amount DECIMAL(18,2) NOT NULL, platform_funded_amount DECIMAL(18,2) NOT NULL,
    shop_funded_amount DECIMAL(18,2) NOT NULL, reserved_at DATETIME(3) NOT NULL,
    consumed_at DATETIME(3) NULL, released_at DATETIME(3) NULL, restored_at DATETIME(3) NULL,
    release_reason VARCHAR(32) NULL, version INT UNSIGNED NOT NULL DEFAULT 0,
    active_coupon_id BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN status IN ('RESERVED','CONSUMED') THEN user_coupon_id ELSE NULL END) STORED,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY(id), UNIQUE KEY uk_coupon_redemption_no(redemption_no),
    UNIQUE KEY uk_coupon_redemption_attempt(user_coupon_id,attempt_no),
    UNIQUE KEY uk_coupon_redemption_trade_coupon(trade_id,user_coupon_id),
    UNIQUE KEY uk_coupon_redemption_active(active_coupon_id),
    KEY idx_coupon_redemption_trade(trade_id,status,id),
    CONSTRAINT fk_coupon_redemption_coupon FOREIGN KEY(user_coupon_id) REFERENCES user_coupon(id),
    CONSTRAINT fk_coupon_redemption_template FOREIGN KEY(template_id) REFERENCES coupon_template(id),
    CONSTRAINT fk_coupon_redemption_user FOREIGN KEY(user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_coupon_redemption_trade FOREIGN KEY(trade_id) REFERENCES trade_order(id),
    CONSTRAINT chk_coupon_redemption_status CHECK(status IN ('RESERVED','CONSUMED','RELEASED','RESTORED')),
    CONSTRAINT chk_coupon_redemption_amount CHECK(discount_amount>0
        AND discount_amount=platform_funded_amount+shop_funded_amount
        AND platform_funded_amount>=0 AND shop_funded_amount>=0),
    CONSTRAINT chk_coupon_redemption_attempt CHECK(attempt_no BETWEEN 1 AND 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupon_redemption_allocation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, redemption_id BIGINT UNSIGNED NOT NULL,
    trade_id BIGINT UNSIGNED NOT NULL, order_id BIGINT UNSIGNED NOT NULL,
    order_item_id BIGINT UNSIGNED NOT NULL, shop_id BIGINT UNSIGNED NOT NULL,
    eligible_gross_amount DECIMAL(18,2) NOT NULL, calculation_base_amount DECIMAL(18,2) NOT NULL,
    discount_amount DECIMAL(18,2) NOT NULL, platform_funded_amount DECIMAL(18,2) NOT NULL,
    shop_funded_amount DECIMAL(18,2) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY(id), UNIQUE KEY uk_coupon_allocation_item(redemption_id,order_item_id),
    KEY idx_coupon_allocation_trade(trade_id,shop_id), KEY idx_coupon_allocation_order(order_id,order_item_id),
    CONSTRAINT fk_coupon_allocation_redemption FOREIGN KEY(redemption_id) REFERENCES coupon_redemption(id),
    CONSTRAINT fk_coupon_allocation_trade FOREIGN KEY(trade_id) REFERENCES trade_order(id),
    CONSTRAINT fk_coupon_allocation_order FOREIGN KEY(order_id,shop_id) REFERENCES order_info(id,shop_id),
    CONSTRAINT fk_coupon_allocation_item FOREIGN KEY(order_item_id,order_id) REFERENCES order_item(id,order_id),
    CONSTRAINT chk_coupon_allocation_amount CHECK(eligible_gross_amount>0 AND calculation_base_amount>0
        AND discount_amount>0 AND discount_amount<=calculation_base_amount
        AND discount_amount=platform_funded_amount+shop_funded_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupon_refund_allocation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, redemption_allocation_id BIGINT UNSIGNED NOT NULL,
    after_sale_id BIGINT UNSIGNED NOT NULL, refund_no VARCHAR(64) NOT NULL,
    refunded_quantity INT UNSIGNED NOT NULL, coupon_discount_reversal_amount DECIMAL(18,2) NOT NULL,
    platform_funding_reversal_amount DECIMAL(18,2) NOT NULL,
    shop_funding_reversal_amount DECIMAL(18,2) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY(id), UNIQUE KEY uk_coupon_refund_allocation(redemption_allocation_id,refund_no),
    CONSTRAINT fk_coupon_refund_allocation FOREIGN KEY(redemption_allocation_id) REFERENCES coupon_redemption_allocation(id),
    CONSTRAINT fk_coupon_refund_after_sale FOREIGN KEY(after_sale_id) REFERENCES after_sale_request(id),
    CONSTRAINT chk_coupon_refund_amount CHECK(refunded_quantity>0 AND coupon_discount_reversal_amount>=0
        AND coupon_discount_reversal_amount=platform_funding_reversal_amount+shop_funding_reversal_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupon_budget_ledger (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, ledger_no VARCHAR(64) NOT NULL,
    template_id BIGINT UNSIGNED NOT NULL, user_coupon_id BIGINT UNSIGNED NULL,
    redemption_id BIGINT UNSIGNED NULL, entry_type VARCHAR(24) NOT NULL,
    reserved_change DECIMAL(18,2) NOT NULL, consumed_change DECIMAL(18,2) NOT NULL,
    reversed_change DECIMAL(18,2) NOT NULL, reserved_after DECIMAL(18,2) NOT NULL,
    consumed_after DECIMAL(18,2) NOT NULL, reversed_after DECIMAL(18,2) NOT NULL,
    platform_amount DECIMAL(18,2) NOT NULL, shop_amount DECIMAL(18,2) NOT NULL,
    business_type VARCHAR(32) NOT NULL, business_no VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY(id), UNIQUE KEY uk_coupon_budget_no(ledger_no),
    UNIQUE KEY uk_coupon_budget_business(entry_type,business_type,business_no),
    KEY idx_coupon_budget_template(template_id,created_at),
    CONSTRAINT fk_coupon_budget_template FOREIGN KEY(template_id) REFERENCES coupon_template(id),
    CONSTRAINT fk_coupon_budget_coupon FOREIGN KEY(user_coupon_id) REFERENCES user_coupon(id),
    CONSTRAINT fk_coupon_budget_redemption FOREIGN KEY(redemption_id) REFERENCES coupon_redemption(id),
    CONSTRAINT chk_coupon_budget_entry CHECK(entry_type IN (
        'CLAIM_RESERVE','USE_CONSUME','EXPIRE_RELEASE','REVOKE_RELEASE','REFUND_REVERSE','RESTORE_RESERVE')),
    CONSTRAINT chk_coupon_budget_snapshot CHECK(reserved_after>=0 AND consumed_after>=reversed_after
        AND reversed_after>=0 AND platform_amount>=0 AND shop_amount>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupon_operation_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, resource_type VARCHAR(24) NOT NULL,
    resource_id BIGINT UNSIGNED NOT NULL, operation_type VARCHAR(32) NOT NULL,
    operator_type VARCHAR(16) NOT NULL, operator_id BIGINT UNSIGNED NULL,
    shop_id BIGINT UNSIGNED NULL, from_status VARCHAR(32) NULL, to_status VARCHAR(32) NULL,
    change_summary_json JSON NULL, reason VARCHAR(500) NULL, request_id VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY(id), KEY idx_coupon_operation_resource(resource_type,resource_id,created_at),
    KEY idx_coupon_operation_shop(shop_id,created_at),
    CONSTRAINT fk_coupon_operation_user FOREIGN KEY(operator_id) REFERENCES sys_user(id),
    CONSTRAINT fk_coupon_operation_shop FOREIGN KEY(shop_id) REFERENCES shop(id),
    CONSTRAINT chk_coupon_operation_operator CHECK(operator_type IN ('USER','SHOP','PLATFORM','SYSTEM')),
    CONSTRAINT chk_coupon_operation_json CHECK(change_summary_json IS NULL OR JSON_TYPE(change_summary_json)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Replace order amount constraints only after the historical rows have been backfilled.
ALTER TABLE trade_order ADD COLUMN gross_amount DECIMAL(18,2) NULL AFTER trade_status,
    ADD COLUMN coupon_discount_amount DECIMAL(18,2) NULL AFTER gross_amount,
    ADD COLUMN uses_first_order_coupon TINYINT(1) NULL DEFAULT 0 AFTER coupon_discount_amount;
UPDATE trade_order SET gross_amount=payable_amount,coupon_discount_amount=0.00,uses_first_order_coupon=0
WHERE gross_amount IS NULL OR coupon_discount_amount IS NULL OR uses_first_order_coupon IS NULL;
ALTER TABLE trade_order MODIFY gross_amount DECIMAL(18,2) NOT NULL,
    MODIFY coupon_discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    MODIFY uses_first_order_coupon TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN active_first_order_coupon_user_id BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN uses_first_order_coupon=1 AND trade_status='PENDING_PAYMENT' THEN user_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_trade_first_order_coupon_user(active_first_order_coupon_user_id),
    DROP CHECK chk_trade_order_amount,
    ADD CONSTRAINT chk_trade_order_amount CHECK(gross_amount>0 AND coupon_discount_amount>=0
        AND payable_amount=gross_amount-coupon_discount_amount AND payable_amount>0),
    ADD CONSTRAINT chk_trade_first_order_coupon CHECK(uses_first_order_coupon IN (0,1));

ALTER TABLE order_info ADD COLUMN coupon_discount_amount DECIMAL(18,2) NULL DEFAULT 0.00 AFTER freight_amount;
UPDATE order_info SET coupon_discount_amount=0.00 WHERE coupon_discount_amount IS NULL;
ALTER TABLE order_info MODIFY coupon_discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    DROP CHECK chk_order_info_amount,
    ADD CONSTRAINT chk_order_info_amount CHECK(item_amount>=0 AND freight_amount>=0
        AND coupon_discount_amount>=0 AND payable_amount=item_amount+freight_amount-coupon_discount_amount
        AND payable_amount>=0.01 AND refund_amount>=0 AND refund_amount<=payable_amount);

ALTER TABLE order_item
    ADD COLUMN coupon_discount_amount DECIMAL(18,2) NULL DEFAULT 0.00 AFTER freight_amount;
UPDATE order_item SET coupon_discount_amount=0.00 WHERE coupon_discount_amount IS NULL;
ALTER TABLE order_item MODIFY coupon_discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    DROP CHECK chk_order_item_amount,
    ADD CONSTRAINT chk_order_item_amount CHECK(unit_price>0 AND quantity>0
        AND original_amount=unit_price*quantity AND freight_amount>=0 AND coupon_discount_amount>=0
        AND payable_amount=original_amount+freight_amount-coupon_discount_amount AND payable_amount>=0.01
        AND refunded_quantity<=quantity AND refunded_amount>=0 AND refunded_amount<=payable_amount);

ALTER TABLE shop_settlement ADD COLUMN buyer_paid_amount DECIMAL(18,2) NULL AFTER gross_amount,
    ADD COLUMN platform_coupon_subsidy_amount DECIMAL(18,2) NULL DEFAULT 0.00 AFTER buyer_paid_amount,
    ADD COLUMN shop_coupon_discount_amount DECIMAL(18,2) NULL DEFAULT 0.00 AFTER platform_coupon_subsidy_amount,
    ADD COLUMN platform_subsidy_refund_amount DECIMAL(18,2) NULL DEFAULT 0.00 AFTER buyer_refund_amount;
UPDATE shop_settlement SET buyer_paid_amount=gross_amount,platform_coupon_subsidy_amount=0.00,
    shop_coupon_discount_amount=0.00,platform_subsidy_refund_amount=0.00
WHERE buyer_paid_amount IS NULL OR platform_coupon_subsidy_amount IS NULL
   OR shop_coupon_discount_amount IS NULL OR platform_subsidy_refund_amount IS NULL;
ALTER TABLE shop_settlement MODIFY buyer_paid_amount DECIMAL(18,2) NOT NULL,
    MODIFY platform_coupon_subsidy_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    MODIFY shop_coupon_discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    MODIFY platform_subsidy_refund_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00;

ALTER TABLE object_asset DROP CHECK chk_object_asset_purpose,
    ADD CONSTRAINT chk_object_asset_purpose CHECK(purpose IN (
        'AVATAR','SHOP_LOGO','BRAND_LOGO','PRODUCT_COVER','PRODUCT_GALLERY','SKU_IMAGE',
        'RICH_TEXT_IMAGE','AFTER_SALE_EVIDENCE','APPEAL_EVIDENCE','COUPON_ACTIVITY_BANNER'));
ALTER TABLE object_asset DROP CHECK chk_object_asset_shop_scope,
    ADD CONSTRAINT chk_object_asset_shop_scope CHECK (
        (purpose IN ('PRODUCT_COVER','PRODUCT_GALLERY','SKU_IMAGE','RICH_TEXT_IMAGE') AND shop_id IS NOT NULL)
        OR (purpose IN ('AVATAR','SHOP_LOGO','BRAND_LOGO','AFTER_SALE_EVIDENCE','APPEAL_EVIDENCE') AND shop_id IS NULL)
        OR purpose='COUPON_ACTIVITY_BANNER');

START TRANSACTION;
INSERT INTO sys_permission(permission_code,permission_name,scope_type,resource,http_method)
SELECT seed.code,seed.name,seed.scope_type,seed.resource,seed.http_method
FROM (
    SELECT 'coupon:read:self' code,'查看本人优惠券' name,'PLATFORM' scope_type,'/api/coupons/**' resource,'GET' http_method UNION ALL
    SELECT 'coupon:claim','领取和兑换优惠券','PLATFORM','/api/coupon-center/**',NULL UNION ALL
    SELECT 'shop:coupon:read','查看本店优惠券','SHOP','/api/shops/*/coupon-*','GET' UNION ALL
    SELECT 'shop:coupon:manage','管理本店优惠券','SHOP','/api/shops/*/coupon-*',NULL UNION ALL
    SELECT 'shop:coupon:grant','本店定向发券','SHOP','/api/shops/*/coupon-*',NULL UNION ALL
    SELECT 'shop:coupon:funding:approve','审批联合承担','SHOP','/api/shops/*/coupon-funding-*',NULL UNION ALL
    SELECT 'platform:coupon:read','查看全局优惠券','PLATFORM','/api/platform/coupon-*','GET' UNION ALL
    SELECT 'platform:coupon:manage','管理平台优惠券','PLATFORM','/api/platform/coupon-*',NULL UNION ALL
    SELECT 'platform:coupon:grant','平台定向发券','PLATFORM','/api/platform/coupon-*',NULL UNION ALL
    SELECT 'platform:coupon:governance','优惠券治理','PLATFORM','/api/platform/coupon-governance/**',NULL
) seed
WHERE NOT EXISTS(SELECT 1 FROM sys_permission p WHERE p.permission_code=seed.code);

INSERT INTO sys_role_permission(role_id,permission_id,scope_type)
SELECT r.id,p.id,r.scope_type FROM sys_role r JOIN sys_permission p
WHERE ((r.role_code='CUSTOMER' AND p.permission_code IN ('coupon:read:self','coupon:claim'))
    OR (r.role_code='SHOP_ADMIN' AND p.permission_code IN (
        'shop:coupon:read','shop:coupon:manage','shop:coupon:grant','shop:coupon:funding:approve'))
    OR (r.role_code='SUPER_ADMIN' AND p.permission_code IN (
        'platform:coupon:read','platform:coupon:manage','platform:coupon:grant','platform:coupon:governance')))
AND p.scope_type=r.scope_type
AND NOT EXISTS(SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
COMMIT;
