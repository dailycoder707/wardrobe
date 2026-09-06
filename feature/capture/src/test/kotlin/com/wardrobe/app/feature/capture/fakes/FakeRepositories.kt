package com.wardrobe.app.feature.capture.fakes

import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.FabricRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.MaterialRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.TagRepository
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.FabricId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.MaterialId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.Fabric
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.Tag
import com.wardrobe.app.core.model.outfit.Occasion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeGarmentRepository : GarmentRepository {
    private val garmentsFlow = MutableStateFlow<List<Garment>>(emptyList())
    val savedGarments: List<Garment> get() = garmentsFlow.value

    override fun observeGarments(filter: GarmentFilter): Flow<List<Garment>> = garmentsFlow.asStateFlow()

    override fun observeGarment(id: GarmentId): Flow<Garment?> =
        garmentsFlow.map { garments -> garments.firstOrNull { it.id == id } }

    override suspend fun getGarment(id: GarmentId): Garment? = garmentsFlow.value.firstOrNull { it.id == id }

    override suspend fun saveGarment(garment: Garment): GarmentId {
        val id = GarmentId(garmentsFlow.value.size + 1L)
        garmentsFlow.value = garmentsFlow.value + garment.copy(id = id)
        return id
    }

    override suspend fun updateGarment(garment: Garment) {
        garmentsFlow.value = garmentsFlow.value.map { if (it.id == garment.id) garment else it }
    }

    override suspend fun setStatus(
        id: GarmentId,
        status: GarmentStatus,
    ) = Unit

    override suspend fun setFavorite(
        id: GarmentId,
        isFavorite: Boolean,
    ) = Unit

    override suspend fun setInLaundry(
        id: GarmentId,
        isInLaundry: Boolean,
    ) = Unit

    override suspend fun deleteGarment(id: GarmentId) {
        garmentsFlow.value = garmentsFlow.value.filterNot { it.id == id }
    }

    var duplicatesToReturn: List<Garment> = emptyList()

    override suspend fun findPotentialDuplicates(
        categoryId: CategoryId,
        colorId: ColorId?,
        excludeGarmentId: GarmentId?,
    ): List<Garment> = duplicatesToReturn
}

class FakeCategoryRepository(
    initial: List<Category> = emptyList(),
) : CategoryRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Category>> = flow.asStateFlow()

    override suspend fun create(
        name: String,
        parentId: CategoryId?,
    ): CategoryId = throw UnsupportedOperationException("not needed for tests")

    override suspend fun delete(id: CategoryId) = Unit
}

class FakeBrandRepository(
    initial: List<Brand> = emptyList(),
) : BrandRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Brand>> = flow.asStateFlow()

    override suspend fun create(
        name: String,
        logoUri: String?,
    ): BrandId = throw UnsupportedOperationException("not needed for tests")
}

class FakeColorRepository(
    initial: List<Color> = emptyList(),
) : ColorRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Color>> = flow.asStateFlow()

    override suspend fun create(
        name: String,
        hexValue: String,
    ): ColorId = throw UnsupportedOperationException("not needed for tests")
}

class FakeMaterialRepository(
    initial: List<Material> = emptyList(),
) : MaterialRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Material>> = flow.asStateFlow()

    override suspend fun create(name: String): MaterialId = throw UnsupportedOperationException("not needed for tests")
}

class FakeFabricRepository(
    initial: List<Fabric> = emptyList(),
) : FabricRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Fabric>> = flow.asStateFlow()

    override suspend fun create(name: String): FabricId = throw UnsupportedOperationException("not needed for tests")
}

class FakeOccasionRepository(
    initial: List<Occasion> = emptyList(),
) : OccasionRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Occasion>> = flow.asStateFlow()

    override suspend fun create(name: String): OccasionId = throw UnsupportedOperationException("not needed for tests")
}

class FakeTagRepository(
    initial: List<Tag> = emptyList(),
) : TagRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Tag>> = flow.asStateFlow()

    override suspend fun create(name: String): TagId = throw UnsupportedOperationException("not needed for tests")

    override suspend fun delete(id: TagId) = Unit
}
