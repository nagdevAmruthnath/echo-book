package com.echobooks.app.tts

import android.content.Context
import com.echobooks.app.data.SegmentInfo
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File

class TtsException(message: String) : Exception(message)

data class SynthesisResult(
    val files: List<File>,
    val durationMs: Long,
    val segmentsJson: String
)

class TtsEngine(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient()
    private val engineMutex = Mutex()

    private var offlineTts: OfflineTts? = null
    private var engineModelKey: String? = null
    private var speed = 1.0f
    private var speakerId = 0

    fun isDownloaded(voiceId: String): Boolean {
        val dir = voiceDir(voiceId)
        val hasOnnx = dir.listFiles()?.any { it.extension == "onnx" } == true
        val hasTokens = File(dir, "tokens.txt").exists()
        val hasEspeak = File(dir, "espeak-ng-data").isDirectory
        val hasVoices = File(dir, "voices.bin").exists()
        return hasOnnx && hasTokens && hasEspeak && hasVoices
    }

    /**
     * Ensures the [voice] model is downloaded (when [allowDownload]) and the
     * inference engine is created. A blank [voice] falls back to the default voice.
     * [onProgress] receives download progress in 0..1.
     */
    suspend fun ensureReady(
        voice: String?,
        speed: Float,
        pitch: Float,
        allowDownload: Boolean = true,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.Default) {
        val selected = TtsVoices.byId(voice?.takeIf { it.isNotBlank() })
        try {
            if (!isDownloaded(selected.id)) {
                if (!allowDownload) return@withContext false
                downloadVoice(selected, onProgress)
            }
            engineMutex.withLock {
                val modelKey = selected.modelKey()
                if (offlineTts == null || engineModelKey != modelKey) {
                    offlineTts?.release()
                    offlineTts = createEngine(selected)
                    engineModelKey = modelKey
                }
                this@TtsEngine.speed = speed.coerceIn(0.5f, 2.0f)
                speakerId = selected.speakerId
                offlineTts != null
            }
        } catch (e: Exception) {
            offlineTts?.let { runCatching { it.release() } }
            offlineTts = null
            false
        }
    }

    /**
     * Synthesizes [text], splits across segments, and adds [gapMs] of trailing
     * silence to the final segment so there is a natural pause between chapters.
     */
    suspend fun synthesizeToFiles(
        text: String,
        dir: File,
        prefix: String,
        gapMs: Long = 800,
        onSegmentProgress: ((current: Int, total: Int) -> Unit)? = null
    ): SynthesisResult {
        if (offlineTts == null) throw TtsException("Speech engine is not ready")
        val segments = splitSegments(text)
        val files = mutableListOf<File>()
        val infos = mutableListOf<SegmentInfo>()
        var totalMs = 0L
        for ((i, seg) in segments.withIndex()) {
            onSegmentProgress?.invoke(i + 1, segments.size)
            val raw = File(dir, "${prefix}_s$i.wav")
            val ok = synthesizeOne(seg, raw)
            if (!ok) throw TtsException("Speech synthesis failed for segment ${i + 1}")
            if (i == segments.lastIndex && gapMs > 0) {
                AudioConverter.appendSilence(raw, gapMs)
            }
            val segMs = AudioConverter.wavDurationMs(raw)
            val m4a = File(dir, "${prefix}_s$i.m4a")
            val converted = AudioConverter.wavToM4a(raw, m4a)
            val finalFile: File
            if (converted) {
                raw.delete()
                finalFile = m4a
            } else {
                finalFile = raw
            }
            files += finalFile
            infos += SegmentInfo(finalFile.name, segMs)
            totalMs += segMs
        }
        return SynthesisResult(files, totalMs, json.encodeToString(ListSerializer(SegmentInfo.serializer()), infos))
    }

    fun shutdown() {
        offlineTts?.let { runCatching { it.release() } }
        offlineTts = null
        engineModelKey = null
    }

    private suspend fun synthesizeOne(text: String, outFile: File): Boolean =
        withContext(Dispatchers.Default) {
            try {
                val tts = offlineTts ?: return@withContext false
                val audio = tts.generate(text, speakerId, speed) ?: return@withContext false
                audio.save(outFile.absolutePath)
            } catch (e: Exception) {
                false
            }
        }

    private fun createEngine(voice: TtsVoice): OfflineTts {
        val dir = voiceDir(voice.id)
        val onnx = dir.listFiles()?.firstOrNull { it.extension == "onnx" }
            ?: throw TtsException("Model file not found for ${voice.id}")
        val kokoro = OfflineTtsKokoroModelConfig.builder()
            .setModel(onnx.absolutePath)
            .setVoices(File(dir, "voices.bin").absolutePath)
            .setTokens(File(dir, "tokens.txt").absolutePath)
            .setDataDir(File(dir, "espeak-ng-data").absolutePath)
            .setLengthScale(1.0f)
            .build()
        val model = OfflineTtsModelConfig.builder()
            .setKokoro(kokoro)
            .setNumThreads(4)
            .setDebug(false)
            .build()
        return OfflineTts(OfflineTtsConfig.builder().setModel(model).build())
    }

    private suspend fun downloadVoice(voice: TtsVoice, onProgress: ((Float) -> Unit)?) {
        val dir = voiceDir(voice.id).apply { mkdirs() }
        val tmp = File(dir, voice.tarballName)
        val request = Request.Builder().url(TtsVoices.downloadUrl(voice)).build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw TtsException("Voice download failed: HTTP ${resp.code}")
            val body = resp.body ?: throw TtsException("Voice download returned no body")
            val total = body.contentLength()
            var received = 0L
            val buffer = ByteArray(64 * 1024)
            body.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        out.write(buffer, 0, n)
                        received += n
                        if (total > 0) onProgress?.invoke((received.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        extractTarball(tmp, dir)
        tmp.delete()
    }

    private fun extractTarball(file: File, dest: File) {
        BZip2CompressorInputStream(file.inputStream()).use { bz2 ->
            TarArchiveInputStream(bz2).use { tar ->
                var rootPrefix: String? = null
                var entry: TarArchiveEntry? = tar.nextTarEntry
                while (entry != null) {
                    val name = entry.name
                    if (rootPrefix == null && entry.isDirectory) {
                        rootPrefix = name.removeSuffix("/")
                    }
                    val relative = rootPrefix?.let { name.removePrefix(it + "/") } ?: name
                    val target = File(dest, relative)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        val buffer = ByteArray(64 * 1024)
                        target.outputStream().use { out ->
                            while (true) {
                                val n = tar.read(buffer)
                                if (n <= 0) break
                                out.write(buffer, 0, n)
                            }
                        }
                    }
                    entry = tar.nextTarEntry
                }
            }
        }
    }

    private fun voiceDir(voiceId: String): File =
        File(context.getDir("tts", Context.MODE_PRIVATE), TtsVoices.byId(voiceId).modelKey())

    private fun splitSegments(text: String, max: Int = 2400): List<String> {
        val paragraphs = text.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (p in paragraphs) {
            if (p.length > max) {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current.clear()
                }
                var chunk = p
                while (chunk.length > max) {
                    val cut = chunk.lastIndexOf(". ", max - 1).let {
                        if (it in (max / 2)..max) it + 1 else max
                    }
                    result.add(chunk.substring(0, cut))
                    chunk = chunk.substring(cut).trimStart()
                }
                current.append(chunk)
            } else if (current.length + p.length + 2 > max) {
                result.add(current.toString())
                current.clear()
                current.append(p)
            } else {
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(p)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}
