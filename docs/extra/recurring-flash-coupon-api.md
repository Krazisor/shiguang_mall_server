# 周期定时抢券需求及 API 文档

## 1. 文档目的

本文定义优惠券活动按固定周期开放抢券窗口的产品规则、HTTP API、数据语义和实现边界，用于支持以下运营配置：

```text
每天 20:00 开抢
每周五、六、日 20:00 开抢
每月 1 日、15 日 10:00 开抢
```

本文中的“定时抢券”是指系统按周期开放和关闭抢券窗口，用户仍需在窗口内主动调用现有领券接口。它不表示用户提前预约后由系统代为领取，也不创建客户端或服务端的用户级自动领券任务。

文档状态：

| 项目 | 状态 |
| --- | --- |
| 一次性优惠券活动 | 已实现，保持现有接口不变 |
| 周期规则配置接口 | 已实现，以本文和主 API 文档作为契约 |
| 买家主动抢券接口 | 已实现，周期活动继续复用 |
| 数据库结构 | 已新增 `coupon_activity_recurrence`，不把规则塞入现有描述字段 |
| 权限 | 复用现有店铺和平台优惠券权限，不新增权限码 |

未重复说明的 ID、时间、认证、分页、统一响应和错误格式继承[统一 API 契约](../api/common-contract.md)，优惠券状态、库存、幂等、风控和用户券规则继承[优惠券模块 API](../api/coupon-api.md)。

契约优先级与落地要求：现有接口和命名 DTO 以主 API 文档及 [DTO 字段目录](../api/dto-catalog.md) 为准，本文不能覆盖或改变其既有语义。本文新增的 Request/View 已登记到 `docs/api/dto-catalog.md`，新增路径已同步到 `docs/api/coupon-api.md`，数据库结构已同步到数据库设计并由 `sql/scheme8.sql` 提供增量迁移。

## 2. 背景与现状

### 2.1 当前能力

当前 `coupon_activity` 使用以下两个字段描述一个连续、一次性的活动时间段：

```text
startsAt
endsAt
```

例如：

```json
{
  "startsAt": "2026-08-14T20:00:00.000+08:00",
  "endsAt": "2026-08-14T20:30:00.000+08:00"
}
```

这只能表达“2026 年 8 月 14 日 20:00 开始一次，20:30 结束”，不能表达“每周五、六、日 20:00 开始，每次持续 30 分钟”。前端文案或 cron 字符串不能弥补这个数据缺口，因为领取服务无法据此执行可靠的时间校验。

当前买家抢券接口已经具备登录、权限、幂等、限流、个人限领、发行量和预算校验：

```http
POST /api/coupon-center/activities/{activityId}/templates/{templateId}/claim
```

周期需求应扩展活动的时间规则，不应新增另一条绕过现有领取链路的发券接口。

### 2.2 设计结论

第一版使用结构化周期规则，不接受 `cronExpression` 或自由文本 `recurrenceRule`：

- cron 不适合作为对外业务契约，无法自然表达生效区间、每次窗口时长和月末缺失日期；
- cron 的星期编号在不同实现中存在差异，容易造成周日偏移；
- 自由文本无法进行稳定校验、展示和跨语言解析；
- 结构化字段可以直接被前端表单、后端校验和测试使用。

周期活动以“活动生命周期”和“单次抢券窗口”两层状态运行：

| 层级 | 含义 |
| --- | --- |
| 活动生命周期 | 从第一个有效窗口开始，到最后一个有效窗口结束；沿用 `SCHEDULED/RUNNING/PAUSED/ENDED` |
| 抢券窗口 | 某一次实际开放区间，例如 `2026-08-14 20:00` 至 `20:30` |

活动在两个窗口之间仍可保持 `RUNNING`，但此时不允许领取。前端不能只依据活动 `status=RUNNING` 点亮“立即抢券”按钮，还必须读取 `window.status` 或模板的 `claimable`。

现有领券中心列表和详情 DTO 不包含 `window`；需要窗口信息的页面应额外调用本文新增的 schedule 子资源。这里的 `window.status` 仅指 `CouponActivityScheduleView.window.status`。

## 3. 产品需求与范围

### 3.1 目标

拥有优惠券管理权限的运营人员应当能够：

1. 为 `FLASH_CLAIM` 草稿活动配置每天、每周或每月重复规则；
2. 配置每天的开抢时刻、每次持续时长、规则生效起止范围和业务时区；
3. 在发布前查看服务端计算的第一个、当前和下一个窗口；
4. 发布后由系统自动判断抢券窗口，无需每场人工开始和结束；
5. 继续使用现有暂停、恢复、结束和取消动作管理活动；
6. 让买家继续使用现有领券接口，无需理解周期计算逻辑。

