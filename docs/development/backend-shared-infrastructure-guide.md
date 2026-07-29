# 时光商城后端共享基础设施复用与扩展规范

## 1. 目的和适用范围

本文面向继续开发时光商城后端的开发者，说明当前代码库已经提供的共享基础设施、正确接入方式和扩展边界。新增接口、补充领域功能、修复缺陷或编写测试时，应先检查本文列出的公共能力，不得在业务包中重复实现响应包装、认证鉴权、异常转换、幂等、内容清洗、规格规范化、编号生成等机制。

本文是实现规范，不替代业务契约。发生冲突时，按以下顺序处理：

1. HTTP 路径、DTO、状态码、错误码和幂等要求以 `docs/api/` 为准。
2. 字段、唯一约束、状态机、事务和锁顺序以 `docs/database/phase-1-design.md` 及 SQL 迁移为准。
3. 领域归属和跨线调用以 `docs/development/backend-dual-track-development.md` 为准。
4. 本文约束公共能力的复用方式以及 Controller、Service、Mapper 的实现边界。

## 2. 不可偏离的技术决策

### 2.1 认证鉴权只使用 Sa-Token

本项目只使用 Sa-Token 处理登录态、Token、角色和权限，不使用 Spring Security。后续开发必须遵守以下规则：

- 不得引入 `spring-security-*` 依赖。
- 不得创建 `SecurityFilterChain`、认证过滤器链或第二套用户上下文。
- 不得引用 `org.springframework.security.*`。
- 登录、退出、Token 获取和登录态检查使用 Sa-Token 的 `StpUtil`。
- 密码散列和校验统一使用 `common/security/PasswordService`；其底层是 Favre BCrypt，当前 cost 为 12。不得在业务 Service 中直接调用 BCrypt，也不得新增另一种密码摘要格式。

`common/config/SaTokenConfig` 已注册全局 `SaInterceptor`。公开路径在这里统一放行，其余 `/api/**` 默认要求登录。新增公开接口时，必须同步评审放行范围和接口文档；不得通过在 Controller 中忽略鉴权或手工解析 Token 来实现公开访问。

### 2.2 数据访问使用 MyBatis-Plus

本项目的数据访问基于 MyBatis-Plus，不引入 JPA Repository。Mapper 应继续使用 `BaseMapper`、类型安全 Wrapper 或参数化 SQL，并遵循数据库文档中的逻辑删除、唯一约束和锁顺序。

### 2.3 公共契约优先于局部便利

以下行为即使能短期跑通，也禁止合入：

- Controller 手写成功或错误 JSON。
- 直接把数据库实体作为 HTTP 响应。
- Controller 直接调用 Mapper，或 Controller 互相调用。
- 在业务包中复制一份鉴权、异常、Redis 幂等锁、HTML 清洗或编号生成代码。
- 为了复用 SQL，跨领域导入并操作不属于本领域的 Mapper。
- 查询出一页数据后在 Java 中做权限或状态过滤，同时保留数据库查询得到的原 `total`。

## 3. 新接口标准开发流程

新增或修改一个接口时，按以下顺序实现：

1. 在 `docs/api/` 确认路径、鉴权级别、请求 DTO、响应 DTO、状态码、错误码以及是否要求 `Idempotency-Key`。
2. 在 `docs/database/` 和 SQL 中确认字段、逻辑删除、唯一约束、状态机、事务边界和锁顺序。
3. 判断接口属于公开、普通登录、平台权限还是店铺权限，按第 5 节接入现有鉴权服务。
4. 定义请求和响应 DTO；请求使用 Bean Validation，响应显式完成 ID、金额和时间格式化。
5. Controller 只负责 HTTP 绑定、`@Valid`、请求头、路径变量、HTTP 状态和调用 Service。
6. Service 负责当前用户、权限、业务校验、状态机、事务、幂等和 DTO 映射编排。
7. Mapper 在数据库查询阶段完成用户/店铺范围、状态、逻辑删除和分页筛选；需要并发控制时使用文档约定的行锁、版本或条件更新。
8. 为接口补齐契约、权限、状态机、事务、范围和特殊语义测试，并同步接口文档。

常用能力入口如下：

