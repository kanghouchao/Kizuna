# Kizuna Platform Context

## Project Overview
Kizuna Platform is a modern, multi-tenant system combining CMS, CRM, and HRM capabilities with a split architecture:
- **Backend:** Spring Boot (Java) handling API logic, data persistence, and multi-tenancy.
- **Frontend:** Next.js (TypeScript) providing the user interface, utilizing server-side rendering and static generation.
- **Infrastructure:** Docker Compose orchestrates the services, with Traefik acting as a reverse proxy/router.

## Architecture
- **Multi-tenancy:** Achieved via host-based routing.
    - **Central Domain (`/central`):** For platform administration (e.g., `admin.kizuna.com`).
    - **Tenant Domain (`/tenant`):** For individual store operations (e.g., `store1.kizuna.com`).
- **Communication:** Frontend talks to Backend via HTTP JSON APIs.
- **Routing:** Traefik routes requests based on path prefixes (`/api` to backend, others to frontend).

## Key Technologies & Versions
| Area | Technology | Version | Config File |
|------|------------|---------|-------------|
| **Backend** | Spring Boot | 3.5.9 | `backend/build.gradle` |
| | Java | 21 | `backend/build.gradle` |
| | Liquibase | Core | `backend/build.gradle` |
| **Frontend** | Next.js | 16.1.3 | `frontend/package.json` |
| | TypeScript | 5.9.3 | `frontend/package.json` |
| | Tailwind CSS | 4.1.18 | `frontend/package.json` |
| **Infra** | Docker | - | `docker-compose.yml` |

## Development Conventions

### Command Line Interface (Makefile)
The project uses a root `Makefile` to manage the entire lifecycle. **Always prefer these commands over direct `npm` or `gradle` calls.**

- **Build:** `make build` (Builds Docker images for both services)
    - `make build service=backend` / `make build service=frontend`
- **Start:** `make up` (Starts the full stack via Docker Compose)
- **Stop:** `make down` (Stops and removes containers)
- **Logs:** `make logs` (Follows logs for all services)
    - `make logs service=backend`
- **Test:** `make test` (Runs tests for both services)
    - `make test service=backend` (JUnit/Jacoco)
    - `make test service=frontend` (Jest)
- **Lint:** `make lint` (Runs linters)
    - `make lint service=backend` (Spotless check)
    - `make lint service=frontend` (ESLint)
- **Format:** `make format` (Applies code formatting)
    - `make format service=backend` (Spotless apply - Google Java Format)
    - `make format service=frontend` (Prettier)

### Directory Structure
- `backend/`: Spring Boot application source.
    - `src/main/java`: Java source code.
    - `src/main/resources`: Config (`application.yml`) and DB migrations (`db/changelog`).
- `frontend/`: Next.js application source.
    - `src/app`: App Router pages and layouts.
    - `src/components`: Reusable UI components.
    - `src/lib`: Utilities and API clients.
- `environment/`: Infrastructure configuration.
    - `development/`: Docker Compose and Traefik config for local dev.

### Coding Standards
- **Backend:**
    - Follows standard Spring Boot layer structure (Controller -> Service -> Repository).
    - Uses Lombok for boilerplate reduction.
    - Enforces Google Java Format via Spotless.
- **Frontend:**
    - Uses Next.js App Router structure.
    - Uses Tailwind CSS for styling.
    - Enforces Prettier and ESLint.

## Quick Start for AI Agent
1.  **Check Context:** Read `backend/build.gradle` or `frontend/package.json` if you need to confirm specific dependencies.
2.  **Run Tests:** Use `make test service=<service>` to verify changes.
3.  **Format Code:** Always run `make format` before finalizing changes to ensure CI passes.
4.  **Restart:** If you modify backend config or dependencies, run `make restart service=backend`.
