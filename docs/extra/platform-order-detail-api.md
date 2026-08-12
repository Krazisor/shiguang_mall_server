# 平台订单详情需求分析与 API 接口约定

## 1. 文档目的

本文定义平台后台“订单管理”页面的订单详情需求和 HTTP API 契约，解决平台订单列表可以跨店铺查询订单，但点击“查看详情”后缺少平台专用详情接口的问题。

本文中的详情接口已经按本契约实现。前端不得把列表摘要拼装成详情，也不得调用买家端或店铺端详情接口绕过数据范围校验。

文档状态：

| 项目 | 状态 |
| --- | --- |
| 平台订单列表 | 已实现，本文仅说明依赖关系 |
| 平台订单详情 | 已实现，以本文作为接口约定 |
| 数据库表结构 | 已具备，不需要新增字段或迁移 |
| 平台权限 | 复用 `platform:operation:read`，不新增权限 |

## 2. 背景与现状分析

### 2.1 当前已有能力

平台运营当前可以使用以下接口分页查询全平台子订单：

```http
GET /api/platform/operations/orders
```

该接口需要 `platform:operation:read`，返回 `PageView<OperationOrderView>`，支持按 `orderNo`、`shopId`、`userId`、`orderStatus` 和 `paymentStatus` 筛选。列表响应包含订单号、交易号、店铺、买家摘要、订单状态、支付状态、应付金额、退款金额、商品摘要和下单时间。

现有平台业务追踪接口：

```http
GET /api/platform/operations/business/{businessType}/{businessNo}
```

只返回交易、订单、支付、售后、库存流水和钱包流水的关联摘要，用于定位业务链路，不返回订单商品、物流和完整时间信息，因此不能替代订单详情接口。

### 2.2 现有详情接口的权限边界

项目已经存在以下订单详情接口：

| 入口 | 鉴权 | 数据范围 |
| --- | --- | --- |
| `GET /api/orders/{orderId}` | `OWNER` | 仅当前买家自己的订单 |
| `GET /api/shops/{shopId}/orders/{orderId}` | `SHOP(shop:order:read)` | 仅当前账号作为有效成员可访问的指定店铺订单 |

平台账号即使拥有 `platform:operation:read`，也不自动成为买家本人或目标店铺成员，因此平台后台不能复用以上路径。不能修改 `ShopAccessService` 或买家归属校验，让平台权限全局绕过原有数据范围；这会同时扩大商品、库存、订单和售后等店铺接口的授权范围。

现有 `OrderDetailView` 还包含完整收货地址和收件手机号。平台订单管理截图不需要这些信息，平台详情直接复用该 DTO 会产生不必要的敏感信息披露。因此必须使用独立的平台详情路径和平台专用 DTO。

### 2.3 数据可用性

截图所需数据已经存在于当前模型中：

| 页面信息 | 数据来源 | 当前平台列表是否返回 |
| --- | --- | --- |
| 订单号、订单状态、支付状态 | `order_info` | 是 |
| 交易号、支付时间、支付到期时间 | `trade_order` | 仅交易号 |
| 商家 | `shop` / 订单店铺快照 | 是 |
| 买家 | `sys_user` | 是，返回脱敏 `UserSummary` |
| 商品金额、运费、应付金额、退款金额 | `order_info` | 仅应付和退款金额 |
| 下单时间 | `order_info.created_at` | 是 |
| 发货时间、物流公司、运单号 | `order_info` | 否 |
| 完成时间、取消时间 | `order_info` | 否 |
| 商品、规格、图片、单价、数量、实付 | `order_item` | 仅商品摘要 |
| 状态历史 | `order_status_history` | 否 |

当前详情弹窗中支付时间、发货时间、完成时间、物流公司和运单号显示 `-`，不能据此判断数据库没有数据。平台列表 DTO 本身不包含这些字段，前端如果只使用列表行数据，只能显示空值占位。

## 3. 产品需求

### 3.1 目标

拥有平台运营只读权限的账号应当能够：

