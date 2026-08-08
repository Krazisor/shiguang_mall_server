# 时光商城三期接口设计：库存调整、商家虚拟钱包与对象存储

## 1. 文档定位

本文是三期当前实现契约，说明已落地的库存人工调整、店铺商家虚拟钱包、对象存储和平台资金只读接口；不改变一期/二期的既有接口和数据库语义。它继承以下现有契约：

- [统一 API 契约](common-contract.md)中的 URL、ID、金额、时间、分页、响应、错误、幂等和并发规则。
- [基础交易接口分册](phase-1-api.md)中的支付、订单、钱包和售后状态。
- [治理与售后接口分册](phase-2-api.md)中的店铺权限、库存流水、售后退款和任务约束。

三期文档合并说明两个相互关联的能力：

1. 承接二期冻结的库存人工调整接口，说明输入、输出和一致性要求。
2. 说明已实现的店铺级商家虚拟钱包，用于记录订单收入、待结算资金、退款、平台佣金和虚拟提现。

三期不接入真实银行、微信、支付宝或其他第三方支付渠道。`withdrawals` 只表示教学环境中的虚拟出款申请和状态流转，不代表真实资金已经转出。

本文中的 `MerchantWallet*`、`ShopSettlement*`、`MerchantWithdrawal*` 和商家钱包枚举均已在当前 Java DTO、Service、Controller、任务和 `sql/scheme3.sql` 中实现；它们独立于买家 `WalletTransactionType` 和 `TransactionDirection`。库存调整接口复用二期已定义的 `InventoryTransactionView` 响应，不另造 `InventoryAdjustmentView`。不得直接把商家资金写入当前买家 `wallet_account`/`wallet_transaction`。

本分册中的所有分页列表默认按 `createdAt,desc` 并追加 `id,desc` 稳定排序；若未来开放 `sort` 参数，必须为每个列表单独声明白名单，不得接受任意数据库字段。

## 2. 三期资金模型

### 2.1 买家钱包和商家钱包的边界

一期的 `/api/wallet` 是买家个人钱包，归属 `sys_user`。三期新增的是店铺钱包，归属 `shop`，不是归属店铺管理员个人账号。

```text
买家钱包 balance
    - 支付成功扣款

平台订单清分记录
    - 订单商品金额进入店铺 pendingBalance
    - 平台佣金单独记录

店铺商家钱包
    - pendingBalance：已支付但仍在履约/售后观察期
    - availableBalance：可以申请虚拟提现的余额
    - frozenBalance：提现处理中或平台风控冻结的余额
```

店铺管理员离职、角色变更或 Token 被踢出，不影响店铺钱包归属。只有拥有目标店铺 `shop:wallet:read` 或 `shop:wallet:withdraw` 权限的有效成员可以操作对应接口。

三期枚举：`MerchantWalletStatus` 为 `ACTIVE`、`FROZEN`、`CLOSED`；`MerchantWithdrawalStatus` 为 `PROCESSING`、`SUCCESS`、`FAILED`、`REJECTED`。这些是三期新增代码，不修改当前买家 `WalletStatus` 或支付状态枚举。

### 2.2 收入、结算和退款时点

一期支付成功时，买家钱包已经扣款，所有子订单进入 `PENDING_SHIPMENT`。三期在同一个支付事务中为每个子订单创建一条结算记录：

```text
grossAmount       = 子订单 payableAmount
commissionAmount  = grossAmount * commissionRate，按分四舍五入
netAmount         = grossAmount - commissionAmount
```

`commissionRate` 是费率而不是金额：三期采用最多 4 位小数的百分比小数表示，例如 `0.1250` 表示 `0.1250%`；费率计算结果按金额分（`0.01` 元）四舍五入。它不适用统一契约中 `Money` 的两位小数格式。

默认店铺佣金比例为 `0.00%`，但结算记录必须保存费率和金额快照，后续调整费率不能改写历史订单。

支付成功后：

- `netAmount` 进入店铺 `pendingBalance`。
- 商家暂时不能将这笔金额提现。
- 订单取消或未发货仅退款成功时，释放或冲回对应的 pending 资金。

订单完成后仍保留售后观察期，默认 `7` 个自然日：

