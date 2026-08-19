# Design System

Design rules for all UI work. Structure follows the DESIGN.md convention (designmd.app): Colors / Fonts / Spacing / Components / Admin restyle rules / Do's and Don'ts, plus a Notifications and failure states section the convention itself does not carry. **Any agent implementing or modifying UI MUST read this file first** and, if a frontend-design skill is available in its environment, invoke it before writing markup. (`frontend/CLAUDE.md` points here.)

## Scope: three visual worlds

| World                                                                                                                                                                                                                                                                                    | Source of truth                                                                                          | Where styles live                                                                                                                      |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| **Admin UI** (platform + store management screens: dashboards, lists, settings forms)                                                                                                                                                                                                    | `src/app/globals.css` token layer + the vendored shadcn/ui primitives in `src/shared/ui` (Base UI-based) | shadcn primitives composed with Tailwind token classes (`bg-background` / `text-muted-foreground` / …); raw palette classes are banned |
| **Public storefront** (`_pages/store-site/templates/**`)                                                                                                                                                                                                                                 | Template code itself; per-template `theme.css` tokens                                                    | `templates/<key>/theme.css` CSS custom properties + shared `_sections/` components                                                     |
| **Auth screens** — exactly the pages rendered inside `AuthLayout`: `_pages/platform-login`, `_pages/cast-invite`, `_pages/member-register` and `_pages/platform-line-callback`, with their `features/platform-login`, `features/cast-invite-accept` and `features/member-register` forms | `src/styles/auth.css` (the Midnight Atelier look)                                                        | `auth.css` classes; outside the admin token contract                                                                                   |

Never mix the vocabularies: no gold-serif storefront styling in admin screens, no admin cards in storefront templates, and no admin token restyling of the auth screens.

The 404 page (`app/(public)/not-found.tsx`) belongs to **admin**, despite its route group. It serves every world at once: the storefront reaches it through its own `notFound()` calls (an unresolved store, a missing cast), and the `[...not-found]` catch-all sends every URL that matches no route there, console typos included. One page cannot be in three voices, so it is in the neutral one — and it is an `app/` route shell, which the sweep rules below already class as pending rather than exempt. Two things follow, and the second is a deliberate limit rather than an omission:

- It is written in admin token classes and admin primitives, like any console screen.
- **It renders light-only, and there is no dark version to check.** The `(public)` root layout carries no theme wiring at all — that is a structural decision fixed by `src/__tests__/theme-provider-scope.test.tsx`, which fs-scans every `(public)` shell for theme strings and asserts `ThemeScope` appears in `(admin)/layout.tsx` and nowhere else. So its tokens always resolve to the `:root` values. Do not "fix" this by wiring a theme into `(public)`; that would pull the storefront and the auth screens into the theme's blast radius to serve one page.

Membership in the auth world is decided by `AuthLayout`, which is the only importer of `auth.css` — not by a slice's name. `features/password-change` in particular is **admin**, not auth: it is embedded in the account settings pages of both consoles (`_pages/store-settings` and `_pages/platform-settings`) and never appears under `AuthLayout`, so it is restyled with the admin tokens. Because two page slices host it, it belongs to the **`store-settings`** restyle ticket — the console whose `AccountPage` carries the surrounding form. The `platform-settings` ticket renders it but does not edit it.

## Colors

### Admin UI (token classes only — never raw palette classes, never raw hex)

The token layer lives in `src/app/globals.css`: `:root` / `.dark` oklch values exposed to Tailwind through `@theme inline`. Always name the semantic, never the hue — that is what makes both light and dark modes follow without per-screen edits.

| Token                 | Class examples                                                                                     | Usage rules                                                                    |
| --------------------- | -------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| Page background       | `bg-background`                                                                                    | App shell behind cards                                                         |
| Surface               | `bg-card` (+ `text-card-foreground`)                                                               | Cards, header, sidebar                                                         |
| Border                | bare `border` (the base layer already applies `border-border`)                                     | Card, input and divider borders                                                |
| Text primary          | `text-foreground`                                                                                  | Headings, key figures                                                          |
| Text secondary        | `text-muted-foreground`                                                                            | Labels, body, hints, "vs 先月"-style annotations                               |
| Muted surface         | `bg-muted`                                                                                         | Progress-bar tracks, table heads, inert fills                                  |
| **Primary**           | CTA `bg-primary text-primary-foreground`, text/graphic `text-primary-strong` / `bg-primary-strong` | CTAs, links, active states, progress fill                                      |
| Primary tint          | `bg-primary/10` (+ `text-primary-strong`)                                                          | Active nav background, rank chips                                              |
| Destructive           | fill `bg-destructive`, text `text-destructive-strong`                                              | Delete actions, validation errors, 却下 / NG status                            |
| Success               | fill `bg-success`, text `text-success-strong`                                                      | Positive trends, 確定 / 有効 / 在籍 / 承認 status                              |
| Warning               | fill `bg-warning`, text `text-warning-strong`                                                      | 保留 / 申請中 status, attention icons                                          |
| Decorative categories | `bg-chart-1/10` … `bg-chart-5/10` behind `text-foreground`                                         | Stat-card icon chips, 指名 chips — category hues that carry no state semantics |

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

| Combination                                                      | Light     | Dark      | Need |
| ---------------------------------------------------------------- | --------- | --------- | ---- |
| `text-foreground` on `bg-background` / `bg-card`                 | 19.89     | 16.98     | 4.5  |
| `text-muted-foreground` on `bg-background` / `bg-card`           | 4.83      | 6.74      | 4.5  |
| `text-foreground` on `bg-muted` (hover rows)                     | 18.07     | 14.26     | 4.5  |
| `bg-primary` + `text-primary-foreground` (CTA)                   | 5.03      | 5.03      | 4.5  |
| `text-primary-strong` on `bg-card`                               | 5.26      | 6.72      | 4.5  |
| `text-primary-strong` on `bg-background`                         | 5.26      | 7.54      | 4.5  |
| `bg-primary/10` + `text-primary-strong`                          | 4.55      | 6.23      | 4.5  |
| `bg-primary-strong` graphic vs `bg-card`                         | 5.26      | 6.72      | 3    |
| `bg-primary-strong` fill vs `bg-muted` track                     | 4.78      | 5.65      | 3    |
| `text-success-strong` on `bg-card`                               | 9.07      | 9.99      | 4.5  |
| `bg-success/10` + `text-success-strong`                          | 8.10      | 8.35      | 4.5  |
| `bg-success` + `text-success-foreground`                         | 6.18      | 11.20     | 4.5  |
| `bg-success/90` hover + `text-success-foreground`                | 5.10      | 9.04      | 4.5  |
| `bg-success` graphic vs `bg-card`                                | 3.22      | 9.99      | 3    |
| `border-success-strong` edge vs `bg-muted` track                 | 8.24      | 8.39      | 3    |
| `text-warning-strong` on `bg-card`                               | 9.09      | 10.33     | 4.5  |
| `bg-warning/10` + `text-warning-strong`                          | 8.15      | 8.50      | 4.5  |
| `bg-warning` + `text-warning-foreground`                         | 6.23      | 11.58     | 4.5  |
| `bg-warning/90` hover + `text-warning-foreground`                | 5.14      | 9.35      | 4.5  |
| `bg-warning` graphic vs `bg-card`                                | 3.19      | 10.33     | 3    |
| `text-destructive-strong` on `bg-card`                           | 10.06     | 6.13      | 4.5  |
| `text-destructive-strong` on `bg-background`                     | 10.06     | 6.88      | 4.5  |
| `bg-destructive/10` + `text-destructive-strong`                  | 8.42      | 5.39      | 4.5  |
| `bg-destructive` + `text-destructive-foreground`                 | 4.56      | 6.88      | 4.5  |
| `bg-destructive` graphic vs `bg-card`                            | 4.76      | 6.13      | 3    |
| `bg-destructive` marker vs `bg-muted` track                      | 4.33      | 5.15      | 3    |
| `text-destructive` on `bg-card` (FormMessage)                    | 4.76      | 6.13      | 4.5  |
| `bg-chart-1/10` … `bg-chart-5/10` + `text-foreground`            | 16.87 min | 14.40 min | 4.5  |
| `text-foreground` on `bg-primary/10`                             | 17.22     | 15.75     | 4.5  |
| `text-primary-strong` on `bg-accent` (ghost hover)               | 4.78      | 5.65      | 4.5  |
| `border-primary` / `ring-primary` vs `bg-card` / `bg-background` | 5.26      | 3.37      | 3    |
| `bg-destructive/90` hover fill + its icon                        | 4.32      | 5.64      | 3    |
| `border-muted-foreground` dropzone edge vs `bg-card`             | 4.83      | 6.74      | 3    |

Every prescribed combination clears its bar in both modes, so **no size or weight condition is attached to any of them**.

A few of the newer rows need a note, since each answers a question that came up more than once:

