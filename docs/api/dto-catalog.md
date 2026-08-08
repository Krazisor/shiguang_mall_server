# 时光商城 API DTO 字段目录

## 1. 目的与记法

本文是[基础交易接口分册](phase-1-api.md)、[治理与售后接口分册](phase-2-api.md)和[三期接口设计](phase-3-api.md)中所有命名 DTO 的字段目录。分册文档中的 JSON 是实例，本文定义字段集合和类型；两者必须同时满足。三期商家钱包 DTO 和对象存储上传 DTO 均已纳入当前实现。

类型记法：

| 记法 | 含义 |
| --- | --- |
| `Id` | 十进制字符串形式的 `BIGINT` |
| `Money` | 两位小数字符串 |
| `Timestamp` | 带 `+08:00` 偏移的 ISO 8601 字符串 |
| `field:T` | 请求字段必须出现且值不可为 JSON `null`；响应字段固定出现 |
| `field?:T` | 请求字段可以省略；出现时值不可为 JSON `null` |
| `field:T \| null` | 字段必须出现，但值可以为 JSON `null` |
| `field?:T \| null` | 请求字段可以省略；出现时也可以为 JSON `null` |
| `Enum<A,B>` | 只允许列出的稳定代码 |
| `Page<T>` | `{items:T[],page:int,pageSize:int,total:long,totalPages:int}` |

请求字段没有 `?` 时必须提交；请求 JSON 出现 DTO 未定义字段时返回 `400 BAD_REQUEST`。PATCH 请求采用三态语义：字段缺失表示“不修改”，显式 `null` 只用于清空标记为可空的字段，对不可空字段提交 `null` 返回 `400 VALIDATION_FAILED`。POST/PUT 中可省略字段的默认或清空行为由对应 DTO 约束说明定义。

响应字段均必须出现；没有值时按类型返回 `null`、空数组或 `0`，不得随机省略。字段长度均指后端按约定完成 `trim` 或内容清洗后的长度；ID 格式错误统一返回 `400 BAD_REQUEST`，其他字段格式或组合约束错误返回 `400 VALIDATION_FAILED`，除非场景文档明确给出业务错误码。

## 2. 公共嵌套类型

| DTO | 字段 |
| --- | --- |
| `UserSummary` | `id:Id`、`username:string`、`nickname:string`、`avatarUrl:string\|null`、`status:UserStatus` |
| `ShopSummary` | `id:Id`、`shopNo:string`、`shopName:string`、`logoUrl:string\|null`、`status:ShopStatus` |
| `AddressSnapshot` | `recipientName:string`、`recipientPhone:string`、`provinceName:string`、`cityName:string`、`districtName:string`、`detailAddress:string` |
| `AddressView` | `id:Id`、`recipientName:string`、`recipientPhone:string`、`provinceName:string`、`cityName:string`、`districtName:string`、`detailAddress:string`、`isDefault:boolean`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `BrandView` | `id:Id`、`brandCode:string`、`brandName:string`、`logoUrl:string\|null`、`status:EnabledStatus` |
| `CategoryBrief` | `id:Id`、`categoryCode:string`、`categoryName:string` |
| `OperatorBrief` | `id:Id`、`username:string`、`nickname:string` |
| `ShippingView` | `carrierCode:string`、`carrierName:string`、`trackingNo:string`、`shippedAt:Timestamp` |
| `Page<T>` | `items:T[]`、`page:int`、`pageSize:int`、`total:long`、`totalPages:int` |

`UserStatus`：`ACTIVE`、`DISABLED`、`LOCKED`。`ShopStatus`：`PENDING`、`ACTIVE`、`SUSPENDED`、`CLOSED`。`EnabledStatus`：`ENABLED`、`DISABLED`。

## 3. 认证、用户与地址

### 3.1 请求

| DTO | 字段 | 约束 |
| --- | --- | --- |
| `RegisterRequest` | `username:string`、`password:string`、`nickname:string`、`phone?:string\|null`、`email?:string\|null` | `username` 符合 `^[A-Za-z][A-Za-z0-9_]{3,63}$`；`password` 为 8..72 字符且至少含一个字母和一个数字；`nickname` 为 1..64；`phone` 非空时为 6..32；`email` 非空时为合法邮箱且最大 128 |
| `LoginRequest` | `username:string`、`password:string` | 两个字段均为非空白字符串 |
| `UpdateProfileRequest` | `nickname?:string`、`phone?:string\|null`、`email?:string\|null`、`avatarUrl?:string\|null` | 至少提交一个字段；`nickname` 出现时不可为 `null`，长度 1..64；`phone` 和 `email` 的非空约束同注册；`avatarUrl` 遵循图片 URL 安全规则；后三项显式 `null` 或空白值均清空 |
| `AddressUpsertRequest` | `recipientName:string`、`recipientPhone:string`、`provinceName:string`、`cityName:string`、`districtName:string`、`detailAddress:string`、`isDefault:boolean` | 字符串均非空白；收件人最大 64，电话 6..32，省/市/区各最大 64，详细地址最大 255；PUT 为完整替换，所有字段必须出现 |

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