- `completedAt` 写入后，结算记录进入 `READY`，但资金仍保持 pending。
- `availableAt = completedAt + settlementDelayDays`；只有当前时间达到 `availableAt` 且没有活跃售后时，自动任务才将 `pendingBalance` 转入 `availableBalance`，并写入 `settledAt`。
- 存在 `PENDING`、`WAITING_RETURN` 或 `REFUNDING` 售后时，不得释放受影响订单对应的金额。

退款规则：

- 未发货仅退款：优先从该订单结算记录的 pending 未结算金额冲回；不足部分从商家 availableBalance 扣回；仍不足时进入 `RECOVERY_REQUIRED`，平台运营需要处理，不得让余额变成负数。
- 已发货退货退款：商家确认收货并退款成功后执行同样的冲回顺序。
- 二期售后申请中的 `requestedAmount`/`approvedAmount` 是买家侧退款金额，必须不超过订单明细尚未退款的买家应付金额；不能用商家 `netAmount` 替代这个上限。
- 平台售后申诉裁决如果批准退款，沿用二期退款服务和原 `refundNo`；三期商家钱包在同一退款事务中按既定锁顺序追加商家结算扣款，不允许绕过买家退款流水单独修改商家余额。
- `commissionRefundAmount` 由结算记录的 `commissionRefundable` 快照决定，三期默认全额退回本次退款对应的佣金；商家钱包实际扣款为 `merchantRefundAmount = buyerRefundAmount - commissionRefundAmount`，平台佣金冲回在结算台账中单独记录。
- 一次退款若同时冲回 pending 和 available，必须在同一事务内写入两条带同一 `refundNo` 根业务号的明细流水；每条流水使用不同后缀业务号，退款重试仍以根 `refundNo` 做幂等判断。

### 2.3 钱包余额不允许混用

买家钱包和店铺钱包是两个不同聚合，不能通过现有 `/api/wallet/recharges` 给商家钱包充值，也不能让商家钱包为买家支付。商家钱包只能由订单结算、退款冲回、平台人工调整和虚拟提现动作改变。

三期首批不开放平台人工调账 HTTP 接口；如需调账，必须另行设计带 `platform:settlement:manage` 权限、原因和审批审计的专用动作，不能通过商家接口或平台只读接口改余额。

## 3. 商家库存人工调整（承接二期接口）

本节不新增路径，也不改变二期接口契约；它整理已实现的库存调整接口输入、输出和一致性要求。正式实现应以 [phase-2-api.md](phase-2-api.md) 和 [dto-catalog.md](dto-catalog.md) 为准。

权限：`SHOP(shop:inventory:manage)`。

所有 `/api/shops/{shopId}/...` 接口都必须校验店铺成员、店铺权限和路径店铺范围。`CLOSED` 店铺只允许查询历史视图，不允许库存调整、提现或其他资金写操作。

### 3.1 调整库存

```http
POST /api/shops/{shopId}/inventory/{skuId}/adjustments
Idempotency-Key: damage-20260808-001
Content-Type: application/json
```

请求体 `InventoryAdjustmentRequest`：

```json
{
  "availableChange": -2,
  "lockedChange": 0,
  "version": 7,
  "reason": "仓库盘点发现 2 件商品损坏并报废"
}
```

字段规则：

| 字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `availableChange` | integer | 是 | 可用库存变化量，可正可负；和 `lockedChange` 不能同时为 0 |
| `lockedChange` | integer | 是 | 锁定库存变化量，可正可负；普通盘亏通常传 `0` |
| `version` | integer | 是 | 最近一次库存查询返回的版本，必须 `>=0` |
| `reason` | string | 是 | 去除首尾空白后 `1..500` 个字符 |

业务约束：

- 调整后的 available 和 locked 均不能小于 0。
- 调整后的 locked 不能小于本 SKU 仍处于 `reservationStatus=LOCKED` 的订单明细数量。
- 服务端必须在库存行锁内重新读取 version 和预占汇总；客户端预览不能替代服务端校验。
- 成功后库存版本递增 1；版本过期返回 `409 VERSION_CONFLICT`。
- 必须写一条不可变的 `ADJUST/MANUAL_ADJUSTMENT` 库存流水，业务号由服务端生成。
- 相同用户、方法、路径和幂等键重复请求返回第一次结果，不重复扣减库存。

