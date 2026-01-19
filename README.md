# Kizuna Platform — Multi-tenant CMS, CRM, & HRM (Spring Boot + Next.js)

![Spring Boot](https://img.shields.io/badge/SpringBoot-3.5+-green.svg)
![Next.js](https://img.shields.io/badge/Next.js-14+-blue.svg)
![Java](https://img.shields.io/badge/Java-21+-blue.svg)
![TypeScript](https://img.shields.io/badge/TypeScript-5.0+-blue.svg)
![Docker](https://img.shields.io/badge/Docker-latest-blue.svg)
[![CodeQL](https://github.com/kanghouchao/MYCMS/actions/workflows/codeql.yml/badge.svg)](https://github.com/kanghouchao/MYCMS/actions/workflows/codeql.yml)
[![Dependabot](https://img.shields.io/badge/Dependabot-enabled-brightgreen.svg)](https://github.com/kanghouchao/MYCMS/security/dependabot)

Kizuna Platform is a modern, multi-tenant system combining CMS, CRM, and HRM capabilities, built with a split architecture: Spring Boot backend and Next.js frontend, orchestrated with Docker compose.

## Highlights

- Multi-tenant by host name: one frontend, isolated tenant contexts
- Split architecture: Spring Boot API + Next.js app
- Comprehensive suite: CMS (Content), CRM (Customer Relationships), HRM (Human Resources)
- Stateless JWT auth; admin (central) and tenant APIs split
- Responsive UI with Tailwind CSS
- Container-first: easy local dev and ops via Make + Docker compose

## Tech Stack

Below are the actual frameworks and key dependency versions used in this repository (extracted from `backend/build.gradle` and `frontend/package.json`). If you upgrade any of these in the project, please update this table accordingly.

| Area | Technology | Version / Notes |
|------|------------|-----------------|
| Backend framework | Spring Boot | 3.5.6 (see `backend/build.gradle`)
| Backend language | Java | 21 (sourceCompatibility in `backend/build.gradle`)
| Web / Security | Spring Web, Spring Security | `spring-boot-starter-web`, `spring-boot-starter-security`
| JWT library | JJWT | 0.13.0 (`io.jsonwebtoken`)
| Data / DB | Spring Data JPA, PostgreSQL driver | `org.postgresql:postgresql` (runtime)
| Cache | Spring Data Redis, Lettuce | `io.lettuce:lettuce-core` (runtime)
| Migrations | Liquibase | `org.liquibase:liquibase-core`
| Frontend framework | Next.js | ^14 (`frontend/package.json`)
| Frontend UI | React | ^18
| Frontend language | TypeScript | 5.4.5 (devDependency)
| Styling | Tailwind CSS | ^3.4.1
| HTTP client | axios | ^1.6.0
| Containers / Local orchestration | Docker, Docker Compose | see `docker-compose.yml`
| Reverse proxy | Traefik | configured under `environment/` (Traefik configs)
| Testing | JUnit/Jacoco (backend), Jest (frontend) | see `backend/build.gradle` and `frontend/package.json`

Note: references to other frameworks (e.g. Micronaut, Quarkus) were removed from the table — they are not used in this repository.


## Architecture

Traefik routes all requests to the right service. The frontend and backend are fully decoupled and communicate over HTTP. All frontend API calls go through the reverse proxy under the `/api` prefix.

### Module Structure & Domain Separation

The application is strictly divided into two functional domains based on the user actor:

1.  **Central Domain (`/central`)** - *The Platform Headquarters*
    *   **User:** Platform Admin / System Owner.
    *   **Purpose:** Manage tenants (stores), system-wide settings, billing, and global analytics.
    *   **Access:** Only accessible via the Admin Domain (e.g., `admin.kizuna.com`).

2.  **Tenant Domain (`/tenant`)** - *The Store Operations*
    *   **User:** Tenant Admin, Store Managers, Casts.
    *   **Purpose:** Day-to-day store operations (Orders, Cast management, Customer CRM).
    *   **Access:** Accessible via Tenant Domains (e.g., `store1.kizuna.com`).
    *   **Sub-modules:**
        *   `/tenant/dashboard`: The secured back-office area (requires login).
        *   `/tenant/site` (Future): Public landing pages for customers.

```text
                   ┌─────────────────┐
                   │   Frontend      │
   ┌──────────────►│   Next.js (3000)│
   │               └─────────────────┘
```

- Routing: `/api/*` → backend (prefix stripped before reaching Spring); everything else → frontend
- Backend API namespaces: `/central/*` (admin) and `/tenant/*` (tenant)
- Domain switch (frontend middleware): admin domains render the central app, others render the tenant app

### Multi-tenant flow and cookies

- Frontend middleware decides the role based on the host name and validates the tenant via backend
- Middleware sets cookies for server components to read:
  - `x-mw-role`: `central | tenant`
  - `x-mw-tenant-template`: template key to load SSR tenant page
  - `x-mw-tenant-id`, `x-mw-tenant-name`: tenant meta
- In server components, read via `cookies()` (not raw `headers()`).

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Make

### Setup

1. Clone the repo

```bash
git clone https://github.com/kanghouchao/MYCMS.git
cd MYCMS
```
2. copy .env.example to .env and adjust if needed

```bash
cp .env.example .env
```

2. edit .env to set your preferred admin domain (e.g. `cms.com`)

3. Start services

```bash
make build up
```

4. Map local domains (for admin/tenant switching)

Add the following lines to `/etc/hosts` (example using the repo default):

```text
127.0.0.1 my-cms.test
```

5. Access

- Central (admin UI): [my-cms.test](http://my-cms.test) (or your configured admin domain)

6. You can find password in [5.central-001-admin.yaml](./backend/src/main/resources/db/changelog/changes/5.central-001-admin.yaml)

7. Login and have fun!

### Useful Make targets

- `make help` — list all commands
- `make build` or `make build service=frontend|backend` — build docker images for all or specified service
- `make up` — start the full stack (Traefik, DB, Redis, backend, frontend)
- `make down` — stop and remove containers
- `make clean` or `make clean service=frontend|backend` — remove containers, volumes, and images for all or specified service
- `make ps` — show running services
- `make logs` or `make logs service=frontend|backend|traefik|database` — follow service logs
- `make test` or `make test service=backend|frontend` — run tests
 - `make lint` or `make lint service=frontend|backend` — run linters for all or specified service
 - `make format` or `make format service=frontend|backend` — run code formatters (Spotless for backend, eslint fixes for frontend)

### Observability quick reference

- Backend Actuator exposes `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness`; the readiness probe includes database and Redis checks.
- Backend responses include an `X-Request-ID` header. Logs render `req=<id>` and `tenant=<value>` from the same correlation ID to make tracing requests across services easier.

## Project Structure

```text
MYCMS/
├── backend/                     # Backend Spring Boot API
├── frontend/                    # Frontend Next.js app
├── environment/                 # Traefik / environment config
├── .env.example                 # Example env file
├── docker-compose.yml
└── Makefile
```

## Troubleshooting

- If ports are busy, ensure nothing else is using 80, 443
- Confirm `/etc/hosts` entries resolve to your machine
- if you cannot login, please create a new password hash via [bcrypt-generator.com](https://bcrypt-generator.com/) and update the password in [5.central-001-admin.yaml](./backend/src/main/resources/db/changelog/changes/5.central-001-admin.yaml), then run `make clean` and `make up` again.

## Support

- Open an issue: [github.com/kanghouchao/MYCMS/issues](https://github.com/kanghouchao/MYCMS/issues)

## Contributing & AI Guidelines

- Contributing Guide: see `CONTRIBUTING.md`
- AI submission rules and PR checklist: see `.github/pull_request_template.md` and `.github/copilot-instructions.md`

## Test reports

CI generates test and coverage reports for both frontend and backend and
publishes them via GitHub Pages. A consolidated index is available in the
repository under `reports/index.html`. The index links to the frontend
coverage (`reports/frontend/lcov-report/index.html`) and backend test and
jacoco output (`reports/backend/...`).

When the reports are published the CI also injects metadata into the index
page about which branch and pull request produced the report.

To view the latest reports, open the `reports/` folder in the GitHub Pages
deployment for this repository or visit the repository on GitHub:

- https://github.com/kanghouchao/MYCMS

If you need to update the links in `reports/index.html`, edit the local file
and open a PR so the CI can regenerate and republish the artifact.

---

Author: [kanghouchao](https://github.com/kanghouchao)
Repository: [github.com/kanghouchao/MYCMS](https://github.com/kanghouchao/MYCMS)
