package com.wardrobe.app.core.data.backup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Exercises the portable export/import logic directly against real files — no
 * Android, no WorkManager, matching phase-5a-data-layer.md's testing strategy: an
 * export→restore round trip should reproduce the original data byte-for-byte.
 */
class BackupFileOperationsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `export then import reproduces the original db, datastore, and image files`() =
        runTest {
            val sourceDb = tempFolder.newFile("wardrobe.db").apply { writeText("fake-db-contents") }
            val sourceDatastoreDir =
                tempFolder.newFolder("datastore").apply {
                    File(this, "wardrobe_preferences.preferences_pb").writeText("fake-prefs")
                }
            val sourceImagesDir =
                tempFolder.newFolder("images").apply {
                    File(this, "1").mkdirs()
                    File(this, "1/original.jpg").writeBytes(byteArrayOf(1, 2, 3, 4))
                }
            val zipFile = tempFolder.newFile("out.wardrobebackup")

            val progressValues = mutableListOf<Float>()
            zipFile.outputStream().use { out ->
                BackupFileOperations.export(
                    destination = out,
                    paths = BackupPaths(sourceDb, sourceDatastoreDir, sourceImagesDir),
                    schemaVersion = 1,
                    appVersionName = "0.1.0-test",
                    onProgress = { progressValues.add(it) },
                )
            }

            assertTrue("progress should reach 1.0", progressValues.last() == 1f)
            assertTrue("backup file should be non-empty", zipFile.length() > 0)

            val restoredDb = File(tempFolder.root, "restored/wardrobe.db")
            val restoredDatastoreDir = File(tempFolder.root, "restored/datastore")
            val restoredImagesDir = File(tempFolder.root, "restored/images")

            val manifest =
                zipFile.inputStream().use { input ->
                    BackupFileOperations.import(
                        source = input,
                        targetPaths = BackupPaths(restoredDb, restoredDatastoreDir, restoredImagesDir),
                        onProgress = {},
                    )
                }

            assertEquals("1", manifest.getProperty(BackupFileOperations.SCHEMA_VERSION_KEY))
            assertEquals("0.1.0-test", manifest.getProperty(BackupFileOperations.APP_VERSION_KEY))
            assertEquals("fake-db-contents", restoredDb.readText())
            assertEquals("fake-prefs", File(restoredDatastoreDir, "wardrobe_preferences.preferences_pb").readText())
            assertTrue(
                File(restoredImagesDir, "1/original.jpg").readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)),
            )
        }

    @Test
    fun `restore replaces existing contents rather than merging with them`() =
        runTest {
            val sourceDb = tempFolder.newFile("wardrobe.db").apply { writeText("new-contents") }
            val emptyDatastoreDir = tempFolder.newFolder("empty-datastore")
            val emptyImagesDir = tempFolder.newFolder("empty-images")
            val zipFile = tempFolder.newFile("out2.wardrobebackup")

            zipFile.outputStream().use { out ->
                BackupFileOperations.export(
                    destination = out,
                    paths = BackupPaths(sourceDb, emptyDatastoreDir, emptyImagesDir),
                    schemaVersion = 1,
                    appVersionName = "0.1.0-test",
                    onProgress = {},
                )
            }

            val targetDb = tempFolder.newFile("existing.db").apply { writeText("stale-contents-to-be-replaced") }
            val targetDatastoreDir =
                tempFolder.newFolder("target-datastore").apply {
                    File(this, "leftover.preferences_pb").writeText("should be deleted")
                }
            val targetImagesDir = tempFolder.newFolder("target-images")

            zipFile.inputStream().use { input ->
                BackupFileOperations.import(
                    source = input,
                    targetPaths = BackupPaths(targetDb, targetDatastoreDir, targetImagesDir),
                    onProgress = {},
                )
            }

            assertEquals("new-contents", targetDb.readText())
            assertTrue(
                "restore must delete files not present in the backup, not merge with them",
                !File(targetDatastoreDir, "leftover.preferences_pb").exists(),
            )
        }
}