成功响应：`201 Created`，统一响应 `data` 为 `InventoryTransactionView`。

```json
{
  "id": "1201",
  "transactionNo": "IT202608080099",
  "skuId": "511",
  "transactionType": "ADJUST",
  "availableBefore": 20,
  "lockedBefore": 3,
  "availableChange": -2,
  "lockedChange": 0,
  "availableAfter": 18,
  "lockedAfter": 3,
  "version": 8,
  "businessType": "MANUAL_ADJUSTMENT",
  "businessNo": "IA202608080001",
  "operator": {
    "id": "105",
    "username": "merchant_01",
    "nickname": "库存员"
  },
  "remark": "仓库盘点发现 2 件商品损坏并报废",
  "createdAt": "2026-08-08T18:30:15.123+08:00"
}
```

### 3.2 查询库存流水

```http
GET /api/shops/{shopId}/inventory/transactions
```

查询参数：

```text
skuId, transactionType, businessType, businessNo,
createdFrom, createdTo, page, pageSize
```

成功响应：`200 OK`，分页 `data` 为 `Page<InventoryTransactionView>`。

```json
{
  "items": [
    {
      "id": "1201",
      "transactionNo": "IT202608080099",
      "skuId": "511",
      "transactionType": "ADJUST",
      "availableBefore": 20,
      "lockedBefore": 3,
      "availableChange": -2,
      "lockedChange": 0,
      "availableAfter": 18,
      "lockedAfter": 3,
      "businessType": "MANUAL_ADJUSTMENT",
      "businessNo": "IA202608080001",
      "operator": {
        "id": "105",
        "username": "merchant_01",
        "nickname": "库存员"
      },
      "remark": "仓库盘点发现 2 件商品损坏并报废",
      "createdAt": "2026-08-08T18:30:15.123+08:00"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1,
  "totalPages": 1
}
```

库存错误码：

| HTTP | 错误码 | 场景 |
| ---: | --- | --- |
| 404 | `RESOURCE_NOT_FOUND` | SKU 不存在、已删除或不属于路径店铺 |
| 409 | `VERSION_CONFLICT` | 库存版本已变化 |
| 409 | `INVENTORY_LOCKED_BELOW_RESERVATIONS` | 调整后锁定库存低于订单预占 |
| 422 | `INVENTORY_ADJUSTMENT_NEGATIVE_RESULT` | 调整后 available 或 locked 小于 0 |
| 422 | `INVENTORY_OPERATION_INVALID` | 变化量均为 0、原因无效或超出整数范围 |

## 4. 商家虚拟钱包接口

### 4.1 钱包概览

权限：`SHOP(shop:wallet:read)`。接口和权限种子已实现。

```http
GET /api/shops/{shopId}/merchant-wallet
```

成功响应 `data`：`MerchantWalletView`。

```json
{
  "walletId": "1501",
  "shopId": "201",
  "currency": "CNY",
  "status": "ACTIVE",
  "pendingBalance": "7998.00",
  "availableBalance": "12000.00",
  "frozenBalance": "0.00",
  "lifetimeGrossIncome": "25600.00",
  "lifetimeCommission": "0.00",
  "lifetimeRefund": "1600.00",
  "version": 12,
  "updatedAt": "2026-08-08T18:30:15.123+08:00"
}
```

字段语义：

- `pendingBalance`：已支付但尚未过售后观察期的订单净收入。
- `availableBalance`：可以申请虚拟提现的余额。
- `frozenBalance`：提现处理中或平台风控冻结的余额。
- `lifetimeGrossIncome`、`lifetimeCommission`、`lifetimeRefund`：只增不减的统计快照，不作为扣款依据。
- 实际扣款和入账必须以钱包流水和结算记录为准。

### 4.2 钱包流水

权限：`SHOP(shop:wallet:read)`。接口和权限种子已实现。

```http
GET /api/shops/{shopId}/merchant-wallet/transactions
```

查询参数：

```text
transactionType, bucket, businessType, businessNo,
createdFrom, createdTo, page, pageSize
```

成功响应 `data` 为分页 `Page<MerchantWalletTransactionView>`。

