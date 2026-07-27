# 时光商城统一 API 契约

## 1. 适用范围

本文是一期和二期全部 HTTP 接口的公共契约。阶段接口文档只描述业务差异；未重复说明的内容一律继承本文。

所有命名 Request/View 的完整字段集合见 [DTO 字段目录](dto-catalog.md)。

## 2. 基础协议

| 项目 | 约定 |
| --- | --- |
| 协议 | 开发环境可使用 HTTP，生产环境必须 HTTPS |
| 根路径 | `/api`，当前不增加 `/v1`，与现有 RBAC 资源路径一致 |
| 媒体类型 | 请求和响应均为 `application/json; charset=UTF-8` |
| JSON 字段 | `camelCase` |
| 字符编码 | UTF-8 |
| CORS | 开发/测试按配置允许明确前端源，允许 `satoken`、`Content-Type`、`X-Request-Id`、`Idempotency-Key`；`allowCredentials=false`，禁止生产环境 `*` |
| 业务时区 | `Asia/Shanghai` |
| API 时间 | ISO 8601 且包含偏移，例如 `2026-07-26T18:30:15.123+08:00` |
| 数据库时间 | `DATETIME(3)`，应用在业务时区读写 |
| 空值 | 响应模型中固定字段允许返回 `null`；仅 PATCH 中字段缺失表示“不修改”、显式 `null` 表示“清空可空字段”；POST/PUT 按各 DTO 的必填规则处理 |

## 3. 基础类型

### 3.1 ID

数据库主键为 `BIGINT UNSIGNED`。为避免 JavaScript `Number` 超过安全整数范围，所有 ID 在 JSON 中必须使用十进制字符串：

```json
{
  "id": "9007199254740993",
  "shopId": "12"
}
```

路径参数仍以十进制数字形式出现，例如 `/api/orders/9007199254740993`。前端不得使用 `Number(id)`，应全程保存为字符串。

### 3.2 金额

所有金额以“元”为单位，在 JSON 中使用两位小数字符串，不使用浮点数：

```json
{
  "salePrice": "199.00",
  "balance": "1000.50"
}
```

请求金额格式为 `^(0|[1-9][0-9]{0,15})\.[0-9]{2}$`。业务上要求大于 0 的字段不能提交 `"0.00"`。后端必须使用 `BigDecimal`，比较和计算前统一保留两位小数，不允许从 `double` 构造。

### 3.3 数量和版本

数量、页码、页大小、排序号和版本号使用 JSON 整数。购物数量范围为 `1..999`；库存操作数量范围为 `1..2147483647`，但还要受当前库存约束。

### 3.4 枚举

枚举使用数据库和 `MarketEnums` 中的稳定英文大写代码。前端展示中文，但提交和判断只使用代码，不使用中文文案。

## 4. 请求头

| Header | 必填条件 | 说明 |
| --- | --- | --- |
| `satoken` | 受保护接口必填 | Sa-Token 登录凭证；当前配置不读 Cookie、不使用 Bearer 前缀 |
| `Content-Type` | 有 JSON 请求体时必填 | `application/json` |
| `Accept` | 建议 | `application/json` |
| `X-Request-Id` | 可选 | 客户端链路 ID，1..64 个 ASCII 字符；未传时后端生成 |
| `Idempotency-Key` | 标记为幂等写接口时必填 | 防止重复下单、充值、支付、库存和退款操作 |

前端在刷新页面后仍要保存登录 token；收到 `AUTH_NOT_LOGGED_IN`、`AUTH_TOKEN_EXPIRED` 或 `AUTH_TOKEN_REPLACED` 时清理本地会话并跳转登录页。

## 5. 统一响应

