# Component Library

Every reusable component, built from the tokens in `phase-4-design-system.md`. Each
entry: purpose, anatomy, states, sizing, and the TalkBack label an implementer should
use verbatim in Phase 5 — written now so accessibility isn't guessed at later.

---

## Navigation Dock

The single most-seen piece of chrome in the app — see `navigation-flow.md` for the
full IA. A floating, frosted-glass pill anchored to the bottom of the screen on
portrait tablet/phone, or a slim glass rail on the left edge in landscape.

**Anatomy**: 5 icon+label destinations (Home, Closet, Outfits, Calendar, More),
28dp corner radius, glass surface (Section 7, design system), 72dp tall on tablet /
64dp on phone. Active destination shown by the icon switching from outline to a
soft-filled state plus a single small gold dot beneath the label — not a filled pill
background (too Material-default).

**States**: default, active (one at a time), pressed (a brief 4% darken, 120ms).

**a11y**: each destination is a single tappable node, content description
`"{label}, tab, {selected/not selected}"` — standard `NavigationBar` semantics, no
custom override needed.

---

## Garment Tile

The core unit of the entire browsing experience — see the Closet screen spec for how
it behaves in a grid at scale.

**Anatomy**: a 16dp-radius card; the garment's thumbnail fills the top ~80% at 12dp
inset radius; a thin label strip below shows the garment's name/category (Body
Medium) and, only if set, brand (Caption, `textSecondary`). A gold outline star,
top-right corner, 4dp inset — filled gold when favorited, thin outline otherwise.
Optionally, a small confidence dot (Constitution rule 7) in the bottom-left corner of
the thumbnail if the item has unreviewed AI-guessed attributes — tapping the tile
still opens Garment Detail; the dot is informational, not a separate hit target.

**States**:
- Default (resting elevation, Section 5)
- Pressed (raised elevation, 96% scale, 120ms)
- Selected (multi-select mode — a 2dp emerald border, a filled checkmark badge
  top-left)
- Dragging (raised→floating elevation, tile scales to 105%, slight rotation ±2°,
  everything else in the grid dims to 85% opacity — see `motion-guide.md`)
- Loading (a warm-toned skeleton shimmer, not a grey Material skeleton — shimmer
  color drawn from `surface`→`border` in the current theme)
- Missing/broken image (a soft line-art hanger placeholder, never a broken-image icon)

**Sizing**: adaptive to the grid-density control (see below) — minimum 96dp square at
the densest setting (5-6 columns landscape), maximum ~180dp at the sparsest (2 columns
phone). Corner radius stays 16dp regardless of size.

**a11y**: content description `"{garment name or category}, {brand if set}, {favorited
if applicable}"`. The favorite star is its own tappable node with description
`"Favorite {garment name}"` / `"Remove {garment name} from favorites"`, sized to the
full 48dp minimum even though it visually reads as smaller (invisible touch padding).

---

## Grid Density Control

Lets the wardrobe browsing screen scale from 2 to 6 columns. Two interaction paths,
both must produce the identical result (accessibility requirement — pinch alone can't
be the only path):
1. **Pinch-to-zoom** directly on the grid (the delightful, primary path).
2. **A density stepper** in the Closet screen's toolbar — 4 discrete icon buttons
   (2/3/4/5+ columns as glyphs of decreasing dot-grid density) for anyone who doesn't
   want to pinch, and the only path on a `TalkBack`-driven session.

Column count snaps to whole numbers only; there's no "3.4 columns" mid-pinch state
held at rest — the animation eases to the nearest whole column count on release
(`motion-guide.md`).

---

## Filter Chip / Tag Chip