- `bg-accent` is not a fourth surface: `globals.css` defines `--accent` identically to `--muted` in both modes, so a ghost `Button`'s hover fill is numerically `bg-muted` and needs no separate measurement beyond this row. Note what the primitive actually paints there: `ghost` emits `hover:bg-accent hover:text-accent-foreground`, and a consumer's plain `className="text-primary-strong"` does **not** survive the hover — the modifier wins on specificity, giving 16.11 / 14.26. The row is what bounds the case where the consumer's colour does survive, such as a hovered ancestor tinting bare text.
- The `border-primary` row is the edge form of the primary hue — the selectable card's selected ring, and the `hover:border-primary` edge on the image-upload dropzone. Its two named surfaces are the **only** ones it certifies, and the row deliberately does not say "vs a surface": dark mode is tight everywhere (3.37 against `bg-card`, 3.78 against `bg-background`) and it **fails on `bg-muted` — 4.78 / 2.83**. So a primary edge must not be drawn on a muted or accent fill; move the edge to a card/background surface, or drop to a `border-border` edge there. Within the prescribed recipes the worst case is the selected card's ring, whose inner side sits on `bg-primary/10` at 4.55 / 3.13 — still clear, but that 3.13 is the real headroom, not the 3.37 in the row.
- The `bg-destructive/90` row is the hover state of a solid destructive fill, as the vendored `Button` destructive variant emits it. Its 10% transparency means the figure depends on what sits behind; the values here are the **worst case over any backdrop**, which the small alpha keeps close to the opaque `bg-destructive` row. It carries an icon, not text, hence the 3:1 bar. The `bg-success/90` / `bg-warning/90` rows follow the same worst-case convention for the hover state of the solid timeline bars; these carry the bar's own time text, hence 4.5, and over their actual `bg-muted` track they measure 6.82 / 6.89 in light (9.41 / 9.75 in dark).
- The two `on bg-background` rows cover text drawn on the page shell outside a card — the timeline view's selected tab (`text-primary-strong`) and page-level error copy (`text-destructive-strong`). In light mode `--background` is identical to `--card`, so only the dark figures differ from the `on bg-card` rows.
- The `bg-destructive` marker row is the timeline's current-time line where it crosses the `bg-muted` track (its crossing of the card gaps is the `vs bg-card` row above).
- The `border-success-strong` row is the shift timeline's 破線中抜き bar — a 確定 shift that is not published to the public site draws its outline instead of a fill, so the edge is the whole graphic and is bound by 3:1 against the track it sits on. It clears with room in both modes, which is why it needs no exemption where the solid `bg-success` bar did: the `-strong` variant is the darker green in light mode, and in dark mode `-strong` and the base are the same value. The bar's own time text falls back to `text-foreground` on `bg-muted` (18.07 / 14.26) once the fill is gone.
- The `border-muted-foreground` row is the image-upload dropzone's dashed edge. A dropzone's boundary is the only cue to where dropping works, so it is a meaningful graphic bound by 3:1 — the decorative exemption that covers `border-border` separators does not extend to it, and `border-border` itself sits at 1.27 / 1.33. Against `bg-background` the same edge measures 4.83 / 7.56, so the row's `bg-card` figures are the binding worst case.
- The toasts (`shared/ui/toast.tsx`) are three rows already in the table, none of them new: the base is `text-foreground` on `bg-card` (`--card-foreground` is defined identically to `--foreground` in both modes, so it is literally that row), the success toast is `bg-success` + `text-success-foreground`, and the error toast is `bg-destructive` + `text-destructive-foreground`. The toast's markup is ours, so each pairing is an ordinary pair of token classes selected by `data-[type=…]` — the tier's `type` reaches the DOM as `data-type` on the toast root, and that attribute is what picks the fill. One consequence binds anyone editing that file: the pairs are written per tier, so a tier that names a background without naming its foreground in the same pair keeps the base foreground on the new fill.

  A third one bounds where those rows apply. Both root layouts mount the `Toaster`, so admin tokens reach the storefront and the auth screens too — and `(public)` has no theme wiring, so there they always resolve to the `:root` values. That is harmless for the types those surfaces actually reach: outside admin every call site is `success` or `error` — the two `warning` calls both sit under `app/(admin)/` — and `--destructive` / `--destructive-foreground` in light are the saturated red on near-white those screens already showed. **The base pairing is the one that does not travel.** It is unreached today, and a toast added later with no tier would paint an admin card surface — white — over Midnight Atelier. Whoever adds the first one settles what the neutral toast looks like outside admin; do not assume this row answers it.

  As the classification stands, none of the three tiers reaches for the base pairing — `success`, `error` and `warning` all paint a hue — so no white card is printed today. **`warning` is issued from exactly two call sites**, both the optimistic-lock 409 — one in `StaffEditModal`, one in `RoleFormModal`. Both files were traced to their host routes, both under `app/(admin)/`, so the tier is not printed outside the admin surfaces. The base pairing is also the only thing a tierless toast can get, **on purpose**, precisely so this warning keeps its force: giving the untyped case a tier's look would make the base pairing unreachable in practice and let any toast written without a tier inherit that severity. None of this is a retraction of the warning above — it is a note on the current state so the next reader does not re-count the tiers.

  The tiers add no row to this table, and this is why: each one reuses a pairing already listed. `error` is this row; `warning` is `bg-warning` + `text-warning-foreground`; every tier's icon and its dismiss button are `currentColor`, so both resolve to the foreground of its own row. That is the whole reason the toast draws its own icons rather than taking a library's: an icon arriving with a hardcoded colour is a pairing this document never measured, and a dependency's default is not an exemption from the rule below. Finally, the headroom on this row: light is **4.56** against the 4.5 text bar, 0.06 to spare. Nothing in the tier system created that, but anything layered onto this row is working with almost none. Note the headroom while reading this row, though: light is **4.56** against the 4.5 text bar, 0.06 to spare. Nothing in the tier system created that, but anything layered onto this row is working with almost none.

Three of these recipes exist in this form only because the matrix caught them failing: `text-primary` on a dark surface (3.37), solid destructive with a near-white foreground in dark (2.77), and category chips coloured with `text-chart-*`, where three of the five hues fall below 3:1 against their own tint in one mode or the other (as low as 1.62). Where a fix changed appearance it is noted with the recipe.

Two relationships inherited from the vendored shadcn tokens sit below these bars and are **deliberately not changed here**, since altering them would restyle every primitive: `border-border` against a surface (1.27 / 1.33 — decorative separators, exempt as they carry no state) and `text-muted-foreground` on `bg-muted` (4.39 in light). The second is why the hover recipes in Components pair `bg-muted` with `text-foreground` rather than leaving muted text on a muted surface.

`text-muted-foreground` is short of headroom generally — 4.83 on a plain surface is only 0.33 above the bar — so **any** tint underneath it is likely to push it under, and light is always the failing mode because muted text is darkest there. Besides the **4.39 light / 5.66 dark** on `bg-muted`, it is **4.18 light / 6.25 dark** on `bg-primary/10`. Neither is a prescribed pairing; both are listed here so the next screen that reaches for muted text on a tinted surface finds the answer instead of re-deriving it. The fix in both directions is the same: where the surface changes, the text goes up to `text-foreground`.

A translucent fill over **arbitrary** content (a loading veil over a user-supplied image, say) cannot be put in this table at all, because the backdrop is not known at authoring time. Measure the worst case; if it fails — `bg-card/70` with a `border-primary` spinner bottoms out at 1.25, and even `bg-card/90` only reaches 2.54 — make the fill opaque so the pairing becomes one of the rows above.

##### Adding a colour combination

**Do not write a colour pairing that is not in the matrix above.** If a screen needs one, compute both modes, add the row, and only then use it. This is a hard rule rather than advice: three separate review rounds found regressions in exactly the combinations nobody had measured, and a parallel sweep multiplies a single unmeasured pairing across every slice that copies it. It binds the PRs that edit this document too.

The computation is oklch → sRGB (OKLab matrices, gamma-encoded and clamped to gamut) → WCAG relative luminance, with `/N` tints composited over the surface in gamma-encoded sRGB the way a browser does. **Calibrate the calculator before trusting it**, in two steps, because the two halves fail independently:

1. Against values that need no token data — black on white is 21.00, `#767676` on white is 4.54. This validates the luminance half only.
2. Against rows already in the table — `text-muted-foreground` on `bg-card` (4.83 / 6.74), `bg-primary/10` + `text-primary-strong` (4.55 / 6.23), `bg-primary-strong` vs `bg-muted` (4.78 / 5.65). This is what validates the oklch conversion and the compositing convention.

If step 2 disagrees, the calculator is wrong and the table is right: these rows have survived several review rounds. Never "correct" an existing row as a side effect of adding a new one.

A row certifies a **pairing**, not a line of shipped code. Rows are added ahead of the slice that will use them — the restyle sweep needs the pairing settled before the slice PR may write it — so a row's "where it appears" note names the intended site, which may not be in the tree yet. Read those notes as the destination, never as evidence the code is already there.

##### Nested tints compound

A `/10` tint row in the matrix certifies the tint composited **directly over `bg-card` / `bg-background`** — nothing else. When a tinted element sits inside another tinted surface (a chip inside a hovered cell, a badge on a hovered row), the browser composites both layers, and the result is a colour no matrix row has measured. Two recipes that each pass in isolation can fail combined: a `bg-primary/10` chip on a cell whose hover is also `bg-primary/10` puts `text-primary-strong` on a double tint at **3.97 in light** (5.73 dark) — under the bar, even though each layer alone is the certified 4.55 / 6.23.

