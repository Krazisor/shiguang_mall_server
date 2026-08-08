-- Repeatable demo data for frontend showcase (MySQL 8.0.16+).
-- Prerequisite: run schema.sql, schema2.sql and phase1-test-data.sql first.
-- All seeded accounts use password: Market123

USE `market`;
SET NAMES utf8mb4;
SET time_zone = '+08:00';

START TRANSACTION;

SET @buyer_a = (SELECT id FROM sys_user WHERE username = 'buyer_a');
SET @buyer_b = (SELECT id FROM sys_user WHERE username = 'buyer_b');
SET @shop_a = (SELECT id FROM shop WHERE shop_no = 'SHOP_SEED_A');
SET @shop_b = (SELECT id FROM shop WHERE shop_no = 'SHOP_SEED_B');
SET @shop_a_admin = (SELECT id FROM sys_user WHERE username = 'shop_a_admin');
SET @shop_b_admin = (SELECT id FROM sys_user WHERE username = 'shop_b_admin');
SET @auditor = (SELECT id FROM sys_user WHERE username = 'platform_auditor');

-- Extra catalog branches and brands give the public catalog realistic variety.
INSERT INTO product_category (parent_id, category_name, category_code, sort_order, status)
VALUES
    (NULL, '家居生活', 'DEMO_HOME', 20, 'ENABLED'),
    (NULL, '电脑办公', 'DEMO_COMPUTER', 30, 'ENABLED'),
    (NULL, '影音娱乐', 'DEMO_AUDIO', 40, 'ENABLED')
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name), sort_order = VALUES(sort_order), status = 'ENABLED';

SET @home = (SELECT id FROM product_category WHERE category_code = 'DEMO_HOME');
SET @computer = (SELECT id FROM product_category WHERE category_code = 'DEMO_COMPUTER');
SET @audio = (SELECT id FROM product_category WHERE category_code = 'DEMO_AUDIO');

INSERT INTO product_category (parent_id, category_name, category_code, sort_order, status)
VALUES
    (@home, '生活日用', 'DEMO_DAILY', 10, 'ENABLED'),
    (@computer, '电脑配件', 'DEMO_PERIPHERAL', 10, 'ENABLED'),
    (@audio, '耳机音箱', 'DEMO_HEADPHONE', 10, 'ENABLED')
ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), category_name = VALUES(category_name), status = 'ENABLED';

SET @daily = (SELECT id FROM product_category WHERE category_code = 'DEMO_DAILY');
SET @peripheral = (SELECT id FROM product_category WHERE category_code = 'DEMO_PERIPHERAL');
SET @headphone = (SELECT id FROM product_category WHERE category_code = 'DEMO_HEADPHONE');

INSERT INTO product_category_attribute (
    category_id, attribute_name, value_type, unit, is_required, is_filterable,
    options_json, sort_order, status
)
VALUES
    (@daily, '容量', 'NUMBER', 'ml', 1, 1, NULL, 1, 'ENABLED'),
    (@peripheral, '连接方式', 'OPTION', NULL, 1, 1, JSON_ARRAY('有线', '蓝牙'), 1, 'ENABLED'),
    (@headphone, '佩戴方式', 'OPTION', NULL, 0, 1, JSON_ARRAY('入耳式', '头戴式'), 1, 'ENABLED')
ON DUPLICATE KEY UPDATE value_type = VALUES(value_type), unit = VALUES(unit),
    is_required = VALUES(is_required), is_filterable = VALUES(is_filterable),
    options_json = VALUES(options_json), status = 'ENABLED';

