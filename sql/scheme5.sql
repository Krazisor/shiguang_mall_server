-- Shiguang Market platform product overview permission migration
-- MySQL 8.0.16+ only. Run after schema.sql, schema2.sql, scheme3.sql and scheme4.sql.
-- This migration is idempotent and changes permission metadata only.

USE `market`;

SET NAMES utf8mb4;
SET time_zone = '+08:00';

START TRANSACTION;

INSERT INTO sys_permission (
    permission_code,
    permission_name,
    scope_type,
    resource,
    http_method
)
SELECT
    'platform:product:read',
    '平台查看全部商品',
    'PLATFORM',
    '/api/platform/products/**',
    'GET'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission
    WHERE permission_code = 'platform:product:read'
);

INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type
FROM sys_role r
JOIN sys_permission p
  ON p.permission_code = 'platform:product:read'
 AND p.scope_type = 'PLATFORM'
WHERE r.role_code IN ('PLATFORM_PRODUCT_AUDITOR', 'SUPER_ADMIN')
  AND r.scope_type = 'PLATFORM'
  AND r.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

COMMIT;