```json
{
  "items": [
    {
      "id": "1701",
      "transactionNo": "MWT202608080001",
      "transactionType": "ORDER_PENDING_CREDIT",
      "direction": "CREDIT",
      "sourceBucket": null,
      "targetBucket": "PENDING",
      "bucket": "PENDING",
      "amount": "7998.00",
      "pendingBefore": "0.00",
      "pendingAfter": "7998.00",
      "availableBefore": "12000.00",
      "availableAfter": "12000.00",
      "frozenBefore": "0.00",
      "frozenAfter": "0.00",
      "businessType": "ORDER_SETTLEMENT",
      "businessNo": "ST202608080001",
      "orderId": "711",
      "orderNo": "OR202608080001",
      "operator": null,
      "remark": "支付成功，进入待结算余额",
      "createdAt": "2026-08-08T18:30:15.123+08:00"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1,
  "totalPages": 1
}
```

`transactionType` 使用以下稳定代码；下表是三期商家钱包内部的新字典，不修改当前买家钱包的 `WalletTransactionType`：

其中 `TRANSFER` 是三期商家钱包流水新增的方向代码，表示同一笔账务在两个余额区之间转移；不能把该动作拆成两个可独立重试的普通 `CREDIT`/`DEBIT` 请求。`PLATFORM_ADJUST` 才允许使用单向 `CREDIT` 或 `DEBIT`。

| 类型 | 方向 | 余额区 | 说明 |
| --- | --- | --- | --- |
| `ORDER_PENDING_CREDIT` | `CREDIT` | `PENDING` | 支付成功后订单净收入入账 |
| `SETTLEMENT_RELEASE` | `TRANSFER` | `PENDING -> AVAILABLE` | 观察期结束后释放；一个业务动作同时减少 pending 并增加 available |
| `COMMISSION_DEBIT` | `DEBIT` | `PENDING` | 平台佣金，默认金额为 0.00 |
| `REFUND_DEBIT` | `DEBIT` | `PENDING` 或 `AVAILABLE` | 售后退款冲回商家收入 |
| `WITHDRAW_FREEZE` | `TRANSFER` | `AVAILABLE -> FROZEN` | 虚拟提现申请冻结；一个业务动作同时减少 available 并增加 frozen |
| `WITHDRAW_SUCCESS` | `DEBIT` | `FROZEN` | 虚拟出款成功 |
| `WITHDRAW_FAILED` | `TRANSFER` | `FROZEN -> AVAILABLE` | 虚拟出款执行失败并释放冻结 |
| `WITHDRAW_REJECT` | `TRANSFER` | `FROZEN -> AVAILABLE` | 虚拟出款驳回释放 |
| `PLATFORM_ADJUST` | `CREDIT/DEBIT` | 指定余额区 | 平台人工调账，必须有原因和操作者 |

三期新增的商家钱包 `businessType` 至少包括：`ORDER_SETTLEMENT`、`AFTER_SALE_MERCHANT_REFUND`、`MERCHANT_WITHDRAWAL`、`PLATFORM_SETTLEMENT_ADJUSTMENT`。三期冻结时必须把这些代码同步加入统一业务字典，并为每个代码补充数据库唯一键和对账规则。

### 4.3 结算记录

权限：`SHOP(shop:wallet:read)`。接口和权限种子已实现。

```http
GET /api/shops/{shopId}/merchant-wallet/settlements
```

查询参数：

```text
orderNo, settlementStatus, createdFrom, createdTo, page, pageSize
```

成功响应 `data` 为分页 `Page<ShopSettlementView>`。

```json
{
  "items": [
    {
      "settlementId": "1601",
      "shopId": "201",
      "orderId": "711",
      "orderNo": "OR202608080001",
      "tradeId": "701",
      "tradeNo": "TR202608080001",
      "status": "PENDING",
      "grossAmount": "7998.00",
      "commissionRate": "0.0000",
      "commissionRefundable": true,
      "commissionAmount": "0.00",
      "buyerRefundAmount": "0.00",
      "commissionRefundAmount": "0.00",
      "merchantRefundAmount": "0.00",
      "netAmount": "7998.00",
      "pendingAmount": "7998.00",
      "releasedAmount": "0.00",
      "availableAt": null,
      "settledAt": null,
      "createdAt": "2026-08-08T18:30:15.123+08:00",
      "updatedAt": "2026-08-08T18:30:15.123+08:00"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1,
  "totalPages": 1
}
```

