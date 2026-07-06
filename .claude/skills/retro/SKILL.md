---
name: retro
description: "Retrospect a completed run or session: mine the execution history for friction, triage every finding into fix/issue/memory, and brief the user. Use at dev-loop post-merge cleanup, or when the user asks for a retrospective (复盘) of significant work."
---

# Retro

Input: the execution history already in context (a dev-loop run, or this session's work). The loop optimizes itself through this step — cleanup alone throws the run's evidence away.

## Steps

1. **Collect friction.** Sweep the history for: plan deviations the coder reported; QA failures and their root causes; budget spent (which loop, why); gates or hooks that missed or false-fired; review-finding patterns; tooling traps hit along the way. Done when: the friction list is written out — an empty list must be argued from the history, never assumed.
2. **Triage every item into exactly one bucket — no silent drops:**
   - **Product** — a defect or gap in shipped behavior → confirm its out-of-scope issue was already filed during the run; file any missed ones (Japanese, `.github/ISSUE_TEMPLATE/bug.md` structure).
   - **Architecture** — the code fought the change (wrong seams, hidden coupling, test blind spots) → file a Japanese issue; for a deeper dive, point it at the `improve-codebase-architecture` skill.
   - **Workflow** — friction in `.claude` config, agents, skills, gates, or conventions → file a Japanese chore issue, or draft the config change as a PR when small (`.claude` is repo-tracked; skill edits follow `writing-great-skills`). Never go beyond a draft PR — the user merges.
3. **Persist lessons** the repo cannot re-derive (behavioral traps, tool quirks, user rulings) into auto-memory; update or delete memories the run disproved.
4. **Brief (Chinese).** Every friction item with its destination — 已修 / issue #N / PR #N / 已记忆 — plus improvement recommendations ranked by expected payoff. Done when: no item lacks a destination.
