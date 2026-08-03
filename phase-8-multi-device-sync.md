# Phase 8 — Multi-Device Sync & Companion Experience

Encrypted, local-network-only synchronization between a paired tablet and
phone — device pairing via QR code, incremental database sync via a
row-level outbox, hash-deduplicated image transfer, deterministic conflict
resolution, a Wardrobe Sync settings screen, and a subtle Home confirmation
when a background sync completes. No accounts, no internet, no cloud —
everything routes over the local Wi-Fi network the two devices already
share, and the app is fully functional if that network (or the other
device) never appears at all.

**Scope note, agreed with the user before implementation began:** the
brief's Personalized Avatar / 2D outfit-preview-on-avatar system is
deliberately **not** part of this phase. It was explicitly marked CUT in the
master prompt's own feature-tier table ("Avatar generation + virtual
try-on... no credible free/on-device path") and listed under Phase 1's
"Future extensibility (planned, not implemented)" section. Multi-device sync
— the more foundational, less speculative half of the brief — was built
first; the avatar system remains a separate, not-yet-scoped future phase.

## Architecture

```
core:model     PairedDevice, SyncStatusSnapshot/SyncState, SyncConflict/
               ConflictReason/ConflictResolution, SyncHistoryEntry/SyncOutcome,
               SyncEntityType, SyncPreferences
core:database  MIGRATION_4_5 — syncId/updatedAt on every syncable table,
               sync_change_log (outbox), paired_device, sync_conflict,
               sync_history, per-table DB triggers
core:domain    DevicePairingRepository, SyncRepository, SyncScheduler,
               SyncPreferencesRepository
core:sync      (new module) crypto (Keystore identity + ECDH/AES-GCM session
               crypto), pairing (QR payload + ZXing codec + pairing exchange),
               discovery (NsdManager), transport (handshake, encrypted framed
               transport, change-batch session), protocol (wire messages)
core:data      16 SyncEntityHandlers, SyncEntityRegistry, SyncEngine
               (orchestrates handshake → image transfer → change exchange),
               DevicePairingRepositoryImpl, SyncRepositoryImpl (discovery
               race), SyncWorker, DI
core:datastore SyncPreferencesDataStore; lastModifiedAt tracking added to
               PersonalizationDataStore/StylistPreferencesDataStore
feature:settings  Wardrobe Sync screen, Pairing screen (show/scan QR tabs)
feature:closet Home's subtle "Wardrobe updated just now" line; Developer
               Panel's Sync Diagnostics section
app            WorkManager scheduling, manifest permissions, nav wiring
```

## Why not UUID primary keys

Every table in this schema uses a local `Long AUTOINCREMENT` primary key.
Two offline devices creating garments independently will assign the *same*
number to two *different* garments — there is no coordination between them
until they first sync. Rewriting every primary key to a UUID would be the
"obviously correct" textbook answer, but it's also a wholesale schema
rewrite touching every entity, every DAO query, every foreign key, and every
call site across seven phases of existing code, for a personal, two-device
app where the actual identity-collision risk is bounded and well
understood.

