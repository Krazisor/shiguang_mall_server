# 平台店铺成员管理接口系列

## 1. 文档目的

本文件定义平台管理员管理指定店铺成员的接口系列，作为现有店铺成员管理接口之外的独立平台端接口契约。

本需求解决的问题是：平台管理员可以通过平台后台进入任意店铺，并查看该店铺的成员、成员状态和店铺角色；平台管理员不需要先成为该店铺成员。

本系列提供完整的店铺成员关系管理能力：查询成员、添加成员、修改成员角色、启用或停用成员，以及移除成员关系。这里的“删除”只删除 `shop_user` 中的店铺成员关系，不删除 `sys_user` 平台账号。

## 2. 背景与权限边界

现有店铺端接口为：

```text
GET /api/shops/{shopId}/members
```

该接口属于店铺工作台，业务层使用以下店铺权限进行鉴权：

```text
shop:member:manage
```

店铺权限不仅检查权限码，还检查当前账号是否是指定店铺的 `ACTIVE` 成员。因此，只有平台角色而不是该店铺成员的账号，不能访问现有店铺端接口。这是预期的权限边界，不是前端调用问题。

平台后台使用另一套权限域：

```text
platform:shop:manage
```

因此平台成员查询必须使用独立的 URL 和平台权限检查：

| 访问入口 | 授权方式 | 数据范围 | 适用角色 |
| --- | --- | --- | --- |
| `/api/shops/{shopId}/members` | `SHOP(shop:member:manage)` | 当前账号所属的指定店铺 | 店铺管理员及被授予该店铺权限的成员 |
| `/api/platform/shops/{shopId}/members` | `PERM(platform:shop:member:manage)` | 平台管理范围内的任意指定店铺 | 平台店铺管理员、超级管理员等拥有该权限的账号 |

不能通过修改 `ShopAccessService.require(...)`，让平台权限全局绕过店铺成员校验。该服务还被商品、库存、订单、售后等店铺业务复用，扩大绕过范围会改变其他业务的授权模型。

## 3. 接口总览

本系列包含以下接口：

| 方法 | 路径 | 鉴权 | 成功响应 |
| --- | --- | --- | --- |
| `GET` | `/api/platform/shops/{shopId}/members` | `platform:shop:member:manage` | `200`，`PageView<ShopMemberView>` |
| `POST` | `/api/platform/shops/{shopId}/members` | `platform:shop:member:manage` | `201`，`ShopMemberView` |
| `PUT` | `/api/platform/shops/{shopId}/members/{userId}/role` | `platform:shop:member:manage` | `200`，`ShopMemberView` |
| `POST` | `/api/platform/shops/{shopId}/members/{userId}/status` | `platform:shop:member:manage` | `200`，`ShopMemberView` |
| `DELETE` | `/api/platform/shops/{shopId}/members/{userId}` | `platform:shop:member:manage` | `200`，`null` |

接口应挂载在现有平台店铺资源下，与以下接口保持同一平台管理路径：

```text
/api/platform/shops
/api/platform/shops/{shopId}
/api/platform/shops/{shopId}/members
```

## 4. 接口定义

### 4.1 请求

```http
GET /api/platform/shops/{shopId}/members
```

路径参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `shopId` | `long` | 是 | 店铺 ID，必须为正整数 |

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `keyword` | `string` | 否 | 无 | 按用户名或昵称模糊查询；空白字符串按未传处理 |
| `roleId` | `long` | 否 | 无 | 按店铺角色 ID 精确筛选 |
| `status` | `ACTIVE` / `DISABLED` | 否 | 无 | 按成员状态筛选 |
| `page` | `long` | 否 | `1` | 页码，从 1 开始 |
| `pageSize` | `long` | 否 | `20` | 每页数量，取值范围 `1..100` |

示例：

```http
GET /api/platform/shops/201/members?keyword=客服&status=ACTIVE&page=1&pageSize=20
```

### 4.2 鉴权要求