### 3.2 明确不做

第一版不包含：

- 用户预约抢券、到点代用户自动调用领券接口；
- 每场单独重置库存、预算或个人限领次数；
- 一天配置多个开抢时间；
- 法定节假日、调休、排除日期或临时加场；
- 任意 cron、秒级频率、每隔 N 分钟或不规则日历表达式；
- 发布后直接修改周期规则；
- 根据前端本地时间判断是否允许领取。

如果同一天需要 10:00 和 20:00 两场，第一版应创建两个独立活动。如果需要每场独立库存或每场限领，应新增“抢券场次”资源和场次库存账本，不能在当前模板的 `issuedCount` 上定时清零。

### 3.3 权限与数据范围

周期配置复用现有权限：

| 调用入口 | 读取权限 | 修改权限 | 数据范围 |
| --- | --- | --- | --- |
| 店铺活动 | `shop:coupon:read` | `shop:coupon:manage` | 当前账号是路径 `shopId` 的有效成员，SQL 同时限定该店铺 |
| 平台活动 | `platform:coupon:read` | `platform:coupon:manage` | 平台自有活动 |
| 买家抢券 | `coupon:read:self` / `coupon:claim` | `coupon:claim` | 当前登录用户本人 |

平台权限不能绕过店铺成员数据范围。平台如需治理店铺活动，继续使用现有治理暂停、恢复接口，不通过周期配置接口修改店铺规则。

## 4. 周期规则

### 4.1 公共字段

`RecurringCouponSchedule` 字段如下：

| 字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `recurrenceType` | `DAILY` / `WEEKLY` / `MONTHLY` | 是 | 重复类型 |
| `weekdays` | `integer[] \| null` | 条件必填 | 仅 `WEEKLY` 使用，ISO 星期 `1..7`，`1=周一`、`7=周日` |
| `monthDays` | `integer[] \| null` | 条件必填 | 仅 `MONTHLY` 使用，取值 `1..31` |
| `dailyStartsAt` | `string` | 是 | 本地开抢时刻，格式 `HH:mm:ss`，第一版秒必须为 `00` |
| `windowDurationMinutes` | `integer` | 是 | 单次窗口时长，范围 `1..1440` |
| `recurrenceStartsAt` | `OffsetDateTime` | 是 | 周期规则生效下界，包含 |
| `recurrenceEndsAt` | `OffsetDateTime` | 是 | 周期规则生效上界，不包含 |
| `timezone` | `string` | 是 | 第一版固定为 `Asia/Shanghai` |

数组必须去重并按升序提交。后端返回时同样按升序规范化，不保留客户端原始顺序。

字段组合约束：

| `recurrenceType` | `weekdays` | `monthDays` |
| --- | --- | --- |
| `DAILY` | 必须为 `null` 或省略 | 必须为 `null` 或省略 |
| `WEEKLY` | 必须包含 `1..7` 中至少一个值 | 必须为 `null` 或省略 |
| `MONTHLY` | 必须为 `null` 或省略 | 必须包含 `1..31` 中至少一个值 |

### 4.2 时间区间语义

所有抢券窗口使用左闭右开区间：

```text
[windowStartsAt, windowEndsAt)
```

因此，窗口为 `20:00:00` 至 `20:30:00` 时：

- `20:00:00.000` 可以领取；
- `20:29:59.999` 可以领取；
- `20:30:00.000` 不可以领取。

候选窗口只有同时满足以下条件才是有效窗口：

```text
windowStartsAt >= recurrenceStartsAt
windowEndsAt   <= recurrenceEndsAt
```

周期生效范围最长为 366 天，并且必须至少生成一个有效窗口。`recurrenceStartsAt` 和 `recurrenceEndsAt` 的偏移必须与 `Asia/Shanghai` 一致，即当前固定为 `+08:00`。

`windowDurationMinutes` 允许窗口跨过午夜。星期和月份归属始终按窗口开始日期判断。例如周日 23:30 开始、持续 120 分钟的窗口属于周日场，结束时间可以在周一 01:30。

### 4.3 每月规则

月度规则遇到不存在的日期时直接跳过，不向月末回退：

```text
monthDays=[29,30,31]
2027 年 2 月：三个日期都不生成窗口
2026 年 4 月：生成 29 日和 30 日窗口，不生成 31 日窗口
```

该规则必须由后端统一计算，前端只展示 API 返回的窗口，不自行推算月末行为。

### 4.4 与现有 `startsAt/endsAt` 的兼容

对于一次性活动，现有字段语义保持不变：

```text
startsAt = 唯一窗口开始时间
endsAt   = 唯一窗口结束时间
```

对于周期活动，`coupon_activity.starts_at` 和 `coupon_activity.ends_at` 继续保留，并由服务端物化为：

