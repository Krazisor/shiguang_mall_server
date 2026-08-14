# 优惠券模板归档需求及 API 文档

## 1. 文档目的

本文定义优惠券模板新增 `ARCHIVED` 状态后的产品规则、HTTP API、查询可见性、状态转换和实现边界，用于隐藏误建或已经废弃的模板，同时完整保留用户权益、交易、预算和审计历史。

本文描述已经实现的归档增量。正式枚举、接口、数据库约束和业务语义已同步到现有[优惠券模块 API](../api/coupon-api.md)、[优惠券数据库设计](../database/coupon-design.md)、[优惠券需求](../product/coupon-requirements.md)和 [DTO 字段目录](../api/dto-catalog.md)。

本文集中说明以下新增语义：

- `CouponTemplateStatus` 增加 `ARCHIVED`；
- 店铺端和平台端增加模板归档动作接口；
- 模板管理列表默认隐藏归档记录；
- 归档后的可读、可复制及已发行权益处理规则。

本文未明确修改的认证、权限、ID、时间、金额、分页、统一响应、幂等、乐观锁、优惠计算、退款返券和审计规则，全部继承[统一 API 契约](../api/common-contract.md)及现有优惠券文档。主 API 文档和 DTO 目录是对外正式契约，本文用于集中解释归档需求和验收边界。

## 2. 背景与现状

### 2.1 改造前能力

改造前 `CouponTemplateStatus` 只有：

```text
DRAFT
ACTIVE
PAUSED
ENDED
```

改造前模板没有删除接口，也没有软删除字段。不同状态支持的管理动作如下：

| 当前状态 | 当前动作 |
| --- | --- |
| `DRAFT` | `EDIT`、`ACTIVATE`、`COPY` |
| `ACTIVE` | `PAUSE`、`END`、`COPY` |
| `PAUSED` | `RESUME`、`END`、`COPY` |
| `ENDED` | `COPY` |

该设计能够保护已经发行的优惠券历史，但误建草稿和不再使用的结束模板会长期出现在默认管理列表中，运营人员无法整理工作区。

模板又被适用范围、联合承担、兑换码、用户券、领取记录、核销记录和预算流水引用。物理删除会破坏优惠计算、支付、退款、结算、对账及审计链路，因此本需求不增加 `DELETE` 接口，也不级联删除任何模板关联数据。

### 2.2 设计结论

新增 `ARCHIVED` 作为模板管理生命周期的终态，语义为“从日常管理视图隐藏，但继续作为历史业务事实存在”。

```text
DRAFT ---------------------> ARCHIVED
  |
  v
ACTIVE <------> PAUSED
  |               |
  +----> ENDED <---+
           |
           v
       ARCHIVED
```

核心原则：

1. 归档不是删除，不改变模板 ID、模板编号或任何经济规则。
2. 归档不撤销用户券，不释放预算，不回滚发行量，不删除兑换码和流水。
3. `ACTIVE` 或 `PAUSED` 模板不能直接归档，必须先执行现有 `end` 动作。
4. `DRAFT` 可以直接归档，用于处理误建或放弃配置的模板。
5. `ENDED` 可以归档，用于隐藏已经完成且不再需要日常查看的模板。
6. `ARCHIVED` 不恢复到原状态；需要再次使用时调用现有 `copy` 动作创建新草稿。
7. 默认管理列表隐藏 `ARCHIVED`，但授权用户仍可显式筛选和读取详情。

复制继续遵循现有活动关系校验。归档源模板如果仍关联已经 `ENDED` 或 `CANCELLED` 的活动，复制请求必须显式提交 `activityId=null` 解除关联，或提交一个归属一致且允许关联的新活动 ID；不能把已结束或已取消的活动原样带入新草稿。

## 3. 产品需求与范围

### 3.1 目标

拥有模板管理权限的店铺或平台运营人员应当能够：

1. 归档不再使用的 `DRAFT` 或 `ENDED` 模板；
2. 在归档前看到明确提示，确认该动作不会删除历史数据；
3. 默认只查看仍需日常管理的模板；
4. 通过状态筛选查看归档模板及其完整详情；
5. 从归档模板复制出一个规则相同、执行数据清零的新草稿；
6. 继续查询归档模板关联的发行、核销、退款、预算和审计记录。

