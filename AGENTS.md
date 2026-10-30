# Project Instructions

## Read by task

Kizuna operates multiple stores within one group (CMS / CRM / HRM). Before reasoning about domain terms, read [GLOSSARY.md](GLOSSARY.md). Before revisiting an architectural decision, inspect the relevant implementation, tests, and git history.

- Backend work: [backend/AGENTS.md](backend/AGENTS.md); builds, tests, and Spotless require JDK 25.
- Endpoint work: read [API contract](backend/AGENTS.md#api-contract) before adding or changing an endpoint. Cross-frontend/backend work requires approval of the API contract before implementation.
- Frontend work: [frontend/AGENTS.md](frontend/AGENTS.md); for any UI work read [DESIGN.md](frontend/DESIGN.md) first.
- Infrastructure: [infrastructure/AGENTS.md](infrastructure/AGENTS.md).
- E2E: [e2e/AGENTS.md](e2e/AGENTS.md).
- Schema changes: [database rules](backend/src/main/resources/db/AGENTS.md).
- Development and PR verification: [CONTRIBUTING.md](CONTRIBUTING.md). Final verification before committing uses Taskfiles (Docker parity); judge success by exit code only. Local toolchains are for fast iteration.

`AGENTS.md` is the instruction source in each directory; `CLAUDE.md` is its single-line loader.

## Agent skills

### Issue tracker

GitHub Issues via `gh`, scheduled on Project #5. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default triage labels, applied alongside `spec` / `implementation`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Language Policy

- **AI-instruction docs** (this file, per-directory `AGENTS.md`, and `CLAUDE.md` loaders): **English**.
- **Human-facing docs** (`README.md`, `GLOSSARY.md`, `CONTRIBUTING.md`, `frontend/DESIGN.md`, `e2e/README.md`), **code comments**, **GitHub issues/PRs**, and **commit messages**: **Japanese**.
- Code identifiers, module names, and shell commands stay verbatim regardless of the surrounding language.

## Comment Policy

Comments justify the code **as it is now**: invariants, security rationale, non-obvious decisions. Never narrate history — what the code replaced, how it evolved, or which discussion decided it. That record belongs to git log and issues, so comments carry **no issue/PR numbers**; traceability goes through git blame → commit message → issue. Applies to backend and frontend alike.

- **Javadoc/JSDoc are optional.** Never write `@param`/`@return` that only restate the signature. No numbered step comments (`// 1. ...`), no section banners.
- **Length: a comment block stays within 3–5 lines.** Longer reasoning gets compressed to the conclusion plus its one key reason; the full argument goes to the commit message. Practical reason: google-java-format re-wraps long CJK blocks badly, so long prose degrades on the next `spotlessApply`.
- **Single-source the explanation.** When one rationale covers many sites, state it in exactly one place and do not duplicate it per call site (a cross-store query states its boundary once, on its repository declaration, not on each caller).
- **Fix on touch — delete vs. compress.** Within the region you touched, **delete** the clearly-violating forms: numbered step comments, `@param`/`@return` that restate the signature, section banners, chatter that restates the code, and English comments (language policy). This is an explicit exception to the default of touching only task-related lines. A long but load-bearing comment — invariant reasoning, a security rationale, a fail-open trap — is **not** a deletion target: at most **compress** it to conclusion + key reason and move the full argument to the commit message, and only when you were already rewriting that comment or you are confident the compression keeps the reasoning. When in doubt, leave it as is.
- **Surviving mandatory-comment exceptions**: transitional-exception notes in `package-info.java` (backend/AGENTS.md) and rule-disable reasons in `steiger.config.mjs` (frontend/AGENTS.md).

## Repository-wide guardrails

Forbidden operations are repository policy. This repository provides no local command deny rules or hooks; agents must enforce these restrictions when choosing commands:

- **Force push to `master` or `releases/**`** is forbidden. Force push to a topic branch is allowed. Always name the remote and topic branch explicitly.
- **Merging `master` into a topic branch** (`git merge master` / `git merge origin/master` / `git pull origin master`) — the branch stops being linear, and under `required_linear_history` GitHub then offers neither _Create a merge commit_ nor _Rebase and merge_, leaving the PR unmergeable. Sync by rebasing the topic branch onto `master` and force-pushing it.
- **Merging PRs** (`gh pr merge`, auto-merge) — the repository owner merges every PR by hand.
- **Destructive git**: `git reset --hard`, `git clean`, `git branch -D`, `git commit --no-verify`.
- **Docker data wipes**: `docker volume rm`, `docker system prune`, `compose down -v` — dev DB volumes must survive.
- **GitGuardian scans every commit**: even placeholder passwords written as literals in compose files or docs trigger alerts. Always write credentials as `${VAR:-default}`. `.env` is never committed or read.

Issues use `.github/ISSUE_TEMPLATE/` (feature / bug); PR bodies follow `.github/pull_request_template.md`. All in Japanese. When opening a PR, check its human-review items against the criteria in CONTRIBUTING.md; if any applies, tell the user to run `/show-me` in this authoring session before merge. A show-me page for such a PR names the criteria met, the `file:line` to read for each, and what the tests do and do not prove.

## Do NOT introduce (unless explicitly requested)

- A second HTTP client on the frontend — `axios` is the established client.
- A second icon library — `lucide-react` is the icon set (`@heroicons/react` was removed; see `frontend/DESIGN.md`).
- CSS-in-JS (styled-components / emotion) or UI kits that bypass the vendored shadcn/ui primitives (`frontend/src/shared/ui`, Base UI-based) + Tailwind CSS. Use Base UI-based primitives; do not add `radix-ui` or `cmdk`.
- Global state libraries (Redux / MobX / Zustand) — none is in use; forms use react-hook-form.
- `logback` — log4j2 is the logging backend and logback is explicitly excluded in `backend/build.gradle`.
- ModelMapper / Dozer (MapStruct is the mapper), MyBatis (Spring Data JPA is the data layer), TestNG (JUnit 5 is the test framework).
- `jjwt` or a hand-written JWT filter — authentication uses the Spring Security standard stack described in [backend/AGENTS.md](backend/AGENTS.md).

## Code Review Rules

Severity and scope for any reviewer, human or bot. The conventions themselves live in this file and the per-directory `AGENTS.md`; this section does not restate them.

- **Important** only when the change can break behavior, expose a store's data outside the caller's store set (`StoreScope` in GLOSSARY.md), bypass authorization, race under concurrent requests, or lose data. Style and naming are at most Nit, and a review carries at most 3 Nits.
- **Do not report**: anything `task lint` (Spotless / ESLint) already enforces, lockfiles, or pinned ids in Liquibase seed changesets for tables that only Liquibase writes.
- **Always check**: an added or changed endpoint follows the [API contract](backend/AGENTS.md#api-contract); a cross-store read takes the caller's store set and a write verifies its single storeId; logs and list DTOs carry no PII the endpoint does not need.
- **Evidence**: a claim about behavior cites the `file:line` that produces it; never infer behavior from names.
- **Human review**: a diff that meets a criterion in [CONTRIBUTING.md](CONTRIBUTING.md) "人手レビューが必要な PR" while the PR body leaves that item unchecked is Important.
- **Scope**: report what this diff introduces. Pre-existing problems go in a separate, non-blocking list. Do not repeat a finding already answered in the thread.
