# 时光商城 B 线开发实施计划

## 项目背景

A 线（队友）已完成正向交易闭环的全部模块：identity、shop、product、inventory、cart、order、payment。  
B 线（你）负责完成当前版本剩余模块：address 迁移、aftersale 售后、task 定时任务、平台运营查询、跨线端口集成。

**开发分支**：`feature/b-line-aftersale-task`（基于 `main` 创建）  
**开发原则**：只修改 B 线领域目录（`address/`、`aftersale/`、`task/`、`integration/` 以及已有 `cart/`、`order/`、`payment/`），不碰 A 线目录。

---

## B 线现有代码与待完成项

### 已完成（phase1 落地，可直接使用和参考）

| 模块 | 关键文件 | 说明 |
|------|---------|------|
| `cart` | Controller / Service / DTO / Mapper / Entity | 购物车 CRUD、结算预览 |
| `order` | TradeController + OrderController / TradeService + OrderService / OrderViewService | 交易创建取消、订单查询、发货、确认收货 |
| `payment` | PaymentController + WalletController / PaymentService + WalletService | 支付创建确认、钱包余额充值流水 |
| `address` | AddressController + AddressService（当前在 `identity/` 包下） | CRUD + 默认地址 |

### 待完成

| 模块 | 现有 | 缺失 | 工作量 |
|------|------|------|--------|
| `address` 迁移 | — | 从 `identity/` 迁移到独立的 `address/` 包 | 0.5天 |
| `aftersale` | Entity + Mapper | Service + Controller + DTO + 测试 | 4天 |
| `task` | 空白 | 5个定时任务 + 5个内部接口 + 5个运营查询接口 | 3天 |
| 已有模块修改 | — | order 发货/收货加活跃售后检查、wallet 加退款入账 | 1天 |
| `integration` | 空白 | 3个端口 Adapter + 7个端口 Fake | 1天 |

---

## 阶段一：address 迁移 + aftersale DTO 定义

### 目标
- 把 address 从 identity 包独立出来
- 冻结 aftersale 全部 Request/Response DTO

### 1.1 address 包迁移

将以下 4 个文件从 `identity/` 移动到新建的 `address/` 包，同时更新所有 import 引用：

| 原路径 | 新路径 |
|--------|--------|
| `identity/model/UserAddress.java` | `address/model/UserAddress.java` |
| `identity/mapper/UserAddressMapper.java` | `address/mapper/UserAddressMapper.java` |
| `identity/service/AddressService.java` | `address/service/AddressService.java` |
| `identity/controller/AddressController.java` | `address/controller/AddressController.java` |

**注意**：`CartService`、`OrderService` 等其他模块引用了 `AddressService` 或 `UserAddress`，迁移后需同步更新它们的 import。在 IDEA 中使用 Refactor → Move 可以自动更新引用。

### 1.2 aftersale DTO

新建 `aftersale/dto/AfterSaleDtos.java`，参考 `docs/api/phase-2-api.md` 第 6-7 节定义全部 record：

**请求 DTO：**
```
CreateAfterSaleRequest      — 创建申请（orderId, orderItemId, requestType, quantity, reasonCode, reasonDescription, evidenceUrls, requestedAmount）
ReturnShipmentRequest       — 提交退货物流（carrierCode, carrierName, trackingNo）
UpdateReturnShipmentRequest — 更正退货物流（carrierCode, carrierName, trackingNo, version）
ApproveAfterSaleRequest     — 批准（approvedQuantity, approvedAmount, reviewComment, version）
RejectAfterSaleRequest      — 拒绝（reviewComment, version）
ConfirmReturnReceivedRequest — 确认收货（remark, version）
RetryRefundRequest          — 退款重试（remark, version）
```

**响应 View DTO：**
```
AfterSaleEligibilityView    — 资格查询结果
AfterSaleDetailView         — 售后详情（含订单/店铺/商品快照/审核/物流/退款进度/availableActions）
AfterSaleSummaryView        — 售后列表摘要
ShopAfterSaleDetailView     — 商家端售后详情
ShopAfterSaleSummaryView    — 商家端售后列表摘要
```

