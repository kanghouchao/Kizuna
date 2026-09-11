# Backend (Java) Conventions

## Module structure (Spring Modulith)

Each module under `com.kizuna` follows the DDD four layers — `domain/` / `application/` (use-case services = the transaction boundary) / `infrastructure/` / `api/` (`dto/` + MapStruct mappers, plus `platform/` and `store/` controllers **each only when that scope is actually exposed**: a single-scope module is normal, not incomplete); `shared/` is the OPEN shared kernel.

### Layer / module rules

- **Aggregate = JPA entity** (rich model): no public setters on the fields an aggregate declares. Already applied in the rich-model modules cast / customer / order / member / shift / user. Modules not yet enriched (store / menu / settings / storeprofile) will adopt this incrementally; **do not add new public setters in new code** (the base-class setters inherited from `BaseEntity` — id / timestamps — and `StoreScopedEntity` are a known exception, slated for gradual removal). Construction uses `@Builder` (consumed by MapStruct); partial updates use a domain-owned `XxxPatch` record + `apply()`; state transitions are behavior methods (e.g. `Order.complete()/cancel()`, with invalid transitions raising a domain exception → 400).
- **Cross-aggregate references are by ID only** (`Order.customerId`, etc.). DB foreign keys are kept. Object assembly happens in the application layer; lists and details use projections (e.g. `OrderView` + JPQL join — reference entity names by FQCN to avoid HQL reserved-word collisions).
- **Synchronous references between modules** go only through packages exposed via `@NamedInterface` (document transitional exceptions in package-info.java). Events use `@ApplicationModuleListener` + the event publication registry (`event_publication` table, spring-modulith-starter-jdbc).
- **Application services are concrete classes by default** (FooService = class). An interface + Impl split is introduced only when a second adapter actually exists, or when a consumer in another module mocks it (current cases: SystemConfigService, FileStorageService). Do not introduce single-implementation interfaces.
- **Platform / Store are authorization scopes, not module boundaries** — resolved by the `api/platform` and `api/store` adapter layers, `StoreIdInterceptor`'s header-driven mount surface, and Spring Security.
- **Store isolation is a Hibernate filter switched on per method**: `@StoreScoped` on an application-service method makes the `StoreFilterEnable` aspect enable `storeFilter` on the current Session for the store in `StoreContext` (set by `StoreIdInterceptor` on `/store/**`; on `/platform/**` wrap the call in `StoreScopeExecutor.runInStore`). It silently turns off in two ways: the aspect must run inside the transaction (OSIV is off — see the `@Order` note on `StoreFilterEnable`), and a self-invocation of a `@StoreScoped` method bypasses the proxy. Every `StoreScopedEntity` declares its filters statically (ADR 0002), pinned by `StoreIsolationTests`.

## Code Conventions

