# 时光商城二期接口文档

## 1. 使用说明

本文定义二期新增接口，并继承[统一 API 契约](common-contract.md)和[一期接口文档](phase-1-api.md)。二期不得改变一期路径、DTO 和错误语义；如需增加响应字段，按兼容变更执行。

## 2. 平台用户与 RBAC

所有 RBAC 写接口需要 `platform:rbac:manage`。列表均分页，默认 `createdAt,desc`。

### 2.1 用户管理

| 方法 | 路径 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- |
| GET | `/api/platform/rbac/users` | `keyword,status,roleCode,page,pageSize` | `Page<PlatformUserView>` |
| GET | `/api/platform/rbac/users/{userId}` | 无 | `PlatformUserDetailView` |
| POST | `/api/platform/rbac/users/{userId}/status` | `ChangeUserStatusRequest` | `PlatformUserDetailView` |
| PUT | `/api/platform/rbac/users/{userId}/roles` | `AssignPlatformRolesRequest` | `PlatformUserDetailView` |
| POST | `/api/platform/rbac/users/{userId}/kickout` | `ReasonRequest` | `null` |

```json
// ChangeUserStatusRequest
{
  "targetStatus": "DISABLED",
  "reason": "测试账号停用"
}
```

允许 `ACTIVE <-> DISABLED`、`ACTIVE <-> LOCKED`、`LOCKED -> DISABLED`、`DISABLED -> LOCKED`。状态变为非 `ACTIVE` 后调用 Sa-Token 踢下线该账号全部终端。重新激活不自动登录。

```json
// AssignPlatformRolesRequest
{
  "roleIds": ["1001", "1002"]
}
```

该接口是全量替换，空数组表示移除所有平台角色。不能给用户分配 `SHOP` 角色。禁止调用者移除自己最后一个可管理 RBAC 的角色，返回 `CANNOT_REMOVE_OWN_ADMIN_ACCESS`。

```json
// PlatformUserDetailView
{
  "id": "101",
  "username": "alice_01",
  "nickname": "Alice",
  "phone": "138****0000",
  "email": "a***@example.com",
  "avatarUrl": null,
  "status": "ACTIVE",
  "platformRoles": [
    {"id": "1001", "roleCode": "CUSTOMER", "roleName": "普通用户", "status": "ACTIVE"}
  ],
  "lastLoginAt": "2026-07-26T18:30:15.123+08:00",
  "createdAt": "2026-07-20T10:00:00.000+08:00",
  "updatedAt": "2026-07-26T18:30:15.123+08:00"
}
```

### 2.2 角色和权限

| 方法 | 路径 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- |
| GET | `/api/platform/rbac/roles` | `scopeType,status,keyword,page,pageSize` | `Page<RoleView>` |
| POST | `/api/platform/rbac/roles` | `CreateRoleRequest` | `RoleDetailView`，`201` |
| GET | `/api/platform/rbac/roles/{roleId}` | 无 | `RoleDetailView` |
| PUT | `/api/platform/rbac/roles/{roleId}` | `UpdateRoleRequest` | `RoleDetailView` |
| POST | `/api/platform/rbac/roles/{roleId}/status` | `StatusRequest<ACTIVE|DISABLED>` | `RoleDetailView` |
| PUT | `/api/platform/rbac/roles/{roleId}/permissions` | `AssignPermissionsRequest` | `RoleDetailView` |
| GET | `/api/platform/rbac/permissions` | `scopeType,status,keyword,page,pageSize` | `Page<PermissionView>` |

```json
// CreateRoleRequest
{
  "roleCode": "SHOP_AFTER_SALE_OPERATOR",
  "roleName": "店铺售后专员",
  "scopeType": "SHOP",
  "description": "只处理本店售后",
  "permissionIds": ["2101"]
}
```

`roleCode` 格式 `^[A-Z][A-Z0-9_]{2,63}$`。创建后 `roleCode` 和 `scopeType` 不可修改。种子角色允许修改展示名称、说明和授权，但禁止删除；本阶段所有角色都不提供删除接口。

