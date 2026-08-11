# 商家角色查询与员工身份调整需求及 API 文档

## 1. 文档目的

本文定义商家管理员查看本店可分配角色、查看店铺员工当前身份，以及调整员工店铺角色的产品需求和 HTTP API 契约。

本文解决现有流程中的一个缺口：当前店铺成员接口允许提交 `roleId` 修改员工角色，但商家端没有角色字典接口，前端只能依赖硬编码角色或让管理员手工输入角色 ID。新增角色查询接口后，商家管理员可以在员工管理页面读取当前有效的商家角色，并通过现有角色修改接口完成身份调整。

本文只描述商家端能力，不开放平台角色管理能力。角色的创建、修改、停用和权限授权仍由平台 RBAC 管理员负责。

## 2. 背景与现状

### 2.1 现有能力

当前已经存在以下店铺成员接口：

| 方法 | 路径 | 能力 |
| --- | --- | --- |
| `GET` | `/api/shops/{shopId}/members` | 分页查询本店成员及其当前角色 |
| `POST` | `/api/shops/{shopId}/members` | 新增本店成员并分配角色 |
| `PUT` | `/api/shops/{shopId}/members/{userId}/role` | 修改成员的店铺角色 |
| `POST` | `/api/shops/{shopId}/members/{userId}/status` | 启用或停用成员 |

现有角色管理接口为：

```text
GET /api/platform/rbac/roles?scopeType=SHOP&status=ACTIVE
```

该接口属于平台 RBAC 管理域，需要 `platform:rbac:manage`，普通商家管理员不能调用。不能通过放宽平台接口权限来解决商家端需求，因为平台 RBAC 接口还包含角色详情、权限替换和角色状态管理等敏感能力。

### 2.2 当前角色模型

角色记录存储在 `sys_role`，店铺成员通过 `shop_user.role_id` 关联角色。店铺角色具有以下约束：

- `scope_type` 必须为 `SHOP`；
- 只有 `status = ACTIVE` 的角色可以分配给成员；
- 角色是全平台共享的角色模板，不是每个店铺独立创建的一套角色；
- 平台新增一个 `SHOP` 角色后，该角色原则上可以被所有拥有成员管理权限的店铺使用；
- 商家管理员不能创建、修改、停用或删除角色及角色权限。

系统内置的店铺角色包括 `SHOP_ADMIN`、`SHOP_PRODUCT_OPERATOR`、`SHOP_ORDER_OPERATOR` 和 `SHOP_INVENTORY_OPERATOR`。平台后续可以创建其他 `SHOP` 作用域角色，前端不得把内置角色代码写死为唯一选项来源。

## 3. 产品需求

### 3.1 目标

商家管理员进入“员工管理”页面后，能够：

1. 查看当前店铺的员工列表、员工状态和当前店铺角色；
2. 查看所有当前有效、可分配的店铺角色；
3. 选择一名店铺员工并为其更换店铺角色；
4. 在角色调整成功后立即看到新的角色名称和权限身份；
5. 在最后一名店铺管理员保护触发时看到明确的业务错误，不产生部分更新。

### 3.2 用户与权限

本需求的调用者不是按角色代码硬编码，而是按店铺权限判断：

| 调用者 | 是否允许 | 说明 |
| --- | --- | --- |
| 当前店铺中拥有 `shop:member:manage` 的 `ACTIVE` 成员 | 允许 | 可查看本店角色、查看本店成员并调整成员角色 |
| 当前店铺中的其他角色成员 | 不允许 | 即使能查看商品或订单，也不能管理员工身份 |
| 不是该店铺成员的平台账号 | 不允许 | 不能通过商家端接口跨店铺读取数据 |
| 拥有 `platform:rbac:manage` 的平台账号 | 不使用商家端接口 | 仍使用 `/api/platform/rbac/**` 管理角色定义 |

`SHOP_ADMIN` 默认拥有 `shop:member:manage`，但自定义角色也可以由平台管理员授予该权限。因此接口鉴权必须检查权限，而不能只检查 `roleCode = SHOP_ADMIN`。

