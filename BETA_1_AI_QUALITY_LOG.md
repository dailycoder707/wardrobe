# Beta 1 — AI Quality Sampling Log

Phase 2 of `BETA_1_REPORT.md`'s source data: the spec's minimum sample
sizes are 100 garment imports, 50 outfit recommendations, and 30 virtual
try-ons. This file is where you record each one as you go — the point is
to **categorize failures, not fix them immediately** (that's Phase 6, the
bug-fix sprint, done afterward once patterns are visible). Don't skip a
row just because the result was good; the success rate only means
something if every attempt is counted, not just the memorable failures.

Every table below can be tracked in whatever tool you actually prefer (a
spreadsheet is probably easier for 100+ rows than Markdown) — this file is
the canonical *definition* of what to record and how to categorize it, and
where the final counts go once you're done. If you track elsewhere, paste
the final tallies into the "Totals" line under each table before Phase 7.

## 1. Garment imports (target: 100)

For each import, record which pipeline stage(s) needed a correction and
whether that correction was on-device or cloud (Settings → AI Providers
tells you which mode each capability was in at the time).

| # | Garment type | Mode (on-device/cloud) | Extraction OK? | Metadata fields wrong (list which) | Needed retry? (which stage) | Notes |
|---|--------------|-------------------------|-----------------|--------------------------------------|-------------------------------|-------|

**Metadata accuracy categories** — when a field is wrong, use one of:
- `not-suggested` — field left blank, no suggestion offered at all
- `suggested-not-applied` — a suggestion showed but didn't autofill (see
  `TECHNICAL_DEBT.md` item 21 for a known cause: reference-data name
  mismatch)
- `suggested-and-wrong` — it autofilled or you tapped it, and it was
  factually incorrect

**Totals**:
- Imports logged: `___` / 100
- Extraction OK (no retake needed): `___`%
- At least one metadata field wrong: `___`%
- Most common wrong field: `___`

## 2. Outfit recommendations (target: 50)

| # | Occasion/context | Mode | Made sense? (Y/N) | Weather-appropriate? (Y/N) | Complete outfit? (Y/N) | If no, why |
|---|-------------------|------|---------------------|-------------------------------|--------------------------|------------|

**"Made sense" means**: you'd actually consider wearing it, or it's at
least a plausible combination — not that it matched your personal taste
exactly.

**Totals**:
- Recommendations logged: `___` / 50
- Made sense: `___`%
- Weather-appropriate: `___`%
- Common failure pattern (e.g. "always suggests the same 3 items",
  "ignores occasion", "clashing colors"): `___`

## 3. Virtual try-ons (target: 30)

| # | Garment type | Mode (on-device/cloud) | Placement OK? | Lighting/shadow OK? | Overall realism (1-5) | Notes |
|---|--------------|-------------------------|-----------------|------------------------|--------------------------|-------|

**Totals**:
- Try-ons logged: `___` / 30
- Average realism score: `___` / 5
- Most common defect (e.g. "garment floats", "wrong scale", "shadow
  direction wrong"): `___`

## Reminder: what this log feeds into

Once all three tables have real rows, the totals above are what
`BETA_1_REPORT.md`'s "AI success rates" and "most common failure types"
sections are built from — don't estimate those numbers there, copy them
from here.