关键角色 `CUSTOMER`、`SUPER_ADMIN`、`SHOP_ADMIN` 不允许停用。任何平台角色分配、角色停用或权限替换完成后，系统必须仍存在至少一名 `ACTIVE` 用户可通过有效角色获得 `platform:rbac:manage`；否则返回 `LAST_RBAC_ADMIN_REQUIRED`。店铺最后管理员保护由店铺成员接口单独保证。

```json
// UpdateRoleRequest
{
  "roleName": "店铺售后客服",
  "description": "处理本店售后申请"
}
```

```json
// AssignPermissionsRequest
{
  "permissionIds": ["2101", "2102"]
}
```

全量替换；所有权限必须与角色 `scopeType` 相同。`PermissionView` 返回 `permissionCode`、`permissionName`、`scopeType`、`resource`、`httpMethod`、`status`。权限作为代码字典，不提供新增、修改或删除 API。

业务错误：`ROLE_CODE_ALREADY_EXISTS`、`ROLE_SCOPE_MISMATCH`、`ROLE_IN_USE`、`PERMISSION_SCOPE_MISMATCH`、`CANNOT_DISABLE_CRITICAL_ROLE`。

## 3. 店铺成员

接口均使用 `SHOP(shop:member:manage)`。

| 方法 | 路径 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- |
| GET | `/api/shops/{shopId}/members` | `keyword,roleId,status,page,pageSize` | `Page<ShopMemberView>` |
| POST | `/api/shops/{shopId}/members` | `AddShopMemberRequest` | `ShopMemberView`，`201` |
| PUT | `/api/shops/{shopId}/members/{userId}/role` | `ChangeShopMemberRoleRequest` | `ShopMemberView` |
| POST | `/api/shops/{shopId}/members/{userId}/status` | `StatusRequest<ACTIVE|DISABLED>` | `ShopMemberView` |

```json
// AddShopMemberRequest
{
  "username": "shop_a_order",
  "roleId": "2002"
}
```

```json
// ChangeShopMemberRoleRequest
{
  "roleId": "2003"
}
```

`username` 必须精确匹配一个 `ACTIVE` 用户，角色必须是 `SHOP/ACTIVE`。成员变更后清理该用户店铺权限缓存。

```json
// ShopMemberView
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
    "roleName": "店铺订单客服"
  },
  "status": "ACTIVE",
  "createdAt": "2026-07-26T18:30:15.123+08:00",
  "updatedAt": "2026-07-26T18:30:15.123+08:00"
}
```

业务错误：`SHOP_MEMBER_ALREADY_EXISTS`、`SHOP_MEMBER_NOT_FOUND`、`SHOP_ROLE_REQUIRED`、`LAST_SHOP_ADMIN_REQUIRED`、`CANNOT_DISABLE_SELF_WITHOUT_OTHER_ADMIN`。

## 4. 平台商品治理

### 4.1 接口

| 方法 | 路径 | 鉴权 | 请求 | 成功 `data` |
| --- | --- | --- | --- | --- |
| POST | `/api/platform/products/bans/{spuId}` | `platform:product:ban` | `ProductGovernanceRequest` | `ProductReviewDetailView` |
| POST | `/api/platform/products/bans/{spuId}/revoke` | `platform:product:ban` | `ProductGovernanceRequest` | `ProductReviewDetailView` |
| POST | `/api/platform/products/bans/{spuId}/take-off-shelf` | `platform:product:ban` | `ProductGovernanceRequest` | `ProductReviewDetailView` |
| GET | `/api/platform/products/reviews/{spuId}/history` | `platform:product:audit` | `page,pageSize` | `Page<ProductStatusHistoryView>` |

```json
// ProductGovernanceRequest
{
  "contentVersion": 3,
  "reason": "商品图片包含违规内容"
}
```

原因必填 `1..500`。禁售只接受 `OFF_SHELF`、`ON_SHELF`；解禁只接受 `BANNED`；平台强制下架只接受 `ON_SHELF`。错误：`PRODUCT_NOT_BANNABLE`、`PRODUCT_NOT_BANNED`、`PRODUCT_NOT_ON_SHELF`。

### 4.2 历史视图

