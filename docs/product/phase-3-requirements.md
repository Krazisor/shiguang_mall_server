# 时光商城三期产品需求：对象存储与图片资源

## 1. 需求定位

三期新增统一的图片对象存储能力，使用已部署的 MinIO 保存图片二进制内容，Spring Boot 保存资源元数据并返回可复用 URL。该能力服务于用户头像、店铺 Logo、品牌 Logo、商品图片、富文本图片、售后凭证和平台申诉凭证。

本期不接收任意附件，不接收视频、压缩包、SVG、HTML 或脚本文件；不改变一期/二期已有 JSON URL 字段的兼容性。旧客户端仍可提交符合统一 URL 安全规则的 HTTPS URL，新客户端应先调用上传接口，再把返回的 `url` 写入相应业务接口。

## 2. 目标与范围

| 能力 | 本期要求 | 验收标准 |
| --- | --- | --- |
| MinIO 配置 | 通过环境变量配置 endpoint、凭证、bucket、公开地址和大小上限 | 密钥不写入仓库；启用时应用启动检查 bucket |
| 图片上传 | 统一 multipart 图片上传接口 | 成功返回资源编号、对象 key、摘要、MIME、大小和 URL |
| 图片校验 | 校验文件大小、文件头、MIME 和用途 | 伪造扩展名、错误 MIME、超限和不支持格式均被拒绝 |
| 资源归属 | 记录上传人和店铺范围 | 商品图片必须属于目标店铺；越权返回资源不存在或权限错误 |
| 图片复用 | 上传结果可用于已有图片 URL 字段 | 商品、SKU、店铺/品牌 Logo、头像和售后凭证可引用 MinIO URL |
| 审计 | 保存 SHA-256、原文件名、bucket、object key、状态和时间 | 可按用户、店铺、摘要追踪资源；不物理删除元数据 |
| 故障处理 | MinIO 或数据库失败时返回统一依赖错误 | 不返回半成功；写入对象后元数据失败应尽力清理对象 |

## 3. 图片用途和权限

上传请求通过 `purpose` 声明用途。服务端不接受客户端指定 bucket 或 object key。

| 用途 | 典型使用位置 | 数据范围和权限 |
| --- | --- | --- |
| `AVATAR` | `/api/auth/me` 头像 | 当前用户；`asset:upload` |
| `SHOP_LOGO` | 平台创建/编辑店铺 | 平台店铺管理权限；`asset:upload` |
| `BRAND_LOGO` | 平台品牌资料 | `platform:catalog:manage`；`asset:upload` |
| `PRODUCT_COVER` | 商品封面 | 目标店铺 `shop:product:manage` |
| `PRODUCT_GALLERY` | 商品画廊 | 目标店铺 `shop:product:manage` |
| `SKU_IMAGE` | SKU 图片 | 目标店铺 `shop:product:manage` |
| `RICH_TEXT_IMAGE` | 商品详情 HTML 图片 | 目标店铺 `shop:product:manage` |
| `AFTER_SALE_EVIDENCE` | 买家售后凭证 | 当前买家；`asset:upload` |
| `APPEAL_EVIDENCE` | 买家申诉补充凭证 | 当前买家；`asset:upload` |

商品相关用途必须提交 `shopId`，其他用途不得提交 `shopId`。上传成功不等于资源已经绑定到商品或售后；业务接口保存 URL 时仍须执行自己的资源归属和业务状态校验。

## 4. 文件和安全规则

- 只允许 JPEG、PNG、GIF、WEBP。
- 服务端以文件头识别真实格式，不信任扩展名和浏览器提交的 `Content-Type`。
- 默认单文件上限为 `10 MiB`，multipart 请求上限为 `12 MiB`；部署环境可以降低但不能绕过应用层校验。
- object key 由服务端使用日期、用途和 UUID 生成，禁止路径穿越、覆盖写和任意 bucket。
- 原始文件名只用于审计和展示，服务端会去除路径并限制长度。
- 公开读模式返回稳定 URL；私有读模式返回有效期为 1 小时的预签名 GET URL。
- 公开 URL 仍需满足统一 `ContentSafety` 规则；生产环境图片 URL 必须使用 HTTPS。
- 上传接口不解析或执行图片中的脚本；SVG、HTML、富文本和任意附件均拒绝。

## 5. 配置要求

配置前缀为 `market.storage`，环境变量名称如下：

```text
MINIO_ENABLED
MINIO_ENDPOINT
MINIO_ACCESS_KEY
MINIO_SECRET_KEY
MINIO_BUCKET
MINIO_PUBLIC_BASE_URL
MINIO_OBJECT_PREFIX
MINIO_AUTO_CREATE_BUCKET
MINIO_PUBLIC_READ
MINIO_MAX_FILE_SIZE_BYTES
UPLOAD_MAX_FILE_SIZE
UPLOAD_MAX_REQUEST_SIZE
```

`MINIO_ENABLED=false` 时应用仍可启动，但上传接口返回 `STORAGE_NOT_CONFIGURED`。启用时如果凭证、bucket 或 bucket 初始化失败，应用启动失败，避免运行中才发现图片无法上传。

## 6. 页面和业务流程

### 买家端

- 个人资料页选择头像，先上传 `AVATAR`，再提交头像 URL。
- 售后申请页上传凭证，先上传 `AFTER_SALE_EVIDENCE`，再创建售后申请。
- 申诉页上传补充证据，先上传 `APPEAL_EVIDENCE`，再提交申诉。

### 商家端

- 商品编辑页分别上传 `PRODUCT_COVER`、`PRODUCT_GALLERY`、`SKU_IMAGE` 和 `RICH_TEXT_IMAGE`。
- 上传图片时必须使用当前店铺 `shopId`，不能跨店复用需要店铺范围的资源。

### 平台端

- 店铺资料编辑使用 `SHOP_LOGO`。
- 品牌资料编辑使用 `BRAND_LOGO`。
- 平台人员不能借助商品用途绕过平台/店铺权限。

## 7. 验收场景

1. MinIO 已配置且 bucket 存在，上传一张 PNG，返回 `201` 和可访问 URL，`object_asset` 写入一条 `ACTIVE` 记录。
2. 上传 JPEG、PNG、GIF、WEBP 均成功；将 PNG 改名为 `.jpg` 仍按文件头识别为 PNG。
3. 上传 SVG、HTML、空文件、超限文件、伪造 MIME 文件均失败，不产生有效资源记录。
4. 商家 A 使用 `shopId=A` 上传商品图成功，使用 `shopId=B` 或无店铺权限时失败。
5. 上传返回的 URL 可被商品封面、SKU 图片、店铺 Logo、头像、售后凭证和申诉凭证字段引用。
6. MinIO 上传成功但数据库写入失败时，对象被尽力删除，接口返回 `503 DEPENDENCY_UNAVAILABLE`。
7. `MINIO_PUBLIC_READ=false` 时，返回预签名 URL；预签名 URL 过期后不能继续访问。
8. 相同文件可以重复上传并生成不同资源编号；SHA-256 仅用于审计和排障，不以摘要唯一约束阻止业务上传。
