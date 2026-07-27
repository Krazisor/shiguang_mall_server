# 时光商城 API DTO 字段目录

## 1. 目的与记法

本文是[一期接口](phase-1-api.md)和[二期接口](phase-2-api.md)中所有命名 DTO 的字段目录。阶段文档中的 JSON 是实例，本文定义字段集合和类型；两者必须同时满足。

类型记法：

| 记法 | 含义 |
| --- | --- |
| `Id` | 十进制字符串形式的 `BIGINT` |
| `Money` | 两位小数字符串 |
| `Timestamp` | 带 `+08:00` 偏移的 ISO 8601 字符串 |
| `T?` | 请求可省略字段；只在 PATCH 中表示不修改 |
| `T \| null` | 字段始终存在，但值可以为 JSON `null` |
| `Enum<A,B>` | 只允许列出的稳定代码 |
| `Page<T>` | `{items:T[],page:int,pageSize:int,total:long,totalPages:int}` |

除请求字段标记为 `?` 外，响应字段均必须出现；没有值时按类型返回 `null`、空数组或 `0`，不得随机省略。

## 2. 公共嵌套类型

| DTO | 字段 |
| --- | --- |
| `UserSummary` | `id:Id`、`username:string`、`nickname:string`、`avatarUrl:string\|null`、`status:UserStatus` |
| `ShopSummary` | `id:Id`、`shopNo:string`、`shopName:string`、`logoUrl:string\|null`、`status:ShopStatus` |
| `AddressSnapshot` | `recipientName:string`、`recipientPhone:string`、`provinceName:string`、`cityName:string`、`districtName:string`、`detailAddress:string` |
| `AddressView` | `id:Id` + `AddressSnapshot` + `isDefault:boolean`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `BrandView` | `id:Id`、`brandCode:string`、`brandName:string`、`logoUrl:string\|null`、`status:EnabledStatus` |
| `CategoryBrief` | `id:Id`、`categoryCode:string`、`categoryName:string` |
| `OperatorBrief` | `id:Id`、`username:string`、`nickname:string` |
| `ShippingView` | `carrierCode:string`、`carrierName:string`、`trackingNo:string`、`shippedAt:Timestamp` |
| `Page<T>` | `items:T[]`、`page:int`、`pageSize:int`、`total:long`、`totalPages:int` |

`UserStatus`：`ACTIVE`、`DISABLED`、`LOCKED`。`ShopStatus`：`PENDING`、`ACTIVE`、`SUSPENDED`、`CLOSED`。`EnabledStatus`：`ENABLED`、`DISABLED`。

## 3. 认证、用户与地址

### 3.1 请求

| DTO | 字段 |
| --- | --- |
| `RegisterRequest` | `username:string`、`password:string`、`nickname:string`、`phone:string\|null`、`email:string\|null` |
| `LoginRequest` | `username:string`、`password:string` |
| `UpdateProfileRequest` | `nickname?:string`、`phone?:string\|null`、`email?:string\|null`、`avatarUrl?:string\|null`；至少一个字段 |
| `AddressUpsertRequest` | `recipientName:string`、`recipientPhone:string`、`provinceName:string`、`cityName:string`、`districtName:string`、`detailAddress:string`、`isDefault:boolean` |

### 3.2 响应

| DTO | 字段 |
| --- | --- |
| `LoginView` | `tokenName:string`、`tokenValue:string`、`expiresInSeconds:long`、`activeTimeoutSeconds:long`、`user:UserSummary` |
| `ShopContextView` | `shop:ShopSummary`、`roleCode:string`、`permissions:string[]` |
| `CurrentUserView` | `user:UserSummary`、`phone:string\|null`、`email:string\|null`、`platformRoles:string[]`、`platformPermissions:string[]`、`shops:ShopContextView[]` |

## 4. 目录与公开商品