- The known repair is the same hover-flips-the-text rule the Selectable preview card uses: while the ancestor's hover tint is active, raise the chip text to `text-foreground` (`group-hover:text-foreground`, 15.04 / 14.47). The day-cell count chip in `store-shifts`' `ShiftCalendar` is where this was measured; the repair ships with that slice's restyle, so apply it there rather than assuming it is already in the tree.
- A new nesting must be measured even when it happens to pass. The status Badge tint on a table row hovering `hover:bg-muted/50` (the NG badge in `store-customers`' `CustomersPage`) composites to 8.04 / 4.94 at worst (destructive) — clear, but clear by measurement, not by any matrix row.

##### The decorative exemption and its boundary

Contrast minima bind elements that carry information. An element is **decorative — and exempt — only when deleting it loses nothing**: everything it expresses is fully available elsewhere on the same screen, or it expresses nothing at all. The recorded instances:

- 1px separators (`border-border`, or a `bg-border` hairline): they carry no state, only rhythm — the 1.27 / 1.33 figures noted above never bind.
- A legend dot immediately beside its own text label (the 確定 / 未確定 dots in the shift calendar): the adjacent text carries the identical meaning, so the dots' worst case — the success / warning dots at 2.78 / 2.76, light mode on a `bg-primary/10` hovered cell — does not bind.

The exemption is never automatic for an element whose position or extent is itself the message (a timeline bar, a chart mark): those are meaningful graphics at 3:1 however redundant their label, and any exemption for one must be an explicitly recorded per-case entry — see the recorded exemption below — never a silent assumption. When in doubt, treat the element as meaningful and measure.

##### Recorded exemption — timeline bars against their track

The shift timeline draws solid bars (`bg-success` 確定 / `bg-warning` 未確定) on a `bg-muted` track once the `store-shifts` restyle lands. A bar's start / end position **is** the information, so the bar edge against the track is a meaningful graphic bound by 3:1 — and it does not meet it: **2.92 / 2.90 in light** (dark passes at 8.39 / 8.67).

**The owner granted this one an explicit exemption.** The grounds: the in-bar time text carries the identical start / end at 6.18:1, the bars carry `shadow-sm`, and the pre-restyle state (green-500 on gray-50) failed the same bound, so keeping it is not a regression. The rejected alternative was returning the track to `bg-background` + `border` — that restores the certified 3.22 / 3.19, but light mode's `--background` is identical to `--card`, so the track would melt into the card and be told apart by its 1px border alone.

This exemption covers **this screen only**. A new bar-on-track surface starts from the 3:1 bound again and needs its own entry; it does not inherit this one.

#### Legacy → token mapping (restyle sweep)

Screens still carrying pre-shadcn classes are converted with this table.

**Substitute the primitive before reaching for this table.** Most legacy class strings belong to hand-built buttons, labels, inputs and tables; swapping in `Button` / `Label` / `Input` / `Table` deletes the whole string rather than mapping it. In the files migrated so far (`store-orders/ui/OrderForm.tsx` and the `store-customers` slice), every occurrence of `text-gray-700` and `hover:bg-gray-50` inside them disappeared this way and none was replaced by a token. Only the classes that survive on bare elements need the table below.

The sweep is finished on the admin side: the grep in "Negative invariant" now returns nothing outside the exemptions listed there. The table stays because it is the authority for anything arriving from outside the sweep — a screen ported from the old system, or a patch written against pre-shadcn markup.

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
| Weekend `text-red-500` (Sun) / `text-blue-500` (Sat)   | `text-destructive` / `text-primary-strong` — destructive is the one base token that reads as text in both modes (4.76 / 6.13); primary is not |
| Now marker `bg-red-500`                                | `bg-destructive`                                                                                                                              |
| Coverage bar `bg-blue-500/80`                          | `bg-primary-strong` — the `/80` variant drops to 2.58:1 against a dark card                                                                   |
| Hand-written `focus:ring-blue-500` etc.                | drop it — the primitives carry their own focus ring; on an element that stays bare, use the focus recipe in Components instead                |
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

One-off domain colors (now marker, weekend, coverage bar, category chips) intentionally reuse existing tokens instead of gaining dedicated ones: a token with a single consumer is dead weight. The same policy is why `globals.css` carries no `--sidebar-*` family: the sidebar is an ordinary admin surface (`bg-card` behind the Sidebar nav item recipe from Components) built from the generic tokens.

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

## Icons

- **One library: `lucide-react`.** Do not (re-)introduce `@heroicons/react` or any other icon set. `components.json`'s `iconLibrary` is what `shadcn add` consults when it generates a primitive, so the icons baked into vendored primitives are lucide; the application uses the same family so that icons sharing a screen share one stroke width and terminal style.
- Import the `Icon`-suffixed aliases (`BellIcon`, not `Bell`) — the style the vendored primitives already use.
- Keep the default `strokeWidth` (2px). Thinning an icon per call site re-creates the mixed-family look the single library exists to prevent.
- The sidebar's `ICON_MAP` keys are the icon strings served by the menu API's seed data. Keys are lucide-react export names verbatim (`HouseIcon`, `SettingsIcon`, …) — the map doubles as the allowlist of icons a menu row may reference, and an unknown key falls back to `HouseIcon`.

## Spacing

There is no custom spacing scale: `globals.css`'s `@theme inline` block defines radius and colour only, so Tailwind v4's default 4px step is what every `p-*` / `gap-*` resolves against. **Do not override `--spacing`** — it would rescale all three visual worlds at once, auth and storefront included.

### Admin scale

| Role                                      | Value                                                                                                                                                                                                                                             |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Console content padding                   | `p-8` (32px) + `max-w-7xl mx-auto` — supplied by the console layouts, never by a page                                                                                                                                                             |
| **Card gutter — content ↔ card edge**     | **24px**, the one invariant this table exists for; see below                                                                                                                                                                                      |
| Between cards / between form field blocks | 24px (`space-y-6` / the `Card`'s own `gap-6`)                                                                                                                                                                                                     |
| Label ↔ control                           | 8px (`gap-2`, already in `FormItem`)                                                                                                                                                                                                              |
| Control group in a header or toolbar      | 12px (`gap-3`)                                                                                                                                                                                                                                    |
| In-row icon action cluster                | 4px (`gap-1`)                                                                                                                                                                                                                                     |
| **Table cell**                            | horizontal 8px — the primitive's own `p-2`, deliberately left alone; vertical 12px (`py-3`); header `h-11`. 44px is the floor for a text-only row; in practice a row carrying an `icon-sm` action measures 56px and one carrying a thumbnail more |
| **Calendar day cell**                     | `p-3` (12px) — a 7-column grid divides the available width, so the horizontal constraint that binds a table does not apply here. Its weekday band is `px-3 py-2.5` — a label strip rather than a cell, kept visibly subordinate to the day cells  |
| **Row card** (one record per card)        | 16px (`p-4`). Precedent: `ShiftRequestInbox`                                                                                                                                                                                                      |
| Fixed shell dimensions                    | sidebar `w-64` (256px); header `h-16` (64px); card radius `rounded-xl` (= `var(--radius)` + 4px, 12px); `shadow-sm` elevation                                                                                                                     |

### The card gutter is a constant, the cell density is not

A form never writes padding: `Card` supplies `py-6` and `CardContent` supplies `px-6`, so its content sits 24px inside the card for free. A table cannot get it the same way — wrapping a `Table` in `CardContent` would make every row's `border-b` and hover fill stop 24px short of the card edge, and a data table's row bands have to run full width.

So a table builds the same gutter from the **edge cells** instead: `TableCard` (`@/shared/ui`) applies `pl-6` to the first cell of every row and `pr-6` to the last. Primer's `DataTable` solves it identically (`.TableRow > *:first-child { padding-inline-start: … }`, offset "to make sure type aligns regardless of cell padding selection"); Material Design states the same target as "24dp of padding around the perimeter of table cards".

The consequence to keep hold of: **the 24px belongs to the card, the cell density belongs to the cell.** Changing one must not move the other. If a second density is ever genuinely needed, add it the way Primer does — condensed / normal / spacious tiers over one unchanged edge constant — rather than by nudging both numbers together.

The two are also bounded by different things, which is why the table's horizontal cell padding stays at the primitive's 8px while its vertical padding goes to 12px. **Horizontal padding is multiplied by the column count; vertical padding and the edge gutter are not.** For an N-column table, going 8px → 12px costs `8(N-2) + 8` px — the interior cells gain 8px each and the two edge cells only 4px, since their outer side is already pinned to the gutter. The 8-column キャスト一覧 is the widest list, so it is the binding case: **+56px**. The edge gutter costs a flat 32px whatever N is, and it buys the alignment this whole rule is about, so it is worth paying; per-column width is not.

As shipped, that list measures `clientWidth 702 / scrollWidth 702` at a 1024px window — no internal scroll. At 12px cells it measures 743 and scrolls. **This is a different bound from the 704px floor below**, and the two must not be conflated: the floor is about the _shell_ clipping controls out of reach, and horizontal scrolling there is the intended, documented outcome. The figure here is about a _table_ starting to scroll inside its own card at a width people actually work at, which is avoidable and therefore avoided. Measure with `scrollWidth` on `[data-slot="table-container"]` before widening a cell.

`TableCard`'s rules are descendant selectors, and their specificity differs by rule — the distinction matters because one of them reaches further than the call site:

- The density rules (`[&_th]:h-11`, `[&_td]:py-3`) compile to `.cls th` / `.cls td` = **(0,1,1)**.
- The gutter rules (`[&_tr>*:first-child]:pl-6`) compile to `.cls tr>*:first-child` = **(0,2,1)** — `:first-child` counts in the class column.

Both out-rank a `padding` a page writes on its own `TableCell` (0,1,0); no page does today, and if one needs to, change `TableCard` rather than fighting it at the call site. The (0,2,1) rules also out-rank `table.tsx`'s own `[&:has([role=checkbox])]:pr-0` (0,2,0), so **a checkbox column placed first or last would get the 24px gutter instead of the primitive's `pr-0`**. No table has a checkbox column today; the first one to add one has to resolve that here, not in the page.

### The admin console has a floor, not a phone layout

The console is a desktop surface. It is **not** made to reflow onto a phone, and pretending otherwise produces half-adapted screens: the sidebar already hides below `md` without putting any navigation in its place.

Instead the shell declares a **minimum supported width** — the content column carries `min-w-[44rem]` (704px, chosen above the header's **worst-case** requirement of 674px — heading 90 + action row 520 + horizontal padding 64, with the store selector stretched to its cap) and the shell scrolls horizontally below it. **The point is the failure mode.** Without the floor the shell's `overflow-hidden` silently clips whatever does not fit, and a clipped control is not merely ugly — it is unreachable. Horizontal scrolling keeps every control usable at any width.

So when a header or toolbar gains a control, the question is not "does it still fit on a phone" but "does the row's intrinsic width still fit under the floor". Measure it with `scrollWidth` on the row rather than eyeballing a resized window; the sidebar takes 256px whenever it is visible, so the viewport needs that much more than the row does.

Two traps this rule exists to catch, both hit in practice:

- **A single measurement is not the worst case.** A row containing user data (a store name, a person's name) is only as wide as whatever happened to be on screen when you measured. Cap such labels — an uncapped `whitespace-nowrap` label has no upper bound, and a floor cannot hold a row that has none.
- **Adding a cap changes the worst case.** The cap becomes the new maximum, so the floor must be recomputed **after** capping, not before.

### Storefront

Sections manage their own rhythm; follow existing `_sections/` patterns (max-w-7xl containers, px-5 lg:px-10).

## Components (admin)

Primitives come from `@/shared/ui` (the barrel over the vendored shadcn components). This section records **which primitive to use and how to compose it** — never a restatement of the styling already baked into the primitive.

- **Buttons**: `Button`. `default` = primary CTA, `outline` = secondary, `ghost` + `size="icon-sm"` = in-row actions, `destructive` = delete. Render links with `render={<Link href="…" />}`, keeping the label as the `Button`'s own children — the primitive has no `asChild`.
- **Form controls**: `Input` / `Textarea` / `Select` / `Checkbox` / `Switch` / `RadioGroup` / `Label`, wired per the form pattern below.
- **Modal (centered dialog)**: `Dialog` (`DialogContent` / `DialogHeader` / `DialogTitle` / `DialogFooter`). Tall forms add `max-h-[calc(100vh-2rem)] overflow-y-auto` on the content so the modal scrolls internally instead of overflowing the viewport (precedent: `StaffCreateModal`). Precedent for the plain case: `ShiftFormModal`.
- **Drawer (side-slide dialog)**: there is none. A record-scoped edit form opened from a list row is a centred `Dialog` like every other modal (precedent: `StaffEditModal`). Do not add a side-slide surface — the console has one modal idiom and a second one reads as a different application.
- **Confirm dialog (destructive confirmation)**: `ConfirmDialog` — controlled `open` + `title` (optionally `description`), with a destructive action button (`confirmLabel`, default 削除する) and a キャンセル cancel. Never call `window.confirm`, and never rebuild the pattern from `AlertDialog` parts in a page. Precedent: `CastFieldsPage`.
- **Combobox (searchable select)**: Base UI's `Combobox`, composed in place — `Combobox.Root` (`filter={null}` when the server does the filtering) + `Trigger` + `Portal` / `Positioner` / `Popup` holding `Input`, `Empty` and `List`. Precedent: `CastSearchCombobox`. **This is the one primitive not behind the `@/shared/ui` barrel**: it has a single consumer, so it is composed at the page layer rather than vendored, and the barrel gains a `combobox.tsx` when a second consumer appears — not before. (The old `Popover` + `Command` recipe is gone: `command.tsx` was cmdk-based and was removed with the Base UI migration.)
- **Destructive menu item**: do not use the vendored `DropdownMenuItem`'s own `variant="destructive"` — it keeps the default red as the text over its tinted focus fill (`text-destructive` on `bg-destructive/10`), which measures **3.99 in light mode** (dark passes at 4.58 over its `/20` fill). Spell the colours out on the consumer instead: `className="text-destructive-strong focus:bg-destructive/10 focus:text-destructive-strong"` — certified by the `bg-destructive/10` + `text-destructive-strong` row (8.42 / 5.39). The primitive itself stays as generated; this rule binds the call sites. Precedent: the logout item in `widgets/header`.
- **Card section heading**: `CardTitle` renders a `<div>`, so wherever it stands for a section heading it must be written `<CardTitle role="heading" aria-level={N}>`. Without both attributes the section disappears from screen-reader heading navigation, and the loss is invisible in a rendered diff. The primitive stays pristine — it spreads `React.ComponentProps<'div'>`, so the consumer supplies them. (Where the card is genuinely not a section heading — a bare label on a stat card — leave it a `div` and do not add the role.)
  - **Pick `N` from the outline the page actually has, never from the tag the card replaced.** Levels must not skip: a card section directly under the page's `<h1>` is `aria-level={2}`, whatever the pre-shadcn markup used. Every admin page today is `<h1>` + card sections, so **2** is the level in practice; a nested sub-section inside such a card would be 3. Sibling headings written as real tags follow the same outline (`CustomerEditPage`'s 注文履歴 is an `<h2>`, not an `<h3>`).
- **Table**: shadcn `Table` inside a **`TableCard`**, never a hand-assembled `Card className="py-0 …"` — the card gutter and cell density live in that one component (see Spacing). A list page gets it through `ListPage`; a table embedded in another page uses `TableCard` directly (precedent: `CustomerEditPage`'s 注文履歴).
- **Tabs**: shadcn `Tabs`. Precedent: `ShiftsPage`.
- **Toast**: never composed at a call site. `toast.tsx` is mounted once by `ToastProvider`, and a page reaches it only through `notify.success` / `notify.error` / `notify.warning` — which toast belongs where, and what each tier means, is the notification section below.
- **Loading placeholder**: never a hand-rolled `animate-pulse` block — that part is absolute. Between the two sanctioned forms, pick by whether the final shape is known before the data arrives:
  - **`Skeleton` sized with layout classes** where it is — a stat card's label and figure, a card body, table rows. The placeholder then occupies the space the content will take and nothing shifts when it lands. Precedent: `DashboardPage`.
  - **A plain `text-muted-foreground` "読み込み中..." line** where it is not, and inside a small sub-region of an already-drawn card. Faking a shape the content will not have is worse than saying nothing: the reader watches a layout that then rearranges itself. This form is sanctioned, not a leftover.
  - The text form is the majority today and **converting a site is not a drive-by**: the string is what several tests select by, so a conversion changes those assertions and belongs to the ticket that owns the screen.
- **Status pill**: `Badge variant="outline"` plus one tint recipe — `border-transparent bg-success/10 text-success-strong` (確定) / `bg-warning/10 text-warning-strong` (保留) / `bg-destructive/10 text-destructive-strong` (却下 / NG). Precedent: `CustomersPage`.
- **Stat card**: `Card`; label (`text-muted-foreground` 14px) → figure (`text-foreground` 30px bold) → trend row (`text-success-strong` delta + `text-muted-foreground` comparison); category icon chip top-right (`rounded-lg p-3`, 24px icon, `bg-chart-N/10` with a `text-foreground` icon).
- **Sidebar nav item**: 40px tall, icon 20px + label 14px. Active: `bg-primary/10 text-primary-strong` with a 2px `bg-primary-strong` edge bar. Inactive: `text-muted-foreground`, hover `bg-muted text-foreground`. Group headings are static `text-muted-foreground` labels — there is no collapse mechanism. Precedent: `widgets/sidebar`.
- **Progress bar**: track `bg-muted h-2 rounded-full`, fill `bg-primary-strong` (plain `bg-primary` is only 2.83:1 against the dark-mode track).
- **Ranking row**: 32px circular rank chip (`bg-primary/10 text-primary-strong`), name + area line (12px icon + `text-muted-foreground`), right-aligned amount (bold) over count (`text-muted-foreground`).
- **Selectable preview card**: `<label class="group">` wrapping an `sr-only` radio; `rounded-lg border p-3 cursor-pointer`. Unselected hover `bg-muted`; selected `border-primary ring-2 ring-primary bg-primary/10`. Body = thumbnail (`w-full rounded border`) → name (`text-sm font-medium`, selected `text-primary-strong`) → description (`text-xs`); keyboard focus via `has-[:focus-visible]:ring-2`. The description is the one part that cannot stay muted throughout, because **both** of the card's non-default surfaces put `text-muted-foreground` under the bar in light mode — 4.39 on the hover fill, 4.18 on the selected tint (dark clears both, at 5.66 and 6.25). So it is `text-muted-foreground group-hover:text-foreground` when unselected and `text-foreground` when selected. That is the same "the hover flips the fill and the text together" rule the Sidebar nav item and Mobile bottom tab bar entries follow; the `group` on the label is what lets the text follow a hover owned by its ancestor. Precedent: the template picker in `StoreProfileForm`.
- **Mobile bottom tab bar**: `fixed inset-x-0 bottom-0` `bg-card` with a `border-t` top edge; each tab is an equal-width flex column (`flex flex-1 flex-col items-center gap-1 py-2`), 24px icon above a 12px label. Active `text-primary-strong`, inactive `text-muted-foreground` with hover `bg-muted text-foreground`. The content area adds `pb-16` so the fixed bar never overlaps scrollable content. Precedent: `CastPortalShell`.

Hover / focus / disabled states come from the primitives. Only hand-write a state when composing bare elements, and then express it in tokens (`hover:bg-muted`, `disabled:opacity-50`) — never a raw hue.

A bare interactive element does not inherit the primitives' focus ring, so it hand-writes one: `focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary`. `ring-primary` is certified against `bg-card` / `bg-background` by the matrix row above (5.26 / 3.37) — and against nothing else, so an element on a muted or tinted fill moves its ring to a certified surface or stays with the primitive. Add `ring-inset` where an overflow container would clip an outer ring (a calendar cell inside a framed grid).

### List page shell

**The shell is a component, not a set of class strings to copy: `ListPage` from `@/widgets/list-page`.** It owns the outer spacing, the heading block, the search card, the table card wrapper, the loading / failed / empty branches and the pagination control. A list page passes `title / description / actions / search / state / emptyMessage / errorMessage / onRetry` and supplies its own table markup as `children` — nothing above the table is the page's to write, and a `space-y-6` or `text-2xl` surviving in a list page file means the shell was re-derived rather than composed.

The seven record-list pages are `store-customers` / `store-casts` / `store-orders` / `platform-staff` / `platform-roles` / `platform-stores` / `store-cast-fields`. Every other admin page — the form pages, the two settings consoles and `store-shifts` — still hand-writes its own heading markup, and for those the heading block alone is available as `PageHeader` (`@/widgets/page-header`; no page composes it today). Widening it to them is a separate question and is not authority to convert one in passing.

**The page heading is `text-2xl` at every breakpoint** — no `sm:text-3xl` step, no `font-semibold` variant. `ListPage` enforces that for the six; a hand-written heading elsewhere follows the same rule.

The paging state comes from `useListPage` (`@/shared/lib`), whose fetcher returns the normalized `PageResult` (`rows / page / pageCount / total`, `page` 0-based) that `shared/api`'s adapters produce from either wire format. A list backed by a plain array API omits `page / pageCount / total / onPageChange` and the shell renders no pagination.

Consequences that the module cannot enforce for the page:

- **The console layout already supplies `p-8` and `max-w-7xl mx-auto`** (`app/(admin)/platform/layout.tsx`, `app/(admin)/store/layout.tsx`), so a list page adds no padding, width cap or `min-h-screen` of its own — and no navigation bar: the sidebar and header own logout, store switching and cross-page navigation.
- **The primary action keeps its element type.** A page that opens a modal passes a plain `Button`; only a page that navigates passes `Button render={<Link … />}`. The two differ in ARIA role (`button` vs `link`), and the Playwright steps under `e2e/steps/` select by role — swapping one for the other breaks them silently, since e2e does not run in CI.
- **The search card submits through the shell's `<form>`.** The page's `search.content` is just the fields plus a `type="submit"` button; Enter submission is native form semantics, so no per-input `onKeyDown` is written.
- **The fetcher reads _applied_ filters, never the live input state.** Keep the typed value in state for the field and the submitted value in a `useRef`; the submit handler copies state into the ref and then calls `search()`. Reading input state directly breaks twice: a page change would silently apply a filter the user typed but never submitted (the fetched page then belongs to a different result set), and a handler that changes a filter and refetches in one go — a clear button — would fetch with the pre-update value. Precedent: `StoresPage` / `CustomersPage`.
- **An offset-paged list needs a total order.** The sort must end in a unique column, or rows shift across page boundaries and get duplicated or skipped between requests. Spring Data takes multiple keys in one parameter as `sort=prop1,prop2,direction`, so `displayOrder,id,asc` is the shape — `displayOrder` alone defaults to `0` for every cast and is not an order at all.
- **Dialogs, modals and drawers stay outside `ListPage`.** `children` is unmounted while loading and when empty; a modal placed there disappears mid-refetch.

## Notifications and failure states

Every message the console shows is placed by two decisions, taken in that order: **which surface it belongs on**, then **how heavy it is**. Surface comes first. Sorting severity alone would only recolour the banner that flies over a list which still reads 0 件 — the structural mistake is the surface, not the hue.

### Which surface (decide this first)

Run the precheck, then apply the three clauses **in order, and stop at the first one that matches**. The input to every step is _what happened_, never the wording's tone.

**Precheck — immediately after this message appears, does the screen carrying it still exist?** If it does not, toast is not among the choices; see "When the screen will not survive the message".

|     | Condition                                                                                                     | Surface                                                 |
| --- | ------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------- |
| ①   | A region that renders fetched content is now holding the wrong content (the fetch failed, or it is not there) | **the region names the failure itself.** No toast       |
| ②   | An input the user can fix on the spot is identifiable (client-side validation)                                | **beside that input.** No toast                         |
| ③   | Everything else — the outcome of an action                                                                    | **toast**, success and failure alike, with no exception |

Each clause's grounds, because each is a rule somebody will want to bend:

- ① Escaping to a toast leaves the region indistinguishable from an empty result, so the screen asserts something untrue while the truth lives in a banner that is gone in seconds. A failed fetch that falls through to a "not configured" branch is the same defect wearing different copy.
- ② A toast cannot point at a field, and by the time the user is back at the field it has expired. **In practice only client-side validation reaches this clause**, because `getApiErrorMessage` — the one path call sites use to turn an exception into copy — reads a flat `error` / `message` and nothing else, so a server-side failure arrives with no field attached to it and falls through to ③. That is a limit of the client path and not of the wire: a Bean Validation 400 does carry a per-field `details` map. **It is deliberately not fed back into field errors.** A 400 that names a field means our client-side rule for that field is missing, and the repair is to add the rule — not to build a return channel that reports the problem after the submit that ② exists to catch it before. A check that **can** be made in the browser belongs in ② however many fields it spans — a validator sees every value in the form, so a "the two passwords differ" mismatch is ②'s work and not a toast's, however it is written today. The catch-all at ③ is for checks that need state the client does not hold: uniqueness, authorization, anything about another user's data. That fallback is accepted rather than an oversight, so nothing is ever silent.
- ③ An event is not a state. Something with no place on the screen that is _currently like that_ cannot go on a surface that expresses state. A 409 conflict is an event too and stays here, even though missing it costs more than missing an ordinary write failure — that cost is answered by severity below, not by moving the surface.

**A "region" in ① is not only a list.** These rank equally: a list or table, a form's options (a `Select`'s items), a combobox's candidates, a detail page's own body, and a **sub-region inside a detail page** (an order-history block, a custom-field definition set). The failure mode is identical in all of them — the failure disguises itself as "nothing here" — so the size of the container does not change the clause that applies.

### When the screen will not survive the message

The precheck's exit. **A toast cannot outlive the screen that carries it.** Crossing between the two root layouts is a full page load and takes the `Toaster` down with it, so a message posted immediately before one of those is never read. Where that is the situation there are exactly two forms, and no third:

- **In place** — the screen says it outright before it goes away. This is the form for a dead end the user has to stop and read; an automatic logout becomes a button the user presses, so nothing disappears before it is read.
- **Reason code** — the landing screen states why it is there. The caller passes a reason as a query parameter and the landing side **resolves it against a whitelist of fixed copy**.

Three rules bind that second form:

- **Never render the query string.** Otherwise a crafted `?reason=<any wording at all>` link makes our login screen speak an attacker's words. An unknown value renders nothing.
- **The reason is passed by the caller.** Do not move `logout()`'s default destination — it is used by reference from several places, and changing the default lands a user who pressed logout themselves on a screen explaining something they never did.
- **Do not build a general mechanism for carrying copy across a transition.** The landing points are few, and the landing side only consults a lookup table.

A client navigation _within_ one root layout does not destroy the `Toaster`, so there the precheck answers yes and clause ③ applies as normal. The question is whether the screen carrying the message survives, never whether a navigation happens.

### Severity — the three tiers

Only clause ③ reaches a toast, and there it is one of three tiers. One question decides which: **after the failure, is the screen still telling the truth?**

| Tier      | Judgment                                                                                                                                                                                                           | Duration |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------- |
| `success` | The action completed                                                                                                                                                                                               | 3000ms   |
| `error`   | It did not complete, and the screen says so by itself — the modal is still open, the values are still in it, the button can be pressed again. The toast supplements the reason; missing it does not mislead anyone | 5000ms   |
| `warning` | It did not complete **and the screen can no longer state that** — the values in front of the user were replaced by something else. The toast is the only record of what was lost                                   | 10000ms  |

The third tier is named for what the reader has to do — someone got there first, re-check the data — but **its entrance test is the truth question above, not "must the user re-review"**. Keep it that way: a failure that leaves the screen unable to state the facts belongs here even when nothing was overwritten (a screen that vanished, say), and narrowing the test to conflicts would leave that member with nowhere to go.

Call sites write the meaning and never the colour, the duration or the icon: `notify.success` / `notify.error` / `notify.warning` from `@/shared/notify`, each taking the message and nothing else. That layer does not wrap `getApiErrorMessage` — turning an exception into copy is a separate job with consumers outside toasts, and folding it in would give `notify` two signatures. `@base-ui/react/toast` is imported nowhere but that layer and the `shared/ui/toast.tsx` that draws it, tests included: a call reaching the toast manager directly looks identical to `notify.error` at the call site, so it would leave the tier system silently. **That sentence describes the tree rather than a destination.** Of the 81 calls that used to reach a second toast library directly, **79** are inside the tier system now; the other two were clause ① wearing a toast, and left the surface altogether for the in-region error state rather than moving between tiers. The library they reached is gone from `package.json`, so there is no other manager left to call. `no-restricted-imports` in `eslint.config.mjs` holds the sentence, closing the `toast` subpath — and everything under it — to every file but those two. The rule reads static `import` and `export … from` only, so naming the module any other way — a dynamic `import()`, a `require()`, a `jest.mock` string — slips past it, and those forms stay enforced by reading.

**All three tiers are written the same way, and the work splits by kind rather than by tier.** The semantic layer names the tier and its duration and nothing else — `toastManager.add({ type, title, timeout })` — while `shared/ui/toast.tsx` draws every tier from that `type`. The library treats the tier as an opaque string and re-emits it as `data-type` on the toast root, so a tier's fill, its icon and its shape are all chosen in that one file. Adding a fourth tier is a `data-[type=…]` pair plus a branch in the icon map, and none of it lands in the provider.

**A toast with no tier is representable, and it paints the base pairing.** `notify` never issues one — each of its three methods names a `type` — so an untyped toast can only come from reaching the manager directly, which the import rule above forbids. Keep it that way: a stray toast painting a card surface is merely ugly, while one inheriting yellow with a stop-triangle and ten seconds is a **lie about severity**. Severity must never be something a toast acquires by accident.

The duration is the one part the layer holds rather than the primitive, and the reason is that it is not a look: it is a judgment about how long the reader needs, which is the same judgment the tier table above makes. Splitting it the other way — duration in the provider's default, appearance in the primitive — would break a tier's definition across two files joined only by a marker string, which degrades silently to a plain `error` if it is ever misspelled.

#### `warning` differs from `error` by colour and shape

`warning` takes `bg-warning` + `text-warning-foreground` — **already a matrix row** (6.23 / 11.58) — so **no new state semantic and no new matrix row**, and no token is added. Its icon is `TriangleAlertIcon`, picked by the primitive from the tier's `type`:

- **The triangle is the universal warning shape**, and this is the seat the Colors table already reserved for `warning`'s attention icon. Between this tier and `error` the difference is now hue **and** outline, which is what makes it readable at a glance rather than by comparison; the octagon that a red-on-red split would have needed is not used anywhere.
- The colour is `currentColor`, i.e. `--warning-foreground`, so it lands on that same row and clears both the 3:1 graphic bar and the 4.5 text bar.
- **20px**, matching the width of the other tiers' icon slot so all three start their text at the same place. The tier difference is carried by hue and shape, never by volume.
- **`shrink-0` is not optional**, and the reason is worth knowing before anyone "tidies" it away. The icon sits directly in the toast's flex row beside a title that is `flex: 1 1 auto`, so it inherits `flex-shrink: 1` / `min-width: auto` and nothing holds its width. Measured — with the octagon this tier first used, in this same row — it crushes to **10.7×20** and the glyph becomes a vertical ellipse; the mechanism is the row, not the glyph, so the triangle crushes identically. **It appears only when both modes are painted in a real browser**: neither static review nor jsdom sees it. Treat the class as part of the icon's identity rather than as styling, and do not "clean it up".

`error` and `success` are drawn on exactly the same terms: `CircleXIcon` and `CircleCheckIcon`, both 20px in `currentColor`, so each lands on the foreground token of its own certified row and clears both bars without introducing a pairing this document has not measured. **Both are enclosed, and that is what separates them from the dismiss control** — the dismiss button is a bare ×, so an unenclosed cross for `error` would put the same glyph on one toast twice, once meaning "this failed" and once meaning "close this".

The rule underneath all three is that **a colour pairing must be one this document measured**. Icons that arrive with a hardcoded fill — a library's built-in status glyphs, typically — are not an exemption from that; drawing our own is what keeps every mark on a row above.

#### Dismissal, ARIA and position are identical across the tiers

- **Every toast carries a dismiss button, at every tier**, and it is the same control everywhere; it carries no part of the tier distinction. The button is a bare × in `currentColor`, which keeps it on the tier's own certified pairing. The shared ghost `Button` is deliberately **not** used for it: that variant's hover paints `bg-accent`, an admin surface this table never measured against a coloured toast fill.
  - **It carries no resting opacity either**, and that is the same rule rather than a second one: a glyph at less than full alpha is composited with the fill behind it, which is by definition a colour this table has not measured. The margin is not academic — at `opacity-70` the `error` tier's × measures **2.64 in light** (from 4.57), under the 3:1 graphic bar, while `warning` and `success` land at 4.10. Dimming a control at rest and restoring it on hover does not answer this: the state that has to clear the bar is the one before anyone has found the control.
- **The keyboard route has three stops, and the focus indicator is settled separately at each one.** <kbd>F6</kbd> moves focus to the viewport, <kbd>Tab</kbd> from there to a toast, <kbd>Tab</kbd> again to that toast's dismiss button. What decides the answer at a given stop is **what the ring is drawn on**, and that is not always what the element itself paints: `ring` is drawn _outside_ the border box unless it is written `ring-inset`, so an element's own fill is its ring's ground only when the ring is turned inward.
  - **On the toast and on its dismiss button the ring is `currentColor`, and on the toast it is also `ring-inset`.** This is the one place in the console where `--ring` is the wrong answer. That token is drawn for the admin surfaces — it manages 2.62 / 3.67 on `bg-card` — but a ring landing on a saturated tier fill measures **1.22 / 2.80** (`warning`), **1.82 / 1.67** (`error`), **1.23 / 2.71** (`success`) at full opacity, and the usual `/50` takes all six down to 1.04–1.67. `currentColor` puts the ring on the tier's own certified row instead. The button needs no `ring-inset` because it already sits within the card; the toast does need it, because its outer edge is where the fill stops.
  - **On the viewport the ring is the browser's own, and that is the deliberate part** — it is the one element here that does not write `outline-none`. The viewport paints nothing, so a ring of ours would land on whatever the page happens to be showing beneath the notification, which is not a colour anyone can measure while writing it; this is the same problem the translucent-fill note above declares untabulatable, and the same answer is unavailable because there is no fill to make opaque. `currentColor` is the worst choice of all there: in dark mode every tier's foreground token is defined identically to `--background`. So this element borrows the browser's own indicator, which is the only one drawn without knowing the ground. **Read the strength of that borrowing narrowly**: what was observed is Chromium's `outline: auto` ring, two-toned and legible over both a white and a near-black ground in the same screenshot. An engine that instead honours the author's `outline-color` under `auto` gets the base reset's `* { outline-ring/50 }` — the weak value this section spent its length arguing against. That is accepted rather than solved, and it is bounded: the viewport is a waypoint holding no control, while both stops that do hold one carry a certified ring of their own. **Do not tidy an `outline-none` onto the viewport**: <kbd>F6</kbd> would then change nothing on screen in any engine, and a key that appears to have done nothing does not get pressed twice.
- **The notification layer sits above every overlay**, at `z-[60]` against the `z-50` shared by `Dialog`, `AlertDialog`, `Popover`, `Select` and the menus. Equal values would not be a tie: the toast viewport is portalled with the layout and a dialog is portalled when it opens, so the later one paints on top — and the `error` tier is _defined_ by the modal still standing, which makes "a backdrop dims the message and swallows the click on its dismiss button" the failure mode of the tier's own core case. The storefront's age gate (`z-[9999]`) deliberately stays above notifications; nothing may paint over that one. **jsdom resolves no stylesheet, so no unit test sees a z-order regression** — this is a browser check or nothing.
- **The message is the toast's accessible name, and it is not a heading.** `Toast.Root` points `aria-labelledby` at the title part, so the text has to live there — routed to the description instead, the dialog would surface with no name at all. But that part renders an `<h2>` by default, which would file every notification into the page's heading outline and let heading navigation land on 「保存に失敗しました」 as a section. It is rendered as a `<span>`; the id moves to the replacement, so the name still resolves. **The description part is unused**, and deliberately: a tier is one factual sentence, and a fixed per-tier title above it would only restate the severity the icon and the `警告：` prefix already carry.
- **That button is `aria-hidden` until the viewport is hovered or holds focus**, which is the library's own wiring and is right. The words reach assistive technology through the viewport's live region, so the control only has to exist in the accessibility tree once a reader is at it — and <kbd>F6</kbd> moves focus into that viewport, which is what makes it reachable without a pointer. A test that looks for the button by role will not find it; query the `data-slot` instead.
- **The viewport is `role="region"` / `aria-live="polite"` / `aria-label="Notifications"`, each toast is `role="dialog"` with `tabIndex=0`, and the position is top-centre.** Those are the library's defaults and stay that way, `error` and `warning` included. There is no per-tier override to write. A tier is never escalated — the library would make the root an `alertdialog` for a high-priority toast, and interrupting a reader mid-utterance is not how severity is conveyed here.
- **So the severity goes into the words.** Hue and shape are both invisible to assistive technology — lucide emits `aria-hidden="true"` on every icon, so the triangle is not merely unlabelled, it is absent — which leaves the copy as the only channel that reaches everyone. **The semantic layer prefixes the `warning` tier's message with `警告：`**, so the tier cannot be announced without its severity and a new call site cannot forget it. Call sites pass the factual sentence only.
- **Nothing else interactive belongs in the body.** The dismiss button is the toast's whole interactive surface; recovery affordances belong in the failing region, which is where clause ① already puts them.
- The limit that remains, named here rather than implied away: **a reader who needs longer than the duration has to hold the toast to keep it.** Hovering pauses the timer, and so does moving focus into the viewport — so unlike a pointer-only pause this one does answer the keyboard case — but the countdown resumes the moment either ends, and all three durations stay finite. The reading to carry forward is therefore that **a toast is a poor sole record** — where a failure leaves lasting state, the durable answer is for the screen holding that state to say so, which is clause ①'s logic applied to a modal whose values were just replaced. Growing the timer is not that answer.

### The in-region error state (clause ①'s shape)

One shape covers every case — a list, a `Select`'s options, a whole detail page, a sub-region inside one: **one line of red copy plus one outline button.** Only the outer placement differs, and placement is the caller's `className`. The copy is `text-destructive-strong` — hand-written colour takes the `-strong` form, and that one is certified on both surfaces such a region sits on (`bg-card` 10.06 / 6.13, `bg-background` 10.06 / 6.88) — and the button is `Button variant="outline"`. Nothing in this shape needs a pairing the matrix does not already have.

- **The retry button is always there.** Copy that tells the user to reload the page is not an alternative: it charges them anything half-typed elsewhere on the screen. A region that names its own failure owns its own recovery, or it has merely moved where it shouts "broken".
- **On failure the region clears completely, and there is no exception.** No stale rows left standing, and no "this is the previously loaded content" caveat either. Rows and paging position both return to the start, so **retry always reads from the first page** — a retry resuming from a cursor would return rows 21–40 over a missing 1–20. This applies to a failed load-more exactly as it applies to a first load.
- **Do not add a gate because a fetch failed.** No disabled submit button, no "cannot save while this is broken" branch: a failed fetch of supporting data is about as likely as a flicker in the line, and is not worth a blocking mechanism. Two things follow, and they are different:
  - **Required-field validation stays exactly as it is**, which means a field whose options never arrived stays empty and its own required message still stops that submit. That is the correct outcome, not a hole: clause ① has the region naming its failure, and the two messages together state the fact instead of blaming the user for a system fault.
  - **A failed pre-flight inquiry** — a uniqueness check and its kind — does not stop anything, because the server re-validates and is the final authority, so letting it through costs nothing.
- **A detail page does not navigate away.** The region of a detail page is the page, so it stays put and shows the error state itself. 404 is distinguished from other failures by **copy and recovery affordance, not by navigation**: a 404 gets a link to the list and **no** retry button, since retrying will never succeed. Navigating away would delete the failure from the screen and hand the whole explanation to the notification — the shape this section exists to remove.
  - **A modal whose subject was deleted does not use this shape at all.** The list link has nowhere to point — the modal is already open _on_ that list, so following it would navigate to the current URL and leave the modal standing. So the modal takes the "In place" form from the section above: its **body** states the fact, and **its own close button is the recovery affordance** — rename the cancel action, drop the save action, and refresh the list on the way out so the deleted row cannot be opened again. Being the modal's own body rather than `RegionError`, it is drawn in the dialog's spacing and keeps the footer's full-size button; what it borrows from this section is only the container's `role="alert"`, and for the same reason — the failure lands after the dialog has opened, so there is no focus event left to ride. The 404 **rule** is unchanged (never a retry; an exit that actually works); only the component carrying it differs, because here the exit cannot be a link.
- **The region announces the change: `role="alert"` on the shared component's container.** The failure arrives after the region has mounted and clause ① has ruled out the toast, so without this a reader not watching that part of the screen gets nothing at all — the rows they were working through vanish and a retry button they cannot see appears. ② is held to the same standard, and it meets it through focus plus `aria-describedby`; ① has no focus event to ride, so it needs the live region. `role="alert"` rather than `role="status"` because the component **mounts on failure**, and a polite live region inserted together with its own content is the case screen readers routinely miss — a mechanism that silently does not fire is worse than none. Its implicit `aria-atomic` reads the container whole, so the retry button's label is announced with the copy and the reader learns that recovery exists.
  - **Do not move focus** instead. It is the stronger signal, but this clause covers a form's options failing too, and yanking focus out of the field someone is typing in is worse than the silence it fixes.
  - The known limit, recorded rather than papered over: announcement-on-insertion still varies between screen readers. The bulletproof form is a live region that is **already** in the DOM with only its contents changing, and the three-prop mount-on-failure shape deliberately does not buy that.
- The shape lives in **one hand-written component in `shared/ui`** (the side without `data-slot`) with three **semantic** props — the copy, a retry handler, and the alternative link for 404 — **plus a `className` passed through**, since placement is the caller's and every hand-written component in that directory already accepts one. Three is the number of decisions the component makes, not the number of attributes it accepts.
  - **Exactly one recovery affordance, always.** Two independently optional props would let a caller pass **neither** — an error with no way out, which the retry rule above forbids — or **both**, which the single-button shape and the 404 rule forbid. Those two states should be unrepresentable in the type rather than left to review: a discriminated union on the recovery arm is the obvious shape, and choosing its exact signature belongs to the ticket that builds the component. Adding to or editing `shared/ui` is a shared-file change and goes through the parallel-PR contract below.

### Where the state behind clause ① comes from

Clause ① says what a failed region shows. **The lifecycle that produces that state is not written per page.** A single resource — a detail page's own body, a `Select`'s options, a sub-region's history or statistics — is fetched through **`useResource`** in `shared/lib`, the one-value sibling of `useListPage` / `useManagedList` / `useCursorList`. It returns `{ data, setData, isLoading, failure, reload }` and owns the four invariants that hand-written copies of it drifted on:

- **A request counter discards out-of-order responses.** A stale in-flight failure must not erase a newer success, and StrictMode's double mount produces exactly that order on every load, so this is not a rare race.
- **A failure clears the data**, which is what makes ①'s "the region clears completely" true rather than aspirational.
- **The failure folds when a retry starts**, so a second failure re-mounts `RegionError` and the live region announces again.
- **404 is classified apart** — `failure` is `'notFound' | 'error' | null`, decided by `isNotFound` — because the two get different recovery affordances.

Three rules bind the call site:

- **Branch on `failure !== null`, never on `data === null`.** "Not loaded yet" and "could not be loaded" are different states and only the second one names a failure. `failure === 'notFound'` selects the list link; everything else gets the retry button. A sub-region that has no 404 copy of its own still branches on `failure !== null` — otherwise a 404 falls through to nothing at all.
- **A region that has not loaded yet says so.** `isLoading` gets a visible state of its own even where the region is small: a `Select` whose options are still in flight is not "no receptionists", exactly as a failed one is not "no receptionists". That is ①'s defect one moment earlier, and it is not answered by the failure state.
- **The render that reveals a form already carries its values.** An effect that copies `data` into form state lands _one render after_ the fields appear, so that render draws them empty — the same "wrong content" defect ① is about, one moment earlier and easy to miss because the correct values arrive a frame later. Two shapes satisfy this, and which one applies is decided by where the form's state lives: for plain `useState`, **derive** rather than copy (`draft ?? data`, with the draft tied to the `data` it was started from, so a later fetch is not shadowed by a stale draft); for **react-hook-form**, whose values live inside the form, seeding is necessarily an effect, so **hold the fields back until the seed has landed** rather than revealing them a render early. Where a successful write returns the new value, `setData` puts it in place instead of a re-read that blanks the region.

A `null` fetcher means "no reason to fetch yet" — a modal that is closed, a detail id the URL does not carry. Nothing is requested and nothing already read is thrown away.

The hook presents nothing, by the same rule the list hooks state: whether a failure is named in place or carried to a toast is the call site's decision, taken with the table above.

### Client-side validation (clause ②'s shape)

- **The browser's own bubble does not satisfy ②.** Only text we draw does.
- **The browser must not be able to pre-empt the handler**, and the way that is guaranteed is **`noValidate` on the `<form>`**. While any unmet native constraint is live the browser stops the submit before `handleSubmit` runs and our message is never drawn — and the class is wider than it first looks: `required`, `minLength`, `min`, `type="email"`. One attribute on the form settles all of them at once, including the next constraint someone reaches for, instead of requiring the list to be re-inventoried each time.

  - **`noValidate` goes on a form only in the same change as a message-bearing rule for every native constraint that was doing real work there.** Adding it first is a silent weakening: today a `type="email"` is the _only_ format check on the login form's address, and a `min={1}` is the _only_ range check on an order's 人数, whose `register` call carries no rules at all. Suppress native validation without replacing those and a malformed address or a `0` reaches the submit callback, round-trips to the server, and comes back as a toast — the exact inversion of what clause ② is for. Treat "the attribute is only a keyboard hint" as true **after** the rule exists, never as the reason to skip writing it.
  - **The attributes then stay, because they earn their keep for reasons other than validation**: `type="email"` brings up the right keyboard, `min` gives a number spinner its floor, and native `required` is what tells assistive technology the field is required — `FormControl` emits `aria-invalid` and `aria-describedby` but **not** `aria-required`, so keeping the attribute is cheaper and less forgettable than hand-writing that on every required field.
  - **That route covers native inputs only.** A primitive-controlled field has no native attribute to keep, because its focusable element is a button — so **pass `required` to the primitive instead**: `Select`, `Checkbox`, `RadioGroup` and `Switch` each emit `aria-required` on their own focusable element from it, and the vendored wrappers spread props through, so it reaches them. That is the whole sanctioned set from the form pattern — a required boolean rendered as a `Switch` is covered the same way as one rendered as a `Checkbox`. Do not hand-write the attribute, and do not assume `rules.required` covers it — that configures react-hook-form and never reaches the markup.
  - **That is right only where the single control is itself the required thing** — a `Select` that must be answered, a `RadioGroup` where one of N must be picked, a lone consent checkbox. **For "at least one of N" it is wrong**: stamping every member says each one is mandatory, which describes "tick them all". Requiredness there belongs to the **group**, and `aria-required` has no valid home on a generic grouping container, so the group's own label and description carry it while the `FormMessage` is associated with the group rather than with any member. Note what the group looks like today before assuming there is something to hang that on: the role picker is a bare `<span>` over loose native checkboxes with its "1 つ以上" hint in an unassociated `<p>` — no group role, no label association. Building the group is part of the work.
  - **`minLength` comes off**, since with native validation suppressed it enforces nothing, announces nothing, and only misleads the next reader into thinking it still guards something.
  - The cost, stated so it can be reviewed for: requiredness is now asserted in two places while only the react-hook-form rule enforces it. **The rule is the enforcement and the attribute is the hint** — an input carrying `required` with no matching rule is a bug, and a greppable one.

- The form is **`FormField` (the sanctioned `Controller` — never a bare one) + `rules` + `FormMessage`**. `FormField` is this repository's standing answer to "react-hook-form can only grab a real `<input>`": every `Select` here is wired that way, and one native `Input` is wrapped in it for no reason other than drawing a message. Where the check spans a group rather than one field ("select at least one"), `rules.validate` is the form it takes, since `required` cannot express it. A hidden input added to give react-hook-form something to grab is not a precedent to copy from.
  - **That vehicle is the admin world's, not every world's.** `FormMessage` emits `text-destructive` — an admin token — and the scope rules at the top of this document forbid admin tokens on the auth screens, which is exactly where the login, cast-invite and member-register forms live. ② binds those forms identically, but there the message is drawn in `auth.css`'s own vocabulary and owes the same association by hand: `aria-invalid` on the control and an `aria-describedby` pointing at the message element. **What it must not reuse is the existing `auth-alert--error` variant**: at that file's 14px it is `#dc2626` on `#fef2f2`, which computes to **4.42** and so misses the 4.5 bar for normal text. That variant is a pre-existing page-level alert and stays as it is; field feedback is a new and far more frequent surface, so the auth world needs a field-error pairing computed to clear 4.5 and recorded in its own terms — the contrast matrix above governs admin tokens and has no jurisdiction here, but the bar is the bar. The login form named in the `noValidate` note above is one of these, so its native `type="email"` is replaced along that path rather than this one.
- **A rule without a message is a silent failure.** `rules: { required: true }` records an error carrying no message, and `FormMessage` renders nothing for an empty one — so the submit stops and the screen says nothing at all, which is worse than the browser bubble this section replaced. Every rule carries its own copy: `required: '…'`, and a validator that returns the explanatory string rather than `false`. This is the line the tree violates most — many controlled fields are still wired `required: true`, including some already inside a `FormField`, so their `FormMessage` is decoration today. Converting them is a separate piece of work; writing a new one that way is not available.
- **"Beside the input" covers the programmatic relation, not only visual proximity.** `FormControl` emits `aria-invalid` and an `aria-describedby` pointing at its `FormMessage`; a hand-written `<p>` emits neither, so a screen reader hears "a line appeared somewhere" instead of "this field is wrong, and this is why". Routing everything through `FormMessage` is also what ends the class-name drift between hand-written error paragraphs — its `text-destructive` is the emission the Colors section names as allowed, and where copy genuinely has to be hand-written it takes `-strong` like any other hand-written colour.
- **Never disable the submit button because validation has not passed.** Let it be pressed, show the messages, and move focus to the first problem — `handleSubmit` already does that. It does it by focusing the field's registered ref, so **a controlled primitive gets the focus move only if `field.ref` reaches its focusable trigger**: the `Select` recipe below passes `value` and `onValueChange` alone, and a field left that way drops the focus move without any other symptom. A greyed-out button does not say what is still missing, and keyboard or screen-reader users may not even be able to put focus on it to find out. (`disabled={isSubmitting}` is unrelated: that is double-submit protection, not validation.)
- Moving a value into react-hook-form pulls in the form's structure, and that is in scope rather than a reason to keep a hand-written paragraph: a modal with no `<form>` element at all, a hand-rolled submitting flag that something else depends on, a `reset()` after a 409 whose reach widens once more fields are registered.

**Neither scope seals the submit pre-emptively, and that is the direction the two share.** A **failed fetch** adds no gate of its own; **failed validation** does not disable the button. What may stop a submit is only a fact stated about the input — a required field that is genuinely empty stops it and says which field, whether its options never arrived or the user simply skipped it — never a precaution taken in advance on the user's behalf. One scope is a system fault and the other is the user's own input, but the rule is identical: put the fact into words instead of closing the door before it is pressed.

## Admin restyle rules (shadcn sweep)

Rules for converting a remaining admin slice to the primitives + token vocabulary. Slices are converted independently and in parallel, so these are contracts, not suggestions.

### Form pattern

Keep native inputs bound straight to `register` and swap the element for the shadcn `Input` / `Textarea`. Reach for `FormField` where a Radix-controlled component needs a controlled value (`Select` / `Checkbox` / `Switch` / `RadioGroup`), and wherever a validation message has to be drawn — `FormMessage` and `FormControl`'s `aria-invalid` / `aria-describedby` resolve through `FormField`'s context and emit nothing outside it, native input or not (see "Client-side validation" above). Nothing else needs it.

**One registration path per field.** What moves inside a `FormField` takes `{...field}` from its render prop and **drops its `register` call** — `FormField` _is_ the `Controller`, so keeping both registers the same name twice and leaves the controller and the element contending over ref, rules and reset. Precedent: the 人数 field in `ReservationRequestEditModal`, a native `Input` wired entirely through `field`.

**A `register` option that transformed the value has to be reproduced when the field moves.** `{...field}` has no equivalent of `valueAsNumber`, so `field.onChange` receives the number input's string and the payload starts carrying `"3"` where it carried `3`. Restore it at the `onChange` boundary the way "Type restoration" below prescribes for Radix values — but **restore the emptiness, not merely the type**. `Number()` is not the equivalent: `Number('')` is `0` where `valueAsNumber` is `NaN`, and that difference is load-bearing. A cleared number field currently serializes as `null` precisely because `NaN` slips past a `?? undefined` and `JSON` turns it into `null`; coerce with `Number()` instead and the same field submits a real `0`. There is a test pinning that chain, and "Behavior preservation" makes the submitted payload the bar. The exact equivalent is the one the DOM already offers — hand `e.target.valueAsNumber` to `field.onChange` — so reach for that before inventing a conversion, and check the cleared and unparseable cases rather than only the happy one. Wrap the whole form in `<Form {...form}>`. Never introduce a bare `Controller`.

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

An "unset" option carries a sentinel value rather than `""`, and the boundary converts it back so the submitted payload keeps its empty string (precedent: `OrderForm`). Base UI represents "no value" as `null`, not `""`, and whether `value=""` is accepted has not been tested here — the sentinel stays the repository's form:

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

The primitives hand back strings and tri-state booleans; restore the original type at the `onChange` boundary: `onValueChange={v => field.onChange(Number(v))}`, `v === 'true'`, `onCheckedChange={v => field.onChange(v === true)}`.

### Tint vs solid

Status labels and badges use the tint recipe (`bg-<token>/10 text-<token>-strong`). Solid (`bg-<token> text-<token>-foreground`) is for **filled surfaces** such as timeline bars. Pick between them by what the element is, not by how big its label is: both clear AA for normal-size text, so neither imposes a minimum size or weight.

This holds for `success`, `warning` and `destructive` — the three semantics whose solid form appears as a row in the contrast matrix. It is **not** a general property of "base token plus its `-foreground`": that pairing is only sound where it has been measured in both modes, which is why the matrix, not the naming pattern, is the authority. `primary` in particular has a solid form for CTAs only (`bg-primary` + `text-primary-foreground`); it has no tint-with-`text-primary` form.

### Behavior preservation

Submitted payloads, react-hook-form wiring and existing `ConfirmDialog` confirmation flows are preserved exactly. Anything that would change the payload — turning a free-text field into a `Select`, normalizing a value — is a separate issue, not part of a restyle.

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
- the vendored shadcn primitives, kept exactly as generated (`badge.tsx` and `button.tsx` carry `text-white`; `dialog.tsx` and `alert-dialog.tsx` carry `bg-black/50`).

Everything else that matches the grep — including `widgets/header` and the `app/` route shells — is **pending, not exempt**; a partially converted file counts as matching. **As of the sweep-cleanup pass, those four vendored lines are the only matches left in `src/**` outside the storefront and auth worlds, and raw hex in admin `.ts`/`.tsx` is at zero.** That is a measurement, not a guarantee: it is not enforced by a test, so re-run the grep rather than trusting this sentence.

### What in `shared/ui` may be edited

`src/shared/ui` holds two kinds of file, and only one of them is frozen.

- **Vendored shadcn primitives** — `alert-dialog` / `button` / `card` / `dialog` / `select` / `form` / `table` / `badge` / `popover` / `skeleton` / `tabs` / `dropdown-menu` / `checkbox` / `switch` / `radio-group` / `input` / `label` / `textarea`. Kept exactly as generated; per-screen deviation goes in the consumer's `className`, never here.
- **Kizuna-authored components** — `image-upload.tsx`, `auth-layout.tsx`, `theme-provider.tsx`, `confirm-dialog.tsx`, `table-card.tsx`, `toast.tsx`, `region-error.tsx`. Hand-written, so they obey the token rules like any other admin file. `auth-layout.tsx` is the exception noted above: it belongs to the auth world.

  `toast.tsx` is on this side even though it wraps Base UI parts and emits `data-slot`, because the tier system is ours: which `type` gets which fill and which glyph is a decision this document makes, so the file is edited when a tier changes rather than frozen.

  `table-card.tsx` is where a per-context deviation from a primitive is _supposed_ to live: the table's card gutter depends on what the table is sitting in (the same rule baked into `table.tsx` would double to 48px inside an already-padded `CardContent`), so it belongs to the consumer side, composed once rather than copied.

Tell them apart by the generator's fingerprint rather than by guessing. The strongest single marker is **`data-slot`**, but it neither is a literal grep nor separates the two sides on its own: all 18 vendored files emit it, yet `button.tsx` and `badge.tsx` produce it from `useRender`'s `state` (`state: { slot: 'button', … }`) instead of writing the attribute, so 16 of the 18 match the text — and `toast.tsx` writes it while being hand-written. The other markers are only suggestive — a `@base-ui/react` import is absent from 6 of the 18, and `cva` from 15 of the 18, so "some of these, not all" is the honest reading and none is usable alone. The negative direction is what holds: six of the seven hand-written files carry none of the markers, and either import application code a generator would never emit (`@/shared/api`, `@/shared/notify`, `next-themes`) or, as `confirm-dialog.tsx`, `table-card.tsx` and `region-error.tsx` do, compose sibling primitives through relative imports where the generator emits the `@/shared/ui` alias. **The list above is the authority, not the fingerprint** — the fingerprint is how to check a file the list has not caught up with, and `git log --follow` settles any remaining doubt.

Editing a hand-written one is still a shared-file change, so it goes through the contract below rather than through a slice PR.

### Parallel-PR contract

A slice restyle PR does **not** edit the shared files — `src/shared/ui/**`, `src/app/globals.css`, `setupTests.ts`, the `shared/ui` barrel, or this document. If something is missing, raise it in the PR instead of patching it locally.

### jsdom shims

`setupTests.ts` supplies three stubs for all tests:

```ts
globalThis.ResizeObserver = ResizeObserverStub;
Element.prototype.scrollIntoView = jest.fn();
globalThis.PointerEvent = class extends MouseEvent {};
```

`scrollIntoView` is needed by any test that **opens** a `Select`: the popup scrolls the selected item into view from a mount effect, so the failure (`candidate?.scrollIntoView is not a function`) happens on open regardless of how it was opened. A `DropdownMenu` test passing without the stub proves nothing — that component has no such effect, so it is not a precedent to copy from.

`PointerEvent` is needed by `Switch` and `Checkbox`. Their trigger relays the click to a hidden `<input>` by constructing a `PointerEvent` (so the modifier-key state survives, which `click()` drops); jsdom does not implement the constructor, so without the stub the handler throws and `onCheckedChange` simply never fires — the test sees a click that did nothing, with no error naming the cause.

Add a further stub **only after seeing a test fail without it**, and put it here rather than in the test file so parallel branches converge on one text. In particular, the pointer-capture trio (`hasPointerCapture` / `setPointerCapture` / `releasePointerCapture`) is **not** required: no test reaches that path. Adding it pre-emptively is how unreachable code accumulates in a file every branch has to merge.

### Driving the primitives from a test

Two rituals, both forced by the library rather than by choice:

- **Open with `fireEvent.click(trigger)`.** The triggers are real buttons and open on click.
- **Confirm a menu or list item with `fireEvent.pointerDown(item)` followed by `fireEvent.click(item)`.** A `Select` / `Combobox` item ignores a mouse click that was not preceded by a `pointerdown` on the same item (the guard exists so that opening the popup under the cursor cannot select whatever lands there). A click on its own leaves the value untouched and the test fails on the payload, not on the interaction.

`Select` also decides the trigger's text from the `items` prop, not from the selected `SelectItem`'s children, so a test asserting the trigger's text is really asserting that the consumer passed `items` — see "Select sentinel" above.

### Two DOM facts worth knowing before writing a selector

- **`FormControl` overwrites the wrapped primitive's `data-slot`.** It injects its own props into the single child, and on a collision the injected value wins — so a `Select` inside a `FormControl` reports `data-slot="form-control"`, not `select-trigger`. The functional half (`id`, `aria-invalid`, `aria-describedby`) still lands. Query by `role` for these, not by `data-slot`.
- **`Checkbox` and `RadioGroupItem` do not render a `<button>`** (the primitives opt into that only via `nativeButton` + `render`), so `:disabled` never matches them and the disabled dimming has to be written as `data-disabled:`. `Select`'s trigger, `Button` and `Tabs`'s tab _are_ real buttons, so those keep `disabled:`. Measured, not assumed: shadcn's own Base UI recipe still writes `disabled:` on the checkbox.

## Do's and Don'ts

- **DO** use token classes for admin colors; if a needed semantic is genuinely missing, extend `globals.css` and THIS file in a dedicated PR.
- **DO** reach for an existing primitive in `@/shared/ui` before composing one from bare elements.
- **DO** keep storefront changes token-driven: template look changes go through `theme.css`, structural blocks through shared `_sections/`.
- **DON'T** introduce raw palette classes or raw hex values in admin UI markup.
- **DON'T** restyle the vendored primitives in `src/shared/ui` at all — they are kept as generated, and per-screen deviation goes in the consumer's `className`. The hand-written components in that directory are a different matter; see "What in `shared/ui` may be edited".
- **DON'T** fork or restyle `_sections/` components per template; differences live in tokens and page layout only.
- **DON'T** invent new visual patterns when a component above fits; extend the pattern list instead if genuinely new.
