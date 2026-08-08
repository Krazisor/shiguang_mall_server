-- Shiguang Market after-sale appeal and phase-3 migration
-- MySQL 8.0.16+ only. Run after schema.sql and schema2.sql on database `market`.
--
-- This migration adds the current-period after-sale appeal/notification domain
-- plus the planned shop-owned merchant wallet domain. It deliberately does
-- not alter the buyer wallet tables or existing phase-1/phase-2 enums.
-- MySQL implicitly commits DDL; the permission seed DML is transactional.

USE `market`;

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ============================================================
-- 1. Shop merchant wallet aggregate
-- ============================================================

CREATE TABLE IF NOT EXISTS merchant_wallet_account (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    shop_id                  BIGINT UNSIGNED NOT NULL,
    currency                 CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CNY',
    pending_balance          DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    available_balance        DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    frozen_balance           DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    lifetime_gross_income    DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    lifetime_commission      DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    lifetime_refund          DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    status                   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version                  INT UNSIGNED NOT NULL DEFAULT 0,
    created_at               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_wallet_account_shop (shop_id),
    UNIQUE KEY uk_merchant_wallet_account_id_shop (id, shop_id),
    KEY idx_merchant_wallet_account_status (status, updated_at),
    CONSTRAINT fk_merchant_wallet_account_shop
        FOREIGN KEY (shop_id) REFERENCES shop (id),
    CONSTRAINT chk_merchant_wallet_account_currency
        CHECK (currency = 'CNY'),
    CONSTRAINT chk_merchant_wallet_account_balance
        CHECK (
            pending_balance >= 0
            AND available_balance >= 0
            AND frozen_balance >= 0
        ),
    CONSTRAINT chk_merchant_wallet_account_lifetime
        CHECK (
            lifetime_gross_income >= 0
            AND lifetime_commission >= 0
            AND lifetime_refund >= 0
        ),
    CONSTRAINT chk_merchant_wallet_account_status
        CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Shop-owned merchant wallet aggregate; independent from buyer wallet';

-- ============================================================
-- 2. Per-shop order settlement snapshot
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_settlement (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    settlement_no            VARCHAR(64) NOT NULL,
    shop_id                  BIGINT UNSIGNED NOT NULL,
    wallet_id                BIGINT UNSIGNED NOT NULL,
    trade_id                 BIGINT UNSIGNED NOT NULL,
    order_id                 BIGINT UNSIGNED NOT NULL,
    status                   VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    gross_amount             DECIMAL(18,2) NOT NULL,
    commission_rate          DECIMAL(7,4) NOT NULL DEFAULT 0.0000 COMMENT 'Percentage; 0.1250 means 0.1250%',
    commission_refundable    TINYINT(1) NOT NULL DEFAULT 1,
    commission_amount        DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    buyer_refund_amount      DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    commission_refund_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    merchant_refund_amount   DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    net_amount               DECIMAL(18,2) NOT NULL,
    pending_amount           DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    released_amount          DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    available_at             DATETIME(3) NULL,
    settled_at               DATETIME(3) NULL,
    version                  INT UNSIGNED NOT NULL DEFAULT 0,
    created_at               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shop_settlement_no (settlement_no),
    UNIQUE KEY uk_shop_settlement_id_shop (id, shop_id),
    UNIQUE KEY uk_shop_settlement_order_shop (order_id, shop_id),
    KEY idx_shop_settlement_shop_status (shop_id, status, created_at),
    KEY idx_shop_settlement_trade (trade_id, created_at),
    KEY idx_shop_settlement_release (status, available_at),
    CONSTRAINT fk_shop_settlement_shop
        FOREIGN KEY (shop_id) REFERENCES shop (id),
    CONSTRAINT fk_shop_settlement_wallet_shop
        FOREIGN KEY (wallet_id, shop_id)
        REFERENCES merchant_wallet_account (id, shop_id),
    CONSTRAINT fk_shop_settlement_trade
        FOREIGN KEY (trade_id) REFERENCES trade_order (id),
    CONSTRAINT fk_shop_settlement_order_shop
        FOREIGN KEY (order_id, shop_id) REFERENCES order_info (id, shop_id),
    CONSTRAINT chk_shop_settlement_status
        CHECK (status IN ('PENDING', 'READY', 'SETTLED', 'REFUNDED', 'RECOVERY_REQUIRED')),
    CONSTRAINT chk_shop_settlement_amounts
        CHECK (
            gross_amount > 0
            AND commission_rate BETWEEN 0.0000 AND 100.0000
            AND commission_refundable IN (0, 1)
            AND commission_amount >= 0
            AND commission_amount <= gross_amount
            AND net_amount = gross_amount - commission_amount
            AND buyer_refund_amount >= 0
            AND buyer_refund_amount <= gross_amount
            AND commission_refund_amount >= 0
            AND commission_refund_amount <= commission_amount
            AND (commission_refundable = 1 OR commission_refund_amount = 0.00)
            AND buyer_refund_amount >= commission_refund_amount
            AND merchant_refund_amount = buyer_refund_amount - commission_refund_amount
            AND merchant_refund_amount <= net_amount
            AND pending_amount >= 0
            AND released_amount >= 0
            AND pending_amount + released_amount + merchant_refund_amount = net_amount
        ),
    CONSTRAINT chk_shop_settlement_times
        CHECK (
            (available_at IS NULL OR available_at >= created_at)
            AND (settled_at IS NULL OR available_at IS NOT NULL)
            AND (settled_at IS NULL OR settled_at >= available_at)
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='One shop settlement record for one child order';

-- ============================================================
-- 3. Virtual merchant withdrawal request
-- ============================================================

CREATE TABLE IF NOT EXISTS merchant_withdrawal (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    withdrawal_no         VARCHAR(64) NOT NULL,
    wallet_id             BIGINT UNSIGNED NOT NULL,
    shop_id               BIGINT UNSIGNED NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    amount                DECIMAL(18,2) NOT NULL,
    fee_amount            DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    net_amount            DECIMAL(18,2) NOT NULL,
    destination_type      VARCHAR(30) NOT NULL DEFAULT 'VIRTUAL_ACCOUNT',
    destination_account   VARCHAR(128) NOT NULL,
    remark                VARCHAR(500) NULL,
    failure_reason        VARCHAR(500) NULL,
    requested_by          BIGINT UNSIGNED NOT NULL,
    requested_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at          DATETIME(3) NULL,
    version               INT UNSIGNED NOT NULL DEFAULT 0,
    business_no           VARCHAR(128) NOT NULL,
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_withdrawal_no (withdrawal_no),
    UNIQUE KEY uk_merchant_withdrawal_id_shop (id, shop_id),
    UNIQUE KEY uk_merchant_withdrawal_business_no (business_no),
    KEY idx_merchant_withdrawal_shop_status (shop_id, status, created_at),
    KEY idx_merchant_withdrawal_wallet_status (wallet_id, status, created_at),
    CONSTRAINT fk_merchant_withdrawal_wallet_shop
        FOREIGN KEY (wallet_id, shop_id)
        REFERENCES merchant_wallet_account (id, shop_id),
    CONSTRAINT fk_merchant_withdrawal_requested_by
        FOREIGN KEY (requested_by) REFERENCES sys_user (id),
    CONSTRAINT chk_merchant_withdrawal_status
        CHECK (status IN ('PROCESSING', 'SUCCESS', 'FAILED', 'REJECTED')),
    CONSTRAINT chk_merchant_withdrawal_amounts
        CHECK (
            amount > 0
            AND fee_amount >= 0
            AND fee_amount <= amount
            AND net_amount = amount - fee_amount
        ),
    CONSTRAINT chk_merchant_withdrawal_destination
        CHECK (destination_type = 'VIRTUAL_ACCOUNT'),
    CONSTRAINT chk_merchant_withdrawal_state
        CHECK (
            (status = 'PROCESSING' AND completed_at IS NULL AND failure_reason IS NULL)
            OR (status = 'SUCCESS' AND completed_at IS NOT NULL AND failure_reason IS NULL)
            OR (status IN ('FAILED', 'REJECTED') AND completed_at IS NOT NULL AND failure_reason IS NOT NULL)
        ),
    CONSTRAINT chk_merchant_withdrawal_completed_time
        CHECK (completed_at IS NULL OR completed_at >= requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Virtual merchant withdrawal request; no external payment channel';

-- ============================================================
-- 4. Immutable merchant wallet ledger
-- ============================================================

CREATE TABLE IF NOT EXISTS merchant_wallet_transaction (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    transaction_no      VARCHAR(64) NOT NULL,
    wallet_id           BIGINT UNSIGNED NOT NULL,
    shop_id             BIGINT UNSIGNED NOT NULL,
    transaction_type    VARCHAR(32) NOT NULL,
    direction           VARCHAR(10) NOT NULL,
    source_bucket       VARCHAR(12) NULL,
    target_bucket       VARCHAR(12) NULL,
    amount              DECIMAL(18,2) NOT NULL,
    pending_before      DECIMAL(18,2) NOT NULL,
    pending_after       DECIMAL(18,2) NOT NULL,
    available_before    DECIMAL(18,2) NOT NULL,
    available_after     DECIMAL(18,2) NOT NULL,
    frozen_before       DECIMAL(18,2) NOT NULL,
    frozen_after        DECIMAL(18,2) NOT NULL,
    business_type       VARCHAR(40) NOT NULL,
    business_no         VARCHAR(128) NOT NULL,
    settlement_id       BIGINT UNSIGNED NULL,
    order_id            BIGINT UNSIGNED NULL,
    withdrawal_id       BIGINT UNSIGNED NULL,
    operator_id         BIGINT UNSIGNED NULL,
    remark              VARCHAR(500) NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_wallet_transaction_no (transaction_no),
    UNIQUE KEY uk_merchant_wallet_transaction_business (business_type, business_no),
    KEY idx_merchant_wallet_transaction_wallet (wallet_id, created_at),
    KEY idx_merchant_wallet_transaction_shop (shop_id, created_at),
    KEY idx_merchant_wallet_transaction_settlement (settlement_id, created_at),
    KEY idx_merchant_wallet_transaction_order (order_id, created_at),
    KEY idx_merchant_wallet_transaction_order_shop (order_id, shop_id, created_at),
    KEY idx_merchant_wallet_transaction_withdrawal (withdrawal_id, created_at),
    CONSTRAINT fk_merchant_wallet_transaction_wallet_shop
        FOREIGN KEY (wallet_id, shop_id)
        REFERENCES merchant_wallet_account (id, shop_id),
    CONSTRAINT fk_merchant_wallet_transaction_settlement
        FOREIGN KEY (settlement_id, shop_id) REFERENCES shop_settlement (id, shop_id),
    CONSTRAINT fk_merchant_wallet_transaction_order
        FOREIGN KEY (order_id, shop_id) REFERENCES order_info (id, shop_id),
    CONSTRAINT fk_merchant_wallet_transaction_withdrawal
        FOREIGN KEY (withdrawal_id, shop_id) REFERENCES merchant_withdrawal (id, shop_id),
    CONSTRAINT fk_merchant_wallet_transaction_operator
        FOREIGN KEY (operator_id) REFERENCES sys_user (id),
    CONSTRAINT chk_merchant_wallet_transaction_type
        CHECK (transaction_type IN (
            'ORDER_PENDING_CREDIT',
            'SETTLEMENT_RELEASE',
            'COMMISSION_DEBIT',
            'REFUND_DEBIT',
            'WITHDRAW_FREEZE',
            'WITHDRAW_SUCCESS',
            'WITHDRAW_FAILED',
            'WITHDRAW_REJECT',
            'PLATFORM_ADJUST'
        )),
    CONSTRAINT chk_merchant_wallet_transaction_direction
        CHECK (direction IN ('CREDIT', 'DEBIT', 'TRANSFER')),
    CONSTRAINT chk_merchant_wallet_transaction_bucket_shape
        CHECK (
            (direction = 'CREDIT' AND source_bucket IS NULL AND target_bucket IN ('PENDING', 'AVAILABLE', 'FROZEN'))
            OR (direction = 'DEBIT' AND source_bucket IN ('PENDING', 'AVAILABLE', 'FROZEN') AND target_bucket IS NULL)
            OR (direction = 'TRANSFER'
                AND source_bucket IN ('PENDING', 'AVAILABLE', 'FROZEN')
                AND target_bucket IN ('PENDING', 'AVAILABLE', 'FROZEN')
                AND source_bucket <> target_bucket)
        ),
    CONSTRAINT chk_merchant_wallet_transaction_amounts
        CHECK (
            amount > 0
            AND pending_before >= 0 AND pending_after >= 0
            AND available_before >= 0 AND available_after >= 0
            AND frozen_before >= 0 AND frozen_after >= 0
        ),
    CONSTRAINT chk_merchant_wallet_transaction_balance
        CHECK (
            pending_after = pending_before
                + CASE
                    WHEN direction = 'CREDIT' AND target_bucket = 'PENDING' THEN amount
                    WHEN direction = 'DEBIT' AND source_bucket = 'PENDING' THEN -amount
                    WHEN direction = 'TRANSFER' AND target_bucket = 'PENDING' THEN amount
                    WHEN direction = 'TRANSFER' AND source_bucket = 'PENDING' THEN -amount
                    ELSE 0
                  END
            AND available_after = available_before
                + CASE
                    WHEN direction = 'CREDIT' AND target_bucket = 'AVAILABLE' THEN amount
                    WHEN direction = 'DEBIT' AND source_bucket = 'AVAILABLE' THEN -amount
                    WHEN direction = 'TRANSFER' AND target_bucket = 'AVAILABLE' THEN amount
                    WHEN direction = 'TRANSFER' AND source_bucket = 'AVAILABLE' THEN -amount
                    ELSE 0
                  END
            AND frozen_after = frozen_before
                + CASE
                    WHEN direction = 'CREDIT' AND target_bucket = 'FROZEN' THEN amount
                    WHEN direction = 'DEBIT' AND source_bucket = 'FROZEN' THEN -amount
                    WHEN direction = 'TRANSFER' AND target_bucket = 'FROZEN' THEN amount
                    WHEN direction = 'TRANSFER' AND source_bucket = 'FROZEN' THEN -amount
                    ELSE 0
                  END
        ),
    CONSTRAINT chk_merchant_wallet_transaction_type_shape
        CHECK (
            (transaction_type = 'ORDER_PENDING_CREDIT'
                AND direction = 'CREDIT' AND target_bucket = 'PENDING')
            OR (transaction_type = 'SETTLEMENT_RELEASE'
                AND direction = 'TRANSFER' AND source_bucket = 'PENDING' AND target_bucket = 'AVAILABLE')
            OR (transaction_type = 'COMMISSION_DEBIT'
                AND direction = 'DEBIT' AND source_bucket = 'PENDING')
            OR (transaction_type = 'REFUND_DEBIT'
                AND direction = 'DEBIT' AND source_bucket IN ('PENDING', 'AVAILABLE'))
            OR (transaction_type = 'WITHDRAW_FREEZE'
                AND direction = 'TRANSFER' AND source_bucket = 'AVAILABLE' AND target_bucket = 'FROZEN')
            OR (transaction_type = 'WITHDRAW_SUCCESS'
                AND direction = 'DEBIT' AND source_bucket = 'FROZEN')
            OR (transaction_type IN ('WITHDRAW_FAILED', 'WITHDRAW_REJECT')
                AND direction = 'TRANSFER' AND source_bucket = 'FROZEN' AND target_bucket = 'AVAILABLE')
            OR (transaction_type = 'PLATFORM_ADJUST'
                AND direction IN ('CREDIT', 'DEBIT'))
        ),
    CONSTRAINT chk_merchant_wallet_transaction_adjust_audit
        CHECK (
            transaction_type <> 'PLATFORM_ADJUST'
            OR (operator_id IS NOT NULL AND remark IS NOT NULL AND TRIM(remark) <> '')
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Immutable shop merchant wallet ledger with bucket transfers';

-- ============================================================
-- 5. After-sale appeal and merchant notification
--
-- These tables are the follow-up migration required by the current
-- after-sale dispute workflow. They do not alter the baseline
-- after_sale_request status enum; platform decisions drive that existing
-- aggregate through the application transaction service.
-- ============================================================

CREATE TABLE IF NOT EXISTS after_sale_appeal (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    appeal_no             VARCHAR(64) NOT NULL,
    after_sale_id         BIGINT UNSIGNED NOT NULL,
    shop_id               BIGINT UNSIGNED NOT NULL,
    appellant_user_id     BIGINT UNSIGNED NOT NULL,
    trigger_type          VARCHAR(30) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason_code           VARCHAR(30) NOT NULL,
    reason_description    VARCHAR(500) NOT NULL,
    evidence_json         JSON NULL COMMENT 'Array of appeal evidence image URLs',
    merchant_reviewer_id   BIGINT UNSIGNED NULL,
    merchant_review_comment VARCHAR(500) NULL,
    merchant_reviewed_at   DATETIME(3) NULL,
    decision              VARCHAR(10) NULL,
    approved_quantity     INT UNSIGNED NULL,
    approved_amount       DECIMAL(18,2) NULL,
    decided_by            BIGINT UNSIGNED NULL,
    decision_comment      VARCHAR(500) NULL,
    decided_at            DATETIME(3) NULL,
    version               INT UNSIGNED NOT NULL DEFAULT 0,
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_after_sale_appeal_no (appeal_no),
    UNIQUE KEY uk_after_sale_appeal_after_sale (after_sale_id),
    UNIQUE KEY uk_after_sale_appeal_id_shop (id, shop_id),
    UNIQUE KEY uk_after_sale_appeal_id_after_sale (id, after_sale_id),
    KEY idx_after_sale_appeal_shop_status (shop_id, status, created_at),
    KEY idx_after_sale_appeal_appellant (appellant_user_id, created_at),
    KEY idx_after_sale_appeal_after_sale (after_sale_id, created_at),
    CONSTRAINT fk_after_sale_appeal_after_sale
        FOREIGN KEY (after_sale_id) REFERENCES after_sale_request (id),
    CONSTRAINT fk_after_sale_appeal_shop
        FOREIGN KEY (shop_id) REFERENCES shop (id),
    CONSTRAINT fk_after_sale_appeal_appellant
        FOREIGN KEY (appellant_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_after_sale_appeal_merchant_reviewer
        FOREIGN KEY (merchant_reviewer_id) REFERENCES sys_user (id),
    CONSTRAINT fk_after_sale_appeal_decided_by
        FOREIGN KEY (decided_by) REFERENCES sys_user (id),
    CONSTRAINT chk_after_sale_appeal_trigger
        CHECK (trigger_type IN ('MERCHANT_REJECTED', 'MERCHANT_TIMEOUT')),
    CONSTRAINT chk_after_sale_appeal_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_after_sale_appeal_evidence
        CHECK (evidence_json IS NULL OR JSON_TYPE(evidence_json) = 'ARRAY'),
    CONSTRAINT chk_after_sale_appeal_decision
        CHECK (decision IS NULL OR decision IN ('APPROVE', 'REJECT')),
    CONSTRAINT chk_after_sale_appeal_amount
        CHECK (
            (approved_quantity IS NULL OR approved_quantity > 0)
            AND (approved_amount IS NULL OR approved_amount > 0)
        ),
    CONSTRAINT chk_after_sale_appeal_merchant_snapshot
        CHECK (
            (trigger_type = 'MERCHANT_TIMEOUT'
                AND merchant_reviewer_id IS NULL
                AND merchant_review_comment IS NULL
                AND merchant_reviewed_at IS NULL)
            OR (trigger_type = 'MERCHANT_REJECTED'
                AND merchant_reviewer_id IS NOT NULL
                AND merchant_review_comment IS NOT NULL
                AND TRIM(merchant_review_comment) <> ''
                AND merchant_reviewed_at IS NOT NULL)
        ),
    CONSTRAINT chk_after_sale_appeal_state
        CHECK (
            (status = 'PENDING'
                AND decision IS NULL
                AND approved_quantity IS NULL
                AND approved_amount IS NULL
                AND decided_by IS NULL
                AND decision_comment IS NULL
                AND decided_at IS NULL)
            OR (status = 'APPROVED'
                AND decision = 'APPROVE'
                AND approved_quantity IS NOT NULL
                AND approved_amount IS NOT NULL
                AND decided_by IS NOT NULL
                AND decision_comment IS NOT NULL
                AND TRIM(decision_comment) <> ''
                AND decided_at IS NOT NULL)
            OR (status = 'REJECTED'
                AND decision = 'REJECT'
                AND approved_quantity IS NULL
                AND approved_amount IS NULL
                AND decided_by IS NOT NULL
                AND decision_comment IS NOT NULL
                AND TRIM(decision_comment) <> ''
                AND decided_at IS NOT NULL)
        ),
    CONSTRAINT chk_after_sale_appeal_times
        CHECK (
            (merchant_reviewed_at IS NULL OR merchant_reviewed_at >= created_at)
            AND (decided_at IS NULL OR decided_at >= created_at)
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Buyer appeal for merchant after-sale refusal or timeout';

CREATE TABLE IF NOT EXISTS merchant_notification (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    shop_id               BIGINT UNSIGNED NOT NULL,
    recipient_user_id     BIGINT UNSIGNED NOT NULL,
    appeal_id             BIGINT UNSIGNED NOT NULL,
    after_sale_id         BIGINT UNSIGNED NOT NULL,
    notification_type     VARCHAR(40) NOT NULL,
    title                 VARCHAR(128) NOT NULL,
    content               VARCHAR(2000) NOT NULL,
    read_at               DATETIME(3) NULL,
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_notification_recipient_event
        (appeal_id, notification_type, recipient_user_id),
    KEY idx_merchant_notification_recipient_read (recipient_user_id, read_at, created_at),
    KEY idx_merchant_notification_shop_created (shop_id, created_at),
    KEY idx_merchant_notification_appeal (appeal_id, created_at),
    CONSTRAINT fk_merchant_notification_shop
        FOREIGN KEY (shop_id) REFERENCES shop (id),
    CONSTRAINT fk_merchant_notification_recipient
        FOREIGN KEY (recipient_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_merchant_notification_appeal_shop
        FOREIGN KEY (appeal_id, shop_id) REFERENCES after_sale_appeal (id, shop_id),
    CONSTRAINT fk_merchant_notification_after_sale_appeal
        FOREIGN KEY (appeal_id, after_sale_id)
        REFERENCES after_sale_appeal (id, after_sale_id),
    CONSTRAINT chk_merchant_notification_type
        CHECK (notification_type IN ('AFTER_SALE_APPEAL_SUBMITTED', 'AFTER_SALE_APPEAL_DECIDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Deduplicated shop notification for after-sale appeals';

-- ============================================================
-- 6. Object-storage asset metadata
--
-- Binary content lives in MinIO. This table is the audit and ownership
-- record used by the upload API; it is not a replacement for product URL
-- fields and does not store binary data.
-- ============================================================

CREATE TABLE IF NOT EXISTS object_asset (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    asset_no            VARCHAR(64) NOT NULL,
    bucket              VARCHAR(128) NOT NULL,
    object_key          VARCHAR(512) NOT NULL,
    original_filename   VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    size_bytes          BIGINT UNSIGNED NOT NULL,
    sha256              CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    purpose             VARCHAR(32) NOT NULL,
    owner_user_id       BIGINT UNSIGNED NOT NULL,
    shop_id             BIGINT UNSIGNED NULL,
    public_url          VARCHAR(1024) NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at          DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_object_asset_no (asset_no),
    UNIQUE KEY uk_object_asset_bucket_key (bucket, object_key),
    KEY idx_object_asset_owner (owner_user_id, status, created_at),
    KEY idx_object_asset_shop (shop_id, status, created_at),
    KEY idx_object_asset_sha256 (sha256, content_type, size_bytes),
    CONSTRAINT fk_object_asset_owner
        FOREIGN KEY (owner_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_object_asset_shop
        FOREIGN KEY (shop_id) REFERENCES shop (id),
    CONSTRAINT chk_object_asset_size
        CHECK (size_bytes > 0),
    CONSTRAINT chk_object_asset_sha256
        CHECK (sha256 REGEXP '^[0-9a-fA-F]{64}$'),
    CONSTRAINT chk_object_asset_purpose
        CHECK (purpose IN (
            'AVATAR', 'SHOP_LOGO', 'BRAND_LOGO', 'PRODUCT_COVER',
            'PRODUCT_GALLERY', 'SKU_IMAGE', 'RICH_TEXT_IMAGE',
            'AFTER_SALE_EVIDENCE', 'APPEAL_EVIDENCE'
        )),
    CONSTRAINT chk_object_asset_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/gif', 'image/webp')),
    CONSTRAINT chk_object_asset_status
        CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_object_asset_deleted_time
        CHECK ((status = 'ACTIVE' AND deleted_at IS NULL)
            OR (status = 'DELETED' AND deleted_at IS NOT NULL)),
    CONSTRAINT chk_object_asset_shop_scope
        CHECK (
            (purpose IN ('PRODUCT_COVER', 'PRODUCT_GALLERY', 'SKU_IMAGE', 'RICH_TEXT_IMAGE')
                AND shop_id IS NOT NULL)
            OR (purpose NOT IN ('PRODUCT_COVER', 'PRODUCT_GALLERY', 'SKU_IMAGE', 'RICH_TEXT_IMAGE')
                AND shop_id IS NULL)
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='MinIO object metadata and ownership audit record';

-- ============================================================
-- 7. Phase-3 RBAC seed data
-- ============================================================

-- Reuse schema2.sql's platform:operation:read and platform:task:execute.
-- Do not add platform:settlement:manage: phase-3 first release has no
-- platform write/adjustment endpoint for that permission.

START TRANSACTION;

UPDATE sys_role
SET description = '审核和管理店铺、处理平台售后申诉'
WHERE role_code = 'PLATFORM_SHOP_ADMIN';

INSERT INTO sys_permission (
    permission_code,
    permission_name,
    scope_type,
    resource,
    http_method
)
SELECT
    'asset:upload',
    '上传图片到对象存储',
    'PLATFORM',
    '/api/assets/images',
    'POST'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission
    WHERE permission_code = 'asset:upload'
);

-- Product image uploads are shop-scoped and reuse the existing
-- shop:product:manage permission; asset:upload is platform-scoped for
-- avatars, platform logos and buyer after-sale evidence.

INSERT INTO sys_permission (
    permission_code,
    permission_name,
    scope_type,
    resource,
    http_method
)
SELECT
    'shop:wallet:read',
    '查看本店商家钱包与结算',
    'SHOP',
    '/api/shops/*/merchant-wallet/**',
    'GET'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission
    WHERE permission_code = 'shop:wallet:read'
);

INSERT INTO sys_permission (
    permission_code,
    permission_name,
    scope_type,
    resource,
    http_method
)
SELECT
    'after-sale:appeal',
    '提交本人售后申诉',
    'PLATFORM',
    '/api/after-sales/*/appeal',
    'POST'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission
    WHERE permission_code = 'after-sale:appeal'
);

INSERT INTO sys_permission (
    permission_code,
    permission_name,
    scope_type,
    resource,
    http_method
)
SELECT
    'platform:after-sale:manage',
    '平台处理售后申诉',
    'PLATFORM',
    '/api/platform/after-sale-appeals/**',
    NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission
    WHERE permission_code = 'platform:after-sale:manage'
);

INSERT INTO sys_permission (
    permission_code,
    permission_name,
    scope_type,
    resource,
    http_method
)
SELECT
    'shop:notification:read',
    '查看本店售后申诉通知',
    'SHOP',
    '/api/shops/*/notifications/**',
    NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission
    WHERE permission_code = 'shop:notification:read'
);

INSERT INTO sys_permission (
    permission_code,
    permission_name,
    scope_type,
    resource,
    http_method
)
SELECT
    'shop:wallet:withdraw',
    '申请本店虚拟提现',
    'SHOP',
    '/api/shops/*/merchant-wallet/withdrawals',
    'POST'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission
    WHERE permission_code = 'shop:wallet:withdraw'
);

INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type
FROM sys_role r
JOIN sys_permission p
  ON p.permission_code IN ('shop:wallet:read', 'shop:wallet:withdraw')
 AND p.scope_type = r.scope_type
WHERE r.role_code = 'SHOP_ADMIN'
  AND r.scope_type = 'SHOP'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type
FROM sys_role r
JOIN sys_permission p
  ON p.permission_code = 'asset:upload'
 AND p.scope_type = r.scope_type
WHERE r.role_code IN (
    'CUSTOMER', 'PLATFORM_SHOP_ADMIN', 'PLATFORM_PRODUCT_AUDITOR', 'SUPER_ADMIN'
)
  AND r.scope_type = 'PLATFORM'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type
FROM sys_role r
JOIN sys_permission p
  ON p.permission_code = 'after-sale:appeal'
 AND p.scope_type = r.scope_type
WHERE r.role_code = 'CUSTOMER'
  AND r.scope_type = 'PLATFORM'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type
FROM sys_role r
JOIN sys_permission p
  ON p.permission_code = 'platform:after-sale:manage'
 AND p.scope_type = r.scope_type
WHERE r.role_code = 'SUPER_ADMIN'
  AND r.scope_type = 'PLATFORM'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type
FROM sys_role r
JOIN sys_permission p
  ON p.permission_code = 'platform:after-sale:manage'
 AND p.scope_type = r.scope_type
WHERE r.role_code = 'PLATFORM_SHOP_ADMIN'
  AND r.scope_type = 'PLATFORM'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type
FROM sys_role r
JOIN sys_permission p
  ON p.permission_code = 'shop:notification:read'
 AND p.scope_type = r.scope_type
WHERE r.role_code IN ('SHOP_ADMIN', 'SHOP_ORDER_OPERATOR')
  AND r.scope_type = 'SHOP'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

COMMIT;