| DTO | 字段 |
| --- | --- |
| `CategoryNode` | `id:Id`、`parentId:Id\|null`、`categoryCode:string`、`categoryName:string`、`sortOrder:int`、`leaf:boolean`、`children:CategoryNode[]` |
| `CategoryAttributeView` | `id:Id`、`categoryId:Id`、`attributeName:string`、`valueType:AttributeValueType`、`unit:string\|null`、`required:boolean`、`filterable:boolean`、`options:string[]\|null`、`sortOrder:int`、`status:EnabledStatus` |
| `PublicShopView` | `shop:ShopSummary`、`description:string\|null`、`contactName:string\|null`、`contactPhone:string\|null`；只有 `ACTIVE` 店铺可从公开接口获得 |
| `ProductCardView` | `id:Id`、`spuNo:string`、`productName:string`、`subtitle:string\|null`、`coverUrl:string\|null`、`shop:ShopSummary`、`categoryId:Id`、`brand:BrandView\|null`、`minimumSalePrice:Money`、`maximumSalePrice:Money`、`inStock:boolean` |
| `ProductAttributeDisplayView` | `attributeId:Id`、`attributeName:string`、`value:string`、`unit:string\|null` |
| `PublicSkuView` | `id:Id`、`skuNo:string`、`skuName:string`、`spec:object<string,string>`、`salePrice:Money`、`marketPrice:Money\|null`、`imageUrl:string\|null`、`status:EnabledStatus`、`availableQuantity:int`、`inStock:boolean`、`purchasable:boolean`、`unavailableReason:string\|null` |
| `ProductDetailView` | `id:Id`、`spuNo:string`、`productName:string`、`subtitle:string\|null`、`coverUrl:string\|null`、`galleryUrls:string[]`、`detailHtml:string\|null`、`packingList:string\|null`、`serviceNote:string\|null`、`shop:ShopSummary`、`category:CategoryBrief`、`brand:BrandView\|null`、`attributes:ProductAttributeDisplayView[]`、`skus:PublicSkuView[]` |

`AttributeValueType`：`TEXT`、`NUMBER`、`BOOLEAN`、`OPTION`。

## 5. 购物车与结算

### 5.1 请求

| DTO | 字段 |
| --- | --- |
| `AddCartItemRequest` | `skuId:Id`、`quantity:int` |
| `UpdateCartItemRequest` | `quantity?:int`、`selected?:boolean`；至少一个字段 |
| `UpdateCartSelectionRequest` | `cartItemIds:Id[]`、`selected:boolean` |
| `CheckoutPreviewRequest` | `cartItemIds?:Id[]`、`addressId:Id\|null`、`shopRemarks:object<Id,string>` |
| `CreateTradeRequest` | 与 `CheckoutPreviewRequest` 相同，但 `addressId:Id` 必填且 `cartItemIds` 缺失时使用已选择项 |

### 5.2 响应

| DTO | 字段 |
| --- | --- |
| `CartItemView` | `id:Id`、`skuId:Id`、`spuId:Id`、`productName:string`、`skuName:string`、`spec:object<string,string>`、`imageUrl:string\|null`、`quantity:int`、`selected:boolean`、`currentSalePrice:Money`、`availableQuantity:int`、`valid:boolean`、`invalidReason:string\|null`、`updatedAt:Timestamp` |
| `CartShopGroupView` | `shop:ShopSummary`、`items:CartItemView[]` |
| `CartView` | `shops:CartShopGroupView[]`、`selectedItemCount:int`、`selectedQuantity:int`、`selectedAmount:Money` |
| `CheckoutItemView` | `cartItemId:Id`、`skuId:Id`、`productName:string`、`skuName:string`、`unitPrice:Money`、`quantity:int`、`originalAmount:Money`、`freightAmount:Money`、`payableAmount:Money`、`valid:boolean`、`invalidReason:string\|null` |
| `CheckoutShopGroupView` | `shop:ShopSummary`、`items:CheckoutItemView[]`、`itemAmount:Money`、`freightAmount:Money`、`payableAmount:Money`、`buyerRemark:string\|null` |
| `InvalidCheckoutItemView` | `cartItemId:Id`、`skuId:Id`、`reason:string`、`message:string` |
| `CheckoutPreviewView` | `address:AddressView\|null`、`shops:CheckoutShopGroupView[]`、`itemAmount:Money`、`freightAmount:Money`、`payableAmount:Money`、`submittable:boolean`、`invalidItems:InvalidCheckoutItemView[]` |

