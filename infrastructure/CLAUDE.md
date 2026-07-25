# Infrastructure Conventions

- **Environment directories**: `infrastructure/development/` (HTTP only) and `infrastructure/release/` (HTTPS, Let's Encrypt, web→websecure redirect). Each holds its own `docker-compose.yml`, `Taskfile.yml`, and `traefik/`. Copy `infrastructure/.env.example` into each environment directory as `.env`. Switch with `task up env=release` (default is development).
- **`infrastructure/docker-compose.example.yml`** is the baseline compose template (same service structure as the per-environment files): apply structural service changes there first, then mirror them into `development/` and `release/`.
- **Traefik routing**: because `exposedByDefault: false`, a service to be exposed must carry the `traefik.enable=true` label (without it, it is silently not exposed). Paths are dispatched by `PathPrefix` (backend = `/api`, static = `/static`, frontend = everything else). If an app is unaware of its prefix, add a `stripPrefix` middleware (e.g. `backend-strip`).
- **DB / Redis**: referenced by the service names `database` / `cache`. Migrations are Liquibase (`backend/src/main/resources/db/changelog/`).
- **Network and host aliases**: the compose network `network` under project `kizuna` materializes as `kizuna_network`, which `e2e/docker-compose.e2e.yml` attaches to as `external: true`. In `development/` (and the example template) the `gateway` service carries the aliases `store1.kizuna.test` / `kizuna.test`; `release/` has none, since it is reached through real DNS. The frontend proxy (`frontend/src/proxy.ts` → `shared/lib/proxy/storeResolver.ts`) tells store from platform by Host, and e2e resolves those names through these aliases — renaming the project, the network, or the aliases breaks e2e silently.
- **Secrets**: `.env` must not be committed.

## Services (compose project `kizuna`)

| service | container | role |
|---|---|---|
| `database` | `database` | PostgreSQL 18 |
| `cache` | `redis` | Redis 8 |
| `backend` | `backend` | Spring Boot, port 8080 |
| `storage` | `storage` | MinIO, port 9000 |
| `frontend` | `frontend` | Next.js, port 3000 |
| `gateway` | `traefik` | Traefik 3 |

Service name and container name differ for `cache` and `gateway`: `task logs service=cache` takes the service name, raw `docker` commands take the container name.
