# Frontend Conventions

For UI work, read [DESIGN.md](DESIGN.md) first. For endpoint additions/changes, read the [API contract](../backend/AGENTS.md#api-contract). These documents own visual behavior and wire rules respectively.

## Architecture

Feature-Sliced Design is checked by Steiger. Dependencies point downward: app → _pages → widgets → features → entities → shared. Import other slices through their index; use relative imports within a slice. Entities do not import each other; compose them in pages. Server-only exports use `index.server.ts`, separate from the client barrel.

- `app/`: thin Next route shells exporting a page. The public root is the exception: it redirects by session and app host before rendering `StoreSitePage`. `(admin)` owns theme wiring; `(public)` has none. Crossing root layouts reloads the page.
- `_app/`: initialization/providers; `_pages/`: scope-prefixed page slices; `widgets/`: composite UI; `features/`: user actions; `entities/`: domain data/types/API; `shared/`: infrastructure and primitives.
- `src/proxy.ts` only runs the route guard in `shared/lib/proxy` for `/admin`, `/store`, `/cast`, `/member`; public pages never pass through it. Public Server Components resolve the store per request from the Host via `resolveHost()` (`shared/api/index.server`) and pass it down as props; the app domain reaches client components through `useAppDomain()`, provided by both root layouts. Cross-cutting invariant tests live in `src/__tests__/`.
- `@/*` resolves to `src/*`; TypeScript and Jest must agree.

## Fetching and store context

Use `shared/lib/hooks` for fetch lifecycles. Pick `useListPage` for paging/applied search, `useCursorList` for queues, `useManagedList` for a whole list, and `useResource` for one value, optional fetching, dependency-driven reload, replacement after writes, or a 404 split. For a plain unparameterized array, either of the last two fits. Failure/loading presentation and retry semantics are defined in [DESIGN.md](DESIGN.md#通知と失敗状態).

There is one admin shell (`widgets/admin-shell`), mounted once by `app/(admin)/(staff)/layout.tsx`, the common parent of `admin/` and `store/` (so crossing between them does not remount it or refetch stores/me/menus; `admin-shell-mount.test.ts` pins this); the cast/member portals sit outside `(staff)` with their own layouts; `/admin/**` and `/store/**` are mount prefixes without authorization meaning; `/auth/**` holds the public login / registration / invitation / LINE callback / initial-setup pages. The shell mounts two seams:

- `StoreContextProvider` / `useStoreContext`: fetches the caller's authorized stores (`GET /stores/me`) once, resolves the current store (last-used cookie, else the first store), writes it to the `current-store-id` cookie, and only then renders `/store/**` children (keyed by the current store so switching remounts the page). The sidebar's context rail is the only switcher: the rail lists the group (when the user sees any group-level menu item, i.e. a path outside `/store/**`) and every authorized store regardless of menu items (a store-level page can be reached from a group-level item, so the store must stay switchable), the panel shows that context's items only (just the dashboard when a store context has no store-level item), and the selected context lives in the sidebar, which the shell never remounts. The rail is hidden when there is only one context. Picking a store calls `switchStore` without navigating; on a group-level page only the panel changes. Entering a store-level page aligns the selected context to the current store (no navigation; going back to a group-level page does not reset it). Store-level pages carry no store id in the URL; they read `currentStoreId` / `currentStore` from the context, and the api client injects `X-Store-ID` from the cookie into every request sent from a `/store` page (API paths carry no prefix to tell store-level endpoints apart; backend handlers that do not take the current store ignore the header). The one exception is a cross-store list on an `/admin` page reading a single row's store-level data (order correction history from `/admin/orders`): that call passes the row's store as an explicit `X-Store-ID`; on `/admin` pages the client injects nothing, so it goes out as given. Do not fetch the same list per consumer.
- `MeProvider` / `useMe`: profile data outside JWT (`display_name`, `email`); update through `setMe` so all consumers observe the response without a refetch. Read authorities/userType from `readTokenClaims()` rather than re-fetching `/me`.

Staff management is one screen (`_pages/admin-staff`, `/admin/staff`) for every staff member; store choices come from the context's own authorized stores because delegated grants must be a subset of the grantor's. Service-identity modals receive their store catalog via props.

Server-side code (the proxy and Server Components) reaches the backend through `backendUrl()` in `shared/lib/backend-url`: `BACKEND_URL` carries the host only (default `http://backend:8080`, the compose service name in every environment), and the endpoint path stays in code. Do not reintroduce env vars that carry an endpoint path — a pinned value in an existing `.env` silently outlives every path rename.

Store paths are assembled only in `shared/lib/store-route` (`storePath(subPath)`). `store-path-invariants.test.ts` catches raw interpolated store paths but not every concatenation; review remains necessary. Login lands everyone on `/admin/dashboard`; the `session-kind` cookie holds the session kind `admin` / `cast` / `member`.

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

<!-- BEGIN:nextjs-agent-rules -->

## This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->
