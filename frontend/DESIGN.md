# Design System

Design rules for all UI work. Structure follows the DESIGN.md convention (designmd.app): Colors / Fonts / Spacing / Components / Admin restyle rules / Do's and Don'ts. **Any agent implementing or modifying UI MUST read this file first** and, if a frontend-design skill is available in its environment, invoke it before writing markup. (`frontend/CLAUDE.md` points here.)

## Scope: three visual worlds

| World                                                                                                                                                                                             | Source of truth                                                                                        | Where styles live                                                                                                                      |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------- |
| **Admin UI** (platform + store management screens: dashboards, lists, settings forms)                                                                                                             | `src/app/globals.css` token layer + the vendored shadcn/ui primitives in `src/shared/ui` (Radix-based) | shadcn primitives composed with Tailwind token classes (`bg-background` / `text-muted-foreground` / …); raw palette classes are banned |
| **Public storefront** (`_pages/store-site/templates/**`)                                                                                                                                          | Template code itself; per-template `theme.css` tokens                                                  | `templates/<key>/theme.css` CSS custom properties + shared `_sections/` components                                                     |
| **Auth screens** — exactly the pages rendered inside `AuthLayout`: `_pages/platform-login` and `_pages/cast-invite`, with their `features/platform-login` and `features/cast-invite-accept` forms | `src/styles/auth.css` (the Midnight Atelier look)                                                      | `auth.css` classes; outside the admin token contract                                                                                   |

Never mix the vocabularies: no gold-serif storefront styling in admin screens, no admin cards in storefront templates, and no admin token restyling of the auth screens.

Membership in the auth world is decided by `AuthLayout`, which is the only importer of `auth.css` — not by a slice's name. `features/password-change` in particular is **admin**, not auth: it is embedded in the account settings pages of both consoles (`_pages/store-settings` and `_pages/platform-settings`) and never appears under `AuthLayout`, so it is restyled with the admin tokens. Because two page slices host it, it belongs to the **`store-settings`** restyle ticket — the console whose `AccountPage` carries the surrounding form. The `platform-settings` ticket renders it but does not edit it.

## Colors

### Admin UI (token classes only — never raw palette classes, never raw hex)

The token layer lives in `src/app/globals.css`: `:root` / `.dark` oklch values exposed to Tailwind through `@theme inline`. Always name the semantic, never the hue — that is what makes both light and dark modes follow without per-screen edits.

| Token                 | Class examples                                                 | Usage rules                                                                     |
| --------------------- | -------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| Page background       | `bg-background`                                                | App shell behind cards                                                          |
| Surface               | `bg-card` (+ `text-card-foreground`)                           | Cards, header (the sidebar is a separate open question — see the mapping notes) |
| Border                | bare `border` (the base layer already applies `border-border`) | Card, input and divider borders                                                 |
| Text primary          | `text-foreground`                                              | Headings, key figures                                                           |
| Text secondary        | `text-muted-foreground`                                        | Labels, body, hints, "vs 先月"-style annotations                                |
| Muted surface         | `bg-muted`                                                     | Progress-bar tracks, table heads, inert fills                                   |
| **Primary**           | `bg-primary text-primary-foreground`, `text-primary`           | CTAs, links, active states, progress fill (blue-600 in both modes)              |
| Primary tint          | `bg-primary/10`                                                | Active nav background, rank chips                                               |
| Destructive           | fill `bg-destructive`, text `text-destructive-strong`          | Delete actions, validation errors, 却下 / NG status                             |
| Success               | fill `bg-success`, text `text-success-strong`                  | Positive trends, 確定 / 有効 / 在籍 / 承認 status                               |
| Warning               | fill `bg-warning`, text `text-warning-strong`                  | 保留 / 申請中 status, attention icons                                           |
| Decorative categories | `bg-chart-1` … `bg-chart-5`                                    | Stat-card icon chips, 指名 chips — category hues that carry no state semantics  |

`destructive` is the only danger vocabulary; there is no `--danger`. If a genuinely new state semantic appears, extend `globals.css` **and** this table in a dedicated PR rather than reaching for a raw hue.

#### Each state semantic has a fill token and a text token

