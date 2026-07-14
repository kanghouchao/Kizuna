---
name: create-pr
description: Draft and open a GitHub PR that passes Kizuna's conformance gate. Use whenever a PR is about to be created (any `gh pr create`) — inside dev-loop or standalone.
---

# Create PR

The only valid form is `gh pr create --title "<title>" --body-file <absolute-path>` — the path as a LITERAL string, never a shell variable (`$DIR/body.md` is rejected: the gate validates the command statically and cannot expand variables). A PreToolUse hook (`.claude/hooks/pre-pr-gate.sh`) rejects every other form and re-runs the gate below, so skipping this skill still gets stopped — passing the gate first is the fast path.

## Steps

1. **Draft.** Title in conventional-commit form (`feat|fix|chore|refactor|docs|test|perf|ci(scope): …`), Japanese. Body written to an absolute-path temp file following `.github/pull_request_template.md` — 概要 / Closes #n（or Refs #n）/ 変更内容 / 検証 / 決定ログ / 備考, Japanese throughout. In 検証, check a gate box `[x]` only if that gate exited 0 against the current tree, and list the e2e scenario names that actually ran. Also check the ローカル code-review line `[x]` only after the **`kizuna-reviewer`** agent (`.claude/agents/kizuna-reviewer.md`) actually reviewed the branch diff (dev-loop Stage 5; a standalone PR spawns it the same way — fresh spawn, diff + spec source, never the plan). Record `effort: medium`（normal-risk）or `high`（high-risk）, plus `指摘: <n>件 / 未修正: <n>件` in the same line — unresolved findings go to 備考, never silently dropped. **Docs-only diffs are exempt from the task gates and the review line**: when no changed file touches `frontend/`, `backend/`, `e2e/`, `infrastructure/`, `Taskfile*`, `.github/workflows/`, or `.github/scripts/`, you may leave the gate boxes AND the review line unchecked IF the 検証 section carries the declaration `対象外（コード変更なし）` — check-pr.sh verifies the claim against the actual diff, so a false declaration fails the gate. In 決定ログ, record the calls a reviewer would otherwise have to reconstruct. Done when: no template comment (`<!-- -->`) remains and every claim in the body is evidenced.
2. **Gate.** Run `.claude/skills/create-pr/check-pr.sh "<title>" <body-file>` — fix the draft and rerun until exit 0. Never edit the script to make it pass.
3. **Open.** Plain `git push`, then `gh pr create --title "..." --body-file <file>` — run both from the worktree whose branch is the PR head, never from the main checkout (which stays on `master`; running there makes `gh pr create` default to `master` as head and error with "head branch is the same as base branch"). Done when: the PR URL is returned. Never merge and never enable auto-merge — merging belongs to the user.
