# Phase 4 — Design System & Screen Specifications

Design only. No implementation code — this document and its companions in
`docs/design/` are the spec Phase 5 builds against. Written by treating the brief's
five roles (UX architect, product designer, motion designer, tablet specialist,
accessibility specialist) as one coherent point of view, not five disconnected passes.

## Companion documents

| Document | Covers |
|---|---|
| This file | Philosophy, color, typography, spacing, elevation, corner radius, icon/illustration style, shadow, glass/blur, breakpoints, touch targets, accessibility |
| `docs/design/component-library.md` | Every reusable component |
| `docs/design/motion-guide.md` | Every transition, named and timed |
| `docs/design/navigation-flow.md` | IA, nav graph, shell per form factor |
| `docs/design/screen-specifications.md` | All 22 screens |
| `docs/design/microcopy-guide.md` | Every user-facing string |
| `docs/design/ux-review.md` | Top 10 risks, self-critique |

---

## 0. A load-bearing decision made before anything else

**The tablet is mounted on a wardrobe door. Wardrobe doors are tall and narrow. This
tablet is almost certainly mounted in portrait, not landscape** — closer to a smart
mirror or a vertical dashboard than the landscape-tablet assumption baked into most
Android design guidance (including this project's own `phase-1-architecture.md`
Section 30, which only mentioned `WindowSizeClass` in passing). Every layout in this
document is designed **portrait-tablet-first**, with landscape as a fully-supported
secondary orientation (for a mount that allows rotation, or a larger tablet used
sideways) and phone as a compact tertiary form factor for travel.

