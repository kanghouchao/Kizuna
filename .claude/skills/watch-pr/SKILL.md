---
name: watch-pr
description: "Watch a GitHub PR to green: poll CI checks and triage review comments in a self-paced loop until CI passes and every finding is consumed. Use after opening or pushing to a PR, or standalone via `/loop /watch-pr <n>`."
---

# Watch PR

A closed loop over one PR: beats, feedback gates, exit conditions. Input: a PR number (default: the current branch's PR via `gh pr view --json number`). Never merge.

## Each beat

1. **Probe**: `gh pr checks <n>` — judge by exit code / structured output only.
2. **Collect**: `gh pr view <n> --json reviews,comments` — findings not yet triaged (claude-review posts as comments; humans may too).
3. **Triage every new finding**:
   - **Blocking** (correctness, security, data loss, spec violation) — inside a dev-loop run: dispatch verbatim to the run's coder via SendMessage, re-run QA if runtime code changed (spends the shared budget). Standalone: report the finding verbatim to the user and wait for their call.
   - **Judgement/style** — record for the exit report; never fix in-run.
4. **CI red**: read the failed job log (`gh run view --log-failed`), diagnose, dispatch the fix like a blocking finding. Push fixes with plain `git push`; CI re-runs, the loop continues.

## Pacing

Self-paced (ScheduleWakeup): ~270s between beats while CI is running — inside the prompt-cache window; stretch to 1200s+ when waiting on something slower (queued runners). Hard cap: **12 beats**.

## Exit — always one of two reports, never a silent stop

- **Green**: CI green AND every finding triaged → report: checks state, findings fixed vs carried, commits pushed during the watch.
- **Blocked**: fix budget or beat cap exhausted, or CI stuck → report: what is red, what was tried, current hypothesis.