### 5.1 成功响应

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": "1001"
  },
  "requestId": "01J3R84M3YJHT8K8MEH3B7NQ5E",
  "timestamp": "2026-07-26T18:30:15.123+08:00"
}
```

- 查询、修改和动作接口通常返回 `200 OK`。
- 创建资源返回 `201 Created`，`data` 中必须包含新资源 ID 和业务编号。
- 删除接口也返回 `200 OK`，`data` 为 `null`，不使用无响应体的 `204`。
- `message` 只用于展示或调试，业务判断必须使用 `code`。

### 5.2 分页响应

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "items": [],
    "page": 1,
    "pageSize": 20,
    "total": 0,
    "totalPages": 0
  },
  "requestId": "01J3R84M3YJHT8K8MEH3B7NQ5E",
  "timestamp": "2026-07-26T18:30:15.123+08:00"
}
```

分页公共参数：

| 参数 | 默认 | 约束 |
| --- | ---: | --- |
| `page` | 1 | 最小 1 |
| `pageSize` | 20 | `1..100`，后端硬上限与 MyBatis-Plus 配置一致 |
| `sort` | 各接口默认值 | 格式 `field,asc` 或 `field,desc`，只接受接口白名单字段 |

页码超过最后一页返回空 `items`，不自动改为最后一页。

### 5.3 错误响应

```json
{
  "code": "VALIDATION_FAILED",
  "message": "请求参数校验失败",
  "details": [
    {
      "field": "quantity",
      "reason": "must be between 1 and 999"
    }
  ],
  "requestId": "01J3R84M3YJHT8K8MEH3B7NQ5E",
  "timestamp": "2026-07-26T18:30:15.123+08:00"
}
```

`details` 可省略。生产环境不得返回堆栈、SQL、表名、密码摘要、Token、Redis Key 或内部类名。请求 JSON 出现 DTO 未定义字段时必须返回 `400 BAD_REQUEST`，不得静默忽略。

## 6. HTTP 状态与公共错误码

| HTTP | 错误码 | 含义 |
| ---: | --- | --- |
| 400 | `BAD_REQUEST` | JSON 无法解析、参数类型错误或查询参数格式错误 |
| 400 | `VALIDATION_FAILED` | Bean Validation 或业务字段格式校验失败 |
| 401 | `AUTH_NOT_LOGGED_IN` | 未提交有效 Token |
| 401 | `AUTH_TOKEN_EXPIRED` | Token 已过期 |
| 401 | `AUTH_TOKEN_REPLACED` | Token 已被顶下线或替换 |
| 401 | `AUTH_TOKEN_KICKED_OUT` | 管理员已踢下线 |
| 403 | `AUTH_ACCOUNT_DISABLED` | 用户不是 `ACTIVE` 或已软删除 |
| 403 | `AUTH_PERMISSION_DENIED` | 缺少平台或普通用户权限 |
| 403 | `SHOP_ACCESS_DENIED` | 不是目标店铺有效成员或缺少店铺权限 |
| 404 | `RESOURCE_NOT_FOUND` | 资源不存在、已软删除或调用方无权知道其存在 |
| 409 | `RESOURCE_CONFLICT` | 唯一键冲突或资源关系冲突 |
| 409 | `STATE_CONFLICT` | 当前状态不允许执行动作 |
| 409 | `VERSION_CONFLICT` | 乐观锁版本过期 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 相同幂等键被用于不同请求体 |
| 422 | `BUSINESS_RULE_VIOLATION` | 请求格式正确但违反业务规则 |
| 429 | `TOO_MANY_REQUESTS` | 触发限流 |
| 500 | `INTERNAL_ERROR` | 未预期服务端错误 |
| 503 | `DEPENDENCY_UNAVAILABLE` | MySQL、Redis 等必要依赖暂时不可用 |

安全规则：对店铺越权访问和其他用户资源访问，优先返回 `RESOURCE_NOT_FOUND`，避免泄露资源是否存在；只有已确认资源公开但动作无权限时返回 `403`。

## 7. 鉴权与数据范围

### 7.1 接口级别

