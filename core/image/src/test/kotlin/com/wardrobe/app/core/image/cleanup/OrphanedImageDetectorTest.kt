package com.wardrobe.app.core.image.cleanup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.temporal.ChronoUnit

class OrphanedImageDetectorTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `findOrphans returns only files with no matching referenced path`() {
        val referenced = tempFolder.newFile("referenced.jpg")
        val orphan = tempFolder.newFile("orphan.jpg")

        val result =
            OrphanedImageDetector.findOrphans(
                referencedPaths = setOf(referenced.path),
                filesOnDisk = listOf(referenced, orphan),
            )

        assertEquals(listOf(orphan), result)
    }

    @Test
    fun `findOrphans returns nothing when every file is referenced`() {
        val fileA = tempFolder.newFile("a.jpg")
        val fileB = tempFolder.newFile("b.jpg")

        val result =
            OrphanedImageDetector.findOrphans(
                referencedPaths = setOf(fileA.path, fileB.path),
                filesOnDisk = listOf(fileA, fileB),
            )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findStaleStagingDirs returns only directories older than the cutoff`() {
        val old = tempFolder.newFolder("old-staging")
        val recent = tempFolder.newFolder("recent-staging")
        val cutoffInThePast = Instant.now().minus(1, ChronoUnit.HOURS)
        old.setLastModified(cutoffInThePast.minusSeconds(60).toEpochMilli())
        recent.setLastModified(Instant.now().toEpochMilli())

        val result = OrphanedImageDetector.findStaleStagingDirs(listOf(old, recent), cutoffInThePast)

        assertEquals(listOf(old), result)
    }
}
