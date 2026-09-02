# CLAUDE.md

## Project Overview

A platform CMS/CRM/HRM system for running the operations of multiple stores under a single group.

Java is pinned to 25 by `backend/.java-version` (jenv, effective under `backend/`) and `backend/gradle/gradle-daemon-jvm.properties` (Gradle daemon). Builds, tests, and Spotless must run on JDK 25.

## Key documents

- [CONTEXT.md](CONTEXT.md) — the ubiquitous language, in full (Tenant/Store, PlatformUser, AuthSession, StoreScope, Store Context). The glossary below is a summary; **read CONTEXT.md before reasoning about domain terms**.
- [ADRs](docs/adr/) — accepted architecture decisions. Read the relevant one before revisiting a decided question.

## Domain Glossary

- **Central** is retired as a structural concept — it survives only as the former name of the platform-management permission group (`PermissionCode.Console.PLATFORM`). Authorization is RBAC: behavior follows **Role** (a bundle of `Permission` rows); data follows store (`StoreScope`) and stores hang off the user, never off the role. Accounts are unified as **PlatformUser**; store-side vocabulary uses the Store prefix (e.g. StoreProfile).
- The customer-visit aggregate is **Order** — never Reservation or Booking.
- **CentralMenu and StoreMenu were unified into a single platform Menu aggregate** (decided 2026-07-18, #404 decision 2).
- **StoreProfile** = store-facing display settings; **SystemConfig** = platform-level system settings, managed by SYSTEM_CONFIG_MANAGE permission holders. Do not mix.
- **Cast is three layers** (#383; implementation #859–#863): **Cast** = the platform-level person, 1:1 with the CAST-type PlatformUser; **CastEnrollment** = one store-enrollment episode (StoreScoped; ENROLLED / SUSPENDED / WITHDRAWN); **CastProfile** = the public profile, 1:1 with an enrollment. **The code is not split yet** — `Cast` is still the single store-scoped row in `t_casts` — so CONTEXT.md gives the target vocabulary and the code gives what exists today.

## Language Policy

- **AI-instruction docs** (this file and the per-directory `CLAUDE.md` files): **English**.
- **Human-facing docs** (`docs/**`, `README.md`), **code comments**, **GitHub issues/PRs**, and **commit messages**: **Japanese**.
- Code identifiers, module names, and shell commands stay verbatim regardless of the surrounding language.

## Comment Policy

Comments justify the code **as it is now**: invariants, security rationale, non-obvious decisions. Never narrate history — what the code replaced, how it evolved, or which discussion decided it. That record belongs to git log and issues, so comments carry **no issue/PR numbers**; traceability goes through git blame → commit message → issue. Applies to backend and frontend alike.

- **Javadoc/JSDoc are optional.** Never write `@param`/`@return` that only restate the signature. No numbered step comments (`// 1. ...`), no section banners.
- **Length: a comment block stays within 3–5 lines.** Longer reasoning gets compressed to the conclusion plus its one key reason; the full argument goes to the commit message or an ADR. Practical reason: google-java-format re-wraps long CJK blocks badly, so long prose degrades on the next `spotlessApply`.
- **Single-source the explanation.** When one rationale covers many sites, state it in exactly one place and do not duplicate it per call site (precedent: ADR 0002's dormant-filter caveat lives only in the `StoreIsolationTests` method Javadoc, not on the eight entities).
- **Fix on touch — delete vs. compress.** Within the region you touched, **delete** the clearly-violating forms: numbered step comments, `@param`/`@return` that restate the signature, section banners, chatter that restates the code, and English comments (language policy). This is an explicit exception to the default of touching only task-related lines. A long but load-bearing comment — invariant reasoning, a security rationale, a fail-open trap — is **not** a deletion target: at most **compress** it to conclusion + key reason and move the full argument to the commit message or an ADR, and only when you were already rewriting that comment or you are confident the compression keeps the reasoning. When in doubt, leave it as is.
- **Surviving mandatory-comment exceptions**: transitional-exception notes in `package-info.java` (backend/CLAUDE.md) and rule-disable reasons in `steiger.config.mjs` (frontend/CLAUDE.md).

## API Design & API-first

API contract rules live in [docs/api-guidelines.md](docs/api-guidelines.md) (Japanese) — the normative source; read it before adding or changing an endpoint. Any task spanning frontend + backend must present the API contract design (endpoints, methods, status codes, request/response fields, authorization, pagination form) for approval **before** implementation. Every controller handler must carry `@PreAuthorize` or `@PermitAll` — `SecurityConfig` is `anyRequest().permitAll()`, so a missing annotation is a silently public endpoint; `EndpointAuthorizationDeclarationTests` enforces this with no exemption list.

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
task e2e                            # Playwright BDD e2e — needs `jq` on the host; PR author's local responsibility, not run in CI

# Lint & format
task lint                           # check
task format                         # auto-fix

# Local startup
task up                             # start full stack (does NOT rebuild images — run task build first to pick up code changes)
task down                           # stop
task logs service=backend           # view logs
```

Use the Taskfile (Docker = CI parity) for final verification before committing. For fast red-green iteration use the local toolchains: `frontend/` → `npm test` / `npm run lint && npm run lint:fsd && npm run typecheck` (the Docker lint stage runs all three); `backend/` → `./gradlew test` / `./gradlew spotlessApply`.

`task build` also runs as a PR gate inside each side's `Lint and Test (frontend)` / `Lint and Test (backend)` job (`.github/workflows/lint-and-test.yml`): a production build failure turns that check red, so a change that breaks the production build cannot pass CI.

CI is tiered (issue #241) and parallelized by side (#346). The PR gate is three required checks — **Lint and Test (frontend)**, **Lint and Test (backend)**, **Repo Lint** — each running lint + unit(coverage) + build for its own side (`task -d frontend|backend lint` / `test` or `test-unit` / `build`) in parallel jobs. Those steps are skipped when the diff touches none of `frontend/`, `backend/`, `e2e/`, `infrastructure/`, `Taskfile*`, `.github/workflows/`, `.github/scripts/` — in a docs-only PR both side jobs still start and report success; what is skipped are their Buildx / lint / test / build **steps**, while **Repo Lint (actionlint) is ungated and always executes**. The detection is by path prefix, so a CLAUDE.md under those directories still triggers the full gate. **Integration and E2E do not run in CI at all**: they are the PR author's local responsibility — run `task test` (unit + integration) and `task e2e` locally before opening a PR, as the PR template's 検証 section requires. Code review is local-only and manual: review the branch diff before opening a PR (the `mattpocock-skills:code-review` skill is the recommended tool). There is no CI-side automated review job and no Claude-triggered GitHub Action.

## Code Style & Conventions

Per-directory `CLAUDE.md` files carry the area conventions and are auto-loaded when working there:

- [Backend](backend/CLAUDE.md)
- [Frontend](frontend/CLAUDE.md) — plus the design system in [frontend/DESIGN.md](frontend/DESIGN.md) (read FIRST for any UI work)
- [Infrastructure](infrastructure/CLAUDE.md)
- [E2E](e2e/CLAUDE.md) — Playwright BDD scenarios (Japanese Gherkin)

## Repository-wide guardrails

Forbidden operations (enforced locally by `.claude/settings.json` deny rules only — there are no hooks in this repo; they are policy even where enforcement is absent):

- **Force push to `master` or `releases/**`** — the GitHub ruleset rejects it server-side (`non_fast_forward`), and the deny rules block both naming those refs and the bare `git push --force` / `-f` / `--force-with-lease` forms (bare forms push the *current* branch implicitly, which is the only way to reach `master` without naming it). **Force push to a topic branch is allowed**, so a topic branch's history can be rewritten in place instead of through a replacement PR. Always name the remote and branch explicitly — string-matched deny rules cannot cover every spelling (`HEAD:master`, `+master`, …); the server-side rule is the real guard.
- **Merging `master` into a topic branch** (`git merge master` / `git merge origin/master` / `git pull origin master`) — the branch stops being linear, and under `required_linear_history` GitHub then offers neither *Create a merge commit* nor *Rebase and merge*, leaving the PR unmergeable. Sync by rebasing the topic branch onto `master` and force-pushing it.
- **Merging PRs** (`gh pr merge`, auto-merge) — the repository owner merges every PR by hand.
- **Destructive git**: `git reset --hard`, `git clean`, `git branch -D`, `git commit --no-verify`.
- **Docker data wipes**: `docker volume rm`, `docker system prune`, `compose down -v` — dev DB volumes must survive.
- **`task clean` without `service=`** — it ends in `docker system prune -f`, which the deny rules do not see through the `task` spelling. `task clean service=backend|frontend` only removes that side's images and is fine.
- **GitGuardian scans every commit**: even placeholder passwords written as literals in compose files or docs trigger alerts. Always write credentials as `${VAR:-default}`. `.env` is never committed or read.

Judge build/test success by **exit code only** — output may be in Japanese locale (「エラー」), so never grep for "error".

Issues use `.github/ISSUE_TEMPLATE/` (feature / bug); PR bodies follow `.github/pull_request_template.md`. All in Japanese.

## Do NOT introduce (unless explicitly requested)

- A second HTTP client on the frontend — `axios` is the established client.
- A second icon library — `lucide-react` is the icon set (`@heroicons/react` was removed; see `frontend/DESIGN.md`).
- CSS-in-JS (styled-components / emotion) or UI kits that bypass the vendored shadcn/ui primitives (`frontend/src/shared/ui`, Base UI-based) + Tailwind CSS. `radix-ui` and `cmdk` were removed with the Base UI migration (`docs/adr/0005-frontend-shadcn-ui-on-base-ui.md`) — do not bring either back.
- Global state libraries (Redux / MobX / Zustand) — none is in use; forms use react-hook-form.
- `logback` — log4j2 is the logging backend and logback is explicitly excluded in `backend/build.gradle`.
- ModelMapper / Dozer (MapStruct is the mapper), MyBatis (Spring Data JPA is the data layer), TestNG (JUnit 5 is the test framework).
- `jjwt` or a hand-written JWT filter — authentication is the Spring Security standard stack (`docs/adr/0001-authentication-spring-security-standard-stack.md`).
