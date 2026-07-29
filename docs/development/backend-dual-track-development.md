# 时光商城后端双线并行开发规范

## 1. 目标与适用范围

本文把当前完整后端范围拆成两条相对独立的领域开发线，使两名后端开发者可以同时实现，而不需要按原 `phase-1`、`phase-2` 文件顺序串行等待。

两份产品分册和两份接口分册仍全部生效；分册只用于组织业务内容，不再用于划分后端人员。双线开发必须遵守以下原则：

- 按领域组织 Java 包，Controller 只处理 HTTP/DTO，Application/Service 负责用例、事务和状态机，Mapper 负责带范围的数据访问。
- 每张业务表只有一条线拥有写权限；另一条线只能通过冻结的 Java 端口调用能力。
- 跨线调用保持同一进程内的类型化方法调用，不通过 Controller 互调，也不复制 SQL 或 Mapper。
- 跨线写事务由业务动作的用例拥有者编排，提供方加入同一个 Spring `REQUIRED` 本地事务。
- 两条线都可以使用 Fake/Mockito 独立完成单元和契约测试；正式 Adapter 在集成阶段接入。
- `sql/schema.sql` 是不可回写基线；所有当前环境统一执行 `schema.sql -> schema2.sql`，存量环境只执行尚未应用的增量迁移。

## 2. 两个实施阶段

### 2.1 阶段一：契约与共享基座冻结

阶段一必须在两条业务线大规模编码前完成，目标是消除共同文件争用。冻结内容包括：

1. [统一 API 契约](../api/common-contract.md)、[DTO 字段目录](../api/dto-catalog.md)和两份接口分册。
2. `schema.sql -> schema2.sql` 的数据库最终状态、Java 枚举和业务字典。
3. 统一响应、分页、异常映射、Sa-Token 当前用户解析、店铺访问决策、幂等、时钟和编号生成基础接口。
4. 本文第 6 节的跨线端口、命令对象、返回对象和异常语义。
5. A/B 两线可直接使用的 Fake 实现和最小 Spring 测试配置。

共享基座只能包含通用技术能力，不得放入商品、订单、退款等领域 Service。建议包结构：

```text
org.dhu.shiguang_market
├── common
│   ├── api             # ApiResponse、PageView、错误响应
│   ├── error           # 公共错误码、异常映射
│   ├── security        # 当前登录身份和通用鉴权适配
│   ├── idempotency     # 幂等门面与存储适配
│   ├── time            # BusinessClock
│   └── number          # 业务编号生成门面
└── integration
    ├── identity
    ├── shop
    ├── product
    ├── inventory
    ├── payment
    └── order
```

`integration` 只保存跨线 Java 端口及其专用值对象，不保存实现。端口参数不得直接使用 MyBatis-Plus 实体，也不得复用 Web Request/View DTO。

阶段一完成标准：共享基座可编译，公共 MVC 测试通过，A/B 两线分别使用 Fake 启动自己的测试切片，所有端口方法签名已经双方评审。

共享基座已经落地的类、标准调用方式、禁止重复实现的能力以及修改 `common/**` 的评审要求，统一见[后端共享基础设施复用与扩展规范](backend-shared-infrastructure-guide.md)。后续领域开发应先复用该文档列出的入口，再评估是否需要扩展公共代码。

### 2.2 阶段二：双线实现与集成

阶段二中两名开发者分别完成 A/B 线的 Controller、DTO、Service、Mapper 和本线测试。前半段默认使用跨线 Fake；端口提供方实现正式 Adapter 后，再按第 10 节顺序接入跨线集成测试。

阶段二不是把治理与售后功能延后。两条线都从当前完整范围中领取自己的领域，A/B 各自同时覆盖原两个功能分册涉及的内容。

## 3. A 线：身份、治理、商品与库存

### 3.1 领域和接口归属

| 领域包 | A 线负责的能力 | 主要 API |
| --- | --- | --- |
| `identity` | 注册、登录、退出、当前会话、个人资料、平台用户、角色、权限、平台角色分配、踢下线 | `/api/auth/**`、`/api/users/me`、`/api/platform/rbac/**` |
| `shop` | 公开店铺、平台店铺管理、店铺状态、店铺成员和店铺范围访问决策 | `/api/shops/{shopId}`、`/api/platform/shops/**`、`/api/shops/{shopId}/members/**` |
| `product` | 公开目录/商品、平台类目品牌、商家 SPU/SKU、审核、上下架、禁售与历史 | `/api/categories/**`、`/api/brands`、`/api/products/**`、商家商品和平台商品/目录接口 |
| `inventory` | 库存查询、入库、调整、流水，以及供 B 线使用的预占、释放、扣减、退货回库 | `/api/shops/{shopId}/inventory/**` |

A 线拥有以下表的实体、Mapper 和写操作：

