# 平台商品总览与违规治理需求及接口文档

## 1. 背景与必要性

现有平台商品审核接口固定查询 `PENDING_REVIEW`，只能处理待审核商品；买家公开目录只返回 `ON_SHELF` 商品；商家商品接口又受 `shopId` 和店铺成员权限限制。平台管理员因此无法跨店铺发现已经上架、下架、草稿、被拒绝或禁售的商品。

本能力是必要的运营和风控入口：平台可以检索全部商品，查看商品内容和状态历史，并对不合适的在售商品立即强制下架或禁售。它不替代商家编辑，不改变公开目录和既有审核流程。

## 2. 角色与权限边界

新增只读平台权限：

```text
platform:product:read
```

现有权限保持不变：

| 权限 | 能力 |
| --- | --- |
| `platform:product:read` | 跨店铺查询商品、详情和状态历史 |
| `platform:product:audit` | 待审核商品的查看、批准和拒绝 |
| `platform:product:ban` | 禁售、解禁和平台强制下架 |

平台商品接口只调用 `currentUser.requirePermission(...)`，不调用店铺成员校验。总览权限不授予商品编辑、价格修改、SKU 修改或库存调整能力。查询权限和下架权限必须分离；`SUPER_ADMIN` 可按平台 RBAC 规则获得全部平台权限，商品审核员同时获得该只读权限以便全量巡检，其他平台角色由 RBAC 管理员显式授权。

## 3. 产品需求

### 3.1 商品总览

默认按 `updatedAt,desc` 返回全部未删除 SPU，不限制商品状态，也不要求店铺为 `ACTIVE`，以便查看暂停/关闭店铺的历史商品。

筛选项：

| 参数 | 规则 |
| --- | --- |
| `status` | `DRAFT`、`PENDING_REVIEW`、`REJECTED`、`OFF_SHELF`、`ON_SHELF`、`BANNED` |
| `shopId` | 指定店铺；不传表示全部店铺 |
| `shopStatus` | `PENDING`、`ACTIVE`、`SUSPENDED`、`CLOSED` |
| `categoryId` | 指定类目及后代类目 |
| `brandId` | 指定品牌，允许历史引用已停用品牌 |
| `keyword` | 匹配 `spuNo` 或商品名称，空白按未传处理 |
| `page/pageSize` | 默认 `1/20`，`pageSize` 为 `1..100` |

列表展示 SPU 编号、商品名称、封面、店铺、类目、品牌、状态、内容版本、SKU 数量、启用 SKU 数量、可用/锁定库存合计及时间。

### 3.2 商品详情

详情展示商品基础内容、图片、详情 HTML、包装和服务说明、店铺、类目、品牌、属性、全部未删除 SKU、只读库存、当前状态、内容版本、创建/更新用户和时间。不得返回买家地址、手机号、订单支付信息等无关敏感数据。

状态动作：`ON_SHELF` 可强制下架或禁售，`OFF_SHELF` 可禁售，`BANNED` 可解禁，其他状态只读。

### 3.3 治理动作

发现普通违规时调用强制下架：`ON_SHELF -> OFF_SHELF`；严重违规时调用禁售：`ON_SHELF/OFF_SHELF -> BANNED`；解禁固定为 `BANNED -> OFF_SHELF`，不自动恢复销售。动作必须携带当前 `contentVersion` 和 `1..500` 字符原因，并写入不可变平台状态历史。

## 4. HTTP 接口契约

未重复说明的响应包装、分页、ID、时间和错误规则继承 [统一 API 契约](../api/common-contract.md)。

权限字典和默认角色授权由后续增量迁移 `sql/scheme5.sql` 提供。该迁移必须在 `schema.sql -> schema2.sql -> scheme3.sql -> scheme4.sql` 之后执行；不得回写或重新执行已经发布的历史脚本。

### 4.1 接口总览