### 3.2 明确不做

本需求不包含：

- 物理删除或级联删除优惠券模板；
- 新增 `DELETE /coupon-templates/{templateId}`；
- 删除用户券、领取记录、兑换码、核销记录、分摊或预算流水；
- 将归档等同于撤销已经领取的用户券；
- 直接归档 `ACTIVE` 或 `PAUSED` 模板；
- 将 `ARCHIVED` 恢复为 `DRAFT`、`ACTIVE`、`PAUSED` 或 `ENDED`；
- 自动归档任务或按结束时间批量归档；
- 修改现有活动状态机；
- 为归档单独新增权限码。

如未来需要可恢复归档，必须另行设计原状态保存、恢复目标状态和并发规则，不能让 `ARCHIVED -> ACTIVE` 绕过激活校验。

### 3.3 权限与数据范围

归档复用现有模板管理权限：

| 调用入口 | 权限 | 数据范围 |
| --- | --- | --- |
| 店铺模板 | `shop:coupon:manage` | 当前账号是路径 `shopId` 的有效成员，模板 `ownerShopId` 必须等于路径店铺 |
| 平台模板 | `platform:coupon:manage` | 只允许平台自有模板，即 `ownerType=PLATFORM` |

读取归档模板继续复用 `shop:coupon:read` 和 `platform:coupon:read`。平台普通管理接口不能借平台权限归档店铺模板，店铺之间也不能交叉读取或归档。越权资源继续使用现有 `404 RESOURCE_NOT_FOUND` 语义，避免泄露模板是否存在。

## 4. 业务规则

### 4.1 状态转换

新增后的完整模板状态动作如下：

| 当前状态 | 允许的状态动作 | 允许的其他动作 |
| --- | --- | --- |
| `DRAFT` | `ACTIVATE`、`ARCHIVE` | `EDIT`、`COPY`、范围修改、展示字段修改 |
| `ACTIVE` | `PAUSE`、`END` | `COPY`、未首次发行时按现有规则修改范围、展示字段修改 |
| `PAUSED` | `RESUME`、`END` | `COPY`、未首次发行时按现有规则修改范围、展示字段修改 |
| `ENDED` | `ARCHIVE` | `COPY`、首次发行前按现有规则修改范围、展示字段修改 |
| `ARCHIVED` | 无 | `COPY`、读取详情和历史关系 |

归档规则：

```text
DRAFT  -> ARCHIVED
ENDED  -> ARCHIVED
```

其他来源状态调用归档接口统一返回 `409 COUPON_TEMPLATE_STATE_CONFLICT`。归档后，除 `copy` 外的模板修改接口必须拒绝请求，包括完整更新、范围替换、展示字段修改、激活、暂停、恢复、结束和发送联合承担邀请。

前端使用响应中的 `availableActions` 控制按钮，但服务端必须始终重新校验状态。目标映射如下：

| `status` | `availableActions` |
| --- | --- |
| `DRAFT` | `EDIT`、`ACTIVATE`、`COPY`、`ARCHIVE` |
| `ACTIVE` | `PAUSE`、`END`、`COPY` |
| `PAUSED` | `RESUME`、`END`、`COPY` |
| `ENDED` | `COPY`、`ARCHIVE` |
| `ARCHIVED` | `COPY` |

`availableActions` 只是界面便利字段，不构成执行授权，也不代替版本和状态校验。

### 4.2 归档原因与审计

归档必须提交去除首尾空白后长度为 `1..500` 的 `reason`，不接受空白原因。成功后必须：

- 将模板状态更新为 `ARCHIVED`；
- 更新 `updatedBy`、`updatedAt` 和 `version`；
- 写入一条 `coupon_operation_log`；
- `resourceType=TEMPLATE`；
- `operationType=ARCHIVE`；
- `fromStatus` 为 `DRAFT` 或 `ENDED`；
- `toStatus=ARCHIVED`；
- 保存操作者、店铺范围、原因和请求链路 ID。