```text
startsAt = 第一个有效窗口的开始时间
endsAt   = 最后一个有效窗口的结束时间
```

这两个字段表示整个周期活动的生命周期边界，不表示当前场次。客户端必须通过 `window.currentWindow` 和 `window.nextWindow` 展示当前或下一次开抢时间。

## 5. HTTP API 契约

### 5.1 接口总览

| 方法 | 路径 | 权限 | 幂等 | 成功 `data` |
| --- | --- | --- | --- | --- |
| `POST` | `/api/shops/{shopId}/coupon-activities/recurring` | `shop:coupon:manage` | 必填 | `CouponActivityAdminView`，`201` |
| `GET` | `/api/shops/{shopId}/coupon-activities/{activityId}/schedule` | `shop:coupon:read` | 否 | `CouponActivityScheduleView` |
| `PUT` | `/api/shops/{shopId}/coupon-activities/{activityId}/schedule` | `shop:coupon:manage` | 推荐 | `CouponActivityScheduleView` |
| `POST` | `/api/platform/coupon-activities/recurring` | `platform:coupon:manage` | 必填 | `CouponActivityAdminView`，`201` |
| `GET` | `/api/platform/coupon-activities/{activityId}/schedule` | `platform:coupon:read` | 否 | `CouponActivityScheduleView` |
| `PUT` | `/api/platform/coupon-activities/{activityId}/schedule` | `platform:coupon:manage` | 推荐 | `CouponActivityScheduleView` |
| `GET` | `/api/coupon-center/activities/{activityId}/schedule` | LOGIN + `coupon:read:self` | 否 | `CouponActivityScheduleView` |

买家只调用表中的领券中心只读 schedule 接口，不调用店铺或平台的创建、查询和修改入口。实际抢券继续使用：

```http
POST /api/coupon-center/activities/{activityId}/templates/{templateId}/claim
```

### 5.2 创建周期抢券活动

店铺请求：

```http
POST /api/shops/201/coupon-activities/recurring
Content-Type: application/json
satoken: <token>
Idempotency-Key: create-recurring-activity-01K2J8N1
```

平台请求使用相同请求体：

```http
POST /api/platform/coupon-activities/recurring
```

请求体 `CreateRecurringCouponActivityRequest`：

```json
{
  "activityName": "周末晚八点抢券",
  "subtitle": "每周五、六、日 20:00 限量开抢",
  "bannerUrl": "https://static.example.com/coupons/weekend-flash.png",
  "recurrence": {
    "recurrenceType": "WEEKLY",
    "weekdays": [5, 6, 7],
    "monthDays": null,
    "dailyStartsAt": "20:00:00",
    "windowDurationMinutes": 30,
    "recurrenceStartsAt": "2026-08-14T00:00:00.000+08:00",
    "recurrenceEndsAt": "2026-10-01T00:00:00.000+08:00",
    "timezone": "Asia/Shanghai"
  }
}
```

字段说明：

| 字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `activityName` | `string` | 是 | 去除首尾空白后长度 `1..128` |
| `subtitle` | `string \| null` | 否 | 去除首尾空白后最大 255 字符 |
| `bannerUrl` | `string \| null` | 否 | 最大 1024 字符，沿用现有图片 URL 规则 |
| `recurrence` | `RecurringCouponSchedule` | 是 | 完整周期规则 |

服务端固定写入：

```text
activityType = FLASH_CLAIM
status = DRAFT
ownerType = 由请求路径决定
shopId = 店铺路径取 path shopId，平台路径为 null
startsAt = 第一个有效窗口开始时间
endsAt = 最后一个有效窗口结束时间
version = 0
```

客户端不得在本请求提交 `activityType`、`ownerType`、`shopId`、`startsAt`、`endsAt`、`status` 或 `version`。出现 DTO 未定义字段时返回 `400 BAD_REQUEST`。活动记录和周期规则必须在同一数据库事务中创建，任一写入或审计失败时整体回滚，不得留下缺少规则的周期活动。

成功返回 `201 Created` 和现有 `CouponActivityAdminView`。该 DTO 的字段集合保持不变；调用方需要周期规则和当前窗口时，继续查询对应的 schedule 子资源。相同幂等键和相同请求返回第一次创建结果，不重复创建活动。

现有一次性活动创建接口保持不变：

```http
POST /api/shops/{shopId}/coupon-activities
POST /api/platform/coupon-activities
```

一次性请求继续提交 `activityType/startsAt/endsAt`。周期活动必须使用 `/recurring` 静态子路径，不允许先创建带占位时间的一次性活动再覆盖，以免审计日志和并发请求观察到错误时间。

### 5.3 配置或替换周期规则

店铺请求：