The base token (`--success` / `--warning` / `--destructive`) is a **fill**: a saturated mid hue meant to sit behind something, or to color an icon or a border. It is too light to read as small text on a pale surface — `text-success` on a white card is only 3.22:1.

So each semantic also has a `-strong` variant, which is the colour to use **whenever the semantic is rendered as text**:

| Use                                  | Class                                                 |
| ------------------------------------ | ----------------------------------------------------- |
| Text (on a card, or on a `/10` tint) | `text-success-strong` / `-warning-` / `-destructive-` |
| Filled surface                       | `bg-success` + `text-success-foreground`              |
| Icon, border, bar, dot               | `bg-success` / `text-success` / `border-success`      |

(`text-destructive` on its own remains valid where a shadcn primitive emits it — `FormMessage` does — because destructive on a card clears AA at 4.76:1. Everywhere you write the class yourself, prefer `-strong`.)

In dark mode the `-strong` value equals the base value: the tint sits on a dark surface there, so the mid hue is already the readable one. The split only does work in light mode.

##### Contrast evidence

Ratios are WCAG relative-luminance figures computed from the oklch values in `globals.css`, with the `/10` tint composited over the surface. Each row compares the recipe against the pre-shadcn combination it replaces, so that the migration cannot darken anyone's day:

| Combination                                 | Replaces                        | Before | After    |
| ------------------------------------------- | ------------------------------- | ------ | -------- |
| `bg-success/10 text-success-strong`         | `bg-green-100 text-green-800`   | 6.45   | **8.10** |
| `bg-warning/10 text-warning-strong`         | `bg-yellow-100 text-yellow-800` | 6.40   | **8.15** |
| `bg-destructive/10 text-destructive-strong` | `bg-red-100 text-red-800`       | 6.86   | **8.42** |
| `text-success-strong` on a card             | `text-green-600`                | 3.22   | **9.07** |
| `bg-success text-success-foreground`        | `bg-green-500 text-white`       | 2.22   | **6.18** |
| `bg-warning text-warning-foreground`        | `bg-yellow-400 text-yellow-900` | 5.54   | **6.23** |

Every recipe clears the 4.5:1 AA bar for normal-size text, so **no size or weight condition is attached to any of them**. Dark mode is checked the same way and is not the binding case (8.35 / 8.50 / 5.39 for the three tints, 11.20 / 11.58 for the two fills).

The success fill deliberately changes appearance: its foreground moves from white to the dark foreground. The white-on-green combination it replaces measured 2.22:1, which fails at every text size, so it was not a contract worth preserving.

#### Legacy → token mapping (restyle sweep)

Screens still carrying pre-shadcn classes are converted with this table.

**Substitute the primitive before reaching for this table.** Most legacy class strings belong to hand-built buttons, labels, inputs and tables; swapping in `Button` / `Label` / `Input` / `Table` deletes the whole string rather than mapping it. In the files migrated so far (`store-orders/ui/OrderForm.tsx` and the `store-customers` slice), every occurrence of `text-gray-700` and `hover:bg-gray-50` inside them disappeared this way and none was replaced by a token. Files those PRs did not touch still carry the legacy classes — `store-orders/ui/OrdersPage.tsx` is one. Only the classes that survive on bare elements need the table below.

The table is **not exhaustive** — it covers the recurring cases, not every class in the codebase. If something you are converting is not listed, do not guess: raise it in the PR so the answer is recorded here once, for everyone.

