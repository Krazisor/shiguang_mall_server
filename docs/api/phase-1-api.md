# 时光商城基础交易接口分册（原一期）

## 1. 使用说明

本文按基础交易主题定义当前版本的一部分 HTTP 接口，并与[治理与售后接口分册](phase-2-api.md)合并生效。文件名中的 `phase-1` 仅为历史稳定名称，不表示只执行 `schema.sql` 即可开发或部署。基础类型、统一响应、鉴权、分页、错误、幂等和并发规则见[统一 API 契约](common-contract.md)。接口返回均包裹在统一响应的 `data` 中，下文只展示 `data` 结构。

鉴权缩写：`PUBLIC` 匿名可访问，`LOGIN` 有效登录，`PERM(x)` 平台权限，`SHOP(x)` 目标店铺有效成员及店铺权限，`OWNER` 本人资源。

## 2. 认证与用户

### 2.1 接口清单

| 方法 | 路径 | 鉴权 | 请求/查询 | 成功 `data` | 说明 |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/auth/register` | PUBLIC | `RegisterRequest` | `UserSummary` | 注册用户、创建钱包、分配 `CUSTOMER` |
| POST | `/api/auth/login` | PUBLIC | `LoginRequest` | `LoginView` | Sa-Token 登录 |
| POST | `/api/auth/logout` | LOGIN | 无 | `null` | 注销当前 Token |
| GET | `/api/auth/me` | LOGIN | 无 | `CurrentUserView` | 前端会话、菜单和店铺上下文入口 |
| PATCH | `/api/users/me` | LOGIN/OWNER | `UpdateProfileRequest` | `CurrentUserView` | 修改本人资料 |

### 2.2 DTO

```json
// RegisterRequest
{
  "username": "alice_01",
  "password": "market123",
  "nickname": "Alice",
  "phone": null,
  "email": "alice@example.com"
}
```

```json
// LoginRequest
{
  "username": "alice_01",
  "password": "market123"
}
```

```json
// LoginView
{
  "tokenName": "satoken",
  "tokenValue": "f62309c1-18a6-4f99-8668-975e75ef12b3",
  "expiresInSeconds": 2592000,
  "activeTimeoutSeconds": 604800,
  "user": {
    "id": "101",
    "username": "alice_01",
    "nickname": "Alice",
    "avatarUrl": null,
    "status": "ACTIVE"
  }
}
```

```json
// CurrentUserView
{
  "user": {
    "id": "101",
    "username": "alice_01",
    "nickname": "Alice",
    "avatarUrl": null,
    "status": "ACTIVE"
  },
  "phone": null,
  "email": "alice@example.com",
  "platformRoles": ["CUSTOMER"],
  "platformPermissions": ["product:read", "cart:manage", "trade:create"],
  "shops": [
    {
      "shop": {
        "id": "201",
        "shopNo": "SHOP202607260001",
        "shopName": "时光数码店",
        "logoUrl": null,
        "status": "ACTIVE"
      },
      "roleCode": "SHOP_ADMIN",
      "permissions": ["shop:product:manage", "shop:inventory:manage"]
    }
  ]
}
```

`UpdateProfileRequest` 可提交 `nickname`、`phone`、`email`、`avatarUrl`。`nickname` 必填时长度 `1..64`；手机号、邮箱和头像允许显式 `null` 清空。

### 2.3 业务错误

`AUTH_INVALID_CREDENTIALS`、`USERNAME_ALREADY_EXISTS`、`PHONE_ALREADY_EXISTS`、`EMAIL_ALREADY_EXISTS`、`AUTH_ACCOUNT_LOCKED`。

## 3. 收货地址

| 方法 | 路径 | 鉴权 | 请求 | 成功 `data` |
| --- | --- | --- | --- | --- |
| GET | `/api/addresses` | LOGIN/OWNER | 无 | `AddressView[]`，默认地址优先，其余按更新时间倒序 |
| POST | `/api/addresses` | LOGIN/OWNER | `AddressUpsertRequest` | `AddressView` |
| PUT | `/api/addresses/{addressId}` | LOGIN/OWNER | `AddressUpsertRequest` | `AddressView` |
| DELETE | `/api/addresses/{addressId}` | LOGIN/OWNER | 无 | `null` |
| POST | `/api/addresses/{addressId}/default` | LOGIN/OWNER | 无 | `AddressView` |

```json
// AddressUpsertRequest
{
  "recipientName": "张三",
  "recipientPhone": "13800000000",
  "provinceName": "上海市",
  "cityName": "上海市",
  "districtName": "杨浦区",
  "detailAddress": "延吉中路 100 号",
  "isDefault": true
}
```

字段均去除首尾空白。收件人 `1..64`，电话 `6..32`，省市区各 `1..64`，详细地址 `1..255`。首个地址无论请求值如何都自动成为默认地址。本人地址不存在、已删除或属于其他用户时，对外统一返回 `RESOURCE_NOT_FOUND`；服务内部可以记录具体原因，但不得用错误码暴露归属关系。

## 4. 公开目录与商品

### 4.1 接口清单

| 方法 | 路径 | 鉴权 | 查询参数 | 成功 `data` |
| --- | --- | --- | --- | --- |
| GET | `/api/categories/tree` | PUBLIC | 无 | `CategoryNode[]`，只含 `ENABLED` |
| GET | `/api/categories/{categoryId}/attributes` | PUBLIC | 无 | `CategoryAttributeView[]`，只含 `ENABLED` |
| GET | `/api/brands` | PUBLIC | `keyword,page,pageSize,sort` | `Page<BrandView>`，只含 `ENABLED` |
| GET | `/api/shops/{shopId}` | PUBLIC | 无 | `PublicShopView`，只返回公开字段 |
| GET | `/api/products` | PUBLIC | `keyword,categoryId,brandId,shopId,minPrice,maxPrice,inStock,page,pageSize,sort` | `Page<ProductCardView>` |
| GET | `/api/products/{spuId}` | PUBLIC | 无 | `ProductDetailView` |

品牌列表默认排序为 `brandName,asc`，品牌排序白名单为 `brandName,asc`、`brandCode,asc`；商品列表默认排序为 `createdAt,desc`，商品排序白名单为 `createdAt,desc`、`salePrice,asc`、`salePrice,desc`、`productName,asc`。所有排序均追加稳定次级排序 `id,desc`。`categoryId` 包含该类目及所有启用后代叶子类目的商品。

### 4.2 目录 DTO

```json
// CategoryNode
{
  "id": "401",
  "parentId": null,
  "categoryCode": "DIGITAL",
  "categoryName": "数码",
  "sortOrder": 10,
  "leaf": false,
  "children": []
}
```

```json
// CategoryAttributeView
{
  "id": "411",
  "categoryId": "401",
  "attributeName": "电池容量",
  "valueType": "NUMBER",
  "unit": "mAh",
  "required": true,
  "filterable": true,
  "options": null,
  "sortOrder": 1,
  "status": "ENABLED"
}
```

```json
// BrandView
{
  "id": "421",
  "brandCode": "APPLE",
  "brandName": "Apple",
  "logoUrl": "https://static.example.com/brands/apple.png",
  "status": "ENABLED"
}
```

### 4.3 商品 DTO

```json
// ProductCardView
{
  "id": "501",
  "spuNo": "SPU202607260001",
  "productName": "示例手机",
  "subtitle": "教学商城演示商品",
  "coverUrl": "https://static.example.com/products/501-cover.png",
  "shop": {
    "id": "201",
    "shopNo": "SHOP202607260001",
    "shopName": "时光数码店",
    "logoUrl": null,
    "status": "ACTIVE"
  },
  "categoryId": "401",
  "brand": {
    "id": "421",
    "brandCode": "APPLE",
    "brandName": "Apple",
    "logoUrl": null,
    "status": "ENABLED"
  },
  "minimumSalePrice": "3999.00",
  "maximumSalePrice": "4999.00",
  "inStock": true
}
```

```json
// ProductDetailView
{
  "id": "501",
  "spuNo": "SPU202607260001",
  "productName": "示例手机",
  "subtitle": "教学商城演示商品",
  "coverUrl": "https://static.example.com/products/501-cover.png",
  "galleryUrls": ["https://static.example.com/products/501-1.png"],
  "detailHtml": "<p>清洗后的详情</p>",
  "packingList": "主机、数据线",
  "serviceNote": "7 天售后说明",
  "shop": {
    "id": "201",
    "shopNo": "SHOP202607260001",
    "shopName": "时光数码店",
    "logoUrl": null,
    "status": "ACTIVE"
  },
  "category": {"id": "401", "categoryCode": "MOBILE_PHONE", "categoryName": "手机"},
  "brand": null,
  "attributes": [
    {"attributeId": "411", "attributeName": "电池容量", "value": "5000", "unit": "mAh"}
  ],
  "skus": [
    {
      "id": "511",
      "skuNo": "SKU202607260001",
      "skuName": "黑色 256GB",
      "spec": {"color": "黑色", "storage": "256GB"},
      "salePrice": "3999.00",
      "marketPrice": "4299.00",
      "imageUrl": null,
      "status": "ENABLED",
      "availableQuantity": 20,
      "inStock": true,
      "purchasable": true,
      "unavailableReason": null
    }
  ]
}
```

公开商品不存在或不再公开时返回 `404 PRODUCT_NOT_FOUND`，不得通过该接口查看草稿、待审、下架或禁售内容。

## 5. 购物车与结算

### 5.1 接口清单

| 方法 | 路径 | 鉴权 | 请求 | 成功 `data` |
| --- | --- | --- | --- | --- |
| GET | `/api/cart` | LOGIN + `cart:manage` | 无 | `CartView` |
| POST | `/api/cart/items` | LOGIN + `cart:manage` | `AddCartItemRequest` | `CartItemView` |
| PATCH | `/api/cart/items/{cartItemId}` | LOGIN/OWNER | `UpdateCartItemRequest` | `CartItemView` |
| DELETE | `/api/cart/items/{cartItemId}` | LOGIN/OWNER | 无 | `null` |
| PUT | `/api/cart/selection` | LOGIN/OWNER | `UpdateCartSelectionRequest` | `CartView` |
| POST | `/api/trades/preview` | LOGIN + `trade:create` | `CheckoutPreviewRequest` | `CheckoutPreviewView` |

```json
// AddCartItemRequest
{
  "skuId": "511",
  "quantity": 2
}
```

重复加购按“原数量 + 请求数量”计算，超过 999 返回 `CART_QUANTITY_LIMIT_EXCEEDED`，不会截断。

```json
// UpdateCartItemRequest
{
  "quantity": 3,
  "selected": true
}
```

两个字段至少传一个。`quantity` 不允许 `null`；`selected` 不允许 `null`。

```json
// UpdateCartSelectionRequest
{
  "cartItemIds": ["601", "602"],
  "selected": true
}
```

传空数组表示不修改；全选由前端传当前购物车全部有效项 ID，后端仍逐项校验归属。

### 5.2 购物车视图

```json
{
  "shops": [
    {
      "shop": {
        "id": "201",
        "shopNo": "SHOP202607260001",
        "shopName": "时光数码店",
        "logoUrl": null,
        "status": "ACTIVE"
      },
      "items": [
        {
          "id": "601",
          "skuId": "511",
          "spuId": "501",
          "productName": "示例手机",
          "skuName": "黑色 256GB",
          "spec": {"color": "黑色", "storage": "256GB"},
          "imageUrl": null,
          "quantity": 2,
          "selected": true,
          "currentSalePrice": "3999.00",
          "availableQuantity": 20,
          "valid": true,
          "invalidReason": null,
          "updatedAt": "2026-07-26T18:30:15.123+08:00"
        }
      ]
    }
  ],
  "selectedItemCount": 1,
  "selectedQuantity": 2,
  "selectedAmount": "7998.00"
}
```

购物车表不保存价格，因此 `priceChanged` 只在客户端保存过上次展示价时由客户端比较；服务端始终返回当前价。

### 5.3 结算预览

```json
// CheckoutPreviewRequest
{
  "cartItemIds": ["601", "602"],
  "addressId": "301",
  "shopRemarks": {
    "201": "工作日送货",
    "202": "请先电话联系"
  }
}
```

`cartItemIds` 缺失时使用当前所有 `selected=true` 项；显式数组必须非空且去重。`addressId` 在预览时可为 `null`，此时返回默认地址或 `null`；正式下单必须非空。备注每店最多 500 字。

```json
// CheckoutPreviewView
{
  "address": {
    "id": "301",
    "recipientName": "张三",
    "recipientPhone": "13800000000",
    "provinceName": "上海市",
    "cityName": "上海市",
    "districtName": "杨浦区",
    "detailAddress": "延吉中路 100 号",
    "isDefault": true,
    "createdAt": "2026-07-20T10:00:00.000+08:00",
    "updatedAt": "2026-07-26T18:00:00.000+08:00"
  },
  "shops": [
    {
      "shop": {
        "id": "201",
        "shopNo": "SHOP202607260001",
        "shopName": "时光数码店",
        "logoUrl": null,
        "status": "ACTIVE"
      },
      "items": [
        {
          "cartItemId": "601",
          "skuId": "511",
          "productName": "示例手机",
          "skuName": "黑色 256GB",
          "unitPrice": "3999.00",
          "quantity": 2,
          "originalAmount": "7998.00",
          "freightAmount": "0.00",
          "payableAmount": "7998.00",
          "valid": true,
          "invalidReason": null
        }
      ],
      "itemAmount": "7998.00",
      "freightAmount": "0.00",
      "payableAmount": "7998.00",
      "buyerRemark": "工作日送货"
    }
  ],
  "itemAmount": "7998.00",
  "freightAmount": "0.00",
  "payableAmount": "7998.00",
  "submittable": true,
  "invalidItems": []
}
```

预览存在无效项时仍返回 `200`，但 `submittable=false` 并列出原因，方便前端展示；正式下单遇到无效项返回 `422 CHECKOUT_ITEMS_INVALID`。

## 6. 交易、支付与买家订单

### 6.1 创建交易

| 方法 | 路径 | 鉴权 | 幂等 | 请求 | 成功 `data` |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/trades` | LOGIN + `trade:create` | 必填 | `CreateTradeRequest` | `TradeDetailView`，`201` |
| GET | `/api/trades/{tradeId}` | LOGIN/OWNER | 否 | 无 | `TradeDetailView` |
| POST | `/api/trades/{tradeId}/cancel` | LOGIN/OWNER | 建议 | `CancelTradeRequest` | `TradeDetailView` |

