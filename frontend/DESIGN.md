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

| Token                 | Class examples                                                                                     | Usage rules                                                                     |
| --------------------- | -------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| Page background       | `bg-background`                                                                                    | App shell behind cards                                                          |
| Surface               | `bg-card` (+ `text-card-foreground`)                                                               | Cards, header (the sidebar is a separate open question — see the mapping notes) |
| Border                | bare `border` (the base layer already applies `border-border`)                                     | Card, input and divider borders                                                 |
| Text primary          | `text-foreground`                                                                                  | Headings, key figures                                                           |
| Text secondary        | `text-muted-foreground`                                                                            | Labels, body, hints, "vs 先月"-style annotations                                |
| Muted surface         | `bg-muted`                                                                                         | Progress-bar tracks, table heads, inert fills                                   |
| **Primary**           | CTA `bg-primary text-primary-foreground`, text/graphic `text-primary-strong` / `bg-primary-strong` | CTAs, links, active states, progress fill                                       |
| Primary tint          | `bg-primary/10` (+ `text-primary-strong`)                                                          | Active nav background, rank chips                                               |
| Destructive           | fill `bg-destructive`, text `text-destructive-strong`                                              | Delete actions, validation errors, 却下 / NG status                             |
| Success               | fill `bg-success`, text `text-success-strong`                                                      | Positive trends, 確定 / 有効 / 在籍 / 承認 status                               |
| Warning               | fill `bg-warning`, text `text-warning-strong`                                                      | 保留 / 申請中 status, attention icons                                           |
| Decorative categories | `bg-chart-1/10` … `bg-chart-5/10` behind `text-foreground`                                         | Stat-card icon chips, 指名 chips — category hues that carry no state semantics  |

`destructive` is the only danger vocabulary; there is no `--danger`. If a genuinely new state semantic appears, extend `globals.css` **and** this table in a dedicated PR rather than reaching for a raw hue.

#### Every hue token has a fill form and a `-strong` form

A base token (`--primary` / `--success` / `--warning` / `--destructive`) is a **fill**: a saturated mid hue meant to sit _behind_ content. It is not reliably readable _as_ content — `text-success` on a white card is 3.22:1, and `text-primary` on a dark card is 3.37:1.

So each hue also has a `-strong` variant: the value that reads against the surface **in that mode**. Use it whenever the hue itself is the thing being perceived — text, or a graphic that carries meaning.

| Use                                                | Class                                                               |
| -------------------------------------------------- | ------------------------------------------------------------------- |
| Text (on a surface or on a `/10` tint)             | `text-primary-strong` / `-success-` / `-warning-` / `-destructive-` |
| Meaningful graphic (progress fill, edge bar, ring) | `bg-primary-strong`, `border-primary`                               |
| Icon carrying a state semantic                     | base — `text-success` / `text-warning` / `text-destructive`         |
| Filled surface carrying a label                    | `bg-success` + `text-success-foreground`                            |
| Solid CTA                                          | `bg-primary` + `text-primary-foreground`                            |

Two consequences worth stating outright, because both are counter-intuitive:

- **`text-primary` is not a valid admin class.** Primary stays blue-600 in both modes (a deliberate brand decision), which is readable on a light surface but not on a dark one. `text-primary-strong` is blue-600 in light and blue-400 in dark, so it follows the mode. The solid CTA is unaffected: `bg-primary` + `text-primary-foreground` is 5.03:1 in both modes.
- **`-strong` is not always different from the base.** For success and warning in dark mode it is the same value, because the mid hue is already the readable one there. The split earns its keep in the modes where it differs; using it unconditionally is what makes the rule mechanical.

(`text-destructive` on its own remains valid where a shadcn primitive emits it — `FormMessage` does — because destructive clears AA on a card in both modes, 4.76 / 6.13. Everywhere you write the class yourself, prefer `-strong`.)

##### Contrast matrix — every colour combination this document prescribes