```http
PUT /api/shops/201/coupon-activities/3001/schedule
Content-Type: application/json
satoken: <token>
Idempotency-Key: schedule-3001-v0
```

平台请求使用相同请求体：

```http
PUT /api/platform/coupon-activities/3001/schedule
```

请求体 `UpdateCouponActivityScheduleRequest`：

```json
{
  "scheduleType": "RECURRING",
  "recurrence": {
    "recurrenceType": "WEEKLY",
    "weekdays": [5, 6, 7],
    "monthDays": null,
    "dailyStartsAt": "20:00:00",
    "windowDurationMinutes": 30,
    "recurrenceStartsAt": "2026-08-14T00:00:00.000+08:00",
    "recurrenceEndsAt": "2026-10-01T00:00:00.000+08:00",
    "timezone": "Asia/Shanghai"
  },
  "version": 0
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `scheduleType` | `RECURRING` | 是 | 本接口第一版只接受周期模式 |
| `recurrence` | `RecurringCouponSchedule` | 是 | 完整周期规则，每次 `PUT` 全量替换 |
| `version` | `integer` | 是 | 活动当前乐观锁版本，最小为 0 |

接口只允许修改 `DRAFT` 且 `activityType=FLASH_CLAIM` 的活动。成功后活动仍为 `DRAFT`，不会自动发布。重复提交相同规则和相同版本时，如果第一次请求已经成功但响应丢失，应使用同一个 `Idempotency-Key` 重试；使用新 Key 和旧版本将返回 `VERSION_CONFLICT`。

一次性活动继续通过现有创建或更新活动接口配置 `startsAt/endsAt`。第一版不通过周期子资源把活动切回一次性模式；草稿周期活动如需改为一次性活动，应调用现有 `cancel` 动作取消旧草稿，再通过一次性活动接口重新创建，避免两个时间来源并存。现有活动 `PUT` 对周期活动只允许更新名称、副标题和横幅；不得提交或修改 `activityType`、`startsAt`、`endsAt`，否则返回 `COUPON_ACTIVITY_STATE_CONFLICT` 或 `VALIDATION_FAILED`。

### 5.4 查询周期规则

请求：

```http
GET /api/shops/201/coupon-activities/3001/schedule
satoken: <token>
```

成功响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "scheduleType": "RECURRING",
    "campaignStartsAt": "2026-08-14T20:00:00.000+08:00",
    "campaignEndsAt": "2026-09-27T20:30:00.000+08:00",
    "recurrence": {
      "recurrenceType": "WEEKLY",
      "weekdays": [5, 6, 7],
      "monthDays": null,
      "dailyStartsAt": "20:00:00",
      "windowDurationMinutes": 30,
      "recurrenceStartsAt": "2026-08-14T00:00:00.000+08:00",
      "recurrenceEndsAt": "2026-10-01T00:00:00.000+08:00",
      "timezone": "Asia/Shanghai"
    },
    "window": {
      "status": "WAITING",
      "currentWindow": null,
      "nextWindow": {
        "startsAt": "2026-08-14T20:00:00.000+08:00",
        "endsAt": "2026-08-14T20:30:00.000+08:00"
      }
    },
    "serverTime": "2026-08-13T16:00:00.000+08:00",
    "version": 1
  },
  "requestId": "01K2J8N1B9M5QG3WH2D83DP0K1",
  "timestamp": "2026-08-13T16:00:00.000+08:00"
}
```

`CouponActivityScheduleView`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `scheduleType` | `ONCE` / `RECURRING` | 活动时间模式 |
| `campaignStartsAt` | `OffsetDateTime` | 第一个有效窗口开始时间 |
| `campaignEndsAt` | `OffsetDateTime` | 最后一个有效窗口结束时间 |
| `recurrence` | `RecurringCouponSchedule \| null` | 一次性活动为 `null` |
| `window` | `CouponClaimWindowView` | 以 `serverTime` 计算的当前和下一窗口 |
| `serverTime` | `OffsetDateTime` | 服务端计算基准时间 |
| `version` | `integer` | 活动当前版本 |

`CouponClaimWindowView.status`：

| 值 | 含义 | `currentWindow` | `nextWindow` |
| --- | --- | --- | --- |
| `WAITING` | 首场未开始或当前处于两个窗口之间 | `null` | 下一场；没有则为 `null` |
| `OPEN` | 当前位于有效抢券窗口 | 当前场 | 下一场；没有则为 `null` |
| `PAUSED` | 活动被运营或平台治理暂停 | 当前时间对应的场次可返回，但不可领取 | 下一场可返回 |
| `ENDED` | 最后一场已结束、活动已手动结束或取消 | `null` | `null` |

