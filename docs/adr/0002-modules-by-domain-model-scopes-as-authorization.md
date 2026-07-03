# 模块按领域模型划分，Central/Store 是授权作用域而非模块边界

后端模块（顶层包）按领域模型划分（tenant、cast、customer、order、user、menu、settings、storeprofile…），而**不是**按 central/tenant 切成两个大边界。Central 与 Store 是同一系统的两个访问作用域：同一个领域模型由（必要时）多个接口适配层暴露（`api/central`、`api/store`），权限差异在接口层和 Spring Security 解决，领域层和基础设施共用。依据：两侧共用单库（租户数据靠 `tenant_id` + Hibernate `@Filter` 隔离），领域语言大量重叠（Menu 两侧字段完全同构），按 scope 切边界会导致真同构概念被迫重复。

## Consequences

- 跨聚合（含跨模块）一律 ID 引用，禁止 JPA 对象引用；模块边界由 Spring Modulith `ApplicationModules.verify()` 在 CI 强制。
- 例外记录：SystemConfig 与 StoreProfile（原 TenantConfig）是**假同名**——字段零重叠的两个概念，各自成模块，不因名字相似而合并。