`CreateTradeRequest` 与结算预览请求结构一致，但 `addressId` 必填。后端不接受金额、价格、运费、商品名或店铺名。

```json
// CancelTradeRequest
{
  "reason": "暂时不需要了"
}
```

原因 `1..255`。取消只允许 `PENDING_PAYMENT`；成功后父交易和所有子订单均为 `CANCELLED`，释放所有锁定库存。

### 6.2 交易详情

```json
{
  "id": "701",
  "tradeNo": "TR202607260001",
  "tradeStatus": "PENDING_PAYMENT",
  "payableAmount": "8098.00",
  "address": {
    "recipientName": "张三",
    "recipientPhone": "13800000000",
    "provinceName": "上海市",
    "cityName": "上海市",
    "districtName": "杨浦区",
    "detailAddress": "延吉中路 100 号"
  },
  "payExpireAt": "2026-07-26T19:00:15.123+08:00",
  "paidAt": null,
  "cancelledAt": null,
  "orders": [],
  "availableActions": ["CANCEL", "PAY"]
}
```

`availableActions` 只用于界面便利，后端仍校验动作状态。

### 6.3 钱包

| 方法 | 路径 | 鉴权 | 幂等 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/wallet` | LOGIN + `wallet:read:self` | 否 | 无 | `WalletView` |
| GET | `/api/wallet/transactions` | LOGIN + `wallet:read:self` | 否 | `transactionType,createdFrom,createdTo,page,pageSize` | `Page<WalletTransactionView>` |
| POST | `/api/wallet/recharges` | LOGIN + `wallet:recharge` | 必填 | `RechargeRequest` | `WalletOperationView`，`201` |

```json
// RechargeRequest
{
  "amount": "1000.00",
  "remark": "联调测试充值"
}
```

```json
// WalletView
{
  "walletId": "801",
  "balance": "1000.00",
  "status": "ACTIVE",
  "version": 1,
  "updatedAt": "2026-07-26T18:30:15.123+08:00"
}
```

```json
// WalletOperationView
{
  "transactionNo": "WT202607260001",
  "transactionType": "RECHARGE",
  "direction": "CREDIT",
  "amount": "1000.00",
  "balanceBefore": "0.00",
  "balanceAfter": "1000.00",
  "createdAt": "2026-07-26T18:30:15.123+08:00"
}
```

### 6.4 支付

| 方法 | 路径 | 鉴权 | 幂等 | 请求 | 成功 `data` |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/trades/{tradeId}/payments` | LOGIN/OWNER | 必填 | `CreatePaymentRequest` | `PaymentView`，`201` |
| POST | `/api/payments/{paymentId}/confirm` | LOGIN/OWNER | 必填 | 无 | `PaymentResultView` |
| GET | `/api/payments/{paymentId}` | LOGIN/OWNER | 否 | 无 | `PaymentView` |

