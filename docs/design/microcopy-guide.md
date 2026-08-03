# Microcopy Guide

Every voice principle below exists to prevent the same failure mode: generic
enterprise-app tone leaking in through a default string. Read this before writing any
user-facing text in Phase 5 — it's easier to keep a voice consistent by having the
rules written down than by "just feeling it out" per-screen.

## Voice principles

1. **Speak to this app's one user by name, not to "the user."** Never "Welcome back,
   User" — "Good Morning, {displayName}." That name is never a hardcoded string
   anywhere in the app — it's the editable Display Name field in Personalization
   Settings (`phase-5a-data-layer.md`), read live through `PersonalizationRepository`
   so every greeting updates the instant the name is changed, no restart. Never "Your
   items" as a cold aggregate — "your closet," "your looks," "your wardrobe story."
2. **Warm, unhurried, a little bit editorial** — closer to a considerate stylist
   friend than a productivity app. Short sentences are fine; clipped corporate
   fragments ("3 items pending") are not.
3. **Never guilt, never gamify.** A dormant item is framed as an invitation ("Worth
   another look"), never a scold ("You haven't worn this in 90 days!"). No streaks,
   no badges, no "don't break your streak" pressure — there is no one to perform for.
4. **Confidence without hype.** Suggestions are offered plainly ("Layered for a mild
   morning"), never oversold ("PERFECT match!! 🔥").
5. **Honest about limits.** When something can't be done (a delete blocked by wear
   history, a stale weather forecast, a failed background removal), say exactly what
   happened and what to do next — never a vague "Something went wrong."

## Greetings (Home)

`{displayName}` is always the live value of Personalization Settings' Display Name
(`PersonalizationSettings.greetingText`, `core:model`) — never a literal string in
code, a resource file, or a design doc example rendered as if it were fixed. If no
display name is set yet, the name and its leading comma are dropped entirely
("Good Morning" — never "Good Morning,").

**Time-of-day style** (the default `GreetingStyle`):

| Time of day | Line |
|---|---|
| Morning (05:00–11:59) | "Good Morning, {displayName}" |
| Afternoon (12:00–17:59) | "Good Afternoon, {displayName}" |
| Evening (18:00–04:59) | "Good Evening, {displayName}" |

**Alternative styles** (user-selectable in Personalization Settings, replacing the
time-of-day line entirely rather than combining with it):

| Style | Line |
|---|---|
| Welcome Back | "Welcome back, {displayName}" |
| Ready For Today | "Ready for today, {displayName}" |

Subtitle beneath the greeting is always the date, plain — "Tuesday, 12 August" — no
forced enthusiasm ("Happy Tuesday!"). The greeting section itself can be hidden
entirely (Personalization Settings' "Show Greeting" toggle) — when off, Home simply
starts at the next visible card, never a blank placeholder in its place.

## Empty states

| Screen | Line |
|---|---|
| Home, day one | "Your closet's still empty — start by adding what's already in front of you." |
| Closet, no items | "Nothing here yet — your closet starts with one photo." |
| Closet, filtered to nothing | "No items match these filters." (+ "Clear filters" action) |
| Wishlist | "Nothing on your wishlist — add something you're eyeing." |
| Saved Looks | "No looks saved yet — build one, or save a suggestion from Home." |
| Calendar day, nothing logged | "Nothing logged for this day." |
| Wear History | "Nothing logged yet — start on Calendar." |
| Wardrobe Story, not enough data | "Your story is just getting started — log a few outfits and this page will fill in." |
| Trips | "No trips planned — add one and we'll help you pack." |
| Search, no results | "Nothing matches '{query}'" |

## Encouragement (framing dormant/underused items positively)

- "Worth another look" (section header, Home/Statistics — never "Rarely worn" or
  "Neglected")
- "It's been a while since this one's had a moment"
- "Still one of your best cost-per-wear pieces" (for a well-used item, said warmly,
  not as a spreadsheet fact)

## Confirmation & success

- Save (garment): "Saved to your closet"
- Save (look): "Look saved"
- Favourite: "Added to Favourites" / "Removed from Favourites"
- Backup complete: "Backup complete — saved to {location}"
- Wear logged: "Logged for today" (or the relevant date if backdated)

## Confirmation dialogs (destructive/consequential actions)

- Delete garment (no history): "Remove {name} from your closet? This can't be undone."
  — buttons: "Cancel" / "Remove"
- Delete garment (blocked, has wear history): not a dialog at all — an inline
  explanation instead: "This item has been logged as worn, so it can't be deleted —
  you can mark it Sold or Donating instead." (see Garment Detail spec)
- Delete outfit (blocked, has wear history): same pattern, outfit-specific wording.
- Restore from backup: "Restoring will replace everything currently in your closet
  with the contents of this backup. This can't be undone." — buttons: "Cancel" /
  "Restore"

## Loading messages

Used sparingly — most loading states are silent shimmer, not text (Section on
Loading State, `component-library.md`). Text is reserved for genuinely
multi-second operations:

- Background removal: "Processing…"
- Backup/Restore in progress: "Backing up… {percent}%" / "Restoring… {percent}%"

## Error messages

- Background removal failed outright: "Couldn't auto-crop this one — you can still
  save it, or try retaking the photo."
- Prettify Photo failed: "Couldn't improve this one — kept your original crop."
- Styling engine has nothing to suggest: "Add a few more pieces and we'll have
  suggestions ready."
- Weather stale (chip, not a dialog): "· yesterday's forecast" appended inline,
  dashed border — never a popup for this.
- Restore failed: "Couldn't restore from that file. {specific reason if known, e.g.
  'The file may be from a newer version of the app.'}"
- Generic last resort (should be rare — most failures above have a specific
  message): "That didn't work. Nothing's been changed — you can try again."

## Wardrobe Story framing

- "62% worn this year" (never "38% wasted" or "38% inefficiency" — same number,
  framed as what *is* used, not what isn't)
- Gap Analysis intro line: "A few things your closet doesn't quite cover yet:"
- Gap Analysis footer: "These are patterns in what you already own — never a
  shopping list from us." (an explicit, deliberate reassurance — see ADR-004/Section
  0 of the master prompt: Gap Analysis must never read as a sales pitch)

## Today's Inspiration (Home, rotating line)

A short rotating set of plain styling observations, never generated hype, never
attributed to "AI" by name in the copy itself (the tone should feel like a personal
note, not a feature callout):

- "Cost-per-wear rewards patience, not restraint."
- "The pieces you reach for without thinking are usually the ones worth repeating."
- "A good outfit is mostly about fit and confidence, not novelty."

(A handful more of these should be written before Phase 5 ships Home — treat this as
a starter set, not the complete list; keep every one short, plain, and free of
exclamation points.)