| DTO | 字段 | 约束 |
| --- | --- | --- |
| `AddCartItemRequest` | `skuId:Id`、`quantity:int` | `quantity` 为 1..999；重复加购后总数量也不得超过 999 |
| `UpdateCartItemRequest` | `quantity?:int`、`selected?:boolean` | 至少提交一个字段；两个字段出现时均不可为 `null`；`quantity` 为 1..999 |
| `UpdateCartSelectionRequest` | `cartItemIds:Id[]`、`selected:boolean` | `cartItemIds` 必须出现且不可重复；空数组表示不修改；`selected` 不可为 `null` |
| `CheckoutPreviewRequest` | `cartItemIds?:Id[]`、`addressId?:Id\|null`、`shopRemarks?:object<Id,string>\|null` | `cartItemIds` 缺失时使用已选择项，显式数组必须非空且去重；`addressId` 缺失或为 `null` 时尝试默认地址，无默认地址则响应 `address=null`；备注对象键为店铺 ID 字符串，每个实际结算店铺的备注最大 500 字符 |
| `CreateTradeRequest` | `cartItemIds?:Id[]`、`addressId:Id`、`shopRemarks?:object<Id,string>\|null` | `addressId` 非空；`cartItemIds` 缺失时使用已选择项，显式数组必须非空且去重；备注对象键为店铺 ID 字符串、每店最大 500 字符；任一结算项无效时返回 `422 CHECKOUT_ITEMS_INVALID` |

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

| DTO | 字段 | 约束 |
| --- | --- | --- |
| `CancelTradeRequest` | `reason:string` | 非空白，1..255 字符 |
| `RechargeRequest` | `amount:Money`、`remark?:string\|null` | `amount` 为 `0.01..100000.00`；`remark` 最大 500 字符 |
| `CreatePaymentRequest` | `paymentMethod:Enum<WALLET>` | 当前只接受稳定代码 `WALLET`，前端不提交金额 |
| `ShipOrderRequest` | `carrierCode:string`、`carrierName:string`、`trackingNo:string` | 均为非空白字符串；长度依次为 1..64、1..128、1..128 |
| `ReasonRequest` | `reason?:string\|null` | 仅用于可省略请求体的商品下架接口；整个请求体可省略，提供请求体时 `reason` 仍可省略或为 `null`，非空值长度 1..500 |

### 6.2 响应

| DTO | 字段 |
| --- | --- |
| `WalletView` | `walletId:Id`、`balance:Money`、`status:WalletStatus`、`version:int`、`updatedAt:Timestamp` |
| `WalletTransactionView` | `id:Id`、`transactionNo:string`、`transactionType:WalletTransactionType`、`direction:TransactionDirection`、`amount:Money`、`balanceBefore:Money`、`balanceAfter:Money`、`businessType:string`、`businessNo:string`、`remark:string\|null`、`createdAt:Timestamp` |
| `WalletOperationView` | `transactionNo:string`、`transactionType:WalletTransactionType`、`direction:TransactionDirection`、`amount:Money`、`balanceBefore:Money`、`balanceAfter:Money`、`createdAt:Timestamp` |
| `PaymentView` | `id:Id`、`paymentNo:string`、`tradeId:Id`、`amount:Money`、`status:PaymentOrderStatus`、`failureReason:string\|null`、`paidAt:Timestamp\|null`、`expiresAt:Timestamp`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `PaymentResultView` | `paymentId:Id`、`paymentNo:string`、`status:PaymentOrderStatus`、`amount:Money`、`paidAt:Timestamp\|null`、`tradeId:Id`、`tradeStatus:TradeStatus`、`walletBalance:Money` |
| `OrderItemSummaryView` | `productName:string`、`skuName:string`、`imageUrl:string\|null`、`quantity:int` |
| `OrderSummaryView` | `id:Id`、`orderNo:string`、`tradeId:Id`、`tradeNo:string`、`shop:ShopSummary`、`orderStatus:OrderStatus`、`paymentStatus:OrderPaymentStatus`、`payableAmount:Money`、`refundAmount:Money`、`itemSummary:OrderItemSummaryView[]`、`itemKinds:int`、`totalQuantity:int`、`createdAt:Timestamp`、`availableActions:string[]` |
| `OrderItemView` | `id:Id`、`spuId:Id`、`skuId:Id`、`spuNo:string`、`skuNo:string`、`productName:string`、`skuName:string`、`spec:object<string,string>`、`imageUrl:string\|null`、`unitPrice:Money`、`quantity:int`、`originalAmount:Money`、`freightAmount:Money`、`payableAmount:Money`、`refundedQuantity:int`、`refundedAmount:Money`、`reservationStatus:ReservationStatus` |
| `OrderStatusHistoryView` | `fromStatus:OrderStatus\|null`、`toStatus:OrderStatus`、`operationType:OrderOperationType`、`operatorType:OperatorType`、`remark:string\|null`、`createdAt:Timestamp` |
| `OrderDetailView` | `id:Id`、`orderNo:string`、`tradeId:Id`、`tradeNo:string`、`shop:ShopSummary`、`orderStatus:OrderStatus`、`paymentStatus:OrderPaymentStatus`、`itemAmount:Money`、`freightAmount:Money`、`payableAmount:Money`、`refundAmount:Money`、`buyerRemark:string\|null`、`address:AddressSnapshot`、`shipping:ShippingView\|null`、`items:OrderItemView[]`、`history:OrderStatusHistoryView[]`、`availableActions:string[]` |
| `TradeDetailView` | `id:Id`、`tradeNo:string`、`tradeStatus:TradeStatus`、`payableAmount:Money`、`address:AddressSnapshot`、`payExpireAt:Timestamp`、`paidAt:Timestamp\|null`、`cancelledAt:Timestamp\|null`、`orders:OrderSummaryView[]`、`availableActions:string[]` |
| `ShopOrderSummaryView` | `id:Id`、`orderNo:string`、`tradeId:Id`、`tradeNo:string`、`shop:ShopSummary`、`orderStatus:OrderStatus`、`paymentStatus:OrderPaymentStatus`、`payableAmount:Money`、`refundAmount:Money`、`itemSummary:OrderItemSummaryView[]`、`itemKinds:int`、`totalQuantity:int`、`createdAt:Timestamp`、`availableActions:string[]`、`buyer:UserSummary`；手机号不在列表返回 |