```json
// CreatePaymentRequest
{
  "paymentMethod": "WALLET"
}
```

前端不提交金额。后端使用父交易金额创建 `PENDING` 支付单，`expiresAt` 不晚于父交易 `payExpireAt`。

```json
// PaymentResultView
{
  "paymentId": "901",
  "paymentNo": "PAY202607260001",
  "status": "SUCCESS",
  "amount": "8098.00",
  "paidAt": "2026-07-26T18:35:15.123+08:00",
  "tradeId": "701",
  "tradeStatus": "PAID",
  "walletBalance": "1902.00"
}
```

错误：`TRADE_NOT_PAYABLE`、`PAYMENT_EXPIRED`、`PAYMENT_NOT_PENDING`、`WALLET_INSUFFICIENT_BALANCE`、`WALLET_UNAVAILABLE`、`PAYMENT_AMOUNT_MISMATCH`。

支付确认结果规则：余额不足、钱包冻结/关闭和金额数据不一致属于确定性失败，当前支付单更新为 `FAILED` 并写 `failureReason`，父交易继续 `PENDING_PAYMENT`，用户可创建新支付尝试；支付单或父交易到期时支付单为 `CANCELLED`。MySQL/Redis 等依赖暂时不可用时返回 `503`，支付单保持 `PENDING`，客户端用原幂等键安全重试。

