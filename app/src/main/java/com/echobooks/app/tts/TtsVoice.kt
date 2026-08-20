package com.echobooks.app.tts

data class TtsVoice(
    val id: String,
    val label: String,
    val detail: String,
    val tarballName: String,
    val modelFile: String,
    val downloadBytes: Long,
    val speakerId: Int = 0
) {
    val downloadMb: Int
        get() = (downloadBytes / (1024 * 1024)).toInt().coerceAtLeast(1)

    fun modelKey(): String = tarballName.removeSuffix(".tar.bz2")
}

object TtsVoices {

    private const val TTS_MODELS_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"

    private const val KOKORO_TARBALL = "kokoro-int8-en-v0_19.tar.bz2"
    private const val KOKORO_MODEL = "model.onnx"
    private const val KOKORO_BYTES = 103_248_205L

    // All Kokoro voices share a single model download; the speaker id selects the voice.
    val all = listOf(
        TtsVoice("af_sarah", "Sarah", "American English · female narrator", KOKORO_TARBALL, KOKORO_MODEL, KOKORO_BYTES, speakerId = 3),
        TtsVoice("af_bella", "Bella", "American English · female", KOKORO_TARBALL, KOKORO_MODEL, KOKORO_BYTES, speakerId = 1),
        TtsVoice("af", "Heart", "American English · female", KOKORO_TARBALL, KOKORO_MODEL, KOKORO_BYTES, speakerId = 0),
        TtsVoice("af_sky", "Sky", "American English · female", KOKORO_TARBALL, KOKORO_MODEL, KOKORO_BYTES, speakerId = 4),
        TtsVoice("af_nicole", "Nicole", "American English · female", KOKORO_TARBALL, KOKORO_MODEL, KOKORO_BYTES, speakerId = 2),
        TtsVoice("am_adam", "Adam", "American English · male", KOKORO_TARBALL, KOKORO_MODEL, KOKORO_BYTES, speakerId = 5),
        TtsVoice("am_michael", "Michael", "American English · male", KOKORO_TARBALL, KOKORO_MODEL, KOKORO_BYTES, speakerId = 6),
        TtsVoice("bf_emma", "Emma", "British English · female", KOKORO_TARBALL, KOKORO_MODEL, KOKORO_BYTES, speakerId = 7),
        TtsVoice("bf_isabella", "Isabella", "British English · female", KOKORO_TARBALL, KOKORO_MODEL, KOKORO_BYTES, speakerId = 8),
        TtsVoice("bm_george", "George", "British English · male", KOKORO_TARBALL, KOKORO_MODEL, KOKORO_BYTES, speakerId = 9),
        TtsVoice("bm_lewis", "Lewis", "British English · male", KOKORO_TARBALL, KOKORO_MODEL, KOKORO_BYTES, speakerId = 10)
    )

    fun byId(id: String?): TtsVoice = all.firstOrNull { it.id == id } ?: all.first()

    fun downloadUrl(voice: TtsVoice): String = TTS_MODELS_URL + voice.tarballName
}