Ratios are WCAG relative-luminance figures computed from the oklch values in `globals.css`, with `/10` tints composited over the surface. **Text needs 4.5:1; meaningful non-text graphics need 3:1.** Both modes are checked, because several combinations pass in one and fail in the other — that asymmetry is the whole reason this table exists.

| Combination                                            | Light     | Dark      | Need |
| ------------------------------------------------------ | --------- | --------- | ---- |
| `text-foreground` on `bg-background` / `bg-card`       | 19.89     | 16.98     | 4.5  |
| `text-muted-foreground` on `bg-background` / `bg-card` | 4.83      | 6.74      | 4.5  |
| `text-foreground` on `bg-muted` (hover rows)           | 18.07     | 14.26     | 4.5  |
| `bg-primary` + `text-primary-foreground` (CTA)         | 5.03      | 5.03      | 4.5  |
| `text-primary-strong` on `bg-card`                     | 5.26      | 6.72      | 4.5  |
| `bg-primary/10` + `text-primary-strong`                | 4.55      | 6.23      | 4.5  |
| `bg-primary-strong` graphic vs `bg-card`               | 5.26      | 6.72      | 3    |
| `bg-primary-strong` fill vs `bg-muted` track           | 4.78      | 5.65      | 3    |
| `text-success-strong` on `bg-card`                     | 9.07      | 9.99      | 4.5  |
| `bg-success/10` + `text-success-strong`                | 8.10      | 8.35      | 4.5  |
| `bg-success` + `text-success-foreground`               | 6.18      | 11.20     | 4.5  |
| `bg-success` graphic vs `bg-card`                      | 3.22      | 9.99      | 3    |
| `text-warning-strong` on `bg-card`                     | 9.09      | 10.33     | 4.5  |
| `bg-warning/10` + `text-warning-strong`                | 8.15      | 8.50      | 4.5  |
| `bg-warning` + `text-warning-foreground`               | 6.23      | 11.58     | 4.5  |
| `bg-warning` graphic vs `bg-card`                      | 3.19      | 10.33     | 3    |
| `text-destructive-strong` on `bg-card`                 | 10.06     | 6.13      | 4.5  |
| `bg-destructive/10` + `text-destructive-strong`        | 8.42      | 5.39      | 4.5  |
| `bg-destructive` + `text-destructive-foreground`       | 4.56      | 6.88      | 4.5  |
| `bg-destructive` graphic vs `bg-card`                  | 4.76      | 6.13      | 3    |
| `text-destructive` on `bg-card` (FormMessage)          | 4.76      | 6.13      | 4.5  |
| `bg-chart-1/10` … `bg-chart-5/10` + `text-foreground`  | 16.87 min | 14.40 min | 4.5  |
| `text-foreground` on `bg-primary/10`                   | 17.22     | 15.75     | 4.5  |
| `text-primary-strong` on `bg-accent` (ghost hover)     | 4.78      | 5.65      | 4.5  |
| `border-primary` / `ring-primary` edge vs a surface    | 5.26      | 3.37      | 3    |
| `bg-destructive/90` hover fill + its icon              | 4.32      | 5.64      | 3    |

Every prescribed combination clears its bar in both modes, so **no size or weight condition is attached to any of them**.

Three of the newer rows need a note, since each answers a question that came up more than once:

- `bg-accent` is not a fourth surface: `globals.css` defines `--accent` identically to `--muted` in both modes, so a ghost `Button`'s hover fill is numerically `bg-muted` and needs no separate measurement beyond this row. Note what the primitive actually paints there: `ghost` emits `hover:bg-accent hover:text-accent-foreground`, and a consumer's plain `className="text-primary-strong"` does **not** survive the hover — the modifier wins on specificity, giving 16.11 / 14.26. The row is what bounds the case where the consumer's colour does survive, such as a hovered ancestor tinting bare text.
- The `border-primary` row is the edge form of the primary hue — the selectable card's selected ring, and the `hover:border-primary` edge on the image-upload dropzone. Dark mode is the tight side (3.37 against `bg-card`, 3.78 against `bg-background`), so the row carries the worse of the two.
- The `bg-destructive/90` row is the hover state of a solid destructive fill, as the vendored `Button` destructive variant emits it. Its 10% transparency means the figure depends on what sits behind; the values here are the **worst case over any backdrop**, which the small alpha keeps close to the opaque `bg-destructive` row. It carries an icon, not text, hence the 3:1 bar.

