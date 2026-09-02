# Frontend (TypeScript) Conventions

- **UI work**: read [`DESIGN.md`](./DESIGN.md) FIRST (design system: colors/fonts/spacing/components); if a frontend-design skill is available, invoke it before writing markup.
- **Architecture**: Feature-Sliced Design (FSD). The layer structure is machine-checked by **Steiger** in `task lint` / CI.

## FSD structure

```
frontend/src/
├── app/          # Next App Router: thin route shells only — just `export { XxxPage as default } from '@/_pages/...'`.
│                 #   Split into two root layouts: `(admin)` (console + cast / member portals, owns the theme wiring)
│                 #   and `(public)` (storefront + auth screens, no theme wiring) — crossing them is a full
│                 #   page load by design, so the theme blocking script always runs first.
│                 #   Exception: the root route `/` (app/(public)/page.tsx) dispatches templates dynamically
│                 #   based on the cookie-resolved store, so it cannot be a thin shell.
├── _app/         # App initialization such as providers (named _app because `app` collides with Next's reserved name)
├── _pages/       # Page slices, named with a scope prefix: platform-* / store-* plus cast-invite, cast-portal, member-register, member-portal, store-site
│                 #   store-entry is the store console's invisible entry point: it resolves the store and the
│                 #   menu-derived landing page, then redirects. It has no route of its own to link to, and no UI
│                 #   beyond the failure and dead-end states it names in place instead of redirecting.
├── widgets/      # Composite UI such as sidebar, header
├── features/     # Slices per user action: platform-login, staff-management, ...
├── entities/     # Mirror of the backend domain modules:
│                 #   store, user, menu, cast, customer, order, store-profile, system-config, shift, member, point, benefit-rule
│                 #   each slice = model (types) / api (requests) / index (public API)
└── shared/       # api (apiClient, shared types), lib (navigation, config, proxy), ui (shadcn/ui barrel + hand-written generics),
                  #   notify (the toast severity tiers — call sites write the meaning, never the colour/duration/icon;
                  #   importing `@base-ui/react/toast` directly is ESLint-banned — the only whitelisted files are
                  #   this slice's index and the `shared/ui/toast.tsx` renderer; everything else goes through notify)
```

Outside the layers, at `src/` root: `styles/` (global CSS not owned by a slice), `proxy.ts` + `proxy.test.ts` (the Next proxy entry — Host-based store/platform dispatch, delegating to `shared/lib/proxy`), and `__tests__/` (cross-cutting invariant tests).