```json
{
  "id": "1101",
  "spuId": "501",
  "fromStatus": "ON_SHELF",
  "toStatus": "BANNED",
  "operationType": "BAN",
  "contentVersion": 3,
  "operatorType": "PLATFORM",
  "operator": {"id": "1", "username": "auditor", "nickname": "审核员"},
  "reason": "商品图片包含违规内容",
  "createdAt": "2026-07-26T18:30:15.123+08:00"
}
```

商家商品详情中的历史只返回本商品记录，不显示不必要的操作者联系方式。

## 5. 库存调整与流水

接口使用 `SHOP(shop:inventory:manage)`。

| 方法 | 路径 | 幂等 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- | --- |
| GET | `/api/shops/{shopId}/inventory/transactions` | 否 | `skuId,transactionType,businessType,businessNo,createdFrom,createdTo,page,pageSize` | `Page<InventoryTransactionView>` |
| POST | `/api/shops/{shopId}/inventory/{skuId}/adjustments` | 必填 | `InventoryAdjustmentRequest` | `InventoryOperationView`，`201` |

```json
// InventoryAdjustmentRequest
{
  "availableChange": -2,
  "lockedChange": 0,
  "version": 7,
  "reason": "盘点发现库存差异"
}
```

- 两个变化量至少一个非 0。
- 调整后可用、锁定库存均不能为负。
- `lockedQuantity` 调整后不得小于所有本 SKU、`reservationStatus=LOCKED` 订单明细数量合计。
- `version` 必须等于当前库存版本。
- 流水为 `ADJUST`、`businessType=MANUAL_ADJUSTMENT`，`businessNo` 由后端生成并作为幂等业务号。

```json
// InventoryTransactionView
{
  "id": "1201",
  "transactionNo": "IT202607260099",
  "skuId": "511",
  "transactionType": "ADJUST",
  "availableChange": -2,
  "lockedChange": 0,
  "availableBefore": 20,
  "lockedBefore": 3,
  "availableAfter": 18,
  "lockedAfter": 3,
  "businessType": "MANUAL_ADJUSTMENT",
  "businessNo": "IA202607260001",
  "operator": {"id": "105", "username": "merchant_01", "nickname": "库存员"},
  "remark": "盘点发现库存差异",
  "createdAt": "2026-07-26T18:30:15.123+08:00"
}
```

`availableBefore` 和 `lockedBefore` 由 after-change 减 change 计算。错误：`INVENTORY_ADJUSTMENT_NEGATIVE_RESULT`、`INVENTORY_LOCKED_BELOW_RESERVATIONS`。

## 6. 买家售后

创建申请要求登录、本人资源和 `after-sale:create`；资格、列表、详情、撤销及退货物流接口要求登录和本人资源。

### 6.1 接口清单

| 方法 | 路径 | 幂等 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- | --- |
| GET | `/api/orders/{orderId}/items/{orderItemId}/after-sale-eligibility` | 否 | 无 | `AfterSaleEligibilityView` |
| POST | `/api/after-sales` | 必填 | `CreateAfterSaleRequest` | `AfterSaleDetailView`，`201` |
| GET | `/api/after-sales` | 否 | `status,requestType,orderNo,createdFrom,createdTo,page,pageSize` | `Page<AfterSaleSummaryView>` |
| GET | `/api/after-sales/{afterSaleId}` | 否 | 无 | `AfterSaleDetailView` |
| POST | `/api/after-sales/{afterSaleId}/cancel` | 建议 | 无 | `AfterSaleDetailView` |
| POST | `/api/after-sales/{afterSaleId}/return-shipment` | 必填 | `ReturnShipmentRequest` | `AfterSaleDetailView` |
| PUT | `/api/after-sales/{afterSaleId}/return-shipment` | 否 | `UpdateReturnShipmentRequest` | `AfterSaleDetailView` |

### 6.2 资格与申请

```json
// AfterSaleEligibilityView
{
  "orderId": "711",
  "orderItemId": "721",
  "orderStatus": "PENDING_RECEIPT",
  "purchasedQuantity": 2,
  "refundedQuantity": 0,
  "occupiedQuantity": 0,
  "maximumRequestQuantity": 2,
  "itemPayableAmount": "7998.00",
  "refundedAmount": "0.00",
  "occupiedAmount": "0.00",
  "maximumRequestAmount": "7998.00",
  "supportedTypes": ["REFUND_ONLY", "RETURN_REFUND"],
  "eligibleUntil": null,
  "eligible": true,
  "ineligibleReason": null
}
```