Three of these recipes exist in this form only because the matrix caught them failing: `text-primary` on a dark surface (3.37), solid destructive with a near-white foreground in dark (2.77), and category chips coloured with `text-chart-*`, where three of the five hues fall below 3:1 against their own tint in one mode or the other (as low as 1.62). Where a fix changed appearance it is noted with the recipe.

Two relationships inherited from the vendored shadcn tokens sit below these bars and are **deliberately not changed here**, since altering them would restyle every primitive: `border-border` against a surface (1.27 / 1.33 — decorative separators, exempt as they carry no state) and `text-muted-foreground` on `bg-muted` (4.39 in light). The second is why the hover recipes in Components pair `bg-muted` with `text-foreground` rather than leaving muted text on a muted surface.

`text-muted-foreground` is short of headroom generally — 4.83 on a plain surface is only 0.33 above the bar — so **any** tint underneath it is likely to push it under. Besides the 4.39 on `bg-muted`, it is 4.18 on `bg-primary/10`. Neither is a prescribed pairing; both are listed here so the next screen that reaches for muted text on a tinted surface finds the answer instead of re-deriving it. The fix in both directions is the same: where the surface changes, the text goes up to `text-foreground`.

A translucent fill over **arbitrary** content (a loading veil over a user-supplied image, say) cannot be put in this table at all, because the backdrop is not known at authoring time. Measure the worst case; if it fails — `bg-card/70` with a `border-primary` spinner bottoms out at 1.25, and even `bg-card/90` only reaches 2.54 — make the fill opaque so the pairing becomes one of the rows above.

##### Adding a colour combination

**Do not write a colour pairing that is not in the matrix above.** If a screen needs one, compute both modes, add the row, and only then use it. This is a hard rule rather than advice: three separate review rounds found regressions in exactly the combinations nobody had measured, and a parallel sweep multiplies a single unmeasured pairing across every slice that copies it. It binds the PRs that edit this document too.

The computation is oklch → sRGB (OKLab matrices, gamma-encoded and clamped to gamut) → WCAG relative luminance, with `/N` tints composited over the surface in gamma-encoded sRGB the way a browser does. **Calibrate the calculator before trusting it**, in two steps, because the two halves fail independently:

1. Against values that need no token data — black on white is 21.00, `#767676` on white is 4.54. This validates the luminance half only.
2. Against rows already in the table — `text-muted-foreground` on `bg-card` (4.83 / 6.74), `bg-primary/10` + `text-primary-strong` (4.55 / 6.23), `bg-primary-strong` vs `bg-muted` (4.78 / 5.65). This is what validates the oklch conversion and the compositing convention.

