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
    fun `findOrphans returns only old files with no matching referenced path`() {
        val referenced = tempFolder.newFile("referenced.jpg")
        val orphan = tempFolder.newFile("orphan.jpg")
        val oldCutoff = Instant.now().plusSeconds(60)

        val result =
            OrphanedImageDetector.findOrphans(
                referencedPaths = setOf(referenced.path),
                filesOnDisk = listOf(referenced, orphan),
                olderThan = oldCutoff,
            )

        assertEquals(listOf(orphan), result)
    }

    @Test
    fun `findOrphans returns nothing when every file is referenced`() {
        val fileA = tempFolder.newFile("a.jpg")
        val fileB = tempFolder.newFile("b.jpg")
        val oldCutoff = Instant.now().plusSeconds(60)

        val result =
            OrphanedImageDetector.findOrphans(
                referencedPaths = setOf(fileA.path, fileB.path),
                filesOnDisk = listOf(fileA, fileB),
                olderThan = oldCutoff,
            )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findOrphans never deletes a just-written unreferenced file, only ones older than the cutoff`() {
        // Regression test for RC2's race finding: ImageRepositoryImpl.commitStagedImage
        // moves a garment's files into place before inserting its image_metadata
        // row, so a file can briefly exist on disk with no row yet. A cutoff in
        // the past (not the future, unlike the other tests here) simulates that
        // real narrow window — the file was just written, so its mtime is after
        // the cutoff, and it must survive this sweep.
        val justWritten = tempFolder.newFile("just-committed.jpg")
        val trueOrphan = tempFolder.newFile("long-abandoned.jpg")
        trueOrphan.setLastModified(Instant.now().minusSeconds(3600).toEpochMilli())
        val cutoffInThePast = Instant.now().minusSeconds(60)

        val result =
            OrphanedImageDetector.findOrphans(
                referencedPaths = emptySet(),
                filesOnDisk = listOf(justWritten, trueOrphan),
                olderThan = cutoffInThePast,
            )

        assertEquals(listOf(trueOrphan), result)
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
