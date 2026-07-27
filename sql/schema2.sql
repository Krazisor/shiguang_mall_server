-- Shiguang Market required incremental migration 2
-- MySQL 8.0.16+ only. Run after schema.sql on database `market`.
-- Required by the current combined product/API contract, including baseline features.
-- This migration contains only DML and is safe to run more than once.

USE `market`;

SET NAMES utf8mb4;
SET time_zone = '+08:00';

START TRANSACTION;

-- Align existing seed metadata with the current product and API contracts.
UPDATE sys_role
SET description = '管理目录基础资料、审核商品和平台强制禁售'
WHERE role_code = 'PLATFORM_PRODUCT_AUDITOR';

UPDATE sys_permission
SET resource = '/api/trades/**'
WHERE permission_code = 'trade:create';

-- Add current-contract platform permissions without masking unrelated constraint errors.
INSERT INTO sys_permission (
    permission_code,
    permission_name,
    scope_type,
    resource,
    http_method
)
SELECT
    'platform:catalog:manage',
    '平台管理类目品牌',
    'PLATFORM',
    '/api/platform/catalog/**',
    NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission
    WHERE permission_code = 'platform:catalog:manage'
);

INSERT INTO sys_permission (
    permission_code,
    permission_name,
    scope_type,
    resource,
    http_method
)
SELECT
    'platform:operation:read',
    '平台运营只读查询',
    'PLATFORM',
    '/api/platform/operations/**',
    'GET'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission
    WHERE permission_code = 'platform:operation:read'
);

INSERT INTO sys_permission (
    permission_code,
    permission_name,
    scope_type,
    resource,
    http_method
)
SELECT
    'platform:task:execute',
    '平台执行内部任务',
    'PLATFORM',
    '/api/internal/tasks/**',
    'POST'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission
    WHERE permission_code = 'platform:task:execute'
);

-- Product auditors manage catalog data in addition to review and ban actions.
INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type
FROM sys_role r
JOIN sys_permission p
  ON p.permission_code = 'platform:catalog:manage'
 AND p.scope_type = r.scope_type
WHERE r.role_code = 'PLATFORM_PRODUCT_AUDITOR'
  AND r.scope_type = 'PLATFORM'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

-- SUPER_ADMIN was seeded before these permissions existed, so grant them explicitly.
INSERT INTO sys_role_permission (role_id, permission_id, scope_type)
SELECT r.id, p.id, r.scope_type
FROM sys_role r
JOIN sys_permission p
  ON p.permission_code IN (
      'platform:catalog:manage',
      'platform:operation:read',
      'platform:task:execute'
  )
 AND p.scope_type = r.scope_type
WHERE r.role_code = 'SUPER_ADMIN'
  AND r.scope_type = 'PLATFORM'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

COMMIT;
