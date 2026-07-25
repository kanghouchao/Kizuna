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
- **Constructor injection**: dependencies come in via the constructor. A pure field-assignment constructor must be replaced by Lombok `@RequiredArgsConstructor`; hand-writing a constructor is allowed only when it performs real construction logic beyond assignment (e.g. building a configured `TransactionTemplate`).
- **Formatting**: Spotless + Google Java Format (google-java-format 1.35.0, JDK 25 support). JDK 25 is pinned by `backend/.java-version` (jenv) and `backend/gradle/gradle-daemon-jvm.properties` (daemon JVM), so `./gradlew spotlessApply` runs locally as-is. Fallback only if the active JDK is not 25: `docker run --rm -u root -v "$PWD":/app -w /app gradle:9.6.1-jdk25-ubi10 gradle spotlessApply --no-daemon`.
- **Coverage**: the only Jacoco exclusions are `**/api/dto/**` (DTOs + MapStruct-generated code) and `**/shared/config/**` (pure configuration). **The domain layer must always be covered.**
- **DB migrations (Liquibase)**: YAML under `db/changelog/releases/<version>/`. `v0.1.0` is a squashed single baseline creating every table in its final shape, split into `platform/` (platform-wide tables), `store/` (store-scoped tables), and `seed/`. New migrations go in a new `releases/<version>/` directory.
  - **Never edit an applied changeset** (checksum). A database predating the baseline cannot be migrated onto it — recreate the database instead (the Docker volume itself must survive, per the repo guardrails).
  - **Demo seed** (2 stores + their manager/staff) lives in `seed/05-demo.yaml` behind `contextFilter: demo`, selected at runtime by `spring.liquibase.contexts` (`LIQUIBASE_CONTEXTS`). **The application default is `production` (no demo data)** — dev/integration/e2e opt in by setting `demo` in their compose file, so a missing setting fails safe. Seeds take no explicit ids — FKs resolve via natural-key subselects, so IDENTITY sequences never collide with seeded rows.
  - **Password hashes come from changelog parameters**: the initial HQ admin's from `initialAdminPasswordHash` (`spring.liquibase.parameters`, env `INITIAL_ADMIN_PASSWORD_HASH`), demo users' from `demoUserPasswordHash`. **These set the first-deployment value only** — Liquibase checksums the changeset *after* parameter expansion, so changing either value once it has been applied fails startup with `Validation Failed: changesets check sum`. Rotate passwords through the application, never by editing these.
  - Changesets are baked into the backend image at build time: after adding one, `task up` alone does not apply it to the dev volume — run `task build service=backend` first.
- **Authentication is the Spring Security standard stack** (`docs/adr/0001-authentication-spring-security-standard-stack.md`): `AuthenticationManager` for login, oauth2-resource-server (`NimbusJwtDecoder`) for Bearer verification, and `JwtEncoder` (`NimbusJwtEncoder`, symmetric HS256) for issuance — see `auth/infrastructure/`. No hand-written JWT filter, no jjwt. Standard-stack failure paths return **401, not 403**. Revocation has two granularities in Redis: per session (`blacklist:tokens:`, TTL read from the token's own `exp`) and per account (`blacklist:users:`, TTL of one `app.jwt.expiration`, cleared when the user is re-enabled). Only the per-session TTL is immune to a runtime shortening of `app.jwt.expiration`; for the per-account key, shortening it can let a longer-lived token outlive the blacklist entry.
- **Config values**: read from `AppProperties` (shared/config). No hardcoding.
- **Logging**: keep the `req=<id> store=<id>` format.
- **Modulith docs**: `ModularityTests` generates them under `backend/docs/modulith/` (committed). The Documenter's Rel-line ordering is unstable, so unless there is a structural change, revert the diff with checkout.
- **Optional filter queries**: the JPQL `(:param is null or ...)` pattern can cause a runtime 500 due to PostgreSQL parameter type inference (see `CustomerService.searchSpec`). Build variable filter conditions with a `Specification` instead.
- **Build verification**: Gradle failure messages may be non-English depending on the local JVM locale. Do not judge success/failure with `grep error`; check the exit code or the presence of `BUILD FAILED`.
- **Manual API verification**: when hitting a store-scoped endpoint directly with curl, the `X-Role: store` and `X-Store-ID: <id>` headers are required (see `StoreIdInterceptor`). Without them the request is treated as having no store context — endpoints without `@StoreOptional` return 403.
- **Integration tests**: `integrationTest` runs against the compose-provided DB (`backend/docker-compose.test.yml`'s ephemeral stack), not Testcontainers.
