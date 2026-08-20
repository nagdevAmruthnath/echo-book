package com.echobooks.app.generation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.echobooks.app.EchoBooksApp
import com.echobooks.app.data.Book
import com.echobooks.app.data.Chapter
import com.echobooks.app.llm.BookSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class GenerationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var bookId: Long = 0L
    private var lastUpdate = AtomicLong(0L)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancel()
                return START_NOT_STICKY
            }
            else -> {
                val spec = intent?.let { BookSpec(
                    titleHint = it.getStringExtra(EXTRA_TITLE) ?: "",
                    author = it.getStringExtra(EXTRA_AUTHOR) ?: "",
                    genre = it.getStringExtra(EXTRA_GENRE) ?: "",
                    brief = it.getStringExtra(EXTRA_BRIEF) ?: "",
                    lengthMin = it.getIntExtra(EXTRA_LENGTH, 30)
                ) } ?: run {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (job?.isActive == true) return START_NOT_STICKY
                startForeground(NOTIF_ID, buildNotification("Starting…", 0, 0))
                GenerationState.begin()
                job = scope.launch { runGeneration(spec) }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runGeneration(spec: BookSpec) {
        val app = application as EchoBooksApp
        try {
            GenerationState.update { it.copy(active = true, phase = GenerationState.Phase.Outline, detail = "Designing your story…") }
            updateNotif("Designing your story…", 0, 0)

            val apiKey = app.settings.apiKey.first()
            val model = app.settings.model.first()
            if (apiKey.isBlank()) {
                fail("Set your OpenRouter API key in Settings first.")
                return
            }

            val outline = app.generator.generateOutline(apiKey, model, spec)
            val total = outline.chapters.size
            bookId = app.database.bookDao().insert(
                Book(
                    title = outline.title,
                    author = outline.author.ifBlank { spec.author.ifBlank { "EchoBooks" } },
                    genre = spec.genre,
                    brief = spec.brief,
                    lengthMin = spec.lengthMin,
                    chapterCount = total,
                    coverHue = Random.nextInt(360)
                )
            )
            val dir = File(app.getDir("books", Context.MODE_PRIVATE), bookId.toString()).apply { mkdirs() }

            GenerationState.update { it.copy(
                active = true, phase = GenerationState.Phase.Writing, bookId = bookId,
                title = outline.title, chapters = outline.chapters, totalChapters = total,
                written = 0, narrated = 0, detail = "Writing chapter 1 of $total…"
            ) }

            val channel = Channel<Pair<Int, String>>(capacity = 2)

            coroutineScope {
                val writer = launch {
                    var prev = ""
                    for (i in outline.chapters.indices) {
                        GenerationState.update { it.copy(
                            active = true, phase = GenerationState.Phase.Writing, written = i,
                            detail = "Writing chapter ${i + 1} of $total…"
                        ) }
                        updateNotif("Writing chapter ${i + 1} of $total…", i, total)
                        val wordCount = AtomicInteger(0)
                        val text = retry("write chapter ${i + 1}") {
                            app.generator.generateChapter(apiKey, model, spec, outline, i, prev) { delta ->
                                wordCount.addAndGet(delta.split(Regex("\\s+")).size)
                                maybeThrottled {
                                    GenerationState.update { st -> st.copy(
                                        active = true, phase = GenerationState.Phase.Writing, written = i,
                                        detail = "Writing chapter ${i + 1} of $total… (${wordCount.get()} words)"
                                    ) }
                                }
                            }
                        }
                        prev = text.takeLast(3000)
                        channel.send(i to text)
                    }
                    channel.close()
                }

                val ttsJob = launch {
                    val speed = app.settings.speed.first()
                    val pitch = app.settings.pitch.first()
                    val voice = app.settings.voice.first()
                    val ready = app.tts.ensureReady(voice.ifBlank { null }, speed, pitch)
                    if (!ready) throw IllegalStateException("Text-to-speech is not available on this device.")
                    for ((i, text) in channel) {
                        GenerationState.update { it.copy(
                            active = true, phase = GenerationState.Phase.Narrating,
                            narratingSegment = 0, narratingSegmentTotal = 0,
                            detail = "Narrating chapter ${i + 1} of $total…"
                        ) }
                        updateNotif("Narrating chapter ${i + 1} of $total…", i, total)
                        val ch = outline.chapters[i]
                        val res = retry("narrate chapter ${i + 1}") {
                            app.tts.synthesizeToFiles(text, dir, "ch${i + 1}") { seg, segTotal ->
                                maybeThrottled {
                                    GenerationState.update { st -> st.copy(
                                        active = true, phase = GenerationState.Phase.Narrating,
                                        narratingSegment = seg, narratingSegmentTotal = segTotal,
                                        detail = "Narrating chapter ${i + 1} of $total — segment $seg of $segTotal"
                                    ) }
                                    updateNotif("Narrating chapter ${i + 1} of $total — segment $seg of $segTotal", i, total)
                                }
                            }
                        }
                        app.database.chapterDao().insert(
                            Chapter(
                                bookId = bookId, index = i, title = ch.title, text = text,
                                segments = res.segmentsJson, durationMs = res.durationMs
                            )
                        )
                        GenerationState.update { it.copy(active = true, narrated = i + 1, narratingSegment = 0, narratingSegmentTotal = 0) }
                    }
                }

                writer.join()
                ttsJob.join()
            }

            val chapters = app.database.chapterDao().getForBook(bookId)
            val totalMs = chapters.sumOf { it.durationMs }
            val current = app.database.bookDao().getById(bookId)
            if (current != null) {
                app.database.bookDao().update(
                    current.copy(completed = true, durationMs = totalMs, chapterCount = chapters.size)
                )
            }
            GenerationState.update { it.copy(
                active = true, phase = GenerationState.Phase.Done, written = total, narrated = total,
                detail = "Completed — “${outline.title}” is in your library", done = true
            ) }
            updateNotif("Done — ${outline.title} added to your library", total, total)
            delay(4000)
            GenerationState.end()
        } catch (e: CancellationException) {
            cleanup()
            throw e
        } catch (e: Exception) {
            fail(e.message ?: "Generation failed")
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cancel() {
        job?.cancel()
        job = null
        cleanup()
        GenerationState.end()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(message: String) {
        GenerationState.update { it.copy(
            active = true, phase = GenerationState.Phase.Error, error = message, detail = message, done = false
        ) }
        cleanup()
    }

    private fun cleanup() {
        if (bookId == 0L) return
        val id = bookId
        bookId = 0L
        val app = application as EchoBooksApp
        cleanupScope.launch {
            app.database.chapterDao().deleteForBook(id)
            app.database.bookmarkDao().deleteForBook(id)
            app.database.bookDao().getById(id)?.let { app.database.bookDao().delete(it) }
            File(app.getDir("books", Context.MODE_PRIVATE), id.toString()).deleteRecursively()
        }
    }

    /** Retries a transient failure (network hiccup, TTS error) up to 3 times. */
    private suspend fun <T> retry(what: String, block: suspend () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt >= 3) throw e
                GenerationState.update { it.copy(
                    active = true, detail = "Failed to $what (attempt $attempt) — retrying…"
                ) }
                updateNotif("Retrying to $what…", attempt, 3)
                delay(1500L * attempt)
                attempt++
            }
        }
    }

    /** Emits a state update at most ~6×/sec so the UI is not flooded during writing. */
    private inline fun maybeThrottled(block: () -> Unit) {
        val now = SystemClock.uptimeMillis()
        if (now - lastUpdate.get() < 150) return
        lastUpdate.set(now)
        block()
    }

    private fun updateNotif(text: String, current: Int, total: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text, current, total))
    }

    private fun buildNotification(text: String, current: Int, total: Int): Notification {
        val channel = "generation"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && getSystemService(NotificationManager::class.java).getNotificationChannel(channel) == null) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channel, "Book creation", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val cancelIntent = PendingIntent.getService(
            this, 0,
            Intent(this, GenerationService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, channel)
            .setContentTitle("EchoBooks — creating audiobook")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Cancel & delete progress", cancelIntent)
        if (total > 0) {
            builder.setProgress(total, current.coerceIn(0, total), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 42
        private const val ACTION_CANCEL = "com.echobooks.app.action.CANCEL_GENERATION"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_AUTHOR = "author"
        private const val EXTRA_GENRE = "genre"
        private const val EXTRA_BRIEF = "brief"
        private const val EXTRA_LENGTH = "length"

        fun start(context: Context, spec: BookSpec) {
            val intent = Intent(context, GenerationService::class.java).apply {
                putExtra(EXTRA_TITLE, spec.titleHint)
                putExtra(EXTRA_AUTHOR, spec.author)
                putExtra(EXTRA_GENRE, spec.genre)
                putExtra(EXTRA_BRIEF, spec.brief)
                putExtra(EXTRA_LENGTH, spec.lengthMin)
            }
            context.startForegroundService(intent)
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, GenerationService::class.java).setAction(ACTION_CANCEL))
        }
    }
}