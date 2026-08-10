# Beta 1 — Daily Usage Friction Log

Phase 1 of `BETA_1_REPORT.md`'s source data. This is a raw log, not a
report — add a row every time something is annoying, confusing, slow, or
wrong while using the app normally. Don't filter for "is this worth
reporting" — the point of Phase 1 is to catch things a assumption-driven
review would never think to flag. `BETA_1_REPORT.md`'s Phase 1 section
gets written by summarizing this log once it has real entries, not the
other way around.

Copy the row template below for each new entry. Keep entries in the order
you hit them — no need to sort or group.

## How to rate severity

- **Blocker** — couldn't complete what you were trying to do at all.
- **High** — completed it, but had to work around something broken.
- **Medium** — completed it fine, but it was confusing, slow, or annoying.
- **Low** — a nitpick; wouldn't stop you from recommending the app.

## Log

| # | Date | Screen/flow | Steps to reproduce | Expected | Actual | Severity | Screenshot? |
|---|------|-------------|---------------------|----------|--------|----------|-------------|
| 1 |      |             |                     |          |        |          |             |

<!--
Example of a filled-in row, delete once you have real entries:

| 1 | 2026-08-07 | Add-to-Wardrobe review screen | Imported a navy hoodie, tapped the Category chip suggestion | Category field fills with "Hoodie" | Field stayed empty, chip did nothing | Medium | no |
-->

## Friction categories to watch for specifically (per the Beta 1 spec)

Use these as a checklist while you use the app, not just when something
obviously breaks:

- [ ] Too many taps to do something routine
- [ ] Confusing wording (a label, a button, an empty-state message)
- [ ] Slow screens (anything that feels like it's making you wait)
- [ ] Poor loading states (no spinner/progress where one is needed, or one
      that never resolves)
- [ ] AI mistakes (wrong metadata, a nonsensical outfit suggestion, a bad
      try-on render)
- [ ] Retry frequency (how often you needed to hit Retry/Regenerate before
      getting something usable)
- [ ] Incorrect metadata (specifically: field was filled, but wrong — as
      opposed to just not filled)
- [ ] Poor try-on quality (specifically what looked wrong: placement,
      lighting, edges, proportions)
- [ ] Styling suggestions that don't make sense (wrong weather, wrong
      occasion, items that clash, incomplete outfits)

## Rollup (fill in once the log has entries)

| Severity | Count |
|----------|-------|
| Blocker  |       |
| High     |       |
| Medium   |       |
| Low      |       |

Top 3 recurring themes (fill in once patterns emerge, not from a single
occurrence):

1.
2.
3.