结算状态：

```text
PENDING -> READY -> SETTLED
PENDING -> REFUNDED
READY   -> REFUNDED
PENDING/READY -> RECOVERY_REQUIRED
```

`RECOVERY_REQUIRED` 表示退款金额超过商家当前可冲回余额，系统不能产生负余额；该状态需要平台运营人工处理。平台运营只能通过专用治理接口处理，不复用商家只读接口。

三期首批不提供自动追缴或负余额能力。若需要在系统内解决 `RECOVERY_REQUIRED`，应新增独立的 `platform:settlement:manage` 权限和平台结算调账动作；不能使用现有只读 `platform:operation:read` 接口，也不能把该状态静默改为 `REFUNDED`。

### 4.4 虚拟提现申请

权限：`SHOP(shop:wallet:withdraw)`。接口和权限种子已实现，且必须提交 `Idempotency-Key`。

```http
POST /api/shops/{shopId}/merchant-wallet/withdrawals
Idempotency-Key: wd-20260808-001
Content-Type: application/json
```

请求体 `CreateMerchantWithdrawalRequest`：

```json
{
  "amount": "500.00",
  "destinationType": "VIRTUAL_ACCOUNT",
  "destinationAccount": "MERCHANT-DEMO-201",
  "remark": "测试环境虚拟提现"
}
```

字段规则：

- `amount` 为两位小数字符串，范围 `0.01..1000000.00`，且不得超过 `availableBalance`。
- `destinationType` 当前固定为 `VIRTUAL_ACCOUNT`；不接受银行卡、微信、支付宝等真实渠道代码。
- `destinationAccount` 去除首尾空白后 `1..128` 个字符；仅用于生成虚拟出款结果。
- `remark` 可省略或为 `null`，非空最大 500 字符。

成功响应：`201 Created`，`data` 为 `MerchantWithdrawalView`。

```json
{
  "withdrawalId": "1801",
  "withdrawalNo": "MWD202608080001",
  "shopId": "201",
  "status": "PROCESSING",
  "amount": "500.00",
  "feeAmount": "0.00",
  "netAmount": "500.00",
  "destinationType": "VIRTUAL_ACCOUNT",
  "destinationAccountMasked": "MERCHANT-****-201",
  "failureReason": null,
  "requestedAt": "2026-08-08T18:30:15.123+08:00",
  "completedAt": null
}
```

虚拟提现状态：

```text
PROCESSING -> SUCCESS
PROCESSING -> FAILED
PROCESSING -> REJECTED
```

测试环境可以由内部任务或平台运维动作完成 `PROCESSING -> SUCCESS`。真实支付渠道接入前，`SUCCESS` 只表示虚拟账务完成，不表示银行到账。

`FAILED` 和 `REJECTED` 都必须在同一事务内将 frozen 余额转回 available，并分别写入 `WITHDRAW_FAILED` 或 `WITHDRAW_REJECT` 转移流水；只有 `SUCCESS` 才最终扣除 frozen 余额。

### 4.5 查询提现记录

```http
GET /api/shops/{shopId}/merchant-wallet/withdrawals
```

查询参数：`status,createdFrom,createdTo,page,pageSize`。

成功响应为 `Page<MerchantWithdrawalView>`，字段与创建接口响应相同。

### 4.6 三期新增权限

三期迁移 `sql/scheme3.sql` 新增以下店铺权限；它们不写入一期/二期基线，并只默认授予 `SHOP_ADMIN`，不会自动授予其他现有店铺角色：

| 权限代码 | 作用域 | 资源 |
| --- | --- | --- |
| `shop:wallet:read` | `SHOP` | `/api/shops/*/merchant-wallet/**` |
| `shop:wallet:withdraw` | `SHOP` | `/api/shops/*/merchant-wallet/withdrawals` |

建议由 `SHOP_ADMIN` 默认获得两项权限，其他店铺角色由管理员按最小权限原则分配。提现权限不能仅依赖前端按钮，必须在服务端校验。

## 5. 对象存储与图片上传

