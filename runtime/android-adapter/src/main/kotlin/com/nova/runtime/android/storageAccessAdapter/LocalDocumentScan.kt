package com.nova.runtime.android.storageAccessAdapter

import android.content.Context
import android.os.Build
import android.os.Environment
import com.nova.runtime.storage.search.DownloadItem
import java.io.File

/**
 * Scans device storage for indexable documents/images.
 * Always attempts the public Documents folder (best-effort); expands further when
 * All files access is granted.
 */
internal object LocalDocumentScan {
    private val DOCUMENT_EXTENSIONS = setOf(
        "pdf", "txt", "md", "csv", "json", "doc", "docx", "rtf", "html", "htm", "log", "xml",
    )
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

    private val PRIORITY_NAME_HINTS = listOf(
        "timetable", "time-table", "schedule", "menu", "mess", "notes", "syllabus", "prospectus",
    )

    fun scan(context: Context, limit: Int, offset: Int): List<DownloadItem> {
        val roots = mutableListOf<File>()
        // App-owned
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { roots += it }
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { roots += it }
        context.getExternalFilesDir(null)?.let { roots += it }
        context.filesDir.resolve("Documents").let { roots += it }

        // Public Documents — always try (not Downloads-only). Readable when All files
        // access is granted; best-effort otherwise on some OEMs.
        roots += documentsRoots()

        val hasAllFiles =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
        if (hasAllFiles) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?.let { roots += it }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                ?.let { roots += it }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                ?.let { roots += it }
            val ext = Environment.getExternalStorageDirectory()
            roots += File(ext, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents")
            roots += File(ext, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images")
            roots += File(ext, "WhatsApp/Media/WhatsApp Documents")
            roots += File(ext, "Download")
            roots += File(ext, "Downloads")
            // Broad tree under external storage (depth-capped below).
            roots += ext
        }

        val maxDepth = if (hasAllFiles) 8 else 5
        val files = roots
            .distinctBy { it.absolutePath }
            .filter { it.exists() && it.canRead() }
            .flatMap { root ->
                runCatching {
                    root.walkTopDown()
                        .maxDepth(maxDepth)
                        .onEnter { dir ->
                            val name = dir.name.lowercase()
                            name !in SKIP_DIR_NAMES && !name.startsWith(".")
                        }
                        .filter { it.isFile && shouldIndex(it) }
                        .take(MAX_WALK_FILES)
                        .toList()
                }.getOrDefault(emptyList())
            }
            .distinctBy { it.absolutePath }
            .sortedWith(
                compareByDescending<File> { folderBoost(it) }
                    .thenByDescending { priorityBoost(it) }
                    .thenByDescending { it.lastModified() },
            )

        return files
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceAtLeast(1))
            .mapIndexed { index, file ->
                DownloadItem(
                    downloadId = stableId(file.absolutePath, index + offset),
                    uri = file.toURI().toString(),
                    displayName = file.name,
                    mimeType = mimeFor(file),
                    dateAdded = (file.lastModified() / 1000L).coerceAtLeast(0L),
                    size = file.length(),
                )
            }
    }

    /** Public / OEM Documents directories (always attempted). */
    private fun documentsRoots(): List<File> {
        val roots = mutableListOf<File>()
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            ?.let { roots += it }
        val ext = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        if (ext != null) {
            // Common OEM / user folder names for documents.
            roots += File(ext, "Documents")
            roots += File(ext, "Document")
            roots += File(ext, "My Documents")
            roots += File(ext, "MyFiles/Documents")
            roots += File(ext, "Documents/PDF")
            roots += File(ext, "Documents/Docs")
        }
        return roots
    }

    private fun shouldIndex(file: File): Boolean {
        val ext = file.extension.lowercase()
        if (ext in DOCUMENT_EXTENSIONS) return true
        if (ext !in IMAGE_EXTENSIONS) return false
        val path = file.absolutePath.lowercase()
        val name = file.name.lowercase()
        val inUserFolder =
            path.contains("/download") ||
                path.contains("/documents") ||
                path.contains("/document/") ||
                path.contains("/dcim") ||
                path.contains("/pictures") ||
                path.contains("whatsapp")
        val nameHint = PRIORITY_NAME_HINTS.any { it in name }
        return inUserFolder || nameHint
    }

    /** Prefer Documents-folder files over Downloads when merging results. */
    private fun folderBoost(file: File): Int {
        val path = file.absolutePath.lowercase()
        return when {
            path.contains("/documents/") || path.endsWith("/documents") ||
                path.contains("/document/") || path.contains("/my documents/") -> 2
            path.contains("/download") -> 1
            else -> 0
        }
    }

    private fun priorityBoost(file: File): Int {
        val name = file.name.lowercase()
        return if (PRIORITY_NAME_HINTS.any { it in name }) 1 else 0
    }

    private fun stableId(path: String, salt: Int): Long {
        val hash = path.hashCode().toLong().and(0x7fff_ffffL)
        return 900_000_000L + hash + salt
    }

    private fun mimeFor(file: File): String =
        when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "txt", "log", "md", "csv", "json", "xml", "html", "htm", "rtf" -> "text/plain"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            else -> "application/octet-stream"
        }

    private val SKIP_DIR_NAMES = setOf(
        "cache", "code_cache", "no_backup", ".thumbnails", "thumbnails",
        "node_modules", ".git", "lost+found",
    )

    private const val MAX_WALK_FILES = 8_000
}
