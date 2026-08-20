package com.echobooks.app.llm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean,
    val temperature: Double,
    val max_tokens: Int
)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
private data class ChatChoice(val message: ChatMsg? = null, val delta: ChatDelta? = null)

@Serializable
private data class ChatMsg(val content: String? = null)

@Serializable
private data class ChatDelta(val content: String? = null)

class OpenRouterException(message: String, val status: Int? = null) : Exception(message)

class OpenRouterClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.MINUTES)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun streamChat(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int = 4096,
        temperature: Double = 1.0,
        onDelta: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val request = buildRequest(apiKey, model, messages, maxTokens, temperature, stream = true)
        val call = client.newCall(request)
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isCancelled) {
                        cont.resumeWithException(CancellationException("Generation cancelled"))
                    } else {
                        cont.resumeWithException(e)
                    }
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (!response.isSuccessful) {
                            val err = response.body?.string().orEmpty()
                            cont.resumeWithException(
                                OpenRouterException(
                                    "OpenRouter returned ${response.code}: ${err.take(300)}",
                                    response.code
                                )
                            )
                            return
                        }
                        val sb = StringBuilder()
                        response.body!!.use { body ->
                            val source = body.source()
                            while (true) {
                                if (cont.isCancelled) {
                                    call.cancel()
                                    cont.resumeWithException(CancellationException("Generation cancelled"))
                                    return
                                }
                                val line = source.readUtf8Line() ?: break
                                if (!line.startsWith("data:")) continue
                                val payload = line.removePrefix("data:").trim()
                                if (payload == "[DONE]") break
                                val chunk = try {
                                    json.decodeFromString<ChatResponse>(payload)
                                } catch (e: Exception) {
                                    continue
                                }
                                val content = chunk.choices.firstOrNull()?.delta?.content
                                    ?: chunk.choices.firstOrNull()?.message?.content
                                if (!content.isNullOrEmpty()) {
                                    sb.append(content)
                                    onDelta(content)
                                }
                            }
                        }
                        if (cont.isCancelled) {
                            cont.resumeWithException(CancellationException("Generation cancelled"))
                        } else {
                            cont.resume(sb.toString())
                        }
                    } catch (t: Throwable) {
                        if (cont.isCancelled) {
                            cont.resumeWithException(CancellationException("Generation cancelled"))
                        } else {
                            cont.resumeWithException(t)
                        }
                    }
                }
            })
        }
    }

    suspend fun completeChat(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int = 4096,
        temperature: Double = 1.0
    ): String = withContext(Dispatchers.IO) {
        val request = buildRequest(apiKey, model, messages, maxTokens, temperature, stream = false)
        val call = client.newCall(request)
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isCancelled) {
                        cont.resumeWithException(CancellationException("Generation cancelled"))
                    } else {
                        cont.resumeWithException(e)
                    }
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (!response.isSuccessful) {
                            val err = response.body?.string().orEmpty()
                            cont.resumeWithException(
                                OpenRouterException(
                                    "OpenRouter returned ${response.code}: ${err.take(300)}",
                                    response.code
                                )
                            )
                            return
                        }
                        val parsed = response.body!!.use { body ->
                            json.decodeFromString<ChatResponse>(body.string())
                        }
                        if (cont.isCancelled) {
                            cont.resumeWithException(CancellationException("Generation cancelled"))
                        } else {
                            cont.resume(parsed.choices.firstOrNull()?.message?.content ?: "")
                        }
                    } catch (t: Throwable) {
                        if (cont.isCancelled) {
                            cont.resumeWithException(CancellationException("Generation cancelled"))
                        } else {
                            cont.resumeWithException(t)
                        }
                    }
                }
            })
        }
    }

    private fun buildRequest(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        temperature: Double,
        stream: Boolean
    ): Request {
        val payload = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(model, messages, stream, temperature, maxTokens)
        )
        return Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://echobooks.local")
            .addHeader("X-Title", "EchoBooks")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
    }
}