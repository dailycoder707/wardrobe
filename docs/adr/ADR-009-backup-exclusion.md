# ADR-009: Exclude App Data from Android Auto Backup's Cloud Channel

**Status**: Accepted (Phase 1 Section 0 pushback #2 / Section 24) — configured in Phase 2

## Context

Android's Auto Backup silently copies an app's Room database and internal files to the
user's Google Drive account by default on API 23+, with no in-app UI and no relation to
this app's own, explicit Backup/Restore feature. For an app whose entire premise
(ADR-004) is "no cloud, ever," this is a real, easy-to-miss way that premise could be
silently broken by default OS behavior rather than by anything the app's own code does.
This was flagged before any code existed (Phase 1 Section 0) specifically because it's
the kind of thing that's invisible until a user notices their photos synced somewhere
they didn't expect.

## Decision

Two files, both configured in Phase 2:
- `data_extraction_rules.xml` (API 31+): excludes the Room database, `images/`,
  the DataStore preferences file, and all SharedPreferences from the **cloud-backup**
  channel specifically, while leaving the **device-transfer** channel (a direct
  cable/Wi-Fi-Direct copy to a new phone during setup) unrestricted.
- `backup_rules.xml` (legacy, API 23–30): the same exclusions, applied to the single
  undifferentiated Auto Backup channel those OS versions have (no cloud/device-transfer
  split exists pre-API-31).

`android:allowBackup="true"` is kept (not set to `false`) specifically so the
device-transfer channel keeps working on Android 12+ — see Alternatives below.

## Consequences

**Positive**:
- Closes the single most likely way this app's "no cloud" premise could be violated
  without any application code being at fault.
- A user moving to a new phone via the standard Android device-to-device transfer flow
  still gets their wardrobe carried over, without that transfer ever touching a server.

**Negative**:
- If a user loses or wipes their phone without ever running the in-app manual export,
  there is no OS-level safety net — this is an explicit, accepted tradeoff (see
  Alternatives), not an oversight.
- Two separate exclusion files must be kept in sync with the data model as new storage
  locations are added (e.g. if a future feature introduces a new file location, both
  files need updating) — a real, ongoing maintenance obligation.

## Alternatives Considered

- **`android:allowBackup="false"` entirely**: rejected — this also disables the
  legitimate, non-cloud device-transfer channel (Android 12+'s cable/Wi-Fi-Direct
  "copy your data" flow during new-phone setup), meaning a user who upgrades phones
  without first running the manual export loses their entire wardrobe. Excluding only
  the cloud channel gets the privacy guarantee without that downside.
- **No exclusion at all (rely on the manual Backup/Restore feature only, let Auto
  Backup do whatever it does by default)**: rejected outright — this is precisely the
  silent-cloud-copy risk this ADR exists to close.
- **Encrypt the database at rest so a cloud copy would be harmless even if it happened**:
  considered and rejected for v1 — it doesn't address the actual complaint (a promise
  of "no cloud," not merely "no *readable* cloud copy"), and adds real complexity
  (SQLCipher-backed Room, key management) for a problem better solved by simply not
  letting the copy happen. Noted in `phase-1-architecture.md` Section 24 as a
  contained future option if a shared-device threat model ever becomes relevant.
