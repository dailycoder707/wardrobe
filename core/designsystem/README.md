# :core:designsystem

Material3 theming, now carrying the real Atelier Light/Night design tokens from
`docs/design/phase-4-design-system.md`, wired up in Phase 5c.

## Packages
| Package | Holds |
|---|---|
| `theme/` | `Theme.kt` (`WardrobeTheme`), `Color.kt` (Atelier Light/Night palettes + `WardrobeExtendedColors`), `Type.kt` (real type scale), `WardrobeRadius.kt`, `WardrobeElevation.kt`, `WardrobeMotion.kt` |

## Status
- `WardrobeTheme` builds the real Atelier Light/Night `ColorScheme`s, plus
  `WardrobeTheme.extendedColors` (accent/success/warning/textSecondary — slots
  M3's own `ColorScheme` has no room for) via a `CompositionLocal`.
- `WardrobeRadius`/`WardrobeElevation`/`WardrobeMotion` (renamed from
  `Shape`/`Elevation`/`Motion` to satisfy detekt's `MatchingDeclarationName`)
  hold the real corner-radius, elevation, and animation-duration/easing tokens
  screens use directly rather than through M3's generic `Shapes()`/no-op
  elevation defaults.
- **Known gap**: `FrauncesFamily`/`InterFamily` in `Type.kt` fall back to
  `FontFamily.Serif`/`FontFamily.SansSerif` — no bundled `.ttf` files, since
  this environment can't fetch Google Fonts binaries. Tracked in
  `TECHNICAL_DEBT.md` item 8. `wardrobeShadow()` similarly approximates the
  design doc's independent y-offset/blur/opacity elevation spec onto Compose's
  single-value `Modifier.shadow`.
