package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.data.mapper.toDomain
import com.wardrobe.app.core.database.dao.BrandDao
import com.wardrobe.app.core.database.dao.CategoryDao
import com.wardrobe.app.core.database.dao.ColorDao
import com.wardrobe.app.core.database.dao.FabricDao
import com.wardrobe.app.core.database.dao.MaterialDao
import com.wardrobe.app.core.database.dao.OccasionDao
import com.wardrobe.app.core.database.dao.TagDao
import com.wardrobe.app.core.database.entity.BrandEntity
import com.wardrobe.app.core.database.entity.CategoryEntity
import com.wardrobe.app.core.database.entity.ColorEntity
import com.wardrobe.app.core.database.entity.FabricEntity
import com.wardrobe.app.core.database.entity.MaterialEntity
import com.wardrobe.app.core.database.entity.OccasionEntity
import com.wardrobe.app.core.database.entity.TagEntity
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.FabricRepository
import com.wardrobe.app.core.domain.repository.MaterialRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.TagRepository
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
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepositoryImpl
    @Inject
    constructor(
        private val dao: CategoryDao,
    ) : CategoryRepository {
        override fun observeAll(): Flow<List<Category>> = dao.observeAll().map { it.map(CategoryEntity::toDomain) }

        override suspend fun create(
            name: String,
            parentId: CategoryId?,
        ): CategoryId {
            val level =
                if (parentId == null) {
                    com.wardrobe.app.core.model.garment.CategoryLevel.TOP
                } else {
                    com.wardrobe.app.core.model.garment.CategoryLevel.SUB
                }
            val id =
                dao.insert(
                    CategoryEntity(
                        name = name,
                        parentId = parentId?.value,
                        level = level,
                        syncId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            return CategoryId(id)
        }

        override suspend fun delete(id: CategoryId) = dao.deleteById(id.value)
    }

class ColorRepositoryImpl
    @Inject
    constructor(
        private val dao: ColorDao,
    ) : ColorRepository {
        override fun observeAll(): Flow<List<Color>> = dao.observeAll().map { it.map(ColorEntity::toDomain) }

        override suspend fun create(
            name: String,
            hexValue: String,
        ): ColorId =
            ColorId(
                dao.insert(
                    ColorEntity(
                        name = name,
                        hexValue = hexValue,
                        syncId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                ),
            )
    }

class MaterialRepositoryImpl
    @Inject
    constructor(
        private val dao: MaterialDao,
    ) : MaterialRepository {
        override fun observeAll(): Flow<List<Material>> = dao.observeAll().map { it.map(MaterialEntity::toDomain) }

        override suspend fun create(name: String): MaterialId =
            MaterialId(
                dao.insert(
                    MaterialEntity(
                        name = name,
                        syncId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                ),
            )
    }

class FabricRepositoryImpl
    @Inject
    constructor(
        private val dao: FabricDao,
    ) : FabricRepository {
        override fun observeAll(): Flow<List<Fabric>> = dao.observeAll().map { it.map(FabricEntity::toDomain) }

        override suspend fun create(name: String): FabricId =
            FabricId(
                dao.insert(
                    FabricEntity(
                        name = name,
                        syncId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                ),
            )
    }

class BrandRepositoryImpl
    @Inject
    constructor(
        private val dao: BrandDao,
    ) : BrandRepository {
        override fun observeAll(): Flow<List<Brand>> = dao.observeAll().map { it.map(BrandEntity::toDomain) }

        override suspend fun create(
            name: String,
            logoUri: String?,
        ): BrandId =
            BrandId(
                dao.insert(
                    BrandEntity(
                        name = name,
                        logoUri = logoUri,
                        syncId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                ),
            )
    }

class TagRepositoryImpl
    @Inject
    constructor(
        private val dao: TagDao,
    ) : TagRepository {
        override fun observeAll(): Flow<List<Tag>> = dao.observeAll().map { it.map(TagEntity::toDomain) }

        override suspend fun create(name: String): TagId =
            TagId(
                dao.insert(
                    TagEntity(
                        name = name,
                        syncId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                ),
            )

        override suspend fun delete(id: TagId) = dao.deleteById(id.value)
    }

class OccasionRepositoryImpl
    @Inject
    constructor(
        private val dao: OccasionDao,
    ) : OccasionRepository {
        override fun observeAll(): Flow<List<Occasion>> = dao.observeAll().map { it.map(OccasionEntity::toDomain) }

        override suspend fun create(name: String): OccasionId =
            OccasionId(
                dao.insert(
                    OccasionEntity(
                        name = name,
                        syncId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                ),
            )
    }