1. 从平台订单列表选择一张子订单并查看详情；
2. 查看该子订单的订单号、交易号、店铺、买家摘要、履约状态和支付状态；
3. 区分商品金额、运费、应付金额和已退款金额；
4. 查看下单、支付、发货、完成或取消等实际存在的时间；
5. 查看物流公司、运单号以及完整商品明细；
6. 查看订单状态历史，以便解释订单当前状态；
7. 在不成为买家本人或店铺成员的前提下，按平台权限跨店铺只读访问；
8. 不通过该接口发货、确认收货、取消订单、退款或修改任何业务状态。

### 3.2 用户与权限

| 调用者 | 是否允许 | 说明 |
| --- | --- | --- |
| 拥有 `platform:operation:read` 的有效平台账号 | 允许 | 可以读取全平台任意存在的子订单详情 |
| 只有 `platform:rbac:manage`、`platform:shop:manage` 等其他平台权限的账号 | 不允许 | 其他平台权限不隐含运营订单读取权限 |
| 只有 `shop:order:read` 的店铺成员 | 不使用本接口 | 继续调用店铺订单详情接口 |
| 普通买家 | 不使用本接口 | 继续调用本人订单详情接口 |
| 未登录、账号停用、锁定或删除的用户 | 不允许 | 沿用统一认证错误语义 |

平台订单详情继续复用现有权限：

```text
platform:operation:read
```

本需求不新增写权限，也不允许 `platform:operation:read` 调用商家发货、买家确认收货或售后处理接口。

### 3.3 页面字段及映射

平台订单详情至少展示以下内容：

| 页面区域 | 展示项 | API 字段 | 空值规则 |
| --- | --- | --- | --- |
| 基础信息 | 订单号 | `orderNo` | 非空 |
| 基础信息 | 交易号 | `tradeNo` | 非空 |
| 基础信息 | 商家 | `shop.shopName` | 非空 |
| 基础信息 | 买家 | `buyer.nickname`，为空时回退 `buyer.username` | 用户摘要非空 |
| 基础信息 | 订单金额 | `payableAmount` | 非空，两位小数字符串 |
| 基础信息 | 订单状态 | 优先使用 `displayStatus` | 非空 |
| 时间信息 | 下单时间 | `createdAt` | 非空 |
| 时间信息 | 支付到期时间 | `payExpireAt` | 非空，前端可按状态决定是否展示 |
| 时间信息 | 支付时间 | `paidAt` | 未支付时为 `null` |
| 时间信息 | 发货时间 | `shipping.shippedAt` | 未发货时 `shipping` 整体为 `null` |
| 时间信息 | 完成时间 | `completedAt` | 未完成时为 `null` |
| 时间信息 | 取消时间 | `cancelledAt` | 未取消时为 `null` |
| 物流信息 | 物流公司 | `shipping.carrierName` | 未发货时不展示或显示 `-` |
| 物流信息 | 运单号 | `shipping.trackingNo` | 未发货时不展示或显示 `-` |
| 商品明细 | 商品、规格、图片 | `items[].productName/skuName/imageUrl` | 图片允许为 `null` |
| 商品明细 | 数量 | `items[].quantity` | 正整数 |
| 商品明细 | 单价 | `items[].unitPrice` | 两位小数字符串 |
| 商品明细 | 实付 | `items[].payableAmount` | 两位小数字符串 |

金额展示必须使用 API 返回的字符串，不允许前端转成 JavaScript 浮点数后重新计算。所有金额单位为元。

### 3.4 金额口径

截图中的“订单金额”容易与商品金额混淆，第一版统一定义如下：

| 字段 | 业务含义 |
| --- | --- |
| `itemAmount` | 子订单全部商品金额合计，不含运费 |
| `freightAmount` | 子订单运费合计 |
| `payableAmount` | 买家针对该子订单应付金额；页面“订单金额”使用此字段 |
| `refundAmount` | 该子订单已成功退款金额合计 |
| `items[].originalAmount` | 该明细优惠前商品金额快照 |
| `items[].freightAmount` | 分摊到该明细的运费 |
| `items[].payableAmount` | 该明细最终应付金额快照，页面“实付”使用此字段 |

