# E2E Conventions (playwright-bdd)

Human-facing docs (how to run, the Japanese Gherkin keyword table, local iteration without Docker) live in [`README.md`](./README.md) — read it for anything not covered here.

- **Structure**: `features/**/*.feature` (scenarios) + `steps/**/*.ts` (step definitions) + `playwright.config.ts` (`defineBddConfig`).
- **Scenarios are Japanese Gherkin**: every `.feature` starts with `# language: ja` and uses `機能` / `シナリオ` / `前提` / `もし` / `ならば`. **Step definitions stay on the English `createBdd()` API** (`Given` / `When` / `Then`) and match the Japanese scenario lines by regex or cucumber expression.
- **Shared API helpers belong in `steps/store-api.ts`** — login (`loginAsStoreAdmin`, `loginViaUiAndEnterStore`), the store-context headers (`X-Role` / `X-Store-ID`), and cast / shift / store-config CRUD are already there. Extend that module instead of hand-rolling requests inside a steps file.
- **`After` restore hooks must be sentinel-guarded**: record whether the snapshot step actually succeeded, and skip the restore when it did not. A scenario that fails *before* the snapshot would otherwise PUT an empty object and wipe live data (see the `custom_texts` case in `steps/store-settings.steps.ts`). Restores are best-effort — swallow their errors so they cannot mask the real failure.
- **Keep seed constants in `store-api.ts`, not in scenario flow**: that module deliberately pins the store-context header ids and the seeded login (`STORE1_ID`, `ADMIN_EMAIL`). Everywhere else, read `storeId` from the landing URL (`loginViaUiAndEnterStore` returns it) and pass it through the scenario instead of restating a seed id.
- **Run from the repo root**: `task e2e`, which needs `jq` on the host (see README for what it does and where artifacts land).
- **E2E does not run in CI** — running it before opening a PR is the author's responsibility.
