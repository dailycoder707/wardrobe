package com.wardrobe.app.core.data.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The three on-disk locations a backup covers — bundled into one type so `export`/
 * `import` each take one parameter instead of three, and so a caller can't
 * accidentally swap the db/datastore/images arguments around. */
data class BackupPaths(
    val dbFile: File,
    val datastoreDir: File,
    val imagesDir: File,
)

private const val PROGRESS_START = 0f
private const val PROGRESS_AFTER_MANIFEST = 0.1f
private const val PROGRESS_AFTER_DB = 0.4f
private const val PROGRESS_AFTER_DATASTORE = 0.6f
private const val PROGRESS_COMPLETE = 1f

/**
 * The portable half of backup/restore — plain `java.io` against real files, no
 * `Context`, no WorkManager, no Android dependency at all. `BackupExportWorker`/
 * `BackupRestoreWorker` are the only callers in the running app; unit tests
 * (phase-5a-data-layer.md's testing strategy) call this directly against a JUnit
 * `TemporaryFolder`, exercising the exact same code path.
 */
object BackupFileOperations {
    const val SCHEMA_VERSION_KEY = "schemaVersion"
    const val CREATED_AT_KEY = "createdAt"
    const val APP_VERSION_KEY = "appVersionName"
    private const val MANIFEST_ENTRY = "manifest.properties"
    private const val DB_ENTRY = "wardrobe.db"
    private const val DATASTORE_PREFIX = "datastore/"
    private const val IMAGES_PREFIX = "images/"

    /**
     * Writes manifest → db → datastore → images → into [destination], in that order,
     * reporting coarse per-step progress via [onProgress] (0f..1f) — see
     * phase-5a-data-layer.md for why step-level, not byte-level, granularity.
     * [paths.dbFile] must already be checkpointed by the caller (Room's WAL mode
     * means the `.db` file alone isn't guaranteed consistent otherwise).
     */
    suspend fun export(
        destination: OutputStream,
        paths: BackupPaths,
        schemaVersion: Int,
        appVersionName: String,
        onProgress: suspend (Float) -> Unit,
    ) {
        ZipOutputStream(destination).use { zip ->
            onProgress(PROGRESS_START)
            writeManifest(zip, schemaVersion, appVersionName)
            onProgress(PROGRESS_AFTER_MANIFEST)

            if (paths.dbFile.exists()) writeFileEntry(zip, paths.dbFile, DB_ENTRY)
            onProgress(PROGRESS_AFTER_DB)

            if (paths.datastoreDir.exists()) writeDirEntries(zip, paths.datastoreDir, DATASTORE_PREFIX)
            onProgress(PROGRESS_AFTER_DATASTORE)

            if (paths.imagesDir.exists()) writeDirEntries(zip, paths.imagesDir, IMAGES_PREFIX)
            onProgress(PROGRESS_COMPLETE)
        }
    }

    /**
     * The reverse direction. [targetPaths]' three locations are replaced wholesale —
     * any existing contents are deleted first, matching the "restore replaces
     * everything" contract stated in the confirmation dialog copy
     * (`docs/design/microcopy-guide.md`). Returns the parsed manifest so the caller
     * can validate `schemaVersion` before trusting the restored data.
     */
    suspend fun import(
        source: InputStream,
        targetPaths: BackupPaths,
        onProgress: suspend (Float) -> Unit,
    ): Properties {
        val manifest = Properties()
        targetPaths.dbFile.parentFile?.mkdirs()
        targetPaths.datastoreDir.deleteRecursively()
        targetPaths.imagesDir.deleteRecursively()
        onProgress(PROGRESS_START)

        ZipInputStream(source).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                restoreEntry(zip, entry, targetPaths, manifest)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        onProgress(PROGRESS_COMPLETE)
        return manifest
    }

    private fun restoreEntry(
        zip: ZipInputStream,
        entry: ZipEntry,
        targetPaths: BackupPaths,
        manifest: Properties,
    ) {
        when {
            entry.name == MANIFEST_ENTRY -> {
                manifest.load(zip)
            }

            entry.name == DB_ENTRY -> {
                targetPaths.dbFile.outputStream().use { zip.copyTo(it) }
            }

            entry.name.startsWith(DATASTORE_PREFIX) && !entry.isDirectory -> {
                extractTo(zip, targetPaths.datastoreDir, entry.name.removePrefix(DATASTORE_PREFIX))
            }

            entry.name.startsWith(IMAGES_PREFIX) && !entry.isDirectory -> {
                extractTo(zip, targetPaths.imagesDir, entry.name.removePrefix(IMAGES_PREFIX))
            }
        }
    }

    private fun extractTo(
        zip: ZipInputStream,
        dir: File,
        relativePath: String,
    ) {
        val outFile = File(dir, relativePath)
        outFile.parentFile?.mkdirs()
        outFile.outputStream().use { zip.copyTo(it) }
    }

    private fun writeManifest(
        zip: ZipOutputStream,
        schemaVersion: Int,
        appVersionName: String,
    ) {
        val props =
            Properties().apply {
                setProperty(SCHEMA_VERSION_KEY, schemaVersion.toString())
                setProperty(CREATED_AT_KEY, System.currentTimeMillis().toString())
                setProperty(APP_VERSION_KEY, appVersionName)
            }
        zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
        props.store(zip, "Wardrobe backup manifest")
        zip.closeEntry()
    }

    private fun writeFileEntry(
        zip: ZipOutputStream,
        file: File,
        entryName: String,
    ) {
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun writeDirEntries(
        zip: ZipOutputStream,
        dir: File,
        prefix: String,
    ) {
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(dir).path.replace(File.separatorChar, '/')
            writeFileEntry(zip, file, "$prefix$relative")
        }
    }
}