| 级别 | 规则 |
| --- | --- |
| `PUBLIC` | 不要求 Token；若提交 Token，也不改变公开数据范围 |
| `LOGIN` | 只要求有效登录和 `sys_user.status = ACTIVE` |
| `PERMISSION` | 登录后还要拥有指定平台权限代码 |
| `SHOP_PERMISSION` | 登录后拥有指定店铺权限，且是路径 `shopId` 的有效成员 |
| `OWNER` | 资源必须属于当前登录用户 |

### 7.2 店铺范围

所有 `/api/shops/{shopId}/...` 管理接口必须同时校验：

1. 当前用户有效。
2. 店铺存在；具体动作满足店铺状态能力矩阵。
3. `shop_user(shop_id, user_id)` 存在且为 `ACTIVE`。
4. 店铺角色和权限均为 `ACTIVE`，权限作用域为 `SHOP`。
5. SQL 查询或更新条件包含同一个 `shop_id`，不能仅在 Controller 中检查后再按资源 ID 裸查。

`SUPER_ADMIN` 不自动获得店铺数据范围。

店铺状态能力矩阵：

| 店铺状态 | 成员/商品草稿/库存 | 提交审核与上架 | 已支付订单履约与售后 | 公开购买 |
| --- | --- | --- | --- | --- |
| `PENDING` | 允许 | 可提交审核，不可上架 | 无新增订单；历史业务允许处理 | 否 |
| `ACTIVE` | 允许 | 允许 | 允许 | 是 |
| `SUSPENDED` | 允许，用于整改 | 不可提交审核、不可上架 | 允许，必须完成已有义务 | 否 |
| `CLOSED` | 只读历史 | 不允许 | 原则上关闭前已无未完结业务；只读 | 否 |

平台将店铺改为 `CLOSED` 前，必须确认没有 `PENDING_PAYMENT`、`PENDING_SHIPMENT`、`PENDING_RECEIPT` 子订单和 `PENDING`、`WAITING_RETURN`、`REFUNDING` 售后；否则返回 `SHOP_HAS_ACTIVE_BUSINESS`。暂停店铺不取消已有订单。

### 7.3 前端权限

`GET /api/auth/me` 返回平台权限和店铺成员上下文，前端据此控制菜单和按钮。前端控制只改善体验，不能替代后端鉴权。

## 8. 幂等规则

以下操作必须提交 `Idempotency-Key`：创建交易、创建/执行支付、模拟充值、库存入库/调整、创建售后、提交退货物流、执行或重试退款。

规则如下：

1. Key 长度 `1..64`，只允许字母、数字、`-`、`_`、`.`。
2. 幂等范围为“当前用户 + HTTP 方法 + 规范化路径 + Key”。
3. 后端保存请求体 SHA-256 和最终响应，默认保留 24 小时。
4. 相同 Key、相同请求返回第一次结果及原 HTTP 状态，不重复执行业务。
5. 相同 Key、不同请求返回 `409 IDEMPOTENCY_KEY_REUSED`。
6. 数据库业务唯一键仍是最终保护；Redis 幂等记录不是替代品。
7. 前端一次用户动作只生成一个 Key；网络重试沿用该 Key，用户主动再次操作生成新 Key。

推荐值为 UUID，例如 `e330b640-2f69-44ef-b4d8-7ef00b9267a8`。

## 9. 并发与版本

### 9.1 客户端版本字段

直接编辑带 `version` 的聚合资源时，请求体必须回传最近一次查询获得的 `version`。成功后响应返回递增后的版本；过期返回 `VERSION_CONFLICT`，前端应刷新数据，不自动覆盖。

适用资源：SKU 价格/启停、钱包人工调整、售后审核动作中的版本校验。库存、订单、交易和支付动作虽然内部使用版本或行锁，但客户端不提交版本，后端以状态条件和幂等键处理。

SPU 使用 `contentVersion` 表示受审核内容版本。内容更新请求必须提交当前 `contentVersion`，冲突同样返回 `VERSION_CONFLICT`。

