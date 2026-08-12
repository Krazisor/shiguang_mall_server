# 时光商城三期数据库设计：新增表与对象存储资源

## 1. 文档定位

本文完整说明 `sql/scheme3.sql` 新增的 7 张表：商家虚拟钱包、订单结算、虚拟提现、商家钱包流水、售后申诉、商家通知和 MinIO 图片资源元数据。字段含义、约束和应用层规则以本文与 [`sql/scheme3.sql`](../../sql/scheme3.sql) 为准；一期/二期基线表仍以 [一期数据库设计](phase-1-design.md) 为准。

物理数据库按以下顺序建立：

```text
schema.sql -> schema2.sql -> scheme3.sql
```

以下表均不属于一期/二期 26 张表基线，由 `scheme3.sql` 创建。商家钱包表保存店铺归属的虚拟账务，`object_asset` 只保存对象的审计元数据和归属关系，图片二进制内容保存在 MinIO，不写入 MySQL。

## 2. 对象存储模型

上传流程如下：

```text
客户端 multipart 上传
    -> Spring Boot 校验 MIME、文件头、大小和用途
    -> MinIO 写入 bucket/objectKey
    -> MySQL 写入 object_asset 元数据
    -> 返回可供现有图片 URL 字段使用的 URL
```

MinIO 是三期唯一的对象存储实现。应用不接受客户端直接指定 bucket 或 object key；bucket 由配置决定，object key 由服务端生成。上传接口只支持图片，不提供任意文件上传能力。

## 3. 表结构与字段含义

### 3.1 `object_asset`

一条记录代表一次成功写入对象存储的图片资源。资源创建后不可修改对象内容；删除采用状态和时间标记，不物理删除元数据。

| 字段 | 类型与空值 | 含义 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED`，主键，自增 | 资源内部 ID，API 以十进制字符串返回 |
| `asset_no` | `VARCHAR(64)`，非空，唯一 | 资源业务编号，由服务端生成 |
| `bucket` | `VARCHAR(128)`，非空 | 实际写入的 MinIO bucket 快照 |
| `object_key` | `VARCHAR(512)`，非空 | 对象在 bucket 中的完整 key；客户端不能指定 |
| `original_filename` | `VARCHAR(255)`，非空 | 经过路径清理和长度截断后的原始文件名，仅用于展示和审计 |
| `content_type` | `VARCHAR(100)`，非空 | 服务端根据文件头识别的图片 MIME，当前为 JPEG/PNG/GIF/WEBP |
| `size_bytes` | `BIGINT UNSIGNED`，非空 | 上传内容大小，必须大于 0，并受 MinIO 配置上限约束 |
| `sha256` | `CHAR(64)`，非空 | 对象内容 SHA-256 摘要，用于审计、去重检查和完整性排障 |
| `purpose` | `VARCHAR(32)`，非空 | `AVATAR`、`SHOP_LOGO`、`BRAND_LOGO`、`PRODUCT_COVER`、`PRODUCT_GALLERY`、`SKU_IMAGE`、`RICH_TEXT_IMAGE`、`AFTER_SALE_EVIDENCE`、`APPEAL_EVIDENCE` |
| `owner_user_id` | `BIGINT UNSIGNED`，非空，外键 | 上传用户；账号删除不会级联删除历史资源 |
| `shop_id` | `BIGINT UNSIGNED`，条件可空，外键 | 店铺作用域。商品封面、商品画廊、SKU 图片和富文本图片必须填写；头像、店铺/品牌 Logo、售后凭证和申诉凭证必须为空 |
| `public_url` | `VARCHAR(1024)`，可空 | 公开读模式下的稳定 URL；私有读模式下为空，接口返回短期预签名 URL |
| `status` | `VARCHAR(16)`，非空，默认 `ACTIVE` | `ACTIVE` 或 `DELETED` |
| `created_at` | `DATETIME(3)`，非空 | 资源上传成功时间 |
| `deleted_at` | `DATETIME(3)`，可空 | 标记删除时间；`DELETED` 时必填，`ACTIVE` 时必须为空 |

索引和约束：

- `asset_no` 唯一，保证 API 业务编号稳定。
- `(bucket, object_key)` 唯一，防止同一对象被重复登记。
- `owner_user_id`、`shop_id`、`sha256` 建立查询索引，支持资源审计和孤儿对象排查。
- `content_type` 只能是 `image/jpeg`、`image/png`、`image/gif`、`image/webp`。
- `purpose` 必须使用稳定枚举值，不能使用任意字符串。
- 商品相关用途必须有 `shop_id`，其他用途必须没有 `shop_id`，防止跨作用域登记资源。
- 数据库不删除 MinIO 对象；应用删除动作应先写资源状态，再由受保护清理任务删除对象。

### 3.2 `merchant_wallet_account`

一间店铺最多拥有一个商家钱包。该聚合独立于一期买家 `wallet_account`，不能用于买家充值或支付。

| 字段 | 类型与空值 | 含义 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED`，主键，自增 | 商家钱包 ID，API 以 `Id` 返回 |
| `shop_id` | `BIGINT UNSIGNED`，非空，唯一，外键 | 钱包归属店铺 |
| `currency` | `CHAR(3)`，非空 | 当前固定为 `CNY` |
| `pending_balance` | `DECIMAL(18,2)`，非负 | 已支付但尚未过观察期的待结算余额 |
| `available_balance` | `DECIMAL(18,2)`，非负 | 可申请虚拟提现的余额 |
| `frozen_balance` | `DECIMAL(18,2)`，非负 | 提现处理中或风控冻结的余额 |
| `lifetime_gross_income` | `DECIMAL(18,2)`，非负 | 历史订单毛收入统计快照，只增不减 |
| `lifetime_commission` | `DECIMAL(18,2)`，非负 | 历史平台佣金统计快照 |
| `lifetime_refund` | `DECIMAL(18,2)`，非负 | 历史商家退款统计快照 |
| `status` | `VARCHAR(20)`，非空 | `ACTIVE`、`FROZEN`、`CLOSED` |
| `version` | `INT UNSIGNED`，非空 | 钱包乐观锁版本，每次余额变化递增 |
| `created_at`、`updated_at` | `DATETIME(3)`，非空 | 创建和最后修改时间 |

