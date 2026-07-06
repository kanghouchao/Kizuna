# Design System

Design rules for all UI work. Structure follows the DESIGN.md convention (designmd.app): Colors / Fonts / Spacing / Components / Do's and Don'ts. **Any agent implementing or modifying UI MUST read this file first** and, if a frontend-design skill is available in its environment, invoke it before writing markup. (`frontend/CLAUDE.md` points here.)

## Scope: two visual worlds

| World | Source of truth | Where styles live |
|---|---|---|
| **Admin UI** (central + tenant management screens: dashboards, lists, settings forms) | Figma file `5r40p5muNqFxwZLa5itKB8` (page 総合ダッシュボード). Screens not yet designed in Figma are extrapolated from these tokens. | Tailwind semantic classes (default v4 palette — the Figma design uses exactly these values) |
| **Public storefront** (`_pages/store-site/templates/**`) | Template code itself; per-template `theme.css` tokens | `templates/<key>/theme.css` CSS custom properties + shared `_sections/` components |

Never mix the two vocabularies: no gold-serif storefront styling in admin screens, no admin blue/gray cards in storefront templates.

## Colors

### Admin UI (Tailwind semantic classes only — never raw hex)

| Token | Tailwind | Usage rules |
|---|---|---|
| Page background | `bg-gray-50` | App shell behind cards |
| Surface | `bg-white` | Cards, sidebar, header |
| Border | `border-gray-200` | Card and input borders |
| Text primary | `text-gray-900` | Headings, key figures |
| Text secondary | `text-gray-600` | Labels, body |
| Text muted | `text-gray-500` | Hints, "vs 先月"-style annotations |
| **Primary** | `blue-600` (`text-blue-600`, `bg-blue-600`) | CTAs, links, active states, progress fill. **Primary is blue, not indigo** — new screens use blue-600; existing indigo-600 usages are migrated opportunistically in PRs that already touch them (no dedicated recolor PRs) |
| Primary tint | `bg-blue-50` | Active nav background, rank chips |
| Success | `green-600` text / `bg-green-100 text-green-800` pill | Positive trends; 確定 status |
| Warning | `bg-yellow-100 text-yellow-800` pill | 保留 status |
| Danger | `red-600` | Destructive actions, errors |
| Icon chips | `bg-blue-500` / `bg-green-500` / `bg-orange-500` / `bg-purple-500` | Stat-card icons; one hue per metric, white icon |

### Public storefront (three templates)

Each template owns a `templates/<key>/theme.css` that defines the same `--storefront-*` token contract on a `.storefront-<key>` class; the shared `_sections/` read only these tokens — never raw hex/rgba (sections use `var()` for solid colors and `color-mix(in srgb, var(--token) N%, transparent)` for opacities). Templates differ only via token values and page layout, never by forking `_sections/`. New color needs = a new `--storefront-*` token added to **all** template theme.css files in the same PR, never inline hex in sections.

| token | default (dark luxury) | modern (dark vivid) | classic (light) |
|---|---|---|---|
| `--storefront-bg` | `#080808` | `#0b0b12` | `#faf7f2` |
| `--storefront-fg` | `#f8f4f0` | `#f2eff4` | `#2a2a28` |
| `--storefront-accent` | `#c9a84c` gold | `#e64980` rose | `#4e8da6` teal |
| `--storefront-muted` | `#a89880` | `#8a87a0` | `#7a776e` |
| `--storefront-neutral` | `#484848` | `#3a3a48` | `#d8d4cc` |
| `--storefront-subtle` | `#252525` | `#1e1e29` | `#ebe7e1` |
| `--storefront-danger` | `#8b1a2e` | `#8b1a2e` | `#b0453a` |
| `--storefront-bg-deep` | `#050505` | `#07070c` | `#efe8dc` |
| `--storefront-surface-1` | `#0a0a0a` | `#10101a` | `#f4f0e9` |
| `--storefront-surface-2` | `#0d0d0d` | `#13131e` | `#f0ebe2` |
| `--storefront-surface-3` | `#0f0f0f` | `#161622` | `#ece6db` |
| `--storefront-line` | `#2a2a2a` | `#24242f` | `#e2ddd3` |
| `--storefront-bg-glow` | `#130d08` | `#170d14` | `#fffdf8` |
| `--storefront-hairline` | `rgba(255,255,255,0.04)` | `rgba(255,255,255,0.05)` | `rgba(0,0,0,0.06)` |
| `--storefront-font-display` | `'Noto Serif JP', …, serif` | `'Noto Sans JP', …, sans-serif` | `'Noto Serif JP', …, serif` |

