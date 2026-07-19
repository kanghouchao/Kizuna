# CLAUDE.md

## Project Overview

A multi-tenant CMS/CRM/HRM system for running the operations of multiple stores under a single group.

- Backend: Java 21, Spring Boot 3.5+, JUnit 5, Jacoco, Lombok, MapStruct
- Frontend: TypeScript 5.9, React 19, Next.js 16+, Jest, ESLint, Prettier
- Database: PostgreSQL 18+, Redis 8+, Docker volumes for persistence
- Traefik 3+ for reverse proxy and routing

Java is pinned to 21 by `backend/.java-version` (jenv, effective under `backend/`) and `backend/gradle/gradle-daemon-jvm.properties` (Gradle daemon). Builds, tests, and Spotless must run on JDK 21.

## Domain Glossary

- **Central** = group HQ scope; **Store** = per-shop tenant scope. Store-side vocabulary uses the Store prefix (StoreUser, StoreProfile).
- The customer-visit aggregate is **Order** — never Reservation or Booking.
- **CentralMenu and StoreMenu were unified into a single platform Menu aggregate** (decided 2026-07-18, #404 decision 2).
- **StoreProfile** = store-facing display settings; **SystemConfig** = central-side system settings. Do not mix.

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

# Test (70% coverage required — coverage is measured on unit tests only)
task test                           # unit + integration (backward-compatible full run)
task test-unit                      # frontend Jest + backend unit + coverage gate (the PR gate)
task test-integration               # backend integration only
task test service=frontend          # Jest only
task test service=backend           # JUnit + Jacoco + integration
task e2e                            # Playwright BDD e2e — PR author's local responsibility, not run in CI

# Lint & format
task lint                           # check
task format                         # auto-fix

# Local startup
task up                             # start full stack (does NOT rebuild images — run task build first to pick up code changes)
task down                           # stop
task logs service=backend           # view logs
```

Use the Taskfile (Docker = CI parity) for final verification before committing. For fast red-green iteration use the local toolchains: `frontend/` → `npm test` / `npm run lint`; `backend/` → `./gradlew test` / `./gradlew spotlessApply`.

`task build` also runs as a PR gate inside each side's `Lint and Test (frontend)` / `Lint and Test (backend)` job (`.github/workflows/lint-and-test.yml`): a production build failure turns that check red, so a change that breaks the production build cannot pass CI.

CI is tiered (issue #241) and parallelized by side (#346). The PR gate is three required checks — **Lint and Test (frontend)**, **Lint and Test (backend)**, **Repo Lint** — each running lint + unit(coverage) + build for its own side (`task -d frontend|backend lint` / `test` or `test-unit` / `build`) in parallel jobs. **Integration and E2E do not run in CI at all**: they are the PR author's local responsibility — run `task test` (unit + integration) and `task e2e` locally before opening a PR, as the PR template's 検証 section requires. Code review is local-only: every PR requires a pre-push local review (dev-loop Stage 5 — standing form: the two-axis `/code-review` skill (Standards + Spec); fallback: the committed `kizuna-reviewer` agent, `.claude/agents/kizuna-reviewer.md`), enforced by check-pr.sh's ローカル code-review 実施 line — there is no CI-side automated review job, and no Claude-triggered GitHub Action remains.

## Code Style & Conventions

Per-directory `CLAUDE.md` files carry the area conventions and are auto-loaded when working there:

- [Backend](backend/CLAUDE.md)
- [Frontend](frontend/CLAUDE.md) — plus the design system in [frontend/DESIGN.md](frontend/DESIGN.md) (read FIRST for any UI work)
- [Infrastructure](infrastructure/CLAUDE.md)

## Repository-wide guardrails

Forbidden operations (enforced locally via `.claude/settings.json` deny rules + hooks; they are policy even where enforcement is absent):

- **Force push in any form** (`--force`, `-f`, `--force-with-lease`) — history rewrites go through a replacement PR.
- **Merging PRs** (`gh pr merge`, auto-merge) — the repository owner merges every PR by hand.
- **Destructive git**: `git reset --hard`, `git clean`, `git branch -D`, `git commit --no-verify`.
- **Docker data wipes**: `docker volume rm`, `docker system prune`, `compose down -v` — dev DB volumes must survive.
- **GitGuardian scans every commit**: even placeholder passwords written as literals in compose files or docs trigger alerts. Always write credentials as `${VAR:-default}`. `.env` is never committed or read.

Judge build/test success by **exit code only** — output may be in Japanese locale (「エラー」), so never grep for "error".

Issues use `.github/ISSUE_TEMPLATE/` (feature / bug); PR bodies follow `.github/pull_request_template.md`. All in Japanese.

## Do NOT introduce (unless explicitly requested)

- A second HTTP client on the frontend — `axios` is the established client.
- CSS-in-JS (styled-components / emotion) or component kits that conflict with Tailwind CSS + Headless UI.
- Global state libraries (Redux / MobX / Zustand) — none is in use; forms use react-hook-form.
- `logback` — log4j2 is the logging backend and logback is explicitly excluded in `backend/build.gradle`.
- ModelMapper / Dozer (MapStruct is the mapper), MyBatis (Spring Data JPA is the data layer), TestNG (JUnit 5 is the test framework).