### 3.3 员工管理页面流程

1. 页面加载时请求成员列表和可分配角色列表。
2. 成员列表的 `role` 字段用于展示员工当前身份。
3. 用户点击“调整身份”后，前端使用角色列表生成下拉选项，展示角色名称和说明，提交角色 ID，不提交角色名称或角色代码作为写入依据。
4. 后端重新校验目标角色当前仍为 `SHOP/ACTIVE`，不能信任前端缓存的角色列表。
5. 成功后使用接口返回的 `ShopMemberView` 更新该员工行，不要求前端重新拼装角色对象。
6. 如果返回 `LAST_SHOP_ADMIN_REQUIRED`，前端提示必须保留至少一名可用的店铺管理员，并保留当前员工原角色。

### 3.4 角色列表展示要求

角色列表至少展示：

- 角色名称 `roleName`；
- 角色代码 `roleCode`，可作为辅助信息；
- 角色说明 `description`，为空时展示空值占位；
- 角色 ID 仅作为提交值，不要求直接展示给用户；
- 角色状态固定为 `ACTIVE`，不在商家端展示停用角色。

列表按 `roleCode ASC, id ASC` 稳定排序，便于不同页面和不同请求得到一致的选项顺序。接口返回分页结构；前端如果需要一次加载完整下拉选项，应按 `totalPages` 继续请求，不能假设第一页一定包含全部角色。

### 3.5 范围外事项

本需求不包含以下能力：

- 商家创建或删除角色；
- 商家修改角色名称、说明或权限；
- 给员工分配平台角色；
- 修改员工平台账号状态、密码或登录会话；
- 跨店铺查看或调整员工；
- 为单个店铺创建独有角色；
- 批量导入、批量修改员工角色。

## 4. 业务规则

### 4.1 角色查询规则

1. 请求必须带有效登录 Token。
2. `shopId` 必须对应存在的店铺。
3. 当前用户必须是该店铺的 `ACTIVE` 成员。
4. 当前用户的店铺角色和关联权限必须为 `ACTIVE`，并且拥有 `shop:member:manage`。
5. 查询条件固定为 `sys_role.scope_type = 'SHOP'` 和 `sys_role.status = 'ACTIVE'`。
6. 角色列表不按当前店铺已经使用过的角色过滤；没有成员使用的有效角色也应返回，以便管理员给员工切换到该角色。
7. 角色是否拥有具体业务权限不在本接口中重新计算；分配接口仍以角色作用域和状态为硬校验，权限生效由现有店铺权限查询逻辑决定。

### 4.2 员工角色调整规则

1. 目标成员必须属于路径中的 `shopId`，且成员关系存在。
2. `roleId` 必须是正整数形式的字符串。
3. 目标角色必须存在、作用域为 `SHOP` 且状态为 `ACTIVE`。
4. 角色修改是单成员、单店铺范围的全量替换，不影响该用户在其他店铺的角色关系。
5. 如果目标角色与当前角色相同，接口返回当前成员视图，视为成功，不产生无意义的业务变化。
6. 如果目标成员当前为 `ACTIVE`，且原角色拥有 `shop:member:manage`，新角色不再拥有该权限，则必须检查店铺是否仍有其他 `ACTIVE` 成员拥有该权限。
7. 移除最后一名可用成员管理员时返回 `LAST_SHOP_ADMIN_REQUIRED`，不得修改角色。
8. 角色修改成功后，用户在该店铺的权限查询应使用新角色；现有实现直接查询数据库，不要求额外清理店铺权限缓存。若后续引入缓存，必须在同一业务提交后失效相关缓存。
9. 该接口不因为目标用户是平台管理员而授予平台范围权限，也不修改 `sys_user_role`。

### 4.3 并发与事务

- 角色调整必须在事务中执行。
- 读取目标成员时应使用店铺和用户复合条件，并在写操作期间锁定成员记录，避免角色变更与状态变更相互覆盖。
- 最后管理员检查和角色更新必须属于同一事务。
- 并发请求中只有满足最后管理员保护的请求可以成功；失败请求不得写入新角色。