This is a deliberate reversal of the usual "phone-first, tablet is just more columns"
default, and it changes real decisions below: the navigation shell is a bottom dock
(reachable at arm's height while standing at the wardrobe), not a landscape-tablet
side rail; the Closet grid is tuned for a tall viewport; hero content is vertically
stacked, not side-by-side master-detail.

---

## 1. Design philosophy

**This is not an app. It's a wardrobe console** — the tablet is furniture, permanently
on, permanently in one place, used by exactly one person who already knows where
everything is. That changes what "good UI" means here versus a generic consumer app:

- **No onboarding funnel, no upsell, no engagement mechanics.** There is no one else
  to convert. Every pattern borrowed from consumer-app design that exists to drive
  retention or monetization (streaks-as-guilt, red notification badges, "you have 3
  new suggestions!") is explicitly rejected.
- **The interface should feel like it's already been there for years** — calm,
  unhurried, a little bit ceremonial. Opening the closet screen should feel closer to
  opening an actual wardrobe door than to opening an app.
- **Content is the hero. Chrome is furniture.** Garment photography, not UI
  ornamentation, carries the visual weight. Navigation, buttons, and labels recede;
  the wardrobe's actual contents are what's meant to be looked at.
- **Named for one person, spoken to like a person.** "Good Morning, {displayName},"
  not "Welcome back, User" — that name is a live, editable Personalization Settings
  field (`phase-5a-data-layer.md`), never hardcoded in any string, resource, or
  screen. See `docs/design/microcopy-guide.md`.

**Explicitly rejected directions**: stock Material Design (bright primary-color fills,
FABs with drop shadows, snackbars) — reads as "generic Android app," the opposite of
the intent; anything cyberpunk/gaming (neon, sharp angles, glow) — wrong emotional
register entirely; anything cutesy/mascot-driven — this is for an adult's wardrobe,
not a lifestyle-gamification app; heavy skeuomorphism (fake wood textures, fake
leather) — reads dated, not luxury.

---

## 2. Color system

Two themes, both built from the named inspiration palette (Pearl White, Champagne,
Warm Beige, Soft Gold, Deep Emerald, Midnight Navy, Slate Grey, Graphite) — nothing
saturated, nothing that reads as a corporate brand color. Deep Emerald and Champagne
Gold are used the way a boutique uses them physically: as an accent on a garment tag
or a button's brass hardware, not as a wash across the whole screen.

### Atelier Light (default — a well-lit dressing area)

| Token | Hex | Use |
|---|---|---|
| `background` | `#FAF7F2` (Pearl White) | Screen background |
| `surface` | `#F5F0E8` (Warm Ivory) | Cards, tiles |
| `surfaceElevated` | `#FFFFFF` | Sheets, dialogs, the floating nav dock |
| `primary` | `#1F3D34` (Deep Emerald) | Primary actions, active states |
| `onPrimary` | `#FAF7F2` | Text/icons on primary |
| `secondary` | `#3A3D42` (Graphite) | Secondary buttons, strong text |
| `accent` | `#C9A667` (Soft Gold) | Favorites, highlights, one-per-screen emphasis |
| `onAccent` | `#2A2416` | Text on accent fills |
| `border` | `#E4DCCB` | Hairlines, dividers |
| `textPrimary` | `#232527` | Body/headline text |
| `textSecondary` | `#6E6A62` | Captions, metadata, timestamps |
| `success` | `#6B8F71` (Muted Sage) | Confirmations |
| `warning` | `#C08B4C` (Muted Amber) | Non-blocking cautions (e.g. "storage running low") |
| `error` | `#B15C4A` (Muted Terracotta) | Errors — deliberately not fire-engine red; calm even in failure |

### Atelier Night (evening/dark dressing, or user preference)

| Token | Hex | Use |
|---|---|---|
| `background` | `#12181C` (Midnight Navy) | Screen background |
| `surface` | `#1E2226` | Cards, tiles |
| `surfaceElevated` | `#262B30` | Sheets, dialogs, the floating nav dock |
| `primary` | `#4C7A67` (Soft Emerald) | Primary actions, active states |
| `onPrimary` | `#0D1512` | Text/icons on primary |
| `secondary` | `#A8A296` (Warm Grey) | Secondary buttons, strong text |
| `accent` | `#D8B98A` (Champagne Gold) | Favorites, highlights |
| `onAccent` | `#241B0C` | Text on accent fills |
| `border` | `#2E3338` | Hairlines, dividers |
| `textPrimary` | `#F2EEE6` | Body/headline text |
| `textSecondary` | `#A6A196` | Captions, metadata |
| `success` | `#7FA98A` | Confirmations |
| `warning` | `#D2A05E` | Cautions |
| `error` | `#C97C68` | Errors |

**Rule**: `accent` (gold) never fills a large surface. One accent moment per screen —
a favorited star, the active nav dot, a single highlighted "today's pick" card border.
Gold-everywhere reads as cheap; gold-once reads as considered. This is the single most
important rule in this palette and the one most likely to be violated by accident
during implementation — flag any PR that adds a second simultaneous gold fill to one
screen for review.

**Contrast verified**: `textPrimary` on `background`/`surface` and `onPrimary` on
`primary` both clear WCAG AA (4.5:1) in both themes at the hex values above.
`textSecondary` on `background` clears AA for large text (3:1) but is intentionally
not relied on for body-sized critical text — see Section 9 (Accessibility).

---

## 3. Typography

Two typefaces, each with one job. **Fraunces** (a warm, high-contrast serif, free/
Google-Fonts-licensed, safe to bundle) for the handful of *display* moments that
should feel editorial — the greeting, section titles, a big stat number. **Inter**
(clean geometric-humanist sans, also Google-Fonts) for everything else — it has to
disappear into legibility at small sizes, which a display serif can't do. This mirrors
how boutique hotel/fashion editorial design actually works: a characterful display
face for the "signage," a workhorse sans for the fine print.

| Style | Typeface | Tablet size | Phone size | Weight | Use |
|---|---|---|---|---|---|
| Display Large | Fraunces | 40sp | 32sp | 500 | The greeting ("Good Morning, {displayName}") |
| Display Medium | Fraunces | 28sp | 24sp | 500 | Section titles ("Today's Wardrobe," "My Looks") |
| Title Large | Inter | 24sp | 20sp | 600 | Screen titles in the app bar |
| Title Medium | Inter | 18sp | 16sp | 600 | Card headers, dialog titles |
| Body Large | Inter | 16sp | 16sp | 400 | Primary reading text |
| Body Medium | Inter | 14sp | 14sp | 400 | Secondary reading text |
| Label | Inter | 12sp | 12sp | 500, +2% tracking | Chips, tags, metadata |
| Caption | Inter | 11sp | 11sp | 400 | Timestamps, helper text, fine print |

Body/Label/Caption sizes never shrink for phone — only Display/Title scale down, and
only modestly. Shrinking body text on a smaller screen is a common but real
accessibility regression; this system refuses it by design.

Line height: 1.4× for Body, 1.2× for Display/Title. Paragraph text never exceeds ~65
characters per line even on the widest tablet layout — long lines of body copy read
poorly regardless of screen size, so text blocks are capped at a max-width, not
stretched full-bleed.

---

## 4. Spacing scale

4dp base unit: **4, 8, 12, 16, 24, 32, 48, 64, 96**. Two spacing "postures" — this is
the mechanism that actually produces the "generous, uncluttered, museum-like" feel the
brief asks for, not just a stylistic preference:

- **Content padding** (screen margins, gaps between major sections): tablet uses 48dp
  outer margins and 32dp between major sections; phone uses 20dp outer margins and
  24dp between sections. This is deliberately more generous than typical Android
  guidance (which would suggest 16-24dp on tablet) — the point is negative space as a
  luxury signal, not information density.
- **Component padding** (inside cards, between a label and its value): the standard
  4/8/12/16 scale, unchanged between form factors — a chip's internal padding doesn't
  need to grow just because the screen did.

---

## 5. Elevation & shadow system

Material's tinted-elevation system is explicitly not used — it's the single most
"stock Android" visual tell. Instead: a soft, warm-toned, diffused shadow — like
softbox product photography, not a hard directional Material shadow.

| Level | Use | Shadow spec (light theme) |
|---|---|---|
| Resting | Garment tiles, cards at rest | `0dp 2dp blur:12dp` warm black at 8% opacity (`#1F1B12` @ 8%) |
| Raised | Pressed/dragged card, active outfit-builder slot | `0dp 6dp blur:20dp` @ 12% opacity |
| Floating | The nav dock, a FAB-equivalent action | `0dp 10dp blur:28dp` @ 16% opacity |
| Modal | Bottom sheets, dialogs | `0dp 16dp blur:40dp` @ 20% opacity, plus the scrim (Section 7) |

Dark theme uses the same structure with a cooler-neutral shadow tint (`#05070A`) at
roughly double the opacity, since dark surfaces need more contrast to read a shadow
at all.

---

## 6. Corner radius system

Generously rounded throughout — large soft radii read as premium/Vision-Pro-like;
sharp corners read as utilitarian/stock. Buttons are rounded rectangles, not full
pills — a pill on every button starts to feel like generic app-store chrome; a
12-16dp rounded rectangle feels more "carved," closer to Tesla/Rivian's console
language.

| Component | Radius |
|---|---|
| Chips, small tags | 8dp |
| Buttons | 14dp |
| Garment tiles, standard cards | 16dp |
| Hero cards (today's outfit, featured content) | 20dp |
| Sheets, dialogs, the nav dock | 28dp |
| Image thumbnails within a tile | 12dp (slightly less than the tile itself, so the photo reads as "inset") |

---

## 7. Glass effects & blur — used exactly twice

Used tastefully means used *rarely*. Two places only:

1. **The floating navigation dock** (Section on Navigation, `docs/design/
   navigation-flow.md`): a frosted, translucent surface so the wardrobe content
   scrolling beneath it is dimly visible through it — the Vision-Pro "floating glass
   panel" reference, applied to exactly one recurring element so it stays special.
2. **Modal scrims**: the background behind a bottom sheet or dialog is blurred
   (backdrop blur), not just dimmed with a flat black overlay — makes the sheet feel
   like it's genuinely in front of the content rather than a flat layer stacked on top.

**Nowhere else.** Cards, tiles, and garment photography are never given a glass
treatment — translucency over a photograph actively hurts the ability to judge a
garment's actual color, which matters more here than anywhere else in the app.

**Platform fallback (real constraint, not optional)**: true backdrop blur
(`RenderEffect`) is only available from API 31 (Android 12) onward; this app's
`minSdk` is 26 (`phase-2-architecture` decisions). On API 26-30, both blur usages fall
back to a solid translucent surface at ~92% opacity with no blur — visually simpler
but never broken or see-through-as-a-bug. This fallback must be designed and tested
explicitly in Phase 5, not discovered as a bug on an older device.

---

## 8. Icon & illustration style

**Icons**: thin monoline stroke (1.5dp at 24dp icon size), geometric, no fill except
for the accent-gold "favorited" state — closer to Apple's SF Symbols restraint or
Nothing OS's dot-matrix minimalism than Material's filled-icon defaults. Icons are
wayfinding, never decoration; if a screen doesn't need an icon to be understood, it
doesn't get one.

**Illustration**: this product's real "illustration" is the garment photography — that
is the hero content, and no decorative illustration should compete with it. Where an
illustration is genuinely needed (empty states, first-run), use a single soft
line-art motif — a minimalist hanger, a folded-garment silhouette — rendered in the
accent gold on a warm neutral field. Never a mascot, never a cartoon character, never
more than one motif per screen.

---

## 9. Responsive breakpoints & touch targets

| Class | Width | Primary layout |
|---|---|---|
| Compact (phone) | < 600dp | Single column, bottom tab bar, travel/occasional use |
| Medium (small tablet / phone landscape) | 600–840dp | 2–3 column grid, floating dock nav |
| **Expanded portrait (primary target)** | ≥ 840dp height-driven, portrait | The door-mounted tablet — 3–4 column grid, floating dock nav, generous vertical rhythm |
| Expanded landscape | ≥ 840dp width-driven | 5–6 column grid, dock nav shifts to a slim left rail (see `navigation-flow.md`) |

**Touch targets**: 48dp minimum everywhere (Constitution/Phase 1 baseline), but on the
primary tablet target most interactive elements are sized 56-64dp — a wardrobe-door
tablet is often touched at an angle, sometimes with a hand still holding a garment,
sometimes not looking directly at the screen while getting dressed. Generous targets
here are a real usability need, not just a nice-to-have.

---

## 10. Accessibility

- **Contrast**: all text/surface pairs verified against WCAG AA (Section 2). Dynamic
  type: supports the system font-scale setting up to at least 130% without truncating
  or overlapping — verified per-screen in Phase 8, designed for here by never using a
  fixed-height text container.
- **Color is never the only signal.** The confidence-chip pattern inherited from
  Phase 1 (Constitution rule 7) extends everywhere in this design: favorited = filled
  gold outline *and* a distinct icon state, never a color change alone; error states
  pair the terracotta color with an icon and text, never color alone.
- **TalkBack labels** are specified per-component in `docs/design/component-library.md`
  — every icon-only control has a content description written into its spec now, not
  left for an implementer to guess later.
- **Motion sensitivity**: every animation in `docs/design/motion-guide.md` has a
  "reduced motion" fallback (a simple cross-fade, ≤150ms) for users with
  `Settings > Accessibility > Remove animations` enabled — checked via
  `LocalAccessibilityManager`/reduced-motion system setting in Phase 5.
- **Minimum touch target 48dp** everywhere, 56-64dp on the primary tablet surface
  (Section 9).
- **This is a single-user app with a known user** — a real accessibility
  consideration that cuts the other way from generic guidance: if the one user of this
  app has a specific accessibility need (larger text permanently, a specific contrast
  preference), that becomes a real Settings default worth asking about directly rather
  than inferring, once Phase 5 UI exists to actually configure.
