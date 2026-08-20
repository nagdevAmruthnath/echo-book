package com.echobooks.app.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max
import kotlin.math.roundToInt

data class OutlineChapter(val title: String, val summary: String)

data class BookOutline(val title: String, val author: String, val chapters: List<OutlineChapter>)

data class BookSpec(
    val titleHint: String = "",
    val author: String = "",
    val genre: String = "",
    val brief: String,
    val lengthMin: Int
)

class ChapterGenerator(private val client: OpenRouterClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateOutline(apiKey: String, model: String, spec: BookSpec): BookOutline {
        val chapterCount = max(1, (spec.lengthMin / 10.0).roundToInt())
        val hours = spec.lengthMin / 60
        val mins = spec.lengthMin % 60

        fun outlineUser(strict: Boolean): String = buildString {
            append(
                "Create the story blueprint for an audiobook of " +
                    (if (hours > 0) "$hours h" else "") +
                    (if (mins > 0) " ${mins} min" else "") +
                    " of narration (~1500 words per 10-minute chapter).\n\n"
            )
            if (spec.genre.isNotBlank()) append("Genre: ${spec.genre}\n")
            if (spec.author.isNotBlank()) append("Narrator / author voice: ${spec.author}\n")
            if (spec.titleHint.isNotBlank()) append("Title suggestion: ${spec.titleHint}\n")
            append("Story idea: ${spec.brief}\n\n")
            append("Create exactly $chapterCount chapters.\n")
            append(
                "Guardrails:\n" +
                    "- Every chapter must advance a distinct plot beat. Never reuse the same situation, setting, or conflict twice.\n" +
                    "- Chapter titles and summaries must be clearly different from each other; no near-duplicate summaries.\n" +
                    "- Vary locations, conflicts, and character actions across chapters so the story keeps moving forward.\n"
            )
            if (strict) {
                append(
                    "Respond with raw JSON only (no markdown, no code fences, no commentary). " +
                        "Exactly this shape:\n" +
                        "{\"title\":\"...\",\"author\":\"...\",\"chapters\":[{\"title\":\"...\",\"summary\":\"2-3 sentences\"}]}\n"
                )
            } else {
                append(
                    "Return ONLY this JSON:\n" +
                        "{\"title\":\"...\",\"author\":\"...\",\"chapters\":[{\"title\":\"...\",\"summary\":\"...\"}]}\n"
                )
            }
        }

        val system = "You are EchoBooks, an expert audiobook ghostwriter. " +
            "You turn a user's idea into a complete, engaging, coherent story."

        val first = client.completeChat(
            apiKey, model,
            listOf(ChatMessage("system", system), ChatMessage("user", outlineUser(false))),
            maxTokens = 4096, temperature = 0.8
        )
        val parsed = tryParseOutline(first)
        if (parsed != null) return parsed

        val second = client.completeChat(
            apiKey, model,
            listOf(ChatMessage("system", system), ChatMessage("user", outlineUser(true))),
            maxTokens = 4096, temperature = 0.6
        )
        val retry = tryParseOutline(second)
        if (retry != null) return retry

        return BookOutline(
            title = spec.titleHint.ifBlank { spec.brief.trim().take(40).ifBlank { "Untitled" } },
            author = spec.author.ifBlank { "EchoBooks" },
            chapters = (0 until chapterCount).map { OutlineChapter("Chapter ${it + 1}", spec.brief) }
        )
    }

    private fun tryParseOutline(raw: String): BookOutline? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val el = json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject
            val title = el["title"]?.jsonPrimitive?.contentOrNull ?: return null
            val author = el["author"]?.jsonPrimitive?.contentOrNull ?: ""
            val arr = el["chapters"]?.jsonArray ?: return null
            val chapters = arr.mapNotNull { c ->
                val o = c.jsonObject
                val t = o["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val s = o["summary"]?.jsonPrimitive?.contentOrNull ?: ""
                OutlineChapter(t, s)
            }
            if (chapters.isEmpty()) null else BookOutline(title, author, chapters)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun generateChapter(
        apiKey: String,
        model: String,
        spec: BookSpec,
        outline: BookOutline,
        chapterIndex: Int,
        prevText: String,
        onDelta: (String) -> Unit = {}
    ): String {
        val ch = outline.chapters[chapterIndex]
        val system = "You are EchoBooks, an expert audiobook ghostwriter continuing a story chapter by chapter.\n" +
            "Rules:\n" +
            "- Write ONLY the chapter prose. No chapter number, no title, no headings, no markdown, no commentary, no stage directions.\n" +
            "- Each chapter is about 10 minutes of narration (~1500-1750 words).\n" +
            "- WRITE THE FULL LENGTH. Do not summarize, do not rush. Expand scenes, describe settings, show dialogue, build tension line by line until the chapter is genuinely 1500-1750 words long.\n" +
            "- Continue the story naturally from where the previous chapter ended.\n" +
            "- Use vivid, scene-based prose that flows well when read aloud.\n" +
            "- Keep consistent characters, tone and timeline with the outline.\n" +
            "- NEVER repeat yourself: do not reuse the same sentences, phrases, images, or wordings within this chapter.\n" +
            "- Never re-describe a scene, setting, or event that already appeared in an earlier chapter; only reference the past in passing, briefly, when it drives the story.\n" +
            "- Vary sentence openings, vocabulary, and scene beats. If a beat (walking, arriving, thinking, reacting, describing) was used earlier, show it differently.\n" +
            "- End the chapter at a natural break, on a hook that makes the listener want to continue.\n" +
            "- Separate paragraphs with blank lines."

        val user = buildString {
            append("BOOK: ${outline.title}\n")
            append("GENRE: ${spec.genre.ifBlank { "fiction" }}\n")
            append("NARRATOR VOICE: ${(spec.author.ifBlank { outline.author.ifBlank { "warm, vivid storyteller" } })}\n")
            append("STORY PREMISE: ${spec.brief}\n\n")
            append("OUTLINE:\n")
            outline.chapters.forEachIndexed { i, c ->
                append("  ${i + 1}. ${c.title}: ${c.summary}\n")
            }
            append("\nNow write CHAPTER ${chapterIndex + 1} of ${outline.chapters.size} — \"${ch.title}\".\n")
            append("Chapter focus: ${ch.summary}\n\n")
            if (prevText.isNotBlank()) {
                append("The previous chapter ended like this (continue directly from here):\n")
                append(prevText.takeLast(3000))
                append("\n\n")
            }
            append("Write the full chapter now.")
        }

        var text = client.streamChat(
            apiKey, model,
            listOf(ChatMessage("system", system), ChatMessage("user", user)),
            maxTokens = 3000, temperature = 1.0, onDelta = onDelta
        ).trim()

        // Expand until the chapter is genuinely long enough (up to 2 extra passes).
        // Token budgets are capped to the shortfall so chapters never balloon past
        // the target (~1500-1750 words ≈ 10 minutes of narration).
        val targetWords = 1500
        var passes = 0
        while (wordCount(text) < targetWords && passes < 2) {
            passes++
            val current = wordCount(text)
            val gap = (targetWords - current).coerceAtLeast(50)
            val expand = buildString {
                append("The chapter you wrote is only $current words. ")
                append("Continue the same scene and write roughly $gap more words until the chapter is about $targetWords words total.\n")
                append("Do NOT repeat or rephrase anything already written above. Introduce new events, dialogue, or description that advances the scene.\n")
                append("Do NOT end the chapter yet. Write fresh prose that flows straight on from this ending:\n\n")
                append(text.takeLast(800))
            }
            val more = client.streamChat(
                apiKey, model,
                listOf(ChatMessage("system", system), ChatMessage("user", expand)),
                maxTokens = (gap * 1.5).toInt().coerceIn(400, 3000), temperature = 1.0, onDelta = onDelta
            ).trim()
            if (more.isBlank()) break
            text = "$text\n\n$more".trim()
        }
        return text
    }

    private fun wordCount(text: String): Int =
        text.split(Regex("\\s+")).count { it.isNotEmpty() }
}