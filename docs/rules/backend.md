# Backend (Java) Conventions

- **Java version**: 21
- **Framework**: Spring Boot 3.5+, Spring Modulith, Spring Data JPA, Spring Security, Liquibase
- **Testing**: JUnit 5, Jacoco（LINE ≥70%）
- **Code generation**: Lombok, MapStruct
- **Database**: PostgreSQL 18+, Redis 8+

## Module structure (Spring Modulith)

トップレベルパッケージ = モジュール。境界は `ModularityTests`（`ApplicationModules.verify()`）が CI で機械検証する。用語は [CONTEXT.md](../../CONTEXT.md)、決定の根拠は [ADR-0001/0002](../adr/) と [docs/ddd-fsd-refactor-plan.md](../ddd-fsd-refactor-plan.md)。

```
com.kizuna
├── shared/          # 共有カーネル（OPEN モジュール）: tenancy, web, config, exception, persistence
├── tenant/  auth/  user/  cast/  customer/  order/
└── menu/  settings/  storeprofile/  notification/  storage/
```

各モジュール内は DDD 四層:

```
<module>/
├── domain/          # 集約(JPA エンティティ・充血)、値オブジェクト、enum、ドメインイベント、Repository IF
├── application/     # ユースケースサービス（トランザクション境界）、読み側クエリ
├── infrastructure/  # 追加アダプタ（インターセプタ、ユーティリティ等）
└── api/
    ├── central/     # central 側 controller（必要な場合）
    ├── store/       # store 側 controller（必要な場合）
    └── dto/         # request/response + MapStruct マッパー
```

### Layer / module rules

- **集約 = JPA エンティティ**（充血モデル）: 公開 setter 禁止。充血化済みの cast / customer / order では適用済み。未充血化の集約（tenant / user / menu / settings / storeprofile）は今後の充血化で段階的に適用し、**新規コードで公開 setter を増やさない**（`TenantScopedEntity` 基底の setter も段階的廃止予定）。生成は `@Builder`（MapStruct が使用）、部分更新はドメイン所有の `XxxPatch` record + `apply()`、状態遷移は行動メソッド（例: `Order.confirm()/complete()/cancel()`、不正遷移はドメイン例外 → 400）
- **跨集約は ID 参照のみ**（`Order.customerId` 等）。DB の FK は保持。オブジェクト組立は application 層、一覧・詳細は projection（例: `OrderView` + JPQL join。エンティティ名は HQL 予約語衝突を避け FQCN で参照）
- **モジュール間の同期参照**は `@NamedInterface` で公開された package のみ（過渡措置は package-info.java に理由を明記）。イベントは `@ApplicationModuleListener` + イベント発行レジストリ（`event_publication` 表、spring-modulith-starter-jdbc）
- **Central / Store はモジュール境界ではなく認可スコープ**（api/central・api/store の適配層 + Spring Security で解決、ADR-0002）

## Code Conventions

- **Naming**: クラス・メソッド・変数は CamelCase。DB カラム（snake_case）は JPA が、API JSON キー（snake_case）は Jackson が変換
- **Store 側語彙は Store 接頭辞**: StoreUser、StoreProfile、StoreMenu（旧 Tenant* は使用しない、CONTEXT.md 参照）
- **Import**: FQCN のインライン使用禁止、ワイルドカード import（`*`）禁止、クラスごとに個別 import
- **Formatting**: Spotless + Google Java Format（ローカル JDK が 21 以外なら Docker で: `docker run --rm -u root -v "$PWD":/app -w /app gradle:9.2.1-jdk21-ubi-minimal gradle spotlessApply --no-daemon`）
- **Coverage**: Jacoco 除外は `**/api/dto/**`（DTO + MapStruct 生成物）と `**/shared/config/**`（純設定）のみ。**domain 層は必ずカバレッジ対象**
- **DB migrations**: Liquibase（`db/changelog/releases/<version>/central|tenant/` 配下の YAML）
- **Config values**: `AppProperties`（shared/config）から取得。ハードコード禁止
- **Logging**: `req=<id> tenant=<id>` 形式を維持
- **Modulith docs**: `ModularityTests` が `backend/docs/modulith/` に自動生成（コミット対象）