### 6.5 买家订单

| 方法 | 路径 | 鉴权 | 查询/请求 | 成功 `data` |
| --- | --- | --- | --- | --- |
| GET | `/api/orders` | LOGIN + `order:read:self` | `orderStatus,paymentStatus,keyword,createdFrom,createdTo,page,pageSize` | `Page<OrderSummaryView>` |
| GET | `/api/orders/{orderId}` | LOGIN/OWNER | 无 | `OrderDetailView` |
| POST | `/api/orders/{orderId}/complete` | LOGIN/OWNER | 无 | `OrderDetailView` |

订单列表是子订单列表；一个父交易会出现多条店铺订单。`keyword` 匹配 `orderNo`、`tradeNo`、商品名称。

```json
// OrderSummaryView
{
  "id": "711",
  "orderNo": "OR202607260001",
  "tradeId": "701",
  "tradeNo": "TR202607260001",
  "shop": {
    "id": "201",
    "shopNo": "SHOP202607260001",
    "shopName": "时光数码店",
    "logoUrl": null,
    "status": "ACTIVE"
  },
  "orderStatus": "PENDING_SHIPMENT",
  "paymentStatus": "PAID",
  "payableAmount": "7998.00",
  "refundAmount": "0.00",
  "itemSummary": [
    {"productName": "示例手机", "skuName": "黑色 256GB", "imageUrl": null, "quantity": 2}
  ],
  "itemKinds": 1,
  "totalQuantity": 2,
  "createdAt": "2026-07-26T18:30:15.123+08:00",
  "availableActions": []
}
```

