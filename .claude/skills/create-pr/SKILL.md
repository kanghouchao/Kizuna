---
name: create-pr
description: Draft and open a GitHub PR that passes Kizuna's conformance gate. Use whenever a PR is about to be created (any `gh pr create`) — inside dev-loop or standalone.
---

# Create PR

The only valid form is `gh pr create --title "<title>" --body-file <absolute-path>`. A PreToolUse hook (`.claude/hooks/pre-pr-gate.sh`) rejects every other form and re-runs the gate below, so skipping this skill still gets stopped — passing the gate first is the fast path.

## Steps

1. **Draft.** Title in conventional-commit form (`feat|fix|chore|refactor|docs|test|perf|ci(scope): …`), Japanese. Body written to an absolute-path temp file following `.github/pull_request_template.md` — 概要 / Closes #n（or Refs #n）/ 変更内容 / 検証 / 決定ログ / 備考, Japanese throughout. In 検証, check a gate box `[x]` only if that gate exited 0 against the current tree, and list the e2e scenario names that actually ran. In 決定ログ, record the calls a reviewer would otherwise have to reconstruct. Done when: no template comment (`<!-- -->`) remains and every claim in the body is evidenced.
2. **Gate.** Run `.claude/skills/create-pr/check-pr.sh "<title>" <body-file>` — fix the draft and rerun until exit 0. Never edit the script to make it pass.
3. **Open.** Plain `git push`, then `gh pr create --title "..." --body-file <file>`. Done when: the PR URL is returned. Never merge and never enable auto-merge — merging belongs to the user.