约束：三类余额和累计统计不得为负；`shop_id` 唯一；钱包关闭或冻结时禁止对应写操作。

### 3.3 `shop_settlement`

每个店铺子订单一条结算快照，记录订单金额、佣金和退款对商家余额的影响。

| 字段 | 类型与空值 | 含义 |
| --- | --- | --- |
| `id`、`settlement_no` | 主键；`VARCHAR(64)` 唯一 | 结算记录 ID 和业务编号 |
| `shop_id`、`wallet_id` | 非空联合外键 | 店铺和对应商家钱包 |
| `trade_id`、`order_id` | 非空外键 | 父交易和店铺子订单 |
| `status` | `VARCHAR(24)`，非空 | `PENDING`、`READY`、`SETTLED`、`REFUNDED`、`RECOVERY_REQUIRED` |
| `gross_amount` | `DECIMAL(18,2)`，正数 | 子订单应付总额 |
| `commission_rate` | `DECIMAL(7,4)` | 百分比费率；`0.1250` 表示 `0.1250%` |
| `commission_refundable` | `TINYINT(1)` | 佣金是否允许按退款比例冲回 |
| `commission_amount` | `DECIMAL(18,2)` | 佣金金额快照 |
| `buyer_refund_amount` | `DECIMAL(18,2)` | 买家累计退款金额 |
| `commission_refund_amount` | `DECIMAL(18,2)` | 退回平台佣金金额 |
| `merchant_refund_amount` | `DECIMAL(18,2)` | 实际从商家净收入扣除的金额 |
| `net_amount` | `DECIMAL(18,2)` | `gross_amount - commission_amount` |
| `pending_amount` | `DECIMAL(18,2)` | 当前尚未释放的净收入 |
| `released_amount` | `DECIMAL(18,2)` | 已转入可用余额的净收入 |
| `available_at`、`settled_at` | 可空时间 | 预计可结算时间和实际结算时间 |
| `version`、`created_at`、`updated_at` | 版本和审计时间 | 并发控制与生命周期记录 |

金额约束保证 `net_amount = gross_amount - commission_amount`，且
`pending_amount + released_amount + merchant_refund_amount = net_amount`；退款不足以从商家余额冲回时进入 `RECOVERY_REQUIRED`，不得产生负余额。

### 3.4 `merchant_withdrawal`

虚拟提现申请只表示教学环境中的店铺出款状态，不接入真实银行、微信或支付宝。