例如商品金额为 `899.00`、运费为 `10.00` 时，订单“订单金额”应展示 `909.00`。如果产品希望展示 `899.00`，标签应明确改为“商品金额”，不能继续称为订单应付金额。

### 3.5 状态口径

响应同时返回：

- `orderStatus`：订单履约状态，用于排障和业务规则判断；
- `displayStatus`：面向页面的聚合展示状态；存在活跃售后或待裁决申诉时为 `AFTER_SALE`；
- `paymentStatus`：子订单支付和退款聚合状态。

页面状态标签优先使用 `displayStatus`，不能只根据 `orderStatus` 判断。枚举值由前端映射为中文，不返回中文状态作为业务判断依据。

当前枚举范围：

```text
orderStatus: PENDING_PAYMENT | PENDING_SHIPMENT | PENDING_RECEIPT | COMPLETED | CANCELLED
displayStatus: AFTER_SALE | PENDING_PAYMENT | PENDING_SHIPMENT | PENDING_RECEIPT | COMPLETED | CANCELLED
paymentStatus: UNPAID | PAID | PARTIALLY_REFUNDED | REFUNDED
```

### 3.6 “收货时间”和“完成时间”的语义

当前订单状态机在买家确认收货或系统自动确认收货时，直接将订单更新为 `COMPLETED` 并写入 `order_info.completed_at`。系统没有独立的 `received_at` 字段，也没有“已收货但未完成”的中间订单状态。

因此第一版 API 只返回：

```text
completedAt
```

其含义是“订单进入 `COMPLETED` 的时间”，可能来自买家确认收货，也可能来自自动完成任务。页面应将截图中的“收货时间”和“完成时间”合并为“完成时间（确认收货）”，不得为了保留两行而返回两个值相同、语义重复的字段。

如果未来业务确实需要区分物流签收时间、买家确认收货时间和订单最终关闭时间，应先新增明确的数据来源和状态规则，再扩展独立字段；不能从当前 `completedAt` 猜测物流实际签收时间。

### 3.7 空值和加载规则

1. 弹窗打开后必须按 `orderId` 请求详情，显示加载状态；不得先把列表摘要当作完整详情。
2. `paidAt`、`shipping`、`completedAt` 和 `cancelledAt` 按订单状态允许为 `null`。
3. `shipping = null` 时，前端统一显示未发货占位，不访问 `shipping.carrierName` 等子字段。
4. `items` 和 `history` 固定返回数组；正常订单的 `items` 不应为空。
5. 请求失败时弹窗显示统一错误状态，不保留上一次订单的详情数据。
6. 同一弹窗快速切换订单时，前端应取消旧请求或校验当前 `orderId`，避免较慢的旧响应覆盖新订单。

### 3.8 范围外事项

本需求不包含：

- 平台替商家发货或修改物流；
- 平台替买家确认收货或取消订单；
- 发起退款、处理售后或裁决售后申诉；
- 查看完整收件人姓名、手机号和收货地址；
- 查看买家钱包余额、支付密码或完整支付流水；
- 查询第三方物流实时轨迹；
- 修改现有买家端、店铺端详情接口的权限和响应；
- 新增订单状态或数据库时间字段。

## 4. HTTP API 契约

未重复说明的响应包装、ID、金额、时间、认证和错误规则继承[统一 API 契约](../api/common-contract.md)。所有 ID 在 JSON 中使用十进制字符串，时间使用带偏移的 ISO 8601 格式，金额使用两位小数字符串。

### 4.1 接口总览

| 方法 | 路径 | 鉴权 | 成功 `data` | 状态 |
| --- | --- | --- | --- | --- |
| `GET` | `/api/platform/operations/orders` | `platform:operation:read` | `PageView<OperationOrderView>` | 已有列表接口，保持不变 |
| `GET` | `/api/platform/operations/orders/{orderId}` | `platform:operation:read` | `OperationOrderDetailView` | 已实现 |

详情接口属于只读查询，不接收请求体，不要求 `Idempotency-Key`，不返回任何可执行订单动作。

### 4.2 请求