### 9.2 状态动作

审核、上架、支付、取消、发货、收货、售后审核和退款必须使用专门动作接口，禁止前端直接 PATCH 状态字段。

## 10. 查询、排序与筛选

- 字符串搜索默认去除首尾空白；空字符串视为未传。
- `keyword` 使用商品名、编号或订单号等接口指定字段的模糊匹配，不承诺搜索引擎能力。
- 多值枚举使用重复参数，如 `status=PAID&status=CANCELLED`，不使用逗号拼接。
- 时间区间使用 `createdFrom`（含）和 `createdTo`（不含）。
- 所有列表必须有稳定的次级排序，默认 `createdAt desc, id desc`，避免翻页时顺序不确定。

## 11. 通用 DTO

### 11.1 `UserSummary`

```json
{
  "id": "101",
  "username": "alice",
  "nickname": "Alice",
  "avatarUrl": null,
  "status": "ACTIVE"
}
```

### 11.2 `ShopSummary`

```json
{
  "id": "201",
  "shopNo": "SHOP202607260001",
  "shopName": "时光数码店",
  "logoUrl": "https://static.example.com/shop/201.png",
  "status": "ACTIVE"
}
```

### 11.3 `AddressView`

```json
{
  "id": "301",
  "recipientName": "张三",
  "recipientPhone": "13800000000",
  "provinceName": "上海市",
  "cityName": "上海市",
  "districtName": "杨浦区",
  "detailAddress": "延吉中路 100 号",
  "isDefault": true,
  "createdAt": "2026-07-26T18:30:15.123+08:00",
  "updatedAt": "2026-07-26T18:30:15.123+08:00"
}
```

### 11.4 `StockView`

公开商品接口只返回 `inStock` 和最多可购买数量 `availableQuantity`。店铺库存接口额外返回锁定数量和版本：

```json
{
  "skuId": "501",
  "availableQuantity": 20,
  "lockedQuantity": 3,
  "version": 7,
  "updatedAt": "2026-07-26T18:30:15.123+08:00"
}
```

## 12. 初始业务字典

### 12.1 售后原因 `reasonCode`

| 代码 | 展示文案 |
| --- | --- |
| `NOT_WANTED` | 不想要了 |
| `WRONG_ITEM` | 商品错发 |
| `DAMAGED` | 商品破损 |
| `QUALITY_PROBLEM` | 质量问题 |
| `MISSING_PARTS` | 缺少配件 |
| `OTHER` | 其他 |

### 12.2 库存流水 `businessType`

`MANUAL_INBOUND`、`TRADE_ORDER`、`ORDER_SHIPMENT`、`AFTER_SALE`、`MANUAL_ADJUSTMENT`。

### 12.3 钱包流水 `businessType`

`SIMULATED_RECHARGE`、`TRADE_PAYMENT`、`AFTER_SALE_REFUND`、`MANUAL_ADJUSTMENT`。

字典由后端枚举或集中字典维护，前端从本文固定代码开发。新增代码属于契约变更，不能由服务实现临时返回任意字符串。

## 13. URL 和内容安全

- 图片 URL 最大 1024 字符，只允许 `https`；本地开发可额外允许配置的 `http://localhost`。
- `detailHtml` 由后端白名单清洗，禁止 `script`、内联事件、`iframe`、危险 URL 和任意样式注入。
- 密码永不出现在响应、日志或错误详情中。
- 手机和地址属于本人及履约店铺所需数据，公开商品和平台无关列表不得返回。

## 14. 兼容与变更

以下变更视为破坏性变更：删除字段、改变字段类型、改变枚举语义、收紧原有效输入、修改状态机、修改金额公式、改变错误码、将可空改为必填、改变权限或数据范围。

破坏性变更必须先更新文档和 Mock，前后端共同确认后实施。新增可选响应字段通常可兼容，但前端必须忽略未知字段，后端不得依赖前端回传整个响应对象。