`occupied` 包含其他已批准未完成和退款中申请所占额度。若订单已完成，`eligibleUntil=completedAt+7 days`。

```json
// CreateAfterSaleRequest
{
  "orderId": "711",
  "orderItemId": "721",
  "requestType": "RETURN_REFUND",
  "quantity": 1,
  "reasonCode": "QUALITY_PROBLEM",
  "reasonDescription": "开机后屏幕闪烁",
  "evidenceUrls": ["https://static.example.com/evidence/abc.png"],
  "requestedAmount": "3999.00"
}
```

`requestedAmount` 不能超过按数量计算的本次可退上限。`OTHER` 原因必须填写说明；凭证最多 9 个唯一 HTTPS URL。创建时服务端重新计算额度并锁定订单明细，防止并发超额申请。

### 6.3 退货物流

```json
// ReturnShipmentRequest
{
  "carrierCode": "SF",
  "carrierName": "顺丰速运",
  "trackingNo": "SF9876543210"
}
```

`POST` 只允许申请人对 `RETURN_REFUND/WAITING_RETURN` 且尚未填写物流的申请提交，提交时间由服务器写入 `returnedAt`。商家确认收货前，申请人可以通过 `PUT` 更正承运商和运单号；`UpdateReturnShipmentRequest` 在三个物流字段之外必须提交当前 `version`。`returnReceivedAt` 已存在后禁止修改。

### 6.4 售后视图

```json
// AfterSaleDetailView
{
  "id": "1301",
  "afterSaleNo": "AS202607260001",
  "requestType": "RETURN_REFUND",
  "status": "WAITING_RETURN",
  "refundStatus": "NOT_STARTED",
  "order": {"id": "711", "orderNo": "OR202607260001", "orderStatus": "PENDING_RECEIPT"},
  "shop": {},
  "item": {
    "id": "721",
    "productName": "示例手机",
    "skuName": "黑色 256GB",
    "spec": {"color": "黑色", "storage": "256GB"},
    "imageUrl": null,
    "unitPrice": "3999.00",
    "purchasedQuantity": 2
  },
  "quantity": 1,
  "reasonCode": "QUALITY_PROBLEM",
  "reasonDescription": "开机后屏幕闪烁",
  "evidenceUrls": ["https://static.example.com/evidence/abc.png"],
  "requestedAmount": "3999.00",
  "approvedQuantity": 1,
  "approvedAmount": "3999.00",
  "review": {
    "reviewerId": "105",
    "comment": "同意退货退款",
    "reviewedAt": "2026-07-27T10:00:00.000+08:00"
  },
  "returnShipment": null,
  "refundNo": null,
  "refundFailureReason": null,
  "refundedAt": null,
  "completedAt": null,
  "cancelledAt": null,
  "version": 1,
  "createdAt": "2026-07-27T09:00:00.000+08:00",
  "updatedAt": "2026-07-27T10:00:00.000+08:00",
  "availableActions": ["SUBMIT_RETURN_SHIPMENT"]
}
```

业务错误：`AFTER_SALE_NOT_ELIGIBLE`、`AFTER_SALE_QUANTITY_EXCEEDED`、`AFTER_SALE_AMOUNT_EXCEEDED`、`AFTER_SALE_NOT_CANCELLABLE`、`RETURN_SHIPMENT_NOT_ALLOWED`、`RETURN_SHIPMENT_ALREADY_SUBMITTED`。

## 7. 商家售后

接口使用 `SHOP(shop:after-sale:manage)`。

### 7.1 接口清单