| Legacy                                                 | Token                                                                                                                                         |
| ------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `text-gray-900` / `-800`                               | `text-foreground`                                                                                                                             |
| `text-gray-700`                                        | **by role — see below**                                                                                                                       |
| `text-gray-600` / `-500` / `-400`                      | `text-muted-foreground`                                                                                                                       |
| `bg-gray-50` (page) / `bg-white` (surface)             | `bg-background` / `bg-card`                                                                                                                   |
| `bg-white/90` (sticky footer backdrop)                 | `bg-card/90`                                                                                                                                  |
| `hover:bg-gray-50`                                     | `hover:bg-muted`                                                                                                                              |
| `border-gray-200` / `-300` / `-100`                    | bare `border` (or `border-border`)                                                                                                            |
| `divide-gray-200`                                      | bare `divide-y` — the base layer already colors it                                                                                            |
| `bg-gray-200` track                                    | `bg-muted`                                                                                                                                    |
| `bg-gray-100 text-gray-800` (neutral/無効 pill)        | `bg-muted text-muted-foreground`                                                                                                              |
| `placeholder-gray-500`                                 | drop it — `Input` already ships the placeholder color                                                                                         |
| `shadow-indigo-200` / `shadow-indigo-900/20`           | drop the tint; keep the plain elevation (`shadow-sm` etc.)                                                                                    |
| `blue-600` / `indigo-600`                              | `primary`                                                                                                                                     |
| `bg-blue-50`                                           | `bg-primary/10`                                                                                                                               |
| `text-red-600` / `bg-red-100 text-red-800`             | `text-destructive-strong` / `bg-destructive/10 text-destructive-strong` — on an icon, `text-destructive`                                      |
| `text-green-600` / `bg-green-100 text-green-800`       | `text-success-strong` / `bg-success/10 text-success-strong` — on an icon, `text-success`                                                      |
| `text-amber-600` / `bg-yellow-100 text-yellow-800`     | `text-warning-strong` / `bg-warning/10 text-warning-strong` — on an icon, `text-warning`                                                      |
| `bg-green-500 text-white` (確定 shift bar)             | `bg-success text-success-foreground` (foreground is now dark)                                                                                 |
| `bg-yellow-400 text-yellow-900` (未確定 shift bar)     | `bg-warning text-warning-foreground`                                                                                                          |
| Decorative chips blue / green / orange / purple / pink | `chart-1` … `chart-5` — **see the recipe below**                                                                                              |
| any `slate-*` (sidebar)                                | **do not map — see below**                                                                                                                    |
| Weekend `text-red-500` (Sun) / `text-blue-500` (Sat)   | `text-destructive` / `text-primary` — deliberate base exception: the calendar header is colour-coding, and both clear AA anyway (4.76 / 5.26) |
| Now marker `bg-red-500`                                | `bg-destructive`                                                                                                                              |
| Coverage bar `bg-blue-500/80`                          | `bg-primary/80`                                                                                                                               |
| Hand-written `focus:ring-blue-500` etc.                | drop it — the primitives carry their own focus ring                                                                                           |

##### `text-gray-700` is decided by role, not by class

It is the second most common legacy class and it has no single correct token: it sits between `gray-900` (headings) and `gray-500/600` (annotations), so either destination passes the review grep. Guessing per-file is what makes parallel slices diverge. Decide by what the text **does**:

- Text the reader consumes as substance — record values, list items, row labels, body copy → `text-foreground`
- Text that annotates something else — counts, hints, helper text, timestamps, "N 件中 x-y を表示" → `text-muted-foreground`

Most occurrences never reach this rule because they sit on `<label>` and secondary-button class strings that the `Label` / `Button` primitives replace outright.

##### Decorative chip recipes

The `chart-*` tokens are category hues with **no `-foreground` pair**, so the solid recipe (`bg-<token> text-<token>-foreground`) does not apply to them. Pick any hue that keeps sibling categories distinct, and use:

- **Tint (chips, pills, icon chips)**: `bg-chart-3/10 text-chart-3`.
- **Solid**: not available for chart hues. A solid colored element that the user can click is an action, not a category — use `Button` (which is `primary`), as with the dashboard's report button. A non-interactive solid category block is a new pattern: raise it rather than inventing a foreground.
- The neutral counterpart of a category chip (e.g. the フリー fallback beside a 指名 chip) is `bg-muted text-muted-foreground`.

##### The sidebar is deliberately left undecided

`widgets/sidebar/ui/Sidebar.tsx` is a **dark** surface (`bg-slate-800 text-white`, `border-slate-700`, active `bg-indigo-600 text-white`) and the only slate consumer in the admin UI. It contradicts the Sidebar nav item entry in Components above, which describes a light `bg-primary/10 text-primary` treatment.

**Do not mechanically map slate → muted.** That yields a half-tokenized surface that is neither the current dark design nor the documented light one. The two coherent options both change something a class-level mapping cannot decide:

