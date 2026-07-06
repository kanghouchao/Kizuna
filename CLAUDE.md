# CLAUDE.md

## Project Overview

A multi-tenant CMS/CRM/HRM system for running the operations of multiple stores under a single group.

- Backend: Java 21, Spring Boot 3.5+, JUnit 5, Jacoco, Lombok, MapStruct
- Frontend: TypeScript 5.9, React 19, Next.js 16+, Jest, ESLint, Prettier
- Database: PostgreSQL 18+, Redis 8+, Docker volumes for persistence
- Traefik 3+ for reverse proxy and routing

Java is pinned to 21 by the repo-root `.java-version` (jenv). Builds, tests, and Spotless must run on JDK 21.

## Language Policy

- **AI-instruction docs** (this file and the per-directory `CLAUDE.md` files): **English**.
- **Human-facing docs** (`docs/**`, `README.md`), **code comments**, **GitHub issues/PRs**, and **commit messages**: **Japanese**.
- Code identifiers, module names, and shell commands stay verbatim regardless of the surrounding language.

## Build, Test & Verify

The system is built and tested with Docker Compose; all commands are driven through the `task` tool so local runs match CI/CD. Recommended workflow:

```bash
# Build
task build                          # all services
task build service=frontend         # frontend only
task build service=backend          # backend only

# Test (70% coverage required)
task test                           # all tests
task test service=frontend          # Jest only
task test service=backend           # JUnit + Jacoco only

# Lint & format
task lint                           # check
task format                         # auto-fix

# Local startup
task up                             # start full stack
task down                           # stop
task logs service=backend           # view logs
```

Use the Taskfile (Docker = CI parity) for final verification before committing. For fast red-green iteration use the local toolchains: `frontend/` → `npm test` / `npm run lint`; `backend/` → `./gradlew test` / `./gradlew spotlessApply`.

## Code Style & Conventions

Per-directory `CLAUDE.md` files carry the area conventions and are auto-loaded when working there:

- [Backend](backend/CLAUDE.md)
- [Frontend](frontend/CLAUDE.md) — plus the design system in [frontend/DESIGN.md](frontend/DESIGN.md) (read FIRST for any UI work)
- [Infrastructure](infrastructure/CLAUDE.md)

## Repository-wide guardrails

- **GitGuardian scans every commit**: even placeholder passwords written as literals in compose files or docs trigger alerts. Always write credentials as `${VAR:-default}`. `.env` is never committed.
- Judge build/test success by **exit code only** — output may be in Japanese locale (「エラー」), so never grep for "error".

## Do NOT introduce (unless explicitly requested)

- A second HTTP client on the frontend — `axios` is the established client.
- CSS-in-JS (styled-components / emotion) or component kits that conflict with Tailwind CSS + Headless UI.
- Global state libraries (Redux / MobX / Zustand) — none is in use; forms use react-hook-form.
- `logback` — log4j2 is the logging backend and logback is explicitly excluded in `backend/build.gradle`.
- ModelMapper / Dozer (MapStruct is the mapper), MyBatis (Spring Data JPA is the data layer), TestNG (JUnit 5 is the test framework).