| 需求 | 必须使用 |
| --- | --- |
| 当前用户 ID 或账号状态 | `CurrentUserService.id()` / `user()` |
| 平台权限 | `CurrentUserService.requirePermission(...)` |
| 店铺成员和店铺权限 | `ShopAccessService.require(...)` |
| 密码散列和校验 | `PasswordService` |
| 成功响应 | `ApiResponse.success(...)` |
| 分页响应 | `PageView.of(...)` |
| ID、金额、时间和空白处理 | `Formatters` |
| 预期业务失败 | `BusinessException` |
| 幂等写操作 | `IdempotencyService.execute(...)` |
| 可恢复的幂等业务编号 | `IdempotencyService.businessNo(...)` |
| 普通业务编号 | `NumberGenerator.next(...)` |
| 单个图片 URL | `ContentSafety.imageUrl(...)` |
| 图片 URL 数组 | `ContentSafety.imageUrls(...)` |
| 商品详情 HTML | `ContentSafety.detailHtml(...)` |
| SKU 规格身份 | `SpecNormalizer.normalize(...)` 和 `key(...)` |
| 请求链路 ID 和当前时间 | `RequestContext`，由 `RequestIdFilter` 管理生命周期 |

## 4. 统一响应、格式和分页

### 4.1 成功响应

所有成功响应使用 `common/api/ApiResponse`：

```java
return ApiResponse.success(view);
```

创建资源返回 `201 Created`：

```java
return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(view));
```

删除成功返回 `200` 和 `ApiResponse.success(null)`，不要改成 `204 No Content`，否则会破坏统一响应契约。

Controller 不应自行填写 `code`、`message`、`requestId` 或 `timestamp`。`ApiResponse.success(...)` 和 `ApiErrorResponse.of(...)` 会从 `RequestContext` 取得统一元数据。

### 4.2 ID、金额和时间

外部响应 DTO 不得直接暴露 Java `long` ID 或未格式化的 `BigDecimal`：

```java
new ExampleView(
        Formatters.id(entity.getId()),
        Formatters.money(entity.getAmount()),
        Formatters.time(entity.getCreatedAt()));
```

- ID 使用 `Formatters.id(...)` 输出十进制字符串，避免前端 JavaScript 精度丢失。
- 金额使用 `Formatters.money(...)` 输出固定两位小数字符串。
- 数据库 `LocalDateTime` 使用 `Formatters.time(...)` 输出 UTC+08:00 的 `OffsetDateTime`。
- 业务中需要当前时间时使用 `RequestContext.now()`，其时区为 `Asia/Shanghai`。

### 4.3 分页

MyBatis-Plus 查询完成后使用 `PageView.of(page, mappedItems)`。筛选条件必须在分页 SQL 之前生效，`total` 必须表示同一组筛选条件下的记录总数。

禁止先按宽条件分页，再在 Java 中移除无权访问、已失效或状态不符的数据。这会造成空页、少数据和错误的 `totalPages`。

### 4.4 Request ID

`common/config/RequestIdFilter` 会接受合法的 `X-Request-Id`，否则生成 UUID，并把同一个值写入响应头和响应体。业务代码不得生成第二套 request ID，也不得自行管理 `RequestContext` 的 ThreadLocal 生命周期。

日志需要关联请求时，记录 `RequestContext.requestId()`、动作和业务编号；不得记录密码、Token、密码摘要、完整手机号、完整地址或 Redis Key。

## 5. Sa-Token 认证与权限

### 5.1 四种接口级别

| 接口级别 | 标准实现 |
| --- | --- |
| 公开接口 | 在 `SaTokenConfig` 的放行路径中显式登记，并与 API 文档保持一致 |
| 普通登录接口 | 全局拦截器检查登录；Service 调用 `CurrentUserService.id()` 再校验数据库账号状态 |
| 平台权限接口 | Service 调用 `CurrentUserService.requirePermission("platform:...")` |
| 店铺权限接口 | Service 调用 `ShopAccessService.require(shopId, "shop:...")` |

不要只依赖全局拦截器的 `StpUtil.checkLogin()`。登录后的账号可能已被锁定、禁用或删除，`CurrentUserService.id()` 会重新读取账号并统一映射为 `AUTH_ACCOUNT_LOCKED` 或 `AUTH_ACCOUNT_DISABLED`。

### 5.2 平台权限

`common/security/MarketStpInterface` 从数据库加载平台角色和平台权限。业务 Service 使用：

```java
currentUser.requirePermission("platform:product:audit");
```

该方法先校验账号状态，再调用 `StpUtil.checkPermission(...)`。不要自行读取角色表后写 `if (roleCode.equals(...))`，也不要在 Controller 重复权限判断。

### 5.3 店铺权限和数据范围

店铺权限不是平台权限。标准调用为：