```text
sys_user
sys_role
sys_permission
sys_user_role
sys_role_permission
shop
shop_user
product_category
product_category_attribute
product_brand
product_spu
product_attribute_value
product_sku
product_status_history
inventory_stock
inventory_transaction
```

A 线不得直接写 B 线表。注册需要创建钱包时调用 `WalletProvisionPort`；关闭店铺前检查活跃订单/售后时调用 `ActiveShopBusinessPort`；人工调整锁定库存时调用 `LockedReservationQueryPort`。

### 3.2 A 线独立完成条件

A 线在没有 B 线正式实现时，使用以下 Fake：

- `WalletProvisionPortFake`：记录用户 ID，模拟创建零余额钱包或返回钱包已存在。
- `ActiveShopBusinessPortFake`：按测试场景返回是否存在未完结订单/售后。
- `LockedReservationQueryPortFake`：返回指定 SKU 当前有效预占总量。

A 线必须独立通过身份/RBAC、店铺范围、商品状态机、库存聚合与流水的单元测试、MVC 契约测试和 Mapper 测试。

## 4. B 线：买家交易、履约、资金、售后与任务

### 4.1 领域和接口归属

| 领域包 | B 线负责的能力 | 主要 API |
| --- | --- | --- |
| `address` | 本人地址增删改查、默认地址 | `/api/addresses/**` |
| `cart` | 加购、数量/选择修改、删除、购物车分组与实时有效性 | `/api/cart/**` |
| `order` | 结算预览、父交易、子订单、取消、买家订单、商家查单、发货、确认收货 | `/api/trades/**`、`/api/orders/**`、`/api/shops/{shopId}/orders/**` |
| `payment` | 钱包、模拟充值、支付尝试、确认支付、钱包流水和退款入账 | `/api/wallet/**`、`/api/payments/**`、交易支付接口 |
| `aftersale` | 资格、申请、撤销、退货物流、审核、确认收货、退款与重试 | `/api/after-sales/**`、商家售后接口 |
| `task` | 超时取消、自动收货、退款重试、库存/钱包对账、平台运营只读聚合 | `/api/internal/tasks/**`、`/api/platform/operations/**` |

B 线拥有以下表的实体、Mapper 和写操作：

```text
user_address
cart_item
trade_order
order_info
order_item
order_status_history
wallet_account
payment_order
wallet_transaction
after_sale_request
```

当前 `UserAddress` 实体位于 `identity/model`，开始 B 线实现前由共享基座提交一次性移动到 `address/model`；此移动不得与身份功能提交混在一起。B 线不得直接写商品、SKU、店铺、库存聚合或库存流水表。

### 4.2 B 线独立完成条件

B 线在没有 A 线正式实现时，使用以下 Fake：

- `CurrentUserPortFake` 和 `ShopAccessPortFake`：提供用户有效状态、本人判断和店铺权限结果。
- `IdentitySummaryPortFake` 和 `ShopSummaryPortFake`：为运营查询提供指定用户/店铺的最小脱敏摘要。
- `ProductSnapshotPortFake`：提供结算/下单需要的稳定商品、SKU、店铺和价格快照。
- `ProductAvailabilityPortFake`：模拟可购买、下架、SKU 停用和库存不足前置结果。
- `InventoryReservationPortFake`：在内存中记录 `LOCK/RELEASE/DEDUCT/RETURN`，按业务键保证幂等。
- `InventoryTracePortFake`：供平台运营聚合查询返回库存流水摘要。

B 线必须独立通过地址所有权、购物车、下单金额、支付/取消竞态、订单状态机、售后额度、退款和任务幂等的单元测试、MVC 契约测试和 Mapper 测试。

## 5. 表与代码所有权规则

| 对象 | 所有者 | 另一条线允许的操作 |
| --- | --- | --- |
| 领域实体、Mapper、领域枚举 | 对应领域线 | 只通过端口返回值使用，不导入 Mapper |
| Controller 和 Web DTO | API 所属领域线 | 可以评审，不直接修改 |
| `common/**` | 阶段一指定的集成维护人 | 提交独立小变更并由两线评审 |
| `integration/**` | 端口双方共同评审，集成维护人合并 | 实现或消费已冻结接口，不单方改签名 |
| `MarketEnums` | 集成维护人 | 新枚举先同步数据库/API 文档，再独立提交 |
| `schema.sql` | 历史基线，只读 | 任何人都不得回写已发布内容 |
| `schema2.sql` 及后续迁移 | 集成维护人 | 领域线提出迁移，单独评审和验证 |

禁止为了减少端口数量而让一条线直接读取或更新另一条线的 Mapper。平台运营只读聚合也必须使用端口；“只读”不是绕过所有权的理由。

## 6. 冻结的跨线端口