- **Naming**: classes, methods, and variables are CamelCase. DB columns (snake_case) are mapped by JPA; API JSON keys (snake_case) are mapped by Jackson.
- **Store-side vocabulary uses the Store prefix**: StoreVO, StoreProfile
- **Adding a `PermissionCode`** is more than the enum: bump the literal count assertion in `PermissionCodeTest`, add the code to the hand-maintained union in `frontend/src/entities/user/model/types.ts` (no parity test exists), and let `reconcile/` seed the `t_permissions` row — never hand-seed it.
- **Imports**: no inline FQCN usage, no wildcard imports (`*`); one explicit import per class.
- **Constructor injection**: dependencies come in via the constructor. A pure field-assignment constructor must be replaced by Lombok `@RequiredArgsConstructor`; hand-writing a constructor is allowed only when it performs real construction logic beyond assignment (e.g. building a configured `TransactionTemplate`).
- **Formatting**: Spotless + Google Java Format on JDK 25. The version and daemon JVM are pinned in the build configuration; use the Taskfile for final verification.
- **Coverage**: the only Jacoco exclusions are `**/api/dto/**` (DTOs + MapStruct-generated code), `**/shared/config/**` (pure configuration), and `**/Application.class`. **The domain layer must always be covered.**
- **DB migrations**: read [database rules](src/main/resources/db/AGENTS.md) before touching changelogs. The pre-launch baseline is edited in place; that document owns checksum handling, seed rules, FK constraints, and dev-DB recreation. Never remove database volumes.
- **Authentication**: Spring Security AuthenticationManager, NimbusJwtDecoder and NimbusJwtEncoder (HS256); see [ADR 0001](../docs/adr/0001-authentication-spring-security-standard-stack.md). APP_JWT_SECRET has no default and must be at least 32 bytes (startup validation). Revocation uses token-specific logout and account credential-version equality; see [ADR 0022](../docs/adr/0022-session-invalidation-by-credential-version.md). DB is authoritative, Redis caches monotonically, and revocation cache changes must follow transaction commit. Re-enabling an account must not revive old sessions.
- **Config values**: read from `AppProperties` (shared/config). No hardcoding.
- **Logging**: keep the `req=<id> store=<id>` format.
- **Modulith docs**: `ModularityTests` generates them under `backend/docs/modulith/` (committed). The Documenter's Rel-line ordering is unstable, so unless there is a structural change, revert the diff with checkout.
- **Optional filter queries**: the JPQL `(:param is null or ...)` pattern can cause a runtime 500 due to PostgreSQL parameter type inference (see `CustomerService.searchSpec`). Build variable filter conditions with a `Specification` instead.
- **Manual API verification**: when hitting a store-scoped endpoint directly with curl, the `X-Role: store` and `X-Store-ID: <id>` headers are required (see `StoreIdInterceptor`). Without them the request is treated as having no store context — endpoints without `@StoreOptional` return 403.
- **Integration tests**: `integrationTest` runs against the compose-provided DB (`backend/docker-compose.test.yml`'s ephemeral stack), not Testcontainers.

## API contract

This section is the normative source for all HTTP endpoint additions and changes. Before implementing a task spanning frontend and backend, present the paths/methods, success and principal failure codes, request/response fields (types and optionality), authorization, and pagination for approval. Prefer safety, then consistency with the established contract, then textbook REST style. Document code deviations separately; do not copy them or mix unrelated endpoint repairs into the change.

### Resources and responses

- Use plural kebab-case nouns; a genuinely singleton child may be singular. Domain actions use noun subresources (`POST /store/orders/{id}/completion`), not bare verbs. Irreversible transitions need their own authorization, reason, and explicit rejection of a repeated transition (ADR 0013). Do not replace domain operations with generic CRUD.
- Literal path segments may coexist with `{id}`, but reserve those values permanently. `/platform/me/...` owns reads derivable from the authenticated principal.
- `GET` is side-effect-free; `POST` creates resources or performs actions; `PUT` replaces one resource at its own URI, never at a collection root; `PATCH` partially updates; `DELETE` deletes.
- A read accepting a secret token uses `POST` with a body, such as invitation `/view`. Never move the token to a path or query, where logs/history/Referer can expose it. Wire anonymous CSRF handling at the same time.
- Creation returns **201** with the resource id; no `Location` header is required. Deletion returns **204** without a body. Actions return **200** with a result, **201** if creating a resource, or **204** when the consumer only removes/refetches data.
- Authentication failure is **401**, permission denial **403**, invalid input/transition **400**, absent or scope-invisible resource **404**, and conflicts **409** through the existing exception mapping. Do not independently change a family's denial semantics to hide enumeration.
- `CommonExceptionHandler` owns `{ "error": "…", "details": { "field_name": "…" } }`; `details` is optional. Messages must be authored user-facing copy. Never forward framework/DB exception text, SQL, constraint names, or stack traces. Unhandled errors use a fixed 500 message. Use the established `ServiceException`, `ConflictException`, and `NotFoundException` families where applicable. Bearer authentication failures are handled by `PlatformAuthenticationEntryPoint`; login failures also use the exception handler.
- JSON field names and validation detail paths are snake_case. User-defined Map keys are data and must not be renamed by that strategy.
- New body DTOs use `XxxRequest` / `XxxResponse`. Lists use separate Summary DTOs; details carry the heavier fields. Secret values and unnecessary sensitive PII must be absent from the list DTO **type**, not hidden by mapper ignores, nulls, or serialization rules.

### Pagination

- Use Spring `Page` for a numbered list with a total count. Sort by a total order ending in a unique key.
- Use `CursorPage` for queues, infinite scrolling, and growing history without counts. Apply `MAX_SIZE`; the cursor comparison must use the same ordered column tuple, including its unique tie-breaker.
- A bare `List` is only for a small, explainably bounded collection. Growing history is not bounded. State the bound when introducing a list endpoint.

### Authorization and anonymous endpoints

`SecurityConfig` uses `anyRequest().permitAll()`: method annotations are the authorization boundary. Every handler must be **public, non-final, non-static**, with `@PreAuthorize` or `@PermitAll`; proxy-ineligible methods silently bypass method security. `EndpointAuthorizationDeclarationTests` enforces this without exemptions. Permission authorities must resolve to `PermissionCode` (`PermissionLiteralTests`).

For each anonymous endpoint, check all four concerns:

1. Declare `@PermitAll` on the handler.
2. For anonymous writes, add the **actual method plus path** to `SecurityConfig.CSRF_IGNORED_MATCHERS`. A path-only matcher also exempts authenticated sibling methods. Bearer-header requests already have their own CSRF exemption.
3. If stale Bearer credentials must be ignored, add the **method plus path** to `PlatformBearerTokenResolver.BEARER_EXEMPT_MATCHERS`. Do not exempt optional-authentication handlers that inspect `Principal`: doing so discards valid credentials too. Test anonymous access with a broken Bearer, not just without credentials.
4. `/store/**` and `/files/**` still need `X-Role: store` and `X-Store-ID` through `StoreIdInterceptor`. Only handlers intentionally independent of store context declare `@StoreOptional`.

Store reads use `@StoreScoped` row isolation. A handwritten `where store_id = ?` is not a substitute. If a path must bypass the mechanism, document and test its explicit ownership control. The layer rules above describe transaction/proxy requirements.

### Existing deviations

Do not replicate or opportunistically rename `/store/config` versus `/platform/configs`, `/platform/staff`, `StoreVO` / `StoreStatusVO` / `MenuVO`, `Token`, or `StoreCreateDTO` / `StoreUpdateDTO`. Existing path-only anonymous matchers do not relax the method-plus-path rule; their repair requires checking sibling methods and optional authentication together.