If step 2 disagrees, the calculator is wrong and the table is right: these rows have survived several review rounds. Never "correct" an existing row as a side effect of adding a new one.

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
| `bg-gray-50` (inset panel of annotations)              | **drop the fill** — `rounded-lg border p-4`; see below                                                                                        |
| `bg-white/90` (sticky footer backdrop)                 | `bg-card/90`                                                                                                                                  |
| `bg-white/70` (veil over an arbitrary image)           | `bg-card` — opaque, because a translucent veil cannot be measured; see the matrix notes                                                       |
| `hover:bg-gray-50`                                     | `hover:bg-muted`                                                                                                                              |
| `border-gray-200` / `-300` / `-100`                    | bare `border` (or `border-border`)                                                                                                            |
| `divide-gray-200`                                      | bare `divide-y` — the base layer already colors it                                                                                            |
| `bg-gray-200` track                                    | `bg-muted`                                                                                                                                    |
| `bg-gray-100 text-gray-800` (neutral/無効 pill)        | `bg-muted text-foreground` — not `text-muted-foreground`, which is the 4.39 pairing noted above                                               |
| `placeholder-gray-500`                                 | drop it — `Input` already ships the placeholder color                                                                                         |
| `shadow-indigo-200` / `shadow-indigo-900/20`           | drop the tint; keep the plain elevation (`shadow-sm` etc.)                                                                                    |
| `blue-600` / `indigo-600` as a CTA fill                | `bg-primary` + `text-primary-foreground`                                                                                                      |
| `blue-600` / `indigo-600` as text or a meaningful bar  | `text-primary-strong` / `bg-primary-strong`                                                                                                   |
| `bg-blue-50`                                           | `bg-primary/10` (label `text-primary-strong`)                                                                                                 |
| `text-red-600` / `bg-red-100 text-red-800`             | `text-destructive-strong` / `bg-destructive/10 text-destructive-strong` — on an icon, `text-destructive`                                      |
| `text-green-600` / `bg-green-100 text-green-800`       | `text-success-strong` / `bg-success/10 text-success-strong` — on an icon, `text-success`                                                      |
| `text-amber-600` / `bg-yellow-100 text-yellow-800`     | `text-warning-strong` / `bg-warning/10 text-warning-strong` — on an icon, `text-warning`                                                      |
| `bg-green-500 text-white` (確定 shift bar)             | `bg-success text-success-foreground` (foreground is now dark)                                                                                 |
| `bg-yellow-400 text-yellow-900` (未確定 shift bar)     | `bg-warning text-warning-foreground`                                                                                                          |
| Decorative chips blue / green / orange / purple / pink | `bg-chart-1/10` … `bg-chart-5/10` + `text-foreground` — **see the recipe below**                                                              |
| any `slate-*` (sidebar)                                | **do not map — see below**                                                                                                                    |
| Weekend `text-red-500` (Sun) / `text-blue-500` (Sat)   | `text-destructive` / `text-primary-strong` — destructive is the one base token that reads as text in both modes (4.76 / 6.13); primary is not |
| Now marker `bg-red-500`                                | `bg-destructive`                                                                                                                              |
| Coverage bar `bg-blue-500/80`                          | `bg-primary-strong` — the `/80` variant drops to 2.58:1 against a dark card                                                                   |
| Hand-written `focus:ring-blue-500` etc.                | drop it — the primitives carry their own focus ring                                                                                           |
| `hover:border-indigo-400` (dropzone edge)              | `hover:border-primary`                                                                                                                        |
| `hover:bg-red-600` under a `bg-red-500` fill           | `hover:bg-destructive/90` — the form the vendored `Button` destructive variant emits                                                          |

##### An inset `bg-gray-50` panel loses its fill, not its edge

The `bg-gray-50 → bg-background` row above is about the **page** backdrop. An inset panel — a read-only summary or a note block sitting inside a card — is a different case, and applying the token table's "inert fills → `bg-muted`" mechanically breaks it: the annotations such a panel contains are `text-muted-foreground`, and muted text on a muted surface is the 4.39 pairing.

Dropping the fill is the cheaper repair, so the panel becomes `rounded-lg border p-4` on the surrounding `bg-card`. **Nothing new needs measuring**: the annotations are `text-muted-foreground` on `bg-card` (4.83 / 6.74) and the edge is a decorative `border-border`. Precedent: the store summary panels in `StoreCreatePage` and `StoreEditPage`.

If the fill genuinely carries meaning and has to stay, the other resolution is `bg-muted` with the text raised to `text-foreground` (18.07 / 14.26). What is not available is `bg-muted` with the text left muted.

