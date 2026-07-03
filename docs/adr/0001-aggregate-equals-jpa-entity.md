# 聚合即 JPA 实体：写侧合一 + 读侧 projection

严格 DDD 重构中，我们决定**不**为领域模型另建一套持久化类：聚合根直接以 JPA 实体实现（充血模型、无公开 setter、不变量在行为方法内守护），列表/报表等部分字段查询走 Spring Data projection / DTO 查询，不经过聚合。完全分离（领域纯 POJO + JpaEntity + 双向 mapper）会手工重建 ORM 已有的能力（脏检查、变更追踪、延迟加载、级联），投入与收益不成比例——分离真正划算的场景（应用团队与 DB 团队独立演进的大组织）不是我们的情况。这与 Spring 社区主流实践一致（Spring Modulith 官方示例、Drotbohm、Khorikov）。

## Consequences

- 逃生门保留：若某个聚合的 JPA 映射确实扭曲了模型，允许该聚合个案分离，但必须在 ADR 中记录理由。
- 领域层对 JPA 有编译期依赖；如日后想消除注解噪音，可引入 jMolecules + ByteBuddy 插件，不必推翻本决策。