- Keep it dark: map onto the `--sidebar-*` token family, whose values in `:root` are currently light — so keeping the dark look means changing those token values in `globals.css`, a shared file and therefore its own PR.
- Make it light: this is not a class mapping at all but a rebuild against the Sidebar nav item spec in Components, and it visibly changes the app's most prominent chrome.

This is a visual product decision, so it belongs to the sidebar slice's owner, not to the agent converting it. Until it is made, leave the sidebar alone.

One-off domain colors (now marker, weekend, coverage bar, category chips) intentionally reuse existing tokens instead of gaining dedicated ones: a token with a single consumer is dead weight.

### Public storefront (three templates)

Each template owns a `templates/<key>/theme.css` that defines the same `--storefront-*` token contract on a `.storefront-<key>` class; the shared `_sections/` read only these tokens — never raw hex/rgba (sections use `var()` for solid colors and `color-mix(in srgb, var(--token) N%, transparent)` for opacities). Templates differ only via token values and page layout, never by forking `_sections/`. New color needs = a new `--storefront-*` token added to **all** template theme.css files in the same PR, never inline hex in sections.

| token                       | default (dark luxury)       | modern (dark vivid)             | classic (light)             |
| --------------------------- | --------------------------- | ------------------------------- | --------------------------- |
| `--storefront-bg`           | `#080808`                   | `#0b0b12`                       | `#faf7f2`                   |
| `--storefront-fg`           | `#f8f4f0`                   | `#f2eff4`                       | `#2a2a28`                   |
| `--storefront-accent`       | `#c9a84c` gold              | `#e64980` rose                  | `#4e8da6` teal              |
| `--storefront-muted`        | `#a89880`                   | `#8a87a0`                       | `#7a776e`                   |
| `--storefront-neutral`      | `#484848`                   | `#3a3a48`                       | `#d8d4cc`                   |
| `--storefront-subtle`       | `#252525`                   | `#1e1e29`                       | `#ebe7e1`                   |
| `--storefront-danger`       | `#8b1a2e`                   | `#8b1a2e`                       | `#b0453a`                   |
| `--storefront-bg-deep`      | `#050505`                   | `#07070c`                       | `#efe8dc`                   |
| `--storefront-surface-1`    | `#0a0a0a`                   | `#10101a`                       | `#f4f0e9`                   |
| `--storefront-surface-2`    | `#0d0d0d`                   | `#13131e`                       | `#f0ebe2`                   |
| `--storefront-surface-3`    | `#0f0f0f`                   | `#161622`                       | `#ece6db`                   |
| `--storefront-line`         | `#2a2a2a`                   | `#24242f`                       | `#e2ddd3`                   |
| `--storefront-bg-glow`      | `#130d08`                   | `#170d14`                       | `#fffdf8`                   |
| `--storefront-hairline`     | `rgba(255,255,255,0.04)`    | `rgba(255,255,255,0.05)`        | `rgba(0,0,0,0.06)`          |
| `--storefront-font-display` | `'Noto Serif JP', …, serif` | `'Noto Sans JP', …, sans-serif` | `'Noto Serif JP', …, serif` |

`surface-1/2/3` step away from `bg` toward higher contrast (dark templates lighten, classic sinks). `bg-deep` is the Footer band below `bg`; `line` is a weaker border than `neutral`; `bg-glow` is the AgeVerification radial-gradient center; `hairline` is an ultra-thin rule; `subtle` is the faintest near-background text/border tone (legal fine print, copyright line).

## Fonts

- **Admin UI**: system sans stack; Japanese text renders via the Noto Sans JP fallback. Weights: bold for headings and key figures, medium for emphasized inline text, regular otherwise. Key figures: 30px bold. Body/labels: 14px.
- **Storefront default**: `'Noto Serif JP', 'Hiragino Mincho Pro', serif` with wide letter-spacing (`tracking-[0.25em]`-class values) for headings/nav; this serif-luxury voice is part of the template identity.

## Spacing

- Admin: content padding 24px (`p-6`); card padding ~25px (`p-6`); gap between cards 24px (`gap-6`); sidebar fixed 256px (`w-64`); header 64px (`h-16`); card radius `rounded-lg` (= `var(--radius)`, 0.5rem); subtle shadow (`shadow-sm`).
- Storefront: sections manage their own rhythm; follow existing `_sections/` patterns (max-w-7xl containers, px-5 lg:px-10).