枚举取值以数据库文档为准：`WalletStatus`、`WalletTransactionType`、`TransactionDirection`、`PaymentOrderStatus`、`TradeStatus`、`OrderStatus`、`OrderPaymentStatus`、`ReservationStatus`、`OrderOperationType`、`OperatorType`。

## 7. 商家商品与库存

### 7.1 请求

| DTO | 字段 | 约束 |
| --- | --- | --- |
| `ProductAttributeInput` | `attributeId:Id`、`value:string` | `value` 非空白且最大 1000；同一请求内 `attributeId` 不可重复，并且必须属于所选类目的启用属性模板 |
| `SkuCreateInput` | `skuName:string`、`spec:object<string,string>`、`salePrice:Money`、`marketPrice?:Money\|null`、`barcode?:string\|null`、`imageUrl?:string\|null` | `skuName` 为 1..255；`spec` 为 1..10 项，键和值经 trim + NFC 后各为 1..64；`salePrice>0`；`marketPrice` 非空时必须 `>=salePrice`；`barcode` 最大 64，空白归一化为 `null`；`imageUrl` 遵循图片 URL 安全规则 |
| `CreateProductRequest` | `categoryId:Id`、`brandId?:Id\|null`、`productName:string`、`subtitle?:string\|null`、`coverUrl?:string\|null`、`galleryUrls:string[]`、`detailHtml?:string\|null`、`packingList?:string\|null`、`serviceNote?:string\|null`、`attributes:ProductAttributeInput[]`、`skus:SkuCreateInput[]` | `productName` 为 1..255，`subtitle` 最大 500；`galleryUrls` 必须出现、最多 10 个且不可重复，可为空数组；`attributes` 必须出现，可为空数组；`skus` 至少 1 个；详情 HTML 清洗后最大 1,000,000；包装和服务说明各最大 65,535 |
| `SkuContentInput` | `skuId:Id`、`skuName:string`、`imageUrl?:string\|null`、`version:int` | `skuName` 为 1..255；`imageUrl` 遵循图片 URL 安全规则，省略或显式 `null` 均清空该 SKU 图片；`version>=0` |
| `UpdateProductContentRequest` | `categoryId:Id`、`brandId?:Id\|null`、`productName:string`、`subtitle?:string\|null`、`coverUrl?:string\|null`、`galleryUrls:string[]`、`detailHtml?:string\|null`、`packingList?:string\|null`、`serviceNote?:string\|null`、`attributes:ProductAttributeInput[]`、`contentVersion:int`、`skuContents:SkuContentInput[]` | PUT 完整替换；内容字段约束同 `CreateProductRequest`，可空字段省略或为 `null` 均清空；不接受新建 `skus`；`contentVersion>=0`；`skuContents` 必须出现，可为空数组，非空时只提交需要修改展示内容的现有 SKU |
| `CreateSkuRequest` | `skuName:string`、`spec:object<string,string>`、`salePrice:Money`、`marketPrice?:Money\|null`、`barcode?:string\|null`、`imageUrl?:string\|null`、`contentVersion:int` | SKU 字段约束同 `SkuCreateInput`；`contentVersion>=0` 且必须等于当前商品内容版本 |
| `UpdateSkuRequest` | `salePrice?:Money`、`marketPrice?:Money\|null`、`barcode?:string\|null`、`status?:EnabledStatus`、`version:int` | 至少提交一个业务字段且 `version>=0`；`salePrice`、`status` 出现时不可为 `null`；`marketPrice`、`barcode` 可显式 `null` 清空，`barcode` 非空时最大 64、空白也归一化为 `null`；更新后的 `salePrice>0` 且 `marketPrice` 非空时必须 `>=salePrice` |
| `InventoryInboundRequest` | `quantity:int`、`remark?:string\|null` | `quantity>=1` 且增加后库存不得超过 `int` 上限；`remark` 最大 500 |
| `InventoryAdjustmentRequest` | `availableChange:int`、`lockedChange:int`、`version:int`、`reason:string` | 两个变化量至少一个非 0；`version>=0`；`reason` 非空，1..500；其余组合约束见 Phase 2 分册 |