三期新增统一的图片上传接口，二进制内容写入已部署的 MinIO，MySQL 的
`object_asset` 只保存对象元数据、资源用途和归属。上传接口是可直接实现的
三期接口；它不提供任意附件、视频、压缩包或 SVG 上传。

### 5.1 上传图片

```http
POST /api/assets/images
Content-Type: multipart/form-data
satoken: <登录令牌>
```

表单字段：

| 字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `file` | `MultipartFile` | 是 | 图片二进制；服务端按文件头识别，不信任扩展名 |
| `purpose` | `AssetPurpose` | 是 | 图片用途，必须是下表中的稳定代码 |
| `shopId` | `Id` | 条件必填 | 商品图片必须提供；头像、售后凭证、平台 Logo/品牌 Logo 不得提供 |

`purpose` 取值：

| 用途 | `shopId` | 允许角色/权限 | 典型引用字段 |
| --- | --- | --- | --- |
| `AVATAR` | 不允许 | 登录用户 + `asset:upload` | `sys_user.avatar_url` |
| `SHOP_LOGO` | 不允许 | `platform:shop:manage` + `asset:upload` | `shop.logo_url` |
| `BRAND_LOGO` | 不允许 | `platform:catalog:manage` + `asset:upload` | `brand.logo_url` |
| `PRODUCT_COVER` | 必须 | 店铺成员 `shop:product:manage` | 商品 `coverUrl` |
| `PRODUCT_GALLERY` | 必须 | 店铺成员 `shop:product:manage` | 商品 `galleryUrls[]` |
| `SKU_IMAGE` | 必须 | 店铺成员 `shop:product:manage` | SKU `imageUrl` |
| `RICH_TEXT_IMAGE` | 必须 | 店铺成员 `shop:product:manage` | `detailHtml` 中的图片 URL |
| `AFTER_SALE_EVIDENCE` | 不允许 | 买家 + `asset:upload` | `CreateAfterSaleRequest.evidenceUrls[]` |
| `APPEAL_EVIDENCE` | 不允许 | 买家 + `asset:upload` | `CreateAfterSaleAppealRequest.evidenceUrls[]` |

商品用途的 `shopId` 必须是当前登录用户有权管理的店铺；平台用途的上传不接受
`shopId`，避免把平台资源错误归属给商家。服务端还应在业务接口保存 URL 前校验
该 URL 对应的 `object_asset` 用途、状态和店铺范围，不能只校验字符串格式。