| 方法 | 路径 | 鉴权 | 成功 `data` |
| --- | --- | --- | --- |
| `GET` | `/api/platform/products` | `platform:product:read` | `PageView<PlatformProductSummaryView>` |
| `GET` | `/api/platform/products/{spuId}` | `platform:product:read` | `PlatformProductDetailView` |
| `GET` | `/api/platform/products/{spuId}/history` | `platform:product:read` | `PageView<ProductStatusHistoryView>` |
| `POST` | `/api/platform/products/bans/{spuId}` | `platform:product:ban` | 现有 `ProductReviewDetailView` |
| `POST` | `/api/platform/products/bans/{spuId}/take-off-shelf` | `platform:product:ban` | 现有 `ProductReviewDetailView` |
| `POST` | `/api/platform/products/bans/{spuId}/revoke` | `platform:product:ban` | 现有 `ProductReviewDetailView` |

### 4.2 商品列表

```http
GET /api/platform/products?status=ON_SHELF&shopId=201&shopStatus=ACTIVE&categoryId=401&brandId=421&keyword=手机&page=1&pageSize=20&sort=updatedAt,desc
```

列表筛选均在数据库分页查询中执行。`sort` 白名单为 `updatedAt,desc`、`createdAt,desc`、`productName,asc`、`status,asc`，均追加 `id,desc` 稳定排序。不存在的筛选 ID 返回 `200` 空分页。

```json
{
  "items": [{
    "id": "501", "spuNo": "SPU202607260001", "productName": "示例手机",
    "coverUrl": "https://static.example.com/products/501-cover.png",
    "shop": {"id": "201", "shopNo": "SHOP202607260001", "shopName": "时光数码店", "logoUrl": null, "status": "ACTIVE"},
    "category": {"id": "401", "categoryCode": "MOBILE_PHONE", "categoryName": "手机"},
    "brand": null, "status": "ON_SHELF", "contentVersion": 3,
    "skuCount": 2, "enabledSkuCount": 2, "availableQuantity": 18, "lockedQuantity": 1,
    "createdAt": "2026-07-26T18:30:15.123+08:00", "updatedAt": "2026-08-09T09:12:10.000+08:00"
  }], "page": 1, "pageSize": 20, "total": 1, "totalPages": 1
}
```

### 4.3 商品详情和历史

```http
GET /api/platform/products/{spuId}
GET /api/platform/products/{spuId}/history?page=1&pageSize=20
```

详情 `PlatformProductDetailView` 包含 `id、spuNo、productName、subtitle、coverUrl、galleryUrls、detailHtml、packingList、serviceNote、shop、category、brand、attributes、skus、status、contentVersion、createdBy、updatedBy、createdAt、updatedAt`。平台 SKU 视图额外返回 `availableQuantity`、`lockedQuantity`；只返回未删除 SKU。历史返回现有 `ProductStatusHistoryView`，按 `createdAt DESC,id DESC` 排序。

商品不存在、已删除或关联店铺不存在：`404 PRODUCT_NOT_FOUND`。

### 4.4 强制下架、禁售和解禁

```http
POST /api/platform/products/bans/{spuId}/take-off-shelf
POST /api/platform/products/bans/{spuId}
POST /api/platform/products/bans/{spuId}/revoke
```

请求体统一为：

```json
{"contentVersion": 3, "reason": "商品图片包含违规内容"}
```

继续使用现有 `ProductGovernanceRequest`、`ProductReviewDetailView` 和状态机。版本不一致返回 `VERSION_CONFLICT`；状态不允许时分别返回 `PRODUCT_NOT_ON_SHELF`、`PRODUCT_NOT_BANNABLE`、`PRODUCT_NOT_BANNED`；未登录/无权限沿用统一认证错误。失败不得修改状态或写入成功历史，状态和历史必须同事务提交。

## 5. 安全、审计与验收

- 列表、详情、历史在 Service 层强制鉴权；平台权限不能扩大商家 `/api/shops/{shopId}/products` 的范围。
- 下架/禁售/解禁锁定 SPU 或使用等价并发更新，重新读取状态和内容版本；并发请求只能一个成功。
- 历史不可修改/删除，操作者类型固定为 `PLATFORM`。
- 总览页支持状态、店铺、店铺状态、类目、品牌和关键词筛选，详情页根据权限与状态显示治理动作。
- `ON_SHELF` 商品强制下架后立即从公开 `/api/products` 列表和详情消失；只读账号可查看但不能下架。
- 不新增商品编辑、批量导入、库存调整或买家敏感数据查询能力；审核页 `/api/platform/products/reviews` 继续只表示待审核商品。
