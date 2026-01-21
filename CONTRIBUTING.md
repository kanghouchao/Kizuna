# Contributing Guide

The goal of this document is to make contributions safe, reviewable, and fast to ship. The emphasis is on clear human-reviewed changes and high-quality code.

Keep changes small and focused. If a change touches more than one area (frontend/backend/environment), prefer separate pull requests.

## Quick checklist (before opening a PR)

- Build: project builds locally (see "Try locally" below).
- Lint / format: run linters and formatters for the affected area.
- Tests: unit tests for the changed area pass and new behavior is covered by tests.
- Smoke: include a short validation checklist in the PR description for reviewers.
- PR size: prefer smaller PRs that are easy to review (one logical change per PR).

## Try locally (minimal reproducible steps)

You should install Docker and Docker Buildx to run the full stack locally. The Makefile provides common commands.

Before opening a PR it's helpful to run a minimal local validation to save reviewers time. Example quick steps (copy/paste):

- Build & tests:

```bash
make test build
```

- Start full stack for manual smoke checks (requires Docker/Buildx):

```bash
make up
```

## Local development & useful commands

This repository uses Make targets and Docker for local development. Common commands (run from the repository root):

- Build all images: make build service=frontend|backend
- Start local stack: make up
- Show running containers: make ps
- Follow logs for a service: make logs service=backend|frontend|traefik|database
- Run tests for a service: make test service=frontend|backend
- Lint or format: make lint service=frontend|backend
- Format: make format service=frontend|backend
- Stop and remove containers: make down
- (be careful!) Clean all containers, volumes, images: make clean service=frontend|backend

Notes:
- When you are not specifying service=..., the command applies to both frontend and backend, or all services(e.g. `make logs`).
- Frontend dev server: `frontend/` uses Next.js (run `npm run dev` inside `frontend` for a typical dev workflow).
- Backend build/test: use the Gradle wrapper in `backend/` (e.g. `./gradlew build` or `./gradlew test`).
	- On Windows PowerShell use `gradlew.bat build` or `gradlew.bat test`.
	- Alternatively use WSL for a POSIX-like environment and run `./gradlew ...` there.

## Code quality and style

High-quality code is essential. Follow these conventions:

- Keep functions and components small and focused.
- Add unit tests for new logic and bug fixes. If a change touches security or data migrations, include an integration or smoke test where practical.
- Avoid large formatting-only changes in the same PR as functional changes.
- Follow existing project style: backend uses Spotless + Google Java Format; frontend uses ESLint.
- Run linters and formatters before opening a PR.

Commit message guidance:

- Use present-tense, short summary in the first line (<= 72 chars).
- Provide a short description body if the change is non-trivial.
- Reference the issue number when applicable (e.g., `Closes #123`).

## Tests & Coverage (repeat)

- Frontend: Jest, Testing Library; put tests under `frontend/src/**/__tests__/**`.
- Backend: JUnit; generate Jacoco report to `backend/build/reports/jacoco`.

## Quality gates

Before requesting review, please ensure the following (CI will also verify these):

1. Build passes for the affected service(s).
2. Lint/Typecheck (frontend) and code format (backend Spotless) pass.
3. Unit tests for the affected service(s) pass; add tests for new behavior.
4. Include a short smoke validation list in the PR description.
5. Coverage: maintain at least 70% line coverage for unit tests (CI enforces a 70% gate).

Coverage targets are enforced in CI; aim to keep changes well tested and add tests for new behavior.

Notes on coverage enforcement:

- Frontend: Jest's config (`frontend/jest.config.cjs`) enables `collectCoverage` and a global coverageThreshold (lines: 70). Running `npm test` will fail if thresholds are not met — CI runs the same tests.
- Backend: Jacoco is configured in `backend/build.gradle` and the Gradle `check` task depends on `jacocoTestCoverageVerification`. The project is configured to enforce a minimum 70% line coverage (excludes generated/build directories). CI runs the backend test stage via the Dockerfile, which invokes the Gradle test and coverage verification steps; failures will fail the CI job.

## Repo conventions and important paths

- Frontend source: `frontend/src/` (Next.js + TypeScript)
- Frontend middleware: `frontend/src/middleware.ts` — host/tenant resolution and cookies
- Shared HTTP client (frontend): `frontend/src/lib/client.ts`
- Frontend server components (tenant templates): `frontend/src/app/tenant/templates/`
- Backend Java: `backend/src/main/java/com/kizuna/`
- Backend changelogs/migrations: `backend/src/main/resources/db/changelog/`
- Makefile and Docker compose at repo root control local orchestration.

Architecture reminders:

- Traefik routes `/api/*` to the backend and strips the prefix before it reaches Spring Boot.
- Backend namespaces: `/central/*` (admin) and `/tenant/*` (tenant).
- Tenant selection is host-based; middleware sets cookies consumed by server components (`x-mw-role`, `x-mw-tenant-id`, `x-mw-tenant-name`, `x-mw-tenant-template`). Server components should read these via `cookies()`.

## PR workflow and review checklist

When opening a PR, include:

**You can use the template in [here](.github/pull_request_template.md)**

Note: the repository has a CI check that validates PR bodies contain required sections (Context & Motivation, Scope of Changes, Quality Gates, Test Notes, Risk & Mitigation). Omitting those headings will cause the PR template check to fail in CI.

- A short summary of what changed and why.
- The commands used to validate locally (build/test/lint).
- A short smoke checklist for reviewers (what to click/observe, basic expected behavior).
- Any DB migration or infra changes, including rollout and rollback notes.
- Tests added/updated and where they live.


Reviewers will check:

- Does the change have tests (or a good rationale if not)?
- Is the change small and focused enough to review?
- Are linters and formatters satisfied?
- Any security or infra impact clearly documented?

## Optional: AI-assisted contributions

AI tools may be used to help draft code, tests, or documentation, but all AI-assisted changes must be carefully reviewed by a human before merging. If you used AI assistance, it is helpful (but not required) to note that in the PR and include a brief summary of how the output was validated.

Suggested minimal note (optional): "Parts of this PR were drafted with AI assistance; I reviewed and adjusted the generated code and added tests to cover behavior X."

Thanks for contributing!