- **Import through a slice's index (public API)**. Inside a slice, use relative paths. Do not import your own slice via the alias.
- **Layer dependencies point downward only**: app → _pages → widgets → features → entities → shared.
- **Entities must not import each other**. Composition spanning multiple entities (e.g. store-site's storefrontService) is the page layer's responsibility.
- **server-only modules** (those depending on next/headers, etc.) are not exported from the normal index but from a separate `index.server.ts` entry (e.g. serverClient in `shared/api/index.server.ts`).
- **Fetch lifecycles come from `shared/lib/hooks`** — never hand-write the in-flight guard, the clear-on-failure, or the 404 split. Pick by the lifecycle the screen needs: `useListPage` (paging + applied search criteria), `useCursorList` (cursor-paged work queue), `useManagedList` (a list fetched in one go), `useResource` (one fetch of one value — a detail page's body, a `Select`'s options, a sub-region's history or statistics — and the only one carrying the 404 split, an optional fetch, deps-driven refetch, and value replacement after a write). The last two overlap for a plain unparameterised array, and either is correct there; reach for `useResource` when one of its extra affordances is in play. The rules the call site owes are in [`DESIGN.md`](./DESIGN.md) under "Notifications and failure states".
- **`shared/ui` is the shadcn/ui layer on Base UI** (ADR 0005): import primitives through the `@/shared/ui` barrel. The vendored primitives are frozen — which files are vendored vs hand-written, and the restyle rules, live in [`DESIGN.md`](./DESIGN.md). This applies to _any_ change touching `shared/ui`, not only UI work.
- **Public storefront templates live at `_pages/store-site/templates/<key>/<page>.tsx`** where `<page>` is one of `page` (TOP) / `casts` / `cast-detail` / `schedule` / `menu` / `about` / `reservation`:
  - This is the dynamic-import contract keyed by the cookie's templateKey (dispatched via `loadTemplatePage`, which falls back to the default template's same page for unknown keys), so do not change the path structure.
  - Top-level public routes under `app/(public)/` (`/casts`, `/schedule`, `/menu`, `/about`, `/reservation`) are thin shells rendering `StoreSitePage`.
  - Shared section components live in `templates/_sections/` (an underscore dir, never a template key); each template dir holds only its `theme.css` and page layouts.
  - Template text-slot metadata lives in `entities/store-profile` (`getTemplateMeta`) because both store-site and store-settings consume it.
- **Store context is a single seam**: `entities/user`'s `StoreContextProvider` / `useStoreContext` (mounted in both console layouts, `src/app/(admin)/platform/layout.tsx` and `src/app/(admin)/store/layout.tsx`) resolves the _current user's switchable stores_ **once** — the `storeBridge` qualification is read synchronously from the token claims (`readTokenClaims()` in `shared/lib`), then `stores()` is fetched only when it is true (otherwise the list is empty by design). Display/routing data that lives in the JWT claims (authorities / userType / storeBridge) is read via `readTokenClaims()`, never re-fetched from `/platform/me`; `me()` remains for data outside the token — `console` at login reads it directly, while `display_name` / `email` for the header and the store account page flow through the sibling `MeProvider` seam (`useMe()`, mounted next to `StoreContextProvider` in the same layouts), whose `setMe` propagates profile updates to every consumer without a refetch. Consume that through `useStoreContext()`; do not re-fetch it per component. The administrative **catalog** stays a direct `platformAuthApi.stores()` call (fetched once by staff management's list page and shared with its modals/pickers via props): it is the full store registry behind `STORE_MANAGE`, while the seam holds the caller's own authorization-scoped switchable list — a grantor assigns stores they cannot themselves switch into. The **store console's** staff management (`_pages/store-staff`) takes the opposite side on purpose: its store picker reads the seam, because there the grantable set _is_ the actor's own scope (the backend refuses any store outside it — ADR 0020's G2).
- **Store path assembly lives only in `shared/lib/store-route`** (`storePath` / `storeEntryPath` / `resolveStoreHref` / `replaceStoreIdInPath`). The negative invariant test `src/__tests__/store-path-invariants.test.ts` fs-scans `src/` and rejects the `/store/${...}` template-literal form; it does not catch concatenation or an interpolated base, so treat it as a backstop, not a proof.
- **alias**: `@/*` → `./src/*` (configured in both tsconfig and jest).

## Code Conventions

- **Naming**:
  - Component names: **PascalCase**
  - API-related types (interface/type) and their property names: **snake_case** (matching the backend JSON keys; existing camelCase types are a known mismatch tracked in a separate issue)
  - Internal variables/functions: normal TypeScript conventions, but data coming from the API keeps snake_case
  - Store-side vocabulary uses the Store prefix: StoreProfile*. The unified account type is PlatformUser (`entities/user/api/platform.ts`)
- **Coverage**: Jest thresholds of 70% lines/statements, 60% branches, 55% functions. Targets are shared/api, shared/lib, and entities (pages and templates are excluded).
- **`task lint` is four checks**: `npm run format:check` (Prettier) + `npm run lint` (ESLint) + `npm run lint:fsd` (Steiger) + `npm run typecheck` (tsc) — the Dockerfile lint stage runs all of them in that order, so run all four locally before pushing.
- **`PermissionCode` in `entities/user/model/types.ts` is a hand-maintained copy of the backend enum.** Add new codes there when the backend gains one; nothing checks parity.
- **Disabling a Steiger rule requires a reason comment in steiger.config.mjs** (typo-in-layer-name / repetitive-naming / insignificant-slice are off by design decision or rule specification; excessive-slicing is off for `_pages/**` only — one screen = one slice there).
