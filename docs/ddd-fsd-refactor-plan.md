# 架构升级 Plan：后端 DDD × 前端 FSD

> 状态：草案（2026-07-03 grilling 会话产出）。
> 硬性要求：后端严格按 DDD、前端严格按 FSD 重构；细节采用社区通用方案，不重复造轮子；保持既有编码规范（命名、import 规则、Spotless/Prettier、70% 覆盖率、`req=<id> tenant=<id>` 日志格式、AppProperties、Liquibase 路径约定）。

## 一、已敲定的决策

| # | 决策 | 依据 |
|---|------|------|
| D1 | 模块按**领域模型**划分；Central/Store 是**授权作用域**，不是模块边界。同一领域模型可有多个接口适配层（`api/central`、`api/store`）做权限控制，共用领域层与基础设施 | [ADR-0002](./adr/0002-modules-by-domain-model-scopes-as-authorization.md)；单库 + `tenant_id` @Filter，物理上只是权限差异 |
| D2 | 充血模型；**聚合 = JPA 实体**（无公开 setter，不变量在行为方法内）；读侧用 Spring Data projection/DTO 查询，不经过聚合；个案逃生门 | [ADR-0001](./adr/0001-aggregate-equals-jpa-entity.md)；Khorikov / Drotbohm / Spring Modulith 示例 |
| D3 | **跨聚合一律 ID 引用**（`Order.customerId/castId/receptionistId`），数据库 FK 保留；对象组装需要时由 application 层完成，列表页走 JPQL join 出 DTO | Vernon 聚合规则；Spring Modulith 可机检 |
| D4 | 引入 **Spring Modulith**：顶层包 = 模块，`ApplicationModules.verify()` 进 CI；模块间通信用事件（`@ApplicationModuleListener`，事务提交后投递 + 事件发布注册表） | 用户确认；现有 `TenantCreatedEvent` 平滑升级 |
| D5 | 前端按 **FSD 官方 Next.js 适配**：根目录 `app/` 为纯路由薄壳，FSD 层入 `src/`（`_app`、`_pages` 改名避冲突）；**Steiger**（官方 linter）进 CI | [feature-sliced.design 官方指南](https://feature-sliced.design/docs/guides/tech/with-nextjs) |
| D6 | FSD **entities 共享**（镜像后端领域模型），pages 按 scope 前缀切 slice（`central-*` / `store-*`），features 按用户动作切 | 与 D1 对称 |
| D7 | 统一语言：**Tenant = 门店（1:1）**；平台语境叫 Tenant，门店侧 scope 命名用 Store 前缀（StoreUser、store-orders、StoreProfile）。词汇表见 [CONTEXT.md](../CONTEXT.md) | grilling 确认 |
| D8 | 迁移策略：**逐模块 PR、工具先行**（默认方案，可调整） | 可独立 review/回滚 |

### 工作方案（落地前逐个确认）

- **Menu**：两侧字段完全同构 → 建议合并为单一 `Menu` 模型 + scope 字段（需一次小数据迁移，id 类型统一）。
- **Config 假同名**：`SystemConfig`（平台键值设置）与 `TenantConfig` 各自成模块；`TenantConfig` 改名 **StoreProfile**（实体/类名改，表名 `t_tenant_configs` 可不动）。
- **User**：一个 `user` 模块内保留 `CentralUser` / `StoreUser`（原 TenantUser）两个聚合（登录标识与角色目录确实不同），共用认证基础设施。
- **auth** 独立成模块（登录/JWT/会话），具体聚合边界实施时再议。

## 二、后端目标结构

每个模块（顶层包）内部 DDD 四层：

```
com.kizuna
├── shared/                    # 共享内核：TenantContext、BaseEntity、Snowflake、
│                              # 通用异常、AppProperties、安全/缓存等横切配置
├── tenant/                    # 租户生命周期（注册、开通、维护模式实施）
├── auth/                      # 认证（central/store 两侧登录、JWT）
├── user/                      # CentralUser + StoreUser
├── cast/                      # HRM：在册接待人员
├── customer/                  # CRM：客户
├── order/                     # CRM：订单/预约
├── menu/                      # 导航菜单（两 scope 同一概念）
├── settings/                  # SystemConfig：平台键值设置
├── storeprofile/              # 门店站点品牌配置（原 TenantConfig）
├── notification/              # 邮件发送（支撑子域）
└── storage/                   # 文件存储（支撑子域）

<module>/
├── domain/          # 聚合根(JPA实体,充血)、值对象、枚举、领域事件、Repository 接口
├── application/     # 用例服务（事务边界）、读侧查询服务(projection)
├── infrastructure/  # 额外适配器（Spring Data 生成的实现无需文件）
└── api/
    ├── central/     # central 侧 controller（按需）
    ├── store/       # store 侧 controller（按需）
    └── dto/         # request/response record + MapStruct mapper
```

充血化改造要点（以 Order 为例）：

- `status: String` → `OrderStatus` 枚举 + 状态迁移方法（`confirm()`/`complete()`/…，非法迁移抛领域异常）；
- 三个 `@ManyToOne` → ID 字段（D3）；列表查询改 JPQL join projection；
- `TenantConfig.tenant`（跨 scope 对象引用）→ `tenantId`；
- 现有 `TenantCreatedEvent`/listener 从 `controller` 包归位到 `tenant/domain` 事件 + 消费方模块的 `@ApplicationModuleListener`。

边界机检：新增 `ModularityTests`（`ApplicationModules.verify()` + Modulith 文档生成）；可选 ArchUnit 补充层内规则（domain 不依赖 application/api）。

## 三、前端目标结构

```
frontend/
├── app/                       # Next App Router：纯路由薄壳，只 re-export
│   ├── central/…/page.tsx     #   export { XxxPage as default } from '@/_pages/central-xxx'
│   ├── tenant/…/page.tsx      #   URL 暂保持 /tenant/*（对外路径不动，见未决 Q3）
│   ├── login/ register/ health/
│   └── layout.tsx
└── src/
    ├── _app/                  # providers、全局样式、proxy 相关初始化
    ├── _pages/                # central-dashboard, central-tenants, central-settings,
    │                          # store-dashboard, store-orders, store-casts, store-settings,
    │                          # login, register …
    ├── widgets/               # central-sidebar, store-sidebar, header …
    ├── features/              # auth-login, tenant-register, cast-create, cast-edit,
    │                          # order-create, order-edit …
    ├── entities/              # cast, customer, order, tenant, user, menu, store-profile
    │                          #   └── 各含 model(类型)/api(请求)/ui(纯展示件)
    └── shared/                # api(axios 客户端, 拦截器), ui(通用组件), lib, config
```

- 迁移映射：`services/*` → `entities/*/api` + `shared/api`；`components/ui` → `shared/ui`；`components/layout`、`templates` → `widgets`；`contexts`、`hooks` → 按归属拆入 feature/entity/shared；`lib` → `shared/lib`（`proxy.ts` 留在 Next 约定位置）。
- 工具链：**Steiger** 进 `task lint`；tsconfig path alias `@/*` 对准 `src/`；API 类型仍按规范 snake_case。
- server-only 模块按官方建议提供 `index.server.ts` 出口。

## 四、PR 分批计划（D8）

> 状态（2026-07-03）：PR1-PR9 全部完成并开出 stacked PR（#202→#203→#204→#205→#206→#209→#210→#211→#212，按序合并）。

每个 PR 的验收线：`task lint` + `task test` 全绿（≥70% 覆盖率）、Modulith verify/Steiger 无违规、行为零变化（纯重构）。

| PR | 内容 |
|----|------|
| PR1 | 工具先行：后端加 `spring-modulith-starter-core` + `ModularityTests`（先仅对新包结构生效）；前端加 Steiger + alias + `src/` FSD 空骨架；CI 接入两者 |
| PR2 | 后端 `shared` 内核成形（TenantContext、BaseEntity、异常、横切配置归位） |
| PR3 | 后端 `tenant` 模块（含 TenantCreatedEvent 归位、事件升级 `@ApplicationModuleListener`） |
| PR4 | 后端 `auth` + `user` 模块（TenantUser→StoreUser 改名在此落地） |
| PR5 | 后端 `cast` / `customer` / `order` 模块（充血化 + OrderStatus 枚举 + ID 引用改造 + 读侧 projection） |
| PR6 | 后端 `menu` / `settings` / `storeprofile` / `notification` / `storage`（Menu 合并迁移若确认，Liquibase 变更走 `db/changelog/releases/<version>/` 约定） |
| PR7 | 前端 `shared` + `entities`（api 客户端与类型迁移） |
| PR8 | 前端 `features` / `widgets` / `_pages` / `app` 薄壳（可按 central、store 两批） |
| PR9 | 收尾：删除旧包/旧目录、`.claude/rules/backend.md`、`frontend.md` 重写为新结构、Jacoco 排除规则更新、Modulith 文档生成物入库 |

前后端（PR2-6 与 PR7-8）可并行推进。

### 配套更新（PR9 明细）

- **Jacoco 排除**：现排除 `config/model/repository/mapper/exception`。新结构下 `domain` 含业务行为**必须纳入覆盖**；排除面收窄为 `api/dto`、MapStruct 生成物、纯配置类。
- **规则文档**：`.claude/rules/*.md` 按新结构重写（模块模板、层依赖规则、FSD 层级说明、CONTEXT.md 指引）。
- **CONTEXT.md / ADR**：作为活文档随实施更新。

## 五、未决问题

1. **Order.storeName** 的真实含义（屋号/品牌/渠道？）——澄清后改名或移除（CONTEXT.md Open questions）。
2. ~~**Menu 合并**（单模型 + scope） 还是模块内双实体——PR6 前确认。~~ **已决（PR6/#197，2026-07-03）：模块内双实体**（CentralMenu + StoreMenu，零 schema/数据迁移，保持行为零变化与可回滚）。合并方案不被堵死，若后续确认可作为独立 PR 携带专门的数据迁移与回滚方案实施。
3. **对外 URL** `/tenant/*` 是否改 `/store/*`——影响用户书签/外链，默认不改，仅内部命名用 Store。
4. 各聚合的**值对象**提炼（如时间段、积分、地址）——PR5 实施时逐个评审，不预先建模。