DTO 规范遵守现有约定：ID 用 String（`Formatters.id()`）、金额用 String（`Formatters.money()`）、时间用 `OffsetDateTime`、枚举用 code 字符串。

### 1.3 验证标准
- `mvn compile` 通过，无 import 报错
- DTO 字段与 phase-2-api.md 完全一致

---

## 阶段二：aftersale Service 核心逻辑

### 目标
实现售后全部业务逻辑，包括状态机、额度计算、退款执行、库存操作。

### 2.1 买家端 AfterSaleService

路径：`aftersale/service/AfterSaleService.java`

依赖注入（构造器注入 `private final`）：

```
AfterSaleRequestMapper    — B 线售后表
OrderInfoMapper           — B 线子订单表（读）
OrderItemMapper           — B 线订单明细表（读 + 更新累计退款）
TradeOrderMapper          — B 线交易表（读）
WalletAccountMapper       — B 线钱包表（退款操作）
WalletTransactionMapper   — B 线钱包流水表（退款流水）
InventoryStockMapper      — A 线库存表（释放/回库）
InventoryTransactionMapper— A 线库存流水表（写流水）
CurrentUserService        — 当前用户
ShopAccessService         — 商家权限
IdempotencyService        — 幂等
NumberGenerator           — 编号生成
```

**方法清单：**

| 方法 | 逻辑要点 |
|------|---------|
| `eligibility(orderId, orderItemId)` | 查订单明细 → 算累计退款/占用额度 → 判断子订单状态决定支持类型 → 返回 AfterSaleEligibilityView |
| `create(request, idempotencyKey)` | 幂等包裹 → 锁订单明细(FOR UPDATE) → 重算额度 → 校验不超 → 生成 afterSaleNo → insert → 返回详情 |
| `list(status, type, ...)` | 本人 + 分页 + 状态筛选 |
| `detail(afterSaleId)` | 本人资源校验 → 组装完整 AfterSaleDetailView（含 availableActions） |
| `cancel(afterSaleId)` | 只允许 PENDING → 更新 status=CANCELLED + cancelledAt |
| `submitReturnShipment(afterSaleId, request, key)` | 幂等 → WAITING_RETURN + 未填物流 → 写物流字段 + returnedAt |
| `updateReturnShipment(afterSaleId, request)` | WAITING_RETURN + returnReceivedAt 为空 → version 乐观锁 → 更新物流 |

### 2.2 商家端 ShopAfterSaleService

路径：`aftersale/service/ShopAfterSaleService.java`

| 方法 | 逻辑要点 |
|------|---------|
| `list(shopId, ...)` | shopAccess.require(shopId, "shop:after-sale:manage") → 本店售后分页 |
| `detail(shopId, afterSaleId)` | 校验店铺归属 → 组装 ShopAfterSaleDetailView |
| `approve(shopId, afterSaleId, request, key)` | **最复杂的方法**（详见 2.3） |
| `reject(shopId, afterSaleId, request)` | 只允许 PENDING → 写 reviewComment + status=REJECTED |
| `confirmReturnReceived(shopId, afterSaleId, request, key)` | 幂等 → WAITING_RETURN + 已填物流 → 写 returnReceivedAt → 库存回库 → 执行退款（详见 2.3） |
| `retryRefund(shopId, afterSaleId, request, key)` | 幂等 → REFUNDING/FAILED → 只重试退款，不回库 |

### 2.3 批准 + 退款执行逻辑（核心）

**仅退款批准流程：**
```
approve()
  ├── 锁售后行(FOR UPDATE)
  ├── 校验 status=PENDING + version
  ├── 锁订单明细(FOR UPDATE)
  ├── 扣除其他已批准未结束的申请占用量，复核本次批准量
  ├── 更新售后: status → REFUNDING, refundStatus → PROCESSING
  ├── 执行退款:
  │   ├── 锁钱包(FOR UPDATE)
  │   ├── debit 钱包 + 写流水(businessType=AFTER_SALE_REFUND, businessNo=refundNo)
  │   ├── 更新订单明细累计退款(refundQuantity, refundAmount)
  │   ├── 更新子订单累计退款
  │   ├── 释放库存: stockMapper.release(skuId, quantity) + 写 INVENTORY_RELEASE 流水
  │   ├── 检查是否全额退款 → 子订单改为 CANCELLED/REFUNDED
  │   └── 成功: refundStatus=SUCCESS, status=COMPLETED
  │   └── 失败: refundStatus=FAILED, refundFailureReason=...（不回滚批准结果）
  └── 返回结果
```