```http
GET /api/platform/operations/orders/711
Accept: application/json
satoken: <token>
```

路径参数：

| 参数 | 类型 | 必填 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `orderId` | `long` | 是 | `1..Long.MAX_VALUE` | 子订单 `order_info.id`，来自平台订单列表的 `items[].id` |

接口不使用订单号作为路径参数。订单号仍可通过列表接口的 `orderNo` 精确查询，前端取得稳定的内部 ID 后再调用详情，保持与买家端和店铺端详情资源标识一致。

### 4.3 鉴权与数据范围

Controller 入口沿用现有平台运营接口的显式权限检查：

```java
currentUser.requirePermission("platform:operation:read");
```

鉴权通过后可以读取任意店铺的目标订单，不再调用：

```java
shopAccess.require(shopId, "shop:order:read");
```

该规则只适用于 `/api/platform/operations/orders/{orderId}`。不得把平台权限下沉为 `ShopAccessService` 的通用绕过条件，也不得改变 `/api/orders/{orderId}` 的买家归属校验。

### 4.4 `OperationOrderDetailView`

字段定义：

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `id` | `Id` | 否 | 子订单 ID |
| `orderNo` | `string` | 否 | 子订单号 |
| `tradeId` | `Id` | 否 | 父交易 ID |
| `tradeNo` | `string` | 否 | 父交易号 |
| `shop` | `ShopSummary` | 否 | 店铺摘要 |
| `buyer` | `UserSummary` | 否 | 买家脱敏摘要，不含手机号 |
| `orderStatus` | `OrderStatus` | 否 | 订单履约状态 |
| `displayStatus` | `OrderDisplayStatus` | 否 | 页面聚合展示状态 |
| `paymentStatus` | `OrderPaymentStatus` | 否 | 支付/退款聚合状态 |
| `itemAmount` | `Money` | 否 | 商品金额合计，不含运费 |
| `freightAmount` | `Money` | 否 | 运费合计 |
| `payableAmount` | `Money` | 否 | 子订单应付金额 |
| `refundAmount` | `Money` | 否 | 已成功退款金额 |
| `createdAt` | `Timestamp` | 否 | 下单时间，即子订单创建时间 |
| `payExpireAt` | `Timestamp` | 否 | 父交易支付截止时间 |
| `paidAt` | `Timestamp` | 是 | 父交易支付成功时间 |
| `completedAt` | `Timestamp` | 是 | 子订单进入 `COMPLETED` 的时间 |
| `cancelledAt` | `Timestamp` | 是 | 子订单取消时间 |
| `shipping` | `ShippingView` | 是 | 发货信息；未发货时整体为 `null` |
| `items` | `OrderItemView[]` | 否 | 完整商品明细，按明细 ID 升序 |
| `history` | `OrderStatusHistoryView[]` | 否 | 状态历史，按时间和 ID 升序 |

响应固定不包含以下字段：

```text
address
recipientName
recipientPhone
buyerRemark
availableActions
```

其中 `availableActions` 不返回，是为了避免把买家或商家的动作提示错误地暴露给只读平台账号。前端也不得根据订单状态在平台详情弹窗中自行生成发货、完成或取消按钮。

### 4.5 复用 DTO

`shop` 复用现有 `ShopSummary`：

```text
id: Id
shopNo: string
shopName: string
logoUrl: string | null
status: ShopStatus
```

`buyer` 复用现有 `UserSummary`：

```text
id: Id
username: string
nickname: string | null
avatarUrl: string | null
status: UserStatus
```

`shipping` 复用现有 `ShippingView`：

```text
carrierCode: string
carrierName: string
trackingNo: string
shippedAt: Timestamp
```

`items` 复用现有 `OrderItemView`：

```text
id: Id
spuId: Id
skuId: Id
spuNo: string
skuNo: string
productName: string
skuName: string
spec: object<string, string>
imageUrl: string | null
unitPrice: Money
quantity: int
originalAmount: Money
freightAmount: Money
payableAmount: Money
refundedQuantity: int
refundedAmount: Money
reservationStatus: ReservationStatus
```