##### `text-gray-700` is decided by role, not by class

It is the second most common legacy class and it has no single correct token: it sits between `gray-900` (headings) and `gray-500/600` (annotations), so either destination passes the review grep. Guessing per-file is what makes parallel slices diverge. Decide by what the text **does**:

- Text the reader consumes as substance — record values, list items, row labels, body copy → `text-foreground`
- Text that annotates something else — counts, hints, helper text, timestamps, "N 件中 x-y を表示" → `text-muted-foreground`

Most occurrences never reach this rule because they sit on `<label>` and secondary-button class strings that the `Label` / `Button` primitives replace outright.

##### Decorative chip recipes

The `chart-*` tokens are category hues with **no `-foreground` pair** and no consistent lightness: `--chart-4` is a very pale amber in light mode, while `--chart-1` is a deep blue in dark mode. Measured as content colours, four of the five fail 3:1 in one mode or the other — as low as 1.62:1. They are therefore **background-only**:

- **Chip or pill**: `bg-chart-N/10` with `text-foreground`. The hue lives entirely in the tint, which is what carries the category signal; the label stays readable because it never depends on the hue. All five clear 4.5:1 in both modes (16.87 and 14.40 at worst).
- **Never** `text-chart-N`, and never a solid `bg-chart-N` carrying an icon or label — that is the combination the matrix rejects.
- **Solid**: a solid coloured element the user can click is an action, not a category — use `Button` (which is `primary`), as with the dashboard's report button. A non-interactive solid category block is a new pattern: raise it rather than inventing a foreground.
- The neutral counterpart of a category chip (e.g. the フリー fallback beside a 指名 chip) is `bg-muted text-foreground`.

Pick whichever hue keeps sibling categories distinct — with the hue confined to a 10% tint, that choice can no longer create a contrast problem.

##### The sidebar is deliberately left undecided

`widgets/sidebar/ui/Sidebar.tsx` is a **dark** surface (`bg-slate-800 text-white`, `border-slate-700`, active `bg-indigo-600 text-white`) and the only slate consumer in the admin UI. It contradicts the Sidebar nav item entry in Components above, which describes a light `bg-primary/10 text-primary-strong` treatment.

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
- **Card section heading**: `CardTitle` renders a `<div>`, so wherever it replaces an `<h2>` / `<h3>` it must be written `<CardTitle role="heading" aria-level={3}>`. Without both attributes the section disappears from screen-reader heading navigation, and the loss is invisible in a rendered diff. The primitive stays pristine — it spreads `React.ComponentProps<'div'>`, so the consumer supplies them. (Where the card is genuinely not a section heading — a bare label on a stat card — leave it a `div` and do not add the role.)
- **Table**: shadcn `Table` inside a `Card`. Precedent: `CustomersPage`.
- **Tabs**: shadcn `Tabs`. Precedent: `ShiftsPage`.
- **Loading placeholder**: `Skeleton` sized with layout classes, never a hand-rolled `animate-pulse` block.
- **Status pill**: `Badge variant="outline"` plus one tint recipe — `border-transparent bg-success/10 text-success-strong` (確定) / `bg-warning/10 text-warning-strong` (保留) / `bg-destructive/10 text-destructive-strong` (却下 / NG). Precedent: `CustomersPage`.
- **Stat card**: `Card`; label (`text-muted-foreground` 14px) → figure (`text-foreground` 30px bold) → trend row (`text-success-strong` delta + `text-muted-foreground` comparison); category icon chip top-right (`rounded-lg p-3`, 24px icon, `bg-chart-N/10` with a `text-foreground` icon).
- **Sidebar nav item**: 40px tall, icon 20px + label 14px. Active: `bg-primary/10 text-primary-strong` with a 2px `bg-primary-strong` edge bar. Inactive: `text-muted-foreground`, hover `bg-muted text-foreground`. Groups collapse with a chevron. **This describes a light sidebar, which the shipped one is not** — do not apply it until the open question in the mapping notes is settled.
- **Progress bar**: track `bg-muted h-2 rounded-full`, fill `bg-primary-strong` (plain `bg-primary` is only 2.83:1 against the dark-mode track).
- **Ranking row**: 32px circular rank chip (`bg-primary/10 text-primary-strong`), name + area line (12px icon + `text-muted-foreground`), right-aligned amount (bold) over count (`text-muted-foreground`).
- **Selectable preview card**: `<label class="group">` wrapping an `sr-only` radio; `rounded-lg border p-3 cursor-pointer`. Unselected hover `bg-muted`; selected `border-primary ring-2 ring-primary bg-primary/10`. Body = thumbnail (`w-full rounded border`) → name (`text-sm font-medium`, selected `text-primary-strong`) → description (`text-xs`); keyboard focus via `has-[:focus-visible]:ring-2`. The description is the one part that cannot stay muted throughout, because **both** of the card's non-default surfaces put `text-muted-foreground` under the bar — 4.39 on the hover fill, 4.18 on the selected tint. So it is `text-muted-foreground group-hover:text-foreground` when unselected and `text-foreground` when selected. That is the same "the hover flips the fill and the text together" rule the Sidebar nav item and Mobile bottom tab bar entries follow; the `group` on the label is what lets the text follow a hover owned by its ancestor. Precedent: the template picker in `StoreProfileForm`.
- **Mobile bottom tab bar**: `fixed inset-x-0 bottom-0` `bg-card` with a `border-t` top edge; each tab is an equal-width flex column (`flex flex-1 flex-col items-center gap-1 py-2`), 24px icon above a 12px label. Active `text-primary-strong`, inactive `text-muted-foreground` with hover `bg-muted text-foreground`. The content area adds `pb-16` so the fixed bar never overlaps scrollable content. Precedent: `CastPortalShell`.

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