| 方法 | 路径 | 幂等 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- | --- |
| GET | `/api/shops/{shopId}/after-sales` | 否 | `status,refundStatus,requestType,keyword,createdFrom,createdTo,page,pageSize` | `Page<ShopAfterSaleSummaryView>` |
| GET | `/api/shops/{shopId}/after-sales/{afterSaleId}` | 否 | 无 | `ShopAfterSaleDetailView` |
| POST | `/api/shops/{shopId}/after-sales/{afterSaleId}/approve` | 建议 | `ApproveAfterSaleRequest` | `ShopAfterSaleDetailView` |
| POST | `/api/shops/{shopId}/after-sales/{afterSaleId}/reject` | 建议 | `RejectAfterSaleRequest` | `ShopAfterSaleDetailView` |
| POST | `/api/shops/{shopId}/after-sales/{afterSaleId}/confirm-return-received` | 必填 | `ConfirmReturnReceivedRequest` | `ShopAfterSaleDetailView` |
| POST | `/api/shops/{shopId}/after-sales/{afterSaleId}/refund/retry` | 必填 | `RetryRefundRequest` | `ShopAfterSaleDetailView` |

### 7.2 审核

```json
// ApproveAfterSaleRequest
{
  "approvedQuantity": 1,
  "approvedAmount": "3999.00",
  "reviewComment": "同意申请",
  "version": 0
}
```

批准数量和金额必须不超过申请值，也不能与其他申请合计超额。服务端在锁内重新计算。仅退款批准会立即启动退款，响应可能是 `COMPLETED/SUCCESS` 或 `REFUNDING/FAILED`。

```json
// RejectAfterSaleRequest
{
  "reviewComment": "提交凭证无法证明商品问题",
  "version": 0
}
```

拒绝后 `approvedQuantity` 和 `approvedAmount` 仍为 `null`，符合数据库约束。

### 7.3 确认收货和退款重试

```json
// ConfirmReturnReceivedRequest
{
  "remark": "商品及配件已收到",
  "version": 2
}
```

只允许已提交完整物流的 `RETURN_REFUND/WAITING_RETURN`。服务端先在确认收货事务中写 `returnReceivedAt`，并以 `AFTER_SALE + afterSaleNo` 为业务键执行一次 `RETURN` 库存流水；然后执行钱包退款。若退款失败，状态为 `REFUNDING/FAILED`，已确认的实物库存保持回库，重试只处理钱包、钱包流水、订单明细累计退款和子订单累计退款，不得再次回库。

```json
// RetryRefundRequest
{
  "remark": "依赖恢复后手工重试",
  "version": 3
}
```

只允许 `REFUNDING/FAILED`。沿用原 `refundNo`，钱包流水业务键为 `AFTER_SALE_REFUND + refundNo`。成功后变为 `COMPLETED/SUCCESS`。若是未发货仅退款，成功事务还要幂等释放对应锁定库存；若是退货退款，库存已在确认收货时回库，重试不得再次增加库存。

错误：`AFTER_SALE_NOT_PENDING`、`AFTER_SALE_APPROVAL_EXCEEDED`、`RETURN_NOT_SHIPPED`、`RETURN_ALREADY_RECEIVED`、`REFUND_NOT_RETRYABLE`、`REFUND_EXECUTION_FAILED`。

二期启用售后后，一期的发货和确认收货接口增加活跃售后保护：`POST /api/shops/{shopId}/orders/{orderId}/ship`、`POST /api/orders/{orderId}/complete` 遇到本订单 `PENDING`、`WAITING_RETURN`、`REFUNDING` 售后时返回 `409 ORDER_HAS_ACTIVE_AFTER_SALE`。自动确认收货任务使用相同判断。

## 8. 平台运营只读查询

平台只读查询使用已写入权限种子的 `platform:operation:read`，由 `SUPER_ADMIN` 或自定义运营角色获得。接口不得复用 `platform:rbac:manage`。

| 方法 | 路径 | 查询 | 成功 `data` |
| --- | --- | --- | --- |
| GET | `/api/platform/operations/trades` | `tradeNo,userId,status,createdFrom,createdTo,page,pageSize` | `Page<OperationTradeView>` |
| GET | `/api/platform/operations/orders` | `orderNo,shopId,userId,orderStatus,paymentStatus,page,pageSize` | `Page<OperationOrderView>` |
| GET | `/api/platform/operations/payments` | `paymentNo,tradeNo,status,page,pageSize` | `Page<OperationPaymentView>` |
| GET | `/api/platform/operations/after-sales` | `afterSaleNo,shopId,userId,status,refundStatus,page,pageSize` | `Page<OperationAfterSaleView>` |
| GET | `/api/platform/operations/business/{businessType}/{businessNo}` | 无 | `BusinessTraceView` |

