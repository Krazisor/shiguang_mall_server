# 时光商城优惠券模块 API 设计

## 1. 适用范围与继承关系

本文定义优惠券、领券活动、结算选券、商家营销和平台治理的 HTTP 契约，并补充现有结算、交易、订单、售后和商家结算 DTO 的兼容字段。

未重复说明的规则继承：

- [统一 API 契约](common-contract.md)：根路径、统一响应、ID、金额、时间、分页、鉴权、错误、幂等和版本。
- [基础交易接口分册](phase-1-api.md)：购物车、结算、父交易、支付和订单。
- [治理与售后接口分册](phase-2-api.md)：售后额度、退款、平台运营和任务。
- [三期接口设计](phase-3-api.md)：商家钱包、结算、资金锁顺序和对象存储。
- [优惠券产品需求](../product/coupon-requirements.md)：券种、叠加、返券和交互决策。
- [优惠券数据库设计](../database/coupon-design.md)：状态、金额快照、事务和唯一约束。

鉴权缩写：

- `LOGIN`：有效登录账号。
- `PERM(x)`：平台权限。
- `SHOP(x)`：目标店铺有效成员及店铺权限。
- `OWNER`：本人用户券或本人交易。

所有响应仍使用统一 `ApiResponse`；下文表格中的成功 `data` 不包含外层包装。

## 2. 兼容策略

### 2.1 旧客户端

优惠券模块启用后：

1. `CheckoutPreviewRequest` 新增的 `couponSelection` 为可选；省略表示 `NONE`，不会自动使用用户券。
2. `CreateTradeRequest` 新增的 `couponQuoteToken` 为可选；省略表示无券下单。
3. 请求仍不接受价格、优惠金额、应付金额、补贴金额或商家承担金额。
4. 响应新增金额和优惠券字段属于兼容新增；旧客户端必须按统一契约忽略未知字段。
5. 未使用券时所有 `couponDiscountAmount` 为 `"0.00"`、`appliedCoupons=[]`，既有 `payableAmount` 与旧版本相同。
6. 支付请求和钱包接口不新增金额字段，支付仍读取父交易优惠后 `payableAmount`。

### 2.2 契约生效条件

本文件中的订单金额扩展只在优惠券数据库迁移和后端实现同时部署后生效。不能只改前端 DTO 而未安装新数据库约束，也不能只迁移数据库而继续使用旧下单金额公式。

## 3. 公共类型与稳定枚举

### 3.1 类型格式

| 类型 | JSON 格式 | 示例 |
| --- | --- | --- |
| `Id` | 十进制字符串 | `"9007199254740993"` |
| `Money` | 两位小数字符串，单位元 | `"30.00"` |
| `PercentageOff` | 两位小数字符串，表示减免百分比 | `"10.00"` 表示减 10% |
| `Rate4` | 四位小数字符串，表示百分比 | `"25.0000"` 表示平台承担 25% |
| `Timestamp` | ISO 8601 带 `+08:00` | `"2026-08-11T20:00:00.000+08:00"` |

`PercentageOff` 和 `Rate4` 不是 `Money`。服务端使用 `BigDecimal`，不得从 `double` 构造或把字符串按浮点解析。

### 3.2 枚举

| 枚举 | 取值 |
| --- | --- |
| `CouponOwnerType` | `PLATFORM`、`SHOP` |
| `CouponType` | `PERCENTAGE`、`THRESHOLD_REDUCTION`、`CASH_RED_PACKET` |
| `CouponFundingType` | `PLATFORM`、`SHOP`、`SHARED` |
| `CouponScopeType` | `ALL`、`SHOP`、`CATEGORY`、`SPU`、`SKU` |
| `CouponDistributionType` | `PUBLIC_CLAIM`、`FLASH_CLAIM`、`REDEEM_CODE`、`DIRECT_GRANT`、`SYSTEM_GRANT` |
| `CouponAudienceType` | `ALL_USERS`、`NEW_USERS`、`FIRST_ORDER_USERS`、`SPECIFIED_USERS` |
| `CouponValidityType` | `FIXED_RANGE`、`RELATIVE_AFTER_CLAIM` |
| `CouponStackMode` | `EXCLUSIVE`、`CROSS_OWNER` |
| `CouponRestorePolicy` | `NEVER`、`FULL_TRADE_ONLY` |
| `CouponTemplateStatus` | `DRAFT`、`ACTIVE`、`PAUSED`、`ENDED`、`ARCHIVED` |
| `UserCouponStatus` | `AVAILABLE`、`LOCKED`、`USED`、`EXPIRED`、`REVOKED` |
| `CouponActivityType` | `COUPON_CENTER`、`FLASH_CLAIM`、`NEW_USER_WELCOME`、`TARGETED_CAMPAIGN` |
| `CouponActivityStatus` | `DRAFT`、`SCHEDULED`、`RUNNING`、`PAUSED`、`ENDED`、`CANCELLED` |
| `CouponSelectionMode` | `AUTO`、`MANUAL`、`NONE` |
| `CouponRedemptionStatus` | `RESERVED`、`CONSUMED`、`RELEASED`、`RESTORED` |

## 4. 权限代码

| 权限代码 | 作用域 | 用途 |
| --- | --- | --- |
| `coupon:read:self` | `PLATFORM` | 查看领券中心、本人用户券和本人优惠明细 |
| `coupon:claim` | `PLATFORM` | 领取、抢券和兑换 |
| `shop:coupon:read` | `SHOP` | 查看本店活动、模板、核销和报表 |
| `shop:coupon:manage` | `SHOP` | 创建、编辑、发布、暂停、结束和归档本店活动/模板 |
| `shop:coupon:grant` | `SHOP` | 本店定向发券和生成兑换码批次 |
| `shop:coupon:funding:approve` | `SHOP` | 接受或拒绝平台联合活动中本店承担比例 |
| `platform:coupon:read` | `PLATFORM` | 平台优惠券全局只读和报表 |
| `platform:coupon:manage` | `PLATFORM` | 创建、编辑、发布和归档平台活动/模板 |
| `platform:coupon:grant` | `PLATFORM` | 平台定向发券和兑换码批次 |
| `platform:coupon:governance` | `PLATFORM` | 治理暂停店铺活动、撤销未使用用户券 |

`CUSTOMER` 默认获得 `coupon:read:self`、`coupon:claim`；`SHOP_ADMIN` 默认获得四个店铺权限。平台权限不得替代店铺成员数据范围，详细授权见[实现、运营与 RBAC 规范](../development/coupon-implementation-and-operations.md)。

## 5. 买家领券中心

领券中心要求登录，以便一次响应返回本人已领数量、领取资格和稳定原因。若未来需要匿名营销页，应新增不含用户状态的 `PUBLIC` View，不得让当前接口在有无 Token 时返回不同数据范围。

### 5.1 接口清单