Service 层必须调用：

```java
currentUser.requirePermission("platform:shop:member:manage");
```

该调用会同时检查登录状态和当前账号是否有效。账号被禁用、锁定或删除时，应沿用统一的认证错误语义。

平台接口不调用：

```java
shopAccess.require(shopId, "shop:member:manage");
```

因为平台管理员不要求是该店铺成员。鉴权通过后仍必须先确认 `shopId` 对应的店铺存在；不存在时返回 `SHOP_NOT_FOUND`，不能把不存在的店铺静默当成空成员列表。平台成员管理的所有读写操作都使用 `platform:shop:member:manage`，不继承 `platform:shop:manage` 的权限。

### 4.3 添加成员

```http
POST /api/platform/shops/{shopId}/members
```

请求体复用现有 `AddShopMemberRequest`：

```json
{
  "username": "shop_a_order",
  "roleId": "2002"
}
```

规则：

- `username` 必须精确匹配一个 `ACTIVE` 用户；
- `roleId` 必须指向有效的 `SHOP`、`ACTIVE` 角色；
- 用户已经是该店铺成员时返回 `SHOP_MEMBER_ALREADY_EXISTS`；
- 成功后成员状态为 `ACTIVE`，角色范围为 `SHOP`；
- 成功状态码为 `201 Created`。

### 4.4 修改成员角色

```http
PUT /api/platform/shops/{shopId}/members/{userId}/role
```

请求体复用 `ChangeShopMemberRoleRequest`：

```json
{
  "roleId": "2003"
}
```

规则与店铺端一致：目标成员必须存在，目标角色必须是有效的 `SHOP/ACTIVE` 角色；如果操作会移除最后一名拥有 `shop:member:manage` 的有效店铺管理员，返回 `LAST_SHOP_ADMIN_REQUIRED`。

### 4.5 修改成员状态

```http
POST /api/platform/shops/{shopId}/members/{userId}/status
```

请求体复用 `StatusRequest`：

```json
{
  "targetStatus": "DISABLED"
}
```

启用成员时，关联用户必须仍是 `ACTIVE`；停用拥有成员管理权限的最后一名有效管理员时，返回 `LAST_SHOP_ADMIN_REQUIRED`。平台管理员不受“不能停用自己”的店铺端自我保护限制，但仍必须保留至少一名有效店铺成员管理员。

### 4.6 移除成员关系

```http
DELETE /api/platform/shops/{shopId}/members/{userId}
```

删除语义是从 `shop_user` 移除指定店铺与用户之间的成员关系：

- 不删除 `sys_user` 用户账号；
- 不影响该用户在其他店铺的成员关系；
- 不允许移除最后一名有效店铺成员管理员，返回 `LAST_SHOP_ADMIN_REQUIRED`；
- 成员不存在时返回 `SHOP_MEMBER_NOT_FOUND`；
- 成功返回 `200 OK` 和 `ApiResponse.success(null)`。

删除操作必须在 Service 事务中执行，并使用 `shop_id + user_id` 双条件，不能按单一 `userId` 删除。

### 4.7 成功响应

