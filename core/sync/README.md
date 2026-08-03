# :core:sync

New in Phase 8. The protocol/crypto/pairing/discovery machinery multi-device
sync needs — a sibling to `core:network`, not a replacement for anything.
No Hilt (matches `core:network`'s posture): everything here is a plain class
or object, wired up via `core:data`'s `SyncModule`/`RepositoryModule`
instead. No cloud, no accounts — every byte this module sends goes over a
raw `Socket` on the local Wi-Fi network the two paired devices already
share.

## Packages
| Package | Holds |
|---|---|
| `crypto/` | `DeviceIdentityKeyStore`/`AndroidKeystoreDeviceIdentity` (`AndroidKeyStore`-backed `PURPOSE_SIGN` identity key — signing, not ECDH, since `PURPOSE_AGREE_KEY` needs API 31+ and minSdk is 26), `publicKeyFingerprint()`/`verifySignature()` (pure, unit-tested directly), `SessionCrypto` (ephemeral EC keypair generation, ECDH + HKDF-Expand session-key derivation, AES-256-GCM encrypt/decrypt) |
| `pairing/` | `PairingOfferPayload` (the QR's serialized contents — deviceId, display name, identity public key, one-time pairing token, host address/port), `PairingQrCodec` (ZXing encode/decode — plain `com.google.zxing:core`, no Google Play Services/ML Kit, same reasoning Phase 7's `DeviceLocationSource` used for avoiding Fused Location), `PairingExchange` (the network round-trip that turns a scanned QR into two devices each holding the other's pinned identity key) |
| `discovery/` | `DeviceDiscoveryService` — thin `NsdManager` wrapper, `registerService(port): Flow<Unit>`/`discoverDevices(): Flow<DiscoveredDevice>` |
| `transport/` | `SyncHandshake` (mutual identity-signature verification + ECDH session-key agreement), `EncryptedFrameTransport` (AES-256-GCM framing with the frame's own sequence number as AAD, so a replayed/reordered frame fails the auth tag rather than silently decrypting), `SyncSession` (the change-batch exchange layered on top of the encrypted transport) |
| `protocol/` | `SyncMessages.kt` — every wire message (`SyncFrame`/`SyncMessageKind`, handshake bodies, `EntityChangeDto`/`ChangeBatchBody`/`ChangeAckBody`, `ImageManifestBody`/`ImageRequestBody`/`ImageDataBody`) |

## Why this is a separate module, not part of `core:data`

Everything here is pure protocol/crypto/networking logic with zero
dependency on Room, DataStore, or any repository — `core:data`'s
`sync/` package (the `SyncEntityHandler`s, `SyncEngine`, repository
implementations) is what *uses* this module to actually read/write the
database. Keeping the split means the handshake/crypto/framing layer can be
unit-tested in complete isolation from Room (see `SessionCryptoTest`/
`SyncHandshakeTest`/`EncryptedFrameTransportTest`), and — same rationale as
`core:network` — a module that could, in principle, be reused by a future
non-Android target without dragging Room along.

## Protocol shape

Two phases over one TCP socket, both described in full in
`phase-8-multi-device-sync.md`'s "Protocol" section:

1. **Handshake** (plaintext-framed via `PlainJsonFrameIo`, but never
   unauthenticated) — each side signs a fresh ephemeral EC keypair with its
   long-term identity key; the receiver verifies that signature against the
   peer's *already-pinned* identity key (from pairing) before trusting the
   ephemeral key, then both sides run ECDH on the ephemeral keys to derive
   an AES-256 session key neither ever transmitted. An unpinned peer is
   rejected outright (`UnknownPeerException`).
2. **Data exchange**, `EncryptedFrameTransport`-framed from here on: an
   image-transfer phase (checksum manifest exchange, then whole-file
   transfer of only what's missing), then the change-batch exchange.

## Testing

`SessionCryptoTest` (ECDH agreement symmetry, AES-GCM round-trip, tampered-
ciphertext/wrong-AAD rejection), `SyncHandshakeTest` (two simulated peers
over piped streams derive the identical session key; an unpinned peer is
rejected — real threads, real `PipedInputStream`/`PipedOutputStream`, not
mocked crypto), `PairingQrCodecTest` (Robolectric-hosted encode→decode
round-trip via a real `Bitmap`), `EncryptedFrameTransportTest` (round-trip,
wrong-sequence-number rejection, byte-count tracking). All passing, all
against simulated peers — see `TECHNICAL_DEBT.md` item 13 for why real
two-device pairing/discovery has never run in this environment.

## Known gaps, recorded rather than hidden

- No real device has ever scanned a real QR code produced by this module,
  or discovered a real peer via `NsdManager` on a real network — see
  `phase-8-multi-device-sync.md`'s Known Limitations and `TECHNICAL_DEBT.md`
  item 13.
- `DeviceDiscoveryService`'s NSD-based discovery is a best-effort race
  (`core:data`'s `SyncRepositoryImpl` runs both responder and initiator
  concurrently with a timeout), not a persistent listener — see that
  module's README for why.
