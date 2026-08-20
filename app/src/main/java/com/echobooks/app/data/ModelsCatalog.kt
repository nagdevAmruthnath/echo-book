package com.echobooks.app.data

data class WebModel(
    val id: String,
    val label: String,
    val free: Boolean,
    val nsfw: Boolean,
    val context: Long
) {
    val display: String
        get() = if (free) "$label (free)" else label

    val idDisplay: String
        get() = if (free) "$id (free)" else id
}

object ModelsCatalog {

    private val SAFE = listOf(
        WebModel("google/gemma-4-31b-it:free", "Google: Gemma 4 31B", free = true, nsfw = false, context = 262_144),
        WebModel("google/gemma-4-31b-it", "Google: Gemma 4 31B", free = false, nsfw = false, context = 262_144),
        WebModel("google/gemma-4-26b-a4b-it:free", "Google: Gemma 4 26B A4B", free = true, nsfw = false, context = 262_144),
        WebModel("google/gemma-4-26b-a4b-it", "Google: Gemma 4 26B A4B", free = false, nsfw = false, context = 262_144),
        WebModel("z-ai/glm-5.2:free", "Z.ai: GLM 5.2", free = true, nsfw = false, context = 256_000),
        WebModel("z-ai/glm-5.2", "Z.ai: GLM 5.2", free = false, nsfw = false, context = 1_048_576),
        WebModel("nvidia/nemotron-3-super-120b-a12b:free", "NVIDIA: Nemotron 3 Super", free = true, nsfw = false, context = 262_144),
        WebModel("nvidia/nemotron-3.5-lightning:free", "NVIDIA: Nemotron 3.5 Lightning", free = true, nsfw = false, context = 1_000_000),
        WebModel("nvidia/nemotron-3-ultra-550b-a55b:free", "NVIDIA: Nemotron 3 Ultra", free = true, nsfw = false, context = 1_000_000),
        WebModel("nvidia/nemotron-3-nano-30b-a3b:free", "NVIDIA: Nemotron 3 Nano 30B A3B", free = true, nsfw = false, context = 256_000),
        WebModel("nvidia/nemotron-nano-9b-v2:free", "NVIDIA: Nemotron Nano 9B V2", free = true, nsfw = false, context = 128_000),
        WebModel("openai/gpt-oss-20b:free", "OpenAI: gpt-oss-20b", free = true, nsfw = false, context = 131_072),
        WebModel("openai/gpt-oss-20b", "OpenAI: gpt-oss-20b", free = false, nsfw = false, context = 131_072)
    )

    private val NSFW = listOf(
        WebModel("cognitivecomputations/dolphin-mistral-24b-venice-edition", "Venice: Uncensored", free = false, nsfw = true, context = 128_000),
        WebModel("sao10k/l3.3-euryale-70b", "Sao10K: Llama 3.3 Euryale 70B", free = false, nsfw = true, context = 131_072),
        WebModel("sao10k/l3.1-euryale-70b", "Sao10K: Llama 3.1 Euryale 70B v2.2", free = false, nsfw = true, context = 131_072),
        WebModel("sao10k/l3-lunaris-8b", "Sao10K: Llama 3 8B Lunaris", free = false, nsfw = true, context = 8_192),
        WebModel("gryphe/mythomax-l2-13b", "MythoMax 13B", free = false, nsfw = true, context = 8_192),
        WebModel("undi95/remm-slerp-l2-13b", "ReMM SLERP 13B", free = false, nsfw = true, context = 6_144),
        WebModel("thedrummer/rocinante-12b", "TheDrummer: Rocinante 12B", free = false, nsfw = true, context = 65_536),
        WebModel("thedrummer/skyfall-36b-v2", "TheDrummer: Skyfall 36B V2", free = false, nsfw = true, context = 32_768),
        WebModel("thedrummer/unslopnemo-12b", "TheDrummer: UnslopNemo 12B", free = false, nsfw = true, context = 1_024_000),
        WebModel("anthracite-org/magnum-v4-72b", "Magnum v4 72B", free = false, nsfw = true, context = 32_768)
    )

    val all: List<WebModel> = SAFE + NSFW

    fun forFilter(nsfwEnabled: Boolean): List<WebModel> =
        all.filter { it.nsfw == nsfwEnabled }

    fun find(id: String): WebModel? = all.firstOrNull { it.id == id }

    fun displayName(id: String): String {
        if (id.isBlank()) return "Default model"
        return find(id)?.display ?: id
    }
}