### 7.2 响应

| DTO | 字段 |
| --- | --- |
| `StockView` | `skuId:Id`、`availableQuantity:int`、`lockedQuantity:int`、`version:int`、`updatedAt:Timestamp` |
| `ShopSkuView` | `id:Id`、`skuNo:string`、`skuName:string`、`spec:object<string,string>`、`salePrice:Money`、`marketPrice:Money\|null`、`barcode:string\|null`、`imageUrl:string\|null`、`status:EnabledStatus`、`version:int`、`stock:StockView`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `ProductStatusHistoryView` | `id:Id`、`spuId:Id`、`fromStatus:ProductStatus\|null`、`toStatus:ProductStatus`、`operationType:ProductOperationType`、`contentVersion:int`、`operatorType:OperatorType`、`operator:OperatorBrief\|null`、`reason:string\|null`、`createdAt:Timestamp` |
| `ShopProductSummaryView` | `id:Id`、`spuNo:string`、`productName:string`、`coverUrl:string\|null`、`category:CategoryBrief`、`brand:BrandView\|null`、`status:ProductStatus`、`contentVersion:int`、`skuCount:int`、`enabledSkuCount:int`、`availableQuantity:int`、`lockedQuantity:int`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `ShopProductDetailView` | `id:Id`、`spuNo:string`、`productName:string`、`subtitle:string\|null`、`coverUrl:string\|null`、`galleryUrls:string[]`、`detailHtml:string\|null`、`packingList:string\|null`、`serviceNote:string\|null`、`shop:ShopSummary`、`category:CategoryBrief`、`brand:BrandView\|null`、`attributes:ProductAttributeDisplayView[]`、`status:ProductStatus`、`contentVersion:int`、`createdBy:UserSummary`、`updatedBy:UserSummary`、`skus:ShopSkuView[]`、`history:ProductStatusHistoryView[]`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `InventoryItemView` | `spuId:Id`、`spuNo:string`、`productName:string`、`sku:ShopSkuView` |
| `InventoryOperationView` | `transactionNo:string`、`skuId:Id`、`transactionType:InventoryTransactionType`、`availableChange:int`、`lockedChange:int`、`availableAfter:int`、`lockedAfter:int`、`businessType:string`、`businessNo:string`、`remark:string\|null`、`createdAt:Timestamp`；仅用于一期入库响应 |
| `InventoryTransactionView` | `id:Id`、`transactionNo:string`、`skuId:Id`、`transactionType:InventoryTransactionType`、`availableChange:int`、`lockedChange:int`、`availableBefore:int`、`lockedBefore:int`、`availableAfter:int`、`lockedAfter:int`、`version:int`、`businessType:string`、`businessNo:string`、`operator:OperatorBrief\|null`、`remark:string\|null`、`createdAt:Timestamp` |

## 8. 平台店铺与目录

### 8.1 请求

| DTO | 字段 | 约束 |
| --- | --- | --- |
| `CreateShopRequest` | `shopName:string`、`logoUrl?:string\|null`、`description?:string\|null`、`contactName?:string\|null`、`contactPhone?:string\|null`、`adminUsername:string` | `shopName` 为 1..128；`logoUrl` 遵循图片 URL 安全规则；描述最大 500，联系人最大 64，联系电话最大 32；`adminUsername` 非空且必须精确匹配有效用户 |
| `UpdateShopRequest` | `shopName:string`、`logoUrl?:string\|null`、`description?:string\|null`、`contactName?:string\|null`、`contactPhone?:string\|null` | 约束同创建请求；PUT 为完整更新，缺失或显式 `null` 的可空字段均保存为 `null` |
| `ChangeShopStatusRequest` | `targetStatus:ShopStatus`、`reason:string` | `targetStatus` 不可为 `null`；`reason` 非空白且最大 500；状态迁移矩阵见 Phase 1 分册 |
| `CategoryUpsertRequest` | `parentId?:Id\|null`、`categoryName:string`、`categoryCode:string`、`sortOrder:int` | `parentId` 缺失或为 `null` 表示根类目；名称 1..64；代码符合 `^[A-Z][A-Z0-9_]{1,63}$` 且创建后不可修改 |
| `CategoryAttributeRequest` | `attributeName:string`、`valueType:AttributeValueType`、`unit?:string\|null`、`required:boolean`、`filterable:boolean`、`options?:string[]\|null`、`sortOrder:int` | 名称 1..64，单位最大 32；`OPTION` 必须提供非空、不重复的 `options`，每项 1..64；非 `OPTION` 必须省略 `options` 或提交 `null`，不得提交数组 |
| `BrandRequest` | `brandName:string`、`brandCode:string`、`logoUrl?:string\|null` | 名称 1..128；代码符合 `^[A-Z][A-Z0-9_]{1,63}$` 且创建后不可修改；`logoUrl` 遵循图片 URL 安全规则 |
| `StatusRequest` | 泛型语义为 `StatusRequest<T>`：`targetStatus:T`、`reason?:string\|null`；具体接口限定 `T` | `targetStatus` 不可为 `null`；`reason` 最大 500 |
| `ReviewDecisionRequest` | `contentVersion:int`、`reason?:string\|null` | `contentVersion>=0` 且必须等于待审版本；批准时原因可空，拒绝时 trim 后必须为 1..500 |
| `ProductGovernanceRequest` | `contentVersion:int`、`reason:string` | `contentVersion>=0`；`reason` 非空，1..500 |