```json
// OrderDetailView
{
  "id": "711",
  "orderNo": "OR202607260001",
  "tradeId": "701",
  "tradeNo": "TR202607260001",
  "shop": {
    "id": "201",
    "shopNo": "SHOP202607260001",
    "shopName": "时光数码店",
    "logoUrl": null,
    "status": "ACTIVE"
  },
  "orderStatus": "PENDING_RECEIPT",
  "paymentStatus": "PAID",
  "itemAmount": "7998.00",
  "freightAmount": "0.00",
  "payableAmount": "7998.00",
  "refundAmount": "0.00",
  "buyerRemark": "工作日送货",
  "address": {
    "recipientName": "张三",
    "recipientPhone": "13800000000",
    "provinceName": "上海市",
    "cityName": "上海市",
    "districtName": "杨浦区",
    "detailAddress": "延吉中路 100 号"
  },
  "shipping": {
    "carrierCode": "SF",
    "carrierName": "顺丰速运",
    "trackingNo": "SF1234567890",
    "shippedAt": "2026-07-27T09:00:00.000+08:00"
  },
  "items": [
    {
      "id": "721",
      "spuId": "501",
      "skuId": "511",
      "spuNo": "SPU202607260001",
      "skuNo": "SKU202607260001",
      "productName": "示例手机",
      "skuName": "黑色 256GB",
      "spec": {"color": "黑色", "storage": "256GB"},
      "imageUrl": null,
      "unitPrice": "3999.00",
      "quantity": 2,
      "originalAmount": "7998.00",
      "freightAmount": "0.00",
      "payableAmount": "7998.00",
      "refundedQuantity": 0,
      "refundedAmount": "0.00",
      "reservationStatus": "DEDUCTED"
    }
  ],
  "history": [
    {
      "fromStatus": "PENDING_SHIPMENT",
      "toStatus": "PENDING_RECEIPT",
      "operationType": "SHIP",
      "operatorType": "SHOP",
      "remark": null,
      "createdAt": "2026-07-27T09:00:00.000+08:00"
    }
  ],
  "availableActions": ["COMPLETE"]
}
```

## 7. 商家商品

### 7.1 接口清单

| 方法 | 路径 | 鉴权 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- | --- |
| GET | `/api/shops/{shopId}/products` | SHOP(`shop:product:manage`) | `status,keyword,categoryId,page,pageSize,sort` | `Page<ShopProductSummaryView>` |
| POST | `/api/shops/{shopId}/products` | SHOP(`shop:product:manage`) | `CreateProductRequest` | `ShopProductDetailView`，`201` |
| GET | `/api/shops/{shopId}/products/{spuId}` | SHOP(`shop:product:manage`) | 无 | `ShopProductDetailView` |
| PUT | `/api/shops/{shopId}/products/{spuId}/content` | SHOP(`shop:product:manage`) | `UpdateProductContentRequest` | `ShopProductDetailView` |
| POST | `/api/shops/{shopId}/products/{spuId}/skus` | SHOP(`shop:product:manage`) | `CreateSkuRequest` | `ShopProductDetailView`，`201` |
| PATCH | `/api/shops/{shopId}/products/{spuId}/skus/{skuId}` | SHOP(`shop:product:manage`) | `UpdateSkuRequest` | `ShopSkuView` |
| POST | `/api/shops/{shopId}/products/{spuId}/submit-review` | SHOP(`shop:product:manage`) | 无 | `ShopProductDetailView` |
| POST | `/api/shops/{shopId}/products/{spuId}/put-on-shelf` | SHOP(`shop:product:manage`) | 无 | `ShopProductDetailView` |
| POST | `/api/shops/{shopId}/products/{spuId}/take-off-shelf` | SHOP(`shop:product:manage`) | `ReasonRequest` 可选 | `ShopProductDetailView` |

列表默认排序为 `updatedAt,desc`，`sort` 白名单为 `updatedAt,desc`、`createdAt,desc`、`productName,asc`、`status,asc`，并统一追加稳定次级排序 `id,desc`。

### 7.2 创建与更新商品

```json
// CreateProductRequest
{
  "categoryId": "401",
  "brandId": "421",
  "productName": "示例手机",
  "subtitle": "教学商城演示商品",
  "coverUrl": "https://static.example.com/products/501-cover.png",
  "galleryUrls": ["https://static.example.com/products/501-1.png"],
  "detailHtml": "<p>详情</p>",
  "packingList": "主机、数据线",
  "serviceNote": "售后说明",
  "attributes": [
    {"attributeId": "411", "value": "5000"}
  ],
  "skus": [
    {
      "skuName": "黑色 256GB",
      "spec": {"color": "黑色", "storage": "256GB"},
      "salePrice": "3999.00",
      "marketPrice": "4299.00",
      "barcode": null,
      "imageUrl": null
    }
  ]
}
```

