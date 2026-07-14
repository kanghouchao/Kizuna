---
name: dev-loop
description: "Run Kizuna's dev loop: plan → worktree coding → QA → gated PR → CI watch, under a shared 3-fix budget. Use when the user says start (开始/やる/go) on work already discussed to a conclusion, or names a GitHub issue to execute."
---

# Dev Loop

One run turns **one agreed piece of work** into a reviewed, PR-ready branch. Four roles, one job each: the **planner** is the orchestrator itself — it holds the run's intent and routes each task to a model, and it never hands off and leaves (it keeps designing the next task); the **implementer** (`.claude/agents/kizuna-implementer.md`) codes; the **verifier** (`.claude/agents/kizuna-verifier.md`) adversarially checks; the **reviewer** (`.claude/agents/kizuna-reviewer.md`) reviews the diff pre-push with fresh eyes. None pins a model — the planner assigns each its model per run (Stage 2). Companion skills: `create-pr` (stage 5), `watch-pr` (stage 6). Issue-tracker command conventions: `docs/agents/issue-tracker.md` (local-only).

**Entry is a converged conversation, not a cold issue.** Discussion happens outside the loop, in normal conversation; the loop starts when the user says start, and stage 1 crystallizes what was already agreed. Invoked cold — no prior discussion in context — on non-trivial or ambiguous work: discuss first (`grilling` skill — one question at a time, each with a recommended answer). The loop never starts on guesses.

**Two human checkpoints; nothing else waits on a human:**

- **① Entry** — the user has seen the work (issue and/or discussion) and says start.
- **② Exit** — the run ends at "PR created + acceptance brief (Chinese)" and STOPS. The user reviews and merges every PR by hand; never merge, no auto-merge pre-authorization.

**The loop shape**: ① → crystallize+plan (or, given a qualifying plan doc, skip straight to code) → [ code → QA → fix ]\* → gated PR → [ watch → fix ]\* → brief ②. Both `[…]*` cycles draw on ONE shared budget of **3 fix loops per run**; exhausted → stop with a blocked-brief (Chinese) listing what remains. Out-of-scope defects met along the way are never fixed in-run — file each as a Japanese GitHub issue (`.github/ISSUE_TEMPLATE/bug.md` structure) so it re-enters as a future run.

## Stage 0 — Preflight (main checkout)

`git status --porcelain` clean + on master + `git fetch origin && git rev-list master..origin/master --count` = 0 → proceed silently. Anything else → AskUserQuestion with concrete options (commit / stash / switch / continue as-is); never guess what uncommitted work means. The main checkout stays on master for the whole run — all mutation happens in the worktree.

## Stage 1 — Entry: plan-first or crystallize (orchestrator)

**Plan-first**: if the user's start message points at an existing `docs/plans/<slug>.md` that already satisfies Stage 2's mandatory-section checklist, skip straight to Stage 3 — the plan's origin (which session, which model, hand-written) is not this skill's concern; the file's format is the only contract. Missing any mandatory section → not eligible, fall through to crystallize below.

**Crystallize** (no qualifying plan doc):

- Pull the agreed conclusion out of the conversation. Where an issue exists, cross-check it with `gh issue view <n> --json title,body,comments` (plain `--comments` can print nothing yet exit 0).
- Remaining fact-gathering that would sweep more than ~3 files: delegate to Explore agents — the orchestrator consumes conclusions, it does not sweep.
- Done when: every acceptance criterion is written down, verifiable, and classified —
  - **behavioral** (assertable by a program) → pinned as a playwright-bdd `.feature` scenario, verified by `task e2e`;
  - **visual/exploratory** (needs eyes) → QA browser run + one screenshot each.

## Stage 2 — Plan (the orchestrator is the planner)

The planner is the orchestrator itself: it holds the crystallized Stage-1 context, so planning is never delegated to the implementer, and it keeps designing the next task rather than handing off and leaving. Planning is where the judgement is spent — run it on a strong model. Write `docs/plans/<slug>.md` (git-excluded, Japanese). Mandatory sections: **前提事実** (investigated facts with file refs — the implementer trusts these) / **Tasks** (each task = exactly one commit, allowed file scope, exact Japanese commit message, **実行モデル**) / **TDD seams** (the pre-agreed seam set; the implementer tests only there) / **BDD scenarios** (`.feature` path + Gherkin per behavioral criterion) / **Acceptance criteria** mapping / **Risk tier** (normal | **high-risk**: auth・permissions・DB migrations・payments・tenant isolation) / **検証モデル** (the model for both the verifier and the reviewer this run) / branch name (`feat/…`, `fix/…`).

**Model routing** — the thinking lives in the plan, so a cheaper implementer only executes and reports. Set each task's 実行モデル by how much judgement the plan already nailed down (verbatim code + exact scope → `haiku`/`sonnet`; residual judgement at the seam → `opus`; the single highest-stakes task in a high-risk plan may go to `fable` if budget allows), NOT by how mechanical it looks; **high-risk tasks are hard-pinned to `opus`** (or `fable`, when the plan explicitly names it). A same-model verifier shares the implementer's blind spots, so set 検証モデル independent of — and never weaker than — the task implementers (default `sonnet`, `opus`/`fable` for high-risk). `fable` quota is limited, so reserve it for high-risk work and only when the plan explicitly specifies it. A **fork** (Agent tool `subagent_type: "fork"`) always inherits the parent session's model regardless of 実行モデル/検証モデル — routing to `fable`/`opus`/etc. only takes effect for a fresh spawn. Routing to a cheap model never means "execute without thinking": the implementer stays free to flag a plan gap it hits as a deviation.