## 5. API 需求

未重复说明的响应包装、分页、ID、时间、认证错误和 HTTP 状态码继承[统一 API 契约](../api/common-contract.md)。现有成员接口定义见[治理与售后接口分册](../api/phase-2-api.md)中的“店铺成员”章节。

### 5.1 接口总览

| 方法 | 路径 | 鉴权 | 成功 `data` | 说明 |
| --- | --- | --- | --- | --- |
| `GET` | `/api/shops/{shopId}/members/roles` | `SHOP(shop:member:manage)` | `PageView<RoleView>` | 查询本店可分配的有效店铺角色 |
| `GET` | `/api/shops/{shopId}/members` | `SHOP(shop:member:manage)` | `PageView<ShopMemberView>` | 查询本店成员，现有接口保持不变 |
| `PUT` | `/api/shops/{shopId}/members/{userId}/role` | `SHOP(shop:member:manage)` | `ShopMemberView` | 修改成员店铺角色，现有接口保持不变 |

新增接口只读角色字典，不替换或改变现有成员列表和角色修改接口。

### 5.2 查询可分配角色

#### 请求

```http
GET /api/shops/{shopId}/members/roles?keyword=订单&page=1&pageSize=20
```

路径参数：

| 参数 | 类型 | 必填 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `shopId` | `long` | 是 | `1..Long.MAX_VALUE` | 店铺 ID |

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 约束 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `keyword` | `string` | 否 | 无 | 去首尾空白，建议最长 64 字符 | 同时匹配 `roleCode` 和 `roleName`；空白按未传处理 |
| `page` | `long` | 否 | `1` | `>= 1` | 页码 |
| `pageSize` | `long` | 否 | `20` | `1..100` | 每页数量 |

接口不接收 `status` 和 `scopeType` 查询参数。服务端始终只返回 `SHOP/ACTIVE` 角色，避免客户端误请求平台角色或停用角色。

#### 鉴权与数据范围

Service 层必须执行等价于以下校验：

```java
shopAccess.require(shopId, "shop:member:manage");
```

查询角色时不需要把角色限制为当前店铺已经使用过的角色，因为 `sys_role` 中的 `SHOP` 角色是全平台共享模板。店铺访问校验失败时沿用统一店铺资源错误语义，不泄露其他店铺是否存在。

#### 成功响应

HTTP 状态码为 `200 OK`，`data` 使用公共分页结构，单条记录复用现有 `RoleView`：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "items": [
      {
        "id": "2003",
        "roleCode": "SHOP_ORDER_OPERATOR",
        "roleName": "店铺订单客服",
        "scopeType": "SHOP",
        "description": "管理本店订单和售后",
        "status": "ACTIVE",
        "createdAt": "2026-07-20T10:00:00.000+08:00",
        "updatedAt": "2026-07-20T10:00:00.000+08:00"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "totalPages": 1
  },
  "requestId": "01J3R84M3YJHT8K8MEH3B7NQ5E",
  "timestamp": "2026-08-11T10:00:00.000+08:00"
}
```

接口不得直接返回 `SysRole` 实体，不返回角色权限明细。若页面需要展示权限说明，应另行设计只读权限摘要接口，不能让商家调用平台 RBAC 权限接口。

#### 查询实现要求

角色查询应在数据库分页层完成，逻辑等价于：

```sql
SELECT *
FROM sys_role
WHERE scope_type = 'SHOP'
  AND status = 'ACTIVE'
  AND (:keyword IS NULL
       OR role_code LIKE CONCAT('%', :keyword, '%')
       OR role_name LIKE CONCAT('%', :keyword, '%'))