端口放在 `integration/<domain>`，命名按业务能力，不暴露实现技术。以下签名表示语义，不要求逐字使用该伪代码，但实现前必须冻结等价的 Java record/interface。

| 端口 | 提供方 | 消费方 | 语义 |
| --- | --- | --- | --- |
| `CurrentUserPort` | A/identity | B 全线 | 返回当前用户 ID、状态和稳定身份信息；非 `ACTIVE` 映射统一认证错误 |
| `IdentitySummaryPort` | A/identity | B/task/operations | 按用户 ID 批量返回运营列表所需的脱敏 `UserSummary`，不存在或无权披露时按冻结规则处理 |
| `ShopAccessPort` | A/shop | B/order、aftersale | 校验用户是否为路径店铺有效成员并拥有指定店铺权限 |
| `ShopSummaryPort` | A/shop | B/order、aftersale、task/operations | 按店铺 ID 批量返回订单/售后/运营响应所需的稳定店铺摘要，不提供写能力 |
| `ProductSnapshotPort` | A/product | B/cart、order | 批量返回商品/SKU/店铺、价格、图片、规格和可购买状态，输入输出按 SKU ID 稳定排序 |
| `ProductAvailabilityPort` | A/product | B/order | 下单事务中重新校验并锁定必要商品/店铺状态，禁止使用购物车旧快照 |
| `InventoryReservationPort` | A/inventory | B/order、aftersale、task | 按业务键原子执行预占、释放、发货扣减和退货回库，并返回变化后库存 |
| `InventoryTracePort` | A/inventory | B/task/operations | 只读查询业务号关联的库存流水摘要 |
| `WalletProvisionPort` | B/payment | A/identity | 注册事务中为新用户创建唯一零余额钱包；重复调用返回同一钱包 |
| `ActiveShopBusinessPort` | B/order/aftersale | A/shop | 判断店铺是否仍有未完结订单或活跃售后，供关闭店铺使用 |
| `LockedReservationQueryPort` | B/order | A/inventory | 返回指定 SKU 所有 `LOCKED` 订单明细数量合计，供人工调整校验 |

端口值对象只包含用例必需字段：内部 ID 使用 `long/Long`，金额使用 `BigDecimal`，时间使用 `OffsetDateTime` 或项目统一时间类型，状态使用稳定枚举。端口不得返回 `Map<String,Object>`、数据库实体、Web `ApiResponse` 或分页 JSON 包装。

## 7. 跨线事务与锁责任

| 业务动作 | 事务编排方 | 跨线调用 | 必须保证 |
| --- | --- | --- | --- |
| 用户注册 | A/identity | `WalletProvisionPort` | 用户、`CUSTOMER` 角色和钱包同一事务成功或回滚 |
| 创建交易 | B/order | 商品可用性、快照、库存预占 | 重新定价，按 SKU ID 升序预占；任一失败整单回滚 |
| 取消/超时取消 | B/order/task | 库存释放 | 状态条件、订单明细和 `RELEASE` 流水在同一事务，重复执行不重复释放 |
| 商家发货 | B/order | 店铺权限、库存扣减 | 子订单状态、明细 `DEDUCTED`、库存和流水同一事务 |
| 仅退款 | B/aftersale | 库存释放（未发货时） | 退款、累计退款、状态和释放库存幂等；批准入口强制幂等键 |
| 退货退款 | B/aftersale | 退货回库 | 确认收货只回库一次；退款重试不得再次回库 |
| 关闭店铺 | A/shop | 活跃业务查询 | 先锁定店铺，再检查活跃业务；下单校验必须读取相同店铺状态，避免关闭后新建交易 |
| 库存人工调整 | A/inventory | 有效预占查询 | 调整后的锁定量不得小于有效预占合计 |

跨线 Adapter 不得使用 `REQUIRES_NEW` 拆开上述原子动作。事务编排方负责 `@Transactional`，提供方使用默认 `REQUIRED` 加入事务；所有数据库锁继续遵守[数据库设计规范](../database/phase-1-design.md)的统一顺序。

端口调用失败必须抛出可分类的应用异常，由编排方映射成冻结错误码。不得把 MyBatis、SQL、Redis 或 HTTP 客户端异常直接传播给 Controller。

## 8. Spring Boot 实现规则

- 包按领域组织，例如 `order/controller`、`order/dto`、`order/service`、`order/mapper`、`order/adapter`，不创建全局 `controller` 或 `service` 大包。
- 使用构造器注入，依赖字段为 `private final`；禁止字段注入和静态 Service Locator。
- Controller 只做参数绑定、Bean Validation、鉴权入口和调用 Application Service，不直接查表或开启事务。
- Application/Service 保持无状态，事务放在最小但完整的业务用例边界。
- Response DTO 明确映射，不直接序列化实体；跨线端口 DTO 与 HTTP DTO 分离。
- Mapper SQL 必须参数化；店铺资源查询同时包含资源 ID 和 `shop_id` 范围条件。
- 配置使用 `@ConfigurationProperties` 和环境变量，不在代码中硬编码密码、Token、Redis Key 前缀或任务开关。
- 日志使用 SLF4J 参数化消息，包含 `requestId`、动作和业务编号，不记录密码、Token、完整手机号或地址。

