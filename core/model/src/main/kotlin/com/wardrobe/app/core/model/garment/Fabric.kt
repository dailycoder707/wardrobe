package com.wardrobe.app.core.model.garment

import com.wardrobe.app.core.model.common.FabricId

/** A weave/construction reference (Denim, Jersey, Twill, Flannel...) —
 * deliberately distinct from [Material] (fiber content: Cotton, Wool,
 * Polyester...). Two garments can share a material and differ in fabric,
 * or vice versa, so this is its own reference-data table rather than a
 * field on [Material]. */
data class Fabric(
    val id: FabricId,
    val name: String,
)

/** One fabric component of a garment — `garment_fabrics`, mirroring
 * [MaterialComposition]'s shape exactly. */
data class FabricComposition(
    val fabric: Fabric,
    val percentage: Int?,
)
