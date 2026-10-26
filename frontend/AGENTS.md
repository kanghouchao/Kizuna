# Frontend Conventions

For UI work, read [DESIGN.md](DESIGN.md) first. For endpoint additions/changes, read the [API contract](../backend/AGENTS.md#api-contract). These documents own visual behavior and wire rules respectively.

## Architecture

Feature-Sliced Design is checked by Steiger. Dependencies point downward: app → _pages → widgets → features → entities → shared. Import other slices through their index; use relative imports within a slice. Entities do not import each other; compose them in pages. Server-only exports use `index.server.ts`, separate from the client barrel.

- `app/`: thin Next route shells exporting a page. The public root dispatches store templates and is the exception. `(admin)` owns theme wiring; `(public)` has none. Crossing root layouts reloads the page.
- `_app/`: initialization/providers; `_pages/`: scope-prefixed page slices; `widgets/`: composite UI; `features/`: user actions; `entities/`: domain data/types/API; `shared/`: infrastructure and primitives.
- `src/proxy.ts` delegates host resolution and routing to `shared/lib/proxy`; cross-cutting invariant tests live in `src/__tests__/`.
- `@/*` resolves to `src/*`; TypeScript and Jest must agree.

## Fetching and store context

Use `shared/lib/hooks` for fetch lifecycles. Pick `useListPage` for paging/applied search, `useCursorList` for queues, `useManagedList` for a whole list, and `useResource` for one value, optional fetching, dependency-driven reload, replacement after writes, or a 404 split. For a plain unparameterized array, either of the last two fits. Failure/loading presentation and retry semantics are defined in [DESIGN.md](DESIGN.md#通知と失敗状態).

There is one admin shell (`widgets/admin-shell`), mounted once by `app/(admin)/(staff)/layout.tsx`, the common parent of `admin/` and `store/` (so crossing between them does not remount it or refetch stores/me/menus; `admin-shell-mount.test.ts` pins this); the cast/member portals sit outside `(staff)` with their own layouts; `/admin/**` and `/store/**` are mount prefixes without authorization meaning; `/auth/**` holds the public login / registration / invitation / LINE callback / initial-setup pages. The shell mounts two seams:

- `StoreContextProvider` / `useStoreContext`: fetches the caller's authorized stores (`GET /stores/me`) once, resolves the current store (last-used cookie, else the first store), writes it to the `current-store-id` cookie, and only then renders `/store/**` children (keyed by the current store so switching remounts the page). Store-level pages carry no store id in the URL; they read `currentStoreId` / `currentStore` from the context, and the api client injects `X-Store-ID` from the cookie into every request sent from a `/store` page (API paths carry no prefix to tell store-level endpoints apart; backend handlers that do not take the current store ignore the header). The one exception is a cross-store list on an `/admin` page reading a single row's store-level data (order correction history from `/admin/orders`): that call passes the row's store as an explicit `X-Store-ID`; on `/admin` pages the client injects nothing, so it goes out as given. A group with one store hides the switcher. Do not fetch the same list per consumer.
- `MeProvider` / `useMe`: profile data outside JWT (`display_name`, `email`); update through `setMe` so all consumers observe the response without a refetch. Read authorities/userType from `readTokenClaims()` rather than re-fetching `/me`.

Staff management is one screen (`_pages/admin-staff`, `/admin/staff`) for every staff member; store choices come from the context's own authorized stores because delegated grants must be a subset of the grantor's. Service-identity modals receive their store catalog via props.

Store paths are assembled only in `shared/lib/store-route` (`storePath(subPath)`). `store-path-invariants.test.ts` catches raw interpolated store paths but not every concatenation; review remains necessary. Legacy `/store/{id}/...` bookmarks are redirected by `routeGuard` to the id-less path with the store written to the cookie. Login lands everyone on `/admin/dashboard`; the `session-kind` cookie holds the session kind `admin` / `cast` / `member`. Legacy `/platform/**` bookmarks are redirected by `routeGuard` to `/auth/**` (login / register / invite / line/callback) or `/admin/**`.

## Public templates and shared UI

- Public pages load `_pages/store-site/templates/<key>/<page>.tsx` through `loadTemplatePage`; unknown keys use the default template's same page. Preserve that dynamic import contract. Pages are page / casts / cast-detail / schedule / menu / about / reservation.
- Shared sections live in `templates/_sections/`, never as a template key. Template directories contain themes and layouts. `entities/store-profile/getTemplateMeta` owns text-slot metadata used by site and settings.
- Import shadcn primitives through `@/shared/ui`. For any shared/ui change, consult DESIGN.md's vendored/authored classification; generated primitives stay frozen.
- Toast call sites use `@/shared/notify`. Direct Base UI toast imports are limited to that module and `shared/ui/toast.tsx`; this also applies to dynamic imports and test mocks not caught by ESLint.

## Code and checks

- Components/types use PascalCase; internal variables/functions follow TypeScript conventions. API **property names** remain snake_case. Store-facing identifiers use Store; the unified account is User.
- PermissionCode in `entities/user/model/types.ts` mirrors the backend enum manually; update both when adding a permission. There is no parity test.
- A disabled Steiger rule needs a Japanese reason in `steiger.config.mjs` (the mandatory-comment exception). Keep `_pages` excessive-slicing suppression scoped to pages.
- Docker lint runs format:check, lint, lint:fsd, typecheck. Run all four for local verification. Coverage and final Taskfile/PR checks are documented in [CONTRIBUTING.md](../CONTRIBUTING.md).
