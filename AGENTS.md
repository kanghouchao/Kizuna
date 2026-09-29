# Project Instructions

## Read by task

Kizuna operates multiple stores within one group (CMS / CRM / HRM). Before reasoning about domain terms, read [CONTEXT.md](CONTEXT.md). Before revisiting an architectural decision, inspect the relevant implementation, tests, and git history.

- Backend work: [backend/AGENTS.md](backend/AGENTS.md); builds, tests, and Spotless require JDK 25.
- Endpoint work: read [API contract](backend/AGENTS.md#api-contract) before adding or changing an endpoint. Cross-frontend/backend work requires approval of the API contract before implementation.
- Frontend work: [frontend/AGENTS.md](frontend/AGENTS.md); for any UI work read [DESIGN.md](frontend/DESIGN.md) first.
- Infrastructure: [infrastructure/AGENTS.md](infrastructure/AGENTS.md).
- E2E: [e2e/AGENTS.md](e2e/AGENTS.md).
- Schema changes: [database rules](backend/src/main/resources/db/AGENTS.md).
- Development and PR verification: [CONTRIBUTING.md](CONTRIBUTING.md). Final verification before committing uses Taskfiles (Docker parity); judge success by exit code only. Local toolchains are for fast iteration.

`AGENTS.md` is the instruction source in each directory; `CLAUDE.md` is its single-line loader.

## Language Policy

- **AI-instruction docs** (this file, per-directory `AGENTS.md`, and `CLAUDE.md` loaders): **English**.
- **Human-facing docs** (`docs/**`, `README.md`), **code comments**, **GitHub issues/PRs**, and **commit messages**: **Japanese**.
- Code identifiers, module names, and shell commands stay verbatim regardless of the surrounding language.

## Comment Policy

Comments justify the code **as it is now**: invariants, security rationale, non-obvious decisions. Never narrate history — what the code replaced, how it evolved, or which discussion decided it. That record belongs to git log and issues, so comments carry **no issue/PR numbers**; traceability goes through git blame → commit message → issue. Applies to backend and frontend alike.

- **Javadoc/JSDoc are optional.** Never write `@param`/`@return` that only restate the signature. No numbered step comments (`// 1. ...`), no section banners.
- **Length: a comment block stays within 3–5 lines.** Longer reasoning gets compressed to the conclusion plus its one key reason; the full argument goes to the commit message. Practical reason: google-java-format re-wraps long CJK blocks badly, so long prose degrades on the next `spotlessApply`.
- **Single-source the explanation.** When one rationale covers many sites, state it in exactly one place and do not duplicate it per call site (the dormant-filter caveat lives only in the `StoreIsolationTests` method Javadoc, not on each entity).
- **Fix on touch — delete vs. compress.** Within the region you touched, **delete** the clearly-violating forms: numbered step comments, `@param`/`@return` that restate the signature, section banners, chatter that restates the code, and English comments (language policy). This is an explicit exception to the default of touching only task-related lines. A long but load-bearing comment — invariant reasoning, a security rationale, a fail-open trap — is **not** a deletion target: at most **compress** it to conclusion + key reason and move the full argument to the commit message, and only when you were already rewriting that comment or you are confident the compression keeps the reasoning. When in doubt, leave it as is.
- **Surviving mandatory-comment exceptions**: transitional-exception notes in `package-info.java` (backend/CLAUDE.md) and rule-disable reasons in `steiger.config.mjs` (frontend/CLAUDE.md).

## Repository-wide guardrails

Forbidden operations are repository policy. This repository provides no local command deny rules or hooks; agents must enforce these restrictions when choosing commands:

- **Force push to `master` or `releases/**`** is forbidden. Force push to a topic branch is allowed. Always name the remote and topic branch explicitly.
- **Merging `master` into a topic branch** (`git merge master` / `git merge origin/master` / `git pull origin master`) — the branch stops being linear, and under `required_linear_history` GitHub then offers neither _Create a merge commit_ nor _Rebase and merge_, leaving the PR unmergeable. Sync by rebasing the topic branch onto `master` and force-pushing it.
- **Merging PRs** (`gh pr merge`, auto-merge) — the repository owner merges every PR by hand.
- **Destructive git**: `git reset --hard`, `git clean`, `git branch -D`, `git commit --no-verify`.
- **Docker data wipes**: `docker volume rm`, `docker system prune`, `compose down -v` — dev DB volumes must survive.
- **GitGuardian scans every commit**: even placeholder passwords written as literals in compose files or docs trigger alerts. Always write credentials as `${VAR:-default}`. `.env` is never committed or read.

Issues use `.github/ISSUE_TEMPLATE/` (feature / bug); PR bodies follow `.github/pull_request_template.md`. All in Japanese.

## Do NOT introduce (unless explicitly requested)

- A second HTTP client on the frontend — `axios` is the established client.
- A second icon library — `lucide-react` is the icon set (`@heroicons/react` was removed; see `frontend/DESIGN.md`).
- CSS-in-JS (styled-components / emotion) or UI kits that bypass the vendored shadcn/ui primitives (`frontend/src/shared/ui`, Base UI-based) + Tailwind CSS. Use Base UI-based primitives; do not add `radix-ui` or `cmdk`.
- Global state libraries (Redux / MobX / Zustand) — none is in use; forms use react-hook-form.
- `logback` — log4j2 is the logging backend and logback is explicitly excluded in `backend/build.gradle`.
- ModelMapper / Dozer (MapStruct is the mapper), MyBatis (Spring Data JPA is the data layer), TestNG (JUnit 5 is the test framework).
- `jjwt` or a hand-written JWT filter — authentication uses the Spring Security standard stack described in [backend/AGENTS.md](backend/AGENTS.md).