### 8.2 响应

| DTO | 字段 |
| --- | --- |
| `PlatformShopView` | `shop:ShopSummary`、`description:string\|null`、`contactName:string\|null`、`contactPhone:string\|null`、`membersCount:int`、`activeMembersCount:int`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `PlatformCategoryView` | `id:Id`、`parentId:Id\|null`、`categoryCode:string`、`categoryName:string`、`sortOrder:int`、`status:EnabledStatus`、`leaf:boolean`、`childrenCount:int`、`attributeCount:int`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `PlatformCategoryNode` | `id:Id`、`parentId:Id\|null`、`categoryCode:string`、`categoryName:string`、`sortOrder:int`、`status:EnabledStatus`、`leaf:boolean`、`childrenCount:int`、`attributeCount:int`、`createdAt:Timestamp`、`updatedAt:Timestamp`、`children:PlatformCategoryNode[]` |
| `ProductReviewSummaryView` | `spuId:Id`、`spuNo:string`、`productName:string`、`coverUrl:string\|null`、`shop:ShopSummary`、`category:CategoryBrief`、`contentVersion:int`、`submittedAt:Timestamp` |
| `ProductReviewSkuView` | `id:Id`、`skuNo:string`、`skuName:string`、`spec:object<string,string>`、`salePrice:Money`、`marketPrice:Money\|null`、`barcode:string\|null`、`imageUrl:string\|null`、`status:EnabledStatus`、`version:int`、`createdAt:Timestamp`、`updatedAt:Timestamp`；不返回库存数量 |
| `ProductReviewDetailView` | `id:Id`、`spuNo:string`、`productName:string`、`subtitle:string\|null`、`coverUrl:string\|null`、`galleryUrls:string[]`、`detailHtml:string\|null`、`packingList:string\|null`、`serviceNote:string\|null`、`shop:ShopSummary`、`category:CategoryBrief`、`brand:BrandView\|null`、`attributes:ProductAttributeDisplayView[]`、`skus:ProductReviewSkuView[]`、`status:ProductStatus`、`contentVersion:int`、`createdBy:UserSummary`、`updatedBy:UserSummary`、`history:ProductStatusHistoryView[]`、`createdAt:Timestamp`、`updatedAt:Timestamp`；不返回 `stock` 或操作能力字段 |

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
| `RoleDetailView` | `id:Id`、`roleCode:string`、`roleName:string`、`scopeType:ScopeType`、`description:string\|null`、`status:ActiveStatus`、`createdAt:Timestamp`、`updatedAt:Timestamp`、`permissions:PermissionView[]` |
| `PlatformUserView` | `id:Id`、`username:string`、`nickname:string`、`phoneMasked:string\|null`、`emailMasked:string\|null`、`status:UserStatus`、`platformRoles:RoleView[]`、`lastLoginAt:Timestamp\|null`、`createdAt:Timestamp` |
| `PlatformUserDetailView` | `id:Id`、`username:string`、`nickname:string`、`phoneMasked:string\|null`、`emailMasked:string\|null`、`status:UserStatus`、`platformRoles:RoleView[]`、`lastLoginAt:Timestamp\|null`、`createdAt:Timestamp`、`avatarUrl:string\|null`、`updatedAt:Timestamp`；联系方式仍使用脱敏字段 |
| `ShopMemberView` | `shopId:Id`、`user:UserSummary`、`role:RoleView`、`status:ActiveStatus`、`createdAt:Timestamp`、`updatedAt:Timestamp` |

`ScopeType`：`PLATFORM`、`SHOP`。`ActiveStatus`：`ACTIVE`、`DISABLED`。

## 10. 售后

### 10.1 请求

