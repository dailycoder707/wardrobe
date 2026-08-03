package com.wardrobe.app.feature.stats.common

import androidx.compose.runtime.Immutable

/** The one shared row shape every insight list in this module renders — a
 * garment or an outfit, always with a short metric string explaining *why*
 * it's in this particular list ("Worn 12 times," "143 days ago," "₹42/wear").
 * [isOutfit] picks the navigation target ([com.wardrobe.app.feature.stats.navigation]
 * doesn't own that decision — the screen does, since Garment Detail and Outfit
 * Detail live in two different feature modules this one can't depend on). */
@Immutable
data class InsightItemUiModel(
    val id: Long,
    val isOutfit: Boolean,
    val thumbnailPath: String?,
    val title: String,
    val metric: String,
)
