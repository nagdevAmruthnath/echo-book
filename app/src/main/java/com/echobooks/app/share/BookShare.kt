package com.echobooks.app.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.echobooks.app.data.Book
import com.echobooks.app.data.Chapter
import com.echobooks.app.data.SegmentInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BookShare {

    /**
     * Exports the book's audio into a single zip under cache/exports and starts
     * the Android share sheet. The share sheet lists Bluetooth transfers as well
     * as apps like WhatsApp, Signal and Telegram.
     */
    suspend fun shareBook(
        context: Context,
        book: Book,
        chapters: List<Chapter>,
        bookDir: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val zip = buildZip(context, book, chapters, bookDir) ?: return@withContext false
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, book.title)
                putExtra(Intent.EXTRA_TEXT, "“${book.title}” by ${book.author} — audiobook shared from EchoBooks")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Share “${book.title}”")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun buildZip(context: Context, book: Book, chapters: List<Chapter>, bookDir: File): File? {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = book.title.replace(Regex("[^A-Za-z0-9 _-]"), "_").trim().ifBlank { "book" }
        val zip = File(exportDir, "$safe.zip")
        if (zip.exists()) zip.delete()
        ZipOutputStream(zip.outputStream().buffered()).use { zos ->
            val meta = buildString {
                append("# ${book.title}\n")
                append("Author: ${book.author}\n")
                append("Genre: ${book.genre}\n")
                append("Duration: ${book.durationMs / 60000} min\n\n")
            }
            zos.putNextEntry(ZipEntry("_info.txt"))
            zos.write(meta.toByteArray())
            zos.closeEntry()

            val seen = HashSet<String>()
            for ((ci, ch) in chapters.withIndex()) {
                val files = parseSegments(ch.segments)
                if (files.isEmpty()) continue
                val prefix = "Chapter ${ci + 1} - ${ch.title}".replace(Regex("[^A-Za-z0-9 _-]"), "_")
                for ((fi, f) in files.withIndex()) {
                    val src = File(bookDir, f.file)
                    if (!src.exists()) continue
                    val ext = src.extension
                    val name = if (files.size == 1) "$prefix.$ext"
                        else "$prefix - ${fi + 1}.$ext"
                    var unique = name
                    var n = 2
                    while (!seen.add(unique)) {
                        unique = "$prefix - $n.$ext".let { seen.add(it); it }
                        n++
                    }
                    zos.putNextEntry(ZipEntry(unique))
                    src.inputStream().buffered().copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
        return if (zip.exists() && zip.length() > 0) zip else null
    }

    private fun parseSegments(json: String): List<SegmentInfo> = try {
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<List<SegmentInfo>>(json)
    } catch (e: Exception) {
        emptyList()
    }
}