ORDER BY role_code ASC, id ASC;
```

实际实现应继续使用项目的 MyBatis 参数绑定和分页 API，不拼接用户输入。`total` 必须对应所有筛选条件，不能查询后在 Java 内存中二次过滤。

### 5.3 查询本店成员

现有接口保持不变：

```http
GET /api/shops/{shopId}/members?keyword=客服&roleId=2003&status=ACTIVE&page=1&pageSize=20
```

该接口继续返回 `PageView<ShopMemberView>`，其中 `ShopMemberView.role` 是员工当前角色。前端应使用该字段显示当前身份，并使用 `/members/roles` 接口返回的角色 ID 作为调整候选值。

### 5.4 修改成员角色

现有接口保持不变：

```http
PUT /api/shops/{shopId}/members/{userId}/role
Content-Type: application/json
satoken: <token>
```

请求体：

```json
{
  "roleId": "2003"
}
```

请求字段：

| 字段 | 类型 | 必填 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `roleId` | `string` | 是 | 非空、正整数 ID | 目标 `SHOP/ACTIVE` 角色 ID |

成功返回 `200 OK` 和完整的 `ShopMemberView`。响应中的 `role` 必须是修改后的角色，前端不得根据请求中的角色 ID 自行拼装展示数据。

### 5.5 角色调整错误

除公共错误码外，接口至少支持以下业务错误：

| HTTP | 错误码 | 触发条件 |
| ---: | --- | --- |
| `400` | `BAD_REQUEST` | 查询参数类型错误或分页参数越界 |
| `400` | `VALIDATION_FAILED` | 修改角色时 `roleId` 为空或格式错误 |
| `400` | `SHOP_ROLE_REQUIRED` | 目标角色不存在、不是 `SHOP` 作用域或已停用 |
| `401` | `AUTH_NOT_LOGGED_IN` 等认证错误 | 未提交有效 Token、Token 过期或 Token 已被替换、踢下线 |
| `403` | `AUTH_ACCOUNT_DISABLED` / `AUTH_ACCOUNT_LOCKED` | 当前登录账号不可用 |
| `404` | `RESOURCE_NOT_FOUND` | 店铺不存在、调用者不是本店有效成员或没有本店成员管理权限；按统一契约隐藏越权资源 |
| `404` | `SHOP_MEMBER_NOT_FOUND` | 目标用户不是该店铺成员 |
| `409` | `LAST_SHOP_ADMIN_REQUIRED` | 修改会移除最后一名有效成员管理员 |

同一角色重复提交时返回成功，不应返回冲突。角色在读取列表后被平台管理员停用时，修改接口必须重新校验并返回 `SHOP_ROLE_REQUIRED`（当前实现为 HTTP `400`），不能因为前端仍持有旧列表而允许分配停用角色。

## 6. DTO 与实现边界

### 6.1 推荐 DTO

第一版可以直接复用现有 `RoleView` 和 `ShopMemberView`，新增 Service 方法：

```java
PageView<RoleView> roles(
        long shopId, String keyword, long page, long pageSize);