读取一次性活动的 schedule 子资源时也返回 `200`，其中 `scheduleType=ONCE`、`recurrence=null`，唯一窗口由现有 `startsAt/endsAt` 计算。这样前端详情页不需要根据活动类型调用两套读取逻辑。

三个读取入口沿用各自主资源的数据范围：店铺入口只查询路径 `shopId` 下的活动，平台管理入口只查询平台自有活动，买家入口必须先满足现有领券中心活动详情的可见性规则。活动不可见、不属于调用入口的数据范围或已软删除时统一返回 `404 RESOURCE_NOT_FOUND`，schedule 子资源不得扩大现有活动的可见范围。

### 5.5 现有管理活动响应兼容性

现有 `CouponActivityAdminView` 的精确字段集合保持不变，不增加 `schedule`、`scheduleType`、`window` 或 `serverTime`。其中既有时间字段对周期活动返回第 4.4 节定义的生命周期边界：

```json
{
  "startsAt": "2026-08-14T20:00:00.000+08:00",
  "endsAt": "2026-09-27T20:30:00.000+08:00"
}
```

管理端需要判断一次性或周期模式、展示规则、当前窗口或下一窗口时，必须调用第 5.4 节的独立 schedule 子资源，并以 `CouponActivityScheduleView` 为准。这样不会单方面改变 [`dto-catalog.md`](../api/dto-catalog.md) 已登记的命名 DTO 字段集合。

### 5.6 现有买家领券响应兼容性

现有 `ClaimableActivitySummaryView`、`ClaimableActivityDetailView` 和 `ClaimableTemplateView` 的精确字段集合保持不变，不增加 `scheduleType` 或 `window`；`ClaimableActivitySummaryView` 继续保留既有 `serverTime`。买家活动详情需要周期规则和下一场时间时，额外调用：

```http
GET /api/coupon-center/activities/{activityId}/schedule
```

`ClaimableTemplateView.claimable` 的计算增加服务端周期窗口判断，但响应结构和封闭原因码目录不变。周期活动在首场开始前或两个窗口之间时返回既有原因码：

```json
{
  "claimable": false,
  "unclaimableReason": "NOT_STARTED"
}
```

前端使用 `CouponActivityScheduleView` 的 `nextWindow.startsAt - serverTime` 计算展示倒计时。倒计时归零后应重新获取活动详情和 schedule 子资源，不能仅在浏览器中把按钮改为可领取。正式抢券请求在窗口外失败时，继续返回既有 `422 COUPON_ACTIVITY_NOT_CLAIMABLE`。

### 5.7 买家抢券

周期活动不新增买家写接口，仍调用：

```http
POST /api/coupon-center/activities/3001/templates/3101/claim
satoken: <token>
Idempotency-Key: claim-3001-3101-01K2J8N1
```

请求无 JSON 请求体。成功返回 `201 Created` 和现有 `UserCouponDetailView`。

后端领取判断顺序扩展为：

```text
登录账号
-> 活动存在且对用户可见
-> 模板属于活动
-> 活动生命周期状态
-> 服务端当前时间位于有效周期窗口
-> 模板状态和模板领取范围
-> 人群资格
-> 用户累计限领
-> 累计发行量和预算
-> 创建用户券及领取记录
```

周期窗口判断必须在发行事务内再次执行。列表或详情中的 `claimable=true` 只是响应生成时的快照，不构成领取承诺。

## 6. 发布、状态和修改规则

### 6.1 发布前校验

周期活动执行现有 `publish` 动作前必须新增以下校验：

1. 活动为 `FLASH_CLAIM` 且存在有效周期规则；
2. 周期规则能生成至少一个尚未结束的窗口；
3. `campaignStartsAt` 和 `campaignEndsAt` 已由服务端正确物化；
4. 活动至少关联一个已激活模板；
5. 关联模板的 `distributionType=FLASH_CLAIM`；
6. `FLASH_CLAIM` 模板继续遵循现有模板契约，必须配置 `claimStartsAt/claimEndsAt`，并满足 `claimStartsAt <= activity.startsAt`、`claimEndsAt >= activity.endsAt`，覆盖整个周期活动生命周期；周期窗口是在模板领取时间范围基础上增加的服务端资格判断；
7. 店铺、范围、预算和联合承担校验继续满足现有规则。

任一校验失败时不得部分发布。

### 6.2 状态转换

发布后的生命周期规则：

