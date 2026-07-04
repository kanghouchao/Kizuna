# CLAUDE.md

## Project Overview

A multi-tenant CMS/CRM/HRM system for running the operations of multiple stores under a single group.

- Backend: Java 21, Spring Boot 3.5+, JUnit 5, Jacoco, Lombok, MapStruct
- Frontend: TypeScript 5.9, React 19, Next.js 16+, Jest, ESLint, Prettier
- Database: PostgreSQL 18+, Redis 8+, Docker volumes for persistence
- Traefik 3+ for reverse proxy and routing

Domain glossary / ubiquitous language: `docs/CONTEXT.md` (Store vs Tenant naming, aggregate vocabulary, open questions).

## Language Policy

- **AI-instruction docs** (this file, `.claude/rules/*.md`): **English**.
- **Human-facing docs** (`docs/**`, `README.md`) and **code comments**: **Japanese**.

Code identifiers, module names, and shell commands stay verbatim regardless of the surrounding language.

## Build, Test & Verify

The system is built and tested with Docker Compose; all commands are driven through the `task` tool so that local runs match CI/CD. Recommended workflow:

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

## Code Style & Conventions

- [Backend](./rules/backend.md)
- [Frontend](./rules/frontend.md)
- [Infrastructure](./rules/infrastructure.md)

Use the Taskfile so that commands run locally match the CI/CD environment, avoiding build or test failures caused by environment differences.