`surface-1/2/3` step away from `bg` toward higher contrast (dark templates lighten, classic sinks). `bg-deep` is the Footer band below `bg`; `line` is a weaker border than `neutral`; `bg-glow` is the AgeVerification radial-gradient center; `hairline` is an ultra-thin rule; `subtle` is the faintest near-background text/border tone (legal fine print, copyright line).

## Fonts

- **Admin UI**: system sans stack (Figma uses Inter; Japanese text renders via Noto Sans JP fallback). Weights: bold for headings and key figures, medium for emphasized inline text, regular otherwise. Key figures: 30px bold. Body/labels: 14px.
- **Storefront default**: `'Noto Serif JP', 'Hiragino Mincho Pro', serif` with wide letter-spacing (`tracking-[0.25em]`-class values) for headings/nav; this serif-luxury voice is part of the template identity.

## Spacing

- Admin: content padding 24px (`p-6`); card padding ~25px (`p-6`); gap between cards 24px (`gap-6`); sidebar fixed 256px (`w-64`); header 64px (`h-16`); card radius 10px (`rounded-[10px]`, visually ≈ `rounded-lg`); subtle shadow (`shadow-sm`).
- Storefront: sections manage their own rhythm; follow existing `_sections/` patterns (max-w-7xl containers, px-5 lg:px-10).

## Components (admin)

- **Stat card**: white card; label (`text-gray-600` 14px) → figure (`text-gray-900` 30px bold) → trend row (green-600 delta + gray-500 comparison); colored icon chip top-right (`rounded-[10px] p-3`, 24px white icon).
- **Sidebar nav item**: 40px tall, icon 20px + label 14px. Active: `bg-blue-50 text-blue-600` with a 2px blue-600 edge bar. Inactive: gray-600 text, hover `bg-gray-50`. Groups collapse with chevron.
- **Status pill**: fully rounded, 12px text, `bg-green-100 text-green-800` (確定) / `bg-yellow-100 text-yellow-800` (保留); add hues per new status semantics, always `*-100` bg + `*-800` text.
- **Progress bar**: track `bg-gray-200 h-2 rounded-full`, fill `bg-blue-600`.
- **Ranking row**: 32px circular rank chip (`bg-blue-50 text-blue-600`), name + area line (12px icon + gray-500), right-aligned amount (bold) over count (gray-500).
- **Selectable preview card**: `<label>` wrapping an `sr-only` radio; `rounded-[10px] border p-3 cursor-pointer`. Unselected `border-gray-200 hover:bg-gray-50`; selected `border-blue-600 ring-2 ring-blue-600 bg-blue-50`. Body = thumbnail image (`w-full rounded border border-gray-200`) → name (`text-sm font-medium`, selected `text-blue-600`) → description (`text-xs text-gray-500`); keyboard focus via `has-[:focus-visible]:ring-blue-500`.

Component states (hover / focus / disabled) must always be styled: hover darkens one step (e.g. `hover:bg-blue-700`), focus uses ring (`focus:ring-blue-500`), disabled uses `disabled:opacity-50`.

## Do's and Don'ts

- **DO** use only the Tailwind classes listed above for admin colors; if a needed semantic is missing, extend THIS file in the same PR.
- **DO** fetch the Figma node via the Figma MCP (`get_design_context` / `get_screenshot`) when a task references a designed screen; match it, then record any new tokens here.
- **DO** keep storefront changes token-driven: template look changes go through `theme.css`, structural blocks through shared `_sections/`.
- **DON'T** introduce raw hex values in admin UI markup.
- **DON'T** use indigo for new admin UI (legacy only, migrate on touch).
- **DON'T** fork or restyle `_sections/` components per template; differences live in tokens and page layout only.
- **DON'T** invent new visual patterns when a component above fits; extend the pattern table instead if genuinely new.