| 方法 | 路径 | 鉴权 | 幂等 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/coupon-center/activities` | LOGIN + `coupon:read:self` | 否 | `activityType,shopId,status,page,pageSize,sort` | `Page<ClaimableActivitySummaryView>` |
| GET | `/api/coupon-center/activities/{activityId}` | LOGIN + `coupon:read:self` | 否 | 无 | `ClaimableActivityDetailView` |
| GET | `/api/coupon-center/activities/{activityId}/schedule` | LOGIN + `coupon:read:self` | 否 | 无 | `CouponActivityScheduleView` |
| POST | `/api/coupon-center/activities/{activityId}/templates/{templateId}/claim` | LOGIN + `coupon:claim` | 必填 | 无 | `UserCouponDetailView`，`201` |

买家列表只返回当前可展示的 `SCHEDULED`、`RUNNING`、`PAUSED` 活动；默认排序 `startsAt,asc`，追加 `id,asc`。`status` 买家侧只允许上述三个值。

### 5.2 活动摘要

```json
{
  "id": "3001",
  "activityNo": "CA202608110001",
  "activityType": "FLASH_CLAIM",
  "activityName": "开学抢券日",
  "subtitle": "每日 20:00 限量开抢",
  "bannerUrl": "https://static.example.com/coupons/activity-3001.png",
  "ownerType": "PLATFORM",
  "shop": null,
  "status": "SCHEDULED",
  "startsAt": "2026-08-11T20:00:00.000+08:00",
  "endsAt": "2026-08-11T22:00:00.000+08:00",
  "serverTime": "2026-08-11T19:59:30.000+08:00",
  "templateCount": 3
}
```

`serverTime` 用于前端校准倒计时；前端本地倒计时不构成领取资格依据。

### 5.3 可领取模板

```json
{
  "template": {
    "id": "3101",
    "templateNo": "CT202608110001",
    "couponName": "满 200 减 30",
    "ownerType": "PLATFORM",
    "shop": null,
    "couponType": "THRESHOLD_REDUCTION",
    "benefit": {
      "thresholdAmount": "200.00",
      "discountAmount": "30.00",
      "percentageOff": null,
      "maximumDiscountAmount": null,
      "displayText": "满 200.00 减 30.00"
    },
    "scope": {
      "scopeType": "ALL",
      "summary": "全平台可用"
    },
    "validity": {
      "validityType": "RELATIVE_AFTER_CLAIM",
      "validFrom": null,
      "validTo": null,
      "effectiveDelayMinutes": 0,
      "validForHours": 168,
      "summary": "领取后 7 天内有效"
    },
    "stackMode": "CROSS_OWNER",
    "description": "不含运费，每单限用一张平台券"
  },
  "distributionType": "FLASH_CLAIM",
  "claimStartsAt": "2026-08-11T20:00:00.000+08:00",
  "claimEndsAt": "2026-08-11T22:00:00.000+08:00",
  "remainingState": "LIMITED",
  "remainingQuantity": 18,
  "claimedCountByUser": 0,
  "perUserLimit": 1,
  "claimable": false,
  "unclaimableReason": "NOT_STARTED"
}
```

`remainingState`：

- `AVAILABLE`：有库存但不返回精确值，`remainingQuantity=null`；
- `LIMITED`：进入尾量阈值，可返回精确 `remainingQuantity`；
- `SOLD_OUT`：售罄，数量为 `0`。

`unclaimableReason`：`null` 或 `NOT_STARTED`、`ACTIVITY_PAUSED`、`ACTIVITY_ENDED`、`SOLD_OUT`、`USER_LIMIT_REACHED`、`AUDIENCE_NOT_ELIGIBLE`、`ACCOUNT_UNAVAILABLE`。

周期抢券活动在首场前或两个场次之间继续使用 `NOT_STARTED`。活动详情需要当前和下一周期窗口时调用 schedule 子资源；现有领券中心活动和模板 DTO 不增加周期字段。

### 5.4 周期窗口查询

schedule 子资源对一次性活动返回 `scheduleType=ONCE`、`recurrence=null`，对周期活动返回结构化规则及当前/下一窗口。`window.status` 为 `WAITING`、`OPEN`、`PAUSED` 或 `ENDED`。买家入口沿用活动详情可见性和模板存在性判断，不扩大活动数据范围。

### 5.5 领取

领取请求无 JSON 请求体，必须提供 `Idempotency-Key`。成功返回 `201` 和用户券；相同 Key、相同路径返回第一次 `201` 结果。

领取判断顺序固定为：

```text
登录账号 -> 活动存在/可见 -> 模板属于活动
-> 时间和状态 -> 人群 -> 用户限领 -> 发行量/预算 -> 创建用户券
```

越权或模板不属于活动统一 `404 RESOURCE_NOT_FOUND`；已售罄使用 `422 COUPON_SOLD_OUT`，已超个人限领使用 `409 COUPON_USER_LIMIT_REACHED`。

## 6. 我的优惠券与兑换码

### 6.1 接口清单

| 方法 | 路径 | 鉴权 | 幂等 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/coupons` | LOGIN + `coupon:read:self` + OWNER | 否 | `status,couponType,ownerType,expiringBefore,keyword,page,pageSize,sort` | `Page<UserCouponSummaryView>` |
| GET | `/api/coupons/{userCouponId}` | LOGIN/OWNER | 否 | 无 | `UserCouponDetailView` |
| GET | `/api/coupons/{userCouponId}/eligible-products` | LOGIN/OWNER | 否 | `keyword,page,pageSize,sort` | `Page<ProductCardView>` |
| POST | `/api/coupons/redeem` | LOGIN + `coupon:claim` | 必填 | `RedeemCouponCodeRequest` | `UserCouponDetailView`，`201` |

列表默认 `validTo,asc`，追加 `id,asc`。查询 `status=EXPIRED` 时，服务端必须包含“数据库仍为 `AVAILABLE` 但 `validTo<=now`”的实时过期记录。

### 6.2 用户券视图

```json
{
  "id": "3201",
  "couponNo": "UC202608110001",
  "template": {
    "id": "3101",
    "templateNo": "CT202608110001",
    "couponName": "满 200 减 30",
    "ownerType": "PLATFORM",
    "shop": null,
    "couponType": "THRESHOLD_REDUCTION",
    "benefit": {
      "thresholdAmount": "200.00",
      "discountAmount": "30.00",
      "percentageOff": null,
      "maximumDiscountAmount": null,
      "displayText": "满 200.00 减 30.00"
    },
    "scope": {"scopeType": "ALL", "summary": "全平台可用"},
    "stackMode": "CROSS_OWNER",
    "description": "不含运费，每单限用一张平台券"
  },
  "status": "AVAILABLE",
  "displayStatus": "AVAILABLE",
  "validFrom": "2026-08-11T20:00:00.000+08:00",
  "validTo": "2026-08-18T20:00:00.000+08:00",
  "lockedTradeId": null,
  "claimSource": "FLASH_CLAIM",
  "claimedAt": "2026-08-11T20:00:00.123+08:00",
  "usedAt": null,
  "restoreCount": 0,
  "lastRestoredAt": null,
  "unavailableReason": null,
  "availableActions": ["VIEW_ELIGIBLE_PRODUCTS", "USE"]
}
```

`displayStatus` 是实时状态：数据库状态为 `AVAILABLE` 但已经过期时返回 `EXPIRED`。`lockedTradeId` 仅对券本人返回；运营列表不能借此泄露其他用户交易。

### 6.3 兑换码

```json
{
  "code": "SGM-8Q4K-VJ2M-X7PF"
}
```

