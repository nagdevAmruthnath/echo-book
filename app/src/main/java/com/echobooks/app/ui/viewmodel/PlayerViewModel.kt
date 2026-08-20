package com.echobooks.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.echobooks.app.EchoBooksApp
import com.echobooks.app.audio.PlayerController
import com.echobooks.app.data.Book
import com.echobooks.app.data.Bookmark
import com.echobooks.app.data.Chapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as EchoBooksApp
    private val controller = PlayerController(application)

    data class UiState(
        val book: Book? = null,
        val chapters: List<Chapter> = emptyList(),
        val chapterIndex: Int = 0,
        val totalChapters: Int = 0,
        val chapterTitle: String = "",
        val isPlaying: Boolean = false,
        val bookPositionMs: Long = 0,
        val bookDurationMs: Long = 0,
        val speed: Float = 1f,
        val sleepMinutes: Int = 0,
        val bookmarks: List<Bookmark> = emptyList(),
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var bookId: Long = 0
    private var autoSaveJob: Job? = null
    private var sleepJob: Job? = null
    private var polling = false
    private var knownChapterCount = -1
    private val generationProgress = com.echobooks.app.generation.GenerationState.progress

    fun load(id: Long) {
        if (id == bookId && polling) return
        bookId = id
        viewModelScope.launch {
            val book = app.database.bookDao().getById(id) ?: return@launch
            val chapters = app.database.chapterDao().getForBook(id)
            knownChapterCount = chapters.size
            _state.update { it.copy(book = book, chapters = chapters, totalChapters = chapters.size, speed = 1f) }
            controller.bind()
            controller.loadBook(book, chapters, bookDir(id))
            startPolling()
            startAutoSave()
            observeBookmarks()
            startChapterWatcher()
        }
    }

    /** If the book is still generating in the background, newly finished
     * chapters are appended to the live playlist as they land. */
    private fun startChapterWatcher() {
        viewModelScope.launch {
            app.database.chapterDao().observeForBook(bookId).collect { chapters ->
                if (chapters.size > knownChapterCount && knownChapterCount >= 0) {
                    knownChapterCount = chapters.size
                    val book = _state.value.book
                    if (book != null) {
                        val resume = controller.bookPosition.value
                        controller.loadBook(book, chapters, bookDir(bookId), resumeBookMs = resume)
                    }
                } else {
                    knownChapterCount = chapters.size
                }
                _state.update {
                    it.copy(chapters = chapters, totalChapters = chapters.size)
                }
            }
        }
    }

    private fun observeBookmarks() {
        viewModelScope.launch {
            app.database.bookmarkDao().observeForBook(bookId).collect { list ->
                _state.update { it.copy(bookmarks = list) }
            }
        }
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        viewModelScope.launch {
            while (isActive) {
                val item = controller.itemIndex.value
                val ch = controller.chapterIndex.value
                val chapters = _state.value.chapters
                _state.update {
                    it.copy(
                        chapterIndex = ch,
                        chapterTitle = chapters.getOrNull(ch)?.title ?: "",
                        isPlaying = controller.isPlaying.value,
                        bookPositionMs = controller.bookPosition.value,
                        bookDurationMs = controller.bookDuration.value.let { d ->
                            if (d == 0L) chapters.sumOf { c -> c.durationMs } else d
                        }
                    )
                }
                if (controller.playbackState.value == Player.STATE_ENDED) {
                    markCompleted()
                }
                delay(300)
            }
        }
    }

    private suspend fun markCompleted() {
        val b = _state.value.book ?: return
        if (b.completed) return
        app.database.bookDao().update(b.copy(completed = true, progressFraction = 1f))
        _state.update { it.copy(book = b.copy(completed = true, progressFraction = 1f)) }
    }

    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                saveProgress()
            }
        }
    }

    fun saveNow() {
        viewModelScope.launch { saveProgress() }
    }

    private suspend fun saveProgress() {
        if (bookId == 0L) return
        val b = app.database.bookDao().getById(bookId) ?: return
        val item = controller.itemIndex.value
        val pos = controller.position.value
        val dur = _state.value.bookDurationMs
        val frac = if (dur > 0) (controller.bookPosition.value.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
        app.database.bookDao().update(
            b.copy(progressItem = item, progressMs = pos, progressFraction = frac)
        )
        _state.update { it.copy(book = it.book?.copy(progressItem = item, progressMs = pos, progressFraction = frac)) }
    }

    fun togglePlay() {
        controller.toggle()
        saveNow()
    }

    fun stop() {
        controller.stopAndReset()
        saveNow()
    }

    fun seekToBook(ms: Long) {
        controller.seekToBook(ms)
        saveNow()
    }

    fun nextChapter() {
        controller.nextChapter()
        saveNow()
    }

    fun prevChapter() {
        controller.prevChapter()
        saveNow()
    }

    fun seekToChapter(i: Int) {
        controller.seekToChapter(i)
        saveNow()
    }

    fun setSpeed(speed: Float) {
        controller.setSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _state.update { it.copy(sleepMinutes = 0) }
            return
        }
        _state.update { it.copy(sleepMinutes = minutes) }
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            controller.pause()
            saveNow()
            _state.update { it.copy(sleepMinutes = 0) }
        }
    }

    fun addBookmark(label: String) {
        val item = controller.itemIndex.value
        val pos = controller.position.value
        val chTitle = _state.value.chapterTitle.ifBlank { "Chapter ${_state.value.chapterIndex + 1}" }
        val bookmark = Bookmark(
            bookId = bookId,
            itemIndex = item,
            positionMs = pos,
            chapterTitle = chTitle,
            label = label.ifBlank { formatClock(pos) }
        )
        viewModelScope.launch { app.database.bookmarkDao().insert(bookmark) }
    }

    fun jumpToBookmark(b: Bookmark) {
        controller.seekToItem(b.itemIndex, b.positionMs)
        saveNow()
    }

    fun deleteBookmark(b: Bookmark) {
        viewModelScope.launch { app.database.bookmarkDao().delete(b) }
    }

    fun deleteBook() {
        viewModelScope.launch {
            controller.stop()
            app.database.chapterDao().deleteForBook(bookId)
            app.database.bookmarkDao().deleteForBook(bookId)
            app.database.bookDao().getById(bookId)?.let { app.database.bookDao().delete(it) }
            bookDir(bookId).deleteRecursively()
        }
    }

    private fun bookDir(id: Long): File =
        File(app.getDir("books", android.content.Context.MODE_PRIVATE), id.toString())

    private fun formatClock(ms: Long): String {
        val total = ms / 1000
        val m = total / 60
        val s = total % 60
        return "%02d:%02d".format(m, s)
    }

    override fun onCleared() {
        super.onCleared()
        autoSaveJob?.cancel()
        sleepJob?.cancel()
        controller.release()
    }
}