`BusinessTraceView` 聚合返回关联交易、子订单、支付、售后、库存流水和钱包流水的只读链接摘要，不允许通过该接口执行动作。列表手机号和地址脱敏；详情仅返回排障必要字段。

## 9. 运维任务内部接口

定时任务默认不暴露公网 HTTP。为测试和内管手工触发，可在非生产环境提供以下接口，并要求 `platform:task:execute`、内网访问和显式配置 `market.internal-task-api-enabled=true`：

| 方法 | 路径 | 请求 | 成功 `data` |
| --- | --- | --- | --- |
| POST | `/api/internal/tasks/cancel-expired-trades` | `TaskRunRequest` | `TaskRunView` |
| POST | `/api/internal/tasks/complete-shipped-orders` | `TaskRunRequest` | `TaskRunView` |
| POST | `/api/internal/tasks/retry-refunds` | `TaskRunRequest` | `TaskRunView` |
| POST | `/api/internal/tasks/reconcile-inventory` | `TaskRunRequest` | `TaskRunView` |
| POST | `/api/internal/tasks/reconcile-wallets` | `TaskRunRequest` | `TaskRunView` |

```json
// TaskRunRequest
{
  "dryRun": true,
  "batchSize": 100
}
```

`batchSize` 范围 `1..500`。对账接口 `dryRun` 固定为 `true`；本阶段不支持自动修复。生产环境未开启时返回 `404`，避免暴露管理面。

## 10. 二期权限种子补充

为使本文路径与数据库 RBAC 完全一致，`schema2.sql` 增量加入：

| 权限代码 | 作用域 | 资源 |
| --- | --- | --- |
| `platform:catalog:manage` | `PLATFORM` | `/api/platform/catalog/**` |
| `platform:operation:read` | `PLATFORM` | `/api/platform/operations/**` |
| `platform:task:execute` | `PLATFORM` | `/api/internal/tasks/**` |

`SUPER_ADMIN` 由 `schema2.sql` 显式补齐上述三项权限；`PLATFORM_PRODUCT_AUDITOR` 获得 `platform:catalog:manage`，以负责类目、类目属性模板和品牌基础资料。其余角色默认不获得新增权限，后续可由超级管理员在角色授权页面分配。

## 11. 二期错误码补充

| 模块 | 错误码 |
| --- | --- |
| RBAC | `ROLE_NOT_FOUND`、`ROLE_CODE_ALREADY_EXISTS`、`ROLE_SCOPE_MISMATCH`、`PERMISSION_SCOPE_MISMATCH`、`CANNOT_REMOVE_OWN_ADMIN_ACCESS`、`LAST_RBAC_ADMIN_REQUIRED` |
| 成员 | `SHOP_MEMBER_ALREADY_EXISTS`、`SHOP_MEMBER_NOT_FOUND`、`LAST_SHOP_ADMIN_REQUIRED` |
| 治理 | `PRODUCT_NOT_BANNABLE`、`PRODUCT_NOT_BANNED`、`PRODUCT_NOT_ON_SHELF` |
| 库存 | `INVENTORY_ADJUSTMENT_NEGATIVE_RESULT`、`INVENTORY_LOCKED_BELOW_RESERVATIONS` |
| 售后 | `AFTER_SALE_NOT_ELIGIBLE`、`AFTER_SALE_QUANTITY_EXCEEDED`、`AFTER_SALE_AMOUNT_EXCEEDED`、`AFTER_SALE_NOT_PENDING`、`AFTER_SALE_NOT_CANCELLABLE` |
| 退货退款 | `RETURN_SHIPMENT_NOT_ALLOWED`、`RETURN_SHIPMENT_ALREADY_SUBMITTED`、`RETURN_NOT_SHIPPED`、`REFUND_NOT_RETRYABLE`、`REFUND_EXECUTION_FAILED` |
| 订单保护 | `ORDER_HAS_ACTIVE_AFTER_SALE` |
| 内部任务 | `TASK_ALREADY_RUNNING`、`RECONCILIATION_MISMATCH_FOUND`；接口未开启时按不存在处理并返回 `RESOURCE_NOT_FOUND` |
