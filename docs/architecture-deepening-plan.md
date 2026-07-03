# 架构深化批次计划（Architecture Deepening Plan）

> 2026-07-04 架构评审（/improve-codebase-architecture）产出，与用户商定。
> 词汇约定沿用 codebase-design 术语：module / interface / implementation / depth / seam / adapter / leverage / locality。
> 前提约束：不违背 [ADR-0001](adr/0001-aggregate-equals-jpa-entity.md)（聚合=JPA 实体）、[ADR-0002](adr/0002-modules-by-domain-model-scopes-as-authorization.md)（模块按领域划分）、CONTEXT.md 2026-07 决定（CentralMenu/StoreMenu 不合并表）。

## 目标

把六个评审确认的浅 module / seam 泄漏点收拢为 deep module，提升 testability 与 AI 可导航性。**行为零变化**（除 PR-B 的隔离加固与 PR-C 的死搜索框修复）。

## 批次结构：三个 PR，顺序固定

```
PR-A（backend 结构收拢，机械优先）→ PR-B（租户隔离，独立评审）→ PR-C（前端小步）
```

C3 的 diff 横扫所有模块、与 C1/C4/C5 冲突，必须最先落；C2 动安全语义，独立成 PR 保证评审质量与可回滚性。

---

## PR-A：backend 结构收拢（C3 → C1 → C4 → C5，四个独立 commit）

### Commit 1（C3）：删除 Service interface 仪式

- **范围**：13 对 FooService/FooServiceImpl 中，10 对只有一个 adapter 且无任何测试以 interface 为 seam —— Impl 并入类名（`CustomerServiceImpl` → `CustomerService` 类）。
- **保留** interface 的两个（seam 是真的，有跨模块消费者 mock）：
  - `SystemConfigService`（← MaintenanceModeInterceptor、MailService）
  - `MailService`（← TenantCreatedListener）
  - `FileStorageService` 一并保留：命名（Local…Impl）预示远端存储第二 adapter，成本为零。
- **同步**：规范正本即本文档本节（application 层不再默认 interface+Impl；出现第二 adapter 或跨模块 mock 需求时再引入 interface）。本地 agent 规则（`.claude/rules/backend.md`，不入库）已同步同一表述。
- **验收**：全测试绿、ModularityTests 绿、无行为变化；删除 10 个 interface 文件。

### Commit 2（C1）：会话失效 deep module

- **范围**：auth/application 新增会话失效入口（命名候选 `AuthSessionService.invalidate(authHeader)`，实现内做 blacklist 写入 + `SecurityContextHolder.clearContext()`）；两侧 changePassword service 方法内部自行调用失效；4 处 controller 编排（logout×2 + changePassword×2）退化为单行委托。
- **约束**：blacklist 读侧（JwtAuthenticationFilter）与写侧共处 auth/infrastructure，KEY_PREFIX 不再跨文件包可见共享——判定逻辑也可考虑并入同一 module（`isBlacklisted(token)`）。
- **验收**：controller 不再 import SecurityContextHolder/TokenBlacklistService；现有 E2E（改密后旧 token 403、logout 后 403）不回归。

### Commit 3（C4）：菜单双胞胎收拢 + PERM_ 泄漏封堵

- **范围**：
  1. user 模块公开权限判定 interface（如 `Authorities.hasPermission(Authentication, String)` 静态 helper 或 @NamedInterface 导出），`"PERM_" + x` 编码知识收回 user 一处；
  2. CentralMenuServiceImpl / StoreMenuServiceImpl 字节级相同的 toVO / hasPermission / getUserPermissions 收成一份泛型实现，两侧作为薄 adapter。
- **不动**：实体与表（CONTEXT.md 2026-07 决定）。
- **验收**：`grep -r "PERM_" menu/` 为零；菜单树组装逻辑一份测试覆盖两侧。

### Commit 4（C5）：SMTP 配置契约类型化

- **范围**：settings 模块导出 `SmtpSettings` record（键名知识归 settings interface 所有），`smtpSettings()` 加入 SystemConfigService interface；MailServiceImpl 删除 5 处裸字符串键消费类型。原 `// ponytail:` 注记的真实成本是每次发信读 5 次 DB 配置——由 `smtpSettings()` 的缓存（配置更新时全量失效）解决；JavaMailSenderImpl 对象是无连接池的轻量配置容器（SMTP 连接本就按发信建立），每次发信重建即可，**不做 sender 对象缓存**。
- **验收**：`grep -rn "smtp_" notification/` 为零；SMTP 动态生效的既有行为不回归（#189 的 E2E 手法复验）。

**PR-A 门禁**：`task lint` + `task test`；改动后 `task build service=backend` + `task up` 后按 memory 的三件套头做一轮登录/菜单/发信冒烟。