## Components (admin)

Primitives come from `@/shared/ui` (the barrel over the vendored shadcn components). This section records **which primitive to use and how to compose it** — never a restatement of the styling already baked into the primitive.

- **Buttons**: `Button`. `default` = primary CTA, `outline` = secondary, `ghost` + `size="icon-sm"` = in-row actions, `destructive` = delete. Render links with `asChild` wrapping the anchor.
- **Form controls**: `Input` / `Textarea` / `Select` / `Checkbox` / `Switch` / `RadioGroup` / `Label`, wired per the form pattern below.
- **Modal (centered dialog)**: `Dialog` (`DialogContent` / `DialogHeader` / `DialogTitle` / `DialogFooter`). Tall forms add `max-h-[calc(100vh-2rem)] overflow-y-auto` on the content so the modal scrolls internally instead of overflowing the viewport (precedent: `StaffCreateModal`). Precedent for the plain case: `ShiftFormModal`.
- **Drawer (side-slide dialog)**: `Sheet` with `side="right"` for record-scoped edit forms opened from a list row. Never re-create a drawer by re-positioning `DialogContent`.
- **Combobox (searchable select)**: `Popover` + `Command` (`CommandInput` / `CommandList` / `CommandEmpty` / `CommandItem`).
- **Table**: shadcn `Table` inside a `Card`. Precedent: `CustomersPage`.
- **Tabs**: shadcn `Tabs`. Precedent: `ShiftsPage`.
- **Loading placeholder**: `Skeleton` sized with layout classes, never a hand-rolled `animate-pulse` block.
- **Status pill**: `Badge variant="outline"` plus one tint recipe — `border-transparent bg-success/10 text-success-strong` (確定) / `bg-warning/10 text-warning-strong` (保留) / `bg-destructive/10 text-destructive-strong` (却下 / NG). Precedent: `CustomersPage`.
- **Stat card**: `Card`; label (`text-muted-foreground` 14px) → figure (`text-foreground` 30px bold) → trend row (`text-success-strong` delta + `text-muted-foreground` comparison); category icon chip top-right (`rounded-lg p-3`, 24px icon, `bg-chart-*`).
- **Sidebar nav item**: 40px tall, icon 20px + label 14px. Active: `bg-primary/10 text-primary` with a 2px `bg-primary` edge bar. Inactive: `text-muted-foreground`, hover `bg-muted`. Groups collapse with a chevron. **This describes a light sidebar, which the shipped one is not** — do not apply it until the open question in the mapping notes is settled.
- **Progress bar**: track `bg-muted h-2 rounded-full`, fill `bg-primary`.
- **Ranking row**: 32px circular rank chip (`bg-primary/10 text-primary`), name + area line (12px icon + `text-muted-foreground`), right-aligned amount (bold) over count (`text-muted-foreground`).
- **Selectable preview card**: `<label>` wrapping an `sr-only` radio; `rounded-lg border p-3 cursor-pointer`. Unselected hover `bg-muted`; selected `border-primary ring-2 ring-primary bg-primary/10`. Body = thumbnail (`w-full rounded border`) → name (`text-sm font-medium`, selected `text-primary`) → description (`text-xs text-muted-foreground`); keyboard focus via `has-[:focus-visible]:ring-2`.
- **Mobile bottom tab bar**: `fixed inset-x-0 bottom-0` `bg-card` with a `border-t` top edge; each tab is an equal-width flex column (`flex flex-1 flex-col items-center gap-1 py-2`), 24px icon above a 12px label. Active `text-primary`, inactive `text-muted-foreground` with hover `bg-muted`. The content area adds `pb-16` so the fixed bar never overlaps scrollable content. Precedent: `CastPortalShell`.

Hover / focus / disabled states come from the primitives. Only hand-write a state when composing bare elements, and then express it in tokens (`hover:bg-muted`, `disabled:opacity-50`) — never a raw hue.

## Admin restyle rules (shadcn sweep)

Rules for converting a remaining admin slice to the primitives + token vocabulary. Slices are converted independently and in parallel, so these are contracts, not suggestions.