**退货退款批准流程：**
```
approve() → status=WAITING_RETURN, refundStatus=NOT_STARTED
           → 不执行退款，等待买家退货

confirmReturnReceived()
  ├── 锁售后行 + 校验 WAITING_RETURN + 已填物流
  ├── 写 returnReceivedAt
  ├── 库存回库: stockMapper.return(skuId, quantity) + 写 RETURN 流水(businessType=AFTER_SALE + afterSaleNo)
  ├── 执行退款（同上退款流程，但不释放库存）
  └── 退款失败: status=REFUNDING, refundStatus=FAILED

retryRefund()
  ├── 只允许 REFUNDING/FAILED
  ├── 沿用原 refundNo（钱包流水 businessNo 不变，唯一约束保证不重复）
  ├── 仅退款 → 幂等释放库存
  ├── 退货退款 → 不回库（已在 confirmReturnReceived 时回库）
  └── 成功: status=COMPLETED, refundStatus=SUCCESS
```

### 2.4 额度计算公式

参考 `docs/database/phase-1-design.md` 和 `docs/product/phase-2-requirements.md` 第 2.5 节：

```
已退数量 = orderItem.refundQuantity（COMPLETED 已计入）
已退金额 = orderItem.refundAmount
占用数量 = 其他 PENDING 申请的 quantity
         + 其他 WAITING_RETURN/REFUNDING 申请的 approvedQuantity
占用金额 = 其他 PENDING 申请的 requestedAmount
         + 其他 WAITING_RETURN/REFUNDING 申请的 approvedAmount

最大可申请数量 = purchasedQuantity - 已退数量 - 占用数量
最大可申请金额 = itemPayableAmount - 已退金额 - 占用金额
```

### 2.5 状态机变迁路径

```
PENDING ──reject()──→ REJECTED
PENDING ──cancel()──→ CANCELLED
PENDING ──approve(REFUND_ONLY)──→ REFUNDING → COMPLETED
PENDING ──approve(RETURN_REFUND)──→ WAITING_RETURN
WAITING_RETURN ──confirmReturnReceived()──→ REFUNDING → COMPLETED
REFUNDING(FAILED) ──retryRefund()──→ COMPLETED / REFUNDING(FAILED)
```

### 2.6 验证标准
- 售后额度计算单元测试通过（各种占用组合）
- 状态机变迁单元测试通过（合法迁移成功 + 非法迁移抛异常）
- 仅退款批准后库存正确释放、钱包正确入账
- 退货退款确认收货后库存正确回库
- 并发创建申请不超额（行锁 + 唯一约束保护）
- 退款重试幂等（同 refundNo 只产生一条钱包流水）

---

## 阶段三：aftersale Controller + 测试

### 目标
暴露 HTTP 接口，通过 MVC 契约测试验证。

### 3.1 AfterSaleController（买家端）

路径：`aftersale/controller/AfterSaleController.java`  
基础路径：`/api`

| 方法 | 路径 | 鉴权 | 幂等 |
|------|------|------|------|
| GET | `/api/orders/{orderId}/items/{orderItemId}/after-sale-eligibility` | 登录 | 否 |
| POST | `/api/after-sales` | `after-sale:create` | 必填 |
| GET | `/api/after-sales` | 登录 | 否 |
| GET | `/api/after-sales/{afterSaleId}` | 登录 | 否 |
| POST | `/api/after-sales/{afterSaleId}/cancel` | 登录 | 建议 |
| POST | `/api/after-sales/{afterSaleId}/return-shipment` | 登录 | 必填 |
| PUT | `/api/after-sales/{afterSaleId}/return-shipment` | 登录 | 否 |

Controller 只做：参数绑定、`@Valid`、`@RequestHeader("Idempotency-Key")`、调用 Service、返回 `ApiResponse`/`201`。

### 3.2 ShopAfterSaleController（商家端）

