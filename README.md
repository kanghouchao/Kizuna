# Kizuna Platform — Platform CMS, CRM, & HRM for Multiple Stores (Spring Boot + Next.js)

![Docker](https://img.shields.io/badge/Docker-latest-blue.svg)
[![CodeQL](https://github.com/kanghouchao/Kizuna/actions/workflows/codeql.yml/badge.svg)](https://github.com/kanghouchao/Kizuna/actions/workflows/codeql.yml)
[![Dependabot](https://img.shields.io/badge/Dependabot-enabled-brightgreen.svg)](https://github.com/kanghouchao/Kizuna/security/dependabot)

Kizuna Platform is a modern platform system for running multiple stores under a single group, combining CMS, CRM, and HRM capabilities, built with a split architecture: Spring Boot backend and Next.js frontend, orchestrated with Docker compose.

## Highlights

- Multi-store by host name: one frontend, isolated store contexts
- Split architecture: Spring Boot API + Next.js app
- Comprehensive suite: CMS (Content), CRM (Customer Relationships), HRM (Human Resources)
- Stateless JWT auth; platform and store APIs split
- Responsive UI with Tailwind CSS
- Container-first: easy local dev and ops via Task + Docker Compose

## Architecture

Traefik routes all requests to the right service. The frontend and backend are fully decoupled and communicate over HTTP. All frontend API calls go through the reverse proxy under the `/api` prefix.

### Module Structure & Domain Separation

The application is strictly divided into two functional domains based on the user actor:

1.  **Platform Domain (`/platform`)** - _The Platform Headquarters_
    - **User:** Platform Admin / System Owner.
    - **Purpose:** Manage stores, system-wide settings, billing, and global analytics.
    - **Access:** Only accessible via the Admin Domain (e.g., `admin.kizuna.com`).

2.  **Store Domain (`/store`)** - _The Store Operations_
    - **User:** Store Managers, Store Staff, Casts.
    - **Purpose:** Day-to-day store operations (Orders, Cast management, Customer CRM).
    - **Access:** Accessible via Store Domains (e.g., `store1.kizuna.com`).
    - **Sub-modules:**
      - `/store/{storeId}/...`: The secured back-office area (requires login).
      - Public store site: served from the store domain root (`/`, `/casts`, `/schedule`, `/menu`, `/about`), rendered with the template selected in the store profile.

### Platform/store flow and cookies

- Frontend middleware decides the role based on the host name and resolves the store via backend
- Middleware sets cookies for server components to read:
  - `x-mw-role`: `platform | store`
  - `x-mw-store-template`: template key to load SSR store page
  - `x-mw-store-id`, `x-mw-store-name`, `x-mw-store-domain`: store meta
- In server components, read via `cookies()` (not raw `headers()`).

## Quick Start

### Prerequisites

- Docker & Docker Compose
- [Task](https://taskfile.dev) (go-task) — every build/test/lint command in this repo is driven through it

### Setup

1. Clone the repo

```bash
git clone https://github.com/kanghouchao/Kizuna.git
cd Kizuna
```

2. copy .env.example to .env and adjust if needed

```bash
cp infrastructure/.env.example infrastructure/development/.env
```

3. edit .env to set your preferred admin domain (e.g. `kizuna.com`)

4. Start services

```bash
task build up
```

5. Map local domains (for admin/store switching)

Add the following lines to `/etc/hosts` (example using the repo default):

```text
127.0.0.1 kizuna.test store1.kizuna.test store2.kizuna.test
```

6. Access

- Platform (admin UI): [kizuna.test](http://kizuna.test) (or your configured admin domain)
- Sample store (store UI): [store1.kizuna.test](http://store1.kizuna.test)

7. Default Credentials

Login is by email address. The accounts come from the seed changelogs under [`seed/`](./backend/src/main/resources/db/changelog/releases/v0.1.0/seed/).

- **HQ Admin:** `admin@kizuna.test` — all stores. Part of the baseline, so it is seeded in **every** environment including production.
- **Store Manager:** `tanaka.hanako@kizuna.test` — store1 + store2
- **Store Staff:** `yamada.jiro@kizuna.test` — store1

The two store accounts and the sample stores themselves are demo data: they are seeded only under `LIQUIBASE_CONTEXTS=demo`, which is already the default in `infrastructure/development/docker-compose.yml` (the application default is `production`, i.e. no demo data).

All accounts share the same default password `pass`

8. Login and have fun!

### Useful Task commands

- `task help` — list all commands
- `task build` or `task build service=frontend|backend` — build docker images for all or specified service
- `task up` — start the full stack (Traefik, DB, Redis, backend, frontend)
- `task down` — stop and remove containers
- `task clean` or `task clean service=frontend|backend` — remove the built docker images for all or the specified service. Database volumes are never touched.
- `task ps` — show running services
- `task logs` or `task logs service=frontend|backend|traefik|database` — follow service logs
- `task test` or `task test service=backend|frontend` — run tests
- `task lint` or `task lint service=frontend|backend` — run linters for all or specified service
- `task format` or `task format service=frontend|backend` — run code formatters (Spotless for backend, eslint fixes for frontend)

### Observability quick reference

- Backend Actuator exposes `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness`; the readiness probe includes database and Redis checks.
- Backend responses include an `X-Request-ID` header. Logs render `req=<id>` and `store=<value>` from the same correlation ID to make tracing requests across services easier.

## Project Structure

```text
Kizuna/
├── backend/                     # Backend Spring Boot API
├── frontend/                    # Frontend Next.js app
├── e2e/                         # Playwright BDD end-to-end suite
├── docs/                        # Design docs and ADRs
├── infrastructure/              # Docker Compose / Traefik config per environment
│   ├── .env.example             # Example env file
│   ├── development/             # docker-compose.yml + Traefik config (development)
│   └── release/                 # docker-compose.yml + Traefik config (release)
└── Taskfile.yml
```

## Troubleshooting

- If ports are busy, ensure nothing else is using 80, 443
- Confirm `/etc/hosts` entries resolve to your machine
- If you cannot log in, note that the seeded passwords are fixed at the **first** deployment. The hashes come from changelog parameters — `initialAdminPasswordHash` (env `INITIAL_ADMIN_PASSWORD_HASH`) for the HQ admin, `demoUserPasswordHash` (env `DEMO_USER_PASSWORD_HASH`) for the demo store users — and Liquibase checksums each changeset *after* the parameter is expanded. Set those env vars before the first `task up`; editing them against an already-migrated database makes the backend fail to start with a checksum validation error. Rotate passwords through the application instead. If you are already locked out of a local database, recreate the database itself (`DROP DATABASE kizuna` + `CREATE DATABASE kizuna`, then `task up`) — never by removing the Docker volume.

## Support

- Open an issue: [github.com/kanghouchao/Kizuna/issues](https://github.com/kanghouchao/Kizuna/issues)

## Contributing & AI Guidelines

- Contributing Guide: see `CONTRIBUTING.md`
- AI submission rules and PR checklist: see `.github/pull_request_template.md` and `CLAUDE.md`

---

Author: [kanghouchao](https://github.com/kanghouchao)
Repository: [github.com/kanghouchao/Kizuna](https://github.com/kanghouchao/Kizuna)
