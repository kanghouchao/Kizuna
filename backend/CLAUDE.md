# Backend (Java) Conventions

- **Java version**: 25
- **Framework**: Spring Boot 4.1+, Spring Modulith, Spring Data JPA, Spring Security, Liquibase
- **Testing**: JUnit 5, Jacoco (LINE ≥ 70%)
- **Code generation**: Lombok, MapStruct
- **Database**: PostgreSQL 18+, Redis 8+

## Module structure (Spring Modulith)

```
com.kizuna
├── shared/          # Shared kernel (OPEN module): storescope, web, config, exception, persistence
├── store/  auth/  user/  cast/  customer/  order/
└── menu/  settings/  storeprofile/  shift/  notification/  storage/
```

Each module follows the DDD four layers:

```
<module>/
├── domain/          # Aggregate (JPA entity, rich model), value objects, enums, domain events, repository interfaces
├── application/     # Use-case services (transaction boundary), read-side queries
├── infrastructure/  # Additional adapters (interceptors, utilities, etc.)
└── api/
    ├── platform/    # Platform-side controllers (when needed)
    ├── store/       # Store-side controllers (when needed)
    └── dto/         # request/response + MapStruct mappers
```

### Layer / module rules

- **Aggregate = JPA entity** (rich model): no public setters. Already applied in the rich-model modules cast / customer / order. Modules not yet enriched (store / user / menu / settings / storeprofile) will adopt this incrementally; **do not add new public setters in new code** (the `StoreScopedEntity` base setters are also slated for gradual removal). Construction uses `@Builder` (consumed by MapStruct); partial updates use a domain-owned `XxxPatch` record + `apply()`; state transitions are behavior methods (e.g. `Order.confirm()/complete()/cancel()`, with invalid transitions raising a domain exception → 400).
- **Cross-aggregate references are by ID only** (`Order.customerId`, etc.). DB foreign keys are kept. Object assembly happens in the application layer; lists and details use projections (e.g. `OrderView` + JPQL join — reference entity names by FQCN to avoid HQL reserved-word collisions).
- **Synchronous references between modules** go only through packages exposed via `@NamedInterface` (document transitional exceptions in package-info.java). Events use `@ApplicationModuleListener` + the event publication registry (`event_publication` table, spring-modulith-starter-jdbc).
- **Application services are concrete classes by default** (FooService = class). An interface + Impl split is introduced only when a second adapter actually exists, or when a consumer in another module mocks it (current cases: SystemConfigService, FileStorageService). Do not introduce single-implementation interfaces.
- **Platform / Store are authorization scopes, not module boundaries** — resolved by the `api/platform` and `api/store` adapter layers, `StoreIdInterceptor`'s header-driven mount surface, and Spring Security.

## Code Conventions

- **Naming**: classes, methods, and variables are CamelCase. DB columns (snake_case) are mapped by JPA; API JSON keys (snake_case) are mapped by Jackson.
- **Store-side vocabulary uses the Store prefix**: StoreVO, StoreProfile
- **Imports**: no inline FQCN usage, no wildcard imports (`*`); one explicit import per class.
- **Formatting**: Spotless + Google Java Format (google-java-format 1.35.0, JDK 25 対応). JDK 25 is pinned by `backend/.java-version` (jenv) and `backend/gradle/gradle-daemon-jvm.properties` (daemon JVM), so `./gradlew spotlessApply` runs locally as-is. Fallback only if the active JDK is not 25: `docker run --rm -u root -v "$PWD":/app -w /app gradle:9.6.1-jdk25-ubi10 gradle spotlessApply --no-daemon`.
- **Coverage**: the only Jacoco exclusions are `**/api/dto/**` (DTOs + MapStruct-generated code) and `**/shared/config/**` (pure configuration). **The domain layer must always be covered.**
- **DB migrations**: Liquibase (YAML under `db/changelog/releases/<version>/`). `v0.1.0` is a squashed single baseline — it creates every table in its final shape, split into `platform/` (platform-wide tables), `store/` (store-scoped tables), and `seed/`. New migrations go in a new `releases/<version>/` directory; **never edit an applied changeset** (checksum). The v0.1.0 baseline was itself a one-time squash of the former v0.1.0–v0.17.0 chain, and its changeset ids do not match that history — a database created before the squash cannot be migrated onto it. Recreate the database instead (`DROP DATABASE kizuna` + `CREATE DATABASE`, which keeps the Docker volume the guardrails require to survive). Do not repeat this manoeuvre: from here on the rule above is absolute. Demo seed (2 stores + their manager/staff) lives in `seed/05-demo.yaml` behind `contextFilter: demo`, selected at runtime by `spring.liquibase.contexts` (`LIQUIBASE_CONTEXTS`). **The application default is `production` (no demo data)** — dev/integration/e2e opt in by setting `demo` in their compose file, so a missing setting fails safe. Seeds take no explicit ids — FKs resolve via natural-key subselects, so IDENTITY sequences never collide with seeded rows. The initial HQ admin's password hash comes from the changelog parameter `initialAdminPasswordHash` (`spring.liquibase.parameters`, env `INITIAL_ADMIN_PASSWORD_HASH`); demo users use `demoUserPasswordHash` the same way. **These set the first-deployment value only** — Liquibase checksums the changeset *after* parameter expansion, so changing either value once it has been applied fails startup with `Validation Failed: changesets check sum`. Rotate passwords through the application, never by editing these. Changesets are baked into the backend image at build time — after adding one, `task up` alone (without `task build service=backend` first) will not apply it to the dev volume.
- **Config values**: read from `AppProperties` (shared/config). No hardcoding.
- **Logging**: keep the `req=<id> store=<id>` format.
- **Modulith docs**: `ModularityTests` generates them under `backend/docs/modulith/` (committed). The Documenter's Rel-line ordering is unstable, so unless there is a structural change, revert the diff with checkout.
- **Optional filter queries**: the JPQL `(:param is null or ...)` pattern can cause a runtime 500 due to PostgreSQL parameter type inference (see `CustomerService.searchSpec`). Build variable filter conditions with a `Specification` instead.
- **Build verification**: Gradle failure messages may be non-English depending on the local JVM locale. Do not judge success/failure with `grep error`; check the exit code or the presence of `BUILD FAILED`.
- **Manual API verification**: when hitting a store-scoped endpoint directly with curl, the `X-Role: store` and `X-Store-ID: <id>` headers are required (see `StoreIdInterceptor`). Without them the request is treated as having no store context — endpoints without `@StoreOptional` return 403.
- **Integration tests**: `integrationTest` runs against the compose-provided DB (`backend/docker-compose.test.yml`'s ephemeral stack), not Testcontainers.