## 9. 两条线的测试边界

每条线的普通提交不得依赖另一条线尚未完成的正式 Adapter；应依赖 Fake 或 Mock。最低测试要求：

| 测试层级 | A 线 | B 线 |
| --- | --- | --- |
| 单元测试 | RBAC 决策、店铺状态、商品状态机、规格规范化、库存变化 | 金额/快照、订单状态机、支付竞态、售后额度、退款状态机、任务幂等 |
| MVC 契约测试 | A 线全部 API 的路径、鉴权、字段和错误码 | B 线全部 API 的路径、鉴权、字段和错误码 |
| Mapper 测试 | A 表范围、软删除、唯一键、锁 SQL | B 表所有权、分页、状态条件、锁 SQL |
| 领域集成测试 | A 线真实 MySQL + B 端口 Fake | B 线真实 MySQL/Redis + A 端口 Fake |
| 并发测试 | 商品版本、库存入库/调整、RBAC 最后管理员 | 同 SKU 下单、支付/取消、售后审核/退款、任务抢占 |

Fake 必须模拟业务语义，不能永远返回成功。例如库存 Fake 至少支持库存不足、重复业务键返回原结果、释放超过预占失败；商品 Fake 至少支持店铺暂停、商品下架、SKU 停用和价格变化。

## 10. 集成顺序与跨线场景

合并和联调按以下顺序执行：

1. 合并阶段一共享基座、迁移、端口接口及 Fake。
2. A/B 两线各自合并不依赖正式跨线 Adapter 的领域实现和测试。
3. A 线实现身份摘要、店铺访问/摘要、商品、库存端口 Adapter；B 线实现钱包、活跃业务和有效预占查询端口 Adapter。
4. 在独立小提交中替换集成测试配置中的 Fake，接入正式 Adapter。
5. 最后执行跨线事务、并发和端到端验收；不得在该步骤顺手重构两条线内部代码。

必须覆盖的跨线集成场景：

1. 注册任一步骤失败时用户、角色和钱包全部回滚；重复钱包创建不会产生第二个账户。
2. 跨店下单按最新价格生成快照并锁定库存；任一 SKU 失败时不产生父交易和半锁库存。
3. 支付与取消并发只有一个成功状态；取消和超时任务不会重复释放库存。
4. 发货将订单明细和库存预占同时转为已扣减，不允许部分成功。
5. 未发货仅退款只释放批准数量；重复批准/重试不重复退款或释放。
6. 退货确认只回库一次，钱包退款失败后重试不再次增加库存。
7. 有活跃订单或售后时店铺关闭失败；关闭成功后新的下单校验失败。
8. 库存人工调整不能把锁定库存降到有效订单预占总量以下。
9. 平台运营业务追踪能够聚合 B 线交易/资金与 A 线库存流水，但全程只读。

## 11. Git 与协作规则

- 两名开发者只在自己拥有的领域目录中进行常规开发，避免同时修改同一 DTO、枚举、迁移或公共配置。
- `common/**`、`integration/**`、`MarketEnums`、`schema2.sql` 和后续迁移必须使用独立小提交，不与大段领域实现混合。
- 端口签名变更需要提供方、消费方和集成维护人共同评审，并在同一变更中更新 Fake、正式 Adapter、测试和本文。
- 一条线需要另一条线新增能力时，先修改端口契约和 Fake；消费方不能临时导入提供方 Mapper 或 Service。
- 合并前各线自行运行本线测试；合并正式 Adapter 后由集成维护人运行全量 Maven 测试和跨线并发场景。
- 发生冲突时优先保留已冻结外部 API 和数据库语义，不能以减少 Git 冲突为理由改变业务契约。

## 12. 双线完成定义

两名后端可以分别声明“本线完成”，前提是：

- 本线拥有的全部当前版本接口已经实现，且没有占用另一条线表的 Mapper。
- 本线单元、MVC、Mapper、集成和关键并发测试通过。
- 所有消费端口均可由 Fake 驱动完成本线业务测试。
- 所有提供端口均有正式 Adapter 和契约测试。
- API DTO、错误码、幂等、状态机和排序规则与文档一致。

整个后端完成还必须满足：所有正式 Adapter 接入，本文第 10 节跨线场景通过，新空库按 `schema.sql -> schema2.sql` 可启动，存量库只执行增量脚本可升级，两个产品分册的端到端验收全部通过。