| DTO | 字段 |
| --- | --- |
| `CreateAfterSaleRequest` | `orderId:Id`、`orderItemId:Id`、`requestType:AfterSaleType`、`quantity:int`、`reasonCode:string`、`reasonDescription:string\|null`、`evidenceUrls:string[]`、`requestedAmount:Money` |
| `ReturnShipmentRequest` | `carrierCode:string`、`carrierName:string`、`trackingNo:string` |
| `UpdateReturnShipmentRequest` | `carrierCode:string`、`carrierName:string`、`trackingNo:string`、`version:int` |
| `ApproveAfterSaleRequest` | `approvedQuantity:int`、`approvedAmount:Money`、`reviewComment:string\|null`、`version:int` |
| `RejectAfterSaleRequest` | `reviewComment:string`、`version:int` |
| `ConfirmReturnReceivedRequest` | `remark:string\|null`、`version:int` |
| `RetryRefundRequest` | `remark:string`、`version:int` |
| `CreateAfterSaleAppealRequest` | `reasonCode:string`、`reasonDescription:string`、`evidenceUrls:string[]`、`version:int` |
| `DecideAfterSaleAppealRequest` | `decision:AppealDecision`、`approvedQuantity:int\|null`、`approvedAmount:Money\|null`、`reviewComment:string`、`version:int` |

### 10.2 响应

| DTO | 字段 |
| --- | --- |
| `AfterSaleEligibilityView` | `orderId:Id`、`orderItemId:Id`、`orderStatus:OrderStatus`、`purchasedQuantity:int`、`refundedQuantity:int`、`occupiedQuantity:int`、`maximumRequestQuantity:int`、`itemPayableAmount:Money`、`refundedAmount:Money`、`occupiedAmount:Money`、`maximumRequestAmount:Money`、`supportedTypes:AfterSaleType[]`、`eligibleUntil:Timestamp\|null`、`eligible:boolean`、`ineligibleReason:string\|null` |
| `AfterSaleOrderBrief` | `id:Id`、`orderNo:string`、`orderStatus:OrderStatus` |
| `AfterSaleItemView` | `id:Id`、`productName:string`、`skuName:string`、`spec:object<string,string>`、`imageUrl:string\|null`、`unitPrice:Money`、`purchasedQuantity:int` |
| `AfterSaleReviewView` | `reviewerId:Id`、`comment:string\|null`、`reviewedAt:Timestamp` |
| `ReturnShipmentView` | `carrierCode:string`、`carrierName:string`、`trackingNo:string`、`returnedAt:Timestamp`、`receivedAt:Timestamp\|null` |
| `AfterSaleSummaryView` | `id:Id`、`afterSaleNo:string`、`requestType:AfterSaleType`、`status:AfterSaleStatus`、`refundStatus:RefundStatus`、`order:AfterSaleOrderBrief`、`shop:ShopSummary`、`item:AfterSaleItemView`、`quantity:int`、`requestedAmount:Money`、`approvedAmount:Money\|null`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `AfterSaleDetailView` | `id:Id`、`afterSaleNo:string`、`requestType:AfterSaleType`、`status:AfterSaleStatus`、`refundStatus:RefundStatus`、`order:AfterSaleOrderBrief`、`shop:ShopSummary`、`item:AfterSaleItemView`、`quantity:int`、`requestedAmount:Money`、`approvedAmount:Money\|null`、`createdAt:Timestamp`、`updatedAt:Timestamp`、`reasonCode:string`、`reasonDescription:string\|null`、`evidenceUrls:string[]`、`approvedQuantity:int\|null`、`review:AfterSaleReviewView\|null`、`returnShipment:ReturnShipmentView\|null`、`appeal:AfterSaleAppealSummaryView\|null`、`refundNo:string\|null`、`refundFailureReason:string\|null`、`refundedAt:Timestamp\|null`、`completedAt:Timestamp\|null`、`cancelledAt:Timestamp\|null`、`version:int`、`availableActions:string[]` |
| `ShopAfterSaleSummaryView` | `id:Id`、`afterSaleNo:string`、`requestType:AfterSaleType`、`status:AfterSaleStatus`、`refundStatus:RefundStatus`、`order:AfterSaleOrderBrief`、`shop:ShopSummary`、`item:AfterSaleItemView`、`quantity:int`、`requestedAmount:Money`、`approvedAmount:Money\|null`、`createdAt:Timestamp`、`updatedAt:Timestamp`、`buyer:UserSummary` |
| `ShopAfterSaleDetailView` | `id:Id`、`afterSaleNo:string`、`requestType:AfterSaleType`、`status:AfterSaleStatus`、`refundStatus:RefundStatus`、`order:AfterSaleOrderBrief`、`shop:ShopSummary`、`item:AfterSaleItemView`、`quantity:int`、`requestedAmount:Money`、`approvedAmount:Money\|null`、`createdAt:Timestamp`、`updatedAt:Timestamp`、`reasonCode:string`、`reasonDescription:string\|null`、`evidenceUrls:string[]`、`approvedQuantity:int\|null`、`review:AfterSaleReviewView\|null`、`returnShipment:ReturnShipmentView\|null`、`appeal:AfterSaleAppealSummaryView\|null`、`refundNo:string\|null`、`refundFailureReason:string\|null`、`refundedAt:Timestamp\|null`、`completedAt:Timestamp\|null`、`cancelledAt:Timestamp\|null`、`version:int`、`availableActions:string[]`、`buyer:UserSummary`、`eligibilityAtReview:AfterSaleEligibilityView` |

