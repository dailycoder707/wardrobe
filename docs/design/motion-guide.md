# Motion Guide

Motion here should feel like a boutique fitting room, not a productivity app —
unhurried, confident, never bouncy or springy, never a Material "emphasized"
overshoot. Two easing curves cover everything; nothing needs a third.

| Curve | Cubic-bezier | Use |
|---|---|---|
| Calm-out | `(0.22, 1, 0.36, 1)` | Entrances — elements arriving, sheets opening, grids reflowing |
| Calm-in-out | `(0.4, 0, 0.2, 1)` | Cross-fades, elements that both enter and exit in the same transition |

| Duration | ms | Use |
|---|---|---|
| Micro | 120 | Icon/chip state toggles, button press feedback |
| Small | 150 | Tooltip/toast appearance |
| Standard | 240 | Card expand, tile press-scale, filter apply |
| Large | 350 | Screen-to-screen navigation, sheet open/close |
| Emphasis | 450–600 | Outfit recommendation reveal, "look saved" confirmation, hero moments that deserve a beat |

**Reduced motion**: every entry below has a fallback — a plain 150ms cross-fade, no
scale/slide/rotation — activated when the system's reduce-motion accessibility
setting is on. This is a per-transition requirement, not a global toggle Phase 5 can
skip and add later.

---

## Named transitions

**Open garment (tile → Garment Detail)**: the tapped tile's thumbnail performs a
shared-element transform, growing from its grid position into the detail screen's
hero image position while the rest of the grid fades to 0 opacity (Calm-out, 350ms).
The detail screen's text content fades/slides up 8dp, starting 80ms after the image
transform begins (a slight stagger, not simultaneous — the photo should feel like it
arrives first).

**Close garment (Detail → back to grid)**: the exact reverse, same duration. If the
user edited the item, the tile's position in the grid gets a brief 200ms gold-outline
pulse on return so it's clear which tile just changed.

**Search**: tapping the search field expands it from a compact chip in the toolbar to
a full-width field (Calm-out, 240ms), the rest of the toolbar's icons fade out during
the same window rather than being covered. Results stream in with a subtle 80ms
stagger per row/tile, capped at the first 6 (no stagger delay past that, to avoid the
list feeling slow at scroll).

**Filtering**: applying a filter animates the grid via a cross-fade + reflow (Calm-
in-out, 240ms) — items leaving the filtered set fade and collapse; items already
visible reflow to their new position; items newly entering fade in. No item ever
"jumps" instantly to a new position.

**Outfit recommendation reveal**: the highest-emphasis moment in the app. The
suggested outfit's garments animate in one at a time (top layer, then mid, then
bottom, then accessories — following actual dressing order), each with a 400ms
Calm-out entrance and a 100ms stagger, total reveal under ~1s. This is the one place
in the app where a slightly theatrical pace is intentional — it's meant to feel like
a small moment of delight, not a data refresh.

**Save**: a filled checkmark morphs in on the primary button (icon cross-fade, 150ms)
and a Confirmation Toast rises from behind the nav dock (Calm-out, 240ms slide + fade).

**Delete**: the confirmation dialog's warning icon does not shake or pulse (no
"gamified alarm" register) — it simply appears (Calm-in-out fade, 150ms). On confirm,
the deleted tile collapses (scale to 0, fade, 200ms) and the grid reflows.

**Favorite**: the star icon fills with a brief 300ms scale-bounce-free "settle"
(scale 1 → 1.15 → 1, Calm-out, no overshoot past 1.15) plus a small burst of 3-4 soft
gold particles that fade over 400ms — the single most playful moment allowed in this
system, deliberately small and quiet rather than a full-screen celebration.

**Calendar transition (month ↔ day)**: tapping a day cell expands it via a shared-
element transform into the Day Detail sheet (Calm-out, 350ms) — the day cell's date
number grows into the sheet's header date. Swiping between months is a horizontal
slide (Calm-in-out, 240ms), current month's grid sliding out as the next slides in,
no bounce at the calendar's start/end bounds — it simply resists past a small
threshold and springs back (standard scroll-edge behavior, not custom).

**Screen navigation (nav dock destinations)**: a cross-fade + subtle 4dp vertical
settle (Calm-in-out, 240ms) — deliberately not a full slide-transition between top-
level destinations (a slide implies "going deeper," which isn't true for switching
between Home/Closet/Outfits/Calendar/More — they're peers, not a hierarchy).
Navigating *into* a detail screen from within a section (e.g. Closet → Garment
Detail) does use a forward slide/shared-element (see "Open garment" above) since that
genuinely is going deeper.

**Scrolling behavior**: standard platform overscroll/fling physics, no custom
scroll-jank "premium" effects layered on top — restraint here matters more than a
custom bounce curve; a scroll that behaves exactly as expected feels calmer than one
trying to prove it's fancy.

**Drag & drop** (Outfit Builder, primarily): picking up a tile scales it to 105% and
lifts it to the "floating" elevation (Section 5, design system) over 150ms; empty
slots the drag is currently over highlight with the gold glow described in
`component-library.md`; releasing over a valid slot settles the tile into place with
a 200ms Calm-out snap; releasing over empty space returns the tile to its origin with
the same 200ms curve played in reverse, not a linear snap-back.

**Grid animation (density change)**: whether triggered by pinch or the density
stepper, the grid reflows over 300ms (Calm-in-out) — every tile animates both its
size and position simultaneously (a shared reflow, not size-then-reposition in two
steps), so the whole grid reads as one continuous gesture rather than a jump-cut.