| 当前时间/动作 | 活动状态 |
| --- | --- |
| 第一场开始前发布 | `SCHEDULED` |
| 在有效窗口内发布 | `RUNNING` |
| 两场之间且第一场已经开始 | `RUNNING`，但 `window.status=WAITING` |
| 到达最后一场结束时间 | 自动转为 `ENDED` |
| 调用 `pause` | `PAUSED`，即使处于场次窗口也立即禁止领取 |
| 首场前调用 `resume` | 恢复为 `SCHEDULED`，是否能领仍取决于当前窗口 |
| 首场已开始、末场未结束时调用 `resume` | 恢复为 `RUNNING`，是否能领仍取决于当前窗口 |
| 调用 `end` | `ENDED`，后续场次全部取消 |
| 调用 `cancel` | 只允许现有 `DRAFT/SCHEDULED` 状态，后续场次全部取消 |

暂停期间错过的窗口不会补开，也不会顺延。恢复后只按原规则判断当前或下一窗口。

### 6.3 修改冻结

周期规则只允许在活动 `DRAFT` 时全量替换。活动发布后，以下字段冻结：

- 重复类型和日期选择；
- 每日开始时刻；
- 窗口时长；
- 周期生效起止范围；
- 时区。

已发布活动只能暂停、恢复、提前结束或取消允许取消的未来活动。需要修改规则时，应结束或取消旧活动，并从草稿创建新活动。这与现有活动和模板发布后的不可变原则一致，也避免已经领取的用户券与新规则产生审计歧义。

## 7. 库存、限领、幂等和并发

### 7.1 累计口径

第一版所有场次共享同一优惠券模板：

| 指标 | 口径 |
| --- | --- |
| `totalIssueLimit` | 整个周期活动累计发行上限 |
| `issuedCount` | 所有历史场次累计已发行数量 |
| `perUserLimit` | 用户跨所有历史场次累计领取上限 |
| `budgetAmount` | 整个模板累计预算 |
| `remainingQuantity` | 模板累计剩余量，不是当场剩余量 |

例如模板 `totalIssueLimit=1000`，周五发出 700 张后，周六最多只能继续发出 300 张；系统不会在周六 20:00 把计数恢复为 1000。

### 7.2 幂等

管理 `PUT schedule` 使用“当前用户 + 方法 + 规范化路径 + Idempotency-Key”作为幂等范围，建议始终提交 Header。乐观锁 `version` 是最终并发保护，幂等记录不能替代版本检查。

抢券继续强制提交 `Idempotency-Key`：

- 相同 Key、相同活动和模板返回第一次结果，不重复发券；
- 相同 Key 被用于不同请求返回 `409 IDEMPOTENCY_KEY_REUSED`；
- 用户下一次主动点击应生成新 Key；
- 网络重试沿用原 Key。

### 7.3 并发和时钟

领取资格以应用统一业务时钟为准，不信任请求参数、浏览器时钟或缓存倒计时。多实例必须使用相同的 `Asia/Shanghai` 时区配置，并通过数据库条件更新保护模板发行量和预算，保证并发不超发。

窗口结束边界和模板发行更新必须位于同一事务判断链路。即使后台生命周期任务延迟，领取服务也必须直接计算当前周期窗口：

- 当前时间不在窗口内，一律拒绝；
- 当前时间已经越过最后窗口结束时间，一律拒绝并允许任务稍后物化 `ENDED`；
- 不能因为数据库活动仍为 `RUNNING` 就绕过时间判断。

## 8. 错误语义

### 8.1 管理接口

| HTTP | 错误码 | 场景 |
| ---: | --- | --- |
| `400` | `BAD_REQUEST` | 时间、枚举或数组类型无法解析，或出现未定义字段 |
| `400` | `VALIDATION_FAILED` | 字段范围、组合、时区、有效窗口或 366 天上限不合法 |
| `401/403` | 统一认证错误 | 未登录、账号不可用或缺少权限 |
| `403` | `SHOP_ACCESS_DENIED` | 不是目标店铺有效成员或缺少店铺权限 |
| `404` | `RESOURCE_NOT_FOUND` | 活动不存在、已删除或不属于路径数据范围 |
| `409` | `COUPON_ACTIVITY_STATE_CONFLICT` | 非草稿活动试图修改周期规则，或非 `FLASH_CLAIM` 活动配置周期抢券 |
| `409` | `VERSION_CONFLICT` | 请求版本与当前活动版本不一致 |
| `409` | `IDEMPOTENCY_KEY_REUSED` | 相同幂等键被用于不同请求体 |
| `422` | `COUPON_TEMPLATE_INVALID` | 发布时模板类型、状态或领取范围与周期活动不匹配 |

店铺越权优先返回 `RESOURCE_NOT_FOUND` 或现有统一店铺错误语义，不能通过 schedule 子资源泄露其他店铺活动及周期配置。

### 8.2 买家抢券

