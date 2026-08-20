package com.echobooks.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SpeechToText {

    suspend fun listen(
        context: Context,
        onPartial: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ): String = suspendCancellableCoroutine { cont ->
        val recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        } catch (e: Exception) {
            onError("Speech recognition is not available on this device")
            cont.resume("")
            return@suspendCancellableCoroutine
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(bundle: Bundle?) {
                val text = bundle?.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!text.isNullOrBlank()) onPartial(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech heard"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy"
                    SpeechRecognizer.ERROR_NETWORK -> "Recognition needs a connection"
                    SpeechRecognizer.ERROR_AUDIO -> "Could not hear audio"
                    else -> "Recognition error ($error)"
                }
                onError(msg)
                if (cont.isActive) cont.resume("")
                runCatching { recognizer.destroy() }
            }
            override fun onResults(bundle: Bundle?) {
                val text = bundle?.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
                if (cont.isActive) cont.resume(text)
                runCatching { recognizer.destroy() }
            }
        }

        recognizer.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(intent)

        cont.invokeOnCancellation {
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
        }
    }
}