约束：去除首尾空白后长度 `8..64`，只接受可打印 ASCII；服务端不改变大小写。错误统一使用 `COUPON_CODE_INVALID`，不向调用者区分代码不存在、已被他人使用或被撤销。

同一用户对已由本人成功兑换的代码使用原幂等键重试时返回原用户券；换 Key 再次提交仍返回 `409 COUPON_CODE_ALREADY_REDEEMED_BY_SELF`，`details` 可包含原用户券 ID，不能产生新券。

## 7. 结算和创建交易扩展

### 7.1 `CouponSelectionRequest`

```json
{
  "mode": "AUTO",
  "userCouponIds": null
}
```

组合约束：

| `mode` | `userCouponIds` |
| --- | --- |
| `AUTO` | 省略、`null` 或空数组；后端选唯一最优组合 |
| `MANUAL` | 必填非空，最多 20 个、不重复 |
| `NONE` | 省略、`null` 或空数组；提交非空数组返回 `VALIDATION_FAILED` |

20 是请求防滥用上限，业务最终可使用数量仍为“1 张平台券 + 每店 1 张店铺券”。

### 7.2 `CheckoutPreviewRequest` 扩展

```json
{
  "cartItemIds": ["601", "602"],
  "addressId": "301",
  "shopRemarks": {"201": "工作日送货"},
  "couponSelection": {
    "mode": "AUTO",
    "userCouponIds": null
  }
}
```

省略 `couponSelection` 等同 `NONE`，以兼容旧客户端。请求不接受任何金额。

### 7.3 预览金额扩展

`CheckoutItemView` 新增：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `couponDiscountAmount` | `Money` | 所有选中券分摊到该明细的总优惠 |
| `payableAmount` | `Money` | 新公式：`originalAmount + freightAmount - couponDiscountAmount` |

`CheckoutShopGroupView` 新增：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `grossAmount` | `Money` | `itemAmount + freightAmount` |
| `couponDiscountAmount` | `Money` | 本店明细优惠合计 |
| `appliedCoupons` | `AppliedCouponView[]` | 作用于本店的店铺券和平台券分摊摘要 |

`CheckoutPreviewView` 新增：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `grossAmount` | `Money` | 全交易优惠前金额 |
| `couponDiscountAmount` | `Money` | 推荐/手选组合总优惠 |
| `couponQuote` | `CouponQuoteView\|null` | `NONE` 或无任何优惠时可为 `null` |

父交易 `payableAmount = grossAmount - couponDiscountAmount`。

### 7.4 `CouponQuoteView`

```json
{
  "quoteToken": "cq_01K2E9M3G1Y5JQ4T9F6W8N2R7C",
  "expiresAt": "2026-08-11T20:05:00.000+08:00",
  "selectionMode": "AUTO",
  "selectedCoupons": [
    {
      "userCouponId": "3201",
      "couponNo": "UC202608110001",
      "templateId": "3101",
      "couponName": "满 200 减 30",
      "ownerType": "PLATFORM",
      "shopId": null,
      "discountAmount": "30.00",
      "platformFundedAmount": "30.00",
      "shopFundedAmount": "0.00",
      "validTo": "2026-08-18T20:00:00.000+08:00"
    }
  ],
  "availableCoupons": [],
  "unavailableCoupons": [
    {
      "userCouponId": "3202",
      "couponName": "满 500 减 80",
      "reason": "THRESHOLD_NOT_MET",
      "thresholdAmount": "500.00",
      "eligibleAmount": "460.00",
      "amountNeeded": "40.00"
    }
  ],
  "totalDiscountAmount": "30.00",
  "warnings": []
}
```

`availableCoupons` 返回未被选中但可用的候选及预计优惠。`warnings` 稳定代码包括 `PARTIAL_FACE_VALUE_USED`、`EXPIRES_SOON`，展示文案由前端字典生成。

报价 Token：

- 长度 `1..128`，不透明，不能由前端解析或拼接。
- 默认有效 5 分钟，绑定 `userId`、购物车项 ID/数量、实时价格摘要、选券 ID、预计分摊和创建时间。
- Token 不锁商品、库存或用户券，不保证正式下单成功。
- 服务端不得把完整报价 JSON、兑换码或用户隐私编码到未加密可读 Token。

### 7.5 `CreateTradeRequest` 扩展

```json
{
  "cartItemIds": ["601", "602"],
  "addressId": "301",
  "shopRemarks": {"201": "工作日送货"},
  "couponQuoteToken": "cq_01K2E9M3G1Y5JQ4T9F6W8N2R7C"
}
```

`couponQuoteToken` 可省略或为 `null`，表示无券下单。非空时服务端必须：

1. 校验 Token 属于当前用户且未过期。
2. 校验购物车项和数量与报价一致。
3. 重新读取商品、价格、库存、用户券、不可变模板规则、人群和当前时间；活动暂停/结束只阻止继续发券，不使已经领取的券失效。
4. 使用同一组用户券重新计算优惠和分摊。
5. 结果与报价不一致时返回 `409 COUPON_QUOTE_CHANGED`，整个下单不创建任何记录。
6. 结果一致时在创建交易事务中预占券。

不能在优惠变少时静默继续，也不能信任 Token 内金额跳过重算。

报价或选择中包含 `FIRST_ORDER_USERS` 券时，创建交易还要保证当前用户不存在成功支付父交易，且不存在另一张使用首单券的待支付父交易。后一场景返回 `409 COUPON_FIRST_ORDER_TRADE_EXISTS`，`details` 可给出属于本人的现有 `tradeId`，便于跳转支付或取消。

### 7.6 正式下单响应

`TradeDetailView` 新增：

```json
{
  "grossAmount": "340.00",
  "couponDiscountAmount": "61.00",
  "payableAmount": "279.00",
  "appliedCoupons": []
}
```

`appliedCoupons` 使用 `AppliedCouponView`，只返回券号、名称、归属、总优惠和状态，不返回内部风控或完整预算。

每条订单明细和每张店铺子订单的优惠后应付都必须至少为 `"0.01"`。固定红包面额超过上限时，响应在报价 `warnings` 返回 `PARTIAL_FACE_VALUE_USED`。

### 7.7 报价错误与无效项

- 商品无效仍按现有 `invalidItems` 和 `CHECKOUT_ITEMS_INVALID` 处理。
- 券无效但商品有效时，预览返回 `200`、`submittable=false`，并在 `unavailableCoupons` 给出原因。
- 正式下单券变化返回 `409 COUPON_QUOTE_CHANGED`；报价 Token 过期或不存在返回 `409 COUPON_QUOTE_EXPIRED`。
- Redis 暂时不可用时，无券预览/下单仍可工作；带券报价返回 `503 DEPENDENCY_UNAVAILABLE`，不得降级为偷偷无券下单。

支付接口路径和请求体不变。使用首单券时，支付确认在锁定买家钱包后、扣款前再次检查没有其他成功支付父交易；资格已被另一交易占用时，本支付单进入确定性 `FAILED`，返回 `COUPON_FIRST_ORDER_QUALIFICATION_LOST`，父交易保持待支付但只适合取消并重新结算，不能移除优惠后直接按新金额支付。

## 8. 订单、售后与结算响应扩展

### 8.1 订单 DTO

`OrderItemView` 新增 `couponDiscountAmount:Money`，`payableAmount` 改为优惠后实付快照。