| HTTP | 错误码 | 场景 |
| ---: | --- | --- |
| `422` | `COUPON_ACTIVITY_NOT_CLAIMABLE` | 首场未开始、两场之间、活动暂停、最后一场已结束或活动取消 |
| `422` | `COUPON_TEMPLATE_NOT_CLAIMABLE` | 模板状态或模板领取范围不允许 |
| `422` | `COUPON_SOLD_OUT` | 周期活动累计发行量已用完 |
| `409` | `COUPON_USER_LIMIT_REACHED` | 用户跨所有场次累计达到限领上限 |
| `429` | `TOO_MANY_REQUESTS` | 触发限时抢券频率限制 |

读接口不新增 `unclaimableReason`。首次开抢前以及两个有效窗口之间均复用现有 `NOT_STARTED`；活动结束仍返回 `ACTIVITY_ENDED`，活动暂停仍返回 `ACTIVITY_PAUSED`。正式抢券接口在上述不可领取状态下复用现有 `422 COUPON_ACTIVITY_NOT_CLAIMABLE`。

## 9. 前端使用规范

前端创建或编辑周期活动时，应采用结构化控件：

1. 使用分段选项选择每天、每周、每月；
2. 周规则使用星期复选框，显示周一至周日，提交 ISO 数字；
3. 月规则使用 `1..31` 多选，不将“31 日”自动改为月末；
4. 使用时间选择器提交 `dailyStartsAt`，不让用户输入 cron；
5. 使用数字输入设置窗口时长；
6. 时区第一版固定显示“北京时间”，提交 `Asia/Shanghai`；
7. 保存后展示 API 返回的第一场和下一场时间，作为规则预览；
8. 收到 `VERSION_CONFLICT` 后重新加载，不用旧版本覆盖；
9. 活动详情页调用 schedule 子资源，并使用其中的 `serverTime` 校准倒计时；现有活动列表响应不增加周期字段；
10. 倒计时结束时重新请求详情，由 `claimable` 决定按钮状态。

前端不得：

- 拼接 cron 或根据活动文案推测日期；
- 只看 `status=RUNNING` 就允许点击；
- 在浏览器中排队并代表用户自动抢券；
- 把 ID 转为 JavaScript `Number`；
- 在场次切换时把剩余库存或本人已领数量本地重置。

## 10. 数据库与实现边界

### 10.1 数据库迁移

本需求需要新增专用周期规则表，例如 `coupon_activity_recurrence`，至少保存：

```text
activity_id
recurrence_type
weekdays_json
month_days_json
daily_starts_at
window_duration_minutes
recurrence_starts_at
recurrence_ends_at
timezone
created_at
updated_at
```

约束要求：

- `activity_id` 是主键并外键关联 `coupon_activity.id`，一项活动最多一条周期规则；
- 星期和月日使用独立 JSON 数组字段并由应用层按本文封闭规则解析，不使用逗号分隔字符串；
- 不修改 `subtitle`、`banner_url` 或其他展示字段保存规则；
- `coupon_activity.starts_at/ends_at` 保存服务端计算的生命周期边界；
- 本需求使用 `sql/scheme8.sql` 作为 `sql/scheme7.sql` 之后的增量迁移，没有回写任何已经发布的迁移。

第一版不需要持久化每一个未来场次，因为窗口可由冻结的规则确定性计算。如果未来引入每场库存、临时加场或单场取消，再新增 `coupon_activity_occurrence`，不能改变当前累计口径。

### 10.2 服务分层

建议职责边界：

| 组件 | 职责 |
| --- | --- |
| Controller | DTO 校验、路径参数和统一响应，不计算日期 |
| Schedule Service | 校验并保存规则，计算第一、当前、下一和最后窗口 |
| Coupon Admin Service | 权限、草稿状态、版本、发布校验和审计 |
| Coupon Service | 在领取事务中调用窗口计算，继续执行资格、库存和发券 |
| Coupon Task Service | 物化活动开始和最终结束状态，不按每场反复切换活动状态 |

日期计算应集中在一个可单元测试的组件中，使用 `java.time` 的 `ZoneId`、`ZonedDateTime`、`LocalDate` 和 `LocalTime`，禁止以字符串拼接或固定毫秒数计算自然日和月份。

### 10.3 自动任务

现有任务路径保持不变：

```http
POST /api/internal/tasks/start-coupon-activities
POST /api/internal/tasks/end-coupon-activities
```

任务只负责把活动生命周期从 `SCHEDULED` 推进到 `RUNNING`，以及在最后一个窗口结束后推进到 `ENDED`。不新增“每场开始时改为 RUNNING、每场结束时改回 SCHEDULED”的状态抖动。

多实例继续使用任务锁和数据库状态条件。任务延迟不能扩大领取窗口，领取接口的同步时间判断才是最终依据。

### 10.4 审计与日志

配置或替换周期规则必须写入现有优惠券操作日志，至少记录：