本需求不增加 `archived_at`、`archived_by` 或 `archive_reason` 模板列。归档时间、操作者和原因以现有不可变操作日志为权威来源，模板自身的 `updatedAt/updatedBy` 只表示最近一次模板变更。

### 4.3 已发行权益

归档不得改变已经发行的用户券行为：

- `AVAILABLE` 用户券继续按自身有效期、人群、适用范围、门槛和叠加规则使用；
- `LOCKED` 用户券继续随原待支付交易完成核销，或在交易取消、超时后释放；
- `USED`、`EXPIRED`、`REVOKED` 状态不变；
- 符合现有退款返券政策的用户券继续正常恢复；
- 支付、售后、退款和商家结算继续使用原核销及分摊数据；
- 模板预算预占、消耗、冲回和发行数量不因归档变化。

优惠报价和正式下单仍需读取归档模板的不可变经济规则和范围。因此，归档实现不得删除或清空范围关系，也不得在已领取券使用校验中增加“模板必须为 `ACTIVE`”的条件。

### 4.4 新发行与兑换码

现有发券链路只允许 `status=ACTIVE`，所以 `ARCHIVED` 模板不能产生新的用户券，包括：

- 公开领取和限时抢券；
- 定向发券；
- 系统事件发券；
- 兑换码兑换。

已生成但尚未兑换的兑换码继续保留，使用时因模板不是 `ACTIVE` 而返回现有 `422 COUPON_TEMPLATE_NOT_CLAIMABLE`。本需求不自动撤销兑换码；如未来增加兑换码批量撤销，应作为独立业务动作处理并逐条审计。

### 4.5 活动和任务影响

归档模板不改变关联活动状态。为了避免活动仍显示可领但没有可领取模板：

- `ACTIVE`、`PAUSED` 模板不能直接归档；
- `DRAFT` 模板本来就不会成为买家可领取模板；
- `ENDED` 模板本来就不出现在买家领券模板集合中；
- 活动发布校验仍要求存在满足现有条件的已激活模板；
- 系统发券任务继续只扫描 `ACTIVE` 模板；
- 优惠券对账必须继续包含 `ARCHIVED` 模板，不能因为默认管理列表隐藏而跳过。

## 5. HTTP API 契约

### 5.1 接口总览

| 方法 | 路径 | 权限 | 幂等 | 请求 | 成功 `data` |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/shops/{shopId}/coupon-templates/{templateId}/archive` | `shop:coupon:manage` | 必填 | 现有 `ReasonVersionRequest` | `CouponTemplateAdminDetailView` |
| `POST` | `/api/platform/coupon-templates/{templateId}/archive` | `platform:coupon:manage` | 必填 | 现有 `ReasonVersionRequest` | `CouponTemplateAdminDetailView` |

使用动作型 `POST /archive` 与现有模板 `activate/pause/resume/end/copy` 接口保持一致，不使用 `DELETE`，也不允许客户端通过通用 `PUT` 或 `PATCH` 直接提交 `status=ARCHIVED`。

### 5.2 请求

店铺归档示例：

```http
POST /api/shops/2001/coupon-templates/3001/archive
satoken: <token>
Idempotency-Key: 9c0bfa87-a369-48d6-baaa-e9eb07a8bdf4
Content-Type: application/json
```

```json
{
  "reason": "测试期间误建，未投入使用",
  "version": 3
}
```

平台归档使用相同请求体：

```http
POST /api/platform/coupon-templates/3001/archive
```

字段规则沿用现有 `ReasonVersionRequest`：

| 字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `reason` | `string` | 是 | trim 后 `1..500`，写入审计日志 |
| `version` | `integer` | 是 | `>=0`，必须等于当前模板版本 |

### 5.3 成功响应

成功返回 `200 OK` 和现有 `CouponTemplateAdminDetailView`，不新增响应 DTO：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": "3001",
    "templateNo": "CT202608140001",
    "couponName": "测试优惠券",
    "status": "ARCHIVED",
    "issuedCount": 0,
    "version": 4,
    "availableActions": ["COPY"]
  },
  "requestId": "01J5A8M6P2Q3R4S5T6V7W8X9YZ",
  "timestamp": "2026-08-14T18:30:15.123+08:00"
}
```

