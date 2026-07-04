---
paths:
  - "infrastructure/**"
---

# Infrastructure Conventions

- **Environment directories**: `infrastructure/development/` (HTTP only) and `infrastructure/release/` (HTTPS, Let's Encrypt, web→websecure redirect). Copy `infrastructure/.env.example` into each environment directory as `.env`. Switch with `task up env=release` (default is development).
- **Traefik routing**: because `exposedByDefault: false`, a service to be exposed must carry the `traefik.enable=true` label (without it, it is silently not exposed). Paths are dispatched by `PathPrefix` (backend = `/api`, static = `/static`, frontend = everything else). If an app is unaware of its prefix, add a `stripPrefix` middleware (e.g. `backend-strip`).
- **DB / Redis**: referenced by the service names `database` / `cache`. Migrations are Liquibase (`backend/src/main/resources/db/changelog/`).
- **Secrets**: `.env` must not be committed.
