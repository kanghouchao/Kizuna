---
paths:
  - "frontend/**"
---

# Frontend (TypeScript) Conventions

- **Stack**: TypeScript 5.9, React 19, Next.js 16 (App Router), Jest, ESLint, Prettier
- **Architecture**: Feature-Sliced Design (FSD). The layer structure is machine-checked by **Steiger** in `task lint` / CI.

## FSD structure

```
frontend/src/
├── app/          # Next App Router: thin route shells only — just `export { XxxPage as default } from '@/_pages/...'`.
│                 #   Exception: the root route `/` (app/page.tsx) dispatches templates dynamically based on
│                 #   the cookie-resolved tenant, so it cannot be a thin shell.
├── _app/         # App initialization such as providers (named _app because `app` collides with Next's reserved name)
├── _pages/       # Page slices, named with a scope prefix: central-* / store-* plus login, register, store-site
├── widgets/      # Composite UI such as sidebar, header
├── features/     # Slices per user action: auth-login, tenant-register, ...
├── entities/     # Mirror of the backend domain modules:
│                 #   tenant, user, menu, cast, customer, order, store-profile, system-config
│                 #   each slice = model (types) / api (requests) / index (public API)
└── shared/       # api (apiClient, shared types), lib (navigation, config, proxy), ui (generic components)
```

- **Import through a slice's index (public API)**. Inside a slice, use relative paths. Do not import your own slice via the alias.
- **Layer dependencies point downward only**: app → _pages → widgets → features → entities → shared.
- **Entities must not import each other**. Composition spanning multiple entities (e.g. store-site's storefrontService) is the page layer's responsibility.
- **server-only modules** (those depending on next/headers, etc.) are not exported from the normal index but from a separate `index.server.ts` entry (e.g. serverClient in `shared/api/index.server.ts`).
- **Public storefront templates live at `_pages/store-site/templates/<key>/page.tsx`**. This is the dynamic-import contract keyed by the cookie's templateKey, so do not change the path structure.
- **alias**: `@/*` → `./src/*` (configured in both tsconfig and jest).

## Code Conventions

- **Naming**:
  - Component names: **PascalCase**
  - API-related types (interface/type) and their property names: **snake_case** (matching the backend JSON keys; existing camelCase types are a known mismatch tracked in a separate issue)
  - Internal variables/functions: normal TypeScript conventions, but data coming from the API keeps snake_case
  - Store-side vocabulary uses the Store prefix: StoreProfile*, StoreUserResponse, storeAuthApi, storeMenuApi (see `docs/CONTEXT.md`)
- **Coverage**: Jest threshold of 70% lines/statements. Targets are shared/api, shared/lib, and entities (pages and templates are excluded).
- **Disabling a Steiger rule requires a reason comment in steiger.config.mjs** (typo-in-layer-name / repetitive-naming / insignificant-slice are off by design decision or rule specification).