成功状态码为 `200 OK`，统一响应包装为：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "items": [
      {
        "shopId": "201",
        "user": {
          "id": "105",
          "username": "merchant_01",
          "nickname": "商家客服",
          "avatarUrl": null,
          "status": "ACTIVE"
        },
        "role": {
          "id": "2003",
          "roleCode": "SHOP_ORDER_OPERATOR",
          "roleName": "店铺订单客服",
          "scopeType": "SHOP",
          "description": "处理本店订单和售后",
          "status": "ACTIVE",
          "createdAt": "2026-07-20T10:00:00.000+08:00",
          "updatedAt": "2026-07-20T10:00:00.000+08:00"
        },
        "status": "ACTIVE",
        "createdAt": "2026-07-26T18:30:15.123+08:00",
        "updatedAt": "2026-07-26T18:30:15.123+08:00"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "totalPages": 1
  },
  "requestId": "4f7e0f35-1c0d-4f20-9c61-c3a46c4b6fb7",
  "timestamp": "2026-08-09T10:00:00.000+08:00"
}
```

`ShopMemberView` 与现有店铺成员接口保持一致：

```text
shopId: string
user: UserSummary
role: RoleView
status: ACTIVE | DISABLED
createdAt: OffsetDateTime
updatedAt: OffsetDateTime
```

外部 ID 必须以字符串返回，时间使用统一的 `Formatters.time(...)` 格式。不得直接暴露数据库实体。

### 4.8 查询与排序

查询继续使用 `ShopUserMapper.selectMemberPage(...)` 的数据库分页能力：

- `shop_id = shopId` 必须在 SQL 中限定；
- 用户必须关联 `sys_user` 且未逻辑删除；
- `keyword` 同时匹配 `username` 和 `nickname`；
- `roleId`、`status` 作为可选条件在数据库层生效；
- 默认按 `su.created_at DESC, su.user_id DESC` 排序；
- `total` 必须对应全部筛选条件，不能查询后再在 Java 中过滤；
- 成员角色和用户信息通过现有 DTO 映射返回。

平台查询和店铺查询可以复用分页、筛选和视图转换逻辑，但必须保留两条独立的鉴权入口。例如：

```java
public PageView<ShopMemberView> listForShop(...) {
    shopAccess.require(shopId, "shop:member:manage");
    return listMembers(...);
}