成功响应：`201 Created`，统一响应 `data` 为 `AssetUploadView`。

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": "2101",
    "assetNo": "ASSET202608080001",
    "purpose": "PRODUCT_COVER",
    "bucket": "shiguang-market",
    "objectKey": "assets/2026-08-08/product_cover/3f0d8c6e-4a31-4e5a-8bb0-8f4b1cb0d6b2.jpg",
    "originalFilename": "cover.jpg",
    "contentType": "image/jpeg",
    "sizeBytes": 248631,
    "sha256": "f4d7a8c4d2d8e6a7b5d1c9f8e7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9a8",
    "url": "http://127.0.0.1:9000/shiguang-market/assets/2026-08-08/product_cover/3f0d8c6e-4a31-4e5a-8bb0-8f4b1cb0d6b2.jpg",
    "createdAt": "2026-08-08T18:30:15.123+08:00"
  },
  "requestId": "req-01J...",
  "timestamp": "2026-08-08T18:30:15.123+08:00"
}
```

当 `MINIO_PUBLIC_READ=true` 时，`url` 是稳定公开 URL，适合写入商品、头像、Logo
和售后凭证字段；当其为 `false` 时，接口返回有效期 1 小时的预签名 GET URL，业务
字段不得长期持久化该临时 URL。私有模式下，后续需要通过资源 URL 刷新接口或在业务
详情组装时动态生成访问地址，本期不把临时 URL 当作永久资源地址。

### 5.2 文件校验与失败处理

- 只接受 JPEG、PNG、GIF、WEBP，默认单文件上限 `10 MiB`，请求体上限 `12 MiB`；实际限制以 `MINIO_MAX_FILE_SIZE_BYTES`、`UPLOAD_MAX_FILE_SIZE` 和 `UPLOAD_MAX_REQUEST_SIZE` 为准。
- 服务端同时检查文件头和客户端 `Content-Type`；文件头与 MIME 不一致返回 `400 UPLOAD_CONTENT_TYPE_MISMATCH`，不支持的内容返回 `400 UPLOAD_IMAGE_TYPE_UNSUPPORTED`。
- 空文件返回 `400 UPLOAD_FILE_REQUIRED`，超过 multipart 或业务大小上限返回 `413 UPLOAD_FILE_TOO_LARGE`。
- MinIO 未启用返回 `422 STORAGE_NOT_CONFIGURED`；MinIO 写入或 `object_asset` 元数据写入失败返回 `503 DEPENDENCY_UNAVAILABLE`。元数据失败时服务端应尽力删除已上传对象，并记录孤儿对象清理日志。
- 客户端不得提交 bucket、object key 或公开 URL；object key、资源编号和摘要均由服务端生成。

一期/二期现有 JSON 中的 `avatarUrl`、`logoUrl`、`coverUrl`、`galleryUrls`、`imageUrl`、
`evidenceUrls` 仍保持 URL 字段兼容。新客户端应先上传图片，再把响应中的 `url` 提交到
对应业务接口；旧客户端继续提交符合统一 URL 安全规则的外部 HTTPS URL，不要求立即迁移。

## 6. 平台运营只读与任务接口

为便于排障，三期增加以下平台只读查询，不允许直接替商家执行提现或退款：

```http
GET /api/platform/operations/merchant-wallets
GET /api/platform/operations/merchant-wallet-transactions
GET /api/platform/operations/settlements
GET /api/platform/operations/withdrawals
```

权限统一为现有 `platform:operation:read`。该权限的资源前缀 `/api/platform/operations/**` 已由二期权限增量覆盖，因此三期只需增加查询 DTO/Controller；平台可查询店铺、结算单、钱包流水和虚拟提现状态，但不能通过只读接口改余额。

建议增加以下非生产环境内部任务：

```http
POST /api/internal/tasks/release-settlements
POST /api/internal/tasks/process-virtual-withdrawals
```

两者都要求 `platform:task:execute`、内网访问和 `market.internal-task-api-enabled=true`。请求体沿用 `TaskRunRequest`；结算释放和虚拟出款必须使用固定业务号和状态条件保证幂等。

`release-settlements` 是写任务：`dryRun=true` 只扫描并返回可释放数量，`dryRun=false` 才执行余额转移；`process-virtual-withdrawals` 同样支持预览和执行。两项任务都必须按店铺 ID 升序获取钱包锁，重复执行不得重复产生流水。

## 7. 资金错误码

| HTTP | 错误码 | 场景 |
| ---: | --- | --- |
| 404 | `RESOURCE_NOT_FOUND` | 钱包、结算单或提现记录不存在，或不属于目标店铺 |
| 409 | `VERSION_CONFLICT` | 钱包或结算聚合版本过期 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 相同幂等键提交了不同请求 |
| 409 | `MERCHANT_WALLET_FROZEN` | 店铺钱包被冻结 |
| 409 | `SETTLEMENT_NOT_RELEASEABLE` | 订单仍未完成、仍在售后观察期或存在活跃售后 |
| 409 | `WITHDRAWAL_NOT_PROCESSABLE` | 提现状态不允许重复处理 |
| 422 | `MERCHANT_WALLET_INSUFFICIENT` | 可用余额不足 |
| 422 | `MERCHANT_WALLET_REFUND_INSUFFICIENT` | 退款需要平台人工追缴 |
| 422 | `WITHDRAWAL_AMOUNT_INVALID` | 金额格式、范围或精度不符合要求 |
| 422 | `WITHDRAWAL_DESTINATION_INVALID` | 虚拟收款账户格式不合法 |
| 503 | `DEPENDENCY_UNAVAILABLE` | 必要依赖暂时不可用，客户端可使用原幂等键重试 |

## 8. 资金一致性和锁顺序

### 8.1 支付成功

支付确认事务必须在现有支付锁顺序上追加店铺资金对象，按以下顺序执行：

```text
父交易 -> 支付单 -> 买家钱包 -> 子订单 -> 店铺钱包 -> 结算记录
```

跨店交易包含多个店铺时，子订单按 `orderId ASC`，店铺钱包和对应结算记录按 `shopId ASC`、`settlementId ASC` 获取锁。这条排序是支付锁顺序的一部分；子订单状态更新仍在同一事务内完成。

同一支付幂等键只能产生一条买家 `CONSUME` 流水和每个店铺一条 `ORDER_PENDING_CREDIT` 流水。

### 8.2 结算释放

结算释放事务必须按 `shopId ASC` 锁定店铺钱包，再按 `settlementId ASC` 锁定结算记录，检查订单及售后状态后，再执行：

```text
pendingBalance - netAmount
availableBalance + netAmount
结算状态 READY -> SETTLED
```

### 8.3 退款

退款事务必须保留二期既定顺序，并在买家资金对象之后追加商家资金对象：

```text
售后 -> 子订单 -> 订单明细 -> SKU 库存 -> 原成功支付单 -> 买家钱包 -> 商家钱包 -> 结算记录
```

在商家钱包内按 pending 优先、available 其次的顺序冲回；钱包流水业务号使用 `AFTER_SALE_MERCHANT_REFUND + refundNo`，重试不得产生第二组同根业务流水。该顺序是对二期[数据库设计](../database/phase-1-design.md)既有顺序的追加，不得把商家钱包提前到原成功支付单或买家钱包之前。

### 8.4 钱包对账

每日对账比较：

```text
钱包当前三类余额 = 钱包流水按 bucket 汇总结果
所有结算记录余额 = 对应店铺钱包 pending/available 变化
```

发现差异只报告，不自动改余额。对账结果应可通过平台运营查询接口定位到店铺、订单、结算单和流水。

## 9. 建议新增的数据模型

三期实现需要新增独立表，不复用买家 `wallet_account`：

```text
merchant_wallet_account
merchant_wallet_transaction
shop_settlement
merchant_withdrawal
```

推荐最小字段：

- `merchant_wallet_account`：`id, shop_id, pending_balance, available_balance, frozen_balance, status, version, created_at, updated_at`。
- `merchant_wallet_transaction`：三类余额变化前后值、方向、金额、`business_type/business_no`、订单/结算/提现关联、操作者和备注；业务号唯一。
- `shop_settlement`：店铺、订单、交易、商品总额、佣金费率、`commission_refundable` 快照、佣金金额、买家退款金额、佣金退回金额、商家退款扣款、净收入、待结算金额、已释放金额、状态、`available_at`、`settled_at`、版本和时间。
- `merchant_withdrawal`：店铺钱包、提现单号、申请金额、手续费、净额、虚拟目的地、状态、失败原因、幂等业务号和时间。

所有资金流水不可更新、不可删除；聚合钱包余额和结算状态必须只能通过事务服务修改。三期商家钱包单独使用 `CREDIT`、`DEBIT`、`TRANSFER` 三种方向；当前买家钱包的 `TransactionDirection` 仍只有 `CREDIT/DEBIT`，不能直接复用或修改当前买家流水表的 CHECK 约束。`TRANSFER` 流水必须同时保存来源余额区和目标余额区的前后余额，不能只记录一个方向字段。

## 10. 三期首批验收场景

1. 商家库存为 `20`，提交损坏调整 `availableChange=-2`，结果为 `18`，流水只产生一条 `ADJUST`。
2. 相同库存调整幂等键并发提交三次，只产生一次库存变化。
3. 买家支付一笔跨两店交易，两家店各产生一条待结算收入，金额按各自子订单拆分。
4. 订单完成但未过 7 天观察期，商家只能看到 pending，不能提现。
5. 观察期结束后，定时任务将 pending 转为 available，重复执行不重复入账。
6. 未发货仅退款成功，商家结算金额被冲回，买家钱包收到退款。
7. 商家提交虚拟提现后，可用余额转入 frozen；重复请求不重复冻结。
8. 虚拟提现驳回后 frozen 转回 available；虚拟提现成功后不再回滚余额。
9. 平台运营能查询结算、商家钱包流水和提现状态，但不能通过只读接口直接改余额。
10. 钱包或结算聚合被人为篡改时，对账任务报告差异，不自动修数。
