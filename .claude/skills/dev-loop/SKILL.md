---
name: dev-loop
description: "Run Kizuna's dev loop: plan → worktree coding → QA → gated PR → CI watch, under a shared 3-fix budget. Use when the user says start (开始/やる/go) on work already discussed to a conclusion, or names a GitHub issue to execute."
---

# Dev Loop

One run turns **one agreed piece of work** into a reviewed, PR-ready branch. Stage executors: `.claude/agents/kizuna-coder.md` (code) and `.claude/agents/kizuna-qa.md` (verify); companion skills: `create-pr` (stage 5), `watch-pr` (stage 6). Issue-tracker command conventions: `docs/agents/issue-tracker.md` (local-only).

**Entry is a converged conversation, not a cold issue.** Discussion happens outside the loop, in normal conversation; the loop starts when the user says start, and stage 1 crystallizes what was already agreed. Invoked cold — no prior discussion in context — on non-trivial or ambiguous work: discuss first (`grilling` skill — one question at a time, each with a recommended answer). The loop never starts on guesses.

**Two human checkpoints; nothing else waits on a human:**

- **① Entry** — the user has seen the work (issue and/or discussion) and says start.
- **② Exit** — the run ends at "PR created + acceptance brief (Chinese)" and STOPS. The user reviews and merges every PR by hand; never merge, no auto-merge pre-authorization.

**The loop shape**: ① → crystallize → plan → [ code → QA → fix ]\* → gated PR → [ watch → fix ]\* → brief ②. Both `[…]*` cycles draw on ONE shared budget of **3 fix loops per run**; exhausted → stop with a blocked-brief (Chinese) listing what remains. Out-of-scope defects met along the way are never fixed in-run — file each as a Japanese GitHub issue (`.github/ISSUE_TEMPLATE/bug.md` structure) so it re-enters as a future run.

## Stage 0 — Preflight (main checkout)

`git status --porcelain` clean + on master + `git fetch origin && git rev-list master..origin/master --count` = 0 → proceed silently. Anything else → AskUserQuestion with concrete options (commit / stash / switch / continue as-is); never guess what uncommitted work means. The main checkout stays on master for the whole run — all mutation happens in the worktree.

## Stage 1 — Crystallize (orchestrator)

- Pull the agreed conclusion out of the conversation. Where an issue exists, cross-check it with `gh issue view <n> --json title,body,comments` (plain `--comments` can print nothing yet exit 0).
- Remaining fact-gathering that would sweep more than ~3 files: delegate to Explore agents — the orchestrator consumes conclusions, it does not sweep.
- Done when: every acceptance criterion is written down, verifiable, and classified —
  - **behavioral** (assertable by a program) → pinned as a playwright-bdd `.feature` scenario, verified by `task e2e`;
  - **visual/exploratory** (needs eyes) → QA browser run + one screenshot each.

## Stage 2 — Plan (orchestrator)

Write `docs/plans/<slug>.md` (git-excluded, Japanese). Mandatory sections: **前提事実** (investigated facts with file refs — the coder trusts these) / **Tasks** (each task = exactly one commit, allowed file scope, exact Japanese commit message) / **TDD seams** (the pre-agreed seam set; the coder tests only there) / **BDD scenarios** (`.feature` path + Gherkin per behavioral criterion) / **Acceptance criteria** mapping / **Risk tier** (normal | **high-risk**: auth・permissions・DB migrations・payments・tenant isolation) / branch name (`feat/…`, `fix/…`).

Post a short plan brief (goal, tasks, seams, risk) and continue without waiting — the next human checkpoint is ②.

## Stage 3 — Code (subagent kizuna-coder)

From the main checkout:

```bash
git worktree add .worktrees/<branch> -b <branch> master
cp infrastructure/development/.env .worktrees/<branch>/infrastructure/development/.env
```

The worktree lives in `.worktrees/` inside the main checkout (git-ignored, so the main checkout stays clean); pass its **absolute** path to the subagent.

Spawn `kizuna-coder` with the **absolute worktree path** and **absolute plan path**. Done when it reports: one commit per task, every gate exit code, deviations.

## Stage 4 — QA (subagent kizuna-qa)

Brief: absolute worktree path, the acceptance criteria with classifications, any narrowing (no UI surface → gates + `task e2e` only). Verification is fully automated — the user never tests by hand.

FAIL → send the QA report verbatim to the SAME coder via SendMessage (it keeps plan + code context; spawn fresh with plan + report only if it is gone), then re-run QA. Each cycle spends budget.

## Stage 5 — PR (orchestrator)

- **High-risk plans only**: before pushing, one adversarial pre-review — a FRESH subagent gets the diff and the issue but NOT the plan; it lists candidate defects each with a concrete failure scenario (inputs/state → wrong outcome), then verifies or dismisses each against the code. Blocking findings → fix loop (shared budget). Normal-risk skips this.
- Invoke the **create-pr** skill. Done when: the PR URL is returned.

## Stage 6 — Watch (loop)

Invoke the **watch-pr** skill on the new PR. Blocking findings it dispatches spend the shared budget; judgement/style findings flow into the brief. Done when watch-pr exits green (CI passing AND review consumed) or reports blocked.

## Stage 7 — Acceptance brief (②, Chinese)

What shipped in 1–2 sentences; PR + issue links; CI status; evidence per criterion (behavioral: executed `task e2e` scenario names; visual: screenshot path + what it shows); review findings fixed vs left to the user; deviations from plan; out-of-scope issues filed. Then STOP and wait.

## Post-merge cleanup + retro (after the user says merged)

`task down` in the worktree if its stack is up, then from the main checkout:

```bash
git worktree remove .worktrees/<branch>
git pull && git fetch --prune
```

This repo squash-merges, so the branch tip is never an ancestor of master and `git branch -d` always refuses. Verify the content landed (`git diff <tip> master -- <touched paths>` shows nothing), then hand the exact `git branch -D <branch>` line to the user — force-delete is theirs, never yours.

Then invoke the **retro** skill — the run is not closed until its friction is mined into fix / issue / memory and briefed.

## Ground rules

Exit-code-only judgement, `${VAR:-default}` credentials, and the forbidden-git list are repo-wide law — root `CLAUDE.md`. Never commit `docs/plans/`, `docs/superpowers/`, `docs/agents/`.