## 6. 交易、钱包、支付与订单

### 6.1 请求

| DTO | 字段 |
| --- | --- |
| `CancelTradeRequest` | `reason:string` |
| `RechargeRequest` | `amount:Money`、`remark:string\|null` |
| `CreatePaymentRequest` | `paymentMethod:Enum<WALLET>` |
| `ShipOrderRequest` | `carrierCode:string`、`carrierName:string`、`trackingNo:string` |
| `ReasonRequest` | `reason:string` |

### 6.2 响应

| DTO | 字段 |
| --- | --- |
| `WalletView` | `walletId:Id`、`balance:Money`、`status:WalletStatus`、`version:int`、`updatedAt:Timestamp` |
| `WalletTransactionView` | `id:Id`、`transactionNo:string`、`transactionType:WalletTransactionType`、`direction:TransactionDirection`、`amount:Money`、`balanceBefore:Money`、`balanceAfter:Money`、`businessType:string`、`businessNo:string`、`remark:string\|null`、`createdAt:Timestamp` |
| `WalletOperationView` | `WalletTransactionView` 去除 `id`、`businessType`、`businessNo` 后的字段集合；一期充值仍返回 `transactionNo` |
| `PaymentView` | `id:Id`、`paymentNo:string`、`tradeId:Id`、`amount:Money`、`status:PaymentOrderStatus`、`failureReason:string\|null`、`paidAt:Timestamp\|null`、`expiresAt:Timestamp`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `PaymentResultView` | `paymentId:Id`、`paymentNo:string`、`status:PaymentOrderStatus`、`amount:Money`、`paidAt:Timestamp\|null`、`tradeId:Id`、`tradeStatus:TradeStatus`、`walletBalance:Money` |
| `OrderItemSummaryView` | `productName:string`、`skuName:string`、`imageUrl:string\|null`、`quantity:int` |
| `OrderSummaryView` | `id:Id`、`orderNo:string`、`tradeId:Id`、`tradeNo:string`、`shop:ShopSummary`、`orderStatus:OrderStatus`、`paymentStatus:OrderPaymentStatus`、`payableAmount:Money`、`refundAmount:Money`、`itemSummary:OrderItemSummaryView[]`、`itemKinds:int`、`totalQuantity:int`、`createdAt:Timestamp`、`availableActions:string[]` |
| `OrderItemView` | `id:Id`、`spuId:Id`、`skuId:Id`、`spuNo:string`、`skuNo:string`、`productName:string`、`skuName:string`、`spec:object<string,string>`、`imageUrl:string\|null`、`unitPrice:Money`、`quantity:int`、`originalAmount:Money`、`freightAmount:Money`、`payableAmount:Money`、`refundedQuantity:int`、`refundedAmount:Money`、`reservationStatus:ReservationStatus` |
| `OrderStatusHistoryView` | `fromStatus:OrderStatus\|null`、`toStatus:OrderStatus`、`operationType:OrderOperationType`、`operatorType:OperatorType`、`remark:string\|null`、`createdAt:Timestamp` |
| `OrderDetailView` | `id:Id`、`orderNo:string`、`tradeId:Id`、`tradeNo:string`、`shop:ShopSummary`、`orderStatus:OrderStatus`、`paymentStatus:OrderPaymentStatus`、`itemAmount:Money`、`freightAmount:Money`、`payableAmount:Money`、`refundAmount:Money`、`buyerRemark:string\|null`、`address:AddressSnapshot`、`shipping:ShippingView\|null`、`items:OrderItemView[]`、`history:OrderStatusHistoryView[]`、`availableActions:string[]` |
| `TradeDetailView` | `id:Id`、`tradeNo:string`、`tradeStatus:TradeStatus`、`payableAmount:Money`、`address:AddressSnapshot`、`payExpireAt:Timestamp`、`paidAt:Timestamp\|null`、`cancelledAt:Timestamp\|null`、`orders:OrderSummaryView[]`、`availableActions:string[]` |
| `ShopOrderSummaryView` | `OrderSummaryView` + `buyer:UserSummary`；手机号不在列表返回 |

