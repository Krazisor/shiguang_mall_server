-- Shiguang Market current-stage schema
-- MySQL 8.0.16+ only. Run on an empty database.

CREATE DATABASE IF NOT EXISTS `market`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `market`;

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ============================================================
-- 1. Users and scoped RBAC
-- ============================================================

CREATE TABLE sys_user (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username        VARCHAR(64) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL COMMENT 'BCrypt or Argon2 digest',
    nickname        VARCHAR(64) NOT NULL,
    phone           VARCHAR(32) NULL,
    email           VARCHAR(128) NULL,
    avatar_url      VARCHAR(1024) NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at   DATETIME(3) NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at      DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    UNIQUE KEY uk_sys_user_phone (phone),
    UNIQUE KEY uk_sys_user_email (email),
    KEY idx_sys_user_status_created (status, created_at),
    CONSTRAINT chk_sys_user_status CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Platform login account';

CREATE TABLE sys_role (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role_code       VARCHAR(64) NOT NULL,
    role_name       VARCHAR(64) NOT NULL,
    scope_type      VARCHAR(20) NOT NULL COMMENT 'PLATFORM or SHOP',
    description     VARCHAR(255) NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (role_code),
    UNIQUE KEY uk_sys_role_id_scope (id, scope_type),
    CONSTRAINT chk_sys_role_scope CHECK (scope_type IN ('PLATFORM', 'SHOP')),
    CONSTRAINT chk_sys_role_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Platform or shop scoped RBAC role';

CREATE TABLE sys_permission (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(64) NOT NULL,
    scope_type      VARCHAR(20) NOT NULL COMMENT 'PLATFORM or SHOP',
    resource        VARCHAR(255) NULL COMMENT 'Route or API pattern',
    http_method     VARCHAR(10) NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (permission_code),
    UNIQUE KEY uk_sys_permission_id_scope (id, scope_type),
    CONSTRAINT chk_sys_permission_scope CHECK (scope_type IN ('PLATFORM', 'SHOP')),
    CONSTRAINT chk_sys_permission_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='API permission';

CREATE TABLE sys_user_role (
    user_id         BIGINT UNSIGNED NOT NULL,
    role_id         BIGINT UNSIGNED NOT NULL,
    role_scope      VARCHAR(20) NOT NULL DEFAULT 'PLATFORM',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, role_id),
    KEY idx_sys_user_role_role (role_id, user_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id, role_scope) REFERENCES sys_role (id, scope_type),
    CONSTRAINT chk_sys_user_role_scope CHECK (role_scope = 'PLATFORM')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Platform role assignment; role scope must be PLATFORM';

CREATE TABLE sys_role_permission (
    role_id         BIGINT UNSIGNED NOT NULL,
    permission_id   BIGINT UNSIGNED NOT NULL,
    scope_type      VARCHAR(20) NOT NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (role_id, permission_id),
    KEY idx_sys_role_permission_permission (permission_id, role_id),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id, scope_type) REFERENCES sys_role (id, scope_type),
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id, scope_type) REFERENCES sys_permission (id, scope_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Role-permission mapping';

CREATE TABLE user_address (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    recipient_name      VARCHAR(64) NOT NULL,
    recipient_phone     VARCHAR(32) NOT NULL,
    province_name       VARCHAR(64) NOT NULL,
    city_name           VARCHAR(64) NOT NULL,
    district_name       VARCHAR(64) NOT NULL,
    detail_address      VARCHAR(255) NOT NULL,
    is_default          TINYINT(1) NOT NULL DEFAULT 0,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at          DATETIME(3) NULL,
    default_user_id     BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN is_default = 1 AND deleted_at IS NULL THEN user_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_address_one_default (default_user_id),
    KEY idx_user_address_user (user_id, deleted_at, created_at),
    CONSTRAINT fk_user_address_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_user_address_default CHECK (is_default IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User shipping address';

-- ============================================================
-- 2. Shops and shop data scope
-- ============================================================

CREATE TABLE shop (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    shop_no         VARCHAR(64) NOT NULL,
    shop_name       VARCHAR(128) NOT NULL,
    logo_url        VARCHAR(1024) NULL,
    description     VARCHAR(500) NULL,
    contact_name    VARCHAR(64) NULL,
    contact_phone   VARCHAR(32) NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shop_no (shop_no),
    KEY idx_shop_status_created (status, created_at),
    CONSTRAINT chk_shop_status CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Shop and tenant boundary';

CREATE TABLE shop_user (
    shop_id         BIGINT UNSIGNED NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    role_id         BIGINT UNSIGNED NOT NULL COMMENT 'Role scope must be SHOP',
    role_scope      VARCHAR(20) NOT NULL DEFAULT 'SHOP',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (shop_id, user_id),
    KEY idx_shop_user_user (user_id, status),
    KEY idx_shop_user_role (role_id),
    CONSTRAINT fk_shop_user_shop FOREIGN KEY (shop_id) REFERENCES shop (id),
    CONSTRAINT fk_shop_user_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_shop_user_role FOREIGN KEY (role_id, role_scope) REFERENCES sys_role (id, scope_type),
    CONSTRAINT chk_shop_user_role_scope CHECK (role_scope = 'SHOP'),
    CONSTRAINT chk_shop_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='One current role per user in a shop';

-- ============================================================
-- 3. Product catalog and governance
-- ============================================================

CREATE TABLE product_category (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_id       BIGINT UNSIGNED NULL,
    category_name   VARCHAR(64) NOT NULL,
    category_code   VARCHAR(64) NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_category_code (category_code),
    KEY idx_product_category_parent (parent_id, status, sort_order),
    CONSTRAINT fk_product_category_parent FOREIGN KEY (parent_id) REFERENCES product_category (id),
    CONSTRAINT chk_product_category_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Platform product category tree';

CREATE TABLE product_category_attribute (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    category_id     BIGINT UNSIGNED NOT NULL,
    attribute_name  VARCHAR(64) NOT NULL,
    value_type      VARCHAR(20) NOT NULL COMMENT 'TEXT, NUMBER, BOOLEAN, OPTION',
    unit            VARCHAR(32) NULL,
    is_required     TINYINT(1) NOT NULL DEFAULT 0,
    is_filterable   TINYINT(1) NOT NULL DEFAULT 0,
    options_json    JSON NULL COMMENT 'String array for OPTION attributes',
    sort_order      INT NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_attribute_name (category_id, attribute_name),
    UNIQUE KEY uk_category_attribute_id_category (id, category_id),
    KEY idx_category_attribute_category (category_id, status, sort_order),
    CONSTRAINT fk_category_attribute_category FOREIGN KEY (category_id) REFERENCES product_category (id),
    CONSTRAINT chk_category_attribute_type CHECK (value_type IN ('TEXT', 'NUMBER', 'BOOLEAN', 'OPTION')),
    CONSTRAINT chk_category_attribute_flags CHECK (is_required IN (0, 1) AND is_filterable IN (0, 1)),
    CONSTRAINT chk_category_attribute_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT chk_category_attribute_options CHECK (
        (value_type = 'OPTION' AND options_json IS NOT NULL AND JSON_TYPE(options_json) = 'ARRAY')
        OR (value_type <> 'OPTION' AND options_json IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Leaf-category descriptive attribute template';

CREATE TABLE product_brand (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    brand_name      VARCHAR(128) NOT NULL,
    brand_code      VARCHAR(64) NOT NULL,
    logo_url        VARCHAR(1024) NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_brand_code (brand_code),
    CONSTRAINT chk_product_brand_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Platform product brand';

CREATE TABLE product_spu (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    shop_id             BIGINT UNSIGNED NOT NULL,
    category_id         BIGINT UNSIGNED NOT NULL,
    brand_id            BIGINT UNSIGNED NULL,
    spu_no              VARCHAR(64) NOT NULL,
    product_name        VARCHAR(255) NOT NULL,
    subtitle            VARCHAR(500) NULL,
    cover_url           VARCHAR(1024) NULL,
    gallery_json        JSON NULL COMMENT 'Ordered string array of gallery image URLs',
    detail_html         LONGTEXT NULL COMMENT 'Sanitized HTML, including detail images',
    packing_list        TEXT NULL,
    service_note        TEXT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    content_version     INT UNSIGNED NOT NULL DEFAULT 0,
    created_by          BIGINT UNSIGNED NOT NULL,
    updated_by          BIGINT UNSIGNED NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at          DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_spu_no (spu_no),
    UNIQUE KEY uk_product_spu_id_shop (id, shop_id),
    UNIQUE KEY uk_product_spu_id_category (id, category_id),
    KEY idx_product_spu_shop_status (shop_id, status, created_at),
    KEY idx_product_spu_category_status (category_id, status, created_at),
    CONSTRAINT fk_product_spu_shop FOREIGN KEY (shop_id) REFERENCES shop (id),
    CONSTRAINT fk_product_spu_category FOREIGN KEY (category_id) REFERENCES product_category (id),
    CONSTRAINT fk_product_spu_brand FOREIGN KEY (brand_id) REFERENCES product_brand (id),
    CONSTRAINT fk_product_spu_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_product_spu_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id),
    CONSTRAINT chk_product_spu_status CHECK (status IN (
        'DRAFT', 'PENDING_REVIEW', 'REJECTED', 'OFF_SHELF', 'ON_SHELF', 'BANNED'
    )),
    CONSTRAINT chk_product_spu_gallery CHECK (
        gallery_json IS NULL OR JSON_TYPE(gallery_json) = 'ARRAY'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Shop-owned standard product and governed content';

CREATE TABLE product_attribute_value (
    spu_id              BIGINT UNSIGNED NOT NULL,
    category_id         BIGINT UNSIGNED NOT NULL,
    attribute_id        BIGINT UNSIGNED NOT NULL,
    attribute_value     VARCHAR(1000) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (spu_id, attribute_id),
    KEY idx_product_attribute_value_attribute (attribute_id, attribute_value(191)),
    CONSTRAINT fk_product_attribute_value_spu FOREIGN KEY (spu_id, category_id) REFERENCES product_spu (id, category_id),
    CONSTRAINT fk_product_attribute_value_template FOREIGN KEY (attribute_id, category_id) REFERENCES product_category_attribute (id, category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SPU descriptive attribute value';

CREATE TABLE product_sku (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    spu_id          BIGINT UNSIGNED NOT NULL,
    shop_id         BIGINT UNSIGNED NOT NULL,
    sku_no          VARCHAR(64) NOT NULL,
    sku_name        VARCHAR(255) NOT NULL,
    spec_json       JSON NOT NULL COMMENT 'Sale-spec object canonicalized by the application before hashing',
    spec_key        CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Lowercase SHA-256 hex of canonical spec_json',
    sale_price      DECIMAL(18,2) NOT NULL,
    market_price    DECIMAL(18,2) NULL,
    barcode         VARCHAR(64) NULL,
    image_url       VARCHAR(1024) NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    version         INT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at      DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_sku_no (sku_no),
    UNIQUE KEY uk_product_sku_spec (spu_id, spec_key),
    UNIQUE KEY uk_product_sku_id_spu_shop (id, spu_id, shop_id),
    KEY idx_product_sku_spu_status (spu_id, status),
    KEY idx_product_sku_barcode (barcode),
    CONSTRAINT fk_product_sku_spu_shop FOREIGN KEY (spu_id, shop_id) REFERENCES product_spu (id, shop_id),
    CONSTRAINT chk_product_sku_price CHECK (
        sale_price > 0 AND (market_price IS NULL OR market_price >= sale_price)
    ),
    CONSTRAINT chk_product_sku_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT chk_product_sku_spec_json CHECK (JSON_TYPE(spec_json) = 'OBJECT'),
    CONSTRAINT chk_product_sku_spec_key CHECK (spec_key REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Shop-owned purchasable SKU';

CREATE TABLE product_status_history (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    spu_id          BIGINT UNSIGNED NOT NULL,
    from_status     VARCHAR(30) NULL,
    to_status       VARCHAR(30) NOT NULL,
    operation_type  VARCHAR(30) NOT NULL,
    content_version INT UNSIGNED NOT NULL,
    operator_type   VARCHAR(20) NOT NULL,
    operator_id     BIGINT UNSIGNED NULL COMMENT 'NULL only for system operations',
    reason          VARCHAR(500) NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_product_status_history_spu (spu_id, created_at),
    KEY idx_product_status_history_review (to_status, created_at),
    CONSTRAINT fk_product_status_history_spu FOREIGN KEY (spu_id) REFERENCES product_spu (id),
    CONSTRAINT fk_product_status_history_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id),
    CONSTRAINT chk_product_status_history_from CHECK (
        from_status IS NULL OR from_status IN (
            'DRAFT', 'PENDING_REVIEW', 'REJECTED', 'OFF_SHELF', 'ON_SHELF', 'BANNED'
        )
    ),
    CONSTRAINT chk_product_status_history_to CHECK (to_status IN (
        'DRAFT', 'PENDING_REVIEW', 'REJECTED', 'OFF_SHELF', 'ON_SHELF', 'BANNED'
    )),
    CONSTRAINT chk_product_status_history_operation CHECK (operation_type IN (
        'CREATE', 'SUBMIT_REVIEW', 'APPROVE', 'REJECT', 'PUT_ON_SHELF',
        'TAKE_OFF_SHELF', 'BAN', 'UNBAN', 'CONTENT_CHANGED'
    )),
    CONSTRAINT chk_product_status_history_from_presence CHECK (
        (operation_type = 'CREATE' AND from_status IS NULL)
        OR (operation_type <> 'CREATE' AND from_status IS NOT NULL)
    ),
    CONSTRAINT chk_product_status_history_operator CHECK (
        operator_type IN ('SHOP', 'PLATFORM', 'SYSTEM')
    ),
    CONSTRAINT chk_product_status_history_transition CHECK (
        (operation_type = 'CREATE' AND from_status IS NULL AND to_status = 'DRAFT')
        OR (operation_type = 'SUBMIT_REVIEW' AND from_status = 'DRAFT' AND to_status = 'PENDING_REVIEW')
        OR (operation_type = 'APPROVE' AND from_status = 'PENDING_REVIEW' AND to_status = 'OFF_SHELF')
        OR (operation_type = 'REJECT' AND from_status = 'PENDING_REVIEW' AND to_status = 'REJECTED')
        OR (operation_type = 'PUT_ON_SHELF' AND from_status = 'OFF_SHELF' AND to_status = 'ON_SHELF')
        OR (operation_type = 'TAKE_OFF_SHELF' AND from_status = 'ON_SHELF' AND to_status = 'OFF_SHELF')
        OR (operation_type = 'BAN' AND from_status IN ('OFF_SHELF', 'ON_SHELF') AND to_status = 'BANNED')
        OR (operation_type = 'UNBAN' AND from_status = 'BANNED' AND to_status = 'OFF_SHELF')
        OR (operation_type = 'CONTENT_CHANGED' AND from_status IN ('DRAFT', 'REJECTED', 'OFF_SHELF') AND to_status = 'DRAFT')
    ),
    CONSTRAINT chk_product_status_history_reason CHECK (
        operation_type NOT IN ('REJECT', 'BAN') OR reason IS NOT NULL
    ),
    CONSTRAINT chk_product_status_history_operator_id CHECK (
        (operator_type = 'SYSTEM' AND operator_id IS NULL)
        OR (operator_type <> 'SYSTEM' AND operator_id IS NOT NULL)
    ),
    CONSTRAINT chk_product_status_history_actor CHECK (
        (operation_type = 'CREATE' AND operator_type IN ('SHOP', 'SYSTEM'))
        OR (operation_type IN ('SUBMIT_REVIEW', 'PUT_ON_SHELF', 'CONTENT_CHANGED')
            AND operator_type = 'SHOP')
        OR (operation_type IN ('APPROVE', 'REJECT', 'BAN', 'UNBAN')
            AND operator_type = 'PLATFORM')
        OR (operation_type = 'TAKE_OFF_SHELF' AND operator_type IN ('SHOP', 'PLATFORM'))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Product review and listing audit trail';

-- ============================================================
-- 4. Inventory and cart
-- ============================================================

CREATE TABLE inventory_stock (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sku_id              BIGINT UNSIGNED NOT NULL,
    available_quantity  INT UNSIGNED NOT NULL DEFAULT 0,
    locked_quantity     INT UNSIGNED NOT NULL DEFAULT 0,
    version             INT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_stock_sku (sku_id),
    CONSTRAINT fk_inventory_stock_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SKU stock aggregate';

CREATE TABLE inventory_transaction (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    transaction_no      VARCHAR(64) NOT NULL,
    sku_id              BIGINT UNSIGNED NOT NULL,
    transaction_type    VARCHAR(30) NOT NULL,
    available_change    INT NOT NULL DEFAULT 0,
    locked_change       INT NOT NULL DEFAULT 0,
    available_after     INT UNSIGNED NOT NULL,
    locked_after        INT UNSIGNED NOT NULL,
    business_type       VARCHAR(30) NOT NULL,
    business_no         VARCHAR(64) NOT NULL,
    operator_id         BIGINT UNSIGNED NULL,
    remark              VARCHAR(500) NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_transaction_no (transaction_no),
    UNIQUE KEY uk_inventory_transaction_business (sku_id, transaction_type, business_type, business_no),
    KEY idx_inventory_transaction_sku (sku_id, created_at),
    KEY idx_inventory_transaction_business (business_type, business_no),
    CONSTRAINT fk_inventory_transaction_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id),
    CONSTRAINT fk_inventory_transaction_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id),
    CONSTRAINT chk_inventory_transaction_type CHECK (transaction_type IN (
        'INBOUND', 'LOCK', 'RELEASE', 'DEDUCT', 'RETURN', 'ADJUST'
    )),
    CONSTRAINT chk_inventory_transaction_nonzero CHECK (
        available_change <> 0 OR locked_change <> 0
    ),
    CONSTRAINT chk_inventory_transaction_before CHECK (
        CAST(available_after AS SIGNED) - available_change >= 0
        AND CAST(locked_after AS SIGNED) - locked_change >= 0
    ),
    CONSTRAINT chk_inventory_transaction_shape CHECK (
        (transaction_type IN ('INBOUND', 'RETURN') AND available_change > 0 AND locked_change = 0)
        OR (transaction_type = 'LOCK' AND available_change < 0 AND locked_change = -available_change)
        OR (transaction_type = 'RELEASE' AND available_change > 0 AND locked_change = -available_change)
        OR (transaction_type = 'DEDUCT' AND available_change = 0 AND locked_change < 0)
        OR transaction_type = 'ADJUST'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Immutable inventory ledger';

CREATE TABLE cart_item (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    sku_id          BIGINT UNSIGNED NOT NULL,
    quantity        INT UNSIGNED NOT NULL,
    selected        TINYINT(1) NOT NULL DEFAULT 1,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_item_user_sku (user_id, sku_id),
    KEY idx_cart_item_user (user_id, updated_at),
    CONSTRAINT fk_cart_item_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_cart_item_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id),
    CONSTRAINT chk_cart_item_quantity CHECK (quantity BETWEEN 1 AND 999),
    CONSTRAINT chk_cart_item_selected CHECK (selected IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Shopping cart item';

-- ============================================================
-- 5. Cross-shop checkout and shop orders
-- ============================================================

CREATE TABLE trade_order (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trade_no            VARCHAR(64) NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    trade_status        VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    payable_amount      DECIMAL(18,2) NOT NULL,
    recipient_name      VARCHAR(64) NOT NULL COMMENT 'Checkout-time address snapshot',
    recipient_phone     VARCHAR(32) NOT NULL,
    province_name       VARCHAR(64) NOT NULL,
    city_name           VARCHAR(64) NOT NULL,
    district_name       VARCHAR(64) NOT NULL,
    detail_address      VARCHAR(255) NOT NULL,
    pay_expire_at       DATETIME(3) NOT NULL,
    paid_at             DATETIME(3) NULL,
    cancelled_at        DATETIME(3) NULL,
    version             INT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_order_no (trade_no),
    UNIQUE KEY uk_trade_order_id_user (id, user_id),
    KEY idx_trade_order_user (user_id, created_at),
    KEY idx_trade_order_expire (trade_status, pay_expire_at),
    CONSTRAINT fk_trade_order_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_trade_order_status CHECK (trade_status IN (
        'PENDING_PAYMENT', 'PAID', 'CANCELLED'
    )),
    CONSTRAINT chk_trade_order_amount CHECK (payable_amount > 0),
    CONSTRAINT chk_trade_order_timestamps CHECK (
        (trade_status = 'PENDING_PAYMENT' AND paid_at IS NULL AND cancelled_at IS NULL)
        OR (trade_status = 'PAID' AND paid_at IS NOT NULL AND cancelled_at IS NULL)
        OR (trade_status = 'CANCELLED' AND paid_at IS NULL AND cancelled_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='One cross-shop checkout and one wallet payment';

CREATE TABLE order_info (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no            VARCHAR(64) NOT NULL,
    trade_id            BIGINT UNSIGNED NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    shop_id             BIGINT UNSIGNED NOT NULL,
    shop_name           VARCHAR(128) NOT NULL COMMENT 'Checkout-time shop name snapshot',
    order_status        VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_status      VARCHAR(30) NOT NULL DEFAULT 'UNPAID',
    item_amount         DECIMAL(18,2) NOT NULL,
    freight_amount      DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    payable_amount      DECIMAL(18,2) NOT NULL,
    refund_amount       DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    buyer_remark        VARCHAR(500) NULL,
    cancel_reason       VARCHAR(255) NULL,
    carrier_code        VARCHAR(64) NULL COMMENT 'Carrier for this shop order',
    carrier_name        VARCHAR(128) NULL,
    tracking_no         VARCHAR(128) NULL,
    shipped_at          DATETIME(3) NULL,
    completed_at        DATETIME(3) NULL,
    cancelled_at        DATETIME(3) NULL,
    version             INT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_info_no (order_no),
    UNIQUE KEY uk_order_info_trade_shop (trade_id, shop_id),
    UNIQUE KEY uk_order_info_id_user (id, user_id),
    UNIQUE KEY uk_order_info_id_shop (id, shop_id),
    KEY idx_order_info_user_status (user_id, order_status, created_at),
    KEY idx_order_info_shop_status (shop_id, order_status, created_at),
    KEY idx_order_info_auto_complete (order_status, shipped_at),
    UNIQUE KEY uk_order_info_tracking (carrier_code, tracking_no),
    CONSTRAINT fk_order_info_trade_user FOREIGN KEY (trade_id, user_id) REFERENCES trade_order (id, user_id),
    CONSTRAINT fk_order_info_shop FOREIGN KEY (shop_id) REFERENCES shop (id),
    CONSTRAINT chk_order_info_status CHECK (order_status IN (
        'PENDING_PAYMENT', 'PENDING_SHIPMENT', 'PENDING_RECEIPT', 'COMPLETED', 'CANCELLED'
    )),
    CONSTRAINT chk_order_info_payment_status CHECK (payment_status IN (
        'UNPAID', 'PAID', 'PARTIALLY_REFUNDED', 'REFUNDED'
    )),
    CONSTRAINT chk_order_info_amount CHECK (
        item_amount >= 0
        AND freight_amount >= 0
        AND payable_amount = item_amount + freight_amount
        AND payable_amount > 0
        AND refund_amount >= 0
        AND refund_amount <= payable_amount
    ),
    CONSTRAINT chk_order_info_shipping CHECK (
        (order_status IN ('PENDING_PAYMENT', 'PENDING_SHIPMENT', 'CANCELLED')
            AND carrier_code IS NULL AND carrier_name IS NULL AND tracking_no IS NULL AND shipped_at IS NULL)
        OR (order_status IN ('PENDING_RECEIPT', 'COMPLETED')
            AND carrier_code IS NOT NULL AND carrier_name IS NOT NULL AND tracking_no IS NOT NULL AND shipped_at IS NOT NULL)
    ),
    CONSTRAINT chk_order_info_completion CHECK (
        (order_status = 'COMPLETED' AND completed_at IS NOT NULL AND cancelled_at IS NULL)
        OR (order_status = 'CANCELLED' AND completed_at IS NULL AND cancelled_at IS NOT NULL)
        OR (order_status NOT IN ('COMPLETED', 'CANCELLED') AND completed_at IS NULL AND cancelled_at IS NULL)
    ),
    CONSTRAINT chk_order_info_payment_alignment CHECK (
        (order_status = 'PENDING_PAYMENT' AND payment_status = 'UNPAID' AND refund_amount = 0)
        OR (order_status = 'CANCELLED' AND payment_status IN ('UNPAID', 'REFUNDED'))
        OR (order_status = 'PENDING_SHIPMENT'
            AND payment_status IN ('PAID', 'PARTIALLY_REFUNDED'))
        OR (order_status IN ('PENDING_RECEIPT', 'COMPLETED')
            AND payment_status IN ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED'))
    ),
    CONSTRAINT chk_order_info_refund_amount CHECK (
        (payment_status IN ('UNPAID', 'PAID') AND refund_amount = 0)
        OR (payment_status = 'PARTIALLY_REFUNDED'
            AND refund_amount > 0 AND refund_amount < payable_amount)
        OR (payment_status = 'REFUNDED' AND refund_amount = payable_amount)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='One shop-specific child order';

CREATE TABLE order_item (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id            BIGINT UNSIGNED NOT NULL,
    shop_id             BIGINT UNSIGNED NOT NULL,
    spu_id              BIGINT UNSIGNED NOT NULL,
    sku_id              BIGINT UNSIGNED NOT NULL,
    spu_no              VARCHAR(64) NOT NULL,
    sku_no              VARCHAR(64) NOT NULL,
    product_name        VARCHAR(255) NOT NULL,
    sku_name            VARCHAR(255) NOT NULL,
    spec_json           JSON NOT NULL,
    image_url           VARCHAR(1024) NULL,
    unit_price          DECIMAL(18,2) NOT NULL,
    quantity            INT UNSIGNED NOT NULL,
    original_amount     DECIMAL(18,2) NOT NULL,
    freight_amount      DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT 'Allocated shop-order freight',
    payable_amount      DECIMAL(18,2) NOT NULL,
    refunded_quantity   INT UNSIGNED NOT NULL DEFAULT 0,
    refunded_amount     DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    reservation_status  VARCHAR(20) NOT NULL DEFAULT 'LOCKED',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_item_id_order (id, order_id),
    KEY idx_order_item_order (order_id),
    KEY idx_order_item_sku (sku_id, created_at),
    KEY idx_order_item_reservation (reservation_status, order_id),
    CONSTRAINT fk_order_item_order_shop FOREIGN KEY (order_id, shop_id) REFERENCES order_info (id, shop_id),
    CONSTRAINT fk_order_item_sku_spu_shop FOREIGN KEY (sku_id, spu_id, shop_id) REFERENCES product_sku (id, spu_id, shop_id),
    CONSTRAINT chk_order_item_amount CHECK (
        unit_price > 0
        AND quantity > 0
        AND original_amount = unit_price * quantity
        AND freight_amount >= 0
        AND payable_amount = original_amount + freight_amount
        AND refunded_quantity <= quantity
        AND refunded_amount >= 0
        AND refunded_amount <= payable_amount
    ),
    CONSTRAINT chk_order_item_reservation CHECK (
        reservation_status IN ('LOCKED', 'RELEASED', 'DEDUCTED')
    ),
    CONSTRAINT chk_order_item_spec_json CHECK (JSON_TYPE(spec_json) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Order item, product snapshot and stock reservation';

CREATE TABLE order_status_history (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id            BIGINT UNSIGNED NOT NULL,
    from_status         VARCHAR(30) NULL,
    to_status           VARCHAR(30) NOT NULL,
    operation_type      VARCHAR(30) NOT NULL,
    operator_type       VARCHAR(20) NOT NULL,
    operator_id         BIGINT UNSIGNED NULL,
    remark              VARCHAR(500) NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_order_status_history_order (order_id, created_at),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES order_info (id),
    CONSTRAINT fk_order_status_history_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id),
    CONSTRAINT chk_order_status_history_from CHECK (
        from_status IS NULL OR from_status IN (
            'PENDING_PAYMENT', 'PENDING_SHIPMENT', 'PENDING_RECEIPT', 'COMPLETED', 'CANCELLED'
        )
    ),
    CONSTRAINT chk_order_status_history_to CHECK (to_status IN (
        'PENDING_PAYMENT', 'PENDING_SHIPMENT', 'PENDING_RECEIPT', 'COMPLETED', 'CANCELLED'
    )),
    CONSTRAINT chk_order_status_history_operation CHECK (operation_type IN (
        'CREATE', 'PAY', 'CANCEL', 'SHIP', 'COMPLETE'
    )),
    CONSTRAINT chk_order_status_history_from_presence CHECK (
        (operation_type = 'CREATE' AND from_status IS NULL)
        OR (operation_type <> 'CREATE' AND from_status IS NOT NULL)
    ),
    CONSTRAINT chk_order_status_history_operator CHECK (operator_type IN ('USER', 'SHOP', 'PLATFORM', 'SYSTEM')),
    CONSTRAINT chk_order_status_history_transition CHECK (
        (operation_type = 'CREATE' AND from_status IS NULL AND to_status = 'PENDING_PAYMENT')
        OR (operation_type = 'PAY' AND from_status = 'PENDING_PAYMENT' AND to_status = 'PENDING_SHIPMENT')
        OR (operation_type = 'CANCEL' AND from_status IN ('PENDING_PAYMENT', 'PENDING_SHIPMENT') AND to_status = 'CANCELLED')
        OR (operation_type = 'SHIP' AND from_status = 'PENDING_SHIPMENT' AND to_status = 'PENDING_RECEIPT')
        OR (operation_type = 'COMPLETE' AND from_status = 'PENDING_RECEIPT' AND to_status = 'COMPLETED')
    ),
    CONSTRAINT chk_order_status_history_operator_id CHECK (
        (operator_type = 'SYSTEM' AND operator_id IS NULL)
        OR (operator_type <> 'SYSTEM' AND operator_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Shop-order status audit history';

-- ============================================================
-- 6. Wallet and payment
-- ============================================================

CREATE TABLE wallet_account (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    balance             DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version             INT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_account_user (user_id),
    CONSTRAINT fk_wallet_account_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_wallet_account_balance CHECK (balance >= 0),
    CONSTRAINT chk_wallet_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Buyer wallet aggregate balance';

CREATE TABLE payment_order (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payment_no          VARCHAR(64) NOT NULL,
    trade_id            BIGINT UNSIGNED NOT NULL,
    amount              DECIMAL(18,2) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    success_guard       BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN status = 'SUCCESS' THEN trade_id ELSE NULL END
    ) STORED,
    failure_reason      VARCHAR(500) NULL,
    paid_at             DATETIME(3) NULL,
    expires_at          DATETIME(3) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_order_no (payment_no),
    UNIQUE KEY uk_payment_order_one_success (success_guard),
    KEY idx_payment_order_trade (trade_id, created_at),
    KEY idx_payment_order_pending (status, expires_at),
    CONSTRAINT fk_payment_order_trade FOREIGN KEY (trade_id) REFERENCES trade_order (id),
    CONSTRAINT chk_payment_order_amount CHECK (amount > 0),
    CONSTRAINT chk_payment_order_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_payment_order_timestamps CHECK (
        (status = 'SUCCESS' AND paid_at IS NOT NULL)
        OR (status <> 'SUCCESS' AND paid_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Wallet payment attempt for a parent trade';

CREATE TABLE wallet_transaction (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    transaction_no      VARCHAR(64) NOT NULL,
    wallet_id           BIGINT UNSIGNED NOT NULL,
    transaction_type    VARCHAR(20) NOT NULL,
    direction           VARCHAR(10) NOT NULL,
    amount              DECIMAL(18,2) NOT NULL,
    balance_before      DECIMAL(18,2) NOT NULL,
    balance_after       DECIMAL(18,2) NOT NULL,
    business_type       VARCHAR(30) NOT NULL,
    business_no         VARCHAR(64) NOT NULL,
    operator_id         BIGINT UNSIGNED NULL,
    remark              VARCHAR(500) NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_transaction_no (transaction_no),
    UNIQUE KEY uk_wallet_transaction_business (business_type, business_no),
    KEY idx_wallet_transaction_wallet (wallet_id, created_at),
    CONSTRAINT fk_wallet_transaction_wallet FOREIGN KEY (wallet_id) REFERENCES wallet_account (id),
    CONSTRAINT fk_wallet_transaction_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id),
    CONSTRAINT chk_wallet_transaction_type CHECK (transaction_type IN (
        'RECHARGE', 'CONSUME', 'REFUND', 'ADJUST'
    )),
    CONSTRAINT chk_wallet_transaction_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_wallet_transaction_type_direction CHECK (
        (transaction_type IN ('RECHARGE', 'REFUND') AND direction = 'CREDIT')
        OR (transaction_type = 'CONSUME' AND direction = 'DEBIT')
        OR transaction_type = 'ADJUST'
    ),
    CONSTRAINT chk_wallet_transaction_balance CHECK (
        amount > 0
        AND balance_before >= 0
        AND balance_after >= 0
        AND ((direction = 'CREDIT' AND balance_after = balance_before + amount)
          OR (direction = 'DEBIT' AND balance_after = balance_before - amount))
    ),
    CONSTRAINT chk_wallet_transaction_adjust_audit CHECK (
        transaction_type <> 'ADJUST' OR (operator_id IS NOT NULL AND remark IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Immutable wallet ledger, including direct simulated recharge';

-- ============================================================
-- 7. After-sale and wallet refund
-- ============================================================

CREATE TABLE after_sale_request (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    after_sale_no       VARCHAR(64) NOT NULL,
    order_id            BIGINT UNSIGNED NOT NULL,
    order_item_id       BIGINT UNSIGNED NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    request_type        VARCHAR(30) NOT NULL,
    quantity            INT UNSIGNED NOT NULL,
    reason_code         VARCHAR(30) NOT NULL,
    reason_description  VARCHAR(500) NULL,
    evidence_json       JSON NULL COMMENT 'Array of evidence image URLs',
    requested_amount    DECIMAL(18,2) NOT NULL,
    approved_quantity   INT UNSIGNED NULL,
    approved_amount     DECIMAL(18,2) NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    reviewer_id         BIGINT UNSIGNED NULL,
    review_comment      VARCHAR(500) NULL,
    reviewed_at         DATETIME(3) NULL,
    return_carrier_code VARCHAR(64) NULL,
    return_carrier_name VARCHAR(128) NULL,
    return_tracking_no  VARCHAR(128) NULL,
    returned_at         DATETIME(3) NULL COMMENT 'Buyer handed return parcel to carrier',
    return_received_at  DATETIME(3) NULL COMMENT 'Shop confirmed returned goods received',
    refund_no           VARCHAR(64) NULL,
    refund_status       VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    refund_failure_reason VARCHAR(500) NULL,
    refunded_at         DATETIME(3) NULL,
    completed_at        DATETIME(3) NULL,
    cancelled_at        DATETIME(3) NULL,
    version             INT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_after_sale_request_no (after_sale_no),
    UNIQUE KEY uk_after_sale_refund_no (refund_no),
    KEY idx_after_sale_order (order_id, created_at),
    KEY idx_after_sale_item (order_item_id, created_at),
    KEY idx_after_sale_user (user_id, created_at),
    KEY idx_after_sale_status (status, created_at),
    CONSTRAINT fk_after_sale_order_user FOREIGN KEY (order_id, user_id) REFERENCES order_info (id, user_id),
    CONSTRAINT fk_after_sale_order_item FOREIGN KEY (order_item_id, order_id) REFERENCES order_item (id, order_id),
    CONSTRAINT fk_after_sale_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user (id),
    CONSTRAINT chk_after_sale_type CHECK (request_type IN ('REFUND_ONLY', 'RETURN_REFUND')),
    CONSTRAINT chk_after_sale_status CHECK (status IN (
        'PENDING', 'REJECTED', 'WAITING_RETURN', 'REFUNDING', 'COMPLETED', 'CANCELLED'
    )),
    CONSTRAINT chk_after_sale_refund_status CHECK (refund_status IN (
        'NOT_STARTED', 'PROCESSING', 'SUCCESS', 'FAILED'
    )),
    CONSTRAINT chk_after_sale_amount CHECK (
        quantity > 0
        AND requested_amount > 0
        AND (approved_quantity IS NULL OR (approved_quantity > 0 AND approved_quantity <= quantity))
        AND (approved_amount IS NULL OR (approved_amount > 0 AND approved_amount <= requested_amount))
    ),
    CONSTRAINT chk_after_sale_approval CHECK (
        (status IN ('PENDING', 'REJECTED', 'CANCELLED')
            AND approved_quantity IS NULL AND approved_amount IS NULL)
        OR (status IN ('WAITING_RETURN', 'REFUNDING', 'COMPLETED')
            AND approved_quantity IS NOT NULL AND approved_amount IS NOT NULL)
    ),
    CONSTRAINT chk_after_sale_review CHECK (
        (status IN ('PENDING', 'CANCELLED') AND reviewer_id IS NULL AND reviewed_at IS NULL)
        OR (status IN ('REJECTED', 'WAITING_RETURN', 'REFUNDING', 'COMPLETED')
            AND reviewer_id IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT chk_after_sale_reject_comment CHECK (
        status <> 'REJECTED' OR review_comment IS NOT NULL
    ),
    CONSTRAINT chk_after_sale_return_flow CHECK (
        (request_type = 'REFUND_ONLY'
            AND status <> 'WAITING_RETURN'
            AND return_carrier_code IS NULL AND return_carrier_name IS NULL
            AND return_tracking_no IS NULL AND returned_at IS NULL AND return_received_at IS NULL)
        OR (request_type = 'RETURN_REFUND' AND (
            (status IN ('PENDING', 'REJECTED', 'CANCELLED')
                AND return_carrier_code IS NULL AND return_carrier_name IS NULL
                AND return_tracking_no IS NULL AND returned_at IS NULL AND return_received_at IS NULL)
            OR (status = 'WAITING_RETURN' AND return_received_at IS NULL)
            OR (status IN ('REFUNDING', 'COMPLETED')
                AND returned_at IS NOT NULL AND return_received_at IS NOT NULL)
        ))
    ),
    CONSTRAINT chk_after_sale_return_shipping CHECK (
        (return_carrier_code IS NULL AND return_carrier_name IS NULL
            AND return_tracking_no IS NULL AND returned_at IS NULL)
        OR (return_carrier_code IS NOT NULL AND return_carrier_name IS NOT NULL
            AND return_tracking_no IS NOT NULL AND returned_at IS NOT NULL)
    ),
    CONSTRAINT chk_after_sale_return_time CHECK (
        return_received_at IS NULL
        OR (returned_at IS NOT NULL AND return_received_at >= returned_at)
    ),
    CONSTRAINT chk_after_sale_refund_fields CHECK (
        (refund_status = 'NOT_STARTED' AND refund_no IS NULL)
        OR (refund_status <> 'NOT_STARTED' AND refund_no IS NOT NULL AND approved_amount IS NOT NULL)
    ),
    CONSTRAINT chk_after_sale_state_alignment CHECK (
        (status IN ('PENDING', 'REJECTED', 'WAITING_RETURN', 'CANCELLED') AND refund_status = 'NOT_STARTED')
        OR (status = 'REFUNDING' AND refund_status IN ('PROCESSING', 'FAILED'))
        OR (status = 'COMPLETED' AND refund_status = 'SUCCESS')
    ),
    CONSTRAINT chk_after_sale_refunded_at CHECK (
        (refund_status = 'SUCCESS' AND refunded_at IS NOT NULL)
        OR (refund_status <> 'SUCCESS' AND refunded_at IS NULL)
    ),
    CONSTRAINT chk_after_sale_refund_failure CHECK (
        (refund_status = 'FAILED' AND refund_failure_reason IS NOT NULL)
        OR (refund_status <> 'FAILED' AND refund_failure_reason IS NULL)
    ),
    CONSTRAINT chk_after_sale_terminal_time CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND completed_at IS NULL AND cancelled_at IS NOT NULL)
        OR (status NOT IN ('COMPLETED', 'CANCELLED') AND completed_at IS NULL AND cancelled_at IS NULL)
    ),
    CONSTRAINT chk_after_sale_time_order CHECK (
        (reviewed_at IS NULL OR reviewed_at >= created_at)
        AND (returned_at IS NULL OR (reviewed_at IS NOT NULL AND returned_at >= reviewed_at))
        AND (return_received_at IS NULL OR return_received_at >= returned_at)
        AND (refunded_at IS NULL OR (
            reviewed_at IS NOT NULL
            AND refunded_at >= reviewed_at
            AND (request_type <> 'RETURN_REFUND'
                OR (return_received_at IS NOT NULL AND refunded_at >= return_received_at))
        ))
        AND (completed_at IS NULL OR (refunded_at IS NOT NULL AND completed_at >= refunded_at))
        AND (cancelled_at IS NULL OR cancelled_at >= created_at)
    ),
    CONSTRAINT chk_after_sale_evidence CHECK (
        evidence_json IS NULL OR JSON_TYPE(evidence_json) = 'ARRAY'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Single-order-item after-sale request and wallet refund execution';

-- ============================================================
-- 8. Minimal RBAC seed data
-- ============================================================

INSERT INTO sys_role (role_code, role_name, scope_type, description) VALUES
    ('CUSTOMER', '普通用户', 'PLATFORM', '购物、下单、钱包及本人售后'),
    ('PLATFORM_SHOP_ADMIN', '平台店铺管理员', 'PLATFORM', '审核和管理店铺'),
    ('PLATFORM_PRODUCT_AUDITOR', '平台商品审核员', 'PLATFORM', '审核商品和平台强制禁售'),
    ('SUPER_ADMIN', '超级管理员', 'PLATFORM', '拥有所有平台权限'),
    ('SHOP_ADMIN', '店铺管理员', 'SHOP', '管理店铺成员和全部店铺业务'),
    ('SHOP_PRODUCT_OPERATOR', '店铺商品运营', 'SHOP', '管理本店商品'),
    ('SHOP_ORDER_OPERATOR', '店铺订单客服', 'SHOP', '管理本店订单和售后'),
    ('SHOP_INVENTORY_OPERATOR', '店铺库存人员', 'SHOP', '管理本店库存和发货');

INSERT INTO sys_permission (permission_code, permission_name, scope_type, resource, http_method) VALUES
    ('product:read', '查看在售商品', 'PLATFORM', '/api/products/**', 'GET'),
    ('cart:manage', '管理本人购物车', 'PLATFORM', '/api/cart/**', NULL),
    ('trade:create', '创建跨店交易', 'PLATFORM', '/api/trades', 'POST'),
    ('order:read:self', '查看本人订单', 'PLATFORM', '/api/orders/**', 'GET'),
    ('after-sale:create', '申请本人售后', 'PLATFORM', '/api/after-sales', 'POST'),
    ('wallet:read:self', '查看本人钱包', 'PLATFORM', '/api/wallet/**', 'GET'),
    ('wallet:recharge', '模拟钱包充值', 'PLATFORM', '/api/wallet/recharges', 'POST'),
    ('platform:shop:manage', '平台管理店铺', 'PLATFORM', '/api/platform/shops/**', NULL),
    ('platform:product:audit', '平台审核商品', 'PLATFORM', '/api/platform/products/reviews/**', NULL),
    ('platform:product:ban', '平台禁售商品', 'PLATFORM', '/api/platform/products/bans/**', NULL),
    ('platform:rbac:manage', '平台管理权限', 'PLATFORM', '/api/platform/rbac/**', NULL),
    ('shop:member:manage', '管理本店成员', 'SHOP', '/api/shops/*/members/**', NULL),
    ('shop:product:manage', '管理本店商品', 'SHOP', '/api/shops/*/products/**', NULL),
    ('shop:inventory:manage', '管理本店库存', 'SHOP', '/api/shops/*/inventory/**', NULL),
    ('shop:order:read', '查看本店订单', 'SHOP', '/api/shops/*/orders/**', 'GET'),
    ('shop:order:manage', '处理本店订单', 'SHOP', '/api/shops/*/orders/*/service-actions/**', NULL),
    ('shop:order:ship', '为本店订单发货', 'SHOP', '/api/shops/*/orders/*/ship', 'POST'),
    ('shop:after-sale:manage', '管理本店售后', 'SHOP', '/api/shops/*/after-sales/**', NULL);

INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'product:read', 'cart:manage', 'trade:create', 'order:read:self',
    'after-sale:create', 'wallet:read:self', 'wallet:recharge'
)
WHERE r.role_code = 'CUSTOMER';

INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'platform:shop:manage'
WHERE r.role_code = 'PLATFORM_SHOP_ADMIN';

INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('platform:product:audit', 'platform:product:ban')
WHERE r.role_code = 'PLATFORM_PRODUCT_AUDITOR';

INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.scope_type = 'PLATFORM';

INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'shop:member:manage', 'shop:product:manage', 'shop:inventory:manage',
    'shop:order:read', 'shop:order:manage', 'shop:order:ship', 'shop:after-sale:manage'
)
WHERE r.role_code = 'SHOP_ADMIN';

INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'shop:product:manage'
WHERE r.role_code = 'SHOP_PRODUCT_OPERATOR';

INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'shop:order:read', 'shop:order:manage', 'shop:after-sale:manage'
)
WHERE r.role_code = 'SHOP_ORDER_OPERATOR';

INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'shop:inventory:manage', 'shop:order:read', 'shop:order:ship'
)
WHERE r.role_code = 'SHOP_INVENTORY_OPERATOR';