| 字段 | 类型与空值 | 含义 |
| --- | --- | --- |
| `id`、`withdrawal_no` | 主键；`VARCHAR(64)` 唯一 | 提现记录 ID 和业务编号 |
| `wallet_id`、`shop_id` | 非空联合外键 | 目标钱包和店铺 |
| `status` | `VARCHAR(20)` | `PROCESSING`、`SUCCESS`、`FAILED`、`REJECTED` |
| `amount`、`fee_amount`、`net_amount` | `DECIMAL(18,2)` | 申请金额、手续费和实际虚拟出款金额；`net_amount = amount - fee_amount` |
| `destination_type` | `VARCHAR(30)` | 当前固定 `VIRTUAL_ACCOUNT` |
| `destination_account` | `VARCHAR(128)` | 虚拟收款账号；响应中必须脱敏 |
| `remark`、`failure_reason` | 可空字符串 | 申请备注和失败/驳回原因 |
| `requested_by` | 非空外键 | 发起提现的店铺成员 |
| `requested_at`、`completed_at` | 时间 | 申请和处理完成时间 |
| `version` | `INT UNSIGNED` | 状态并发版本 |
| `business_no` | `VARCHAR(128)`，唯一 | 幂等业务号 |
| `created_at`、`updated_at` | `DATETIME(3)` | 审计时间 |

`PROCESSING` 时冻结可用余额；`SUCCESS` 最终扣除 frozen；`FAILED/REJECTED` 将 frozen 转回 available，重复处理必须幂等。

### 3.5 `merchant_wallet_transaction`

该表是不可更新、不可删除的商家钱包流水，所有余额变化必须通过事务服务写入。

| 字段 | 类型与空值 | 含义 |
| --- | --- | --- |
| `id`、`transaction_no` | 主键；`VARCHAR(64)` 唯一 | 流水 ID 和编号 |
| `wallet_id`、`shop_id` | 非空联合外键 | 钱包和店铺 |
| `transaction_type` | `VARCHAR(32)` | 订单入账、结算释放、佣金、退款、提现和平台调账类型 |
| `direction` | `VARCHAR(10)` | `CREDIT`、`DEBIT`、`TRANSFER`；转移表示余额区间原子转账 |
| `source_bucket`、`target_bucket` | 可空 | `PENDING`、`AVAILABLE`、`FROZEN` 的来源/目标余额区 |
| `amount` | `DECIMAL(18,2)`，正数 | 本次流水金额 |
| `pending_before/after` | `DECIMAL(18,2)` | 流水前后待结算余额 |
| `available_before/after` | `DECIMAL(18,2)` | 流水前后可用余额 |
| `frozen_before/after` | `DECIMAL(18,2)` | 流水前后冻结余额 |
| `business_type`、`business_no` | 非空，业务号唯一 | 根业务类型和幂等号 |
| `settlement_id`、`order_id`、`withdrawal_id` | 可空联合外键 | 关联结算、子订单和提现 |
| `operator_id` | 可空外键 | 平台人工调账操作者；非人工流水可空 |
| `remark` | 可空字符串 | 业务说明；`PLATFORM_ADJUST` 时必填 |
| `created_at` | `DATETIME(3)` | 流水发生时间 |

数据库 `CHECK` 约束同时校验方向、余额前后值和流水类型组合，确保流水可由余额快照重算。

### 3.6 `after_sale_appeal`

该表保存买家针对商家拒绝或超时未处理的售后申诉，平台裁决结果驱动原售后聚合的退款或退货流程。

| 字段 | 类型与空值 | 含义 |
| --- | --- | --- |
| `id`、`appeal_no` | 主键；`VARCHAR(64)` 唯一 | 申诉 ID 和业务编号 |
| `after_sale_id` | 非空唯一外键 | 关联售后申请；一份售后最多一条申诉 |
| `shop_id`、`appellant_user_id` | 非空外键 | 目标店铺和申诉买家 |
| `trigger_type` | `VARCHAR(30)` | `MERCHANT_REJECTED` 或 `MERCHANT_TIMEOUT` |
| `status` | `VARCHAR(20)` | `PENDING`、`APPROVED`、`REJECTED` |
| `reason_code`、`reason_description` | 非空 | 申诉原因和补充说明 |
| `evidence_json` | 可空 JSON 数组 | 申诉图片 URL 列表 |
| `merchant_reviewer_id`、`merchant_review_comment`、`merchant_reviewed_at` | 可空 | 商家拒绝快照；超时申诉必须为空 |
| `decision`、`approved_quantity`、`approved_amount` | 可空 | 平台 `APPROVE/REJECT` 和批准结果 |
| `decided_by`、`decision_comment`、`decided_at` | 可空 | 平台操作者、裁决理由和时间 |
| `version`、`created_at`、`updated_at` | 版本和审计时间 | 申诉并发控制及生命周期 |