INSERT INTO product_brand (brand_name, brand_code, logo_url, status)
VALUES
    ('时光声场', 'DEMO_AUDIO_BRAND', 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=160&q=80', 'ENABLED'),
    ('拾光家居', 'DEMO_HOME_BRAND', 'https://images.unsplash.com/photo-1586023492125-27b2c045efd7?w=160&q=80', 'ENABLED'),
    ('极简办公', 'DEMO_COMPUTER_BRAND', 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=160&q=80', 'ENABLED')
ON DUPLICATE KEY UPDATE brand_name = VALUES(brand_name), logo_url = VALUES(logo_url), status = 'ENABLED';

SET @audio_brand = (SELECT id FROM product_brand WHERE brand_code = 'DEMO_AUDIO_BRAND');
SET @home_brand = (SELECT id FROM product_brand WHERE brand_code = 'DEMO_HOME_BRAND');
SET @computer_brand = (SELECT id FROM product_brand WHERE brand_code = 'DEMO_COMPUTER_BRAND');

INSERT INTO product_spu (
    shop_id, category_id, brand_id, spu_no, product_name, subtitle, cover_url,
    gallery_json, detail_html, packing_list, service_note, status, content_version,
    created_by, updated_by
)
VALUES
    (@shop_a, @headphone, @audio_brand, 'SPU_DEMO_A3', '时光降噪耳机 Pro', '通勤降噪，续航 40 小时',
     'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=720&q=85',
     JSON_ARRAY('https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=720&q=85', 'https://images.unsplash.com/photo-1484704849700-f032a568e944?w=720&q=85'),
     '<h3>沉浸式降噪体验</h3><p>适合通勤、办公和旅行的无线耳机。</p>', '耳机、充电盒、数据线、收纳袋', '7 天无理由，1 年质保', 'ON_SHELF', 1, @shop_a_admin, @shop_a_admin),
    (@shop_a, @peripheral, @computer_brand, 'SPU_DEMO_A4', '极简机械键盘 87 键', '静音轴体，办公游戏皆宜',
     'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=720&q=85',
     JSON_ARRAY('https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=720&q=85'),
     '<h3>紧凑布局</h3><p>87 键配列释放桌面空间，支持多设备连接。</p>', '键盘、拔键器、说明书', '全国联保', 'ON_SHELF', 1, @shop_a_admin, @shop_a_admin),
    (@shop_b, @daily, @home_brand, 'SPU_DEMO_B2', '晨光保温杯 480ml', '轻量便携，长效保温',
     'https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=720&q=85',
     JSON_ARRAY('https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=720&q=85'),
     '<h3>一杯随行</h3><p>食品级内胆，适合办公室和户外使用。</p>', '保温杯、杯盖、清洁刷', '破损包赔', 'ON_SHELF', 1, @shop_b_admin, @shop_b_admin),
    (@shop_b, @daily, @home_brand, 'SPU_DEMO_B3', '云朵护颈枕', '慢回弹记忆棉，午休好伴侣',
     'https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?w=720&q=85',
     JSON_ARRAY('https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?w=720&q=85'),
     '<h3>柔软支撑</h3><p>贴合颈部曲线，午休和旅行都能保持舒适。</p>', '护颈枕、收纳袋', '30 天舒适体验', 'ON_SHELF', 1, @shop_b_admin, @shop_b_admin),
    (@shop_a, @headphone, @audio_brand, 'SPU_DEMO_REVIEW', '待审核蓝牙音箱', '平台审核列表演示商品',
     'https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?w=720&q=85',
     JSON_ARRAY('https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?w=720&q=85'),
     '<p>待平台审核的商品详情。</p>', '音箱、数据线', '售后说明', 'PENDING_REVIEW', 2, @shop_a_admin, @shop_a_admin)
ON DUPLICATE KEY UPDATE
    product_name = VALUES(product_name), subtitle = VALUES(subtitle), cover_url = VALUES(cover_url),
    gallery_json = VALUES(gallery_json), detail_html = VALUES(detail_html), status = VALUES(status),
    content_version = VALUES(content_version), updated_by = VALUES(updated_by), deleted_at = NULL;

SET @spu_a3 = (SELECT id FROM product_spu WHERE spu_no = 'SPU_DEMO_A3');
SET @spu_a4 = (SELECT id FROM product_spu WHERE spu_no = 'SPU_DEMO_A4');
SET @spu_b2 = (SELECT id FROM product_spu WHERE spu_no = 'SPU_DEMO_B2');
SET @spu_b3 = (SELECT id FROM product_spu WHERE spu_no = 'SPU_DEMO_B3');
SET @spu_review = (SELECT id FROM product_spu WHERE spu_no = 'SPU_DEMO_REVIEW');

SET @attr_daily = (SELECT id FROM product_category_attribute WHERE category_id = @daily AND attribute_name = '容量');
SET @attr_connect = (SELECT id FROM product_category_attribute WHERE category_id = @peripheral AND attribute_name = '连接方式');
SET @attr_wear = (SELECT id FROM product_category_attribute WHERE category_id = @headphone AND attribute_name = '佩戴方式');
INSERT INTO product_attribute_value (spu_id, category_id, attribute_id, attribute_value)
VALUES
    (@spu_a3, @headphone, @attr_wear, '头戴式'),
    (@spu_a4, @peripheral, @attr_connect, '蓝牙'),
    (@spu_b2, @daily, @attr_daily, '480'),
    (@spu_b3, @daily, @attr_daily, '600'),
    (@spu_review, @headphone, @attr_wear, '便携式')
ON DUPLICATE KEY UPDATE attribute_value = VALUES(attribute_value);

INSERT INTO product_sku (
    spu_id, shop_id, sku_no, sku_name, spec_json, spec_key, sale_price, market_price,
    barcode, image_url, status, version
)
VALUES
    (@spu_a3, @shop_a, 'SKU_DEMO_A3_BLACK', '曜石黑', JSON_OBJECT('color', '曜石黑'), SHA2('SKU_DEMO_A3_BLACK', 256), 899.00, 999.00, '6900000000301', 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=480&q=80', 'ENABLED', 0),
    (@spu_a3, @shop_a, 'SKU_DEMO_A3_WHITE', '云雾白', JSON_OBJECT('color', '云雾白'), SHA2('SKU_DEMO_A3_WHITE', 256), 929.00, 1029.00, '6900000000302', 'https://images.unsplash.com/photo-1484704849700-f032a568e944?w=480&q=80', 'ENABLED', 0),
    (@spu_a4, @shop_a, 'SKU_DEMO_A4_STANDARD', '静音轴', JSON_OBJECT('switch', '静音轴'), SHA2('SKU_DEMO_A4_STANDARD', 256), 499.00, 599.00, '6900000000401', 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=480&q=80', 'ENABLED', 0),
    (@spu_b2, @shop_b, 'SKU_DEMO_B2_GREEN', '抹茶绿', JSON_OBJECT('color', '抹茶绿'), SHA2('SKU_DEMO_B2_GREEN', 256), 129.00, 159.00, '6900000000201', 'https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=480&q=80', 'ENABLED', 0),
    (@spu_b2, @shop_b, 'SKU_DEMO_B2_WHITE', '月光白', JSON_OBJECT('color', '月光白'), SHA2('SKU_DEMO_B2_WHITE', 256), 129.00, 159.00, '6900000000202', 'https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=480&q=80', 'ENABLED', 0),
    (@spu_b3, @shop_b, 'SKU_DEMO_B3_STANDARD', '标准款', JSON_OBJECT('size', '标准款'), SHA2('SKU_DEMO_B3_STANDARD', 256), 159.00, 199.00, '6900000000303', 'https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?w=480&q=80', 'ENABLED', 0),
    (@spu_review, @shop_a, 'SKU_DEMO_REVIEW', '蓝牙版', JSON_OBJECT('version', '蓝牙版'), SHA2('SKU_DEMO_REVIEW', 256), 299.00, 359.00, '6900000000501', 'https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?w=480&q=80', 'ENABLED', 0)
ON DUPLICATE KEY UPDATE sku_name = VALUES(sku_name), spec_json = VALUES(spec_json),
    sale_price = VALUES(sale_price), market_price = VALUES(market_price), image_url = VALUES(image_url),
    status = 'ENABLED', deleted_at = NULL;

SET @sku_a3 = (SELECT id FROM product_sku WHERE sku_no = 'SKU_DEMO_A3_BLACK');
SET @sku_a4 = (SELECT id FROM product_sku WHERE sku_no = 'SKU_DEMO_A4_STANDARD');
SET @sku_b2 = (SELECT id FROM product_sku WHERE sku_no = 'SKU_DEMO_B2_GREEN');
SET @sku_b3 = (SELECT id FROM product_sku WHERE sku_no = 'SKU_DEMO_B3_STANDARD');
SET @sku_review = (SELECT id FROM product_sku WHERE sku_no = 'SKU_DEMO_REVIEW');

-- Product lifecycle examples for shop/platform management screens.
INSERT INTO product_status_history (spu_id, from_status, to_status, operation_type, content_version, operator_type, operator_id, reason)
SELECT @spu_a3, NULL, 'DRAFT', 'CREATE', 0, 'SHOP', @shop_a_admin, NULL
WHERE NOT EXISTS (SELECT 1 FROM product_status_history WHERE spu_id = @spu_a3 AND operation_type = 'CREATE');
INSERT INTO product_status_history (spu_id, from_status, to_status, operation_type, content_version, operator_type, operator_id, reason)
SELECT @spu_a3, 'DRAFT', 'PENDING_REVIEW', 'SUBMIT_REVIEW', 1, 'SHOP', @shop_a_admin, NULL
WHERE NOT EXISTS (SELECT 1 FROM product_status_history WHERE spu_id = @spu_a3 AND operation_type = 'SUBMIT_REVIEW');
INSERT INTO product_status_history (spu_id, from_status, to_status, operation_type, content_version, operator_type, operator_id, reason)
SELECT @spu_a3, 'PENDING_REVIEW', 'OFF_SHELF', 'APPROVE', 1, 'PLATFORM', @auditor, '资料齐全，审核通过'
WHERE NOT EXISTS (SELECT 1 FROM product_status_history WHERE spu_id = @spu_a3 AND operation_type = 'APPROVE');
INSERT INTO product_status_history (spu_id, from_status, to_status, operation_type, content_version, operator_type, operator_id, reason)
SELECT @spu_a3, 'OFF_SHELF', 'ON_SHELF', 'PUT_ON_SHELF', 1, 'SHOP', @shop_a_admin, NULL
WHERE NOT EXISTS (SELECT 1 FROM product_status_history WHERE spu_id = @spu_a3 AND operation_type = 'PUT_ON_SHELF');

INSERT INTO product_status_history (spu_id, from_status, to_status, operation_type, content_version, operator_type, operator_id, reason)
SELECT @spu_review, NULL, 'DRAFT', 'CREATE', 0, 'SHOP', @shop_a_admin, NULL
WHERE NOT EXISTS (SELECT 1 FROM product_status_history WHERE spu_id = @spu_review AND operation_type = 'CREATE');
INSERT INTO product_status_history (spu_id, from_status, to_status, operation_type, content_version, operator_type, operator_id, reason)
SELECT @spu_review, 'DRAFT', 'PENDING_REVIEW', 'SUBMIT_REVIEW', 2, 'SHOP', @shop_a_admin, NULL
WHERE NOT EXISTS (SELECT 1 FROM product_status_history WHERE spu_id = @spu_review AND operation_type = 'SUBMIT_REVIEW');

-- Inventory snapshots and immutable ledger rows.
INSERT INTO inventory_stock (sku_id, available_quantity, locked_quantity, version)
VALUES (@sku_a3, 12, 0, 0), (@sku_a4, 5, 1, 0), (@sku_b2, 18, 0, 0), (@sku_b3, 25, 0, 0), (@sku_review, 6, 0, 0)
ON DUPLICATE KEY UPDATE available_quantity = VALUES(available_quantity), locked_quantity = VALUES(locked_quantity);

INSERT INTO inventory_transaction (transaction_no, sku_id, transaction_type, available_change, locked_change,
                                   available_after, locked_after, business_type, business_no, operator_id, remark)
VALUES
    ('INV_DEMO_IN_A3', @sku_a3, 'INBOUND', 12, 0, 12, 0, 'DEMO', 'DEMO_IN_A3', @shop_a_admin, '演示库存入库'),
    ('INV_DEMO_DEDUCT_A3', @sku_a3, 'DEDUCT', 0, -1, 11, 0, 'ORDER', 'ORDER_DEMO_001A', @shop_a_admin, '已完成订单扣减库存'),
    ('INV_DEMO_IN_A4', @sku_a4, 'INBOUND', 6, 0, 6, 0, 'DEMO', 'DEMO_IN_A4', @shop_a_admin, '演示库存入库'),
    ('INV_DEMO_LOCK_A4', @sku_a4, 'LOCK', -1, 1, 5, 1, 'TRADE', 'TRADE_DEMO_002', @buyer_a, '待支付订单锁定'),
    ('INV_DEMO_IN_B2', @sku_b2, 'INBOUND', 18, 0, 18, 0, 'DEMO', 'DEMO_IN_B2', @shop_b_admin, '演示库存入库'),
    ('INV_DEMO_DEDUCT_B2', @sku_b2, 'DEDUCT', 0, -2, 16, 0, 'ORDER', 'ORDER_DEMO_001B', @shop_b_admin, '已发货订单扣减库存'),
    ('INV_DEMO_IN_B3', @sku_b3, 'INBOUND', 25, 0, 25, 0, 'DEMO', 'DEMO_IN_B3', @shop_b_admin, '演示库存入库'),
    ('INV_DEMO_IN_REVIEW', @sku_review, 'INBOUND', 6, 0, 6, 0, 'DEMO', 'DEMO_IN_REVIEW', @shop_a_admin, '演示库存入库')
ON DUPLICATE KEY UPDATE remark = VALUES(remark);
UPDATE inventory_stock SET available_quantity = 11, locked_quantity = 0 WHERE sku_id = @sku_a3;
UPDATE inventory_stock SET available_quantity = 16, locked_quantity = 0 WHERE sku_id = @sku_b2;

-- Cart rows: selected items, an unselected item and a second buyer's cart.
INSERT INTO cart_item (user_id, sku_id, quantity, selected)
VALUES (@buyer_a, @sku_a3, 1, 1), (@buyer_a, @sku_b2, 1, 1),
       (@buyer_a, @sku_b3, 1, 0), (@buyer_b, @sku_a4, 2, 1)
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity), selected = VALUES(selected);

-- Wallet ledger for the paid demo trade. The final balance is kept aligned with these rows.
SET @wallet_a = (SELECT id FROM wallet_account WHERE user_id = @buyer_a);
UPDATE wallet_account SET balance = 23825.00, status = 'ACTIVE' WHERE id = @wallet_a;
INSERT INTO wallet_transaction (
    transaction_no, wallet_id, transaction_type, direction, amount, balance_before, balance_after,
    business_type, business_no, operator_id, remark
)
VALUES
    ('WALLET_DEMO_RECHARGE', @wallet_a, 'RECHARGE', 'CREDIT', 5000.00, 20000.00, 25000.00,
     'RECHARGE', 'RECHARGE_DEMO_001', @buyer_a, '演示账户充值'),
    ('WALLET_DEMO_CONSUME', @wallet_a, 'CONSUME', 'DEBIT', 1175.00, 25000.00, 23825.00,
     'TRADE', 'TRADE_DEMO_001', @buyer_a, '演示跨店订单支付')
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

-- Three parent trades cover paid, pending-payment and cancelled list states.
INSERT INTO trade_order (
    trade_no, user_id, trade_status, payable_amount, recipient_name, recipient_phone,
    province_name, city_name, district_name, detail_address, pay_expire_at, paid_at, cancelled_at, version, created_at
)
VALUES
    ('TRADE_DEMO_001', @buyer_a, 'PAID', 1175.00, '张三', '13800000001', '上海市', '上海市', '杨浦区', '延吉中路 100 号',
     DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 20 DAY), DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 5 DAY), NULL, 1, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 6 DAY)),
    ('TRADE_DEMO_002', @buyer_a, 'PENDING_PAYMENT', 509.00, '张三', '13800000001', '上海市', '上海市', '杨浦区', '延吉中路 100 号',
     DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 MINUTE), NULL, NULL, 0, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 2 HOUR)),
    ('TRADE_DEMO_003', @buyer_b, 'CANCELLED', 167.00, '李梅', '13800000002', '浙江省', '杭州市', '西湖区', '文三路 88 号',
     DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 3 DAY), NULL, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 2 DAY), 1, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 4 DAY))