枚举取值以数据库文档为准：`WalletStatus`、`WalletTransactionType`、`TransactionDirection`、`PaymentOrderStatus`、`TradeStatus`、`OrderStatus`、`OrderPaymentStatus`、`ReservationStatus`、`OrderOperationType`、`OperatorType`。

## 7. 商家商品与库存

### 7.1 请求

| DTO | 字段 |
| --- | --- |
| `ProductAttributeInput` | `attributeId:Id`、`value:string` |
| `SkuCreateInput` | `skuName:string`、`spec:object<string,string>`、`salePrice:Money`、`marketPrice:Money\|null`、`barcode:string\|null`、`imageUrl:string\|null` |
| `CreateProductRequest` | `categoryId:Id`、`brandId:Id\|null`、`productName:string`、`subtitle:string\|null`、`coverUrl:string\|null`、`galleryUrls:string[]`、`detailHtml:string\|null`、`packingList:string\|null`、`serviceNote:string\|null`、`attributes:ProductAttributeInput[]`、`skus:SkuCreateInput[]` |
| `SkuContentInput` | `skuId:Id`、`skuName:string`、`imageUrl:string\|null`、`version:int` |
| `UpdateProductContentRequest` | `CreateProductRequest` 去除 `skus`，增加 `contentVersion:int`、`skuContents:SkuContentInput[]` |
| `CreateSkuRequest` | `SkuCreateInput` + `contentVersion:int` |
| `UpdateSkuRequest` | `salePrice?:Money`、`marketPrice?:Money\|null`、`barcode?:string\|null`、`status?:EnabledStatus`、`version:int`；至少一个可选业务字段 |
| `InventoryInboundRequest` | `quantity:int`、`remark:string\|null` |
| `InventoryAdjustmentRequest` | `availableChange:int`、`lockedChange:int`、`version:int`、`reason:string` |

### 7.2 响应

| DTO | 字段 |
| --- | --- |
| `StockView` | `skuId:Id`、`availableQuantity:int`、`lockedQuantity:int`、`version:int`、`updatedAt:Timestamp` |
| `ShopSkuView` | `id:Id`、`skuNo:string`、`skuName:string`、`spec:object<string,string>`、`salePrice:Money`、`marketPrice:Money\|null`、`barcode:string\|null`、`imageUrl:string\|null`、`status:EnabledStatus`、`version:int`、`stock:StockView`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `ProductStatusHistoryView` | `id:Id`、`spuId:Id`、`fromStatus:ProductStatus\|null`、`toStatus:ProductStatus`、`operationType:ProductOperationType`、`contentVersion:int`、`operatorType:OperatorType`、`operator:OperatorBrief\|null`、`reason:string\|null`、`createdAt:Timestamp` |
| `ShopProductSummaryView` | `id:Id`、`spuNo:string`、`productName:string`、`coverUrl:string\|null`、`category:CategoryBrief`、`brand:BrandView\|null`、`status:ProductStatus`、`contentVersion:int`、`skuCount:int`、`enabledSkuCount:int`、`availableQuantity:int`、`lockedQuantity:int`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `ShopProductDetailView` | `ProductDetailView` 的全部内容 + `status:ProductStatus`、`contentVersion:int`、`createdBy:UserSummary`、`updatedBy:UserSummary`、`skus:ShopSkuView[]`、`history:ProductStatusHistoryView[]`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `InventoryItemView` | `spuId:Id`、`spuNo:string`、`productName:string`、`sku:ShopSkuView` |
| `InventoryOperationView` | `transactionNo:string`、`skuId:Id`、`transactionType:InventoryTransactionType`、`availableChange:int`、`lockedChange:int`、`availableAfter:int`、`lockedAfter:int`、`businessType:string`、`businessNo:string`、`remark:string\|null`、`createdAt:Timestamp` |
| `InventoryTransactionView` | `id:Id` + `InventoryOperationView` 全部字段 + `availableBefore:int`、`lockedBefore:int`、`operator:OperatorBrief\|null` |