路径：`aftersale/controller/ShopAfterSaleController.java`  
基础路径：`/api/shops/{shopId}/after-sales`

| 方法 | 路径 | 鉴权 | 幂等 |
|------|------|------|------|
| GET | `/api/shops/{shopId}/after-sales` | `shop:after-sale:manage` | 否 |
| GET | `/api/shops/{shopId}/after-sales/{afterSaleId}` | `shop:after-sale:manage` | 否 |
| POST | `/api/shops/{shopId}/after-sales/{afterSaleId}/approve` | `shop:after-sale:manage` | 必填 |
| POST | `/api/shops/{shopId}/after-sales/{afterSaleId}/reject` | `shop:after-sale:manage` | 建议 |
| POST | `/api/shops/{shopId}/after-sales/{afterSaleId}/confirm-return-received` | `shop:after-sale:manage` | 必填 |
| POST | `/api/shops/{shopId}/after-sales/{afterSaleId}/refund/retry` | `shop:after-sale:manage` | 必填 |

### 3.3 Sa-Token 权限路由注册

在 `SaTokenConfig` 的权限放行/路由配置中确认以下权限代码已就绪（来自 `sql/schema2.sql` 的种子数据无需额外添加）：

- 买家：`after-sale:create`
- 商家：`shop:after-sale:manage`

### 3.4 测试

**AfterSaleServiceTests**（参考 `CartServiceTests.java` 结构）：
- 资格查询正确计算可申请额度
- 创建申请 → 并发创建不超额
- 商家批准仅退款 → 钱包入账 + 库存释放
- 商家批准退货退款 → 等待退货
- 买家提交物流 → 商家确认收货 → 库存回库 + 退款
- 退款失败 → 重试成功 → 幂等
- 非法状态迁移抛异常
- 全额退款后子订单状态变为 CANCELLED/REFUNDED

**AfterSaleEndpointContractTests**（参考 `PhaseOneEndpointContractTests.java`）：
- 13 个接口的路径、HTTP 方法、鉴权级别、请求/响应字段
- 未登录 401、无权限 403
- 幂等重放成功、同键不同请求冲突

### 3.5 验证标准
- 全部 aftersale 接口可通过 curl/Postman 正常调用
- 售后单元测试 + 契约测试通过
- `mvn test` 全部通过（新增测试不破坏已有测试）

---

## 阶段四：修改已有 B 线模块

### 目标
在 order 发货/确认收货中加入活跃售后保护，wallet 增加退款入账方法。

### 4.1 OrderService 活跃售后检查

在 `OrderService.ship()` 和 `OrderService.complete()` 方法中，状态校验之前增加：

```
// 检查是否存在活跃售后
boolean hasActiveAfterSale = afterSaleRequestMapper.exists(
    new LambdaQueryWrapper<AfterSaleRequest>()
        .eq(AfterSaleRequest::getOrderId, orderId)
        .in(AfterSaleRequest::getStatus, List.of(
            AfterSaleStatus.PENDING,
            AfterSaleStatus.WAITING_RETURN,
            AfterSaleStatus.REFUNDING)));
if (hasActiveAfterSale) {
    throw BusinessException.conflict("ORDER_HAS_ACTIVE_AFTER_SALE", "订单存在活跃售后");
}
```

同时在 `TradeService.cancel()` 中，取消交易时联动取消关联的 PENDING 售后申请。

### 4.2 WalletService 退款入账

在 `WalletService` 中新增方法：

```
@Transactional
public void refund(long userId, BigDecimal amount, String refundNo, String businessType) {
    WalletAccount wallet = wallet(userId, true);
    BigDecimal before = wallet.getBalance();
    walletMapper.credit(userId, amount);
    WalletAccount after = wallet(userId, false);
    WalletTransaction transaction = new WalletTransaction();
    transaction.setTransactionNo(numbers.next("WT"));
    transaction.setWalletId(wallet.getId());
    transaction.setTransactionType(WalletTransactionType.REFUND);
    transaction.setDirection(TransactionDirection.CREDIT);
    transaction.setAmount(amount);
    transaction.setBalanceBefore(before);
    transaction.setBalanceAfter(after.getBalance());
    transaction.setBusinessType(businessType);
    transaction.setBusinessNo(refundNo);
    walletTransactionMapper.insert(transaction);
}
```

