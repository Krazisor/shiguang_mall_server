# 时光商城

时光商城是一个面向多商户场景的教学商城后端。系统覆盖平台治理、店铺与商品、库存、购物车、交易与售后，以及优惠券和周期性限时领券等营销能力。

后端基于 Spring Boot 4 和 Java 21 构建，使用 MyBatis-Plus、MySQL、Redis、Sa-Token 和 OpenAPI；图片资源可选用 MinIO 存储。

## 快速开始

### 运行条件

- JDK 21
- Maven 3.9+
- MySQL 8.0.16+
- Redis
- MinIO（仅在启用图片上传时需要）

### 初始化本地环境

1. 复制根目录的 [`.env.example`](../.env.example) 为 `.env`，填写 MySQL、Redis 和可选的 MinIO 配置。
2. 在空数据库中按下文的顺序执行迁移脚本。
3. 启动应用：

   ```bash
   ./mvnw spring-boot:run
   ```

   也可以使用本机 Maven：

   ```bash
   mvn spring-boot:run
   ```

4. 服务默认监听 `http://localhost:8080`。启动后可访问：

   - 服务状态：`GET /`
   - OpenAPI JSON：`/v3/api-docs`
   - Swagger UI：`/swagger-ui.html`

### 本地验证

```bash
./mvnw test
```

大部分单元和接口契约测试不依赖外部服务；带 `@SpringBootTest` 的集成测试需要可用的 MySQL，部分基础设施测试还需要 Redis。真实基础设施的售后测试由 `REAL_INFRA_TEST=true` 显式启用。

## 数据库迁移

`sql/schema.sql` 是不可回写的空库基线，后续脚本必须按编号顺序追加。新库的完整安装顺序为：

```text
schema.sql
-> schema2.sql
-> scheme3.sql
-> scheme4.sql
-> scheme5.sql
-> scheme6.sql
-> scheme7.sql
-> scheme8.sql
-> scheme9.sql
```

已安装过基线或部分迁移的环境，只执行尚未应用的后续脚本，不要重新执行 `schema.sql`。其中 `schema2.sql`、`scheme4.sql` 和 `scheme5.sql` 含有可重复执行的权限/种子修正；其余脚本仍应按正常迁移流程记录执行状态。

- [`schema.sql`](../sql/schema.sql)：基础表、RBAC 和初始权限。
- [`schema2.sql`](../sql/schema2.sql)：当前完整契约所需的基础增量权限和种子修正。
- [`scheme3.sql`](../sql/scheme3.sql)：售后申诉、商家通知、商家虚拟钱包和对象存储元数据。
- [`scheme4.sql`](../sql/scheme4.sql)：平台店铺成员管理权限。
- [`scheme5.sql`](../sql/scheme5.sql)：平台商品总览与治理权限。
- [`scheme6.sql`](../sql/scheme6.sql)：优惠券活动、模板、用户券、兑换码、核销分摊、预算账本及交易优惠字段。
- [`scheme7.sql`](../sql/scheme7.sql)：售后申诉时间约束修正。
- [`scheme8.sql`](../sql/scheme8.sql)：周期性限时领券规则。
- [`scheme9.sql`](../sql/scheme9.sql)：优惠券模板 `ARCHIVED` 归档状态。

[`phase1-test-data.sql`](../sql/phase1-test-data.sql) 提供基础交易的可重复测试账号和数据，所有账号密码均为 `Market123`；[`demo-data.sql`](../sql/demo-data.sql) 用于补充前端演示数据。两者都不是生产迁移的一部分。

## 文档导航

### 先了解业务

| 文档 | 说明 |
| --- | --- |
| [产品调研与业务范围](product/product-research-and-scope.md) | 系统定位、角色、范围边界和统一业务决策 |
| [基础交易功能分册](product/phase-1-requirements.md) | 店铺、商品、库存、购物车、下单、支付和履约 |
| [治理与售后功能分册](product/phase-2-requirements.md) | RBAC、平台治理、售后退款、审计和自动任务 |
| [对象存储与图片资源](product/phase-3-requirements.md) | 图片上传、资源归属和安全校验 |
| [优惠券与限时领券需求](product/coupon-requirements.md) | 优惠券、领券中心、核销、退款返券、预算和运营治理 |