## 8. 平台店铺与目录

### 8.1 请求

| DTO | 字段 |
| --- | --- |
| `CreateShopRequest` | `shopName:string`、`logoUrl:string\|null`、`description:string\|null`、`contactName:string\|null`、`contactPhone:string\|null`、`adminUsername:string` |
| `UpdateShopRequest` | `shopName:string`、`logoUrl:string\|null`、`description:string\|null`、`contactName:string\|null`、`contactPhone:string\|null` |
| `ChangeShopStatusRequest` | `targetStatus:ShopStatus`、`reason:string` |
| `CategoryUpsertRequest` | `parentId:Id\|null`、`categoryName:string`、`categoryCode:string`、`sortOrder:int` |
| `CategoryAttributeRequest` | `attributeName:string`、`valueType:AttributeValueType`、`unit:string\|null`、`required:boolean`、`filterable:boolean`、`options:string[]\|null`、`sortOrder:int` |
| `BrandRequest` | `brandName:string`、`brandCode:string`、`logoUrl:string\|null` |
| `StatusRequest` / `StatusRequest<T>` | `targetStatus:T`、`reason:string\|null`；具体接口限定 `T` |
| `ReviewDecisionRequest` | `contentVersion:int`、`reason:string\|null`；拒绝时非空 |
| `ProductGovernanceRequest` | `contentVersion:int`、`reason:string` |

### 8.2 响应

| DTO | 字段 |
| --- | --- |
| `PlatformShopView` | `shop:ShopSummary`、`description:string\|null`、`contactName:string\|null`、`contactPhone:string\|null`、`membersCount:int`、`activeMembersCount:int`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `PlatformCategoryView` | `id:Id`、`parentId:Id\|null`、`categoryCode:string`、`categoryName:string`、`sortOrder:int`、`status:EnabledStatus`、`leaf:boolean`、`childrenCount:int`、`attributeCount:int`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `PlatformCategoryNode` | `PlatformCategoryView` + `children:PlatformCategoryNode[]` |
| `ProductReviewSummaryView` | `spuId:Id`、`spuNo:string`、`productName:string`、`coverUrl:string\|null`、`shop:ShopSummary`、`category:CategoryBrief`、`contentVersion:int`、`submittedAt:Timestamp` |
| `ProductReviewDetailView` | `ShopProductDetailView` 的受审核内容、SKU、状态与历史；不返回库存操作能力字段 `availableActions` |

## 9. RBAC 与店铺成员

### 9.1 请求

| DTO | 字段 |
| --- | --- |
| `ChangeUserStatusRequest` | `targetStatus:UserStatus`、`reason:string` |
| `AssignPlatformRolesRequest` | `roleIds:Id[]` |
| `CreateRoleRequest` | `roleCode:string`、`roleName:string`、`scopeType:ScopeType`、`description:string\|null`、`permissionIds:Id[]` |
| `UpdateRoleRequest` | `roleName:string`、`description:string\|null` |
| `AssignPermissionsRequest` | `permissionIds:Id[]` |
| `AddShopMemberRequest` | `username:string`、`roleId:Id` |
| `ChangeShopMemberRoleRequest` | `roleId:Id` |

### 9.2 响应

| DTO | 字段 |
| --- | --- |
| `RoleView` | `id:Id`、`roleCode:string`、`roleName:string`、`scopeType:ScopeType`、`description:string\|null`、`status:ActiveStatus`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `PermissionView` | `id:Id`、`permissionCode:string`、`permissionName:string`、`scopeType:ScopeType`、`resource:string\|null`、`httpMethod:string\|null`、`status:ActiveStatus` |
| `RoleDetailView` | `RoleView` + `permissions:PermissionView[]` |
| `PlatformUserView` | `id:Id`、`username:string`、`nickname:string`、`phoneMasked:string\|null`、`emailMasked:string\|null`、`status:UserStatus`、`platformRoles:RoleView[]`、`lastLoginAt:Timestamp\|null`、`createdAt:Timestamp` |
| `PlatformUserDetailView` | `PlatformUserView` + `avatarUrl:string\|null`、`updatedAt:Timestamp`；联系方式仍使用脱敏字段 |
| `ShopMemberView` | `shopId:Id`、`user:UserSummary`、`role:RoleView`、`status:ActiveStatus`、`createdAt:Timestamp`、`updatedAt:Timestamp` |