| `AfterSaleAppealAfterSaleView` | `afterSaleId:Id`、`afterSaleNo:string`、`requestType:AfterSaleType`、`status:AfterSaleStatus`、`refundStatus:RefundStatus`、`order:AfterSaleOrderBrief`、`requestedAmount:Money`、`approvedAmount:Money\|null` |
| `AfterSaleAppealSummaryView` | `id:Id`、`appealNo:string`、`afterSaleId:Id`、`afterSaleNo:string`、`triggerType:AppealTriggerType`、`status:AppealStatus`、`createdAt:Timestamp`、`decidedAt:Timestamp\|null` |
| `AfterSaleAppealDetailView` | `id:Id`、`appealNo:string`、`afterSale:AfterSaleAppealAfterSaleView`、`triggerType:AppealTriggerType`、`status:AppealStatus`、`reasonCode:string`、`reasonDescription:string`、`evidenceUrls:string[]`、`merchantReview:AfterSaleReviewView\|null`、`decision:AppealDecision\|null`、`approvedQuantity:int\|null`、`approvedAmount:Money\|null`、`decidedBy:OperatorBrief\|null`、`decisionComment:string\|null`、`decidedAt:Timestamp\|null`、`version:int`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `PlatformAfterSaleAppealSummaryView` | `AfterSaleAppealSummaryView`、`shop:ShopSummary`、`buyer:UserSummary`、`requestType:AfterSaleType`、`requestedAmount:Money` |
| `PlatformAfterSaleAppealDetailView` | `AfterSaleAppealDetailView`、`shop:ShopSummary`、`buyer:UserSummary`、`order:AfterSaleOrderBrief`、`item:AfterSaleItemView` |
| `OperationAfterSaleAppealView` | `id:Id`、`appealNo:string`、`afterSaleNo:string`、`shop:ShopSummary`、`buyer:UserSummary`、`triggerType:AppealTriggerType`、`status:AppealStatus`、`decision:AppealDecision\|null`、`createdAt:Timestamp`、`decidedAt:Timestamp\|null` |
| `MerchantNotificationView` | `id:Id`、`notificationType:MerchantNotificationType`、`appealId:Id`、`appealNo:string`、`afterSaleId:Id`、`afterSaleNo:string`、`title:string`、`content:string`、`readAt:Timestamp\|null`、`createdAt:Timestamp` |

| `MerchantWalletView` | `walletId:Id`、`shopId:Id`、`currency:string`、`status:MerchantWalletStatus`、`pendingBalance:Money`、`availableBalance:Money`、`frozenBalance:Money`、`lifetimeGrossIncome:Money`、`lifetimeCommission:Money`、`lifetimeRefund:Money`、`version:int`、`updatedAt:Timestamp` |
| `MerchantWalletTransactionView` | `id:Id`、`transactionNo:string`、`transactionType:MerchantWalletTransactionType`、`direction:MerchantTransactionDirection`、`sourceBucket:MerchantWalletBucket\|null`、`targetBucket:MerchantWalletBucket\|null`、`bucket:MerchantWalletBucket`、`amount:Money`、`pendingBefore:Money`、`pendingAfter:Money`、`availableBefore:Money`、`availableAfter:Money`、`frozenBefore:Money`、`frozenAfter:Money`、`businessType:string`、`businessNo:string`、`orderId:Id\|null`、`orderNo:string\|null`、`withdrawalId:Id\|null`、`operator:OperatorBrief\|null`、`remark:string\|null`、`createdAt:Timestamp` |
| `ShopSettlementView` | `settlementId:Id`、`shopId:Id`、`orderId:Id`、`orderNo:string\|null`、`tradeId:Id`、`tradeNo:string\|null`、`status:SettlementStatus`、`grossAmount:Money`、`commissionRate:string`、`commissionRefundable:boolean`、`commissionAmount:Money`、`buyerRefundAmount:Money`、`commissionRefundAmount:Money`、`merchantRefundAmount:Money`、`netAmount:Money`、`pendingAmount:Money`、`releasedAmount:Money`、`availableAt:Timestamp\|null`、`settledAt:Timestamp\|null`、`createdAt:Timestamp`、`updatedAt:Timestamp` |
| `MerchantWithdrawalView` | `withdrawalId:Id`、`withdrawalNo:string`、`shopId:Id`、`status:MerchantWithdrawalStatus`、`amount:Money`、`feeAmount:Money`、`netAmount:Money`、`destinationType:WithdrawalDestinationType`、`destinationAccountMasked:string`、`failureReason:string\|null`、`requestedAt:Timestamp`、`completedAt:Timestamp\|null` |

