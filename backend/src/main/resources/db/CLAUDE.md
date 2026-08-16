# Database Migration Rules (Liquibase)

**Pre-launch mode is in effect: the schema is a single squashed baseline, and schema changes
edit that baseline in place.** This file is the normative source for editing anything under
`db/changelog/`; `backend/CLAUDE.md` only summarizes it.

## The one rule that differs from normal Liquibase practice

There is no production database yet. Until the first production release:

- **Do NOT create a new `releases/<version>/` directory.**
- **Do NOT add incremental changesets** (`addColumn` / `dropColumn` / data backfills) for a
  schema change.
- **DO edit the baseline changesets under `releases/v0.1.0/` directly** so every table is
  declared in its terminal shape: add the column to the `createTable`, add the FK/index next to
  the table's other FKs/indexes, update the seed rows to match.

Every runtime database is rebuildable: integration tests and e2e run on tmpfs and are recreated
per run; the persistent dev DB is recreated with the steps below. Incremental changesets would
only preserve data that no environment needs preserved, while burying the terminal schema under
ALTER noise.

**When the first production release ships, this rule flips** to standard append-only Liquibase
(new `releases/<version>/` per change, never edit an applied changeset). Flipping is a deliberate
decision by the repository owner — do not flip it yourself.

## After editing the baseline: the dev DB must be recreated

Editing an applied changeset changes its checksum, so the persistent dev DB (volume
`kizuna_db-data`) fails startup validation afterwards. Recreate the **database**, never the
volume (`docker volume rm` / `compose down -v` are guardrail-forbidden). The dev stack pins
`container_name`s (`database`, `backend`), so this works from any cwd; credentials come from
the container's own env because `.env` may override the compose defaults:

```bash
task build service=backend   # changesets are baked into the image at build time
docker stop backend
docker exec database sh -c 'psql -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE $POSTGRES_DB" -c "CREATE DATABASE $POSTGRES_DB OWNER $POSTGRES_USER"'
task up
```

Integration tests / e2e need nothing: their stacks start from an empty DB every run.

## Structure

- `releases/v0.1.0/` is split into `platform/` (platform-wide tables), `store/` (store-scoped
  tables), and `seed/` (initial data). A table's schema, FKs, and indexes live together in its
  aggregate area's file — one file may hold several tables of one area (`02-cast.yaml` holds
  casts, field definitions, and invitations; `03-customer.yaml` holds customers and merge
  history). Changeset ids carry the file's number prefix (`store-v0100-003-…` lives in
  `store/03-…`).
- `releases/v0.1.0/master.yaml` includes files in **FK dependency order**. Platform tables that
  reference `t_orders` (order attribution, receipt tokens, point ledger) are included **after**
  the store layer — placement in `platform/` states ownership, include order states dependency.
- `reconcile/` is not a release: `runAlways` changesets that re-derive rows from code
  declarations (permission catalogue from `PermissionCode` / `SystemRole`). Its include stays
  **last** in `db.changelog-master.yaml`; `ChangelogOrderTests` pins this. Permission additions
  need only the enum — never hand-seed permission rows.

## Rules that keep the baseline correct

- **Constraint and index names are load-bearing.** `DbConstraint` maps them to business
  exceptions and `DbConstraintLiteralTests` greps this tree for every enum member's name.
  Keep the `pk_` / `uq_` / `fk_` / `idx_` / `ck_` naming and never rename a constraint without
  checking `DbConstraint`.
- **Every FK declares an explicit `onDelete`.** The choice is a design decision (CASCADE for
  store-owned data, SET NULL for audit trails that outlive the referenced row, RESTRICT/NO ACTION
  to refuse the deletion) — copy the reasoning style of the existing comments.
- **An FK whose refusal is mapped to a business exception must be `NO ACTION`, not `RESTRICT`.**
  PostgreSQL reports a RESTRICT violation as SQLSTATE 23001 and Hibernate extracts no constraint
  name from it, so the `DbConstraint` mapping never fires and the refusal surfaces as a 500.
  `DbConstraintLiteralTests` enforces this for every enum member. The two are not synonyms —
  RESTRICT can never be deferred, NO ACTION can if declared DEFERRABLE — but every FK here is
  non-deferrable, and a parent CASCADE that removes the children in the same statement (store
  deletion) behaves the same under both, measured on PG 18 (`StoreDeletionCascadeIT`). What
  actually differs is how the violation is reported.
- **Partial (predicate) indexes and CHECK constraints use raw `sql` changes** — Liquibase OSS
  cannot express them declaratively.
- **Seeds take no explicit ids** (IDENTITY sequences must not collide with seeded rows), with
  one exception: `t_store_profiles.id` is app-generated Snowflake text, so its demo rows pin
  `"1"` / `"2"`. FKs in seeds resolve via natural-key subselects.
- **Seed rows state values explicitly when the column default is not the intended value**
  (demo stores are `ACTIVE` while the column default is `PREPARING`). A NOT NULL column without
  a DB default must be provided by every seed row that touches the table.
- **Demo data lives only in `seed/05-demo.yaml` behind `contextFilter: demo`.** The application
  default is `production` (no demo data); dev/integration/e2e opt in via `LIQUIBASE_CONTEXTS`.
- **Changelog parameters enter checksums** (`initialAdminPasswordHash`,
  `demoUserPasswordHash`): they set the first-deployment value only; rotating them against an
  existing DB fails validation. Pre-launch this just means "recreate the dev DB after changing
  them".
- This file is excluded from the jar by `processResources` in `build.gradle` (the glob covers
  any `CLAUDE.md` under resources) — keep the exclusion in step if you rename this file.