示例只展示与归档相关的字段；实际 `data` 必须返回现有 `CouponTemplateAdminDetailView` 的完整字段，不得以本示例删减正式 DTO。

### 5.4 幂等与并发

归档动作沿用现有模板状态动作的幂等范围：

```text
当前用户 + HTTP 方法 + 规范化请求路径 + Idempotency-Key
```

规则如下：

1. 相同 Key、相同路径、相同请求体重试时返回第一次成功结果，不重复更新版本或写审计日志；
2. 相同 Key 用于不同请求体时返回 `409 IDEMPOTENCY_KEY_REUSED`；
3. 不同 Key 对已归档模板再次发起归档时返回 `409 COUPON_TEMPLATE_STATE_CONFLICT`；
4. `version` 在事务内校验，过期版本返回 `409 VERSION_CONFLICT`；
5. 状态更新使用乐观锁或带 `status/version` 条件的更新，状态检查、模板更新、审计日志和幂等结果必须处于同一事务边界。

### 5.5 错误语义

| HTTP | 错误码 | 场景 |
| ---: | --- | --- |
| `400` | `BAD_REQUEST` | 请求 JSON、枚举或整数格式无法解析，或缺少必填 Header |
| `400` | `VALIDATION_FAILED` | `reason` 为空、全空白、超过 500 字符，或 `version<0` |
| `401/403` | 现有认证错误 | 未登录、账号不可用或缺少模板管理权限 |
| `403` | `SHOP_ACCESS_DENIED` | 不是目标店铺有效成员或缺少店铺管理权限 |
| `404` | `RESOURCE_NOT_FOUND` | 模板不存在，或模板不属于当前店铺/平台数据范围 |
| `409` | `COUPON_TEMPLATE_STATE_CONFLICT` | 模板不是 `DRAFT` 或 `ENDED`，包括重复归档 |
| `409` | `VERSION_CONFLICT` | 请求版本与模板当前版本不一致 |
| `409` | `IDEMPOTENCY_KEY_REUSED` | 相同幂等键被用于不同请求 |

本需求复用现有错误码，不新增“删除成功”“资源已删除”或 `410 Gone` 语义。归档模板通过授权详情接口仍然存在，不能返回 `RESOURCE_NOT_FOUND` 来伪装删除。

## 6. 查询与可见性

### 6.1 模板管理列表

以下现有列表接口不新增查询参数：

```http
GET /api/shops/{shopId}/coupon-templates
GET /api/platform/coupon-templates
GET /api/platform/coupon-operations/templates
```

`status` 继续是单值精确枚举筛选，新增接受 `ARCHIVED`。查询语义调整为：

| 查询方式 | 结果 |
| --- | --- |
| 未提交 `status` | 返回除 `ARCHIVED` 外的模板 |
| `status=ARCHIVED` | 只返回归档模板 |
| `status=DRAFT/ACTIVE/PAUSED/ENDED` | 继续按现有状态精确筛选 |

不新增 `includeArchived`，避免与现有精确 `status` 筛选形成两套重叠语义。默认隐藏必须在数据库查询条件中实现，不能先查询整页再在内存过滤，否则分页总数和页数会错误。

默认隐藏会改变未传 `status` 时的 `total` 和页面内容，这是本需求有意引入的查询行为。客户端需要提供“已归档”状态筛选入口；旧客户端即使不认识新状态，也不会在默认列表收到 `ARCHIVED`。

现有 `CouponActivityAdminView.templateCount` 表示日常可管理的关联模板数量，归档功能已将 `ARCHIVED` 排除，使活动详情数量与默认模板列表一致。活动的 `issuedCount`、`consumedCount` 和 `couponDiscountAmount` 是历史业务指标，仍包含归档模板产生的记录，不随归档减少。

### 6.2 详情和关联查询

以下读取能力不得因模板归档而返回不存在：

- 授权管理端按 `templateId` 查询模板详情；
- 平台优惠券运营查询和业务追踪；
- 模板适用范围目标查询；
- 用户券详情中的模板展示；
- 兑换码批次、领取记录、核销、退款、预算和审计查询；
- 内部优惠券对账。

