# 时光商城文档索引

本目录是时光商城当前业务范围的产品、接口、数据库和协作规范入口。系统定位为多商户、类京东的教学商城，当前实现采用 Spring Boot 4、Java 21、MyBatis-Plus、MySQL、Redis 和 Sa-Token。

## 1. 阅读顺序

| 顺序 | 文档 | 适用对象 | 说明 |
| --- | --- | --- | --- |
| 1 | [产品调研与业务范围](product/product-research-and-scope.md) | 全员 | 产品定位、角色、范围边界、调研结论和统一业务决策 |
| 2 | [基础交易功能分册（原一期）](product/phase-1-requirements.md) | 产品、前端、后端、测试 | 正向交易闭环及对应验收标准 |
| 3 | [治理与售后功能分册（原二期）](product/phase-2-requirements.md) | 产品、前端、后端、测试 | 权限治理、售后退款、审计与自动任务 |
| 4 | [统一 API 契约](api/common-contract.md) | 前端、后端、测试 | URL、鉴权、响应、分页、金额、时间、错误、幂等和并发约定 |
| 5 | [DTO 字段目录](api/dto-catalog.md) | 前端、后端、测试 | 全部请求/响应字段、必填性、空值、边界和组合约束 |
| 6 | [基础交易接口分册（原一期）](api/phase-1-api.md) | 前端、后端、测试 | 正向交易相关 HTTP 接口、DTO 和业务错误 |
| 7 | [治理与售后接口分册（原二期）](api/phase-2-api.md) | 前端、后端、测试 | 治理、售后、任务相关 HTTP 接口、DTO 和业务错误 |
| 8 | [三期接口设计：库存调整、商家虚拟钱包与对象存储](api/phase-3-api.md) | 产品、前端、后端、测试 | 已实现的商家库存调整、店铺结算余额、虚拟提现、MinIO 图片上传和资金一致性契约 |
| 9 | [三期产品需求：对象存储与图片资源](product/phase-3-requirements.md) | 产品、前端、后端、测试 | MinIO 图片上传、资源归属、安全校验和验收场景 |
| 10 | [前后端独立开发规范](development/frontend-backend-parallel-development.md) | 前端、后端、测试 | 契约冻结、Mock、联调、验收数据和变更流程 |
| 11 | [后端双线并行开发规范](development/backend-dual-track-development.md) | 两名后端、测试 | 共享基座、领域归属、跨线端口、测试和合并顺序 |
| 12 | [后端共享基础设施复用与扩展规范](development/backend-shared-infrastructure-guide.md) | 后端、测试 | Sa-Token、统一响应、异常、幂等、安全工具、PATCH、分层和测试规范 |
| 13 | [一期数据库设计规范](database/phase-1-design.md) | 后端、测试、DBA | 一期/二期 26 张表、字段、约束、状态机、事务和锁顺序 |
| 14 | [三期新增表与对象存储数据库设计](database/phase-3-design.md) | 后端、测试、DBA | `scheme3.sql` 新增 7 张表的结构、字段含义、约束、商家钱包和 MinIO 资源边界 |
| 15 | [`schema.sql`](../sql/schema.sql) | 后端、DBA | MySQL 8.0.16+ 空库初始化物理结构及 RBAC 种子数据，不回写历史发布内容 |
| 16 | [`schema2.sql`](../sql/schema2.sql) | 后端、DBA | 当前完整版本必需的增量迁移，可重复执行 |
| 17 | [`scheme3.sql`](../sql/scheme3.sql) | 后端、DBA | 本期售后申诉、商家通知、对象存储元数据，以及三期商家钱包和权限的增量迁移；与当前 Java 实现配套 |
| 18 | [`scheme6.sql`](../sql/scheme6.sql) | 后端、DBA | 优惠券活动、模板、用户券、兑换码、核销分摊、预算账本以及交易/结算优惠字段迁移；按 `scheme3.sql` 后执行 |
| 19 | [`scheme7.sql`](../sql/scheme7.sql) | 后端、DBA | 修正售后申诉时间约束；正常按编号在 `scheme6.sql` 后执行，紧急修复时最低只依赖 `scheme3.sql` |

## 2. 当前版本与开发阶段

`phase-1-*`、`phase-2-*` 文件名为历史稳定名称，当前只表示两个已冻结功能分册，不表示可独立部署的数据库版本。三期文档记录已落地的扩展能力和对应契约；优惠券模块的产品、接口、数据库和运维规范分别见 `product/coupon-requirements.md`、`api/coupon-api.md`、`database/coupon-design.md`、`development/coupon-implementation-and-operations.md`，其物理结构由 `scheme6.sql` 提供。真实银行或第三方财务出款仍不做。

整个开发过程仍分为两个实施阶段，但后端不再按原一期/二期机械分人：

| 实施阶段 | 目标 | 主要产物 |
| --- | --- | --- |
| 阶段一：契约与共享基座 | 冻结统一契约、DTO、错误码、数据库迁移和跨线端口 | 可供前端使用的 Mock 契约；可供 A/B 后端线使用的公共基础设施与端口 Fake |
| 阶段二：双线实现与集成 | A/B 两条后端线并行实现全部当前范围，再完成跨线集成 | 完整 API、领域测试、跨线事务测试和端到端验收 |

后端 A 线负责身份、平台治理、店铺、商品和库存；B 线负责地址、购物车、交易、支付、订单、售后和任务。详细边界以[后端双线并行开发规范](development/backend-dual-track-development.md)为准。

`database/phase-1-design.md` 文件名同样沿用最初提交名称，其 26 表设计是当前完整版本共同使用的数据模型。物理数据库的有效状态始终为 `schema.sql` 执行结果再按顺序叠加 `schema2.sql` 及后续迁移。

## 3. 文档权威性

当不同材料表述不一致时，按下列规则处理：

1. 数据库最终状态以 [`sql/schema.sql`](../sql/schema.sql) 为不可回写基线，并按编号顺序叠加 [`sql/schema2.sql`](../sql/schema2.sql) 等迁移脚本；新空库执行 `schema.sql -> schema2.sql`，已执行基线的存量库只执行尚未应用的增量脚本。
2. 字段语义、状态迁移、金额公式、事务和锁顺序以 [数据库设计规范](database/phase-1-design.md) 为准。
3. HTTP 路径、请求响应 DTO、鉴权、错误码和幂等规则以 `docs/api/` 为准。
4. 阶段范围、页面能力、业务验收标准以 `docs/product/` 为准。
5. Java 实现必须同时满足上述文档，不得通过实体类反向改变已冻结的外部接口。

如果确实需要修改已冻结规则，必须在同一个变更中同步更新受影响的 SQL、数据库文档、API 文档、产品文档、Java 枚举/DTO 和自动化测试。

## 4. 当前明确不做

一期/二期基线数据库（`schema.sql` + `schema2.sql`）没有对应模型，下列能力不进入该基线：真实第三方支付、优惠券/满减/秒杀、评价、收藏、搜索引擎、推荐、站内信、聊天客服、发票、物流轨迹、运费模板、商家资质申请、换货/维修、泛化争议仲裁、泛化文件存储、视频/压缩包/任意附件和多仓库存。本期针对具体售后单的申诉、平台裁决和商家通知，以及三期对象存储图片元数据、店铺虚拟钱包、结算和虚拟提现，由 `scheme3.sql` 增量提供；真实银行或第三方财务出款仍不做。

这些能力不得通过在现有字段中塞入非预期 JSON 或复用状态码的方式临时实现；后续立项时应独立补充产品、数据和接口设计。
