package com.wardrobe.app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SEED_TIMEOUT_MS = 5_000L

/** Every name this test asserts on, across both of [WardrobeDatabase.SeedCallback]'s
 * separate insert calls (top-level categories, then subcategories) — used to
 * detect when seeding has genuinely finished, not just when the first of the
 * two writes has landed. */
private val EXPECTED_SEEDED_NAMES =
    setOf(
        "Tops",
        "Bottoms",
        "Dresses",
        "Shoes",
        "Sandals",
        "Boots",
        "Sneakers",
        "Jewelry",
        "Watches",
        "Earrings",
        "Necklaces",
        "Bracelets",
        "Rings",
        "Belts",
        "Scarves",
        "Hair Accessories",
        "Sunglasses",
        "Other Accessories",
    )

/** Phase 6's default category seed — every one of the twenty item types the
 * styling engine needs to reason about should exist as a real, user-editable
 * [com.wardrobe.app.core.database.entity.CategoryEntity] row on first launch,
 * not require the user to create them all by hand first. */
@RunWith(RobolectricTestRunner::class)
class WardrobeDatabaseSeedTest {
    @Test
    fun `seeding on database creation adds default categories covering every item type`() =
        runBlocking {
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

            // SeedCallback.onCreate launches its inserts on Room's own real
            // background query executor, not this test's coroutine — there is
            // nothing here for a virtual-time dispatcher to advance through.
            // Waiting on the DAO's own invalidation-tracked Flow until every
            // name this test asserts on has actually landed is what makes this
            // deterministic regardless of how long Room's real executor takes
            // under system load, without an arbitrary sleep/poll.
            val categoryNames =
                withTimeout(SEED_TIMEOUT_MS) {
                    db
                        .categoryDao()
                        .observeAll()
                        .first { rows -> EXPECTED_SEEDED_NAMES.all { name -> rows.any { it.name == name } } }
                        .map { it.name }
                        .toSet()
                }

            // `SeedCallback.onCreate` seeds materials/fabrics in the same
            // coroutine, sequentially after categories — waiting only on
            // categories above and then closing the database can race the
            // still-in-flight materials/fabrics insert (a real
            // `SQLException: connection is closed` this test caught).
            withTimeout(SEED_TIMEOUT_MS) {
                db.materialDao().observeAll().first { it.isNotEmpty() }
                db.fabricDao().observeAll().first { it.isNotEmpty() }
            }

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
