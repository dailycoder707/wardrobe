# Known Limitations

A plain-language summary of what doesn't work yet, or works with a real
caveat, as of RC1. This is the user-facing companion to `TECHNICAL_DEBT.md`
(which has the full engineering detail and reasoning behind each item) —
read this one if you just want to know what to expect; read that one if
you want to know why.

## Not yet validated on a real device or a real cloud account

Everything below has been verified as far as automated tests and code
review can confirm, but has never been run on a physical phone or against
a live cloud AI account — see `PRODUCTION_VALIDATION_REPORT.md` for the
full checklist:

- The entire Add-to-Wardrobe camera/gallery capture flow.
- Every supported cloud AI vendor (OpenAI, Azure OpenAI, Gemini, Claude,
  OpenRouter, Ollama, Generic REST) — each is tested against a mock
  server, not a real account.
- Cold start, memory, battery, and scrolling performance.

## AI features

- **Cloud AI is entirely optional and off by default.** Every capability
  (Garment Extraction, Reconstruction, Metadata, Outfit Styling, Virtual
  Try-On) works fully on-device unless you specifically configure and
  consent to a cloud provider for it.
- **Ollama and self-hosted "Generic REST" providers need HTTPS** (or a
  device-level exception you set up yourself) — a plain `http://` local
  address won't connect, by Android's own secure default. This is a
  disclosed tradeoff, not a bug: see `SECURITY_AUDIT.md` §5.
- **On-device garment extraction/reconstruction/metadata** cover the
  common cases well but aren't perfect on every photo — thin straps,
  cutout necklines, heavy occlusion, and unusual lighting are the hardest
  cases.
- **On-device Virtual Try-On** (the "Compare with Cloud" viewer
  specifically) always anchors a garment at the shoulder line, since that
  comparison path doesn't know which slot (top/bottom/shoes/etc.) the
  garment belongs to. The regular, interactive Try-On screen is
  unaffected and places garments correctly by slot.
- **Outfit Styling's optional inspiration-image feature has no screen to
  use it from yet** — the underlying support exists, but there's no
  camera/gallery picker wired to it in this release.
- No named cloud vendor has ever been tested against a real, live
  account in this development environment — only against a simulated
  server matching each vendor's documented API shape.

## Wardrobe & recommendations

- Outfit recommendations are a rule-based engine (or, optionally, a
  cloud-suggested alternative validated against your real wardrobe) —
  neither is a fashion expert; treat suggestions as a starting point.
- Recurring outfit plans create real, individually-editable calendar
  entries for 8 weeks at a time, not an open-ended repeating rule.
- Duplicate-garment detection is based on category/color/exact-photo
  matching, not visual similarity — a genuinely similar-looking item
  photographed differently won't be flagged.
- "This season" framing is based on the last 90 days, not your actual
  hemisphere/climate.

## Sync

- Multi-device sync works over your local Wi-Fi network only, never the
  internet, and both devices need to be on the network at the same time
  for a sync session to happen.
- No real two-device sync test has been run in this development
  environment (no second physical device available) — every sync
  component is tested against a simulated peer.

## Everything else

- No app icon/brand art yet — the current icon is a placeholder.
- No accessibility (TalkBack, RTL) pass has been run on a real device.
- No tablet-specific layout exists yet, though nothing is expected to
  visually break on a larger screen.
- Performance at large wardrobe sizes (hundreds of items) is designed
  for and covered by JVM-only regression tests, not measured on a real
  device yet.

## What this app will never do

Per this project's own permanent privacy principles (see
`docs/adr/ADR-011-permanent-privacy-first-principles.md` and its
`ADR-012` amendment): no accounts, no analytics, no ad SDK, no crash-
reporting SDK, no cloud storage of your wardrobe data, and no cloud AI
processing without your specific, per-capability, revocable consent.