### 4.3 验证标准
- 有活跃售后的订单发货返回 409
- 有活跃售后的订单确认收货返回 409
- 退款入账后钱包余额和流水正确

---

## 阶段五：task 定时任务 + 平台运营查询

### 目标
实现 5 个定时任务、5 个内部触发接口、5 个平台运营只读接口。

### 5.1 包结构

```
task/
├── scheduler/
│   ├── CancelExpiredTradesTask.java
│   ├── CompleteShippedOrdersTask.java
│   ├── ExpirePaymentOrdersTask.java
│   ├── RetryFailedRefundsTask.java
│   └── ReconciliationTask.java
├── controller/
│   ├── InternalTaskController.java    — /api/internal/tasks/**
│   └── PlatformOperationController.java — /api/platform/operations/**
├── dto/
│   └── TaskDtos.java
```

### 5.2 定时任务

每个任务使用 `@Scheduled` 注解，内部用 Redis 分布式锁保证单实例执行：

```java
@Component
public class CancelExpiredTradesTask {
    @Scheduled(cron = "0 * * * * *")  // 每分钟
    public void execute() {
        // 1. Redis SETNX 抢锁 (key="task:cancel-expired-trades", TTL=120s)
        // 2. 查询 trade_order WHERE trade_status='PENDING_PAYMENT' AND pay_expire_at < NOW()
        //    分批处理，每批 batchSize=100
        // 3. 逐条取消交易 → 释放库存 → 更新子订单状态
        // 4. 释放 Redis 锁
    }
}
```

| 任务 | cron | 核心 SQL 条件 |
|------|------|-------------|
| 超时取消 | `0 * * * * *` | `trade_status=PENDING_PAYMENT AND pay_expire_at < NOW()` |
| 自动收货 | `0 0 * * * *` | `order_status=PENDING_RECEIPT AND shipped_at < NOW()-7天 AND NOT EXISTS(活跃售后)` |
| 支付过期 | `0 * * * * *` | `status=PENDING AND expires_at < NOW()` |
| 退款重试 | `0 */5 * * * *` | `refund_status=FAILED AND status=REFUNDING` |
| 对账 | `0 0 2 * * *` | 流水 SUM vs 聚合值比对，记录差异日志 |

### 5.3 内部触发接口