Post a short plan brief (goal, tasks + per-task 実行モデル, seams, risk) and continue without waiting — the next human checkpoint is ②.

## Stage 3 — Code (subagent kizuna-implementer)

From the main checkout:

```bash
git worktree add .worktrees/<branch> -b <branch> master
```

The worktree lives in `.worktrees/` inside the main checkout (git-ignored, so the main checkout stays clean); pass its **absolute** path to the subagent.

**`.env` is deny-ruled for agents (read AND write — even `cp` is blocked), so never touch it yourself.** Worktrees self-provision (PR #341): `infrastructure/development/Taskfile.yml`'s `ensure-env` — a dep of `up` — copies `.env` from the main checkout on the first `task up` / `task e2e` inside the worktree, so no user action is needed. The only case that still waits on the user is `ensure-env` failing loud because the MAIN checkout itself lacks `.env` — then ask them to place it per `infrastructure/.env.example`.

Spawn `kizuna-implementer` with the **absolute worktree path**, the **absolute plan path**, and — running it on each task's assigned **実行モデル** (Agent tool `model` override) — the exact `Co-Authored-By:` trailer for that model. Consecutive same-model tasks may share one spawn; a model change starts a fresh spawn, which reads the accumulated commits + plan as its context. Done when every task is one commit, with each gate's exit code and any deviations reported.

## Stage 4 — QA (subagent kizuna-verifier)

Brief: absolute worktree path, the acceptance criteria with classifications, any narrowing (no UI surface → gates + `task e2e` only), and the run's **検証モデル** (Agent `model` override). Verification is fully automated — the user never tests by hand.

FAIL → dispatch the verifier's report verbatim to a fresh `kizuna-implementer` on the failing scope's 実行モデル (plan + report as its context; high-risk → `opus`), then re-run the verifier. Each cycle spends budget.

## Stage 5 — PR (orchestrator)

- **Every run**: before pushing, spawn the **reviewer** — **`kizuna-reviewer`** (`.claude/agents/kizuna-reviewer.md`), a FRESH spawn on the run's 検証モデル (never a fork: a fork inherits this session's plan context and defeats the fresh-eyes design). Brief: the absolute worktree path, the fixed point (`master`), the spec source (issue ref or pasted acceptance criteria — NEVER the plan document), and the risk tier. Blocking findings → fix loop (shared budget), fixed in the worktree before pushing; re-review after a fix goes to the SAME reviewer via SendMessage (delta brief, like verifier re-runs). Non-blocking findings are recorded for the PR's 備考. The report's totals feed the PR template's ローカル code-review 実施 line (`effort: medium`（normal-risk）/ `high`（high-risk）, `指摘: n件 / 未修正: n件`). This replaces both the former high-risk-only adversarial pre-review and the environment-dependent bare `code-review` skill invocation (run #346/#347: bare-name resolution depended on the operator's installed plugins — the committed agent closes that).
- Invoke the **create-pr** skill. Done when: the PR URL is returned.

## Stage 6 — Watch (loop)

Invoke the **watch-pr** skill on the new PR. Blocking findings it dispatches spend the shared budget; judgement/style findings flow into the brief. Done when watch-pr exits green (CI passing AND review consumed) or reports blocked.

## Stage 7 — Acceptance brief (②, Chinese)

What shipped in 1–2 sentences; PR + issue links; CI status; evidence per criterion (behavioral: executed `task e2e` scenario names; visual: screenshot path + what it shows); review findings fixed vs left to the user; deviations from plan; out-of-scope issues filed. Then STOP and wait.

## Post-merge cleanup + retro (after the user says merged)

`task down` in the worktree if its stack is up, then from the main checkout:

```bash
git worktree remove .worktrees/<branch>
git -C <main-checkout> status --porcelain   # BEFORE pull — see below
git pull && git fetch --prune
```

If the status shows a stray modification to a file the run touched, a subagent leaked an edit into the main checkout (run #322: CONTEXT.md, surfaced as a pull collision). Diff it against the merged version — byte-identical → `git restore <file>` is lossless; anything else → AskUserQuestion, never discard. Use `git -C <absolute path>` for every cleanup command — the Bash tool's `cd` does not reliably persist across calls.

This repo squash-merges, so the branch tip is never an ancestor of master and `git branch -d` always refuses. Verify the content landed (`git diff <tip> master -- <touched paths>` shows nothing), then hand the exact `git branch -D <branch>` line to the user — force-delete is theirs, never yours.

Then invoke the **retro** skill — the run is not closed until its friction is mined into fix / issue / memory and briefed.

## Ground rules

Exit-code-only judgement, `${VAR:-default}` credentials, and the forbidden-git list are repo-wide law — root `CLAUDE.md`.

Lasting working docs live under `docs/`, never in a system temp dir (`/var/folders`, `/private/tmp`): plans in `docs/plans/`, investigation/diagnostic notes in `docs/research/`. The scratchpad holds only consumed-once throwaway (a PR body, an issue body) — anything a later step or a human might reread belongs under `docs/`. Never commit `docs/plans/`, `docs/agents/`, `docs/superpowers/`, `docs/research/` — all git-excluded.
