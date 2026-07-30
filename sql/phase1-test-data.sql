-- Phase 1 repeatable local test data for MySQL 8.0.16+
-- Prerequisite: run schema.sql and schema2.sql first.
-- All seeded accounts use password: Market123

USE `market`;
SET NAMES utf8mb4;
SET time_zone = '+08:00';

START TRANSACTION;

SET @password_hash = '$2y$12$MQOcYzcSHLXuw71SyrO8I.ZiMKzXfrk8gMSAJjtepSc76.Rkg6KpC';

INSERT INTO sys_user (username, password_hash, nickname, phone, email, status)
VALUES
    ('buyer_a', @password_hash, '买家 A', '13800000001', 'buyer_a@example.com', 'ACTIVE'),
    ('buyer_b', @password_hash, '买家 B', '13800000002', 'buyer_b@example.com', 'ACTIVE'),
    ('platform_shop_admin', @password_hash, '平台店铺管理员', '13800000003', 'shop_admin@example.com', 'ACTIVE'),
    ('platform_auditor', @password_hash, '平台商品审核员', '13800000004', 'auditor@example.com', 'ACTIVE'),
    ('super_admin', @password_hash, '超级管理员', '13800000005', 'super_admin@example.com', 'ACTIVE'),
    ('shop_a_admin', @password_hash, 'A 店管理员', '13800000006', 'shop_a_admin@example.com', 'ACTIVE'),
    ('shop_a_product', @password_hash, 'A 店商品运营', '13800000007', 'shop_a_product@example.com', 'ACTIVE'),
    ('shop_a_order', @password_hash, 'A 店订单客服', '13800000008', 'shop_a_order@example.com', 'ACTIVE'),
    ('shop_a_inventory', @password_hash, 'A 店库存人员', '13800000009', 'shop_a_inventory@example.com', 'ACTIVE'),
    ('shop_b_admin', @password_hash, 'B 店管理员', '13800000010', 'shop_b_admin@example.com', 'ACTIVE')
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash), nickname = VALUES(nickname),
    phone = VALUES(phone), email = VALUES(email), status = 'ACTIVE', deleted_at = NULL;

INSERT IGNORE INTO sys_user_role (user_id, role_id, role_scope)
SELECT u.id, r.id, 'PLATFORM'
FROM sys_user u JOIN sys_role r ON r.role_code = 'CUSTOMER'
WHERE u.username IN ('buyer_a', 'buyer_b', 'shop_a_admin', 'shop_a_product',
                     'shop_a_order', 'shop_a_inventory', 'shop_b_admin');

INSERT IGNORE INTO sys_user_role (user_id, role_id, role_scope)
SELECT u.id, r.id, 'PLATFORM'
FROM sys_user u JOIN sys_role r ON r.role_code = 'PLATFORM_SHOP_ADMIN'
WHERE u.username = 'platform_shop_admin';

INSERT IGNORE INTO sys_user_role (user_id, role_id, role_scope)
SELECT u.id, r.id, 'PLATFORM'
FROM sys_user u JOIN sys_role r ON r.role_code = 'PLATFORM_PRODUCT_AUDITOR'
WHERE u.username = 'platform_auditor';

INSERT IGNORE INTO sys_user_role (user_id, role_id, role_scope)
SELECT u.id, r.id, 'PLATFORM'
FROM sys_user u JOIN sys_role r ON r.role_code = 'SUPER_ADMIN'
WHERE u.username = 'super_admin';

INSERT INTO wallet_account (user_id, balance, status, version)
SELECT id, CASE username WHEN 'buyer_a' THEN 20000.00 ELSE 0.00 END, 'ACTIVE', 0
FROM sys_user
WHERE username IN ('buyer_a', 'buyer_b', 'platform_shop_admin', 'platform_auditor',
                   'super_admin', 'shop_a_admin', 'shop_a_product', 'shop_a_order',
                   'shop_a_inventory', 'shop_b_admin')
ON DUPLICATE KEY UPDATE balance = VALUES(balance), status = 'ACTIVE';