ON DUPLICATE KEY UPDATE payable_amount = VALUES(payable_amount), trade_status = VALUES(trade_status),
    pay_expire_at = VALUES(pay_expire_at), paid_at = VALUES(paid_at), cancelled_at = VALUES(cancelled_at), version = VALUES(version);

SET @trade1 = (SELECT id FROM trade_order WHERE trade_no = 'TRADE_DEMO_001');
SET @trade2 = (SELECT id FROM trade_order WHERE trade_no = 'TRADE_DEMO_002');
SET @trade3 = (SELECT id FROM trade_order WHERE trade_no = 'TRADE_DEMO_003');

INSERT INTO order_info (
    order_no, trade_id, user_id, shop_id, shop_name, order_status, payment_status, item_amount,
    freight_amount, payable_amount, refund_amount, buyer_remark, carrier_code, carrier_name,
    tracking_no, shipped_at, completed_at, cancelled_at, version, created_at
)
VALUES
    ('ORDER_DEMO_001A', @trade1, @buyer_a, @shop_a, '时光数码店 A', 'COMPLETED', 'PAID', 899.00, 10.00, 909.00, 0.00,
     '请尽快发货', 'SF', '顺丰速运', 'SFDEMO001A', DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 4 DAY), DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY), NULL, 3, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 6 DAY)),
    ('ORDER_DEMO_001B', @trade1, @buyer_a, @shop_b, '时光生活店 B', 'PENDING_RECEIPT', 'PAID', 258.00, 8.00, 266.00, 0.00,
     NULL, 'ZTO', '中通快递', 'ZTODEMO001B', DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 2 DAY), NULL, NULL, 2, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 6 DAY)),
    ('ORDER_DEMO_002A', @trade2, @buyer_a, @shop_a, '时光数码店 A', 'PENDING_PAYMENT', 'UNPAID', 499.00, 10.00, 509.00, 0.00,
     NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 2 HOUR)),
    ('ORDER_DEMO_003B', @trade3, @buyer_b, @shop_b, '时光生活店 B', 'CANCELLED', 'UNPAID', 159.00, 8.00, 167.00, 0.00,
     NULL, NULL, NULL, NULL, NULL, NULL, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 2 DAY), 1, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 4 DAY))
