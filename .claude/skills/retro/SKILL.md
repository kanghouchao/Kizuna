---
name: retro
description: "Retrospect a completed run or session: mine the execution history for friction, land every finding in the real plan — absorbed, remembered, or dropped out loud — and brief the user. Use at dev-loop post-merge cleanup, or when the user asks for a retrospective (复盘) of significant work."
---

# Retro

Input: the execution history already in context (a dev-loop run, or this session's work). When that history is NOT in context — post-merge often lands in a later session or after compaction — reconstruct the friction from the PR's review threads, its commit list, and the issues filed during the run, and say in the brief that the retro ran on reconstructed evidence. The loop optimizes itself through this step — cleanup alone throws the run's evidence away.

**The real plan rule** (Derby): improvement work only counts inside the plan that actually executes — a side list of improvements is where retrospectives go to die. So every finding lands in work that will run, becomes memory, or is dropped out loud; an issue counts as a landing only when it names its consumer — the queued run or trigger event that picks it up.

## Steps

1. **Collect friction.** Sweep the history for: plan deviations the coder reported; QA failures and their root causes; budget spent (which loop, why); gates or hooks that missed or false-fired; review-finding patterns; tooling traps hit along the way. Done when: the friction list is written out — an empty list must be argued from the history, never assumed.
2. **Systemic check.** For each item, search open issues for siblings of the same root cause; recurring across runs = systemic → consolidate into ONE umbrella issue that names the root cause and its consumer, closing the variants into it. Done when: every item is marked local or systemic, and no two open issues share a root cause.
3. **Land every item in exactly one destination:**
   - **Product defect** → the issue tracker IS the product's real plan: confirm its issue was filed during the run; file any missed ones (Japanese, `.github/ISSUE_TEMPLATE/bug.md` structure).
   - **Absorbed improvement** (architecture / workflow) → small config fix: draft the change as a PR now (`.claude` is repo-tracked; skill edits follow `writing-great-skills`; never beyond a draft — the user merges). Larger: a Japanese issue naming its consumer. The next run absorbs at most 1–2 improvement items, chosen by impact AND current capacity — an important item nobody has capacity for is deferred out loud.
   - **Memory** → lessons the repo cannot re-derive (behavioral traps, tool quirks, user rulings); update or delete memories the run disproved.
   - **Dropped** → one-line reason in the brief.
4. **Brief (Chinese).** Every item with its destination — 已修 / issue #N / PR #N / 已记忆 / 放弃+理由. The scorecard: issues opened vs closed since the last retro, counting only closures whose work shipped — closures from folding variants into an umbrella (step 2) are bookkeeping, reported as their own number. A widening gap means the loop is filing, not finishing. Proposed queue placement for anything new — the final ranking is the user's. Done when: no item lacks a destination and the scorecard is stated.
