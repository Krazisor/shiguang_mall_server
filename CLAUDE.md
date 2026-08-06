# 时光商城（shiguang_market）后端开发指南

## 项目概览

**多商户（类京东）教学电商商城**，Spring Boot 4 + Java 21 + MyBatis-Plus + MySQL 8.0 + Redis + Sa-Token。

单 Maven 模块，按领域分包：`identity` / `shop` / `product` / `inventory` / `cart` / `order` / `payment` / `aftersale` / `address` / `task` / `common` / `integration`。

当前开发采用 **A/B 双线并行模式**，详见 `docs/development/backend-dual-track-development.md`。

## 双线分工

### A 线（队友负责，已完成）
| 领域包 | 能力 | 拥有的表 |
|--------|------|----------|
| `identity` | 注册/登录/个人资料/平台RBAC | sys_user, sys_role, sys_permission, sys_user_role, sys_role_permission |
| `shop` | 平台店铺管理/店铺成员/店铺状态 | shop, shop_user |
| `product` | 类目/品牌/SPU/SKU/审核/上下架/禁售 | product_* (7张表) |
| `inventory` | 库存查询/入库/调整/预占/释放/扣减/流水 | inventory_stock, inventory_transaction |

### B 线（你负责）
| 领域包 | 能力 | 拥有的表 | 状态 |
|--------|------|----------|------|
| `address` | 地址增删改查/默认地址 | user_address | ✅ 已完成（代码在 identity 下，需迁移） |
| `cart` | 加购/数量/选择/删除/结算预览 | cart_item | ✅ 已完成 |
| `order` | 交易/子订单/取消/发货/确认收货 | trade_order, order_info, order_item, order_status_history | ✅ 已完成 |
| `payment` | 钱包/模拟充值/支付确认/流水 | wallet_account, payment_order, wallet_transaction | ✅ 已完成 |
| `aftersale` | 售后申请/审核/退货物流/退款/重试 | after_sale_request | ❌ 仅 Entity+Mapper，无 Service/Controller |
| `task` | 超时取消/自动收货/退款重试/对账/运营只读 | 无独占表（编排B线表） | ❌ 完全未实现 |
| `integration` | 跨线端口 Adapter + Fake | — | ❌ 完全未实现 |

## B 线待完成工作（按优先级）

### 1. aftersale 售后模块（核心）
- [ ] `aftersale/service/AfterSaleService`：资格查询、创建申请、撤销、退货物流、审核（批准/拒绝）、确认收货、退款执行与重试
- [ ] `aftersale/controller/AfterSaleController`：买家售后接口（`/api/after-sales/**`、`/api/orders/{orderId}/items/{orderItemId}/after-sale-eligibility`）
- [ ] `aftersale/controller/ShopAfterSaleController`：商家售后接口（`/api/shops/{shopId}/after-sales/**`）
- [ ] `aftersale/dto/`：请求/响应 DTO
- [ ] 售后状态机：PENDING → REJECTED/CANCELLED/REFUNDING/WAITING_RETURN → COMPLETED
- [ ] 售后额度计算：按数据库文档的累计退款公式，区分占用口径
- [ ] 活跃售后保护：发货/确认收货时检查是否有活跃售后（供给 A 线 `ActiveShopBusinessPort`）

### 2. task 任务模块（核心）
- [ ] `task/` 包整体新建
- [ ] 超时未支付取消：每分钟扫描到期交易，取消并释放库存
- [ ] 自动确认收货：每小时扫描发货满 7 天的待收货订单
- [ ] 支付单过期处理：每分钟取消到期支付单
- [ ] 退款重试：每 5 分钟重试 FAILED/PROCESSING 退款
- [ ] 库存对账/钱包对账：每日流水 vs 聚合值比对，只报告不自动修正
- [ ] 内部触发接口：`/api/internal/tasks/**`（需 `platform:task:execute`，需配置开关 `market.internal-task-api-enabled=true`）
- [ ] 平台运营只读查询：`/api/platform/operations/**`（需要 `platform:operation:read`）

### 3. integration 跨线端口
- [ ] 实现 A 线需要的端口 Adapter：`WalletProvisionPort`、`ActiveShopBusinessPort`、`LockedReservationQueryPort`
- [ ] 实现 B 线消费 A 线端口的 Fake：`ProductSnapshotPortFake`、`ProductAvailabilityPortFake`、`InventoryReservationPortFake`、`CurrentUserPortFake` 等
- [ ] 后续接入 A 线正式 Adapter

### 4. address 迁移
- [ ] `UserAddress` 实体从 `identity/model/` 迁移到新建的 `address/model/`
- [ ] `AddressService` 和 `AddressController` 迁移到 `address/` 包

## B 线已有的关键代码

### cart
- `cart/controller/CartController` — GET/POST/PATCH/DELETE 购物车、结算预览 (`/api/cart/**`, `/api/trades/preview`)
- `cart/service/CartService`
- `cart/model/CartItem`, `cart/mapper/CartItemMapper`

### order
- `order/controller/TradeController` — 创建交易(幂等)/查询/取消 (`/api/trades/**`)
- `order/controller/OrderController` — 买家订单/商家订单/发货/确认收货 (`/api/orders/**`, `/api/shops/{shopId}/orders/**`)
- `order/service/TradeService`, `order/service/OrderService`, `order/service/OrderViewService`
- `order/model/TradeOrder`, `OrderInfo`, `OrderItem`, `OrderStatusHistory`

