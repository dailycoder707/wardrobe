# Screen Specifications

All wireframes are drawn **tablet portrait** (the primary target, Section 0 of
`phase-4-design-system.md`) unless noted. Every screen lists its landscape and phone
deltas rather than repeating a full wireframe per orientation — the layout principle
carries across; what changes is column count, dock position, and information density,
not the underlying hierarchy. Every screen also states its four required states
(empty/loading/error/offline) even where the honest answer is "not applicable" —
stated explicitly rather than silently skipped.

Legend: `[img]` = photo content, `░░` = skeleton/loading shimmer, `┅┅` = dashed
(unconfirmed/stale) border, `●` = filled accent dot.

---

## 1. Splash

**Purpose**: bridge the cold-start gap (Section 21-23, `phase-1-architecture.md`) with
something calmer than a blank white flash.

**Layout**:
```
┌───────────────────────────────┐
│                                │
│                                │
│                                │
│           ⟨ hanger ⟩          │
│         (line-art mark)        │
│                                │
│           Wardrobe             │
│      (Fraunces, Display)       │
│                                │
│                                │
└───────────────────────────────┘
```
Centered mark + wordmark on `background`, nothing else — no progress bar, no
loading text. If cold start exceeds ~800ms (measured in Phase 9's Macrobenchmark),
a single thin indeterminate line appears beneath the wordmark, not before.

**States**: Loading is the only state (by definition). No error state — if the
database fails to open, that's the Backup & Restore recovery flow's problem to
surface once the app has actually reached a screen that can show it, not Splash's.
**Landscape/phone**: identical, mark stays centered.

---

## 2. Welcome

**Purpose**: shown exactly once, first run only — there is no returning-user
marketing funnel to build (Section 1, design system), so this is closer to "hand the
keys over" than an onboarding sequence.

**Layout**:
```
┌───────────────────────────────┐
│                                │
│         ⟨ hanger mark ⟩       │
│                                │
│   This is your wardrobe,       │
│   made digital.                │
│                                │
│   Everything stays on this     │
│   device. Nothing is shared,   │
│   nothing needs an account.    │
│                                │
│                                │
│   ┌─────────────────────┐     │
│   │   Set Up My Closet   │     │  ← Primary button
│   └─────────────────────┘     │
│                                │
└───────────────────────────────┘
```
Single screen, single primary action. No multi-step carousel, no "skip" — there is
nothing to skip past because there's nothing being sold. Tapping the button goes
straight into a short, optional profile setup (name confirmation, unit preference,
menswear/womenswear/both) folded into the first visit to Settings > Profile rather
than a separate onboarding wizard — see Profile spec.

**States**: no loading/error/offline — this is a static, local-only screen.
**Landscape/phone**: text block width caps per the design system's line-length rule;
otherwise identical.

---

## 3. Home

**Purpose**: the wardrobe's front door. Answers "what should I wear," "what's
happening this week," and "is everything okay in here" in one unhurried scroll — the
brief's "perfect wardrobe dashboard" spec, deliberately not built as a literal
dashboard (no dense metric tiles competing for attention).

**Layout** (tablet portrait):
```
┌───────────────────────────────────┐
│  Good Morning, {displayName}       │
│  Tuesday, 12 August                │
│  ┌───────────┐                     │
│  │ ☀ 22°C    │                     │
│  └───────────┘                     │
│                                     │
│  Today's Wardrobe                  │
│  ┌───────────────────────────────┐ │
│  │                                │ │
│  │   [img]  [img]  [img]         │ │
│  │   top    bottom  shoes        │ │
│  │                                │ │
│  │  "Layered for a mild morning, │ │
│  │   easy to move in for a work  │ │
│  │   day at the studio."         │ │
│  │                                │ │
│  │  ♡ Save      ↻ Another look   │ │
│  └───────────────────────────────┘ │
│                                     │
│  Quick actions                     │
│  [+ Add]  [✦ Create look]  [✈ Trip]│
│                                     │
│  Worth another look                │
│  [img] [img] [img] [img]  →        │
│                                     │
│  Recently worn                     │
│  [img] [img] [img] [img]  →        │
│                                     │
│  ┌───────────────────────────────┐ │
│  │ ✈ Paris in 6 days              │ │
│  │ Packing list not started yet   │ │
│  └───────────────────────────────┘ │
│                                     │
│  Your wardrobe                     │
│  62% worn this year · 214 items    │
│  See your wardrobe story →         │
│                                     │
│  "Cost-per-wear rewards patience,  │
│   not restraint."                  │
│                                     │
├───────────────────────────────────┤
│  ⌂     👗     ✦     📅     ⋯      │
│ Home  Closet Outfits  Cal   More   │
└───────────────────────────────────┘
```

**Hierarchy**: greeting/weather (identity + context) → Today's Wardrobe (the single
highest-value moment, gets the most visual weight) → quick actions (low-height,
utilitarian, deliberately not competing visually with the hero card) → the two
horizontal "worth a look" strips (light-touch, browsable, not another full grid) →
the trip card (conditional — only renders if a trip exists within ~14 days) → a
one-line wardrobe-health teaser (not a stats dashboard — that's what Wardrobe Story
is for) → a small rotating editorial line (Today's Inspiration).

Every section past the date subtitle is independently toggleable from
Personalization Settings (`phase-5a-data-layer.md`'s `PersonalizationRepository`):
Show Greeting, Show Weather Card, Show Recommendation Card, Show Wardrobe Health
Card, and Show Inspiration Card each hide their section entirely when off — Home's
layout collapses to fill the gap rather than leaving an empty slot. "Today's
Wardrobe" is itself the *default* section title; Personalization Settings' Custom
Home Screen Title field overrides it (matching the personalization brief's own
examples — "My Looks" is just as valid a title as "Today's Wardrobe").

**Components**: Weather Chip, hero outfit card (a bespoke component — large, 20dp
radius, not a Garment Tile), quick-action icon buttons, horizontal scroll strips of
Garment Tiles, the Trip teaser card, Stat Tile (compact variant, single number).

**Interactions & animations**: "Today's Wardrobe" supports a horizontal swipe to see
alternate suggestions for the day (dots indicator beneath, max 3 alternates —
avoiding an infinite "keep scrolling for more AI content" feed, which would work
against the calm-console tone). "Another look" swaps the card content via a Calm-out
button (450ms, the Emphasis duration — see Outfit recommendation reveal in
`motion-guide.md`). Tapping any garment within the hero card opens Garment Detail
with the shared-element transition. The trip card and wardrobe-health line are both
simple navigational taps (→ Trips, → Wardrobe Story) with the standard 240ms
cross-fade.

**Empty state** (day one, nothing logged/owned yet): the hero card is replaced with
an empty-state card: line-art motif, *"Your closet's still empty — start by adding
what's already in front of you."*, a single "Add Garment" button. The horizontal
strips and trip card simply don't render (no empty placeholders stacked on top of
each other — one empty state per screen, not one per section).

**Loading state**: skeleton shimmer shapes matching the hero card and strip tiles;
greeting/date/weather render immediately since they need no data fetch beyond a
cached weather value.

**Error state**: only the weather chip can meaningfully error (network-dependent);
per `phase-1-architecture.md` Section 18 it never shows a bare error, only the
dashed/stale treatment described in the Weather Chip component spec. No other part
of Home can "error" in a user-visible way since everything else is local.

**Offline state**: identical to normal, except the Weather Chip shows its stale
variant. This is the clearest single proof-point of the offline-first promise
(ADR-003) — Home must look completely normal with the device in airplane mode,
apart from that one chip.

**Landscape**: the hero card and quick actions sit in a top band; the two horizontal
strips become a two-column side-by-side pair below rather than stacked, since the
wider viewport has room.
**Phone**: identical structure, tighter margins (20dp vs. 48dp), hero card height
reduced (garments shown at smaller scale within it), horizontal strips unchanged.

---

## 4. Closet — the wardrobe browsing experience

**Purpose**: the single most important screen in the app (brief's own framing). Fast
at 300, 600, and 1000+ garments; adjustable density; effortless filtering.

**Layout** (tablet portrait, 3-column default density):
```
┌───────────────────────────────────┐
│ My Closet | Wishlist        🔍 ▤▤▤│  ← segmented tabs, search, density stepper
│ [All][Tops][Bottoms][Dresses][+3] │  ← filter chip row, horizontally scrollable
├───────────────────────────────────┤
│ [img]   [img]   [img]             │
│  ♡       ●       ┅               │
│                                    │
│ [img]   [img]   [img]             │
│                                    │
│ [img]   [img]   [img]             │
│                                    │
│ [img]   [img]   [img]             │
│           ⋮                       │
├───────────────────────────────────┤
│  ⌂     👗     ✦     📅     ⋯      │
└───────────────────────────────────┘
```

**Hierarchy**: the segmented My-Closet/Wishlist control and search/density controls
form a single slim toolbar row — everything else is the grid; there is no secondary
toolbar, no banner, no promotional space above the fold. At scale (600-1000+ items)
the grid is backed by Paging 3 (`phase-1-architecture.md` Section 21) — visually
this is invisible to the user; the only thing that must hold true is that scrolling
never stutters and a placeholder tile never "pops" once loaded, it cross-fades in
(150ms) as its thumbnail decodes.

**Components**: segmented control, search icon (expands per `motion-guide.md`), Grid
Density Control (stepper icons shown; pinch works directly on the grid too), Filter
Chips (category/season/dress-code/status — multi-select, `phase-3-persistence.md`'s
`GarmentFilter`), Garment Tile grid.

**Smart grouping**: an optional grid mode (toggled from the "⋮" overflow, not a
default) groups tiles under soft section headers — "Favourites," "Recently added,"
"Recently worn," "Everything else" — each a Label-scale header with a thin divider,
no boxed card around the group (a boxed card per group at this density would be
visual noise). Default mode is one continuous, ungrouped grid sorted by
most-recently-updated, since grouping by default fights the "fast browsing" goal more
than it helps.

**Interactions & animations**: pinch-to-resize per `component-library.md`'s Grid
Density Control; long-press a tile enters multi-select mode (for bulk status changes
— e.g. marking several items "in storage" at season change) with a Calm-out scale-in
of checkmark badges across the grid (150ms); tapping a filter chip animates the grid
per the Filtering transition in `motion-guide.md`.

**Empty state**: line-art hanger motif, *"Nothing here yet — your closet starts with
one photo."*, "Add Garment" button. **Empty-after-filtering** (distinct from a truly
empty closet) is a smaller inline message within the grid area — *"No items match
these filters"* plus a "Clear filters" text button — never the full empty-state
illustration, which should read as "you own nothing," not "no matches."

**Loading state**: tile-shaped shimmer grid at the current density.

**Error state**: not applicable in the network sense — this data is entirely local.
A genuine read failure (a corrupted database, vanishingly rare) surfaces as a full
dialog directing to Backup & Restore, not an inline grid error.

**Offline state**: identical to normal — this screen has zero network dependency,
which is exactly the point (ADR-003).

**Landscape**: 5-6 columns default density, nav shifts to the left rail
(`navigation-flow.md`), filter chip row and toolbar unchanged in structure.
**Phone**: 2 columns default, density stepper collapses to a single icon (pinch is
the primary path; the stepper opens a small popover rather than sitting inline in
the toolbar, to save width).

---

## 5. Garment Detail

**Purpose**: everything about one item, and the jumping-off point to "Style this
item."

**Layout**:
```
┌───────────────────────────────────┐
│ ←                          ♡  ⋯   │
│                                    │
│      ┌───────────────────┐        │
│      │                    │        │
│      │      [img]         │        │
│      │                    │        │
│      └───────────────────┘        │
│                                    │
│  Cream Silk Blouse                 │
│  Reformation · Worn 12 times       │
│                                    │
│  [Tops] [Silk] ┅Smart Casual┅     │  ← confirmed vs. suggested chips
│  [Cream] [Long Sleeve]             │
│                                    │
│  Cost per wear        £4.20        │
│  Last worn            3 days ago   │
│  Purchased            Mar 2024     │
│                                    │
│  Care notes                        │
│  Dry clean only                    │
│                                    │
│  ┌─────────────────────────────┐  │
│  │   Style this item             │  │  ← primary button
│  └─────────────────────────────┘  │
│                                    │
│  Worn with                         │
│  [outfit] [outfit] [outfit]  →     │
│                                    │
├───────────────────────────────────┤
```

**Hierarchy**: hero photo first and largest (tap to open Image Viewer); identity
(name/brand/wear-count) immediately beneath; attribute chips next, with the
confidence-chip dashed-border pattern applied to anything not yet reviewed
(Constitution rule 7 made visible here more than anywhere else in the app); a compact
metadata table (cost-per-wear, last worn, purchased, care); the single primary
action; a "worn with" strip of past outfits linking onward.

**Components**: Image Viewer trigger, favourite star (top toolbar this time, not
tile corner), Confidence Chips, Stat rows, Primary Button, horizontal Outfit strip.

**Interactions & animations**: entering from a tile uses the shared-element
transform (`motion-guide.md`); tapping any dashed confidence chip opens its inline
editor immediately (no intermediate "are you sure" step — confirming/correcting an
attribute is meant to be nearly frictionless, since low friction here is what makes
the confidence-chip pattern actually get used rather than ignored); the "⋯" overflow
holds Edit, Duplicate to Wishlist-style reference, Change status (Active/Stored/
Donating/Repair/Sold), and Delete (which — per `phase-3-persistence.md`'s RESTRICT
rule — is disabled with an inline explanation if the item has wear history: *"This
item has been logged as worn, so it can't be deleted — you can mark it Sold or
Donating instead."*, not a dead disabled button with no explanation).

**Empty state**: not applicable — this screen only exists for a garment that exists.
**Loading**: hero-image-shaped shimmer, metadata rows as shimmer bars.
**Error**: a failed image decode shows the line-art hanger placeholder in the hero
position rather than a broken-image glyph, with a small "Retry" text action.
**Offline**: identical to normal — no network dependency.

**Landscape**: photo and metadata sit side by side (a genuine two-pane moment — the
one screen in the app where landscape's extra width is used for a true side-by-side
layout rather than just more columns) since Detail screens have exactly the
photo/facts split that suits it.
**Phone**: identical to portrait tablet, tighter margins.

---

## 6. Add Garment

**Purpose**: capture → process → confirm, the single most consequential flow in the
app since it's how the whole closet gets populated. Must feel fast and forgiving.

**Layout** (capture step):
```
┌───────────────────────────────────┐
│ ✕                                  │
│                                    │
│         ┌───────────────┐         │
│         │               │         │
│         │  camera        │         │
│         │  viewfinder    │         │
│         │               │         │
│         └───────────────┘         │
│                                    │
│         ⬤  ← shutter               │
│                                    │
│   [Gallery]         [Flash: auto]  │
└───────────────────────────────────┘
```

**Layout** (review/confirm step, after background removal runs):
```
┌───────────────────────────────────┐
│ ← Add Garment              Save   │
│                                    │
│   ┌───────────────────────────┐  │
│   │        [cutout img]        │  │
│   │                             │  │
│   │  Processing…  ░░░░░░░░     │  │ ← only while the pipeline is running
│   └───────────────────────────┘  │
│                                    │
│  Name (optional)                   │
│  [________________________]        │
│                                    │
│  ┅ Category: Tops ┅                │
│  ┅ Colour: Cream ┅                 │
│  ┅ Season: All ┅                   │
│  Brand            [__________]     │
│  Price             [_______]       │
│                                    │
│  ┌─────────────────────────────┐  │
│  │        Save to Closet         │  │
│  └─────────────────────────────┘  │
└───────────────────────────────────┘
```

**Hierarchy**: capture is a single full-screen camera step with no intermediate
"choose a category first" gate — photograph first, categorize after, since that
matches how someone actually holds up a garment (Phase 1's own "photograph first"
framing). The review step leads with the processed image and a live "Processing…"
state while segmentation runs in the background (`core:image`'s pipeline,
Phase 1 Section 16) — every attribute the pipeline fills in appears as a dashed
Confidence Chip immediately, editable before the first save, never presented as
already-confirmed fact.

**Components**: CameraX viewfinder + shutter, Gallery import (Photo Picker,
`phase-1-architecture.md` Section 16), Confidence Chips, text fields, primary Save
button.

**Interactions & animations**: shutter press triggers a brief 100ms white-flash
overlay + shutter-click haptic, then a Calm-out transition (350ms) into the review
step, with the captured image already visible while the background-removal pipeline
runs behind it (never a blank/loading screen while segmentation happens — the
original photo is shown immediately, then cross-fades to the cutout once ready,
120ms). Attribute chips populate with a soft staggered fade-in (80ms stagger) as the
pipeline resolves each one, rather than all appearing at once — reinforces that
these are live suggestions arriving, not a static form.

**Empty state**: n/a (this screen has no data to be empty of). **Loading**: covered
above (the "Processing…" shimmer strip beneath the image). **Error state**: if
background removal fails outright (rather than just producing a low-confidence
result), the original uncropped photo is kept and used as-is with a small inline
note — *"Couldn't auto-crop this one — you can still save it, or try retaking the
photo."* — never a dead end. **Offline**: fully unaffected — this entire flow is
on-device (ADR-004/008).

**Landscape**: camera viewfinder and review form appear as two panes side-by-side
during the review step (photo left, form right) rather than stacked.
**Phone**: identical single-column flow; camera step is genuinely full-screen (no
persistent nav dock during capture, on any form factor — capture is a modal, focused
task).

---

## 7. Edit Garment

**Purpose**: the confidence-signalled attribute editor in its full form — reached
either from Garment Detail's "⋯" menu or directly by tapping any single confidence
chip in place.

**Layout**: structurally identical to Add Garment's review step, but every attribute
is pre-filled (confirmed chips shown solid-border, still-unreviewed ones still
dashed) and the primary button reads "Save Changes." A secondary "Retake Photo" /
"Prettify Photo" action sits beneath the hero image (Phase 1 Section 16's "Prettify
photo" regenerate-cutout feature) — a Secondary button, not competing with the
primary Save.

**Interactions**: identical attribute-editing interaction as Add Garment; changing
the primary photo re-triggers the background-removal pipeline with the same
in-place processing treatment.

**States**: same as Add Garment across empty/loading/error/offline, with the
addition that a failed re-crop on Prettify Photo simply keeps the previous cutout
unchanged and shows a small inline *"Couldn't improve this one — kept your original
crop."* rather than destroying a working image.

**Landscape/Phone**: identical deltas to Add Garment.

---

## 8. Outfit Builder

**Purpose**: the most delightful screen in the app, by the brief's own instruction.
Drag-and-drop layering across garment/accessory slots, with full undo/redo.

**Layout** (tablet portrait):
```
┌───────────────────────────────────┐
│ ✕ New Look           ↶  ↷    Save │
│                                    │
│  ┌───────────────────────────┐   │
│  │  ┅ outer layer ┅           │   │
│  │  ┅ top ┅                   │   │
│  │  ┅ bottom ┅                 │   │
│  │  ┅ shoes ┅                  │   │
│  │  ┅ bag ┅    ┅ jewelry ┅     │   │
│  └───────────────────────────┘   │
│                                    │
│  Suggestions for this slot         │
│  [img][img][img][img][img]  →     │
│                                    │
├───────────────────────────────────┤
│  My Closet                🔍      │
│  [img][img][img][img][img][img]   │
│                       ⋮            │
├───────────────────────────────────┤
```

**Hierarchy**: the outfit canvas (slots) occupies the top half; a horizontal
"suggestions for this slot" strip (populated by the rule-based styling engine,
Phase 6) sits directly beneath it; the full closet browser occupies the bottom half
as the drag source. This is a genuine split-screen layout — the one other screen
besides Garment Detail's landscape mode where two panes coexist on portrait tablet,
justified because dragging between "my closet" and "the outfit" is the entire point
of the screen.

**Components**: the Drag Handle / Layering Slot component (`component-library.md`),
undo/redo icon buttons (top toolbar, always visible, disabled-state greyed when
there's nothing to undo/redo rather than hidden), a compact horizontal suggestion
strip, the full Garment Tile grid (denser/smaller tiles here than in Closet proper,
since this pane is half-height).

**Interactions & animations**: drag from the closet grid to a slot per
`motion-guide.md`'s Drag & Drop spec, with the non-drag equivalent (tap-to-select,
then tap target slot) always available; tapping an occupied slot opens a small
picker sheet to swap that piece without a full drag; long-press an occupied slot
offers Remove/Style-this-item-instead; **Duplicate Look** (from the "⋯" on a saved
look, not this screen's own toolbar — duplication starts from an existing look) opens
this same builder pre-populated. **Undo/Redo** covers every action taken during the
current session (add/remove/swap per slot), reset when the builder is exited.

**Empty state**: a brand-new look starts with every slot shown dashed/empty — this
*is* the empty state, not a special case; the difference between "editing a saved
look" and "starting fresh" is simply whether slots start populated.
**Loading**: the suggestion strip shows shimmer tiles while the styling engine scores
candidates; the closet grid below loads independently and typically finishes first.
**Error**: if the styling engine can't produce suggestions (e.g. an empty closet in
the relevant category), the strip shows a small inline note — *"Add a few more
pieces and we'll have suggestions ready"* — rather than an empty, unexplained gap.
**Offline**: fully unaffected — the styling engine is on-device/rule-based
(ADR-004), so this screen has no degraded state to design for.

**Landscape**: canvas and closet-browser panes sit side-by-side (canvas left,
browser right) rather than stacked, since landscape's width suits a true side-by-side
split better than portrait's stacked halves.
**Phone**: the closet-browser pane collapses into a bottom sheet the user drags up
from a small handle, rather than a permanently-visible split, since a phone screen
can't afford to permanently dedicate half its height to a drag source.

---

## 9. Saved Looks

**Purpose**: the outfit lookbook — browse everything already built or AI-suggested-
and-saved.

**Layout**: a grid of Outfit Cards (a distinct, slightly larger tile than a Garment
Tile — shows the outfit's 2-4 constituent garments composited as a small flat-lay
arrangement within one card, not a single photo), filterable by occasion, with a
"Create Look" button in the toolbar leading to Outfit Builder.

**Components**: Outfit Card grid, Filter Chips (by occasion), primary toolbar button.

**Interactions**: tapping a card opens it read-only (garments shown, "worn on"
history, a "Restyle" action that reopens it in Outfit Builder); long-press offers
Duplicate/Delete.

**Empty state**: *"No looks saved yet — build one, or save a suggestion from Home."*,
button into Outfit Builder.
**Loading**: card-shaped shimmer grid. **Error**: n/a, local data only.
**Offline**: unaffected.

**Landscape/Phone**: standard column-count scaling, no structural change.

---

## 10. Calendar

**Purpose**: log what was worn; see the month at a glance.

**Layout**:
```
┌───────────────────────────────────┐
│  August 2026            List ⇄    │
│  ‹        M  T  W  T  F  S  S    ›│
│           1  2  3  4  5  6  7     │
│                 ●                 │  ← dot = a wear logged that day
│           8  9 10 11 12 13 14     │
│                    ●●             │  ← multiple outfits that day
│                                    │
│           ⋮                       │
├───────────────────────────────────┤
│  Today                             │
│  Cream Blouse + Tailored Trousers  │
│  + Loafers                         │
│                                    │
│  ┌─────────────────────────────┐  │
│  │      + Log what you wore      │  │
│  └─────────────────────────────┘  │
├───────────────────────────────────┤
```

**Hierarchy**: month grid on top (compact — dots, not thumbnails, keep the grid
scannable at a glance); the selected day's detail (today, by default) beneath it as
a persistent panel, not a separate screen — tapping a different day updates this
panel in place via the shared-element transform described in `motion-guide.md`
rather than navigating away.

**Components**: month grid cell (day number + wear-dot indicator), the day detail
panel, a "List ⇄" toggle that swaps the whole screen to Wear History's list
presentation of the same underlying data (not a separate destination — see
`navigation-flow.md`).

**Interactions & animations**: swiping left/right changes month (Calm-in-out slide,
240ms); tapping a day expands it into the detail panel (shared-element, 350ms); "Log
what you wore" opens a lightweight logger — pick a saved look, or pick individual
garments directly (a single garment can be logged without being forced into an
"outfit" — a documented fix over the source-app teardown, `phase-1-architecture.md`
Section 4).

**Empty state** (a day with nothing logged): the day detail panel shows *"Nothing
logged for this day"* plus the "Log what you wore" button — never a blank panel with
no explanation.
**Loading**: the month grid's dots render once the month's wear events load; shown as
a brief shimmer only on first paint, not on every month swipe (adjacent months are
prefetched).
**Error**: n/a, local data.
**Offline**: unaffected.

**Landscape**: month grid and day-detail panel sit side by side rather than stacked.
**Phone**: identical stacked structure to tablet portrait, tighter margins.

---

## 11. Wear History

**Purpose**: the same wear-event data as Calendar, in a scannable reverse-
chronological list — better for "when did I last wear X" than a month grid is.

**Layout**: a simple list, grouped by month (Label-scale month headers), each row
showing the date, a small thumbnail strip of what was worn, and the occasion if set.
Reached via Calendar's "List ⇄" toggle, not a separate nav destination.

**Components**: grouped list, row = date + thumbnail strip + occasion tag.
**Interactions**: tapping a row opens that day in the Calendar detail panel (toggles
back to Calendar view, scrolled/expanded to that date).
**Empty/Loading/Error/Offline**: identical characteristics to Calendar, since it's
the same underlying data — empty state: *"Nothing logged yet — start on Calendar."*

**Landscape/Phone**: standard list reflow, no structural change.

---

## 12–13. Wardrobe Story (parent) — Statistics & Gap Analysis (tabs)

**Purpose**: the reflective, narrative view of the closet — "what do I actually
wear" — deliberately framed as a story, not a control-panel of metrics (ADR-006:
every number here is derived, never a stale cached stat).

**Layout** (Statistics tab, tablet portrait):
```
┌───────────────────────────────────┐
│  My Wardrobe Story                 │
│  [ Statistics ] [ Gap Analysis ]   │
│                                    │
│  1mo  6mo  1yr  All time           │
│                                    │
│  ┌─────────────┐ ┌─────────────┐ │
│  │  62%         │ │  £3.40       │ │
│  │  worn        │ │  avg cost/   │ │
│  │  this year   │ │  wear        │ │
│  └─────────────┘ └─────────────┘ │
│                                    │
│  Most worn                         │
│  [img][img][img][img]              │
│                                    │
│  Worth reconsidering                │
│  [img][img][img]                    │
│  (0 wears this year)                │
│                                    │
│  Your colours                      │
│  ● ● ● ● ●  (palette swatches)     │
│                                    │
│  Weekday vs weekend                │
│  ▓▓▓▓▓▓░░░░  Tailored vs. relaxed  │
│                                    │
└───────────────────────────────────┘
```

**Layout** (Gap Analysis tab):
```
│  My Wardrobe Story                 │
│  [ Statistics ] [ Gap Analysis ]   │
│                                    │
│  A few things your closet          │
│  doesn't quite cover yet:          │
│                                    │
│  ┌─────────────────────────────┐  │
│  │ No formal-dress-code items    │  │
│  │ in warm colours                │  │
│  └─────────────────────────────┘  │
│  ┌─────────────────────────────┐  │
│  │ Nothing rated for cold,        │  │
│  │ waterproof weather              │  │
│  └─────────────────────────────┘  │
│                                    │
│  These are patterns in what you    │
│  already own — never a shopping    │
│  list from us.                     │
└───────────────────────────────────┘
```

**Hierarchy**: a time-window control (1mo/6mo/1yr/all-time) governs the whole
Statistics tab; two headline Stat Tiles up top (usage %, cost-per-wear — the two
numbers this brief and Phase 1 both call out as most important); horizontal strips
for most-worn/dormant items; a compact colour-palette row; a weekday/weekend
comparison as a simple two-segment bar, never a full chart-library dashboard.
Gap Analysis is intentionally sparse — a handful of plain-language cards, explicitly
captioned as *not* a shopping prompt (`phase-1-architecture.md`'s `ClosetGap` model:
"based on what the wardrobe lacks... not on what a shop wants to sell").

**Components**: segmented tab control, time-window Segmented Control, Stat Tile,
horizontal Garment Tile strips, colour swatch row, a two-segment comparison bar, Gap
cards (plain `surface` cards, no icon-of-doom — this should read as gentle insight,
not a deficiency alert).

**Interactions**: switching time window animates the Stat Tiles' numbers with a
brief count-up/down (200ms) rather than an instant swap — small, not gimmicky.
Tapping "Most worn"/"Worth reconsidering" strip items opens Garment Detail.

**Empty state**: before enough wear history exists to compute anything meaningful
(roughly: fewer than ~5 logged wear events) — *"Your story is just getting started —
log a few outfits and this page will fill in."* — both tabs share this same
threshold-gated empty state.
**Loading**: Stat Tile shimmer + strip shimmer.
**Error**: n/a, local derived queries only (ADR-006).
**Offline**: unaffected.

**Landscape**: the two headline Stat Tiles plus the colour row form a top band; the
strips sit in two side-by-side columns beneath.
**Phone**: fully stacked single column, time-window control becomes a compact
dropdown rather than an inline segmented row to save width.

---

## 14. Trips

**Purpose**: list of trips, each leading to its Packing List.

**Layout**: a simple list of Trip Cards — destination, date range, a small "packing
X% ready" progress indicator once a packing list exists — plus a "Plan a Trip"
primary button.

**Components**: Trip Card, primary button, a short form (destination, dates,
activities, luggage size) on tap of "Plan a Trip," which on submit generates the
packing list via the rule-based engine and navigates straight into it.

**Interactions**: tapping an existing trip opens its Packing List directly (Trips
itself is a thin index, not a screen with its own deep content).

**Empty state**: *"No trips planned — add one and we'll help you pack."*
**Loading**: card shimmer. **Error**: n/a. **Offline**: unaffected (weather-per-leg
suggestions degrade to the same stale-forecast treatment as Home's weather chip, not
a broken trip).

**Landscape/Phone**: standard list reflow.

---

## 15. Packing List

**Purpose**: the generated, explainable packing list for one trip, checkable off as
items go into the bag.

**Layout**:
```
┌───────────────────────────────────┐
│ ← Paris · 18–24 Aug                │
│                                    │
│  Carry-on · 4 activities            │
│                                    │
│  ☐ Cream Silk Blouse                │
│     For: dinner (Fri) — warm,       │
│     packs without creasing          │
│  ☑ Tailored Trousers                │
│  ☐ Waterproof Jacket                │
│     Gap: you don't own one yet      │
│                                    │
│  ┌─────────────────────────────┐  │
│  │     View as Travel Lookbook   │  │
│  └─────────────────────────────┘  │
└───────────────────────────────────┘
```

**Hierarchy**: a checklist, each item carrying its one-line rationale beneath it
(`phase-1-architecture.md`'s "derived, explainable" requirement made visible) —
items that are gaps (nothing owned fills that need) are shown unchecked, greyed,
with the gap explicitly labelled rather than silently omitted. A secondary action
switches to a "Travel Lookbook" view — the same list rendered as outfit-per-day cards
instead of a checklist, for browsing rather than packing.

**Components**: checklist row (checkbox + rationale caption), the lookbook toggle.

**Interactions**: checking an item off is a simple tap, brief 120ms checkmark
animation; the whole list can be regenerated ("Replan") if dates/activities change,
which replaces the list wholesale per `phase-3-persistence.md`'s repository design
(never a manual merge of old and new).

**Empty state**: n/a — a packing list is generated the moment a trip is created; it
always has at least gap-only content to show.
**Loading**: checklist-row shimmer while the engine generates the list.
**Error**: if the engine can't produce anything sensible (an empty closet), a plain
message: *"Add a few items to your closet and we'll build a packing list."*
**Offline**: unaffected (on-device generation).

**Landscape/Phone**: standard list reflow; Travel Lookbook view specifically
benefits from landscape's width (day-cards shown two-across rather than stacked).

---

## 16. Wishlist

**Purpose**: manually-tracked items not yet owned — reached as a segmented tab
within Closet (`navigation-flow.md`), not a separate destination.

**Layout**: a grid of Wishlist Cards (photo, name, estimated price, a small priority
indicator) with an "Add to Wishlist" action; each card supports "Mark as purchased"
(which offers to convert it straight into an Add Garment flow, pre-filled from the
wishlist entry, rather than making the user re-enter everything).

**Components**: Wishlist Card, Add button, priority indicator (a simple 1-3 dot
scale, not a numeric priority field — keeps entry effortless).

**Interactions**: "Mark as purchased" → pre-filled Add Garment (shared-element into
the same Add Garment flow described above); swipe-to-delete for removing an item
that's no longer wanted.

**Empty state**: *"Nothing on your wishlist — add something you're eyeing."*
**Loading**: card shimmer. **Error**: n/a. **Offline**: unaffected — no shopping
integration exists to be offline from (Section 0, `alta-class-closet-app-master-
prompt.md`: Shopping feed is CUT).

**Landscape/Phone**: standard grid reflow.

---

## 17. Search

**Purpose**: fast, scoped search — contextual, not a standalone destination
(`navigation-flow.md`).

**Layout**: expands in place from a toolbar icon into a full-width field with a
cancel action; results render as whatever content type is native to the section it
was opened from (Garment Tiles from Closet, Outfit Cards from Outfits, Trip Cards
from Home/Trips).

**Interactions**: live-filtering as text is typed (debounced ~150ms), matching
`phase-3-persistence.md`'s `searchText` `LIKE` design — no separate "search" button
press required. Recent searches are not stored/shown — a single-user app doesn't
benefit from a search history the same way a multi-session consumer app would, and
it's one less thing to design, build, and have feel stale.

**Empty state (no query yet)**: just the expanded empty field, cursor focused,
keyboard up — no "trending searches" or suggestion chips (nothing to suggest,
single-user app).
**Empty state (no results)**: *"Nothing matches '{query}'"* — plain, no illustration
(a full empty-state illustration for a no-results search is more ceremony than a
quick, low-stakes moment deserves).
**Loading**: n/a — local `LIKE` queries at this data volume return well under a
frame's worth of time (phase-3-persistence.md's own reasoning for rejecting FTS).
**Error/Offline**: n/a, fully local.

**Landscape/Phone**: identical behaviour, field width adapts to available space.

---

## 18–21. Settings (parent) — Profile, Backup & Restore, About (sub-screens)

**Purpose**: configuration and the few genuinely-personal preferences, kept out of
the way of daily use (ADR-004's "no accounts" posture means this is deliberately
thin compared to a typical consumer app's settings screen).

**Layout** (Settings, tablet portrait):
```
┌───────────────────────────────────┐
│  Settings                          │
│                                    │
│  Profile                       ›   │
│  Units                Metric   ›   │
│  Theme            Light / Dark ›   │
│  Style Rules                   ›   │
│  Backup & Restore              ›   │
│  About                         ›   │
└───────────────────────────────────┘
```

A plain grouped list — no icons-in-colored-circles (a common but busy pattern),
just Label-scale row titles and a trailing value/chevron. This screen should feel
almost invisible; it's infrastructure, not a feature.

**Profile**: occupation, sizing, menswear/womenswear/both, a free-text styling
preference blurb, preferred brands, budget bands (`phase-1-architecture.md`'s
`StyleProfile`) — a single scrollable form, grouped under Label-scale section
headers, saved automatically per field (no separate "Save" button for a settings
form — changes take effect as they're made, standard for this kind of screen).

**Backup & Restore**:
```
┌───────────────────────────────────┐
│  ← Backup & Restore                │
│                                    │
│  Last backup: 3 days ago            │
│                                    │
│  ┌─────────────────────────────┐  │
│  │      Back Up Now               │  │
│  └─────────────────────────────┘  │
│                                    │
│  ┌─────────────────────────────┐  │
│  │      Restore from File         │  │
│  └─────────────────────────────┘  │
│                                    │
│  Backups are saved as a single     │
│  file you choose the location for. │
│  Nothing is uploaded anywhere.     │
└───────────────────────────────────┘
```
A progress state (determinate bar + percentage, matching `core:domain`'s
`BackupProgress`/`RestoreProgress` Flow) replaces the buttons while running; a
completion toast confirms; a failure shows a plain-language dialog with the
specific reason where known (e.g. "Couldn't write to that location").

**About**: version number, a short line about what the app is and isn't (no
telemetry, no account, ADR-004), and nothing else — no social links, no rate-this-
app prompt (there's no app store audience to court), no changelog list (a single-
user app's owner doesn't need a marketing changelog for their own build).

**Empty/Loading/Error/Offline** (all four sub-screens): no empty state (settings
always has content); no network dependency anywhere in Settings; Backup/Restore's
loading state is the progress bar described above; a Restore failure is the one
place in Settings that shows a real error dialog, since a failed restore is
genuinely consequential.

**Landscape/Phone**: all four screens are simple vertical forms/lists; landscape
gets a modestly wider max-content-width (never full-bleed — see the design system's
line-length rule) rather than a new column.

---
