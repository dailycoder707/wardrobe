package com.wardrobe.app.core.model.styling

import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.outfit.Outfit

/** One accessory item the engine recommends wearing alongside the outfit, with
 * its own one-line explanation — see [ScoredOutfit.accessoryItems]'s KDoc for
 * why this rides alongside the outfit rather than inside it. */
data class AccessoryItemExplanation(
    val category: AccessoryCategory,
    val garmentId: GarmentId,
    val explanation: String,
)

/** The jewelry equivalent of [AccessoryItemExplanation]. */
data class JewelryItemExplanation(
    val category: JewelryCategory,
    val garmentId: GarmentId,
    val explanation: String,
)

/**
 * An outfit suggestion with its score and a human-readable explanation of *why* it was
 * chosen — Constitution: no black-box suggestions. [passedWeatherFilter] records that
 * the hard weather filter (phase-1-architecture.md, "Quality bar for the styling
 * engine") ran before scoring; an outfit that fails it is never constructed as a
 * `ScoredOutfit` in the first place, but the flag stays here for Phase 8's adversarial
 * tests to assert against.
 *
 * [accessoryItems]/[jewelryItems] (Phase 9) are every accessory/jewelry item the
 * engine recommends wearing *together*, beyond the single item persisted at
 * [outfit]'s own `ACCESSORIES`/`JEWELRY` slot. `outfit_garments`' primary key is
 * `(outfitId, layerSlot)` — one garment per slot, the same constraint the Phase 5d
 * manual Outfit Builder lives under — so a full multi-item look (a necklace *and*
 * a bracelet *and* a ring) can't be persisted as one `Outfit` without a schema
 * change this phase deliberately doesn't make. These two fields are presentation-
 * only, rendered as "Also consider wearing" chips, never written to the database;
 * both default to empty so every pre-Phase-9 construction site keeps compiling.
 *
 * [provenance]/[aiConfidence] are `null` for every rule-engine-generated
 * outfit (the only source until Add-to-Wardrobe v2's Cloud Outfit Styling
 * capability, M12) — a cloud-suggested outfit that survives
 * [validateCloudOutfit]'s validation carries a real [AiResultProvenance] and
 * its provider's genuine confidence estimate instead, so the Review UI can
 * show "AI Styled" with its provider/confidence/reasoning. Never fabricated:
 * a rule-engine outfit's `null` here is the honest "no cloud provenance to
 * show," not an omitted value. Kept separate from [score] (the rule
 * engine's own arbitrary-scale ranking number, always present) since a
 * provider's 0..1 confidence is a different, cloud-only concept.
 */
data class ScoredOutfit(
    val outfit: Outfit,
    val score: Double,
    val explanation: String,
    val passedWeatherFilter: Boolean,
    val accessoryItems: List<AccessoryItemExplanation> = emptyList(),
    val jewelryItems: List<JewelryItemExplanation> = emptyList(),
    val provenance: AiResultProvenance? = null,
    val aiConfidence: Float? = null,
)