INSERT INTO user_address (
    user_id, recipient_name, recipient_phone, province_name, city_name,
    district_name, detail_address, is_default
)
SELECT id, '张三', '13800000001', '上海市', '上海市', '杨浦区', '延吉中路 100 号', 1
FROM sys_user WHERE username = 'buyer_a'
  AND NOT EXISTS (
      SELECT 1 FROM user_address a
      WHERE a.user_id = sys_user.id AND a.deleted_at IS NULL
  );

INSERT INTO shop (shop_no, shop_name, logo_url, description, contact_name, contact_phone, status)
VALUES
    ('SHOP_SEED_A', '时光数码店 A', NULL, 'Phase 1 测试店铺 A', '李四', '13900000001', 'ACTIVE'),
    ('SHOP_SEED_B', '时光生活店 B', NULL, 'Phase 1 测试店铺 B', '王五', '13900000002', 'ACTIVE'),
    ('SHOP_SEED_C', '暂停测试店铺 C', NULL, '用于不可购买场景', '赵六', '13900000003', 'SUSPENDED')
ON DUPLICATE KEY UPDATE
    shop_name = VALUES(shop_name), description = VALUES(description),
    contact_name = VALUES(contact_name), contact_phone = VALUES(contact_phone), status = VALUES(status);

INSERT INTO shop_user (shop_id, user_id, role_id, role_scope, status)
SELECT s.id, u.id, r.id, 'SHOP', 'ACTIVE'
FROM shop s JOIN sys_user u JOIN sys_role r ON r.role_code = 'SHOP_ADMIN'
WHERE (s.shop_no = 'SHOP_SEED_A' AND u.username = 'shop_a_admin')
   OR (s.shop_no = 'SHOP_SEED_B' AND u.username = 'shop_b_admin')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id), status = 'ACTIVE';

INSERT INTO shop_user (shop_id, user_id, role_id, role_scope, status)
SELECT s.id, u.id, r.id, 'SHOP', 'ACTIVE'
FROM shop s JOIN sys_user u JOIN sys_role r
WHERE s.shop_no = 'SHOP_SEED_A'
  AND ((u.username = 'shop_a_product' AND r.role_code = 'SHOP_PRODUCT_OPERATOR')
    OR (u.username = 'shop_a_order' AND r.role_code = 'SHOP_ORDER_OPERATOR')
    OR (u.username = 'shop_a_inventory' AND r.role_code = 'SHOP_INVENTORY_OPERATOR'))
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id), status = 'ACTIVE';

INSERT INTO product_category (parent_id, category_name, category_code, sort_order, status)
VALUES (NULL, '数码', 'SEED_DIGITAL', 10, 'ENABLED')
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name), sort_order = VALUES(sort_order), status = 'ENABLED';

SET @digital_id = (SELECT id FROM product_category WHERE category_code = 'SEED_DIGITAL');
INSERT INTO product_category (parent_id, category_name, category_code, sort_order, status)
VALUES (@digital_id, '手机', 'SEED_MOBILE', 10, 'ENABLED')
ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), category_name = VALUES(category_name), status = 'ENABLED';

SET @mobile_id = (SELECT id FROM product_category WHERE category_code = 'SEED_MOBILE');
INSERT INTO product_category_attribute (
    category_id, attribute_name, value_type, unit, is_required, is_filterable,
    options_json, sort_order, status
)
VALUES (@mobile_id, '电池容量', 'NUMBER', 'mAh', 1, 1, NULL, 1, 'ENABLED')
ON DUPLICATE KEY UPDATE value_type = 'NUMBER', unit = 'mAh', is_required = 1,
                        is_filterable = 1, sort_order = 1, status = 'ENABLED';

INSERT INTO product_brand (brand_name, brand_code, logo_url, status)
VALUES ('SeedPhone', 'SEED_PHONE', NULL, 'ENABLED')
ON DUPLICATE KEY UPDATE brand_name = VALUES(brand_name), status = 'ENABLED';

