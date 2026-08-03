package com.wardrobe.app.core.testing.rule

import android.content.Context
import androidx.room.Room
import com.wardrobe.app.core.database.WardrobeDatabase

/**
 * An in-memory `WardrobeDatabase` for repository tests (Phase 5a) — real Room, real
 * SQLite (via Robolectric's native shadow in the `test` source set), no fakes. A
 * repository's whole job is translating between two real schemas; a mocked DAO would
 * let a subtly wrong query slip past unnoticed, see phase-5a-data-layer.md.
 * `allowMainThreadQueries` is test-only — production code never does this.
 */
fun createInMemoryWardrobeDatabase(context: Context): WardrobeDatabase =
    Room
        .inMemoryDatabaseBuilder(context, WardrobeDatabase::class.java)
        .allowMainThreadQueries()
        .build()