`ScopeType`：`PLATFORM`、`SHOP`。`ActiveStatus`：`ACTIVE`、`DISABLED`。

## 10. 售后

### 10.1 请求

| DTO | 字段 |
| --- | --- |
| `CreateAfterSaleRequest` | `orderId:Id`、`orderItemId:Id`、`requestType:AfterSaleType`、`quantity:int`、`reasonCode:string`、`reasonDescription:string\|null`、`evidenceUrls:string[]`、`requestedAmount:Money` |
| `ReturnShipmentRequest` | `carrierCode:string`、`carrierName:string`、`trackingNo:string` |
| `UpdateReturnShipmentRequest` | `ReturnShipmentRequest` + `version:int` |
| `ApproveAfterSaleRequest` | `approvedQuantity:int`、`approvedAmount:Money`、`reviewComment:string\|null`、`version:int` |
| `RejectAfterSaleRequest` | `reviewComment:string`、`version:int` |
| `ConfirmReturnReceivedRequest` | `remark:string\|null`、`version:int` |
| `RetryRefundRequest` | `remark:string`、`version:int` |

### 10.2 响应

| DTO | 字段 |
| --- | --- |
| `AfterSaleEligibilityView` | `orderId:Id`、`orderItemId:Id`、`orderStatus:OrderStatus`、`purchasedQuantity:int`、`refundedQuantity:int`、`occupiedQuantity:int`、`maximumRequestQuantity:int`、`itemPayableAmount:Money`、`refundedAmount:Money`、`occupiedAmount:Money`、`maximumRequestAmount:Money`、`supportedTypes:AfterSaleType[]`、`eligibleUntil:Timestamp\|null`、`eligible:boolean`、`ineligibleReason:string\|null` |
| `AfterSaleOrderBrief` | `id:Id`、`orderNo:string`、`orderStatus:OrderStatus` |
| `AfterSaleItemView` | `id:Id`、`productName:string`、`skuName:string`、`spec:object<string,string>`、`imageUrl:string\|null`、`unitPrice:Money`、`purchasedQuantity:int` |
| `AfterSaleReviewView` | `reviewerId:Id`、`comment:string\|null`、`reviewedAt:Timestamp` |
| `ReturnShipmentView` | `carrierCode:string`、`carrierName:string`、`trackingNo:string`、`returnedAt:Timestamp`、`receivedAt:Timestamp\|null` |
| `AfterSaleSummaryView` | `id:Id`、`afterSaleNo:string`、`requestType:AfterSaleType`、`status:AfterSaleStatus`、`refundStatus:RefundStatus`、`order:AfterSaleOrderBrief`、`shop:ShopSummary`、`item:AfterSaleItemView`、`quantity:int`、`requestedAmount:Money`、`approvedAmount:Money\|null`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `AfterSaleDetailView` | `AfterSaleSummaryView` + `reasonCode:string`、`reasonDescription:string\|null`、`evidenceUrls:string[]`、`approvedQuantity:int\|null`、`review:AfterSaleReviewView\|null`、`returnShipment:ReturnShipmentView\|null`、`refundNo:string\|null`、`refundFailureReason:string\|null`、`refundedAt:Timestamp\|null`、`completedAt:Timestamp\|null`、`cancelledAt:Timestamp\|null`、`version:int`、`availableActions:string[]` |
| `ShopAfterSaleSummaryView` | `AfterSaleSummaryView` + `buyer:UserSummary` |
| `ShopAfterSaleDetailView` | `AfterSaleDetailView` + `buyer:UserSummary`、`eligibilityAtReview:AfterSaleEligibilityView` |

`AfterSaleType`：`REFUND_ONLY`、`RETURN_REFUND`。`AfterSaleStatus` 和 `RefundStatus` 取值以数据库设计为准。

## 11. 平台运营与内部任务