SET @shop_a = (SELECT id FROM shop WHERE shop_no = 'SHOP_SEED_A');
SET @shop_b = (SELECT id FROM shop WHERE shop_no = 'SHOP_SEED_B');
SET @shop_c = (SELECT id FROM shop WHERE shop_no = 'SHOP_SEED_C');
SET @brand_id = (SELECT id FROM product_brand WHERE brand_code = 'SEED_PHONE');
SET @shop_a_admin = (SELECT id FROM sys_user WHERE username = 'shop_a_admin');
SET @shop_b_admin = (SELECT id FROM sys_user WHERE username = 'shop_b_admin');

INSERT INTO product_spu (
    shop_id, category_id, brand_id, spu_no, product_name, subtitle, cover_url,
    gallery_json, detail_html, packing_list, service_note, status, content_version,
    created_by, updated_by
)
VALUES
    (@shop_a, @mobile_id, @brand_id, 'SPU_SEED_A1', 'A 店示例手机', '正常购买和多 SKU 场景', NULL,
     JSON_ARRAY(), '<p>清洗后的示例详情</p>', '主机、数据线', '7 天售后', 'ON_SHELF', 1, @shop_a_admin, @shop_a_admin),
    (@shop_a, @mobile_id, @brand_id, 'SPU_SEED_A2', 'A 店下架手机', '购物车失效场景', NULL,
     JSON_ARRAY(), '<p>下架商品</p>', '主机', '售后说明', 'OFF_SHELF', 1, @shop_a_admin, @shop_a_admin),
    (@shop_b, @mobile_id, @brand_id, 'SPU_SEED_B1', 'B 店示例手机', '跨店拆单场景', NULL,
     JSON_ARRAY(), '<p>B 店商品</p>', '主机', '售后说明', 'ON_SHELF', 1, @shop_b_admin, @shop_b_admin),
    (@shop_a, @mobile_id, @brand_id, 'SPU_SEED_REVIEW', '待审示例手机', '平台审核场景', NULL,
     JSON_ARRAY(), '<p>待审详情</p>', '主机', '售后说明', 'PENDING_REVIEW', 3, @shop_a_admin, @shop_a_admin),
    (@shop_c, @mobile_id, @brand_id, 'SPU_SEED_SUSPENDED', '暂停店铺商品', '店铺不可购买场景', NULL,
     JSON_ARRAY(), '<p>暂停店铺商品</p>', '主机', '售后说明', 'ON_SHELF', 1, @shop_a_admin, @shop_a_admin)
ON DUPLICATE KEY UPDATE product_name = VALUES(product_name), subtitle = VALUES(subtitle),
    detail_html = VALUES(detail_html), status = VALUES(status), content_version = VALUES(content_version),
    updated_by = VALUES(updated_by), deleted_at = NULL;

SET @attr_id = (SELECT id FROM product_category_attribute WHERE category_id = @mobile_id AND attribute_name = '电池容量');
INSERT INTO product_attribute_value (spu_id, category_id, attribute_id, attribute_value)
SELECT id, category_id, @attr_id, '5000'
FROM product_spu WHERE spu_no IN ('SPU_SEED_A1', 'SPU_SEED_A2', 'SPU_SEED_B1', 'SPU_SEED_REVIEW', 'SPU_SEED_SUSPENDED')
ON DUPLICATE KEY UPDATE attribute_value = VALUES(attribute_value);

INSERT INTO product_sku (
    spu_id, shop_id, sku_no, sku_name, spec_json, spec_key,
    sale_price, market_price, barcode, image_url, status, version
)
SELECT id, shop_id, 'SKU_SEED_A1_BLACK', '黑色 256GB', JSON_OBJECT('color', '黑色', 'storage', '256GB'),
       'c0ea808f2b1dcc48f113490a9f36bf8404dc74eef1bd4b4a261b029eb400de48',
       3999.00, 4299.00, NULL, NULL, 'ENABLED', 0
FROM product_spu WHERE spu_no = 'SPU_SEED_A1'
UNION ALL
SELECT id, shop_id, 'SKU_SEED_A1_WHITE', '白色 128GB', JSON_OBJECT('color', '白色', 'storage', '128GB'),
       '6f29523978acffad071f91fbc826154d64165d406d95276d40a538684bc878b3',
       3499.00, 3799.00, NULL, NULL, 'ENABLED', 0
