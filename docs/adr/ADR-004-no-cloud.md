# ADR-004: No Cloud, No Accounts, No Third-Party SDKs

**Status**: Accepted (Section 0, `alta-class-closet-app-master-prompt.md`).
Hardened from "current posture" to a **permanent** product principle by
[ADR-011](ADR-011-permanent-privacy-first-principles.md) (2026-08-03) — see
that ADR for why this is no longer framed as something a future funding
event could reverse.

## Context

The teardown of the source app (`alta-class-closet-app-master-prompt.md` Part 1–2)
made clear that roughly 70% of that product's feature surface is server-bound and
metered per user: avatar try-on, LLM stylist chat, shopping/affiliate, community feed,
receipt-email parsing. The user explicitly chose the zero-cost/offline posture over
that, which this ADR formalizes as a binding constraint on every future phase, not just
a Phase-1-time decision.

## Decision

No backend, no user accounts/auth, no Firebase, no analytics SDK, no crash-reporting
SDK, no third-party API beyond the one free/keyless weather call
(`core:network`, Open-Meteo). Photos and all personal data stay device-local, with the
only intentional copy mechanism being the user-triggered Backup/Restore export
(`feature:settings`).

This also governs what App-level infrastructure is (and isn't) allowed:
- Crash visibility comes from Play Console's built-in, SDK-free crash/ANR reporting —
  not Crashlytics, which would itself be a Firebase dependency.
- Android's own Auto Backup is explicitly excluded from carrying user data to the
  cloud (see ADR-009) — an easy way this decision could be silently violated by
  default OS behavior if left unaddressed.

## Consequences

**Positive**:
- Zero recurring infrastructure cost, ever, regardless of user count.
- No auth flow, no session/token security surface, no server-side data breach exposure
  — the strongest possible privacy story for a data type as sensitive as photos of a
  user's home/wardrobe.
- No dependency on any third party's uptime, pricing changes, or policy changes.

**Negative**:
- No cross-device sync without manual export/import, at the time this ADR
  was written — **superseded by Phase 8**, which built encrypted
  local-network device-to-device sync (pairing via QR, incremental outbox,
  conflict resolution). This does not reopen the no-cloud decision: sync
  never touches a server, only the two paired devices' own Wi-Fi
  connection (see ADR-011 rule 8).
- The single most-praised feature in the teardown (avatar/virtual try-on)
  and the entire commerce/community feature set were CUT at Phase 1 — the
  commerce/community set remains cut, but virtual try-on is now Phase 10,
  built as a fully **local, 2D, non-cloud** system rather than the
  cloud-rendered avatar this ADR originally rejected on cost/privacy
  grounds. See ADR-011 for why that's additive, not a reversal.
- No server-side LLM styling — the recommendation engine must be rule-based
  (`phase-1-architecture.md` Section 6/Phase 6), which is a real capability ceiling
  against an LLM-backed competitor. ADR-011 makes this permanent rather than
  provisional.

## Alternatives Considered

- **Minimal free-tier backend** (e.g. Supabase/Firebase free tier) for just auth +
  sync: rejected — it reopens exactly the recurring-cost and privacy-surface questions
  this decision exists to close, for a feature (sync) that isn't core to the "know
  your own closet" value proposition.
- **Firebase Crashlytics specifically** (separate from a full backend): rejected
  explicitly and by name in `phase-1-architecture.md` Section 24 — it's a Firebase
  dependency regardless of whether the rest of Firebase is used.
- **Revisit later as a funded product**: originally left open deliberately —
  `phase-1-architecture.md` Section 30 documented which future features
  (AI Stylist, Virtual Try-On, Shopping, Cloud Sync) would become newly
  viable if this decision were ever reversed. **This door is now closed**:
  ADR-011 (2026-08-03) makes the no-cloud/no-accounts/local-only-ML posture
  a permanent product principle, not a provisional one awaiting a business
  decision. Virtual Try-On (Phase 10) and Multi-Device Sync (Phase 8) both
  shipped as fully local features precisely because the architecture was
  already shaped to make that additive — the same shape ADR-004 predicted,
  just without ever needing the cloud reversal it also predicted.
