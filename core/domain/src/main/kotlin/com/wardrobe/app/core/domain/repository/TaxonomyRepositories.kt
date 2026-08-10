package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.FabricId
import com.wardrobe.app.core.model.common.MaterialId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.Fabric
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.Tag
import com.wardrobe.app.core.model.outfit.Occasion
import kotlinx.coroutines.flow.Flow

/**
 * The reference-data repositories (`Category`, `Color`, `Material`, `Brand`, `Tag`,
 * `Occasion`) grouped in one file — each is a handful of methods over a small,
 * mostly-user-extensible table (phase-3-persistence.md), not worth a full file each.
 */

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>

    suspend fun create(
        name: String,
        parentId: CategoryId?,
    ): CategoryId

    suspend fun delete(id: CategoryId)
}

interface ColorRepository {
    fun observeAll(): Flow<List<Color>>

    suspend fun create(
        name: String,
        hexValue: String,
    ): ColorId
}

interface MaterialRepository {
    fun observeAll(): Flow<List<Material>>

    suspend fun create(name: String): MaterialId
}

/** Weave/construction reference data — deliberately distinct from
 * [MaterialRepository] (fiber content), see [Fabric]'s KDoc. */
interface FabricRepository {
    fun observeAll(): Flow<List<Fabric>>

    suspend fun create(name: String): FabricId
}

interface BrandRepository {
    fun observeAll(): Flow<List<Brand>>

    suspend fun create(
        name: String,
        logoUri: String?,
    ): BrandId
}

interface TagRepository {
    fun observeAll(): Flow<List<Tag>>

    suspend fun create(name: String): TagId

    suspend fun delete(id: TagId)
}

interface OccasionRepository {
    fun observeAll(): Flow<List<Occasion>>

    suspend fun create(name: String): OccasionId
}