至少一个 SKU。`spec` 必须为非空字符串键值对象，键和值长度 `1..64`；后端负责 NFC、排序、规范 JSON 和 SHA-256。创建商品、属性、SKU、库存 0 记录和 `CREATE` 历史在同一事务。

`UpdateProductContentRequest` 字段与创建请求相同，但不含新建 `skus`，并额外要求 `contentVersion`。它可以携带 `skuContents` 数组更新已有 SKU 的 `skuName` 和 `imageUrl`；这两项属于受审核展示内容。已有 SKU 的 `spec` 永远不可修改。销售规格变化通过创建新 SKU、禁用旧 SKU 实现，避免改变订单和购物车引用身份。

```json
// UpdateProductContentRequest 中的 skuContents
{
  "contentVersion": 3,
  "skuContents": [
    {
      "skuId": "511",
      "skuName": "黑色 256GB",
      "imageUrl": "https://static.example.com/products/sku-511.png",
      "version": 3
    }
  ]
}
```

`skuContents` 必传；没有 SKU 展示内容需要修改时传空数组 `[]`。非空时只包含本次需要修改的 SKU，`version` 用于逐 SKU 并发校验。任一 SKU 不属于当前 SPU、已删除或版本冲突时，整个内容更新事务回滚。

```json
// CreateSkuRequest
{
  "skuName": "白色 256GB",
  "spec": {"color": "白色", "storage": "256GB"},
  "salePrice": "3999.00",
  "marketPrice": "4299.00",
  "barcode": null,
  "imageUrl": null,
  "contentVersion": 3
}
```

新增 SKU 属于销售规格变化：只允许 SPU 当前为 `DRAFT`、`REJECTED`、`OFF_SHELF`；成功后 `contentVersion + 1`、状态进入 `DRAFT` 并写 `CONTENT_CHANGED` 历史，所以响应返回完整 `ShopProductDetailView`。同一 SPU 已存在相同规范化规格时返回 `SKU_SPEC_DUPLICATED`，包括已软删除记录；需要恢复原 SKU，不得重复创建。

```json
// UpdateSkuRequest
{
  "salePrice": "3899.00",
  "marketPrice": "4299.00",
  "barcode": null,
  "status": "ENABLED",
  "version": 3
}
```

普通 SKU 更新只允许 `salePrice`、`marketPrice`、`barcode` 和 `status`；这些字段不触发内容审核。所有业务字段可选，但 `version` 必填且至少有一个业务字段。`skuName`、`imageUrl` 只能通过受审核的商品内容更新修改。`UpdateProductContentRequest` 与新增 SKU 一样只接受 `DRAFT`、`REJECTED`、`OFF_SHELF`；在售商品必须先下架。错误：`CATEGORY_NOT_LEAF`、`CATEGORY_DISABLED`、`BRAND_DISABLED`、`PRODUCT_REQUIRED_ATTRIBUTE_MISSING`、`PRODUCT_ATTRIBUTE_INVALID`、`SKU_SPEC_DUPLICATED`、`PRODUCT_NOT_EDITABLE`。

### 7.3 商家商品视图

`ShopProductDetailView` 在公开详情字段基础上额外返回 `status`、`contentVersion`、`createdBy`、`updatedBy`、完整 SKU 状态/版本/库存和 `history`。受审核内容不得使用公开接口读取草稿。

提交审核前后端校验：内容必填、至少一个未删除且启用 SKU、所有必填属性有效。上架前再校验店铺 `ACTIVE`、至少一个启用 SKU 且可用库存大于 0。

## 8. 商家库存