| DTO | 字段 |
| --- | --- |
| `OperationTradeView` | `id:Id`、`tradeNo:string`、`user:UserSummary`、`tradeStatus:TradeStatus`、`payableAmount:Money`、`orderCount:int`、`createdAt:Timestamp` |
| `OperationOrderView` | `OrderSummaryView` + `buyer:UserSummary` |
| `OperationPaymentView` | `PaymentView` + `tradeNo:string`、`user:UserSummary` |
| `OperationAfterSaleView` | `AfterSaleSummaryView` + `buyer:UserSummary` |
| `BusinessTraceLink` | `resourceType:string`、`resourceId:Id`、`businessNo:string`、`status:string`、`createdAt:Timestamp` |
| `BusinessTraceView` | `businessType:string`、`businessNo:string`、`trade:BusinessTraceLink\|null`、`orders:BusinessTraceLink[]`、`payments:BusinessTraceLink[]`、`afterSales:BusinessTraceLink[]`、`inventoryTransactions:BusinessTraceLink[]`、`walletTransactions:BusinessTraceLink[]` |
| `TaskRunRequest` | `dryRun:boolean`、`batchSize:int` |
| `TaskRunView` | `taskName:string`、`dryRun:boolean`、`scanned:int`、`processed:int`、`succeeded:int`、`failed:int`、`mismatches:int`、`startedAt:Timestamp`、`finishedAt:Timestamp`、`requestId:string` |

平台运营列表中的联系方式均按接口文档脱敏。`BusinessTraceView` 只提供关联摘要，前端需要详情时再调用对应受权限保护的详情接口。

## 12. DTO 变更检查

### 12.1 常用字段校验矩阵

| 字段 | 约束 |
| --- | --- |
| `username` | `^[A-Za-z][A-Za-z0-9_]{3,63}$`，创建后不可修改 |
| `password` | 8..72 字符，至少一个字母和一个数字 |
| `nickname` | 去除首尾空白后 1..64 字符 |
| `phone` / `recipientPhone` | 6..32 字符；账号手机号非空时全局唯一 |
| `email` | 符合邮箱格式，最大 128 字符，非空时全局唯一 |
| `avatarUrl` / 图片 URL | 最大 1024 字符，遵循统一 URL 安全规则 |
| `shopName` | 1..128 字符；`description` 最大 500 |
| `contactName` | 最大 64；`contactPhone` 最大 32 |
| `categoryName` | 1..64；`categoryCode` 符合 `^[A-Z][A-Z0-9_]{1,63}$` |
| `brandName` | 1..128；`brandCode` 符合 `^[A-Z][A-Z0-9_]{1,63}$` |
| `productName` | 1..255；`subtitle` 最大 500 |
| `galleryUrls` | 最多 10 个唯一 URL，保持请求顺序 |
| `detailHtml` | 清洗后最大 1,000,000 个字符 |
| `packingList` / `serviceNote` | 各最大 65,535 个字符 |
| `skuName` | 1..255；`barcode` 最大 64 |
| `spec` | 1..10 个键；键和值去除首尾空白后各 1..64；键不可重复 |
| `buyerRemark` | 每店最大 500 字符 |
| `reason` / `reviewComment` | 按接口要求必填时 1..500 字符 |
| `carrierCode` | 1..64；`carrierName` 1..128；`trackingNo` 1..128 |
| `evidenceUrls` | 最多 9 个唯一 URL |
| `roleName` | 1..64；`description` 最大 255 |

后端先去除规定字段的首尾空白再做长度校验；保留商品详情 HTML 和买家/审核说明内部的换行。数组超过上限、含重复项或 URL 非法统一返回 `VALIDATION_FAILED`。

### 12.2 变更检查

每次修改 DTO 时必须检查：

1. 本文、阶段接口示例和 OpenAPI 是否同步。
2. ID、金额和时间是否仍符合统一类型。
3. 新增响应字段是否有稳定默认值或空值规则。
4. Request 是否明确 POST/PUT/PATCH 的缺失与 `null` 语义。
5. 前端类型、Mock、后端 DTO、Controller 契约测试是否同步。