`OrderSummaryView`、`ShopOrderSummaryView`、`OperationOrderView` 新增：

- `grossAmount:Money`；
- `couponDiscountAmount:Money`。

`OrderDetailView` 新增：

- `grossAmount:Money`；
- `couponDiscountAmount:Money`；
- `appliedCoupons:OrderAppliedCouponView[]`。

`OrderAppliedCouponView` 字段：

```text
redemptionId:Id, couponNo:string, couponName:string,
ownerType:CouponOwnerType, discountAmount:Money,
platformFundedAmount:Money, shopFundedAmount:Money,
redemptionStatus:CouponRedemptionStatus
```

买家订单详情不展示资金承担；买家专用 View 固定省略 `platformFundedAmount` 和 `shopFundedAmount`。店铺和平台运营 View 固定包含这两个字段。同一路径不能按当前登录角色返回时有时无的字段结构。

### 8.2 售后资格

`AfterSaleEligibilityView.itemPayableAmount` 自动使用优惠后明细 `payableAmount`。新增：

- `itemGrossAmount:Money`；
- `couponDiscountAmount:Money`；
- `couponRestoreHint:CouponRestoreHintView|null`。

```json
{
  "restorableOnlyAfterFullTradeRefund": true,
  "currentRequestWillRestore": false,
  "reason": "PARTIAL_REFUND_DOES_NOT_RESTORE"
}
```

售后请求仍提交 `requestedAmount`，但服务端上限为优惠后实付，不接受按商品原价申请。

### 8.3 商家结算

`ShopSettlementView` 新增：

```text
buyerPaidAmount:Money,
platformCouponSubsidyAmount:Money,
shopCouponDiscountAmount:Money,
platformSubsidyRefundAmount:Money
```

原 `grossAmount` 在优惠券模块启用后表示商家计佣和应收基数，即 `buyerPaidAmount + platformCouponSubsidyAmount`。历史无券记录四个新增字段按数据库回填返回。

## 9. 店铺活动和模板管理

所有路径同时要求店铺有效成员、对应权限和 SQL `shop_id` 范围。`SUPER_ADMIN` 不自动获得访问权。

### 9.1 活动接口

| 方法 | 路径 | 权限 | 幂等 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/shops/{shopId}/coupon-activities` | `shop:coupon:read` | 否 | `status,activityType,keyword,createdFrom,createdTo,page,pageSize,sort` | `Page<CouponActivityAdminView>` |
| POST | `/api/shops/{shopId}/coupon-activities` | `shop:coupon:manage` | 必填 | `CreateCouponActivityRequest` | `CouponActivityAdminView`，`201` |
| POST | `/api/shops/{shopId}/coupon-activities/recurring` | `shop:coupon:manage` | 必填 | `CreateRecurringCouponActivityRequest` | `CouponActivityAdminView`，`201` |
| GET | `/api/shops/{shopId}/coupon-activities/{activityId}` | `shop:coupon:read` | 否 | 无 | `CouponActivityAdminView` |
| PUT | `/api/shops/{shopId}/coupon-activities/{activityId}` | `shop:coupon:manage` | 建议 | `UpdateCouponActivityRequest` | `CouponActivityAdminView` |
| GET | `/api/shops/{shopId}/coupon-activities/{activityId}/schedule` | `shop:coupon:read` | 否 | 无 | `CouponActivityScheduleView` |
| PUT | `/api/shops/{shopId}/coupon-activities/{activityId}/schedule` | `shop:coupon:manage` | 建议 | `UpdateCouponActivityScheduleRequest` | `CouponActivityScheduleView` |
| POST | `/api/shops/{shopId}/coupon-activities/{activityId}/publish` | `shop:coupon:manage` | 必填 | `VersionRequest` | `CouponActivityAdminView` |
| POST | `/api/shops/{shopId}/coupon-activities/{activityId}/pause` | `shop:coupon:manage` | 必填 | `ReasonVersionRequest` | `CouponActivityAdminView` |
| POST | `/api/shops/{shopId}/coupon-activities/{activityId}/resume` | `shop:coupon:manage` | 必填 | `VersionRequest` | `CouponActivityAdminView` |
| POST | `/api/shops/{shopId}/coupon-activities/{activityId}/end` | `shop:coupon:manage` | 必填 | `ReasonVersionRequest` | `CouponActivityAdminView` |
| POST | `/api/shops/{shopId}/coupon-activities/{activityId}/cancel` | `shop:coupon:manage` | 必填 | `ReasonVersionRequest` | `CouponActivityAdminView` |

`PUT` 仅允许 `DRAFT`；完整更新字段为：

```json
{
  "activityName": "店庆领券专区",
  "subtitle": "限量店铺券",
  "bannerUrl": null,
  "activityType": "COUPON_CENTER",
  "startsAt": "2026-08-20T00:00:00.000+08:00",
  "endsAt": "2026-08-31T23:59:59.000+08:00",
  "version": 0
}
```

店铺活动 `ownerType` 由路径决定，客户端不得提交。店铺必须为 `ACTIVE` 才能发布；`PENDING/SUSPENDED/CLOSED` 返回 `SHOP_COUPON_PUBLISH_NOT_ALLOWED`。

周期创建接口固定写入 `activityType=FLASH_CLAIM` 和 `status=DRAFT`，并将活动 `startsAt/endsAt` 物化为第一个有效窗口开始和最后一个有效窗口结束。周期规则只允许在草稿期通过 schedule 子资源全量替换；现有活动 PUT 对周期活动只允许修改展示字段，不能改变类型或物化时间边界。

### 9.2 模板接口

| 方法 | 路径 | 权限 | 幂等 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/shops/{shopId}/coupon-templates` | `shop:coupon:read` | 否 | `activityId,status,couponType,distributionType,keyword,page,pageSize,sort` | `Page<CouponTemplateAdminSummaryView>` |
| POST | `/api/shops/{shopId}/coupon-templates` | `shop:coupon:manage` | 必填 | `CreateCouponTemplateRequest` | `CouponTemplateAdminDetailView`，`201` |
| GET | `/api/shops/{shopId}/coupon-templates/{templateId}` | `shop:coupon:read` | 否 | 无 | `CouponTemplateAdminDetailView` |
| PUT | `/api/shops/{shopId}/coupon-templates/{templateId}` | `shop:coupon:manage` | 建议 | `UpdateCouponTemplateRequest` | `CouponTemplateAdminDetailView` |
| PUT | `/api/shops/{shopId}/coupon-templates/{templateId}/scope` | `shop:coupon:manage` | 建议 | `UpdateCouponScopeRequest` | `CouponTemplateAdminDetailView` |
| POST | `/api/shops/{shopId}/coupon-templates/{templateId}/activate` | `shop:coupon:manage` | 必填 | `VersionRequest` | `CouponTemplateAdminDetailView` |
| POST | `/api/shops/{shopId}/coupon-templates/{templateId}/pause` | `shop:coupon:manage` | 必填 | `ReasonVersionRequest` | `CouponTemplateAdminDetailView` |
| POST | `/api/shops/{shopId}/coupon-templates/{templateId}/resume` | `shop:coupon:manage` | 必填 | `VersionRequest` | `CouponTemplateAdminDetailView` |
| POST | `/api/shops/{shopId}/coupon-templates/{templateId}/end` | `shop:coupon:manage` | 必填 | `ReasonVersionRequest` | `CouponTemplateAdminDetailView` |
| POST | `/api/shops/{shopId}/coupon-templates/{templateId}/archive` | `shop:coupon:manage` | 必填 | `ReasonVersionRequest` | `CouponTemplateAdminDetailView` |
| POST | `/api/shops/{shopId}/coupon-templates/{templateId}/copy` | `shop:coupon:manage` | 必填 | `CopyCouponTemplateRequest` | 新 `CouponTemplateAdminDetailView`，`201` |
| PATCH | `/api/shops/{shopId}/coupon-templates/{templateId}/presentation` | `shop:coupon:manage` | 建议 | `UpdateCouponPresentationRequest` | `CouponTemplateAdminDetailView` |