```java
Shop shop = shopAccess.require(shopId, "shop:inventory:manage");
```

`ShopAccessService` 会同时校验当前账号、路径店铺、有效店铺成员关系以及该成员的店铺权限。越权访问统一按 `RESOURCE_NOT_FOUND` 处理，避免泄露资源是否存在。

通过权限校验后，Mapper 查询仍必须使用相同 `shop_id` 限定资源：

```java
mapper.selectOne(new LambdaQueryWrapper<Resource>()
        .eq(Resource::getId, resourceId)
        .eq(Resource::getShopId, shopId));
```

不能先校验路径中的 `shopId`，随后只按裸 `resourceId` 查询。该规则同时防止路径与资源归属不一致以及越权访问。`SUPER_ADMIN` 只代表平台角色，不自动获得任意店铺的数据范围。

## 6. 业务异常与依赖异常

预期内的业务失败抛出 `common/exception/BusinessException`。按 HTTP 语义选择工厂方法：

```java
throw BusinessException.badRequest("VALIDATION_FAILED", "请求内容不合法");
throw BusinessException.forbidden("OPERATION_FORBIDDEN", "当前状态不允许该操作");
throw BusinessException.notFound("RESOURCE_NOT_FOUND", "资源不存在");
throw BusinessException.conflict("RESOURCE_CONFLICT", "资源状态已变化");
throw BusinessException.unprocessable("INVENTORY_INSUFFICIENT", "库存不足");
```

错误码必须来自已冻结 API 契约；不要为同一种失败在不同 Service 中创造近义错误码。Controller 不捕获这些异常，也不把异常转换成 `Map` 或 `ResponseEntity`；`GlobalExceptionHandler` 负责统一响应。

当前全局映射包括：

| 异常类别 | 对外结果 |
| --- | --- |
| Bean Validation | `400 VALIDATION_FAILED`，包含字段详情 |
| JSON、参数类型、请求头格式错误 | `400 BAD_REQUEST` |
| Sa-Token 未登录、过期、被顶替、被踢下线 | 对应 `401 AUTH_*` |
| Sa-Token 角色或权限不足 | `403 AUTH_PERMISSION_DENIED` |
| 数据唯一或关系约束冲突 | `409 RESOURCE_CONFLICT` |
| 数据源/Redis 连接失败或查询超时 | `503 DEPENDENCY_UNAVAILABLE` |
| 未分类异常 | `500 INTERNAL_ERROR` |

不要把 SQL 语法错误、缺表、字段映射错误等开发缺陷捕获后伪装成 `503`。对外响应和业务日志不得泄露 SQL、表名、堆栈、Token、密码摘要或内部 Redis Key；服务端可以记录受控异常堆栈，但日志内容仍需脱敏。

## 7. 幂等写接口

API 文档标记为幂等的写接口，Controller 必须强制读取请求头：

```java
@RequestHeader("Idempotency-Key") String idempotencyKey
```

Service 使用统一门面包裹完整业务动作：

```java
return idempotency.execute(
        userId,
        "POST",
        normalizedPath,
        idempotencyKey,
        request,
        ResponseView.class,
        () -> executeBusinessAction(request, idempotencyKey));
```

`normalizedPath` 必须是稳定的规范路径。包含路径参数时，应由确定值构造，例如 `"/api/payments/" + paymentId + "/confirm"`；不得使用带查询参数的原始 URL，也不得让等价请求产生不同路径字符串。

`IdempotencyService` 已统一实现：

- `Idempotency-Key` 格式校验，当前允许 1..64 位字母、数字、点、下划线和连字符。
- 按用户、HTTP 方法、规范路径和幂等键划分作用域。
- 请求体 SHA-256 摘要以及同键不同请求的 `IDEMPOTENCY_KEY_REUSED` 冲突。
- 并发执行锁、等待和依赖不可用处理。
- 成功响应重放。
- 确定性 4xx 业务失败重放。
- 5xx 和未分类运行时异常不缓存，原键可重试。
- 事务成功提交后才缓存成功结果；回滚不得留下成功响应。

不得只调用 Redis `setIfAbsent`、自行写分布式锁或仅依赖前端禁用按钮。Redis 幂等层不是数据库约束的替代品：业务表仍必须保留业务号/业务键唯一约束和状态条件更新，作为最终一致性保护。

需要让重试恢复同一业务编号时，使用：

```java
String tradeNo = idempotency.businessNo("TR", userId, idempotencyKey);
```

