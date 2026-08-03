package com.wardrobe.app.core.model.styling

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
 */
data class ScoredOutfit(
    val outfit: Outfit,
    val score: Double,
    val explanation: String,
    val passedWeatherFilter: Boolean,
    val accessoryItems: List<AccessoryItemExplanation> = emptyList(),
    val jewelryItems: List<JewelryItemExplanation> = emptyList(),
)