| 方法 | 路径 | 鉴权 | 幂等 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/shops/{shopId}/inventory` | SHOP(`shop:inventory:manage`) | 否 | `keyword,spuId,stockState,page,pageSize` | `Page<InventoryItemView>` |
| GET | `/api/shops/{shopId}/inventory/{skuId}` | SHOP(`shop:inventory:manage`) | 否 | 无 | `InventoryItemView` |
| POST | `/api/shops/{shopId}/inventory/{skuId}/inbounds` | SHOP(`shop:inventory:manage`) | 必填 | `InventoryInboundRequest` | `InventoryOperationView`，`201` |

```json
// InventoryInboundRequest
{
  "quantity": 100,
  "remark": "首批入库"
}
```

```json
// InventoryOperationView
{
  "transactionNo": "IT202607260001",
  "skuId": "511",
  "transactionType": "INBOUND",
  "availableChange": 100,
  "lockedChange": 0,
  "availableAfter": 100,
  "lockedAfter": 0,
  "businessType": "MANUAL_INBOUND",
  "businessNo": "II202607260001",
  "remark": "首批入库",
  "createdAt": "2026-07-26T18:30:15.123+08:00"
}
```

`stockState`：`OUT_OF_STOCK`、`LOW_STOCK`（1..10）、`IN_STOCK`（大于 10）。SKU 不存在、已删除或不属于路径店铺时统一返回 `RESOURCE_NOT_FOUND`，不得用错误码暴露店铺归属。其他错误：`INVENTORY_OPERATION_INVALID`。

## 9. 商家订单履约

| 方法 | 路径 | 鉴权 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- | --- |
| GET | `/api/shops/{shopId}/orders` | SHOP(`shop:order:read`) | `orderStatus,paymentStatus,keyword,createdFrom,createdTo,page,pageSize` | `Page<ShopOrderSummaryView>` |
| GET | `/api/shops/{shopId}/orders/{orderId}` | SHOP(`shop:order:read`) | 无 | `OrderDetailView` |
| POST | `/api/shops/{shopId}/orders/{orderId}/ship` | SHOP(`shop:order:ship`) | `ShipOrderRequest` | `OrderDetailView` |

```json
// ShipOrderRequest
{
  "carrierCode": "SF",
  "carrierName": "顺丰速运",
  "trackingNo": "SF1234567890"
}
```

承运商代码 `1..64`，名称 `1..128`，运单号 `1..128`。错误：`ORDER_NOT_SHIPPABLE`、`TRACKING_NO_ALREADY_USED`、`LOCKED_INVENTORY_INCONSISTENT`。

商家订单详情可查看履约需要的完整地址快照；其他店铺、平台商品审核员和普通用户不能通过该接口查看。

## 10. 平台店铺

| 方法 | 路径 | 鉴权 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- | --- |
| GET | `/api/platform/shops` | PERM(`platform:shop:manage`) | `status,keyword,page,pageSize,sort` | `Page<PlatformShopView>` |
| POST | `/api/platform/shops` | PERM(`platform:shop:manage`) | `CreateShopRequest` | `PlatformShopView`，`201` |
| GET | `/api/platform/shops/{shopId}` | PERM(`platform:shop:manage`) | 无 | `PlatformShopView` |
| PUT | `/api/platform/shops/{shopId}` | PERM(`platform:shop:manage`) | `UpdateShopRequest` | `PlatformShopView` |
| POST | `/api/platform/shops/{shopId}/status` | PERM(`platform:shop:manage`) | `ChangeShopStatusRequest` | `PlatformShopView` |

列表默认排序为 `createdAt,desc`，`sort` 白名单为 `createdAt,desc`、`updatedAt,desc`、`shopName,asc`、`status,asc`，并统一追加稳定次级排序 `id,desc`。

```json
// CreateShopRequest
{
  "shopName": "时光数码店",
  "logoUrl": null,
  "description": "数码产品教学店铺",
  "contactName": "李四",
  "contactPhone": "13900000000",
  "adminUsername": "shop_a_admin"
}
```

店铺号由后端生成。`adminUsername` 必须精确匹配一个有效用户；后端解析用户 ID，并保证该用户成为新店首位 `SHOP_ADMIN`。创建成功状态默认 `PENDING`，平台再通过状态动作改为 `ACTIVE`。

```json
// ChangeShopStatusRequest
{
  "targetStatus": "ACTIVE",
  "reason": "资料核验通过"
}
```

允许：`PENDING -> ACTIVE|CLOSED`、`ACTIVE -> SUSPENDED|CLOSED`、`SUSPENDED -> ACTIVE|CLOSED`；`CLOSED` 终态。关闭前必须没有未完结订单和售后，状态矩阵见统一 API 契约。店铺状态变更不会自动改 SPU 状态，但公开购买条件会立即失效。

## 11. 平台目录管理

权限均为 `platform:catalog:manage`，资源前缀统一为 `/api/platform/catalog`。

### 11.1 类目

| 方法 | 路径 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- |
| GET | `/api/platform/catalog/categories/tree` | `status` 可选 | `PlatformCategoryNode[]` |
| POST | `/api/platform/catalog/categories` | `CategoryUpsertRequest` | `PlatformCategoryView`，`201` |
| PUT | `/api/platform/catalog/categories/{categoryId}` | `CategoryUpsertRequest` | `PlatformCategoryView` |
| POST | `/api/platform/catalog/categories/{categoryId}/status` | `StatusRequest<ENABLED|DISABLED>` | `PlatformCategoryView` |

```json
// CategoryUpsertRequest
{
  "parentId": "401",
  "categoryName": "手机",
  "categoryCode": "MOBILE_PHONE",
  "sortOrder": 10
}
```

根类目 `parentId=null`。错误：`CATEGORY_CODE_ALREADY_EXISTS`、`CATEGORY_CYCLE_DETECTED`、`CATEGORY_HAS_ENABLED_CHILDREN`、`CATEGORY_IN_USE`。

`categoryCode` 创建后不可修改；PUT 必须回传当前值，变更返回 `IMMUTABLE_FIELD_CHANGED`。把某类目作为新父类目前，后端必须确认它没有属性模板且没有 SPU 引用，否则返回 `CATEGORY_PARENT_NOT_EXTENSIBLE`。禁用父类目前必须先禁用全部后代，避免公开类目树出现孤儿节点。

### 11.2 类目属性模板

| 方法 | 路径 | 请求 | 成功 `data` |
| --- | --- | --- | --- |
| GET | `/api/platform/catalog/categories/{categoryId}/attributes` | 无 | `CategoryAttributeView[]` |
| POST | `/api/platform/catalog/categories/{categoryId}/attributes` | `CategoryAttributeRequest` | `CategoryAttributeView`，`201` |
| PUT | `/api/platform/catalog/categories/{categoryId}/attributes/{attributeId}` | `CategoryAttributeRequest` | `CategoryAttributeView` |
| POST | `/api/platform/catalog/categories/{categoryId}/attributes/{attributeId}/status` | `StatusRequest` | `CategoryAttributeView` |

```json
// CategoryAttributeRequest
{
  "attributeName": "颜色",
  "valueType": "OPTION",
  "unit": null,
  "required": true,
  "filterable": true,
  "options": ["黑色", "白色"],
  "sortOrder": 1
}
```

只有叶子类目允许新增属性。已有 SPU 使用的属性模板不得更改 `valueType`；可编辑名称、单位、过滤标记、排序和状态。将可选项移除前必须确认没有现有属性值使用。

### 11.3 品牌

| 方法 | 路径 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- |
| GET | `/api/platform/catalog/brands` | `status,keyword,page,pageSize` | `Page<BrandView>` |
| POST | `/api/platform/catalog/brands` | `BrandRequest` | `BrandView`，`201` |
| PUT | `/api/platform/catalog/brands/{brandId}` | `BrandRequest` | `BrandView` |
| POST | `/api/platform/catalog/brands/{brandId}/status` | `StatusRequest` | `BrandView` |

```json
// BrandRequest
{
  "brandName": "Apple",
  "brandCode": "APPLE",
  "logoUrl": "https://static.example.com/brands/apple.png"
}
```

错误：`BRAND_CODE_ALREADY_EXISTS`、`BRAND_IN_USE`。停用不需要解除历史引用。

`brandCode` 创建后不可修改；PUT 必须回传当前值，变更返回 `IMMUTABLE_FIELD_CHANGED`。

## 12. 平台商品审核

| 方法 | 路径 | 鉴权 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- | --- |
| GET | `/api/platform/products/reviews` | PERM(`platform:product:audit`) | `shopId,categoryId,keyword,page,pageSize` | `Page<ProductReviewSummaryView>` |
| GET | `/api/platform/products/reviews/{spuId}` | PERM(`platform:product:audit`) | 无 | `ProductReviewDetailView` |
| POST | `/api/platform/products/reviews/{spuId}/approve` | PERM(`platform:product:audit`) | `ReviewDecisionRequest` | `ProductReviewDetailView` |
| POST | `/api/platform/products/reviews/{spuId}/reject` | PERM(`platform:product:audit`) | `ReviewDecisionRequest` | `ProductReviewDetailView` |

```json
// ReviewDecisionRequest
{
  "contentVersion": 3,
  "reason": "图片与商品描述一致，审核通过"
}
```

批准时 `reason` 可空，拒绝时必填 `1..500`。`contentVersion` 必须等于待审版本，防止审核旧内容。错误：`PRODUCT_NOT_PENDING_REVIEW`、`PRODUCT_REVIEW_VERSION_CHANGED`。

## 13. 基础交易错误码补充

| 模块 | 错误码 |
| --- | --- |
| 用户 | `AUTH_INVALID_CREDENTIALS`、`USERNAME_ALREADY_EXISTS`、`PHONE_ALREADY_EXISTS`、`EMAIL_ALREADY_EXISTS` |
| 商品 | `PRODUCT_NOT_FOUND`、`PRODUCT_NOT_PURCHASABLE`、`SKU_NOT_FOUND`、`SKU_NOT_PURCHASABLE`、`SKU_SPEC_DUPLICATED` |
| 购物车 | `CART_ITEM_NOT_FOUND`、`CART_QUANTITY_LIMIT_EXCEEDED`、`CHECKOUT_ITEMS_INVALID` |
| 库存 | `INVENTORY_NOT_FOUND`、`INVENTORY_INSUFFICIENT`、`INVENTORY_OPERATION_INVALID` |
| 交易 | `TRADE_NOT_FOUND`、`TRADE_NOT_PAYABLE`、`TRADE_NOT_CANCELLABLE`、`TRADE_EXPIRED` |
| 支付钱包 | `PAYMENT_NOT_FOUND`、`PAYMENT_EXPIRED`、`PAYMENT_NOT_PENDING`、`WALLET_INSUFFICIENT_BALANCE`、`WALLET_UNAVAILABLE` |
| 订单 | `ORDER_NOT_FOUND`、`ORDER_NOT_SHIPPABLE`、`ORDER_NOT_COMPLETABLE`、`TRACKING_NO_ALREADY_USED` |
| 平台资料 | `SHOP_NOT_FOUND`、`CATEGORY_NOT_FOUND`、`BRAND_NOT_FOUND`、`CATALOG_RESOURCE_IN_USE` |

所有错误的 HTTP 状态按[统一 API 契约](common-contract.md)选择。不存在、越权和已软删除资源统一不泄露内部差异。