ON DUPLICATE KEY UPDATE order_status = VALUES(order_status), payment_status = VALUES(payment_status),
    payable_amount = VALUES(payable_amount), carrier_code = VALUES(carrier_code), carrier_name = VALUES(carrier_name),
    tracking_no = VALUES(tracking_no), shipped_at = VALUES(shipped_at), completed_at = VALUES(completed_at),
    cancelled_at = VALUES(cancelled_at), version = VALUES(version);

SET @order1a = (SELECT id FROM order_info WHERE order_no = 'ORDER_DEMO_001A');
SET @order1b = (SELECT id FROM order_info WHERE order_no = 'ORDER_DEMO_001B');
SET @order2a = (SELECT id FROM order_info WHERE order_no = 'ORDER_DEMO_002A');
SET @order3b = (SELECT id FROM order_info WHERE order_no = 'ORDER_DEMO_003B');

INSERT INTO order_item (
    order_id, shop_id, spu_id, sku_id, spu_no, sku_no, product_name, sku_name, spec_json, image_url,
    unit_price, quantity, original_amount, freight_amount, payable_amount, refunded_quantity,
    refunded_amount, reservation_status
)
VALUES
    (@order1a, @shop_a, @spu_a3, @sku_a3, 'SPU_DEMO_A3', 'SKU_DEMO_A3_BLACK', '时光降噪耳机 Pro', '曜石黑', JSON_OBJECT('color', '曜石黑'), 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=480&q=80', 899.00, 1, 899.00, 10.00, 909.00, 0, 0.00, 'DEDUCTED'),
    (@order1b, @shop_b, @spu_b2, @sku_b2, 'SPU_DEMO_B2', 'SKU_DEMO_B2_GREEN', '晨光保温杯 480ml', '抹茶绿', JSON_OBJECT('color', '抹茶绿'), 'https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=480&q=80', 129.00, 2, 258.00, 8.00, 266.00, 0, 0.00, 'DEDUCTED'),
    (@order2a, @shop_a, @spu_a4, @sku_a4, 'SPU_DEMO_A4', 'SKU_DEMO_A4_STANDARD', '极简机械键盘 87 键', '静音轴', JSON_OBJECT('switch', '静音轴'), 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=480&q=80', 499.00, 1, 499.00, 10.00, 509.00, 0, 0.00, 'LOCKED'),
    (@order3b, @shop_b, @spu_b3, @sku_b3, 'SPU_DEMO_B3', 'SKU_DEMO_B3_STANDARD', '云朵护颈枕', '标准款', JSON_OBJECT('size', '标准款'), 'https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?w=480&q=80', 159.00, 1, 159.00, 8.00, 167.00, 0, 0.00, 'RELEASED')
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity), reservation_status = VALUES(reservation_status), image_url = VALUES(image_url);

SET @item1a = (SELECT id FROM order_item WHERE order_id = @order1a);
SET @item1b = (SELECT id FROM order_item WHERE order_id = @order1b);

-- Order histories make the status timeline pages useful immediately.
INSERT INTO order_status_history (order_id, from_status, to_status, operation_type, operator_type, operator_id, remark)
SELECT @order1a, NULL, 'PENDING_PAYMENT', 'CREATE', 'USER', @buyer_a, NULL
WHERE NOT EXISTS (SELECT 1 FROM order_status_history WHERE order_id = @order1a AND operation_type = 'CREATE');
INSERT INTO order_status_history (order_id, from_status, to_status, operation_type, operator_type, operator_id, remark)
SELECT @order1a, 'PENDING_PAYMENT', 'PENDING_SHIPMENT', 'PAY', 'USER', @buyer_a, '钱包支付成功'
WHERE NOT EXISTS (SELECT 1 FROM order_status_history WHERE order_id = @order1a AND operation_type = 'PAY');
INSERT INTO order_status_history (order_id, from_status, to_status, operation_type, operator_type, operator_id, remark)
SELECT @order1a, 'PENDING_SHIPMENT', 'PENDING_RECEIPT', 'SHIP', 'SHOP', @shop_a_admin, '顺丰发货'
WHERE NOT EXISTS (SELECT 1 FROM order_status_history WHERE order_id = @order1a AND operation_type = 'SHIP');
INSERT INTO order_status_history (order_id, from_status, to_status, operation_type, operator_type, operator_id, remark)
SELECT @order1a, 'PENDING_RECEIPT', 'COMPLETED', 'COMPLETE', 'USER', @buyer_a, '买家确认收货'
WHERE NOT EXISTS (SELECT 1 FROM order_status_history WHERE order_id = @order1a AND operation_type = 'COMPLETE');

INSERT INTO order_status_history (order_id, from_status, to_status, operation_type, operator_type, operator_id, remark)
SELECT @order1b, NULL, 'PENDING_PAYMENT', 'CREATE', 'USER', @buyer_a, NULL
WHERE NOT EXISTS (SELECT 1 FROM order_status_history WHERE order_id = @order1b AND operation_type = 'CREATE');
INSERT INTO order_status_history (order_id, from_status, to_status, operation_type, operator_type, operator_id, remark)
SELECT @order1b, 'PENDING_PAYMENT', 'PENDING_SHIPMENT', 'PAY', 'USER', @buyer_a, '钱包支付成功'
WHERE NOT EXISTS (SELECT 1 FROM order_status_history WHERE order_id = @order1b AND operation_type = 'PAY');
INSERT INTO order_status_history (order_id, from_status, to_status, operation_type, operator_type, operator_id, remark)
SELECT @order1b, 'PENDING_SHIPMENT', 'PENDING_RECEIPT', 'SHIP', 'SHOP', @shop_b_admin, '中通发货'
WHERE NOT EXISTS (SELECT 1 FROM order_status_history WHERE order_id = @order1b AND operation_type = 'SHIP');

INSERT INTO order_status_history (order_id, from_status, to_status, operation_type, operator_type, operator_id, remark)
SELECT @order2a, NULL, 'PENDING_PAYMENT', 'CREATE', 'USER', @buyer_a, NULL
WHERE NOT EXISTS (SELECT 1 FROM order_status_history WHERE order_id = @order2a AND operation_type = 'CREATE');
INSERT INTO order_status_history (order_id, from_status, to_status, operation_type, operator_type, operator_id, remark)
SELECT @order3b, NULL, 'PENDING_PAYMENT', 'CREATE', 'USER', @buyer_b, NULL
WHERE NOT EXISTS (SELECT 1 FROM order_status_history WHERE order_id = @order3b AND operation_type = 'CREATE');
INSERT INTO order_status_history (order_id, from_status, to_status, operation_type, operator_type, operator_id, remark)
SELECT @order3b, 'PENDING_PAYMENT', 'CANCELLED', 'CANCEL', 'SYSTEM', NULL, '超时未支付'
WHERE NOT EXISTS (SELECT 1 FROM order_status_history WHERE order_id = @order3b AND operation_type = 'CANCEL');

INSERT INTO payment_order (payment_no, trade_id, amount, status, failure_reason, paid_at, expires_at)
VALUES
    ('PAY_DEMO_001', @trade1, 1175.00, 'SUCCESS', NULL, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 5 DAY), DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 5 DAY)),
    ('PAY_DEMO_002', @trade2, 509.00, 'PENDING', NULL, NULL, DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 MINUTE)),
    ('PAY_DEMO_003', @trade3, 167.00, 'CANCELLED', NULL, NULL, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 2 DAY))