public PageView<ShopMemberView> listForPlatform(...) {
    currentUser.requirePermission("platform:shop:member:manage");
    requireShop(shopId);
    return listMembers(...);
}
```

## 5. 错误语义

接口应沿用项目统一错误响应结构。主要错误如下：

| 场景 | HTTP | 错误码 | 说明 |
| --- | --- | --- | --- |
| 未登录 | `401` | 项目统一认证错误码 | 由 Sa-Token 处理 |
| 账号被禁用、锁定或删除 | `403` | `AUTH_ACCOUNT_DISABLED` / `AUTH_ACCOUNT_LOCKED` | 由 `CurrentUserService` 处理 |
| 没有平台店铺成员管理权限 | `403` | 项目统一权限错误码 | `platform:shop:member:manage` 校验失败 |
| 店铺不存在 | `404` | `SHOP_NOT_FOUND` | `shopId` 没有对应店铺 |
| 分页参数非法 | `400` | `BAD_REQUEST` | `page < 1`、`pageSize < 1` 或 `pageSize > 100` |
| 参数格式非法 | `400` | `VALIDATION_FAILED` | `roleId`、`shopId` 等无法解析为正整数 |

店铺端接口原有的越权语义仍保持不变：非成员或无 `shop:member:manage` 权限时由 `ShopAccessService` 返回 `RESOURCE_NOT_FOUND`，不因平台接口增加而改变。

## 6. 明确不在本系列范围内的能力

以下能力本次仍不包括：

- 删除平台用户账号；
- 修改平台角色、平台权限或用户登录状态；
- 修改店铺基础资料和店铺状态；
- 批量导入、批量删除成员；
- 将一个用户的成员关系转移到另一家店铺；
- 绕过“最后一名店铺管理员”保护。

如果未来需要更细粒度地区分平台查看和平台管理，可再拆分 `platform:shop:member:read` 与 `platform:shop:member:manage`。当前 CRUD 接口统一使用后者，减少同一操作集合的权限判断分裂。

平台成员管理仍应补充：

- 操作审计和操作者记录；
- 店铺管理员最后一人保护；
- 平台操作是否通知店铺；
- 平台管理员能否跨店铺新增成员；
- 成员角色可选范围和账号状态校验。

## 7. 实现边界

推荐的分层职责如下：

| 层 | 职责 |
| --- | --- |
| `PlatformShopController` | 暴露 `/api/platform/shops/{shopId}/members`，绑定路径和查询参数 |
| `ShopMemberService` 或独立查询 Service | 分别执行平台/店铺鉴权，复用成员分页查询和 DTO 映射 |
| `ShopMapper` | 校验店铺存在并读取店铺基础信息 |
| `ShopUserMapper` | 执行带 `shop_id` 条件的成员分页查询 |
| `IdentityViewMapper` | 将用户和店铺角色转换为公开 DTO |
| `CurrentUserService` | 平台账号有效性和平台权限检查 |
| `ShopAccessService` | 仅用于店铺端成员权限检查，不作为平台入口的授权绕过点 |

Controller 不直接访问 Mapper，也不自行拼接响应 JSON。分页响应使用 `PageView.of(...)`，成功响应使用 `ApiResponse.success(...)`。

## 8. 测试验收清单

至少应覆盖以下场景：

1. 拥有 `platform:shop:member:manage` 但不是该店铺成员的账号，可以查询和管理成员。
2. 只有 `platform:shop:manage`、没有 `platform:shop:member:manage` 的账号不能访问本系列接口。
3. 没有 `platform:shop:member:manage` 的已登录账号不能访问平台接口。
4. 只有 `shop:member:manage` 的店铺成员不能借用平台接口获得访问权。
5. 原 `/api/shops/{shopId}/members` 仍要求当前用户是有效店铺成员。
6. 不存在的店铺返回 `SHOP_NOT_FOUND`，而不是 `200` 空分页。
7. `keyword`、`roleId`、`status`、`page`、`pageSize` 筛选和分页结果正确。
8. 平台查询结果只返回目标 `shopId` 的成员，不能混入其他店铺成员。
9. 添加、改角色、改状态和移除操作均使用 `shop_id + user_id` 双条件。
10. 添加重复成员、无效角色、无效用户时返回既有业务错误码。
11. 修改角色、停用或移除最后一名成员管理员时返回 `LAST_SHOP_ADMIN_REQUIRED`。
12. 移除成员不会删除 `sys_user`，也不会影响该用户在其他店铺的成员关系。
13. 用户或角色已逻辑删除时，不应通过成员查询返回无效关联数据。
14. 响应中的 ID、时间和分页元数据符合统一 API 契约。
15. 平台接口不暴露平台用户账号删除等无关能力。

## 9. 数据库迁移结论

本接口不引入新的持久化模型，直接复用现有表和字段：

- `shop`：校验店铺存在；
- `shop_user`：保存店铺成员关系、角色和成员状态；
- `sys_user`：返回用户摘要并排除逻辑删除用户；
- `sys_role`：返回店铺角色信息；
- `sys_permission` 及角色授权表：提供平台成员管理权限。

新增权限为：

```text
platform:shop:member:manage
```

该权限属于 `PLATFORM` 范围，资源为 `/api/platform/shops/*/members/**`，授予现有 `PLATFORM_SHOP_ADMIN` 和 `SUPER_ADMIN`。表结构、成员关系字段和索引均不变，因此 `sql/scheme4.sql` 只包含幂等的权限字典和角色授权 DML，不创建新表。

数据库执行顺序为：

```text
schema.sql -> schema2.sql -> scheme3.sql -> scheme4.sql
```

如果未来需要把查看和修改拆成独立权限，再新增后续迁移，不回写本文件定义的 `platform:shop:member:manage`。

## 10. 兼容性说明

新增平台接口不会改变以下已有契约：

- `/api/shops/{shopId}/members` 的路径、请求参数、响应 DTO 和店铺权限要求；
- `shop:member:manage` 的权限含义；
- `ShopAccessService` 对店铺成员身份的校验；
- 现有平台店铺列表、详情、编辑和状态接口；
- 现有数据库表结构和迁移顺序。

前端平台后台应调用新的 `/api/platform/shops/{shopId}/members`，店铺工作台继续调用原 `/api/shops/{shopId}/members`。两者不要根据页面名称或当前选中店铺在前端混用接口。