`merchant_reviewed_at` 是申诉创建时从原售后单复制的历史审核时间，正常情况下早于申诉的 `created_at`，不属于申诉自身生命周期的起点。数据库不比较两者，避免应用时钟和数据库时钟的细微偏差阻断正常申诉；平台 `decided_at` 仍不得早于申诉创建时间。已执行 `scheme3.sql` 的数据库必须继续执行 `scheme7.sql`，以替换早期版本中错误的跨生命周期 `chk_after_sale_appeal_times` 比较。

`PENDING` 不得有裁决字段；`APPROVED` 必须有正数批准数量/金额和完整裁决信息；`REJECTED` 不得有批准金额，但必须有裁决人、原因和时间。

### 3.7 `merchant_notification`

该表保存申诉提交和平台裁决事件投递给店铺有效成员的通知，通知只允许目标店铺成员读取。

| 字段 | 类型与空值 | 含义 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED`，主键 | 通知 ID |
| `shop_id`、`recipient_user_id` | 非空外键 | 店铺和接收成员 |
| `appeal_id`、`after_sale_id` | 非空联合外键 | 对应申诉及售后 |
| `notification_type` | `VARCHAR(40)` | `AFTER_SALE_APPEAL_SUBMITTED` 或 `AFTER_SALE_APPEAL_DECIDED` |
| `title`、`content` | 非空字符串 | 通知标题和内容 |
| `read_at` | 可空时间 | 首次标记已读时间 |
| `created_at`、`updated_at` | `DATETIME(3)` | 创建和最后更新时间 |

唯一键 `(appeal_id, notification_type, recipient_user_id)` 防止同一事件重复投递；通知不因成员离职而物理删除。

## 4. MinIO 配置与数据库边界

配置前缀为 `market.storage`，环境变量映射见 [三期接口设计](../api/phase-3-api.md) 和 `.env.example`：

| 配置 | 作用 |
| --- | --- |
| `MINIO_ENABLED` | 是否启用对象存储；关闭时上传接口返回 `STORAGE_NOT_CONFIGURED` |
| `MINIO_ENDPOINT` | MinIO API 地址，不直接作为公开图片地址使用 |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | MinIO 访问凭证，只允许通过环境变量或密钥管理系统注入 |
| `MINIO_BUCKET` | 应用使用的 bucket |
| `MINIO_PUBLIC_BASE_URL` | 浏览器访问对象的公开地址前缀；生产环境应使用 HTTPS |
| `MINIO_OBJECT_PREFIX` | 服务端生成 object key 的根前缀 |
| `MINIO_AUTO_CREATE_BUCKET` | 启动时是否自动创建不存在的 bucket |
| `MINIO_PUBLIC_READ` | `true` 返回稳定公开 URL；`false` 返回短期预签名 GET URL |
| `MINIO_MAX_FILE_SIZE_BYTES` | 应用层图片大小上限 |

Spring multipart 的 `UPLOAD_MAX_FILE_SIZE` 和 `UPLOAD_MAX_REQUEST_SIZE` 是 HTTP 层上限，必须不小于应用层单文件限制，并预留 multipart 请求开销。

## 5. 事务、故障和安全规则

1. 先上传 MinIO，再插入 `object_asset`；数据库插入失败时服务尽力删除对象，并记录失败日志。
2. 只在 MinIO 上传和元数据插入都成功后返回 `201 Created`。
3. MinIO 不可用时返回 `503 DEPENDENCY_UNAVAILABLE`；未启用时返回 `422 STORAGE_NOT_CONFIGURED`。
4. 服务端根据文件头识别 MIME，不信任扩展名和客户端 `Content-Type`；客户端 MIME 与文件头不一致时拒绝。
5. object key 使用服务端随机 UUID，不允许路径穿越、覆盖指定对象或让客户端写入任意 bucket。
6. URL 字段仍经过统一 `ContentSafety` 校验。公开 URL 必须是 HTTPS；仅本地开发可按 `market.content.allow-local-http` 放行 localhost HTTP。
7. 商品图片必须通过目标店铺 `shop:product:manage`；头像及售后凭证必须属于当前登录用户并具有平台级 `asset:upload`；平台 Logo 受平台对应权限控制并具有 `asset:upload`。
8. 上传接口不接收 HTML、SVG、脚本、压缩包、视频或任意附件；富文本中的图片仍需使用 `RICH_TEXT_IMAGE` 上传后再提交 URL。