`AfterSaleType`：`REFUND_ONLY`、`RETURN_REFUND`。`AppealTriggerType`：`MERCHANT_REJECTED`、`MERCHANT_TIMEOUT`。`AppealStatus`：`PENDING`、`APPROVED`、`REJECTED`。`AppealDecision`：`APPROVE`、`REJECT`。`MerchantNotificationType`：`AFTER_SALE_APPEAL_SUBMITTED`、`AFTER_SALE_APPEAL_DECIDED`。`AfterSaleStatus` 和 `RefundStatus` 取值以数据库设计为准。

## 11. 对象存储与图片上传

multipart 请求中的文件本身不是 JSON DTO；上传成功后的统一响应 `data` 使用以下 DTO。

| DTO | 字段 |
| --- | --- |
| `AssetUploadView` | `id:Id`、`assetNo:string`、`purpose:AssetPurpose`、`bucket:string`、`objectKey:string`、`originalFilename:string`、`contentType:string`、`sizeBytes:long`、`sha256:string`、`url:string`、`createdAt:Timestamp` |

`AssetPurpose`：`AVATAR`、`SHOP_LOGO`、`BRAND_LOGO`、`PRODUCT_COVER`、`PRODUCT_GALLERY`、`SKU_IMAGE`、`RICH_TEXT_IMAGE`、`AFTER_SALE_EVIDENCE`、`APPEAL_EVIDENCE`。

`AssetStatus`：`ACTIVE`、`DELETED`。`AssetStatus` 目前只用于数据库资源生命周期和内部查询，不作为上传请求字段；上传成功的资源固定返回 `ACTIVE` 对应的可用 URL。

`AssetUploadView.url` 的访问模式取决于 `MINIO_PUBLIC_READ`：公开读时为稳定 URL，私有读时为有效期 1 小时的预签名 URL。私有 URL 不得长期写入业务字段。

## 12. 平台运营与内部任务

| DTO | 字段 |
| --- | --- |
| `OperationTradeView` | `id:Id`、`tradeNo:string`、`user:UserSummary`、`tradeStatus:TradeStatus`、`payableAmount:Money`、`orderCount:int`、`createdAt:Timestamp` |
| `OperationOrderView` | `id:Id`、`orderNo:string`、`tradeId:Id`、`tradeNo:string`、`shop:ShopSummary`、`orderStatus:OrderStatus`、`paymentStatus:OrderPaymentStatus`、`payableAmount:Money`、`refundAmount:Money`、`itemSummary:OrderItemSummaryView[]`、`itemKinds:int`、`totalQuantity:int`、`createdAt:Timestamp`、`availableActions:string[]`、`buyer:UserSummary` |
| `OperationPaymentView` | `id:Id`、`paymentNo:string`、`tradeId:Id`、`amount:Money`、`status:PaymentOrderStatus`、`failureReason:string\|null`、`paidAt:Timestamp\|null`、`expiresAt:Timestamp`、`createdAt:Timestamp`、`updatedAt:Timestamp`、`tradeNo:string`、`user:UserSummary` |
| `OperationAfterSaleView` | `id:Id`、`afterSaleNo:string`、`requestType:AfterSaleType`、`status:AfterSaleStatus`、`refundStatus:RefundStatus`、`order:AfterSaleOrderBrief`、`shop:ShopSummary`、`item:AfterSaleItemView`、`quantity:int`、`requestedAmount:Money`、`approvedAmount:Money\|null`、`createdAt:Timestamp`、`updatedAt:Timestamp`、`buyer:UserSummary` |
| `BusinessTraceLink` | `resourceType:string`、`resourceId:Id`、`businessNo:string`、`status:string`、`createdAt:Timestamp` |
| `BusinessTraceView` | `businessType:string`、`businessNo:string`、`trade:BusinessTraceLink\|null`、`orders:BusinessTraceLink[]`、`payments:BusinessTraceLink[]`、`afterSales:BusinessTraceLink[]`、`inventoryTransactions:BusinessTraceLink[]`、`walletTransactions:BusinessTraceLink[]` |
| `TaskRunRequest` | `dryRun:boolean`、`batchSize:int` |
| `TaskRunView` | `taskName:string`、`dryRun:boolean`、`scanned:int`、`processed:int`、`succeeded:int`、`failed:int`、`mismatches:int`、`startedAt:Timestamp`、`finishedAt:Timestamp`、`requestId:string` |

平台运营列表中的联系方式均按接口文档脱敏。`BusinessTraceView` 只提供关联摘要，前端需要详情时再调用对应受权限保护的详情接口。

## 13. DTO 变更检查

### 13.1 常用字段校验矩阵

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

### 13.2 变更检查

每次修改 DTO 时必须检查：

1. 本文、接口分册示例和 OpenAPI 是否同步。
2. ID、金额和时间是否仍符合统一类型。
3. 新增响应字段是否有稳定默认值或空值规则。
4. Request 是否明确 POST/PUT/PATCH 的缺失与 `null` 语义。
5. 前端类型、Mock、后端 DTO、Controller 契约测试是否同步。