店铺创建请求不允许提交 `ownerType`、`ownerShopId`、`fundingType` 或 `platformShareRate`；服务端固定 `ownerType=SHOP`、`ownerShopId=path shopId`、`fundingType=SHOP`、`platformShareRate=0.0000`。店铺模板不得选择 `SYSTEM_GRANT`，新客触达使用 `PUBLIC_CLAIM` 或 `DIRECT_GRANT`。

模板归档用于隐藏废弃配置，不是删除。只允许 `DRAFT -> ARCHIVED` 或 `ENDED -> ARCHIVED`，`ACTIVE/PAUSED` 必须先结束；归档原因 trim 后 `1..500`。`ARCHIVED` 不可恢复，只能读取详情、追踪历史或通过 `copy` 创建新草稿。归档不撤销用户券、不删除范围/兑换码/核销/预算记录，也不改变已领券使用和退款返券规则。

模板列表未提交 `status` 时默认排除 `ARCHIVED`；提交 `status=ARCHIVED` 时只查询归档模板，不新增 `includeArchived`。授权详情和范围目标接口仍可按 ID 读取归档模板。`CouponActivityAdminView.templateCount` 排除归档模板，历史 `issuedCount/consumedCount/couponDiscountAmount` 仍包含归档模板产生的数据。

### 9.3 创建模板请求

```json
{
  "activityId": "3001",
  "couponName": "数码满 200 减 30",
  "description": "仅限指定数码类目，不含运费",
  "couponType": "THRESHOLD_REDUCTION",
  "thresholdAmount": "200.00",
  "discountAmount": "30.00",
  "percentageOff": null,
  "maximumDiscountAmount": null,
  "scope": {
    "scopeType": "CATEGORY",
    "shopIds": null,
    "categoryIds": ["401"],
    "spuIds": null,
    "skuIds": null
  },
  "distributionType": "PUBLIC_CLAIM",
  "audienceType": "ALL_USERS",
  "newUserWithinDays": null,
  "claimStartsAt": "2026-08-20T00:00:00.000+08:00",
  "claimEndsAt": "2026-08-31T20:00:00.000+08:00",
  "validity": {
    "validityType": "RELATIVE_AFTER_CLAIM",
    "validFrom": null,
    "validTo": null,
    "effectiveDelayMinutes": 0,
    "validForHours": 168
  },
  "totalIssueLimit": 1000,
  "perUserLimit": 1,
  "stackMode": "CROSS_OWNER",
  "refundRestorePolicy": "FULL_TRADE_ONLY",
  "budgetAmount": "30000.00",
  "sortOrder": 10
}
```

字段组合约束完全继承数据库设计。所有金额字段必须明确出现：不适用字段提交 `null`，不能省略后让服务端猜券种。`scope` 创建时必填，也可在第一次发行前通过范围接口全量替换。

`UpdateCouponTemplateRequest` 与创建请求经济字段相同，另加必填 `version`；只允许 `DRAFT`。已经 `firstIssuedAt!=null` 返回 `COUPON_TEMPLATE_RULES_IMMUTABLE`。

### 9.4 范围请求

```json
{
  "scopeType": "SKU",
  "shopIds": null,
  "categoryIds": null,
  "spuIds": null,
  "skuIds": ["511", "512"],
  "version": 1
}
```

只有与 `scopeType` 对应的数组可以非空：

- `ALL`：四个数组省略、`null` 或空；
- `SHOP`：`shopIds` 非空，只允许平台模板；
- `CATEGORY`：`categoryIds` 非空；
- `SPU`：`spuIds` 非空；
- `SKU`：`skuIds` 非空。

单次最多 1000 个目标，去重后保存；超过上限拆模板，不提供无界批量关系写入。

### 9.5 激活校验

激活前一次性校验：

- 活动归属和状态；
- 券种字段组合；
- 领取窗、有效期和活动时间关系；
- 范围非空、目标存在、店铺归属正确；
- 总发行量、单用户限领和预算最坏责任；
- `SHARED` 模板的目标店铺参与关系完整且全部 `ACCEPTED`；
- 店铺状态；
- 展示文案不为空。

任一失败返回 `400 VALIDATION_FAILED` 或具体 `422 COUPON_TEMPLATE_INVALID`，`details` 列出字段/关系原因，不允许部分激活。

## 10. 平台活动和模板管理

平台自有活动/模板使用与店铺相同的 DTO 和状态动作，路径及权限如下：

| 资源 | 列表/创建前缀 | 权限 |
| --- | --- | --- |
| 活动 | `/api/platform/coupon-activities` | 读 `platform:coupon:read`，写 `platform:coupon:manage` |
| 模板 | `/api/platform/coupon-templates` | 读 `platform:coupon:read`，写 `platform:coupon:manage` |

活动资源除上方后缀外，同时支持 `POST /recurring`、`GET /{activityId}/schedule` 和 `PUT /{activityId}/schedule`，权限和 DTO 与店铺版本一致。模板资源的后缀与上方模板接口一致：`/{templateId}`、`/scope`、`/activate`、`/pause`、`/resume`、`/end`、`/archive`、`/copy`、`/presentation`。两类资源不能交叉调用同名后缀。

平台创建模板额外提交：

```json
{
  "ownerType": "PLATFORM",
  "fundingType": "SHARED",
  "platformShareRate": "60.0000"
}
```

`ownerType` 固定只接受 `PLATFORM`，字段保留用于请求清晰和防止把店铺模板请求误发到平台路径。平台模板 `ownerShopId` 不提交。

平台 `SHARED` 券只允许明确 `SHOP`/`SPU`/`SKU` 范围。平台配置完范围和比例后发送参与邀请，模板激活要求所有目标店铺均已明确接受；不能靠描述字段、线下口头确认或平台代操作。

### 10.1 联合承担邀请

平台发送或重发邀请：

~~~http
POST /api/platform/coupon-templates/{templateId}/funding-invitations
~~~

权限 `platform:coupon:manage`，必须提供 `Idempotency-Key`：

~~~json
{
  "shopIds": ["201", "202"],
  "version": 2
}
~~~

`shopIds` 必须与当前范围推导出的承担店铺集合完全相等、去重后最多 1000 个。成功返回 `CouponFundingParticipationView[]`；模板范围或 `platformShareRate` 在草稿期变化后，原接受结果失效，必须重新邀请。

店铺查看和决定：