前缀属于稳定业务契约，新增或修改前需同步评审。幂等接口至少测试成功重放、同键不同请求冲突、并发/唯一约束以及 5xx 后原键重试。

## 8. URL、HTML 和结构化输入安全

### 8.1 图片 URL

所有由用户提交并将被前端展示或持久化的图片 URL 必须经过 `ContentSafety`：

```java
String logoUrl = contentSafety.imageUrl("logoUrl", request.logoUrl());
List<String> galleryUrls = contentSafety.imageUrls("galleryUrls", request.galleryUrls(), 10);
```

当前规则为：

- URL 最大长度 1024。
- 默认只允许绝对 HTTPS URL，禁止 user-info 等含糊地址。
- 本地开发只有显式设置 `CONTENT_ALLOW_LOCAL_HTTP=true` 后，才允许 `localhost`、`127.0.0.1` 和 `::1` 的 HTTP 地址。
- 数组保持原顺序，不允许空 URL 或重复 URL；商品 `galleryUrls` 最多 10 个。
- 生产环境不得为了临时调试开启本地 HTTP 例外。

### 8.2 商品详情 HTML

商品详情统一调用：

```java
String safeHtml = contentSafety.detailHtml(request.detailHtml());
```

该方法使用 Jsoup 白名单保留允许的内容标签，删除 `script`、`iframe`、内联事件、任意 `style` 和危险 URL，并再次校验图片地址。业务 Service 不得自行编写正则清理 HTML，也不得在清理失败时保存原始 HTML。

### 8.3 SKU 规格

SKU `spec` 的规范化和身份键必须统一生成：

```java
Map<String, String> normalized = SpecNormalizer.normalize(request.spec());
String specKey = SpecNormalizer.key(normalized);
```

规范规则包括 1..10 个键值、trim、Unicode NFC、键和值长度 1..64、规范化后键不可重复、按键排序以及 SHA-256。禁止自行拼 JSON、依赖 Map 迭代顺序或创建另一套 hash 算法。

`SpecNormalizer` 对非法输入抛 `IllegalArgumentException`。业务层应在有明确字段语义的位置将其转换为 `VALIDATION_FAILED`；不要让实现细节错误消息直接作为外部契约。相同 SPU 的规格身份即使对应 SKU 已逻辑删除也不可复用，最终由数据库唯一约束保护。

## 9. DTO、PATCH 和显式清空

### 9.1 请求与响应 DTO

- 请求 DTO 使用 `jakarta.validation` 声明结构约束，并在 Controller 参数上使用 `@Valid`。
- `application.yaml` 已启用未知 JSON 字段失败，客户端拼错字段应返回 `BAD_REQUEST`，不要在局部关闭该行为。
- 响应 DTO 只包含契约允许的字段；不得直接序列化实体、逻辑删除字段、密码摘要或内部版本字段。
- Web DTO 与跨领域端口对象分离；公共 HTTP 摘要优先复用 `common/api/CommonViews`。

### 9.2 PATCH 的三态语义

PATCH 请求必须区分：

| JSON 状态 | 业务含义 |
| --- | --- |
| 字段缺失 | 不修改原值 |
| 字段存在且有值 | 更新为新值 |
| 字段存在且为 `null` | 清空可空字段；不可空字段返回 `VALIDATION_FAILED` |

普通 record 或只判断 `value == null` 无法区分字段缺失和显式 `null`。应沿用 `@JsonSetter` 加 `...Present` 标记的模式：

```java
public final class UpdateRequest {
    private String note;
    private boolean notePresent;

    @JsonSetter("note")
    public void setNote(String note) {
        this.note = note;
        this.notePresent = true;
    }

    public String note() { return note; }
    public boolean hasNote() { return notePresent; }
}
```

可参考 `IdentityDtos.UpdateProfileRequest`、`ShopProductDtos.UpdateSkuRequest` 和 `CartDtos.UpdateCartItemRequest`。

### 9.3 MyBatis-Plus 可空字段

项目全局配置为：

```yaml
mybatis-plus:
  global-config:
    db-config:
      update-strategy: not_null
```

因此实体中允许通过 PATCH 显式清空的字段必须声明：

```java
@TableField(updateStrategy = FieldStrategy.ALWAYS)
private String note;
```

否则 DTO 正确识别了显式 `null`，MyBatis-Plus 仍会忽略该字段，数据库无法清空。新增可空字段时必须同时检查实体映射、Service 更新逻辑和 PATCH null 测试。现有参考包括 `SysUser`、`Shop`、`ProductBrand`、`ProductCategoryAttribute`、`ProductSpu` 和 `ProductSku`。

