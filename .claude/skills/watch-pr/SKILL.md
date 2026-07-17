---
name: watch-pr
description: "Watch a GitHub PR to green: poll CI checks and triage review THREADS (resolved-state driven) in a self-paced loop until CI passes and every thread is dispositioned. Use after opening or pushing to a PR, or standalone via `/loop /watch-pr <n>`."
---

# Watch PR

A closed loop over one PR: beats, feedback gates, exit conditions. Input: a PR number (default: the current branch's PR via `gh pr view --json number`). Never merge.

## Each beat

1. **Probe**: `gh pr checks <n>` — judge by exit code / structured output only.
2. **Collect** — the machine-readable input is the **review-thread resolution state**, not comment-body reading. List threads via GraphQL:
   ```
   gh api graphql -f query='query($o:String!,$r:String!,$n:Int!){repository(owner:$o,name:$r){pullRequest(number:$n){reviewThreads(first:100){nodes{id isResolved path line comments(first:1){nodes{databaseId author{login} body}}}}}}}' -F o=<owner> -F r=<repo> -F n=<n>
   ```
   Each node gives the thread `id` (for `resolveReviewThread`) and its first comment's `databaseId` (the numeric REST id needed to reply — see step 3). A finding = an **unresolved** thread. Anchorless summary findings (issue-level comments with no thread — e.g. a "No issues found" or a whole-file note) carry no thread; collect those via `gh pr view <n> --json comments` and triage by text as a fallback so they aren't dropped.
3. **Triage every unresolved thread, then DISPOSITION it — the exit gate is disposition, not mere triage**:
   - **Blocking** (correctness, security, data loss, spec violation) — dev-loop: dispatch verbatim to a `kizuna-implementer` (plan + finding as its context, on the affected scope's 実行モデル), re-run the verifier if runtime code changed (spends the shared budget). Standalone: report verbatim to the user and wait. If the fix would delete or weaken an existing test, get the user's explicit sign-off (e.g. via a clarifying question) **before** dispatching — a permission layer may reject a test deletion made solely on a relayed agent instruction, so asking first avoids a wasted dispatch-block-report round-trip. **After the fix is pushed (and confirmed by a re-review beat where one runs), close the loop on the thread in two steps — reply, then resolve**: first reply on the thread naming the fix commit (`gh api graphql -f query='mutation($tid:ID!,$body:String!){addPullRequestReviewThreadReply(input:{pullRequestReviewThreadId:$tid,body:$body}){comment{id}}}' -F tid=<threadId> -F body='修正済み(<sha>): <what changed>'`), then resolve it (`gh api graphql -f query='mutation($id:ID!){resolveReviewThread(input:{threadId:$id}){thread{isResolved}}}' -F id=<threadId>`). A bare resolve with no reply leaves the merger no record of what fixed the finding — the reply+resolve pair IS the disposition; never skip the reply. When requesting that re-review beat from `kizuna-reviewer` via `SendMessage`, name the full commit range since its last check, not just the newest commit — an intervening commit you don't mention is still the reviewer's job to catch (#277: it read the full range unprompted and flagged the gap), but under-scoping the ask wastes a round-trip and risks a commit going unmentioned in the eventual disposition record. Both `resolveReviewThread` and filing a follow-up issue may themselves require per-instance user authorization under some permission configurations — treat that as a normal step of the loop, not a stall to route around.
   - **Judgement/style, or a blocking finding carried past the fix budget** — never fix in-run. **Reply a disposition on the thread** naming where it goes (`→ #<issue>` you filed, or a one-line won't-fix reason) via `gh api repos/<o>/<r>/pulls/<n>/comments/<top_comment_databaseId>/replies -f body='…'` (`top_comment_databaseId` = the first comment's `databaseId` from step 2's query), and **leave it UNRESOLVED for the human**. Never `resolveReviewThread` merely to hit zero — resolution is only for confirmed fixes; carried threads stay visible for the merger. It also flows into the brief.
4. **CI red**: read the failed job log (`gh run view --log-failed`), diagnose, dispatch the fix like a blocking finding. Push fixes with plain `git push`; CI re-runs, the loop continues. (New review threads may still appear between beats — from the codex bot, which has no delivery-time guarantee, or from a human reviewer — so re-collect threads every beat regardless of whether a push just happened.)

## Pacing

Self-paced (ScheduleWakeup): ~270s between beats while CI is running — inside the prompt-cache window; stretch to 1200s+ when waiting on something slower (queued runners). Hard cap: **12 beats**.

## Exit — always one of two reports, never a silent stop

- **Green**: CI green AND **every review thread is dispositioned** — bot-resolved (fix confirmed by re-review) or carrying a disposition reply (unresolved, left for the human) — AND every anchorless summary finding triaged. Before reporting, check whether the codex bot has reviewed the **head commit** (newest `reviews` node's `commit.abbreviatedOid` vs the PR head — zero unresolved threads proves nothing about commits the bot hasn't seen; it reviews each push with a ~10–15 min lag and no delivery guarantee). An unreviewed head still exits green, but the report must say so — merging then means accepting a possible post-merge wave (#376: the wave landed 15 minutes before merge, was missed, and cost follow-up PR #377). → report: checks state, threads resolved vs carried (with their issue refs), commits pushed during the watch, and the head-commit review status.
- **Blocked**: fix budget or beat cap exhausted, or CI stuck → report: what is red, what was tried, current hypothesis.