`phase-1` 和 `phase-2` 是保留的历史文件名，表示两个共同生效的功能分册，不代表可独立部署的版本。

### 前后端对接

| 文档 | 说明 |
| --- | --- |
| [统一 API 契约](api/common-contract.md) | 路径、鉴权、统一响应、分页、金额、时间、错误、幂等和并发约定 |
| [DTO 字段目录](api/dto-catalog.md) | 请求/响应字段、必填性、空值和组合约束 |
| [基础交易接口](api/phase-1-api.md) | 基础交易领域 HTTP 接口 |
| [治理与售后接口](api/phase-2-api.md) | RBAC、治理、售后和任务接口 |
| [三期接口设计](api/phase-3-api.md) | 库存调整、商家虚拟钱包、对象存储和平台资金查询 |
| [优惠券模块 API](api/coupon-api.md) | 优惠券、领券、结算选券、运营与治理接口 |

### 数据库与实现规范

| 文档 | 说明 |
| --- | --- |
| [基础数据库设计](database/phase-1-design.md) | 基线数据模型、字段语义、状态机、事务和锁顺序 |
| [三期数据库设计](database/phase-3-design.md) | 商家钱包、售后申诉、通知和对象存储增量结构 |
| [优惠券数据库设计](database/coupon-design.md) | 优惠券领域的数据模型、金额约束、状态机、并发和对账规则 |
| [前后端独立开发规范](development/frontend-backend-parallel-development.md) | 契约冻结、Mock、联调和验收流程 |
| [后端双线并行开发规范](development/backend-dual-track-development.md) | 领域边界、跨线端口、测试和集成顺序 |
| [后端共享基础设施规范](development/backend-shared-infrastructure-guide.md) | 统一响应、鉴权、异常、幂等、安全工具、PATCH 和测试约定 |
| [优惠券实现与运营规范](development/coupon-implementation-and-operations.md) | 角色分工、运营流程、风控、定时任务和故障处理 |

### 已实施的增量能力

`extra/` 记录了在主文档基础上新增、且已同步回产品/API/数据库文档的需求与接口说明：

| 文档 | 说明 |
| --- | --- |
| [平台店铺成员管理](extra/platform-shop-members-api.md) | 平台跨店查看和管理店铺成员 |
| [平台商品总览与违规治理](extra/platform-product-management.md) | 全量商品检索、强制下架和禁售 |
| [平台订单详情](extra/platform-order-detail-api.md) | 平台后台的跨店订单详情 |
| [商家角色与员工身份调整](extra/merchant-role-management.md) | 商家角色字典、员工身份查看和调整 |
| [周期定时抢券](extra/recurring-flash-coupon-api.md) | 固定周期的领券窗口配置与校验 |
| [优惠券模板归档](extra/coupon-template-archive-api.md) | 不删除历史记录的模板归档能力 |

## 文档与变更规则

发生文档表述不一致时，按以下优先级处理：

1. 物理数据库最终状态以 `sql/schema.sql` 加全部已发布增量迁移为准。
2. 字段语义、状态迁移、金额公式、事务和锁顺序以 `database/` 文档为准。
3. HTTP 路径、DTO、鉴权、错误码和幂等规则以 `api/` 文档为准。
4. 业务范围、页面能力和验收标准以 `product/` 文档为准。
5. `development/` 规定协作和实现方式，`extra/` 记录增量能力的需求背景及其契约入口。

修改冻结的业务规则时，必须在同一变更中同步更新相关 SQL、数据库设计、产品需求、API/DTO 文档、Java 实现和自动化测试。历史迁移文件及历史记录不得通过回写或删除的方式改变语义。