---

## PR-B：租户隔离收拢到一个 seam（C2，单独 PR）

唯一有真实事故背书的候选（#216「忘了 @Filter」）。分两步，第一步无风险先行：

### Step 1（确定做）：机器检查扩面 + 漏网实体归位

- `TenantIsolationTests` 扫描依据从「TenantScopedEntity 子类」改为「**所有映射了 tenant_id 列的 @Entity**」（反射沿类层级找 `@Column(name="tenant_id")`，不再要求继承 TenantScopedEntity），断言各自声明 @Filter(tenantFilter)。
- `StoreMenu`（漏网：有 tenant_id 列却无 @Filter）补挂 `@Filter(tenantFilter)`，`StoreMenuService.getMyMenus()` 加 `@TenantScoped` 启用（保留既有 `findByTenantIdAndParentIsNullOrderBySortOrderAsc` 显式查询做双保险，二者同 StoreProfileService 先例）。
- **执行时发现并调整**：`StoreProfile` 未按原计划迁移为继承 TenantScopedEntity —— 其 `id` 是 `BIGINT autoIncrement`，TenantScopedEntity 的 `@Id` 固定为 `String`（`@SnowflakeId`），继承会强制改变主键类型；`StoreProfileResponse.id`（含前端 `StoreProfileResponse.id: number`）是公开 API 契约的一部分，此改动会破坏契约却对隔离安全无增益（StoreProfile 已有正确的 @Filter）。故保留 StoreProfile 现状（手抄字段），仅让机器检查覆盖到它；`StoreMenu` 因 id 已是 `VARCHAR(64)` 且其 DTO（MenuVO）不暴露 id/时间戳，才是真正零成本可动的一个，本节的「归位」只对 StoreMenu 生效。
- **验收**：机器检查覆盖 8/8 租户实体（Order/Cast/TenantRole/StoreUser/TenantPermission/Customer/StoreProfile/StoreMenu）；`task test` 全绿确认无回归。StoreMenu 无建数据 API（Liquibase 静态种子，仅 tenant_id=1），跨租户 curl 负向验证价值有限（既有显式 query 早已隔离，@Filter 是面向未来代码路径的双保险），改为 `task build` + `task up` 后对 `/tenant/menus/me` 做一次功能回归冒烟。

### Step 2（需 spike 验证可行性）：filter 启用从 24 处方法注解上移到请求 seam — **已放弃**

- 设想：TenantIdInterceptor 解析出租户后即对本请求的 Hibernate Session 启用 filter，@TenantScoped 仅保留给非请求上下文（事件监听、定时任务）作逃生门。
- **spike 结论**：`spring.jpa.open-in-view: false`，`preHandle` 阶段线程上尚无 Hibernate Session（Session 由 `@Transactional` 边界触发创建），此时启用 filter 无实际效果。备选的全局 `TransactionExecutionListener` 方案技术上可行，但成本（新机制引入 + 嵌套事务验证 + 测试风格改变）与收益（仅消除「忘记加 @TenantScoped」这一类从未真实发生过的事故；#216 的真实事故类型已由 Step 1 的实体级机器检查堵住）不成比例。详见 [ADR-0003](adr/0003-tenant-filter-enabling-stays-method-level.md)，Step 2 到此结束，不实现。

**PR-B 门禁**：全门禁 + 专门的跨租户隔离 E2E；因动安全语义，评审需独立进行。

---

## PR-C：前端小步（C6）

- `shared/lib` 新增 `getApiErrorMessage(error, fallback)`，删除三处漂移副本（PasswordChangeForm / central-settings SettingsPage / tenant-register RegisterForm 内联）。
- 取数生命周期 hook `useManagedList`（fetch/loading/toast/refetch），三个列表页接入；表格 markup **保留在各页**——明确不做配置驱动通用表格（interface 会和 implementation 一样宽，反 deepening）。
- 顺手修 OrdersPage 死搜索框（状态未接线）。
- **验收**：Jest 全绿 + 覆盖率阈值；三页浏览器冒烟。

---

## 明确不做（本批次）

- 配置驱动的通用表格组件（shallow module 风险）
- CentralMenu/StoreMenu 实体或表合并（CONTEXT.md 已决）
- 领域模型与 JPA 分离（ADR-0001 已决）
- Header 硬编码「管理者 / Central Admin」身份显示（真实身份显示依赖 /central/me、/tenant/me 的消费重构，规模超出本批次，单独立项）

## 遗留跟踪

- ~~C2 Step 2 的 spike 结论（采纳或 ADR 放弃）~~ → ADR-0003 放弃，已完成
- rules/backend.md 与本计划的同步在 PR-A Commit 1 内完成