**Anatomy**: 8dp-radius pill... no — 8dp-radius *rounded rectangle* (per the "no full
pills" rule, Section 6), Label-scale text, 32dp tall, 12dp horizontal padding.
Unselected: `surface` fill, `border` outline. Selected: `primary` fill, `onPrimary`
text, no outline.

**Confidence Chip** (a distinct variant, Constitution rule 7 — carried over from
Phase 1's requirement that any AI-guessed attribute is an editable suggestion, never
a fact): same shape, but unselected-style fill plus a small outlined dot in `accent`
gold and a subtly dashed (not solid) border, to visually read as "suggested, not yet
confirmed." Tapping it opens a small inline editor (a short picker, not a full sheet)
rather than toggling a filter. Once the user confirms or edits it, it becomes a
normal solid-border tag chip — the dashed state only exists pre-review.

**a11y**: content description includes selection state; the confidence chip's
description appends `", suggested — tap to confirm or edit"`.

---

## Buttons

| Variant | Fill | Use | Radius |
|---|---|---|---|
| Primary | `primary` solid | One per screen — the single most important action (e.g. "Save Look") | 14dp |
| Secondary | `surface` + `border` outline | Supporting actions (e.g. "Cancel," "Duplicate") | 14dp |
| Text | No fill, `primary` text | Low-emphasis actions (e.g. "Skip") | — |
| Icon button | Circular touch target, icon only | Toolbar actions | 48-64dp circle |
| Destructive | `error`-outlined, `error` text, no fill until pressed | Delete actions — never a solid red fill; a confirmation dialog always follows, so the button itself stays calm | 14dp |

No FAB. A floating circular "+" button is the single most recognizable "stock
Android" signal this system explicitly avoids — the equivalent action (Add Garment)
lives in the nav dock's center-weighted "Closet" destination via a contextual
in-toolbar button instead, never a detached floating circle.

---

## Bottom Sheet & Dialog

**Bottom Sheet**: 28dp top corners, `surfaceElevated` fill, blurred scrim behind it
(Section 7), a small centered grab-handle (4dp × 32dp, `border` color) at the top for
the drag-to-dismiss affordance. Used for: outfit-builder accessory pickers, quick
filters, the image before/after compare view.

**Dialog**: centered, 28dp radius all corners, `surfaceElevated`, blurred scrim,
max-width capped (never full-bleed on tablet — a full-width dialog on an 840dp+
screen looks like an error, not a considered choice). Used for: destructive
confirmations, the "can't delete — this item has wear history" message
(phase-3-persistence.md's RESTRICT behavior surfacing in the UI).

---

## Confirmation Toast

The snackbar equivalent — but "snackbar" is too Material a word for what this should
feel like. A small pill-shaped surface, 14dp radius, appears just above the nav dock,
auto-dismisses after 2.5s, never blocks interaction, never stacks (a new one replaces
whatever's showing rather than queuing). Used for low-stakes confirmations ("Saved to
Favourites") — never for anything requiring acknowledgment (those are dialogs).

---

## Empty State

**Anatomy**: centered, vertically: the single line-art motif (Section 8) in accent
gold, a Display Medium (Fraunces) short headline, a Body Medium supporting line, and
—only if there's a clear next action— one Secondary button. See
`microcopy-guide.md` for exact copy per screen.

---

## Loading State

A warm-toned shimmer (drawn from `surface` to `border`, animating left-to-right,
1200ms loop) shaped like the content that's coming — garment-tile-shaped skeletons in
a grid, a card-shaped skeleton on Garment Detail, never a generic centered spinner
except on the Splash screen (which has nothing else to skeleton-shape against).

---

## Stat Tile

Used on Statistics/Wardrobe Story. **Anatomy**: `surface` card, 16dp radius, a large
Display-scale (Fraunces) number, a Label-scale caption beneath it, optional small
trend indicator (a thin up/down glyph, never a red/green traffic-light pair — trend
color uses `textSecondary` with the glyph direction carrying the meaning, keeping the
"no color as the only signal" rule intact even for something as low-stakes as a stat
trend).

---

## Image Viewer

Full-screen, `background`-color canvas (not black — a black image viewer is another
"stock Android" tell). Pinch-to-zoom, double-tap to zoom to 2×, swipe down to dismiss
(matches the bottom-sheet drag-to-dismiss gesture language for consistency). The
Before/After compare (original vs. background-removed) is a vertical slider handle
the user drags left/right across the image — not a toggle button — so the comparison
feels tactile.

---

## Weather Chip

Home screen only. **Anatomy**: a small rounded-rectangle chip, a thin-line weather
glyph (sun/cloud/rain — matching the icon style, never a colorful skeuomorphic
weather icon), temperature in Label scale, and — critically — a visible staleness
indicator when the cached forecast is stale (`phase-1-architecture.md` Section 18):
the chip's border switches from solid to dashed and the text appends "· yesterday's
forecast" in `textSecondary`, using the exact same dashed-border visual language as
the Confidence Chip so the user learns one pattern ("dashed border = not fully
current/confirmed") and it applies everywhere.

---

## Drag Handle / Layering Slot (Outfit Builder)

**Anatomy**: a vertical stack of translucent "slots" (top/outer layer, mid layer,
bottom, shoes, bag, jewelry — see Outfit Builder screen spec) rendered as thin dashed
outlines when empty, solid-filled with the placed garment's thumbnail when occupied.
Dragging a garment tile from the closet browser over a slot highlights that slot with
a soft gold glow (2dp, `accent` at 40% opacity) before drop.

**a11y**: drag-and-drop always has a non-drag equivalent — long-press or tap-to-select
a garment, then tap a target slot, produces the identical result for anyone who can't
perform a drag gesture (a hard requirement, not a nice-to-have, given this whole
screen is otherwise gesture-first).