FROM product_spu WHERE spu_no = 'SPU_SEED_A1'
UNION ALL
SELECT id, shop_id, 'SKU_SEED_A2', '标准版', JSON_OBJECT('size', '标准版'),
       'd330b62d0ce7d6060111d3749e92740180b55bc8cc41193218cddc84d62b3b9f',
       1999.00, 2199.00, NULL, NULL, 'ENABLED', 0
FROM product_spu WHERE spu_no = 'SPU_SEED_A2'
UNION ALL
SELECT id, shop_id, 'SKU_SEED_B1', '蓝色 512GB', JSON_OBJECT('color', '蓝色', 'storage', '512GB'),
       '35798c8bd2718dcd51eb8a0e382e1c34ca5639e5f0b561dbe2f439aa59c746fc',
       4599.00, 4999.00, NULL, NULL, 'ENABLED', 0
FROM product_spu WHERE spu_no = 'SPU_SEED_B1'
UNION ALL
SELECT id, shop_id, 'SKU_SEED_REVIEW', '黑色 256GB', JSON_OBJECT('color', '黑色', 'storage', '256GB'),
       'c0ea808f2b1dcc48f113490a9f36bf8404dc74eef1bd4b4a261b029eb400de48',
       2999.00, 3299.00, NULL, NULL, 'ENABLED', 0
FROM product_spu WHERE spu_no = 'SPU_SEED_REVIEW'
UNION ALL
SELECT id, shop_id, 'SKU_SEED_SUSPENDED', '标准版', JSON_OBJECT('size', '标准版'),
       'd330b62d0ce7d6060111d3749e92740180b55bc8cc41193218cddc84d62b3b9f',
       999.00, 1099.00, NULL, NULL, 'ENABLED', 0
FROM product_spu WHERE spu_no = 'SPU_SEED_SUSPENDED'
ON DUPLICATE KEY UPDATE sku_name = VALUES(sku_name), sale_price = VALUES(sale_price),
    market_price = VALUES(market_price), status = 'ENABLED', deleted_at = NULL;

INSERT INTO inventory_stock (sku_id, available_quantity, locked_quantity, version)
SELECT id,
       CASE sku_no
           WHEN 'SKU_SEED_A1_BLACK' THEN 20
           WHEN 'SKU_SEED_A1_WHITE' THEN 8
           WHEN 'SKU_SEED_B1' THEN 15
           WHEN 'SKU_SEED_REVIEW' THEN 5
           WHEN 'SKU_SEED_SUSPENDED' THEN 10
           ELSE 0
       END,
       0, 0
FROM product_sku
WHERE sku_no IN ('SKU_SEED_A1_BLACK', 'SKU_SEED_A1_WHITE', 'SKU_SEED_A2',
                 'SKU_SEED_B1', 'SKU_SEED_REVIEW', 'SKU_SEED_SUSPENDED')
ON DUPLICATE KEY UPDATE available_quantity = VALUES(available_quantity), locked_quantity = 0;

INSERT INTO product_status_history (
    spu_id, from_status, to_status, operation_type, content_version,
    operator_type, operator_id, reason
)
SELECT p.id, 'DRAFT', 'PENDING_REVIEW', 'SUBMIT_REVIEW', p.content_version,
       'SHOP', p.updated_by, NULL
FROM product_spu p
WHERE p.spu_no = 'SPU_SEED_REVIEW'
  AND NOT EXISTS (
      SELECT 1 FROM product_status_history h
      WHERE h.spu_id = p.id AND h.operation_type = 'SUBMIT_REVIEW'
        AND h.content_version = p.content_version
  );

COMMIT;

-- Useful aliases after seeding:
SELECT 'buyer_a' AS username, id AS user_id FROM sys_user WHERE username = 'buyer_a';
SELECT shop_no, id AS shop_id FROM shop WHERE shop_no IN ('SHOP_SEED_A', 'SHOP_SEED_B', 'SHOP_SEED_C');
SELECT spu_no, id AS spu_id FROM product_spu WHERE spu_no LIKE 'SPU_SEED_%';
SELECT sku_no, id AS sku_id FROM product_sku WHERE sku_no LIKE 'SKU_SEED_%';
