# Release Notes

New file, added at M13 (Production Validation & Release Gate) — this project
had no prior release, so there is one entry so far. Entries are added
newest-first; each documents what a real user would notice, not internal
refactors (those live in `TECHNICAL_DEBT.md`/the ADRs).

---

## Unreleased — RC1 (Production Hardening & Release Candidate)

**Status**: `./gradlew clean build` green; a full security/release/
dependency/memory/architecture audit completed (`SECURITY_AUDIT.md`);
three real hardening fixes applied (a Try-On preview file leak, unbounded
AI cache growth, and an encrypted-API-key device-transfer crash risk — see
`CHANGELOG.md` for the full list); one unused dependency removed. **Still
not yet released** — real-device and real-cloud-provider validation
remain open, see `PRODUCTION_VALIDATION_REPORT.md`.

Nothing user-facing changed in this pass beyond what's already listed
below from M12 — RC1 was hardening and documentation only, per its own
"no new features" rule. See `KNOWN_LIMITATIONS.md` for the current,
user-facing limitations summary and `BETA_TEST_GUIDE.md` if you're
running the beta yourself.

## Add-to-Wardrobe v2 / Unified AI Provider Architecture

### Added

- **Premium Add-to-Wardrobe review screen**: a segmented Original/
  Transparent Cutout/White Background image viewer with pinch-to-zoom and
  pan, a 4-stage "what AI changed" comparison strip (Original → Extracted →
  Enhanced → Reconstructed), confidence-tiered attribute suggestions (High
  auto-selects, Medium is a tap-to-accept chip, Low is shown but never
  auto-applied), a provenance info button on every AI-suggested value, an
  honest AI-processing status card, photo-quality warnings with a retake
  recommendation, and one-tap retry for extraction/enhancement/metadata —
  all without ever forcing a recapture.
- **Optional cloud AI processing**, per capability, entirely opt-in: Garment
  Extraction, Garment Reconstruction, Garment Metadata, Outfit Styling, and
  Virtual Try-On can each independently run on-device (the default, and
  the automatic fallback) or against a cloud provider you configure and
  explicitly consent to in Settings → AI Providers. Supported vendors:
  OpenAI, Azure OpenAI, Gemini, Claude, OpenRouter, Ollama, or any
  self-hosted/generic REST backend implementing the documented multipart
  contract.
- **Cloud Outfit Styling**: an optional cloud-suggested outfit alongside
  the existing offline rule engine, always validated against your real
  wardrobe and this app's own styling rules before it can be shown — never
  a hallucinated combination. Suggestions are labeled "AI Styled" with the
  provider/confidence/reasoning available behind an info button; the rule
  engine remains the default and the automatic fallback.
- **Cloud Virtual Try-On**: an optional cloud-rendered try-on alongside the
  existing on-device compositing, with a side-by-side Original/On-Device/
  Cloud comparison viewer. Preview only — never overwrites your garment's
  own saved photo.
- **AI Usage panel** (Settings → AI Providers): per-capability call counts,
  cache-hit counts, average latency, and an optional estimated cost (only
  shown if you supply a price-per-1K-tokens rate; otherwise left blank
  rather than guessed).
- **Test Connection** button per configured cloud provider, so a
  misconfigured key/URL is caught in Settings, not mid-import.

### Changed

- Nothing about the existing fully-offline experience changes unless you
  opt in: with no cloud provider configured, every capability behaves
  exactly as before, on-device only.

### Privacy

- A cloud provider is never used without an explicit, plain-language
  consent dialog naming the destination host; declining reverts to
  on-device. Changing a provider's Base URL invalidates prior consent for
  that capability.
- Before any photo is sent to a configured cloud provider, the app blurs
  any visible face (extraction only — every other capability's cloud call
  already receives only the extracted, faceless garment cutout), resizes
  it below the on-device working resolution, and re-encodes it — the
  re-encoding step cannot carry over EXIF metadata (verified by an
  automated test, not just assumed).
- API keys are stored in `EncryptedApiKeyStore` (Android Keystore-backed),
  never in plain preferences, never logged.

### Known limitations

See `TECHNICAL_DEBT.md` item 18 for the full list. Highlights: no named
vendor adapter has yet been exercised against a real, live account (this
release's cloud paths are unit-tested against mock servers only); the
inspiration-image parameter for Cloud Styling has no UI entry point yet;
the on-device Virtual Try-On wrapper anchors every garment at the shoulder
line (a disclosed simplification specific to the new comparison-viewer
path — the existing interactive Try-On screen is unaffected).