### payment
- `payment/controller/PaymentController` — 支付/确认 (`/api/trades/{tradeId}/payments`, `/api/payments/**`)
- `payment/controller/WalletController` — 钱包/充值/流水 (`/api/wallet/**`)
- `payment/service/PaymentService`, `payment/service/WalletService`

### address（当前在 identity 包下）
- `identity/controller/AddressController` — CRUD + 默认地址 (`/api/addresses/**`)
- `identity/service/AddressService`
- `identity/model/UserAddress`

## B 线可参考的 A 线代码模式

A 线代码是你编写 B 线模块的最佳参考模板：

| 想实现 | 参考 A 线的 |
|--------|------------|
| 带审核流的状态机 Controller | `product/controller/ProductReviewController` |
| 状态迁移 Service（含事务+锁） | `product/service/ProductReviewService` |
| 商家端接口（shopId 范围校验） | `product/controller/ShopProductController` |
| 平台管理接口（分页+权限） | `shop/controller/PlatformShopController` |
| 幂等写操作 | `order/controller/TradeController.createTrade()` |
| 枚举+CHECK 约束对齐 | `common/model/MarketEnums.java` |
| DTO 结构（胖 DTO 文件） | `identity/dto/IdentityDtos.java`、`product/dto/ProductDtos.java` |

## 开发规范速查

### 分层约束
- **Controller**：只做 HTTP 绑定、`@Valid`、调用 Service、返回 `ApiResponse`。不查 Mapper、不开事务
- **Service**：用例编排、事务（`@Transactional`）、权限校验、状态机、幂等。构造器注入 `private final`
- **Mapper**：继承 `BaseMapper`，SQL 参数化，店铺资源带 `shop_id` 条件，本人资源带 `user_id` 条件
- **禁止**：Controller 调 Controller、跨线直接导 Mapper、在 Java 侧分页后过滤

### 鉴权（Sa-Token，非 Spring Security）
```java
// 当前用户
CurrentUserService.id() / .user()
// 平台权限
CurrentUserService.requirePermission("platform:xxx")
// 店铺权限
ShopAccessService.require(shopId, "shop:xxx")
```

### 统一工具（必须使用，不要自己写）
| 用途 | 使用 |
|------|------|
| 成功响应 | `ApiResponse.success(view)` |
| 分页响应 | `PageView.of(page, items)` |
| ID/金额/时间格式化 | `Formatters.id/money/time()` |
| 业务异常 | `throw BusinessException.badRequest/notFound/conflict(...)` |
| 幂等写 | `IdempotencyService.execute(...)` |
| 幂等业务编号 | `IdempotencyService.businessNo(...)` |
| 图片 URL 安全 | `ContentSafety.imageUrl(s)/detailHtml()` |
| SKU 规格身份键 | `SpecNormalizer.normalize()/key()` |
| 密码 | `PasswordService` (BCrypt cost=12) |

### PATCH 三态语义
区分"字段缺失(不改)"、"字段存在有值(更新)"、"字段为 null(清空)"——使用 `@JsonSetter` + `xxxPresent` 标记模式。

可清空字段实体上必须加 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`。

### 关键约定
- Response DTO 中 ID 统一字符串（`Formatters.id()`），金额固定两位小数（`Formatters.money()`），时间 `OffsetDateTime`（UTC+08:00）
- `application.yaml` 已开启 `fail-on-unknown-properties: true`
- 逻辑删除 `deleted_at`，`update-strategy: not_null`
- 金额一律 `BigDecimal`
- 禁止引入 `spring-security-*` 依赖

## 核心文档索引

| 文档 | 路径 |
|------|------|
| 产品需求（正向交易） | `docs/product/phase-1-requirements.md` |
| 产品需求（治理+售后） | `docs/product/phase-2-requirements.md` |
| API 契约（正向交易） | `docs/api/phase-1-api.md` |
| API 契约（治理+售后） | `docs/api/phase-2-api.md` |
| 统一 API 约定 | `docs/api/common-contract.md` |
| DTO 字段目录 | `docs/api/dto-catalog.md` |
| 数据库设计（26表） | `docs/database/phase-1-design.md` |
| 双线开发规范 | `docs/development/backend-dual-track-development.md` |
| 共享基础设施规范 | `docs/development/backend-shared-infrastructure-guide.md` |
| 数据库基线 DDL | `sql/schema.sql` |
| 增量迁移 | `sql/schema2.sql` |

## 数据库要点

- 26 张表，B 线写权限：`user_address`、`cart_item`、`trade_order`、`order_info`、`order_item`、`order_status_history`、`wallet_account`、`payment_order`、`wallet_transaction`、`after_sale_request`
- 大量 CHECK 约束实现状态机，Java 枚举 `MarketEnums` 必须与数据库约束对齐
- 跨线事务由用例编排方 `@Transactional`，提供方默认 `REQUIRED` 加入
- 禁止 `REQUIRES_NEW` 拆开原子动作

## 测试

现有 B 线相关测试：
- `cart/CartServiceTests.java`
- `order/OrderServiceTests.java`
- `common/PhaseOneEndpointContractTests.java`（正向交易契约）

运行测试：`mvn test`

## Git 约束

- 只在 B 线领域目录开发：`address/`、`cart/`、`order/`、`payment/`、`aftersale/`、`task/`、`integration/`
- `common/**`、`integration/**`、`MarketEnums`、`schema2.sql` 的修改需独立提交并双线评审
- 不自动 commit/push，提交前展示变更摘要
- 禁止 `git push --force`、`git reset --hard`