## 10. Controller、Service、Mapper 和事务边界

### 10.1 Controller

Controller 只负责：

- `@RequestBody`、`@PathVariable`、`@RequestParam` 和请求头绑定。
- Bean Validation。
- 选择 `200` 或 `201` 等契约规定的 HTTP 状态。
- 调用一个面向用例的 Service 方法并包装统一成功响应。

Controller 不直接查询 Mapper、不持有事务、不编排多个领域写操作，也不捕获业务异常生成错误响应。

### 10.2 Service

Service 负责：

- 获取当前用户并执行平台/店铺权限检查。
- 业务规则、状态机、金额计算、并发条件和幂等。
- 在最小但完整的用例边界使用 `@Transactional`。
- 调用本领域 Mapper 或冻结的跨领域端口。
- 把实体和查询结果显式映射为响应 DTO。

Service 保持无状态，依赖使用构造器注入和 `private final` 字段。跨领域能力不得通过调用对方 Controller 或复制对方 SQL 获得。

### 10.3 Mapper

Mapper 负责参数化数据访问，并把安全范围下推到 SQL：

- 本人资源同时包含资源 ID 和 `user_id`。
- 店铺资源同时包含资源 ID 和路径 `shop_id`。
- 状态、逻辑删除和搜索条件在分页前过滤。
- 需要串行化的状态迁移按数据库文档使用 `FOR UPDATE` 或条件更新。
- 更新结果行数必须参与并发冲突判断，不能无视版本或状态条件失败。

禁止字符串拼接用户输入生成 SQL。确需动态 SQL 时使用 MyBatis 参数绑定或 Wrapper；表名、排序字段等不能参数化的部分必须来自代码白名单。

### 10.4 事务和幂等的组合

事务边界放在拥有完整业务动作的 Service。幂等 `action` 应覆盖该动作的数据库写入；`IdempotencyService` 会在事务提交后记录成功响应。不要使用 `REQUIRES_NEW` 把文档要求原子完成的库存、订单、支付或钱包步骤拆开。

数据库唯一约束、行锁和状态条件是并发正确性的基础；Java 预检查只用于提供更明确的错误，不能替代数据库保护。

## 11. 公共目录职责

| 目录或类 | 负责 | 不负责 |
| --- | --- | --- |
| `common/api` | HTTP 成功/错误包装、分页和稳定公共 View | 领域状态机、数据库实体 |
| `common/config/SaTokenConfig` | Sa-Token 路由登录校验和 CORS | 店铺数据范围、具体业务权限 |
| `common/config/RequestIdFilter` | request ID 接收、生成、响应头和上下文清理 | 业务编号 |
| `common/exception` | 稳定异常类型和全局 HTTP 映射 | 吞掉或伪装开发错误 |
| `common/security/CurrentUserService` | 当前账号状态和平台权限 | 店铺成员范围 |
| `common/security/ShopAccessService` | 路径店铺成员与店铺权限 | 替代 Mapper 的 `shop_id` 条件 |
| `common/security/MarketStpInterface` | 向 Sa-Token 提供平台角色和权限 | 把店铺权限合并为全局权限 |
| `common/security/PasswordService` | BCrypt 密码摘要兼容入口 | Token、会话或业务签名 |
| `common/service/IdempotencyService` | 请求摘要、并发锁、结果重放和失败策略 | 替代数据库唯一约束 |
| `common/util/ContentSafety` | 图片 URL 和详情 HTML 安全处理 | 任意富文本编辑或文件上传 |
| `common/util/Formatters` | 外部 ID、金额、时间和 trim 规则 | 业务金额计算 |
| `common/util/SpecNormalizer` | SKU 规格规范形态和稳定身份键 | 决定 SKU 是否可售 |
| `common/util/NumberGenerator` | 普通公开业务编号 | 分布式全局序列保证 |
| `common/util/RequestContext` | 单次请求的 request ID 和统一时区时间 | 用户登录上下文 |
| `common/model/MarketEnums` | 与数据库/API 对齐的稳定业务枚举 | 临时页面展示文案 |

向 `common/**` 新增能力前，先确认它确实被多个领域复用，且不包含商品、订单、支付等领域规则。仅一个领域使用的代码应留在该领域包中。

## 12. 测试与交付检查清单

新增接口或修改共享能力时，按风险选择并补齐以下测试：

