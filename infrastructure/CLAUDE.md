# Infrastructure Conventions

- **Environment directories**: `infrastructure/development/` (HTTP only) and `infrastructure/release/` (HTTPS, Let's Encrypt, web→websecure redirect). Each holds its own `docker-compose.yml`, `Taskfile.yml`, and `traefik/`. Copy `infrastructure/.env.example` into each environment directory as `.env`. Switch with `task up env=release` (default is development).
- **`infrastructure/docker-compose.example.yml`** is the baseline compose template (same service structure as the per-environment files): apply structural service changes there first, then mirror them into `development/` and `release/`.
- **Traefik routing**: because `exposedByDefault: false`, a service to be exposed must carry the `traefik.enable=true` label (without it, it is silently not exposed). Paths are dispatched by `PathPrefix` (backend = `/api`, static = `/static`, frontend = everything else). If an app is unaware of its prefix, add a `stripPrefix` middleware (e.g. `backend-strip`).
- **DB / Redis**: referenced by the service names `database` / `cache`. Migrations are Liquibase (`backend/src/main/resources/db/changelog/`).
- **Network and host aliases**: the compose network `network` under project `kizuna` materializes as `kizuna_network`, which `e2e/docker-compose.e2e.yml` attaches to as `external: true`. In `development/` (and the example template) the `gateway` service carries the aliases `store1.kizuna.test` / `kizuna.test`; `release/` has none, since it is reached through real DNS. The frontend proxy (`frontend/src/proxy.ts` → `shared/lib/proxy/storeResolver.ts`) tells store from platform by Host, and e2e resolves those names through these aliases. Renaming the project or the network makes e2e abort loudly (`network kizuna_network declared as external, but could not be found`); renaming an alias is the silent one — it degrades into a DNS failure inside the run.
- **Secrets**: `.env` must not be committed.

## Services (compose project `kizuna`)

Six services: `database`, `cache`, `backend`, `storage` (MinIO, serves `/static`), `frontend`, `gateway`. Images and ports live in the compose files — read them there rather than trusting a copy here. Two things the compose files do not make obvious:

- **Service name ≠ container name** for `cache` (container `redis`) and `gateway` (container `traefik`): `task logs service=cache` takes the service name, raw `docker` commands take the container name.
- **Only the gateway is published to the host** (`80`, plus `8080` for the Traefik dashboard), together with MinIO's console on `9001`. `backend` / `frontend` / `database` / `cache` have no host port — reach them through the gateway (`http://localhost/api/...`) or `task exec`. In particular `localhost:8080` is Traefik, not the backend.