```text
activityId
operatorId
ownerType / shopId
beforeSchedule
afterSchedule
beforeVersion / afterVersion
requestId
operatedAt
```

日志不得记录 Token、Idempotency-Key 原文、IP 原文或设备指纹原文。错误响应不得暴露表名、SQL、内部类名或任务锁键。

## 11. 测试与验收清单

### 11.1 周期计算

1. `DAILY` 能计算连续日期的窗口。
2. `WEEKLY + [5,6,7]` 只生成周五、周六、周日窗口，且 `7` 明确为周日。
3. `MONTHLY + [29,30,31]` 在不同月份按跳过规则生成。
4. 跨午夜窗口按开始日期归属，并正确计算结束时间。
5. 起止边界使用左闭右开规则。
6. 生效范围内没有完整窗口时返回 `VALIDATION_FAILED`。
7. 超过 366 天、错误时区、重复日期、非法日期和非零秒均被拒绝。

### 11.2 权限与状态

1. 店铺成员只有读权限时只能查询，不能配置。
2. 店铺接口同时校验成员关系、权限和 SQL `shopId`。
3. 平台活动接口不能修改店铺自有活动。
4. 非 `FLASH_CLAIM` 活动不能配置周期抢券规则。
5. 只有 `DRAFT` 能替换规则，发布后修改返回状态冲突。
6. 暂停立即禁止领取，恢复不补偿错过场次。
7. 最后一场结束后，即使任务延迟也不能领取。

### 11.3 领取与并发

1. 窗口开始前一毫秒失败，开始时刻成功。
2. 窗口结束前一毫秒成功，结束时刻失败。
3. 首场开始前和两场之间，读接口返回既有 `NOT_STARTED`，正式写接口返回既有 `COUPON_ACTIVITY_NOT_CLAIMABLE`。
4. 相同抢券幂等键并发重放只生成一张用户券和一条领取记录。
5. 不同用户并发到库存上限时不超发、不超预算。
6. `perUserLimit` 和 `totalIssueLimit` 跨场次累计，不在下一场重置。
7. 生命周期任务、领取请求和暂停动作并发时，使用数据库状态和时间条件得到唯一合法结果。

### 11.4 API 契约

1. 所有外部 ID 为十进制字符串，时间包含 `+08:00` 偏移。
2. 新增的 `CouponActivityScheduleView` 固定返回 `scheduleType`、`window`、`serverTime` 和允许为空的窗口字段；现有领券摘要继续返回既有 `serverTime`，其他现有活动和领券 DTO 不增加未登记字段。
3. 未定义 JSON 字段返回 `400 BAD_REQUEST`，不静默忽略。
4. 旧一次性活动读取 schedule 子资源时返回 `scheduleType=ONCE`。
5. 现有一次性活动、模板和买家领取请求保持兼容。
6. 前端能仅依据返回契约正确展示“未开始、可抢、场间等待、暂停、结束”。
7. `CreateRecurringCouponActivityRequest`、`RecurringCouponSchedule`、`UpdateCouponActivityScheduleRequest`、`CouponActivityScheduleView` 和 `CouponClaimWindowView` 的精确字段已同步登记到 `docs/api/dto-catalog.md`，新增路径已同步到 `docs/api/coupon-api.md`。

## 12. 示例汇总

### 12.1 每天 20:00，持续 10 分钟

```json
{
  "recurrenceType": "DAILY",
  "weekdays": null,
  "monthDays": null,
  "dailyStartsAt": "20:00:00",
  "windowDurationMinutes": 10,
  "recurrenceStartsAt": "2026-08-14T00:00:00.000+08:00",
  "recurrenceEndsAt": "2026-08-21T00:00:00.000+08:00",
  "timezone": "Asia/Shanghai"
}
```

### 12.2 每周五、六、日 20:00，持续 30 分钟

```json
{
  "recurrenceType": "WEEKLY",
  "weekdays": [5, 6, 7],
  "monthDays": null,
  "dailyStartsAt": "20:00:00",
  "windowDurationMinutes": 30,
  "recurrenceStartsAt": "2026-08-14T00:00:00.000+08:00",
  "recurrenceEndsAt": "2026-10-01T00:00:00.000+08:00",
  "timezone": "Asia/Shanghai"
}
```

### 12.3 每月 1 日、15 日 10:00，持续 2 小时

```json
{
  "recurrenceType": "MONTHLY",
  "weekdays": null,
  "monthDays": [1, 15],
  "dailyStartsAt": "10:00:00",
  "windowDurationMinutes": 120,
  "recurrenceStartsAt": "2026-09-01T00:00:00.000+08:00",
  "recurrenceEndsAt": "2027-01-01T00:00:00.000+08:00",
  "timezone": "Asia/Shanghai"
}
```