`history` 复用现有 `OrderStatusHistoryView`：

```text
fromStatus: OrderStatus | null
toStatus: OrderStatus
operationType: OrderOperationType
operatorType: OperatorType
remark: string | null
createdAt: Timestamp
```

平台详情可以复用以上不含敏感信息的嵌套 DTO，但不能直接复用包含地址和买家动作的完整 `OrderDetailView`。

### 4.6 成功响应示例

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": "711",
    "orderNo": "OR20260809514978",
    "tradeId": "701",
    "tradeNo": "TRA997C513544CFB532B4CA0EE9BEA1B",
    "shop": {
      "id": "201",
      "shopNo": "SHOP202607260001",
      "shopName": "时光数码店 A",
      "logoUrl": null,
      "status": "ACTIVE"
    },
    "buyer": {
      "id": "101",
      "username": "zkss",
      "nickname": null,
      "avatarUrl": null,
      "status": "ACTIVE"
    },
    "orderStatus": "COMPLETED",
    "displayStatus": "COMPLETED",
    "paymentStatus": "PAID",
    "itemAmount": "899.00",
    "freightAmount": "10.00",
    "payableAmount": "909.00",
    "refundAmount": "0.00",
    "createdAt": "2026-08-09T05:14:29.000+08:00",
    "payExpireAt": "2026-08-09T05:44:29.000+08:00",
    "paidAt": "2026-08-09T05:16:03.000+08:00",
    "completedAt": "2026-08-10T15:20:00.000+08:00",
    "cancelledAt": null,
    "shipping": {
      "carrierCode": "SF",
      "carrierName": "顺丰速运",
      "trackingNo": "SF1234567890",
      "shippedAt": "2026-08-09T09:00:00.000+08:00"
    },
    "items": [
      {
        "id": "721",
        "spuId": "501",
        "skuId": "511",
        "spuNo": "SPU_DEMO_A3",
        "skuNo": "SKU_DEMO_A3_BLACK",
        "productName": "时光降噪耳机 Pro",
        "skuName": "曜石黑",
        "spec": {"color": "曜石黑"},
        "imageUrl": "https://images.example.com/headphone.png",
        "unitPrice": "899.00",
        "quantity": 1,
        "originalAmount": "899.00",
        "freightAmount": "10.00",
        "payableAmount": "909.00",
        "refundedQuantity": 0,
        "refundedAmount": "0.00",
        "reservationStatus": "DEDUCTED"
      }
    ],
    "history": [
      {
        "fromStatus": null,
        "toStatus": "PENDING_PAYMENT",
        "operationType": "CREATE",
        "operatorType": "USER",
        "remark": null,
        "createdAt": "2026-08-09T05:14:29.000+08:00"
      },
      {
        "fromStatus": "PENDING_RECEIPT",
        "toStatus": "COMPLETED",
        "operationType": "COMPLETE",
        "operatorType": "USER",
        "remark": null,
        "createdAt": "2026-08-10T15:20:00.000+08:00"
      }
    ]
  },
  "requestId": "01J3R84M3YJHT8K8MEH3B7NQ5E",
  "timestamp": "2026-08-11T10:00:00.000+08:00"
}
```

### 4.7 空值和一致性约束

后端应保证：

1. `orderStatus = PENDING_PAYMENT` 时，`paidAt` 和 `shipping` 通常为 `null`；
2. `orderStatus = PENDING_SHIPMENT` 时，`paidAt` 非空，`shipping` 为 `null`；
3. `orderStatus = PENDING_RECEIPT` 时，`shipping` 非空，`completedAt` 为 `null`；
4. `orderStatus = COMPLETED` 时，`completedAt` 非空；已发货实物订单的 `shipping` 非空；
5. `orderStatus = CANCELLED` 时，`cancelledAt` 非空；
6. `shipping` 非空时，`carrierCode`、`carrierName`、`trackingNo` 和 `shippedAt` 必须全部非空；
7. `items`、`history` 始终返回数组，不返回 `null`；
8. `itemAmount + freightAmount` 与 `payableAmount` 的关系以已冻结订单快照为准，前端不得自行覆盖后端值；
9. `paidAt` 使用父交易 `trade_order.paid_at` 作为成功支付的权威时间，不从任意一次失败或取消的支付尝试推断；
10. `displayStatus` 必须复用买家/商家订单视图的统一聚合计算，不能在平台模块复制一套可能漂移的售后判断。

如果数据库历史数据违反以上状态和时间约束，接口不得伪造当前时间或根据状态猜测时间。应返回实际可用值、记录数据完整性日志，并通过数据治理修复；关键关联记录缺失时按服务端数据完整性错误处理，不能返回不同订单的替代数据。

### 4.8 错误语义

| 场景 | HTTP | 错误码 | 说明 |
| --- | ---: | --- | --- |
| `orderId` 不是合法整数 | `400` | `BAD_REQUEST` | 路径参数无法绑定 |
| `orderId <= 0` | `400` | `BAD_REQUEST` | 不接受非正数 ID |
| 未提交有效 Token | `401` | `AUTH_NOT_LOGGED_IN` 等 | 沿用 Sa-Token 认证错误 |
| 当前账号停用或锁定 | `403` | `AUTH_ACCOUNT_DISABLED` / `AUTH_ACCOUNT_LOCKED` | 由 `CurrentUserService` 处理 |
| 缺少 `platform:operation:read` | `403` | `AUTH_PERMISSION_DENIED` | 不因拥有其他平台权限而放行 |
| 订单不存在 | `404` | `RESOURCE_NOT_FOUND` | 不返回空对象，不回退到按订单号查询 |
| 必要依赖暂时不可用 | `503` | `DEPENDENCY_UNAVAILABLE` | MySQL 等依赖异常 |
| 订单关键关联数据损坏 | `500` | `INTERNAL_ERROR` | 不向客户端暴露表名、SQL 或内部类名 |

错误响应统一包含 `requestId`，服务端日志使用该 ID 关联订单 ID 和异常，但不得记录 Token、完整手机号或完整地址。

## 5. 前端调用约定

平台订单管理页面按以下流程调用：

1. 使用 `GET /api/platform/operations/orders` 加载列表；
2. 用户点击“查看详情”时，从列表行读取字符串形式的 `id`；
3. 打开弹窗并进入加载状态；
4. 调用 `GET /api/platform/operations/orders/{orderId}`；
5. 使用响应中的 `displayStatus`、金额、时间、物流和商品明细完整渲染弹窗；
6. 关闭弹窗或切换订单时清理当前详情状态；
7. 返回 `AUTH_*` 错误时按统一会话规则处理，返回 `RESOURCE_NOT_FOUND` 时提示订单不存在或已不可用。

前端不得：

- 根据 `orderNo`、`tradeNo` 或列表下标猜测 `orderId`；
- 使用列表的 `itemSummary` 代替详情 `items`；
- 为缺失的支付、发货或完成时间生成当前时间；
- 把 `itemAmount` 当作 `payableAmount`；
- 从 `orderStatus` 自行覆盖 `displayStatus`；
- 在平台详情弹窗中调用买家或店铺订单详情接口；
- 展示或缓存本接口未返回的地址、手机号等敏感信息。

## 6. 实现边界

推荐的分层职责如下：

| 层或组件 | 职责 |
| --- | --- |
| `PlatformOperationController` | 新增 `GET /orders/{orderId}` 映射，绑定并校验路径参数，调用统一平台运营权限检查 |
| `PlatformOperationService` | 在只读事务中加载订单及关联数据，组装平台专用详情 DTO |
| `OperationDtos` | 新增 `OperationOrderDetailView`，复用安全的公共嵌套 View |
| `OrderInfoMapper` | 按 ID 读取子订单 |
| `TradeOrderMapper` | 读取父交易号、支付截止时间和支付时间 |
| `OrderItemMapper` | 按 `order_id` 查询商品明细并稳定排序 |
| `OrderStatusHistoryMapper` | 按 `created_at ASC, id ASC` 查询状态历史 |
| `ShopMapper` / `SysUserMapper` | 读取店铺和买家摘要所需信息 |
| `OrderViewService` | 复用统一的 `displayStatus`、商品、物流和历史转换逻辑，避免不同入口字段口径漂移 |

实现要求：

1. Controller 不直接访问 Mapper，也不自行拼接响应 JSON；
2. Service 使用 `@Transactional(readOnly = true)` 或沿用类级只读事务；
3. 依赖使用构造器注入并声明为 `private final`；
4. 不直接返回 `OrderInfo`、`TradeOrder`、`OrderItem` 等数据库实体；
5. 不先生成包含完整地址的 `OrderDetailView` 再依赖序列化配置删除字段，应直接组装安全 DTO；
6. `displayStatus` 使用现有统一计算逻辑；如果当前方法不可复用，应提取无状态的共享映射方法，而不是复制售后查询条件；
7. 单订单详情允许执行固定数量的关联查询，不在商品或历史循环内逐条查询关联对象；
8. 商品和历史必须使用稳定排序；
9. 本接口只读，不更新访问次数、订单状态或时间字段；
10. 成功响应使用 `ApiResponse.success(...)`，不得引入新的响应包装格式。

## 7. 数据库与兼容性结论

本需求直接复用以下现有表：

- `order_info`：子订单基础信息、金额、状态、物流和完成/取消时间；
- `trade_order`：父交易号、支付截止时间和支付时间；
- `order_item`：商品快照和金额；
- `order_status_history`：状态流转历史；
- `shop`：店铺摘要；
- `sys_user`：买家脱敏摘要；
- `after_sale_request` / `after_sale_appeal`：计算 `displayStatus`。

不新增表、不新增列、不修改索引，也不需要新增权限迁移。现有 `platform:operation:read` 已覆盖 `/api/platform/operations/**` 资源范围。

新增接口不会改变：

- 平台订单列表的路径、查询参数和分页结构；
- 买家订单详情的 OWNER 数据范围；
- 店铺订单详情的店铺成员和 `shop:order:read` 校验；
- 商家发货、买家确认收货、取消和售后的状态机；
- 现有 `OrderDetailView` 字段；
- 现有数据库迁移执行顺序。

如果后续需要在平台订单列表中直接展示 `displayStatus`，应单独为 `OperationOrderView` 增加该字段并同步 DTO 字段目录；该兼容性扩展不阻塞本文定义的详情接口。

## 8. 测试与验收清单

至少覆盖以下场景：

1. 拥有 `platform:operation:read`、但不是买家本人和店铺成员的账号可以读取订单详情；
2. 未登录、Token 过期、账号停用或锁定时返回统一认证错误；
3. 缺少 `platform:operation:read` 的普通买家、店铺成员和其他平台角色不能调用该接口；
4. 不存在、为零或负数的 `orderId` 返回约定错误；
5. 待支付订单返回非空 `payExpireAt`，`paidAt`、`shipping` 和 `completedAt` 为 `null`；
6. 待发货订单返回 `paidAt`，`shipping` 为 `null`；
7. 待收货订单返回完整 `shipping`，`completedAt` 为 `null`；
8. 已完成订单返回支付、发货和完成时间，且时间取自数据库，不由接口推断；
9. 已取消订单返回 `cancelledAt`，不伪造支付或发货数据；
10. 活跃售后或待裁决申诉存在时，`displayStatus = AFTER_SALE`，同时保留真实 `orderStatus`；
11. 多商品订单返回全部明细，按明细 ID 升序，金额和数量正确；
12. 状态历史按 `createdAt ASC, id ASC` 返回；
13. 响应中的所有 ID 为字符串、金额为两位小数字符串、时间包含 `+08:00` 等明确偏移；
14. 响应 JSON 不包含地址、收件人手机号、买家备注和 `availableActions`；
15. 平台详情请求不会修改订单、交易、商品、历史或任何时间字段；
16. 新接口不会放宽买家端和店铺端订单详情的原有鉴权；
17. 平台订单列表和业务追踪接口保持原有响应兼容；
18. 关键关联记录异常时返回统一服务端错误，日志可通过 `requestId` 定位且不泄露敏感数据。
