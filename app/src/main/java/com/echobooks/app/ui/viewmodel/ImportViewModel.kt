package com.echobooks.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echobooks.app.EchoBooksApp
import com.echobooks.app.data.Book
import com.echobooks.app.data.Chapter
import com.echobooks.app.data.EbookParser
import com.echobooks.app.data.ParsedChapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as EchoBooksApp

    enum class Phase { Idle, Importing, Narrating, Done, Error }

    data class State(
        val phase: Phase = Phase.Idle,
        val bookId: Long? = null,
        val chapters: List<ParsedChapter> = emptyList(),
        val totalChapters: Int = 0,
        val narrated: Int = 0,
        val detail: String = "",
        val error: String? = null,
        val done: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null
    private var bookId: Long = 0

    fun start(uri: Uri, displayName: String) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                val fallbackTitle = displayName.substringBeforeLast('.').ifBlank { "Imported Book" }
                val ext = displayName.substringAfterLast('.', "").lowercase()
                _state.value = State(Phase.Importing, detail = "Reading “$fallbackTitle”…")

                val parsed = withContext(Dispatchers.IO) {
                    val resolved = app.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Could not open the selected file.")
                    resolved.use { EbookParser.parse(ext, it, fallbackTitle) }
                }
                if (parsed.chapters.isEmpty()) {
                    _state.value = State(Phase.Error, error = "No text found in this file.")
                    return@launch
                }
                val total = parsed.chapters.size

                bookId = withContext(Dispatchers.IO) { app.database.bookDao().insert(newBook(parsed, fallbackTitle, ext)) }
                val dir = File(app.getDir("books", android.content.Context.MODE_PRIVATE), bookId.toString())
                    .apply { mkdirs() }

                _state.value = State(Phase.Narrating, bookId = bookId, chapters = parsed.chapters,
                    totalChapters = total, narrated = 0, detail = "Narrating chapter 1 of $total…")

                val speed = app.settings.speed.first()
                val pitch = app.settings.pitch.first()
                val voice = app.settings.voice.first()
                val ready = app.tts.ensureReady(voice.ifBlank { null }, speed, pitch)
                if (!ready) throw IllegalStateException("Text-to-speech is not available on this device.")

                for ((i, ch) in parsed.chapters.withIndex()) {
                    _state.update { it.copy(detail = "Narrating chapter ${i + 1} of $total…") }
                    val res = app.tts.synthesizeToFiles(ch.text, dir, "ch${i + 1}")
                    withContext(Dispatchers.IO) {
                        app.database.chapterDao().insert(
                            Chapter(
                                bookId = bookId, index = i, title = ch.title, text = ch.text,
                                segments = res.segmentsJson, durationMs = res.durationMs
                            )
                        )
                    }
                    _state.update { it.copy(narrated = i + 1) }
                }

                val chapters = app.database.chapterDao().getForBook(bookId)
                val totalMs = chapters.sumOf { it.durationMs }
                val current = app.database.bookDao().getById(bookId)
                if (current != null) {
                    app.database.bookDao().update(
                        current.copy(completed = true, durationMs = totalMs, chapterCount = chapters.size)
                    )
                }
                _state.value = State(Phase.Done, bookId = bookId, chapters = parsed.chapters,
                    totalChapters = total, narrated = total, detail = "Ready to listen", done = true)
            } catch (e: CancellationException) {
                cleanup()
                throw e
            } catch (e: Exception) {
                _state.value = State(Phase.Error, error = e.message ?: "Import failed")
                cleanup()
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private fun newBook(parsed: com.echobooks.app.data.ParsedBook, fallbackTitle: String, ext: String) = Book(
        title = parsed.title.ifBlank { fallbackTitle },
        author = parsed.author.ifBlank { "EchoBooks" },
        genre = "Imported",
        brief = "Imported from ${ext.uppercase()} and narrated on-device.",
        lengthMin = 0,
        coverHue = Random.nextInt(360),
        chapterCount = parsed.chapters.size
    )

    private fun cleanup() {
        if (bookId == 0L) return
        viewModelScope.launch {
            app.database.chapterDao().deleteForBook(bookId)
            app.database.bookmarkDao().deleteForBook(bookId)
            app.database.bookDao().getById(bookId)?.let { app.database.bookDao().delete(it) }
            File(app.getDir("books", android.content.Context.MODE_PRIVATE), bookId.toString())
                .deleteRecursively()
            bookId = 0
        }
    }
}