```

Controller 复用现有店铺成员资源入口；其类级路径已经是
`/api/shops/{shopId}/members`，方法映射如下：

```java
@GetMapping("/roles")
// 返回 PageView<RoleView>
```

因此完整路径为 `/api/shops/{shopId}/members/roles`，与现有
`shop:member:manage` 权限资源 `/api/shops/*/members/**` 保持一致。

如果后续需要减少角色列表的响应字段，再新增专用 `ShopRoleOptionView`；在此之前不要复制一份字段相同但语义不清的角色 DTO。

### 6.2 权限与现有服务复用

- 角色列表调用 `ShopAccessService.require(shopId, "shop:member:manage")`；
- 成员查询和角色修改继续调用现有 `ShopMemberService`；
- 角色有效性继续由 `requireShopRole(...)` 统一校验；
- 最后一名管理员保护继续使用 `roleHasActivePermission(..., "shop:member:manage")` 和现有统计逻辑；
- 不修改 `MarketStpInterface` 的平台权限读取逻辑；
- 不把 `platform:rbac:manage` 添加到商家角色，也不让商家端接口调用平台 RBAC Service。

### 6.3 数据库与迁移

本需求不需要新增表或修改现有表结构。角色列表直接读取 `sys_role`，成员关系继续读取 `shop_user`。如果后续增加新的店铺角色，应通过增量 SQL 迁移写入 `sys_role` 和 `sys_role_permission`，不得由商家端接口动态创建角色。

## 7. 安全、审计与可观测性

- 角色列表和成员列表都必须在 Service 层鉴权，不能只依赖前端隐藏菜单或 Controller 路径。
- SQL 查询必须带 `scope_type = 'SHOP'`；不能因为传入某个平台角色 ID 而返回或分配平台角色。
- 角色修改操作日志应记录操作者 ID、店铺 ID、目标用户 ID、原角色 ID、新角色 ID、结果和失败原因。若当前项目尚未统一记录该类日志，至少保留应用审计日志，不得记录 Token 或密码。
- 角色查询日志不得记录完整敏感用户信息；角色字典本身不包含手机号、邮箱或登录凭证。
- 角色被平台停用后，已分配该角色的成员应按现有权限查询逻辑失去对应有效权限；本接口不负责批量迁移已有成员。

## 8. 验收标准

### 8.1 角色查询

- 有 `shop:member:manage` 的本店有效成员可以查询 `GET /api/shops/{shopId}/members/roles`。
- 返回结果只包含 `scopeType = SHOP` 且 `status = ACTIVE` 的角色。
- 角色按 `roleCode ASC, id ASC` 稳定排序，支持关键词和公共分页参数。
- 不属于该店铺的用户，即使拥有其他店铺成员权限，也不能查询该店铺角色。
- 只有平台 RBAC 权限而不是店铺成员的账号，不能通过该商家接口跨店铺读取角色。

### 8.2 员工身份调整

- 前端从角色列表取得 `roleId` 后，可以成功调用现有角色修改接口。
- 成功后响应返回新的 `ShopMemberView.role`，数据库中的 `shop_user.role_id` 已更新。
- 提交平台角色 ID、停用的店铺角色 ID 或不存在的角色 ID 时，返回 `SHOP_ROLE_REQUIRED`，数据库不变。
- 修改最后一名有效成员管理员的角色时，返回 `LAST_SHOP_ADMIN_REQUIRED`，数据库不变。
- 重复提交当前角色返回成功，不产生额外角色变更。
- 角色调整不修改 `sys_user_role`，不影响用户在其他店铺的成员关系。

### 8.3 兼容性

- 现有 `/api/shops/{shopId}/members`、`POST /api/shops/{shopId}/members` 和 `PUT /api/shops/{shopId}/members/{userId}/role` 的路径、请求字段、响应字段和错误语义保持兼容。
- 新增接口不改变 `/api/platform/rbac/roles` 的平台权限要求。
- 不新增前端硬编码角色清单作为正式数据源；角色列表必须来自服务端接口。

## 9. 测试要求

至少覆盖以下测试场景：

1. 店铺管理员查询角色列表，验证只返回 `SHOP/ACTIVE` 角色、排序和分页。
2. 无 `shop:member:manage` 的店铺成员访问角色列表，返回无权限错误。
3. 其他店铺成员访问目标店铺角色列表，返回店铺资源错误。
4. 使用角色列表返回的 ID 修改员工角色，验证响应和数据库角色一致。
5. 使用平台角色 ID、停用角色 ID、不存在角色 ID 修改，验证失败且数据库不变。
6. 修改最后一名成员管理员的角色，验证 `LAST_SHOP_ADMIN_REQUIRED` 和事务回滚。
7. 并发修改成员角色和状态，验证复合条件、行锁和最后管理员保护不被绕过。
8. 平台新增一个有效 `SHOP` 角色后，商家角色列表可以看到该角色；平台停用后列表不再返回，修改接口也拒绝分配。

## 10. 版本与后续扩展

本文件描述的是在现有全局 `SHOP` 角色模型上的第一版能力。未来如果产品需要“每个店铺拥有独立角色集合”，必须先新增店铺与角色的关联表和授权模型，再调整角色列表过滤规则；不能在当前 `sys_role` 全局模型上通过前端隐藏选项模拟店铺独立角色。
