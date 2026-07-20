# Kizuna Platform — Platform CMS, CRM, & HRM for Multiple Stores (Spring Boot + Next.js)

![Spring Boot](https://img.shields.io/badge/SpringBoot-3.5+-green.svg)
![Next.js](https://img.shields.io/badge/Next.js-14+-blue.svg)
![Java](https://img.shields.io/badge/Java-21+-blue.svg)
![TypeScript](https://img.shields.io/badge/TypeScript-5.0+-blue.svg)
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
- Container-first: easy local dev and ops via Make + Docker compose

## Tech Stack

Below are the actual frameworks and key dependency versions used in this repository (extracted from `backend/build.gradle` and `frontend/package.json`). If you upgrade any of these in the project, please update this table accordingly.

| Area                             | Technology                              | Version / Notes                                           |
| -------------------------------- | --------------------------------------- | --------------------------------------------------------- |
| Backend framework                | Spring Boot                             | 3.5.6 (see `backend/build.gradle`)                        |
| Backend language                 | Java                                    | 21 (sourceCompatibility in `backend/build.gradle`)        |
| Web / Security                   | Spring Web, Spring Security             | `spring-boot-starter-web`, `spring-boot-starter-security` |
| JWT library                      | JJWT                                    | 0.13.0 (`io.jsonwebtoken`)                                |
| Data / DB                        | Spring Data JPA, PostgreSQL driver      | `org.postgresql:postgresql` (runtime)                     |
| Cache                            | Spring Data Redis, Lettuce              | `io.lettuce:lettuce-core` (runtime)                       |
| Migrations                       | Liquibase                               | `org.liquibase:liquibase-core`                            |
| Frontend framework               | Next.js                                 | ^14 (`frontend/package.json`)                             |
| Frontend UI                      | React                                   | ^18                                                       |
| Frontend language                | TypeScript                              | 5.4.5 (devDependency)                                     |
| Styling                          | Tailwind CSS                            | ^3.4.1                                                    |
| HTTP client                      | axios                                   | ^1.6.0                                                    |
| Containers / Local orchestration | Docker, Docker Compose                  | see `docker-compose.yml`                                  |
| Reverse proxy                    | Traefik                                 | configured under `infrastructure/` (Traefik configs)      |
| Testing                          | JUnit/Jacoco (backend), Jest (frontend) | see `backend/build.gradle` and `frontend/package.json`    |

Note: references to other frameworks (e.g. Micronaut, Quarkus) were removed from the table — they are not used in this repository.

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
      - `/store/dashboard`: The secured back-office area (requires login).
      - `/store/site` (Future): Public landing pages for customers.

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
- Make

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

2. edit .env to set your preferred admin domain (e.g. `kizuna.com`)

3. Start services

```bash
task build up
```

4. Map local domains (for admin/store switching)

Add the following lines to `/etc/hosts` (example using the repo default):

```text
127.0.0.1 kizuna.test store1.kizuna.test
```

5. Access

- Platform (admin UI): [kizuna.test](http://kizuna.test) (or your configured admin domain)
- Sample Tenant (store UI): [store1.kizuna.test](http://store1.kizuna.test)

6. Default Credentials

You can find the initial data setup in [05-initial-data.yaml](./backend/src/main/resources/db/changelog/releases/v0.1.0/central/05-initial-data.yaml).

- **Platform Admin:** `admin` / default password (see note below)
- **Sample Tenant Admin:** `admin@store1.kizuna.com` / default password (see note below)

Both accounts share the same default password `pass`

7. Login and have fun!

### Useful Make targets

- `task help` — list all commands
- `task build` or `task build service=frontend|backend` — build docker images for all or specified service
- `task up` — start the full stack (Traefik, DB, Redis, backend, frontend)
- `task down` — stop and remove containers
- `task clean` or `task clean service=frontend|backend` — remove containers, volumes, and images for all or specified service
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
├── infrastructure/              # Docker Compose / Traefik config per environment
│   ├── .env.example             # Example env file
│   ├── development/             # docker-compose.yml + Traefik config (development)
│   └── release/                 # docker-compose.yml + Traefik config (release)
└── Taskfile.yml
```

## Troubleshooting

- If ports are busy, ensure nothing else is using 80, 443
- Confirm `/etc/hosts` entries resolve to your machine
- If you cannot log in, generate a new bcrypt password hash locally using trusted tooling (e.g. `htpasswd` or Python's `bcrypt`), update the password in [05-initial-data.yaml](./backend/src/main/resources/db/changelog/releases/v0.1.0/central/05-initial-data.yaml), then run `task clean` and `task up` again.

## Support

- Open an issue: [github.com/kanghouchao/Kizuna/issues](https://github.com/kanghouchao/Kizuna/issues)

## Contributing & AI Guidelines

- Contributing Guide: see `CONTRIBUTING.md`
- AI submission rules and PR checklist: see `.github/pull_request_template.md` and `.github/copilot-instructions.md`

---

Author: [kanghouchao](https://github.com/kanghouchao)
Repository: [github.com/kanghouchao/Kizuna](https://github.com/kanghouchao/Kizuna)
