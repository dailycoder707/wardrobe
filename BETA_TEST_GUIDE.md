# Beta Test Guide

For whoever runs the first private beta (this app's own single-household
scope per ADR-012 — realistically the user and their spouse). This is a
usage/testing guide, not a developer setup guide — see `README.md` for
that.

## Before you start

- Install the beta build (debug or a release build signed with your own
  `keystore.properties` — see `README.md`).
- You do not need a cloud AI provider account to use this app at all —
  every capability works fully offline by default. Only set one up if you
  specifically want to try the optional cloud path.

## What to test — Add-to-Wardrobe (core flow)

1. Tap the "+" / Add to Wardrobe action from Home or Closet.
2. Try both entry points: **Take Photo** and **Choose from Gallery**.
3. Try **Import Multiple Photos** at least once.
4. On the review screen:
   - Switch between the Original / Transparent Cutout / White Background
     tabs.
   - Pinch-to-zoom and pan on the image.
   - Try the "What AI changed" comparison strip.
   - Tap the ⓘ next to any suggested field to see where it came from.
   - Edit at least one AI-suggested field yourself and confirm it sticks.
   - If any field shows "Unknown — please choose," fill it in.
   - If a quality warning appears (blurry/cluttered/occluded), try
     retaking that specific photo and see if it goes away.
   - Try each Retry button (Extraction/Enhancement/Metadata) at least
     once.
5. Save one garment for real, and **Save as Draft** another — confirm
   the draft shows a "Needs Review" badge and reopening it resumes where
   you left off.
6. Edit a saved garment's details afterward, and delete one (confirm
   Undo works within its snackbar window).

**Report**: anything that crashed, anything that looked visually wrong,
any suggestion that seemed clearly incorrect, and which garment type it
was (shirt/pants/dress/jacket/skirt/shoe/scarf) — extraction quality can
vary a lot by category.

## What to test — everyday use

- Build an outfit manually (Outfit Builder) and separately check Today's
  Recommendations.
- Log a few days of "what I wore" and check Wear History/Stats.
- Add a Trip and check its packing suggestions.
- Try Search and Filters in Closet with a realistic number of items.
- If you have a second device, try pairing and syncing between them
  (Settings → Wardrobe Sync).

## What to test — optional cloud AI (only if you want to)

1. Go to Settings → AI Providers.
2. Pick one capability (Garment Metadata is the simplest to try first),
   switch it to Cloud, pick a vendor, and enter your own API key and Base
   URL/model as required by that vendor.
3. You'll be shown a consent dialog naming the destination — read it, then
   confirm.
4. Tap **Test Connection** before doing anything else — if it fails, fix
   the config before continuing.
5. Try that capability for real (re-run metadata generation on a garment,
   or try Outfit Styling / Virtual Try-On if you enabled those).
6. Check Settings → AI Providers → AI Usage afterward — confirm a call
   was actually logged (count, latency).
7. Try switching back to On-Device and confirm the feature still works
   exactly as before you enabled cloud.

**Report**: which vendor/model you used, whether Test Connection
succeeded, whether the capability's actual result looked reasonable, and
anything that failed silently or looked wrong. Costs are your own — this
app never bills you or stores payment info; you're using your own
provider account and key.

## What NOT to worry about reporting

- Missing baseline profile / cold-start performance — known, tracked in
  `PRODUCTION_VALIDATION_REPORT.md`, not yet measured on real hardware.
- Placeholder-feeling app icon — known, tracked in `RELEASE_CHECKLIST.md`
  §5, real brand art is a separate piece of work.
- Anything already listed in `KNOWN_LIMITATIONS.md` — you're welcome to
  confirm it still reproduces, but it's not a new finding.

## How to report an issue

For each issue: what you did, what you expected, what actually happened,
and if possible the garment photo or screen involved (photos never leave
your device unless you've explicitly enabled and consented to cloud AI
for that specific capability — see `SECURITY_AUDIT.md` if you want the
full technical detail on what that does and doesn't send). A screen
recording is more useful than a description for anything visual.