路径：`/api/internal/tasks/**`  
鉴权：`platform:task:execute`  
配置开关：`market.internal-task-api-enabled=true`（否则返回 404）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/internal/tasks/cancel-expired-trades` | 手动触发超时取消 |
| POST | `/api/internal/tasks/complete-shipped-orders` | 手动触发自动收货 |
| POST | `/api/internal/tasks/retry-refunds` | 手动触发退款重试 |
| POST | `/api/internal/tasks/reconcile-inventory` | 手动触发库存对账（dryRun 固定 true） |
| POST | `/api/internal/tasks/reconcile-wallets` | 手动触发钱包对账（dryRun 固定 true） |

### 5.4 平台运营只读查询

路径：`/api/platform/operations/**`  
鉴权：`platform:operation:read`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/platform/operations/trades` | 跨店交易查询（脱敏） |
| GET | `/api/platform/operations/orders` | 子订单查询（脱敏） |
| GET | `/api/platform/operations/payments` | 支付单查询 |
| GET | `/api/platform/operations/after-sales` | 售后单查询 |
| GET | `/api/platform/operations/business/{businessType}/{businessNo}` | 业务追踪聚合视图 |

### 5.5 验证标准
- 定时任务可在单实例正常执行，多实例不重复处理
- 内部接口在配置关闭时返回 404
- 运营查询脱敏手机号、不暴露密码/Token
- 对账发现差异时日志告警，不自动修正

---

## 阶段六：integration 跨线端口

### 目标
实现 B 线提供给 A 线的 3 个端口 Adapter，以及 B 线消费 A 线的 Fake。

### 6.1 B 线提供的端口（给 A 线调用）

路径：`integration/`

| 端口 | 所在包 | 实现逻辑 |
|------|--------|---------|
| `WalletProvisionPort` | `integration/payment/` | 注册时创建零余额钱包，重复调用返回同一钱包 |
| `ActiveShopBusinessPort` | `integration/order/` | 查 order_info + after_sale_request 判断是否有活跃业务 |
| `LockedReservationQueryPort` | `integration/order/` | 查 order_item WHERE reservation_status=LOCKED 汇总 |

每个端口 = 一个 interface + 一个 Adapter 实现类。

### 6.2 B 线消费的端口（A 线提供，先用 Fake）

| Fake | 模拟行为 |
|------|---------|
| `CurrentUserPortFake` | 返回预设用户 ID/状态 |
| `IdentitySummaryPortFake` | 返回脱敏用户摘要 |
| `ShopAccessPortFake` | 返回预设店铺权限结果 |
| `ShopSummaryPortFake` | 返回店铺摘要 |
| `ProductSnapshotPortFake` | 返回商品/SKU/价格快照 |
| `ProductAvailabilityPortFake` | 返回可购买/下架/停用结果 |
| `InventoryReservationPortFake` | 内存记录 LOCK/RELEASE/DEDUCT/RETURN，支持库存不足/重复键 |
| `InventoryTracePortFake` | 返回库存流水摘要 |

### 6.3 验证标准
- A 线调用 `WalletProvisionPort` 能创建钱包
- A 线调用 `ActiveShopBusinessPort` 能正确返回是否有活跃业务
- 关闭店铺前检查 → 有活跃订单/售后 → 关闭失败

---

## 开发顺序总览

```
阶段一: address 迁移 + aftersale DTO      (0.5天)
   │
阶段二: aftersale Service 核心逻辑        (2天)
   │
阶段三: aftersale Controller + 测试       (1.5天)
   │
阶段四: 修改已有 B 线模块                  (1天)
   │
阶段五: task 定时任务 + 运营查询           (2天)
   │
阶段六: integration 跨线端口              (1天)
```

---

## 每一阶段的提交建议

| 阶段 | 提交 message | 内容 |
|------|-------------|------|
| 1 | `refactor(b-line): migrate address to own package and define aftersale DTOs` | address 迁移 + DTO |
| 2 | `feat(aftersale): implement aftersale service with refund state machine` | Service 核心 |
| 3 | `feat(aftersale): add aftersale controllers and contract tests` | Controller + 测试 |
| 4 | `feat(order,payment): add active aftersale protection and wallet refund` | 已有模块修改 |
| 5 | `feat(task): implement scheduled tasks and platform operations API` | 定时任务 + 运营查询 |
| 6 | `feat(integration): add B-line port adapters and A-line port fakes` | 跨线端口 |

---

## 关键参考文档

| 文档 | 用途 |
|------|------|
| `docs/api/phase-2-api.md` | 售后/任务/运营查询的接口契约（最优先参考） |
| `docs/product/phase-2-requirements.md` | 售后业务规则、状态机、额度计算公式 |
| `docs/database/phase-1-design.md` | 26 表设计、CHECK 约束、事务与锁顺序 |
| `docs/api/common-contract.md` | 统一响应、鉴权、幂等约定 |
| `docs/api/dto-catalog.md` | 全部 DTO 字段定义 |
| `docs/development/backend-dual-track-development.md` | A/B 线边界、跨线端口清单、跨线事务规则 |
| `docs/development/backend-shared-infrastructure-guide.md` | 公共工具使用方法、禁止事项 |
| `sql/schema.sql` | 数据库基线 DDL |

## 已有可参考的代码

| 要实现 | 参考文件 |
|--------|---------|
| 幂等写操作 + 业务编号 | `InventoryService.inboundInventory()` |
| 钱包操作 + 流水记录 | `PaymentService.confirmPayment()` |
| 库存锁定/释放 + 流水 | `OrderService.ship()` |
| 商家权限 + shopId 范围 | `OrderService.shopOrders()` / `InventoryService.list()` |
| 状态机 + FOR UPDATE 锁 | `ProductReviewService` |
| DTO 胖文件格式 | `identity/dto/IdentityDtos.java` |
| Controller 幂等 Header | `TradeController.createTrade()` |
| Service 测试写法 | `CartServiceTests.java` |
| 契约测试写法 | `PhaseOneEndpointContractTests.java` |