Status labels and badges use the tint recipe (`bg-<token>/10 text-<token>-strong`). Solid (`bg-<token> text-<token>-foreground`) is for **filled surfaces** such as timeline bars. Pick between them by what the element is, not by how big its label is: both clear AA for normal-size text, so neither imposes a minimum size or weight.

This holds for `success`, `warning` and `destructive` — the three semantics whose solid form appears as a row in the contrast matrix. It is **not** a general property of "base token plus its `-foreground`": that pairing is only sound where it has been measured in both modes, which is why the matrix, not the naming pattern, is the authority. `primary` in particular has a solid form for CTAs only (`bg-primary` + `text-primary-foreground`); it has no tint-with-`text-primary` form.

### Behavior preservation

Submitted payloads, react-hook-form wiring and existing `confirm()` calls are preserved exactly. Anything that would change the payload — turning a free-text field into a `Select`, normalizing a value — is a separate issue, not part of a restyle.

### Negative invariant

A restyled admin file keeps **no** raw palette classes. Review with:

```
grep -rnE '(bg|text|border|ring|divide|shadow|placeholder)-(gray|slate|zinc|white|black|red|green|yellow|amber|orange|blue|indigo|purple|pink)' <slice>
```

**Grepping the slice directory alone is not sufficient**, because admin colours reach the screen from two places that no slice-scoped grep can see:

- `entities/**` — model helpers that hand the slice a ready-made class string (`entities/cast/model/invitationStatusLabel.ts` returned a raw palette badge recipe this way). Every entity the slice consumes is in scope for the slice that consumes it.
- `shared/ui/**` — but only the hand-written components there, not the vendored primitives. See the next section for which is which.

Neither shows up when the reviewer greps `_pages/<slice>`, so run the grep over both as well as the slice.

Permanently out of scope, and the only exemptions:

- the storefront (`_pages/store-site/**`) — its own token world;
- the auth screens: `AuthLayout`, everything it wraps, and `shared/ui/auth-layout.tsx` itself — also its own token world;
- the vendored shadcn primitives, kept exactly as generated (`badge.tsx` and `button.tsx` carry `text-white`; `dialog.tsx` and `sheet.tsx` carry `bg-black/50`).

Everything else that still matches the grep — the unconverted slices, `widgets/header`, `widgets/sidebar`, the `app/` route shells — is **pending, not exempt**. A partially converted file counts as matching: `store-orders/ui/OrderForm.tsx` is otherwise migrated yet still carries one `hover:bg-blue-50`. The sidebar is the one case that is additionally blocked, on the open question recorded in the mapping notes.

### What in `shared/ui` may be edited

`src/shared/ui` holds two kinds of file, and only one of them is frozen.

- **Vendored shadcn primitives** — `button` / `card` / `dialog` / `select` / `form` / `table` / `badge` / `sheet` / `popover` / `command` / `skeleton` / `tabs` / `dropdown-menu` / `checkbox` / `switch` / `radio-group` / `input` / `label` / `textarea`. Kept exactly as generated; per-screen deviation goes in the consumer's `className`, never here.
- **Kizuna-authored components** — `image-upload.tsx`, `auth-layout.tsx`, `theme-provider.tsx`. Hand-written, so they obey the token rules like any other admin file. `auth-layout.tsx` is the exception noted above: it belongs to the auth world.

Tell them apart by the generator's fingerprints rather than by guessing: a vendored file carries `data-slot` attributes, imports from `radix-ui`, and declares its variants with `cva`. The hand-written three carry none of those and import application code a generator would never emit (`@/shared/api`, `@heroicons/react`, `next-themes`). `git log --follow` confirms it — the hand-written files predate the shadcn adoption.

Editing a hand-written one is still a shared-file change, so it goes through the contract below rather than through a slice PR.

### Parallel-PR contract

A slice restyle PR does **not** edit the shared files — `src/shared/ui/**`, `src/app/globals.css`, `setupTests.ts`, the `shared/ui` barrel, or this document. If something is missing, raise it in the PR instead of patching it locally.

### jsdom shims

`setupTests.ts` supplies two stubs for all tests:

```ts
globalThis.ResizeObserver = ResizeObserverStub;
Element.prototype.scrollIntoView = jest.fn();
```

`scrollIntoView` is needed by any test that **opens** a Radix `Select`: the content scrolls the selected item into view from a mount effect, so the failure (`candidate?.scrollIntoView is not a function`) happens on open regardless of which key opened it. A `DropdownMenu` test passing without the stub proves nothing — that component has no such effect, so it is not a precedent to copy from.

Add a further stub **only after seeing a test fail without it**, and put it here rather than in the test file so parallel branches converge on one text. In particular, the pointer-capture trio (`hasPointerCapture` / `setPointerCapture` / `releasePointerCapture`) is **not** required: tests open Radix components by keyboard (`fireEvent.keyDown(trigger, { key: 'ArrowDown' })`), which never reaches the pointer path. Adding it pre-emptively is how unreachable code accumulates in a file every branch has to merge.

## Do's and Don'ts

- **DO** use token classes for admin colors; if a needed semantic is genuinely missing, extend `globals.css` and THIS file in a dedicated PR.
- **DO** reach for an existing primitive in `@/shared/ui` before composing one from bare elements.
- **DO** keep storefront changes token-driven: template look changes go through `theme.css`, structural blocks through shared `_sections/`.
- **DON'T** introduce raw palette classes or raw hex values in admin UI markup.
- **DON'T** restyle the vendored primitives in `src/shared/ui` at all — they are kept as generated, and per-screen deviation goes in the consumer's `className`. The hand-written components in that directory are a different matter; see "What in `shared/ui` may be edited".
- **DON'T** fork or restyle `_sections/` components per template; differences live in tokens and page layout only.
- **DON'T** invent new visual patterns when a component above fits; extend the pattern list instead if genuinely new.