ON DUPLICATE KEY UPDATE status = VALUES(status), amount = VALUES(amount), paid_at = VALUES(paid_at),
    expires_at = VALUES(expires_at), failure_reason = VALUES(failure_reason);

-- Two after-sale states: a newly submitted refund and an approved return awaiting buyer shipment.
INSERT INTO after_sale_request (
    after_sale_no, order_id, order_item_id, user_id, request_type, quantity, reason_code,
    reason_description, evidence_json, requested_amount, approved_quantity, approved_amount,
    status, reviewer_id, review_comment, reviewed_at, return_carrier_code, return_carrier_name,
    return_tracking_no, returned_at, return_received_at, refund_no, refund_status,
    refund_failure_reason, refunded_at, completed_at, cancelled_at, version, created_at
)
VALUES
    ('AFTER_DEMO_001', @order1a, @item1a, @buyer_a, 'REFUND_ONLY', 1, 'QUALITY_PROBLEM',
     '耳机左侧偶发断连，申请仅退款', JSON_ARRAY('https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=480&q=80'),
     899.00, NULL, NULL, 'PENDING',
     NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
     'NOT_STARTED', NULL, NULL, NULL, NULL, 0,
     DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 2 HOUR)),
    ('AFTER_DEMO_002', @order1b, @item1b, @buyer_a, 'RETURN_REFUND', 1, 'NOT_WANTED',
     '颜色与预期不符，申请退货退款', JSON_ARRAY(), 129.00, 1, 129.00, 'WAITING_RETURN', @auditor,
     '同意退货，请在 7 天内寄回', DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY), NULL, NULL, NULL, NULL, NULL,
     'NOT_STARTED', NULL, NULL, NULL, NULL, 1, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 2 DAY))
ON DUPLICATE KEY UPDATE status = VALUES(status), approved_quantity = VALUES(approved_quantity),
    approved_amount = VALUES(approved_amount), review_comment = VALUES(review_comment),
    reviewed_at = VALUES(reviewed_at), created_at = VALUES(created_at), version = VALUES(version);

COMMIT;

-- Quick verification output for local development.
SELECT 'demo counts' AS section,
       (SELECT COUNT(*) FROM product_spu WHERE spu_no LIKE 'SPU_DEMO_%') AS demo_spus,
       (SELECT COUNT(*) FROM product_sku WHERE sku_no LIKE 'SKU_DEMO_%') AS demo_skus,
       (SELECT COUNT(*) FROM trade_order WHERE trade_no LIKE 'TRADE_DEMO_%') AS demo_trades,
       (SELECT COUNT(*) FROM after_sale_request WHERE after_sale_no LIKE 'AFTER_DEMO_%') AS demo_after_sales;