店铺和平台数据范围校验保持不变。归档只影响默认模板列表和买家新发行入口，不是全局查询过滤器，不能通过 MyBatis-Plus 逻辑删除注解统一隐藏。

店铺活跃业务判断只把 `DRAFT`、`ACTIVE`、`PAUSED` 模板视为未完结模板；`ENDED` 和 `ARCHIVED` 都不应单独阻止店铺生命周期操作。已锁定用户券仍按原规则计入活跃业务，避免归档绕过进行中的交易约束。

### 6.3 买家端

领券中心现有查询只展示符合条件的 `ACTIVE/PAUSED` 模板，因此 `ARCHIVED` 自然不会出现在活动模板集合中，不需要新增买家 DTO、状态文案或不可领取原因。

“我的优惠券”、结算、订单详情、退款和返券必须继续读取归档模板及范围。买家看到的是已领取用户券的状态，不把模板 `ARCHIVED` 映射为用户券 `EXPIRED` 或 `REVOKED`。

## 7. 数据库与实现边界

### 7.1 增量迁移

本需求不修改已经发布的 `sql/scheme6.sql` 或 `sql/scheme8.sql`。已新增 `sql/scheme9.sql`，在 `scheme8.sql` 之后执行，只扩展模板状态检查约束：

```sql
ALTER TABLE coupon_template
    DROP CHECK chk_coupon_template_status,
    ADD CONSTRAINT chk_coupon_template_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'ENDED', 'ARCHIVED'));
```

迁移不回写历史模板，不自动把 `ENDED` 转成 `ARCHIVED`。上线后所有历史记录保持原状态，由有权限的运营人员按需归档。

本需求不新增模板软删除时间，也不改变任何外键及级联规则。`ARCHIVED` 是业务状态，不使用 MyBatis-Plus `@TableLogic`。

### 7.2 服务分层

实现职责边界：

| 组件 | 职责 |
| --- | --- |
| Controller | 暴露店铺/平台 `/archive` 动作，复用 `ReasonVersionRequest` 和统一响应 |
| Coupon Admin Service | 权限、归属、状态、版本、幂等、事务和审计 |
| Coupon Template Mapper | 保持普通实体映射；需要时提供带状态和版本条件的原子更新 |
| Coupon Service | 继续仅允许 `ACTIVE` 模板发行，不改变已领券使用规则 |
| Coupon Task Service | 系统发券只选 `ACTIVE`；对账继续覆盖包括 `ARCHIVED` 在内的全部模板 |

不得实现全局“非 `ARCHIVED`”查询拦截器。归档可见性只应用于本文明确列出的默认管理列表；历史权益和内部账务读取必须能够获取归档模板。

### 7.3 枚举和 DTO

已同步以下契约：

- `MarketEnums.CouponTemplateStatus` 增加 `ARCHIVED`；
- `CouponTemplateAdminSummaryView.status` 和 `CouponTemplateAdminDetailView.status` 可以返回 `ARCHIVED`；
- `CouponTemplateAdminDetailView.availableActions` 按本文状态表返回；
- 列表 `status` 查询参数接受 `ARCHIVED`；
- 归档请求复用现有 `ReasonVersionRequest`，不创建字段重复的新 DTO；
- 不改变任何 DTO 字段类型、可空性和金额格式。

### 7.4 前端约定

模板管理页面需要：

1. 默认不显示归档模板；
2. 在状态筛选中增加“已归档”，提交 `status=ARCHIVED`；
3. 只在 `availableActions` 包含 `ARCHIVE` 时显示归档按钮；
4. 归档使用确认对话框并要求填写原因；
5. 明确提示“归档后默认隐藏，历史数据和已领取优惠券不会删除”；
6. 收到 `VERSION_CONFLICT` 后重新加载模板，不自动覆盖；
7. 归档成功后从当前默认列表移除该项；
8. 归档详情只提供复制及历史查看入口，不提供恢复、编辑或状态动作。

前端不得把归档按钮实现为删除图标或展示“删除模板”，避免用户误解数据和权益会被清除。

## 8. 安全、审计与可观测性

