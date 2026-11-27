# Code Review Rules

Severity and scope for any reviewer, human or bot. The conventions themselves live in [AGENTS.md](AGENTS.md) and the per-directory `AGENTS.md`; this file does not restate them.

- **Important** only when the change can break behavior, expose a store's data outside the caller's store set (`StoreScope` in GLOSSARY.md), bypass authorization, race under concurrent requests, lose data, or break an architecture rule that no test or lint enforces (backend layers and aggregates, frontend FSD placement and designated hooks). Style and naming are at most Nit, and a review carries at most 3 Nits; an architecture rule is not style.
- **Do not report**: anything `task lint` (Spotless / ESLint) already enforces, lockfiles, or pinned ids in Liquibase seed changesets for tables that only Liquibase writes.
- **Always check**: an added or changed endpoint follows the [API contract](backend/AGENTS.md#api-contract); a cross-store read takes the caller's store set and a write verifies its single storeId; logs and list DTOs carry no PII the endpoint does not need; added or moved code follows the rules marked as resting on review in [backend/AGENTS.md](backend/AGENTS.md#layer--module-rules) and [frontend/AGENTS.md](frontend/AGENTS.md).
- **Evidence**: a claim about behavior cites the `file:line` that produces it; never infer behavior from names.
- **Human review**: a diff that meets a criterion in [CONTRIBUTING.md](CONTRIBUTING.md) "人手レビューが必要な PR" while the PR body leaves that item unchecked is Important.
- **Scope**: report what this diff introduces. Pre-existing problems go in a separate, non-blocking list. Do not repeat a finding already answered in the thread.
- **Posting**: post a PR review with the GitHub `add_review_to_pr` tool, as one GitHub review: the overall comment in the review body, and each finding as a `file_comments` entry on the relevant diff line, so every finding opens its own review thread. The comment itself states the evidence, which may lie outside the diff or in the PR body. Do not post findings as plain PR comments or fold them into the body; the body holds the summary, the pre-existing list, and any finding that no diff line relates to.
