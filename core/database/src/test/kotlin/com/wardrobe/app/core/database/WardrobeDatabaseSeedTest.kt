package com.wardrobe.app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Phase 6's default category seed — every one of the twenty item types the
 * styling engine needs to reason about should exist as a real, user-editable
 * [com.wardrobe.app.core.database.entity.CategoryEntity] row on first launch,
 * not require the user to create them all by hand first. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WardrobeDatabaseSeedTest {
    @Test
    fun `seeding on database creation adds default categories covering every item type`() =
        runTest(UnconfinedTestDispatcher()) {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            lateinit var db: WardrobeDatabase
            db =
                Room
                    .inMemoryDatabaseBuilder(context, WardrobeDatabase::class.java)
                    .allowMainThreadQueries()
                    .addCallback(WardrobeDatabase.SeedCallback(this) { db })
                    .build()

            // Room only invokes onCreate on first real access — force it.
            db.openHelper.writableDatabase
            advanceUntilIdle()

            val categoryNames =
                db
                    .categoryDao()
                    .observeAll()
                    .first()
                    .map { it.name }
                    .toSet()

            assertTrue(
                "expected top-level clothing categories",
                categoryNames.containsAll(listOf("Tops", "Bottoms", "Dresses")),
            )
            assertTrue(
                "expected footwear categories",
                categoryNames.containsAll(listOf("Shoes", "Sandals", "Boots", "Sneakers")),
            )
            assertTrue(
                "expected jewelry categories",
                categoryNames.containsAll(listOf("Jewelry", "Watches", "Earrings", "Necklaces", "Bracelets", "Rings")),
            )
            assertTrue(
                "expected small accessory categories",
                categoryNames.containsAll(
                    listOf("Belts", "Scarves", "Hair Accessories", "Sunglasses", "Other Accessories"),
                ),
            )

            db.close()
        }
}