Instead, every syncable table gained a second column, `syncId` (a UUID,
unique-indexed), *alongside* its existing local `id`. `syncId` is the only
thing the wire protocol ever references — never the local `id`, which stays
purely an internal implementation detail of whichever device holds it. A
device receiving a change resolves every foreign-key reference (`categorySyncId`,
`brandSyncId`, …) to its *own* local id via a lookup by `syncId`
(`SyncEntityRegistry`'s `resolver`), inserting a fresh local row (with a
fresh, that-device's-own local id) the first time it sees a given `syncId`.
This is the standard, lower-risk pattern for retrofitting sync onto an
existing autoincrement-keyed schema, and it's why every entity handler's
wire payload is built from field names ending in `SyncId`, never `Id`.

## Change tracking: a database-level outbox, not a repository hook

Every syncable table gets three triggers (`AFTER INSERT`/`UPDATE`/`DELETE`)
writing one row into `sync_change_log` — `(tableName, syncId, operation,
changedAt)`. This is deliberately **database-level**, not a hook added to
each repository's mutating methods: a trigger cannot be forgotten at a new
call site the way a manual "don't forget to log this change" convention
can. `sync_change_log.id` (not `changedAt`) is the durable sync cursor: a
device remembers the highest id it has already sent to a given peer
(`paired_device.lastSyncedChangeLogId`) and queries `id > cursor` next
time — correct even when two changes land in the same millisecond.
`SyncChangeLogDao.compact()` collapses the outbox to one row per
`(table, syncId)` after a successful sync, so an item edited ten times
before ever syncing is only sent once.

Cross-ref/"collection" tables (`garment_seasons`, `garment_tags`,
`outfit_garments`, …) are **not** independently tracked — they ride along
inside their parent `GarmentEntity`/`OutfitEntity` payload and are merged as
sets at apply time, per the brief's "Collections: Merge" rule. `weather_cache`
and `stats_cache` are excluded entirely: one is device-local by nature (a
forecast for *this* device's location), the other is a derived cache
recomputed from already-synced data, never a source of truth.

## Protocol

Two phases, both over one TCP socket, `core:sync`'s
`EncryptedFrameTransport` framing every message after the handshake:

1. **Handshake** (necessarily plaintext-framed — no session key exists
   yet, but never *unauthenticated*): each side generates a fresh
   ephemeral EC keypair for this session alone and signs its own public
   key with its long-term identity key (`AndroidKeyStore`-backed,
   `PURPOSE_SIGN`). The receiver — who already pinned the sender's
   identity public key at pairing time — verifies that signature before
   trusting the ephemeral key, then both sides run ECDH on the ephemeral
   keys to derive an identical AES-256 session key neither ever
   transmitted. A device presenting a valid signature for an identity
   nobody paired with is rejected outright (`UnknownPeerException`) —
   "prevent unknown devices joining," concretely.
2. **Data exchange**: an image-transfer phase (checksum manifest exchange,
   then whole-file transfer of only what's actually missing), then a
   change-batch exchange (`SyncSession.exchange`) where both sides send
   their own outbox batch and each acknowledges the cursor it received up
   to. A batch is only ever marked "sent" (cursor advanced) once the peer
   has actually acknowledged it — a connection dropped mid-session simply
   means the same, still-un-acked rows are resent next time.

Every frame after the handshake is AES-256-GCM encrypted with its own
sequence number as associated data, so a captured frame replayed later, or
two frames delivered out of order, fails the authentication tag check
instead of silently decrypting.

## Pairing

Tablet: Settings → Connect Phone → Generate QR encodes a `PairingOfferPayload`
(deviceId, display name, identity public key, a one-time pairing token, this
device's current local IP/port) as a QR code (`core:sync`'s
`PairingQrCodec`, plain ZXing — no Google Play Services / ML Kit
dependency for what is genuinely a small-text encode/decode, not a task
that needs an ML model). Phone: Scan QR uses a CameraX `ImageAnalysis`
frame feed decoded by the same codec, connects directly to the encoded
address, and exchanges identities (`PairingExchange`) — the pairing token
alone proves "I actually scanned the code currently on screen," which is
all the trust pairing needs at this stage (no prior key exists yet to sign
against). Both devices end up with a `PairedDeviceEntity` row for the
other, and every later sync session's handshake authenticates against that
pinned key.

## Local sync — a race, not a persistent listener

`SyncRepositoryImpl.syncNow()` runs both roles concurrently with a shared
20-second timeout: it registers its own NSD service and accepts one
incoming connection (in case the peer finds it first) *while
simultaneously* discovering the peer via NSD and connecting out — whichever
completes a real connection first wins, the other is cancelled. This is a
deliberate tradeoff: WorkManager's execution model is short bursts, not an
always-on background service, so there's no persistent listener the other
device could rely on finding at an arbitrary moment. Two devices' periodic
(or manual) sync attempts need to *overlap* on the same Wi-Fi network for a
session to happen — see Known Limitations.

## Conflict resolution

| Case | Rule |
|---|---|
| Simple fields (name, price, status, …) | Newest `updatedAt` wins — ties (identical timestamp, the common no-op case) favor the local copy so nothing "changes" spuriously every sync |
| Collections (seasons, dress codes, tags, palette, materials, outfit slot composition) | Always merge (set/keyed union), independent of whether the parent row's scalar fields won or lost — union-only, not full CRDT remove-tombstones (see Known Limitations) |
| Photos | Newest version wins, same as any other field; never resent if the checksum already matches |
| Deleted items | Delete only if the local copy has *no* edit newer than the remote delete's timestamp — otherwise surfaced as one user-visible conflict card, never silently deleted or silently kept |

The one case deterministic rules can't settle safely — a row edited on one
device and deleted on the other since they last synced — creates exactly
one `SyncConflictEntity` row, surfaced on the Wardrobe Sync screen with a
plain-language summary and two buttons ("Keep this device's version" /
"Keep the other device's version"). Nothing is ever silently dropped.

## Image synchronization

Reuses Phase 5b's `ImageHasher` (SHA-256): before any `image_metadata` row
is applied, both devices exchange the checksums they already have and each
requests only what it's missing, so an identical photo is never resent.
Transfer is whole-file (base64-framed through the same encrypted channel),
staged by checksum (`ImageFileStore.syncStagingFile`) until the
corresponding metadata row tells `ImageMetadataSyncHandler` which local
garment/type directory to move it into — `filePath` itself is never sent
over the wire, since each device's `images/<garmentId>/...` layout is keyed
by its own local id.

## Offline behavior (Travel Mode)

Every mutation goes through the exact same repositories and DAOs regardless
of pairing state — there is no "offline queue" distinct from the ordinary
Room database, because the outbox (`sync_change_log`) already *is* that
queue. Wearing outfits, adding/editing garments, building looks, and
scheduling calendar entries while traveling on the phone alone all work
identically to the tablet, write real rows immediately, and simply
accumulate outbox entries that get sent the next time the two devices are
back on the same network — automatically, per the Preferences screen's
auto-sync toggle, with no user action required.

## Sync Status screen

Wardrobe Sync (`feature:settings`): connected device name, last sync time,
pending change count, storage used (real db-file + images-directory size),
Manual Sync, per-preference toggles (auto-sync/Wi-Fi-only/charging-only),
unresolved conflict cards, sync history, and Export/Restore Backup wired to
the existing `BackupRepository` (Phase 5a) — the first real UI this app has
for backup beyond the worker classes themselves.

## Home confirmation

`HomeViewModel` observes `SyncRepository.observeStatus().map { it.lastSyncAt }`,
skipping the value already present when Home first loads (so a sync that
finished before this screen opened doesn't retroactively show anything),
and surfaces the plain-language line "Wardrobe updated just now" for four
seconds whenever it actually changes — never a dialog, popup, or technical
message.

## Performance

Sync runs entirely off the main thread (`SyncWorker`, a `CoroutineWorker`);
`SyncRepositoryImpl`'s discovery race runs on `Dispatchers.IO`. The outbox
design means a session only ever transmits what actually changed since the
last successful sync, not the whole database. `SyncChangeLogDao.compact()`
bounds the outbox's size regardless of how many edits happened between
syncs. No device-measured throughput/latency numbers exist — no second
physical device or real Wi-Fi network exists in this development
environment (see Verification).

## Accessibility

Every new screen (Wardrobe Sync, Pairing, conflict cards) uses standard
Material3 components with real content descriptions (back buttons, the QR
image itself, the conflict card's semantics), scales with system font size
(no fixed-height text containers), and uses only text/switches/buttons —
no custom-drawn interactive elements requiring their own TalkBack wiring,
other than the QR image's content description and the camera preview
(which has no interactive target of its own beyond the permission button).

## Developer Panel — Sync Diagnostics

Connected device, pending uploads, bytes transferred (last session), last
successful/failed sync, conflicts resolved, queue size (in-flight history
rows with no `finishedAt` yet), and the `SyncWorker`'s current `WorkInfo`
state — read directly from `SyncRepository`, with no `core:ui` diagnostics
bridge needed (unlike Stats/Recommendations in earlier phases): sync state
is already durably persisted via `SyncHistoryEntity`/`PairedDeviceEntity`,
so there's nothing ephemeral to relay.

## Testing

Real, passing tests, not fabricated: `SessionCryptoTest` (ECDH agreement
symmetry, AES-GCM round-trip, tampered-ciphertext and wrong-AAD rejection),
`SyncHandshakeTest` (two simulated peers over piped streams derive the
identical session key; an unpinned peer is rejected), `PairingQrCodecTest`
(encode→decode round-trip via Robolectric's `Bitmap`; a QR-less frame
returns `null` rather than throwing), `EncryptedFrameTransportTest`
(round-trip, wrong-sequence rejection, byte-count tracking),
`Migration4To5Test` (syncId backfill is non-blank and distinct per row,
`updatedAt` backfilled, and a post-migration insert genuinely fires the
outbox trigger), `TagSyncHandlerTest`/`GarmentSyncHandlerTest` (newest-wins,
ignore-when-older, FK deferral when a referenced row hasn't arrived yet,
edit/delete conflict surfaced instead of silently deleting, collections
merge independent of whether scalar fields won), and Compose UI tests for
`WardrobeSyncScreen`'s status/conflict cards.

## Known limitations

- **No real two-device verification.** Every protocol/crypto/conflict-
  resolution component above is verified against a simulated peer (piped
  streams, in-memory byte arrays, two Robolectric-hosted handshake threads)
  — genuinely correct at the component level, but pairing/discovery/transfer
  between two *physical* Android devices over a real Wi-Fi network has never
  run, because no second device exists in this environment. This is the
  single largest gap between "compiles and passes tests" and "works." See
  the closing report for the honest answer to this phase's own quality bar.
- **Sync is a best-effort race, not a guaranteed rendezvous.** Two devices'
  periodic (or manual) sync windows must overlap for a session to happen at
  all — there's no persistent listener bridging the gap between attempts.
  In practice this converges quickly on a shared home Wi-Fi network with
  periodic background sync and/or both users tapping Manual Sync, but it is
  not instantaneous the way a server-mediated sync would be.
- **Collections merge by union only**, no per-entry tombstones. A tag/season/
  dress-code *removed* on one device can reappear if the other device still
  has it at the next sync — a real, low-stakes edge case for a personal
  wardrobe, not silently ignored but not solved with full CRDT semantics
  either.
- **Reference-data name collisions.** Two devices independently creating a
  brand/material/tag/occasion with the identical name before ever syncing
  may result in one of the two duplicate-named rows being silently skipped
  (an intentional `OnConflictStrategy.IGNORE` choice, safer than crashing a
  whole sync batch over one name clash) rather than merged into one.
- **A harmless echo.** Applying an incoming change is itself a local
  INSERT/UPDATE, which fires that row's own outbox trigger — the next sync
  briefly re-sends a change back to the device that just sent it. Wasteful,
  not incorrect (the peer's own LWW check makes it a no-op), and not solved
  with origin-tracking in this pass.
- **Trip-packed exclusion, occasion-implied dress code, and other
  Phase 6/7 heuristics** are unchanged by this phase and still carry their
  own previously-documented limitations (`TECHNICAL_DEBT.md` items 11–12).
- **No device-measured performance** for sync latency/throughput — no
  device or emulator, and no second device, exists in this environment.
- **Manual location heuristic**: `resolveLocalIpAddress()` picks the first
  non-loopback IPv4 address on any active interface, which can pick the
  wrong one on a device with an active VPN or multiple simultaneous
  networks.
- **Developer settings do not sync** — explicitly optional per the brief,
  and genuinely device-specific (diagnostics counters, recomposition
  tallies) rather than wardrobe data.
- **`ClosetPreferencesRepository`/`WeatherPreferencesRepository`/`StyleProfileRepository`
  are deliberately not synced** — closet sort/grid-density are legitimately
  per-device display preferences (a tablet and a phone may reasonably want
  different grid densities), and weather preferences are device-location-
  specific by nature (syncing them would actively break Travel Mode, since
  the phone's location differs from the tablet's while traveling).

## Future improvements

- Real two-device manual testing once a second physical device is
  available, and tuning the discovery-race timeout against real-world
  Wi-Fi association latency.
- Byte-range resumable image transfer (currently whole-file-per-session;
  correct but not bandwidth-optimal for a large photo over a slow network).
- Origin-tracking to suppress the harmless sync echo.
- Per-entry tombstones for collection removals if real usage shows the
  union-only merge's reappearing-tag behavior actually bothers anyone.
- A real Settings hub screen (still absent) that Wardrobe Sync and Weather
  Settings could both live under instead of being reached via ad-hoc
  top-bar actions on Recommendations.
- The Personalized Avatar / 2D outfit-preview-on-avatar system, deferred
  from this phase per the user's own scope decision — a separate phase,
  scoped on its own terms.

## Verification

Actually run, not assumed — `./gradlew clean build` across all 21 modules
(including the new `core:sync`) is **BUILD SUCCESSFUL**, confirmed on the
third iteration of a real fix-and-rerun loop (2,138 actionable tasks, zero
`FAILED` occurrences in the full log). Every new unit/Compose test passes;
lint, ktlint, and detekt are clean across every touched module. This was
not a one-shot clean build — three consecutive full runs surfaced real,
distinct failures that were fixed and re-verified before declaring green:

**Pass 1** (first full build after implementation) surfaced:
- `feature:closet:ktlintMainSourceSetCheck`/`detekt` — the Phase 8 edits to
  `DeveloperPanelViewModel.kt`/`HomeViewModel.kt` had never been run through
  `ktlintFormat`, and two composables (`DeveloperPanelDiagnosticsSections`,
  `HomeContent`) had grown past detekt's 60-line `LongMethod` threshold with
  the new Sync Diagnostics/confirmation-line additions.
- `core:database:testDebugUnitTest` — **a real, pre-existing-pattern
  regression**: `Migration1To2Test`/`Migration2To3Test`/`Migration3To4Test`
  all failed with `IllegalStateException` because `WardrobeDatabase.version`
  moved to 5 and none of the three tests' `addMigrations(...)` calls
  included the new `MIGRATION_4_5` — the exact "Room validates the full
  migration path to the class's *currently declared* version" trap
  `TECHNICAL_DEBT.md` items 9/11/12 already documented happening at every
  prior version bump, now recurring for a fourth time. Fixed by adding
  `MIGRATION_4_5` to all three tests' migration lists.

**Pass 2** (after fixing pass 1's findings) surfaced two *more* modules that
had never been run through `ktlintFormat`/`detekt` since their own Phase 8
edits:
- `feature:settings:detekt` — 11 `MaxLineLength` findings across
  `PairingScreen.kt`/`PairingViewModel.kt`/`WardrobeSyncScreen.kt`, plus
  `WardrobeSyncScreen`'s own composable growing past the 60-line
  `LongMethod` threshold once the preferences/backup/history sections were
  all added — fixed by `ktlintFormat` plus extracting a
  `WardrobeSyncContent` composable.
- `feature:outfits:detekt` — `RecommendationsScreen`'s `LongParameterList`
  once `onOpenWardrobeSync` became its fifth navigation callback — fixed
  with `@Suppress("LongParameterList")`, the same precedent `HomeScreen`
  already established for composables whose parameter list is entirely
  navigation callbacks, not real complexity.

**Pass 3**: genuinely clean — no findings, `BUILD SUCCESSFUL`.

The pattern across all three passes: every fix was to code this phase
itself introduced (new sync handlers, new screens, or migration tests that
needed the new migration added to their list) — nothing pre-existing from
Phases 1–7 broke. See "Errors and fixes" below for the fuller list,
including the critical `syncId=""` default-parameter regression caught
during implementation (before any of these three passes).

**What "verified" means here, honestly**: every protocol/crypto/conflict-
resolution/database component is exercised by a real, passing JUnit/
Robolectric test against a simulated peer — not a mock standing in for the
whole system, but also not a second physical device. See Known Limitations
for exactly where that line falls.