### Form pattern

Keep native inputs bound straight to `register` and swap the element for the shadcn `Input` / `Textarea`. Reach for `FormField` only where a Radix-controlled component needs a controlled value (`Select` / `Checkbox` / `Switch` / `RadioGroup`). Wrap the whole form in `<Form {...form}>`. Never introduce a bare `Controller`.

```tsx
<FormField
  control={control}
  name="classification"
  render={({ field }) => (
    <FormItem>
      <FormLabel>区分</FormLabel>
      <Select value={field.value} onValueChange={field.onChange}>
        <FormControl>
          <SelectTrigger className="w-full">
            <SelectValue />
          </SelectTrigger>
        </FormControl>
        <SelectContent>
          <SelectItem value="自宅">自宅</SelectItem>
        </SelectContent>
      </Select>
    </FormItem>
  )}
/>
```

### Select sentinel

Radix `SelectItem` throws on `value=""`, so an "unset" option needs a sentinel. Convert back at the boundary so the submitted payload keeps its empty string (precedent: `OrderForm`):

```tsx
const SELECT_NONE = '__none__';

<Select
  value={field.value ? field.value : SELECT_NONE}
  onValueChange={v => field.onChange(v === SELECT_NONE ? '' : v)}
>
  <SelectItem value={SELECT_NONE}>－－－</SelectItem>
```

Fill in `defaultValues` for every field so the payload's key set is unchanged by the migration.

### Type restoration

Radix hands back strings and tri-state booleans; restore the original type at the `onChange` boundary: `onValueChange={v => field.onChange(Number(v))}`, `v === 'true'`, `onCheckedChange={v => field.onChange(v === true)}`.

### Tint vs solid

Status labels and badges use the tint recipe (`bg-<token>/10 text-<token>-strong`). Solid (`bg-<token> text-<token>-foreground`) is for **filled surfaces** such as timeline bars. Both recipes clear AA for normal-size text (see the contrast evidence above), so neither imposes a minimum size or weight — pick between them by what the element is, not by how big its label is.

### Behavior preservation

Submitted payloads, react-hook-form wiring and existing `confirm()` calls are preserved exactly. Anything that would change the payload — turning a free-text field into a `Select`, normalizing a value — is a separate issue, not part of a restyle.

### Negative invariant

A restyled admin file keeps **no** raw palette classes. Review with:

```
grep -rnE '(bg|text|border|ring|divide|shadow|placeholder)-(gray|slate|zinc|white|black|red|green|yellow|amber|orange|blue|indigo|purple|pink)' <slice>
```

Out of scope for this invariant: the storefront (`_pages/store-site/**`) and the auth screens.

### Parallel-PR contract

A slice restyle PR does **not** edit the shared files — `src/shared/ui/**`, `src/app/globals.css`, `setupTests.ts`, the `shared/ui` barrel, or this document. If something is missing, raise it in the PR instead of patching it locally.

### jsdom shims

`setupTests.ts` supplies `ResizeObserver` for all tests. Radix components that are actually _opened_ in a test may additionally need the following; add them to `setupTests.ts` verbatim the first time they are required, so parallel branches converge on identical text:

```ts
Element.prototype.scrollIntoView = jest.fn();
Element.prototype.hasPointerCapture = jest.fn();
Element.prototype.setPointerCapture = jest.fn();
Element.prototype.releasePointerCapture = jest.fn();
```

## Do's and Don'ts

- **DO** use token classes for admin colors; if a needed semantic is genuinely missing, extend `globals.css` and THIS file in a dedicated PR.
- **DO** reach for an existing primitive in `@/shared/ui` before composing one from bare elements.
- **DO** keep storefront changes token-driven: template look changes go through `theme.css`, structural blocks through shared `_sections/`.
- **DON'T** introduce raw palette classes or raw hex values in admin UI markup.
- **DON'T** restyle the vendored primitives in `src/shared/ui` from a slice PR — they are kept as generated, and per-screen deviation goes in the consumer's `className`.
- **DON'T** fork or restyle `_sections/` components per template; differences live in tokens and page layout only.
- **DON'T** invent new visual patterns when a component above fits; extend the pattern list instead if genuinely new.
