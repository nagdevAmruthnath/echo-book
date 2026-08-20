package com.echobooks.app.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.echobooks.app.data.Book
import com.echobooks.app.data.Chapter
import com.echobooks.app.data.SegmentInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class PlayerController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true }

    private var controller: MediaController? = null
    private var chapterStarts = intArrayOf()
    private var cumulativeMs = longArrayOf() // size = items + 1
    private var totalItems = 0
    private var totalBookMs = 0L

    private val _position = MutableStateFlow(0L)
    private val _bookPosition = MutableStateFlow(0L)
    private val _bookDuration = MutableStateFlow(0L)
    private val _isPlaying = MutableStateFlow(false)
    private val _itemIndex = MutableStateFlow(0)
    private val _chapterIndex = MutableStateFlow(0)
    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    private val _error = MutableStateFlow<String?>(null)

    val position: StateFlow<Long> = _position.asStateFlow()
    val bookPosition: StateFlow<Long> = _bookPosition.asStateFlow()
    val bookDuration: StateFlow<Long> = _bookDuration.asStateFlow()
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    val itemIndex: StateFlow<Int> = _itemIndex.asStateFlow()
    val chapterIndex: StateFlow<Int> = _chapterIndex.asStateFlow()
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun bind() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val c = withContext(Dispatchers.IO) {
            MediaController.Builder(context, token).buildAsync().get()
        }
        controller = c
        c.addListener(listener)
        scope.launch {
            while (isActive) {
                _position.value = c.currentPosition
                val item = c.currentMediaItemIndex.coerceIn(0, totalItems)
                _bookPosition.value = (cumulativeMs.getOrElse(item) { 0L } + c.currentPosition)
                    .coerceAtMost(totalBookMs)
                delay(400)
            }
        }
    }

    fun loadBook(book: Book, chapters: List<Chapter>, bookDir: File, resumeBookMs: Long? = null) {
        val c = controller ?: return
        val wasPlaying = c.isPlaying
        val items = mutableListOf<MediaItem>()
        val cum = mutableListOf(0L)
        chapterStarts = IntArray(chapters.size)
        for ((ci, ch) in chapters.withIndex()) {
            chapterStarts[ci] = items.size
            val segs = parseSegments(ch, bookDir)
            for (seg in segs) {
                items += MediaItem.Builder()
                    .setMediaId("${ch.index}_${items.size}")
                    .setUri(Uri.fromFile(File(bookDir, seg.file)))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(ch.title)
                            .setArtist(book.title)
                            .setAlbumTitle(book.title)
                            .build()
                    )
                    .build()
                cum += cum.last() + seg.d
            }
        }
        totalItems = items.size
        cumulativeMs = cum.toLongArray()
        totalBookMs = cum.last()
        _bookDuration.value = totalBookMs
        if (items.isEmpty()) return
        val resume = resumeBookMs ?: (cumulativeMs.getOrElse(book.progressItem) { 0L } + book.progressMs)
        val target = findItem(resume).first
        val targetMs = resume.coerceAtLeast(0L)
        c.setMediaItems(items, target, (targetMs - cumulativeMs.getOrElse(target) { 0L }).coerceAtLeast(0L))
        _itemIndex.value = target
        _chapterIndex.value = chapterForItem(target)
        _bookPosition.value = cumulativeMs.getOrElse(target) { 0L } + (targetMs - cumulativeMs.getOrElse(target) { 0L })
        if (wasPlaying) c.play()
    }

    fun play() {
        if (controller?.playbackState == Player.STATE_ENDED) seekToBook(0)
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun toggle() {
        if (controller?.isPlaying == true) controller?.pause() else play()
    }

    fun stop() {
        controller?.pause()
        controller?.stop()
    }

    fun stopAndReset() {
        val c = controller ?: return
        c.stop()
        c.seekTo(0, 0L)
        _itemIndex.value = 0
        _chapterIndex.value = 0
        _position.value = 0L
        _bookPosition.value = 0L
    }

    fun seekToBook(ms: Long) {
        val c = controller ?: return
        val pos = ms.coerceIn(0L, totalBookMs)
        val item = findItem(pos)
        c.seekTo(item.first, item.second)
        _chapterIndex.value = chapterForItem(item.first)
    }

    fun seekToItem(item: Int, positionMs: Long) {
        controller?.seekTo(item.coerceIn(0, totalItems - 1), positionMs)
        _chapterIndex.value = chapterForItem(item.coerceIn(0, totalItems - 1))
    }

    fun nextChapter() {
        val cur = _chapterIndex.value
        if (chapterStarts.size == 0) return
        val next = (cur + 1).coerceAtMost(chapterStarts.size - 1)
        seekToItem(chapterStarts[next], 0)
    }

    fun prevChapter() {
        val cur = _chapterIndex.value
        if (chapterStarts.size == 0) return
        if (_position.value > 5000) {
            seekToItem(chapterStarts[cur], 0)
        } else {
            val prev = (cur - 1).coerceAtLeast(0)
            seekToItem(chapterStarts[prev], 0)
        }
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed.coerceIn(0.5f, 3f))
    }

    fun seekToChapter(chapterIndex: Int) {
        if (chapterStarts.isEmpty()) return
        val idx = chapterIndex.coerceIn(0, chapterStarts.size - 1)
        seekToItem(chapterStarts[idx], 0)
    }

    fun chapterForItem(item: Int): Int {
        var ch = 0
        for ((i, start) in chapterStarts.withIndex()) {
            if (item >= start) ch = i else break
        }
        return ch
    }

    private fun findItem(posMs: Long): Pair<Int, Long> {
        if (totalItems == 0) return 0 to 0L
        var item = 0
        for (i in 0 until totalItems) {
            if (posMs >= cumulativeMs[i]) item = i else break
        }
        return item to (posMs - cumulativeMs.getOrElse(item) { 0L })
    }

    private fun parseSegments(chapter: Chapter, bookDir: File): List<SegmentInfo> = try {
        json.decodeFromString<List<SegmentInfo>>(chapter.segments)
            .filter { File(bookDir, it.file).exists() }
    } catch (e: Exception) {
        emptyList()
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val idx = controller?.currentMediaItemIndex ?: return
            _itemIndex.value = idx
            _chapterIndex.value = chapterForItem(idx)
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            _playbackState.value = playbackState
        }
        override fun onPlayerError(error: PlaybackException) {
            _error.value = error.localizedMessage ?: "Playback error"
        }
        override fun onEvents(player: Player, events: Player.Events) {
            val idx = player.currentMediaItemIndex
            _itemIndex.value = idx
            _chapterIndex.value = chapterForItem(idx)
            if (player.playbackState == Player.STATE_ENDED) _isPlaying.value = false
        }
    }

    fun release() {
        scope.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }
}