- 每次成功归档必须有一条且只有一条操作日志；幂等重放不得重复记录。
- 日志需要包含模板 ID、模板编号、归档前状态、操作者、归属店铺、原因、请求 ID 和时间。
- 归档失败不得产生状态变更日志。
- 指标至少区分归档成功、状态冲突、版本冲突和权限拒绝。
- 归档模板仍参加发行量、预算、核销分摊和结算对账。
- 不在归档日志中记录兑换码明文、用户隐私或完整请求凭证。

## 9. 测试与验收清单

### 9.1 状态和权限

1. 店铺管理员可以归档本店 `DRAFT` 模板，返回 `ARCHIVED` 和递增版本。
2. 店铺管理员可以归档本店 `ENDED` 模板。
3. `ACTIVE`、`PAUSED` 和 `ARCHIVED` 调用归档均返回 `COUPON_TEMPLATE_STATE_CONFLICT`。
4. 平台可以归档平台自有模板，不能通过平台模板路径归档店铺模板。
5. 店铺成员不能读取或归档其他店铺模板。
6. 缺少 manage 权限时不能归档，但具备 read 权限时仍可读取归档详情。
7. 空白原因、超长原因和负版本被拒绝。

### 9.2 幂等和并发

1. 相同幂等键重放返回同一成功响应，只更新一次版本并写一条审计日志。
2. 相同幂等键修改原因或版本返回 `IDEMPOTENCY_KEY_REUSED`。
3. 两个不同幂等键并发归档同一模板时只有一个成功，另一个返回状态或版本冲突。
4. 归档与激活、结束或编辑并发时只有一个基于当前版本成功，不出现丢失更新。

### 9.3 查询可见性

1. 默认店铺模板列表不返回 `ARCHIVED`，分页 `total/pages` 与过滤后结果一致。
2. 默认平台模板列表和平台运营模板列表不返回 `ARCHIVED`。
3. `status=ARCHIVED` 只返回归档模板，并继续执行店铺/平台数据范围限制。
4. 授权用户按 ID 可以读取归档模板详情和范围目标。
5. 买家领券中心不展示归档模板。
6. 活动管理视图的 `templateCount` 排除归档模板，但发行数、核销数和优惠金额指标保持不变。

### 9.4 历史权益和账务

1. 已发行模板从 `ENDED` 归档后，`AVAILABLE` 用户券仍可参与报价和下单。
2. 归档时已经 `LOCKED` 的用户券仍可支付核销或取消释放。
3. 符合条件的已使用券在全单退款后仍按原策略返券。
4. 归档不改变模板发行数、预算预占、预算消耗和预算冲回金额。
5. 兑换归档模板的未使用兑换码返回 `COUPON_TEMPLATE_NOT_CLAIMABLE`，但兑换码记录不被删除。
6. 对账任务包含归档模板，并能继续关联用户券、领取、核销、分摊和预算流水。
7. 从归档模板复制后得到新 `DRAFT`，模板编号不同，发行数和预算执行金额归零，源模板保持不变；源活动已经结束或取消时，必须显式解除或更换活动关联。

### 9.5 契约兼容

1. 现有 `activate/pause/resume/end/copy/presentation` 路径和请求 DTO 不变。
2. 现有模板列表只增加一种合法状态筛选值，未传状态时按本文默认隐藏。
3. 现有管理详情 DTO 只返回新增枚举值，不增加或删除字段。
4. 已有活动状态、用户券状态、发行、使用、退款和结算规则不变。
5. 主 API 文档、数据库设计、产品需求、DTO 字段目录、后端枚举和数据库检查约束使用同一组模板状态。

## 10. 完成定义

本需求完成必须同时满足：

1. `ARCHIVED` 已加入 Java 枚举、数据库检查约束和正式文档；
2. 店铺端与平台端归档接口完成权限、归属、状态、版本、幂等和审计校验；
3. 默认模板管理列表在数据库层排除归档状态，显式状态筛选可查询；
4. 归档模板只能读取、复制和参与历史业务处理；
5. 已领券使用、锁定、核销、退款返券、预算及对账测试通过；
6. 不存在物理删除、级联删除、全局逻辑删除或归档后失去历史模板规则的问题；
7. 所有新增和回归测试通过，接口文档与实现一致。