| 方法 | 路径 | 权限 | 幂等 | 请求/查询 | 成功 `data` |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/shops/{shopId}/coupon-funding-invitations` | `shop:coupon:funding:approve` | 否 | `status,page,pageSize` | `Page<CouponFundingParticipationView>` |
| POST | `/api/shops/{shopId}/coupon-funding-invitations/{participationId}/decide` | `shop:coupon:funding:approve` | 必填 | `DecideCouponFundingRequest` | `CouponFundingParticipationView` |

~~~json
{
  "decision": "ACCEPT",
  "reason": null,
  "version": 0
}
~~~

`decision` 为 `ACCEPT` 或 `REJECT`；拒绝时 `reason` trim 后必填 `1..500`，接受时可空。第一张用户券发行后不能撤销接受或修改比例；需要停止后续领取时按活动暂停流程处理，已经领取和锁定的权益仍有效。

## 11. 定向发券与兑换码批次

### 11.1 定向发券

店铺路径：

```http
POST /api/shops/{shopId}/coupon-templates/{templateId}/grants
```

平台路径：

```http
POST /api/platform/coupon-templates/{templateId}/grants
```

权限分别为 `shop:coupon:grant`、`platform:coupon:grant`，必须提交 `Idempotency-Key`。

```json
{
  "userIds": ["101", "102"],
  "reason": "客服补偿活动",
  "externalReference": "TICKET-20260811-001"
}
```

约束：`userIds` 非空、去重后最多 100；`reason` `1..500`；`externalReference` 可空、最大 128。模板必须为 `DIRECT_GRANT`；`SYSTEM_GRANT` 只能由内部领域事件或补偿任务使用，人工接口不允许操作。

响应 `BatchCouponGrantView`：

```json
{
  "templateId": "3101",
  "requested": 2,
  "succeeded": 1,
  "failed": 1,
  "results": [
    {"userId": "101", "success": true, "userCouponId": "3201", "couponNo": "UC202608110001", "errorCode": null},
    {"userId": "102", "success": false, "userCouponId": null, "couponNo": null, "errorCode": "COUPON_USER_LIMIT_REACHED"}
  ]
}
```

HTTP 返回 `200`，允许逐用户部分成功；整批幂等重试返回相同结果。每个用户使用由批次 Key 派生的稳定业务号，不能因中途异常重复发给前面已成功用户。

### 11.2 兑换码批次

店铺路径：

```http
POST /api/shops/{shopId}/coupon-templates/{templateId}/redeem-code-batches
```

平台路径：

```http
POST /api/platform/coupon-templates/{templateId}/redeem-code-batches
```

必须有对应 `grant` 权限和 `Idempotency-Key`。

```json
{
  "quantity": 100,
  "codePrefix": "SGM",
  "reason": "线下课程活动发放"
}
```

`quantity` 为 `1..500`；模板必须为 `REDEEM_CODE` 且尚有发行责任容量。生成代码本身不增加 `issuedCount`，用户成功兑换时才发行用户券；被撤销未兑换代码也不占发行量。

成功返回 `201 CouponCodeBatchCreatedView`，其中明文 `codes:string[]` 只在本次响应返回。列表和详情查询永不再次返回明文，只返回批次号、总数和状态统计。

兑换码使用 `HMAC-SHA-256(serverSecret, normalizedCode)` 和密钥版本查找；服务端密钥不入库、不进响应或日志。批次元数据查询接口：

| 方法 | 店铺路径/平台路径 | 权限 | 成功 `data` |
| --- | --- | --- | --- |
| GET | `/api/shops/{shopId}/coupon-code-batches` | `shop:coupon:grant` | `Page<CouponCodeBatchSummaryView>` |
| GET | `/api/platform/coupon-code-batches` | `platform:coupon:grant` | `Page<CouponCodeBatchSummaryView>` |

查询参数为 `templateId,batchNo,status,createdFrom,createdTo,page,pageSize`，任何响应都不返回代码明文、摘要或密钥版本。

## 12. 平台治理与只读运营

### 12.1 全局只读

权限 `platform:coupon:read`：

| 方法 | 路径 | 查询 | 成功 `data` |
| --- | --- | --- | --- |
| GET | `/api/platform/coupon-operations/activities` | `ownerType,shopId,status,keyword,page,pageSize` | `Page<CouponActivityAdminView>` |
| GET | `/api/platform/coupon-operations/templates` | `ownerType,shopId,status,couponType,keyword,page,pageSize` | `Page<CouponTemplateAdminSummaryView>` |
| GET | `/api/platform/coupon-operations/user-coupons` | `couponNo,templateNo,userId,status,page,pageSize` | `Page<OperationUserCouponView>` |
| GET | `/api/platform/coupon-operations/redemptions` | `redemptionNo,tradeNo,orderNo,shopId,status,page,pageSize` | `Page<OperationCouponRedemptionView>` |
| GET | `/api/platform/coupon-operations/trace` | `businessType,businessNo` | `CouponBusinessTraceView` |

只读接口不能暂停、撤销、发券或修改预算。用户摘要默认脱敏，不返回完整手机号、地址或兑换码。

### 12.2 治理暂停

```http
POST /api/platform/coupon-governance/activities/{activityId}/pause
```

权限 `platform:coupon:governance`，必须幂等：

```json
{
  "reason": "活动文案与实际适用范围不一致，暂停整改",
  "version": 3
}
```

只允许店铺活动 `RUNNING -> PAUSED`；记录 `pauseSource=PLATFORM_GOVERNANCE`。店铺人员不能直接恢复平台治理暂停，必须由平台治理接口解除：

```http
POST /api/platform/coupon-governance/activities/{activityId}/resume
```

### 12.3 撤销用户券

```http
POST /api/platform/coupon-governance/user-coupons/{userCouponId}/revoke
```

请求 `ReasonVersionRequest`，必须幂等。只允许 `AVAILABLE` 且未实时过期的券；`LOCKED` 返回 `COUPON_LOCKED_BY_TRADE`，`USED` 返回 `COUPON_ALREADY_USED`。响应 `UserCouponDetailView`，但平台治理 View 中用户信息保持脱敏。

批量撤销不在第一版 HTTP 契约中。后续需要时必须使用异步任务、逐项结果和审批审计，不能开放无界 ID 数组。

## 13. 管理 DTO 精确字段

### 13.1 通用动作请求

| DTO | 字段与约束 |
| --- | --- |
| `VersionRequest` | `version:int`，必须 `>=0` |
| `ReasonVersionRequest` | `reason:string`，trim 后 `1..500`；`version:int>=0` |
| `CopyCouponTemplateRequest` | `couponName:string` `1..128`；`activityId?:Id\|null`；`copyScope:boolean`；`version:int>=0` |
| `UpdateCouponPresentationRequest` | `couponName?:string`、`description?:string\|null`、`sortOrder?:int`、`version:int`；至少一个业务字段，名称不可为 `null` |
| `DecideCouponFundingRequest` | `decision:enum(ACCEPT,REJECT)`；`reason?:string\|null`，拒绝时 trim 后 `1..500`、接受时必须为 `null`；`version:int>=0` |

`CouponFundingParticipationView`：

```text
id, templateId, templateNo, shopId, shop,
platformShareRate, shopShareRate, status,
invitedBy, invitedAt, decidedBy, decidedAt,
decisionReason, version, availableActions
```

`shopShareRate` 由 `100.0000 - platformShareRate` 计算返回，不接受客户端提交；`decisionReason` 只在店铺有权查看该邀请时返回，买家接口永不返回。

### 13.2 管理活动视图

`CouponActivityAdminView`：

```text
id, activityNo, ownerType, shop, activityType,
activityName, subtitle, bannerUrl, startsAt, endsAt,
status, pauseSource, pauseReason, templateCount,
issuedCount, consumedCount, couponDiscountAmount,
version, createdBy, updatedBy, createdAt, updatedAt,
availableActions
```

金额统计为当前数据库聚合值，不能用前端分页结果相加替代。`consumedCount` 按 `coupon_redemption.consumed_at IS NOT NULL` 统计；已经返券而状态为 `RESTORED` 的原核销仍计入历史核销，返券后第二次使用产生新的 `attemptNo=2` 并再计一次。

### 13.3 管理模板视图

`CouponTemplateAdminDetailView`：

```text
id, templateNo, activity, ownerType, ownerShop,
couponName, description, couponType, benefit,
fundingType, platformShareRate, fundingParticipations, scope,
distributionType, audienceType, newUserWithinDays,
claimStartsAt, claimEndsAt, validity,
totalIssueLimit, issuedCount, remainingIssueQuantity,
perUserLimit, stackMode, refundRestorePolicy,
budgetAmount, budgetReservedAmount,
budgetConsumedAmount, budgetReversedAmount,
status, firstIssuedAt, sortOrder, version,
createdBy, updatedBy, createdAt, updatedAt,
availableActions
```

`scope` 在管理详情中返回目标 ID 和最小摘要；目标超过 100 条时详情只返回前 100 条和 `targetCount`，完整范围使用分页子接口：

```http
GET .../coupon-templates/{templateId}/scope-targets?page=1&pageSize=100
```

禁止为了返回上万个目标关闭分页上限。

模板管理详情的 `availableActions` 按状态返回：

| 状态 | `availableActions` |
| --- | --- |
| `DRAFT` | `EDIT`、`ACTIVATE`、`COPY`、`ARCHIVE` |
| `ACTIVE` | `PAUSE`、`END`、`COPY` |
| `PAUSED` | `RESUME`、`END`、`COPY` |
| `ENDED` | `COPY`、`ARCHIVE` |
| `ARCHIVED` | `COPY` |

归档模板拒绝完整更新、范围替换、展示字段修改、状态动作、联合承担邀请和参与决定；复制继续执行现有活动关系校验。源模板关联的活动已经结束或取消时，复制请求必须显式提交 `activityId=null` 或改为一个允许关联的新活动。

## 14. 状态动作、版本与幂等

### 14.1 必须幂等的写接口

以下操作强制 `Idempotency-Key`：

- 领取、限时抢券、兑换码；
- 创建活动、创建模板、复制模板；
- 发布、激活、暂停、恢复、结束、取消、归档；
- 定向发券、生成兑换码批次；
- 平台治理暂停/恢复、撤销用户券；
- 带券创建交易继续继承现有创建交易幂等要求。

普通草稿 `PUT/PATCH` 推荐幂等键，但最终并发保护由 `version` 提供。相同 Key、不同请求仍返回 `IDEMPOTENCY_KEY_REUSED`。

### 14.2 版本冲突

管理活动、模板、用户券治理动作都回传 `version`。过期版本返回 `409 VERSION_CONFLICT`；前端刷新数据，不自动重放覆盖。

### 14.3 时间竞态

活动任务状态不能替代请求时钟：

- 已到 `startsAt` 但自动任务尚未把 `SCHEDULED` 改为 `RUNNING`，领取服务可以在同一事务条件推进并领取，或返回短暂重试错误；实现方案必须冻结一致，不得不同实例行为不同。
- 推荐方案是领取服务对 `SCHEDULED AND startsAt<=now<endsAt` 执行一次条件推进。
- `endsAt<=now` 一律不得领取，即使数据库仍是 `RUNNING`。

## 15. 限流和风控响应

| 接口 | 建议限流键 | 默认建议 |
| --- | --- | --- |
| 普通领取 | `userId + templateId` | 5 次/10 秒 |
| 限时抢券 | `userId + templateId`，辅以 IP/设备摘要 | 3 次/秒，20 次/分钟 |
| 兑换码 | `userId`，辅以 IP 摘要 | 10 次/10 分钟，连续错误后冷却 |
| 结算报价 | `userId` | 30 次/分钟 |
| 管理批量发放 | `operatorId + templateId` | 5 批/分钟 |

命中限流返回 `429 TOO_MANY_REQUESTS`，可增加响应头 `Retry-After` 秒数。不能用“售罄”掩盖风控，也不能在错误详情返回 IP、设备指纹或具体风控阈值。

## 16. 业务错误码

### 16.1 领取与用户券

| HTTP | 错误码 | 场景 |
| ---: | --- | --- |
| 404 | `RESOURCE_NOT_FOUND` | 活动/模板/用户券不存在或无权知道 |
| 409 | `COUPON_USER_LIMIT_REACHED` | 已达到模板个人限领 |
| 409 | `COUPON_CODE_ALREADY_REDEEMED_BY_SELF` | 本人换 Key 重复兑换同一码 |
| 409 | `COUPON_LOCKED_BY_TRADE` | 券已被待支付交易锁定 |
| 409 | `COUPON_ALREADY_USED` | 券已核销且未返还 |
| 422 | `COUPON_ACTIVITY_NOT_CLAIMABLE` | 活动未开始、暂停、结束或取消 |
| 422 | `COUPON_TEMPLATE_NOT_CLAIMABLE` | 模板状态或领取窗不允许 |
| 422 | `COUPON_SOLD_OUT` | 总发行量已用完 |
| 422 | `COUPON_AUDIENCE_NOT_ELIGIBLE` | 不符合新客/首单/指定人群 |
| 422 | `COUPON_CODE_INVALID` | 兑换码无效、已被他人使用或撤销 |
| 422 | `COUPON_NOT_EFFECTIVE` | 用户券尚未到 `validFrom` |
| 422 | `COUPON_EXPIRED` | 用户券已到 `validTo` |
| 422 | `COUPON_REVOKED` | 用户券已撤销 |

### 16.2 结算与交易

| HTTP | 错误码 | 场景 |
| ---: | --- | --- |
| 409 | `COUPON_QUOTE_EXPIRED` | 报价 Token 不存在、过期或不属于本人 |
| 409 | `COUPON_QUOTE_CHANGED` | 商品、数量、价格、券状态或优惠结果变化 |
| 409 | `COUPON_CONCURRENTLY_USED` | 下单锁券时券已被另一交易占用 |
| 409 | `COUPON_FIRST_ORDER_TRADE_EXISTS` | 已有一张使用首单券的待支付父交易 |
| 422 | `COUPON_SCOPE_MISMATCH` | 所选券没有命中商品 |
| 422 | `COUPON_THRESHOLD_NOT_MET` | 有效金额未达门槛 |
| 422 | `COUPON_STACK_CONFLICT` | 平台券/店铺券叠加规则冲突 |
| 422 | `COUPON_SELECTION_LIMIT_EXCEEDED` | 超过每交易/每店允许数量 |
| 422 | `COUPON_FIRST_ORDER_QUALIFICATION_LOST` | 预览后已完成其他首单 |

预览接口通常把范围、门槛、叠加等作为 `unavailableCoupons.reason` 返回 `200`；只有请求结构非法或正式下单失败才使用上述 HTTP 错误。`unclaimableReason`、`UserCouponDetailView.unavailableReason` 和 `unavailableCoupons.reason` 共用下列封闭目录，前端按代码展示文案，不得依赖自由文本：

| 原因码 | 含义 |
| --- | --- |
| `NOT_STARTED` | 活动或领取窗口尚未开始 |
| `ACTIVITY_PAUSED` | 活动被暂停 |
| `ACTIVITY_ENDED` | 活动已结束/取消 |
| `THRESHOLD_NOT_MET` | 命中商品优惠前金额未达门槛 |
| `SCOPE_MISMATCH` | 购物车没有命中适用范围 |
| `STACK_CONFLICT` | 与已选平台券/店铺券叠加冲突 |
| `LOCKED_BY_OTHER_TRADE` | 用户券被另一待支付父交易锁定 |
| `FIRST_ORDER_TRADE_EXISTS` | 已有使用首单券的待支付交易 |
| `FIRST_ORDER_QUALIFICATION_LOST` | 用户已完成其他成功首单 |
| `USER_LIMIT_REACHED` | 已达到模板个人限领 |
| `SOLD_OUT` | 模板发行量已用完 |
| `EXPIRED` | 用户券已超过 `validTo` |
| `REVOKED` | 用户券已被撤销 |
| `NOT_EFFECTIVE` | 用户券尚未到 `validFrom` |
| `AUDIENCE_NOT_ELIGIBLE` | 用户不符合新客/指定人群 |
| `ACCOUNT_UNAVAILABLE` | 账号被冻结、删除或状态无效 |
| `COUPON_ALREADY_USED` | 用户券已核销且未返还 |
| `QUOTE_CHANGED` | 报价后商品、价格或券状态发生变化 |

领券中心只返回活动/领取资格相关代码；结算只返回券时间、范围、门槛、叠加、锁定和首单资格相关代码。`ACTIVITY_PAUSED` 或 `ACTIVITY_ENDED` 不得作为已领取券在结算中不可用的理由。

`ACTIVITY_ENDED`、`EXPIRED`、`REVOKED`、`QUOTE_CHANGED` 是用户侧原因码；正式写接口分别映射到 `COUPON_ACTIVITY_NOT_CLAIMABLE`、`COUPON_EXPIRED`、`COUPON_REVOKED`、`COUPON_QUOTE_CHANGED`，避免前端显示逻辑依赖 HTTP 错误码。

### 16.3 管理与治理

| HTTP | 错误码 | 场景 |
| ---: | --- | --- |
| 409 | `COUPON_ACTIVITY_STATE_CONFLICT` | 当前活动状态不允许动作 |
| 409 | `COUPON_TEMPLATE_STATE_CONFLICT` | 当前模板状态不允许动作，包括非 `DRAFT/ENDED` 模板归档或修改归档模板 |
| 409 | `COUPON_TEMPLATE_RULES_IMMUTABLE` | 已发行后试图修改经济规则/范围 |
| 409 | `COUPON_HAS_LOCKED_TRADES` | 结束/关闭动作会破坏待支付预占 |
| 422 | `COUPON_TEMPLATE_INVALID` | 券种、范围、时间、预算或人群组合无效 |
| 422 | `COUPON_BUDGET_INSUFFICIENT` | 预算小于最坏发行责任 |
| 422 | `COUPON_SCOPE_TARGET_INVALID` | 范围目标不存在、跨店或类型错误 |
| 422 | `COUPON_FUNDING_PARTICIPATION_INCOMPLETE` | 联合承担目标店铺未全部接受或集合不一致 |
| 409 | `COUPON_FUNDING_ALREADY_FROZEN` | 已发行后试图改变参与关系或承担比例 |
| 422 | `SHOP_COUPON_PUBLISH_NOT_ALLOWED` | 店铺状态不允许发布 |
| 403 | `COUPON_GOVERNANCE_REQUIRED` | 店铺试图恢复平台治理暂停活动 |

公共 `VALIDATION_FAILED`、`VERSION_CONFLICT`、`IDEMPOTENCY_KEY_REUSED`、`SHOP_ACCESS_DENIED`、`DEPENDENCY_UNAVAILABLE` 继续使用统一契约，不创建近义优惠券错误码。

## 17. 自动任务接口

沿用内部任务权限 `platform:task:execute` 和 `TaskRunRequest/TaskRunView`：

| 方法 | 路径 | 任务 |
| --- | --- | --- |
| POST | `/api/internal/tasks/start-coupon-activities` | 推进到点活动开始 |
| POST | `/api/internal/tasks/end-coupon-activities` | 推进到期活动结束 |
| POST | `/api/internal/tasks/grant-system-coupons` | 扫描/补偿 `USER_REGISTERED` 事件并幂等发放系统券 |
| POST | `/api/internal/tasks/expire-user-coupons` | 物化过期并释放预算责任 |
| POST | `/api/internal/tasks/recover-coupon-reservations` | 恢复与交易状态不一致的孤立预占 |
| POST | `/api/internal/tasks/reconcile-coupons` | 对账发行、预算、核销、订单和结算 |

写任务支持 `dryRun`；对账任务无论 `dryRun` 值都只报告差异，不自动修改金额。批量大小 `1..1000`，多实例使用任务锁加数据库状态条件。`grant-system-coupons` 只处理平台 `SYSTEM_GRANT + NEW_USERS` 模板，默认按 `userId ASC, templateId ASC` 分页，每项使用固定 `SYSTEM_GRANT:USER_REGISTERED:{userId}:{templateId}` 业务键；单项失败记录并重试，不向注册接口传播异常。

## 18. API 验收清单

1. 所有 ID、金额、百分比、时间、空值和分页严格符合本文格式。
2. 领券中心返回本人领取状态，但不泄露其他用户或精确热点库存。
3. 抢券相同 Key 重放、不同请求冲突、并发不超发均有契约和 MySQL 集成测试。
4. 旧预览/下单请求省略优惠字段时按无券执行。
5. 自动、手动、不使用三种模式组合约束明确，非法手选不被静默替换。
6. 报价 Token 过期、购物车变化、价格变化、券并发占用和首单资格变化返回稳定错误。
7. 订单、售后和结算响应中的优惠字段与数据库分摊相等。
8. 店铺接口同时校验权限和路径 `shopId`，平台权限不能绕过店铺成员边界。
9. 已发行模板经济字段和范围不能修改，展示字段修改有版本和审计。
10. 明文兑换码只在创建批次响应出现一次，日志、列表和错误均不返回。
11. 只读运营权限不能调用发布、发券、暂停、恢复或撤销接口。
12. 所有必填幂等接口缺少 Header 时返回 `400 BAD_REQUEST`，不会先执行业务再报错。
13. 注册事件与补偿任务并发处理同一用户/模板时只发一张券，任一营销失败都不改变注册结果。
14. 草稿或结束模板归档后从默认列表隐藏，显式筛选仍可查询，已领券、预算和对账数据不变。