- 路径、HTTP 方法、状态码、鉴权级别和 DTO 形状的 MVC 契约测试。
- 请求 Bean Validation、未知字段、非法枚举、金额、ID 和时间格式测试。
- 未登录、账号锁定/禁用、平台权限以及店铺成员/权限测试。
- 本人资源和店铺资源的 Mapper 范围测试，包含“资源存在但属于其他主体”的场景。
- 状态机合法迁移、非法迁移、版本冲突和事务回滚测试。
- 分页筛选后的 `items`、`total` 和 `totalPages` 一致性测试。
- PATCH 字段缺失、显式值、显式 `null` 以及实体实际清空测试。
- 逻辑删除不可见、数据库唯一约束和重复请求测试。
- 幂等成功重放、同键不同请求、并发执行、确定性 4xx 重放和 5xx 重试测试。
- 错误响应稳定性以及敏感实现信息不泄露测试。

现有测试可作为入口：

- `PhaseOneEndpointContractTests`：接口路径和基本契约。
- `ContractUtilityTests`：公共格式化和安全工具。
- `EntityMappingTests`：实体字段、逻辑删除和更新策略。
- `IdempotencyServiceTests`：幂等语义。
- `CartServiceTests`、`InventoryServiceTests`、`OrderServiceTests`：领域用例、范围和状态。
- `GlobalExceptionHandlerTests`：异常到外部错误响应的映射。

提交前至少执行：

```bash
git diff --check
'/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn' test
rg -n "org\.springframework\.security|SecurityFilterChain|spring-security" pom.xml src
```

最后一条命令必须无命中。若本机 Maven 路径不同，可使用仓库可用的 Maven 3 执行等价的 `mvn test`。

## 13. 修改共享基础设施的评审规则

`common/**` 会影响多个领域，修改时需要独立评估兼容性：

1. 先列出当前调用方，确认是否会改变返回格式、异常类型、错误码、缓存 Key、权限语义或时间/金额格式。
2. 保持公共方法签名和现有行为向后兼容；必须破坏兼容时，同一变更内完成全部调用方迁移。
3. 同步更新本文、相关 API/数据库文档以及公共单元测试。
4. 权限放行、错误码、业务编号前缀、幂等作用域和缓存结构属于稳定契约，必须由两条后端线共同评审。
5. 公共代码变更应保持小而独立，不夹带单一领域的大量业务修改。
6. 新公共抽象至少应消除真实重复或稳定跨领域契约；不要为了预想中的未来需求提前创建空接口和通用 `Map<String, Object>` 门面。

## 14. 常见错误实现与正确替代

| 错误实现 | 正确替代 |
| --- | --- |
| 在 Controller 中 `try/catch` 后返回自定义错误 Map | 抛 `BusinessException`，交给 `GlobalExceptionHandler` |
| 直接返回实体 | 显式映射响应 DTO，并用 `Formatters` 处理 ID、金额和时间 |
| 直接使用 `StpUtil.getLoginIdAsLong()` 完成所有鉴权 | 普通用户走 `CurrentUserService`，平台/店铺权限走对应公共服务 |
| 因为是 `SUPER_ADMIN` 就读取任意店铺资源 | 仍校验店铺成员权限和相同 `shop_id` 数据范围 |
| 权限检查后只按资源 ID 查询 | Mapper 同时按资源 ID 和用户/店铺范围查询 |
| 每个 Service 自己写 Redis 幂等锁 | 使用 `IdempotencyService.execute(...)`，并保留数据库唯一约束 |
| 用时间戳加随机数随意拼业务编号 | 使用 `NumberGenerator` 或幂等 `businessNo(...)` |
| 用正则删除 `<script>` 作为 HTML 清洗 | 使用 `ContentSafety.detailHtml(...)` |
| 自己序列化 SKU spec 并计算 hash | 使用 `SpecNormalizer.normalize(...)` 和 `key(...)` |
| PATCH 只用 `null` 判断是否修改 | 使用 `@JsonSetter` 和 presence 标记表达三态 |
| DTO 允许显式清空，但实体沿用全局 `not_null` | 对可空实体字段增加 `FieldStrategy.ALWAYS` 并测试实际 SQL |
| 分页后在 Java 中过滤越权数据 | 把范围和筛选条件下推到分页 SQL |
| 引入第二套安全框架或手工 Token 过滤器 | 继续扩展 `SaTokenConfig`、`CurrentUserService` 和 `ShopAccessService` |

开发者在新增工具类前，应先从第 3 节的能力入口表和第 11 节的目录职责中查找现有入口；不能满足需求时，再按第 13 节评审是否扩展共享基础设施。
