# Design System

Design rules for all UI work. Structure follows the DESIGN.md convention (designmd.app): Colors / Fonts / Spacing / Components / Do's and Don'ts. **Any agent implementing or modifying UI MUST read this file first** and, if a frontend-design skill is available in its environment, invoke it before writing markup.

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

### Public storefront (default template)

Defined in `templates/default/theme.css` as CSS custom properties. Current vocabulary: background `#080808` (`--storefront-bg`), gold accent `#C9A84C`, light text `#F8F4F0` at reduced opacities. New color needs = new `--storefront-*` token in theme.css, never inline hex in sections. modern / classic templates (issue #223 Phase 3) get their own `theme.css` token tables; templates may only differ via tokens and page layout, never by forking `_sections/` components.

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

Component states (hover / focus / disabled) must always be styled: hover darkens one step (e.g. `hover:bg-blue-700`), focus uses ring (`focus:ring-blue-500`), disabled uses `disabled:opacity-50`.

## Do's and Don'ts

- **DO** use only the Tailwind classes listed above for admin colors; if a needed semantic is missing, extend THIS file in the same PR.
- **DO** fetch the Figma node via the Figma MCP (`get_design_context` / `get_screenshot`) when a task references a designed screen; match it, then record any new tokens here.
- **DO** keep storefront changes token-driven: template look changes go through `theme.css`, structural blocks through shared `_sections/`.
- **DON'T** introduce raw hex values in admin UI markup.
- **DON'T** use indigo for new admin UI (legacy only, migrate on touch).
- **DON'T** fork or restyle `_sections/` components per template; differences live in tokens and page layout only.
- **DON'T** invent new visual patterns when a component above fits; extend the pattern table instead if genuinely new.
