package com.example.data

enum class ModelType {
    CODER,
    REASONING_LLM,
    CHAT_LLM
}

/**
 * Suggested cooperative role for dual-model "teamwork".
 * DRAFTER: fast, small — produces a first answer.
 * REASONER: stronger — reviews/refines the draft.
 */
enum class TeamRole {
    DRAFTER,
    REASONER,
    GENERAL
}

/**
 * Role of a line in the unified chat surface.
 * Only USER and ASSISTANT messages are persisted; SYSTEM notices are
 * session-only (init/security/inference logs) and never stored.
 */
enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * A real, downloadable on-device model. [downloadUrl] points at a genuine
 * GGUF file on Hugging Face; [fileSizeBytes] is the verified content length so
 * download progress and storage checks are accurate.
 */
data class AiModel(
    val id: String,
    val name: String,
    val developer: String,
    val size: String,
    val type: ModelType,
    val paramSizeGb: Double,
    val minRamGb: Int,
    var isDownloaded: Boolean,
    var isRunning: Boolean,
    val description: String,
    val cpuTokensPerSec: Double,
    val gpuTokensPerSec: Double,
    val accuracyScore: Int,
    val reasoningScore: Int,
    val energyEfficiency: String,
    // ---- Real download / runtime metadata ----
    val repoId: String = "",
    val fileName: String = "",
    val downloadUrl: String = "",
    val fileSizeBytes: Long = 0L,
    val quant: String = "Q4_K_M",
    val contextLength: Int = 2048,
    val teamRole: TeamRole = TeamRole.GENERAL
) {
    val fileSizeGb: Double get() = fileSizeBytes / (1024.0 * 1024.0 * 1024.0)

    val sizeLabel: String
        get() = when {
            fileSizeBytes <= 0 -> size
            fileSizeBytes >= 1_000_000_000L -> String.format("%.2f GB", fileSizeBytes / 1.0e9)
            else -> String.format("%d MB", fileSizeBytes / 1_000_000L)
        }
}

object ModelUniverse {

    private const val HF = "https://huggingface.co"

    private fun hf(repo: String, file: String) = "$HF/$repo/resolve/main/$file"

    /**
     * Real, verified GGUF models (Q4_K_M) ordered from lightest to heaviest.
     * Every URL was confirmed to resolve with the stated byte size.
     */
    val models = listOf(
        AiModel(
            id = "smollm2_135m",
            name = "SmolLM2 135M Instruct",
            developer = "HuggingFaceTB",
            size = "135 Million",
            type = ModelType.CHAT_LLM,
            paramSizeGb = 0.1,
            minRamGb = 1,
            isDownloaded = false,
            isRunning = false,
            description = "Ultra-light chat model. Loads in seconds even on low-RAM phones; ideal as a fast drafter or for constrained devices.",
            cpuTokensPerSec = 35.0,
            gpuTokensPerSec = 60.0,
            accuracyScore = 60,
            reasoningScore = 52,
            energyEfficiency = "A++",
            repoId = "bartowski/SmolLM2-135M-Instruct-GGUF",
            fileName = "SmolLM2-135M-Instruct-Q4_K_M.gguf",
            downloadUrl = hf("bartowski/SmolLM2-135M-Instruct-GGUF", "SmolLM2-135M-Instruct-Q4_K_M.gguf"),
            fileSizeBytes = 105_454_432L,
            contextLength = 2048,
            teamRole = TeamRole.DRAFTER
        ),
        AiModel(
            id = "smollm2_360m",
            name = "SmolLM2 360M Instruct",
            developer = "HuggingFaceTB",
            size = "360 Million",
            type = ModelType.CHAT_LLM,
            paramSizeGb = 0.27,
            minRamGb = 2,
            isDownloaded = false,
            isRunning = false,
            description = "Compact, well-rounded chat model. A good balance of speed and quality for everyday on-device use.",
            cpuTokensPerSec = 22.0,
            gpuTokensPerSec = 48.0,
            accuracyScore = 68,
            reasoningScore = 60,
            energyEfficiency = "A++",
            repoId = "bartowski/SmolLM2-360M-Instruct-GGUF",
            fileName = "SmolLM2-360M-Instruct-Q4_K_M.gguf",
            downloadUrl = hf("bartowski/SmolLM2-360M-Instruct-GGUF", "SmolLM2-360M-Instruct-Q4_K_M.gguf"),
            fileSizeBytes = 270_590_880L,
            contextLength = 2048,
            teamRole = TeamRole.DRAFTER
        ),
        AiModel(
            id = "qwen2_5_0_5b",
            name = "Qwen2.5 0.5B Instruct",
            developer = "Alibaba Qwen",
            size = "0.5 Billion",
            type = ModelType.CHAT_LLM,
            paramSizeGb = 0.4,
            minRamGb = 2,
            isDownloaded = false,
            isRunning = false,
            description = "Strong tiny instruct model with solid multilingual ability. Great default drafter on mid-range devices.",
            cpuTokensPerSec = 18.0,
            gpuTokensPerSec = 42.0,
            accuracyScore = 72,
            reasoningScore = 66,
            energyEfficiency = "A+",
            repoId = "bartowski/Qwen2.5-0.5B-Instruct-GGUF",
            fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
            downloadUrl = hf("bartowski/Qwen2.5-0.5B-Instruct-GGUF", "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf"),
            fileSizeBytes = 397_808_192L,
            contextLength = 4096,
            teamRole = TeamRole.DRAFTER
        ),
        AiModel(
            id = "llama3_2_1b",
            name = "Llama 3.2 1B Instruct",
            developer = "Meta AI",
            size = "1.0 Billion",
            type = ModelType.CHAT_LLM,
            paramSizeGb = 0.8,
            minRamGb = 3,
            isDownloaded = false,
            isRunning = false,
            description = "Meta's compact instruct model. Natural conversation and instruction-following with a small footprint.",
            cpuTokensPerSec = 12.0,
            gpuTokensPerSec = 34.0,
            accuracyScore = 78,
            reasoningScore = 72,
            energyEfficiency = "A+",
            repoId = "bartowski/Llama-3.2-1B-Instruct-GGUF",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            downloadUrl = hf("bartowski/Llama-3.2-1B-Instruct-GGUF", "Llama-3.2-1B-Instruct-Q4_K_M.gguf"),
            fileSizeBytes = 807_694_464L,
            contextLength = 4096,
            teamRole = TeamRole.GENERAL
        ),
        AiModel(
            id = "qwen2_5_1_5b",
            name = "Qwen2.5 1.5B Instruct",
            developer = "Alibaba Qwen",
            size = "1.5 Billion",
            type = ModelType.REASONING_LLM,
            paramSizeGb = 1.0,
            minRamGb = 4,
            isDownloaded = false,
            isRunning = false,
            description = "Capable reasoning + chat model. A strong on-device 'reasoner' to review and refine drafts in teamwork mode.",
            cpuTokensPerSec = 9.0,
            gpuTokensPerSec = 28.0,
            accuracyScore = 84,
            reasoningScore = 82,
            energyEfficiency = "A",
            repoId = "bartowski/Qwen2.5-1.5B-Instruct-GGUF",
            fileName = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            downloadUrl = hf("bartowski/Qwen2.5-1.5B-Instruct-GGUF", "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"),
            fileSizeBytes = 986_048_768L,
            contextLength = 4096,
            teamRole = TeamRole.REASONER
        ),
        AiModel(
            id = "qwen2_5_coder_1_5b",
            name = "Qwen2.5 Coder 1.5B",
            developer = "Alibaba Qwen",
            size = "1.5 Billion",
            type = ModelType.CODER,
            paramSizeGb = 1.0,
            minRamGb = 4,
            isDownloaded = false,
            isRunning = false,
            description = "Specialized coding model. Writes and explains code, shell commands, and structured output.",
            cpuTokensPerSec = 9.0,
            gpuTokensPerSec = 28.0,
            accuracyScore = 83,
            reasoningScore = 78,
            energyEfficiency = "A",
            repoId = "bartowski/Qwen2.5-Coder-1.5B-Instruct-GGUF",
            fileName = "Qwen2.5-Coder-1.5B-Instruct-Q4_K_M.gguf",
            downloadUrl = hf("bartowski/Qwen2.5-Coder-1.5B-Instruct-GGUF", "Qwen2.5-Coder-1.5B-Instruct-Q4_K_M.gguf"),
            fileSizeBytes = 986_048_800L,
            contextLength = 4096,
            teamRole = TeamRole.REASONER
        ),
        AiModel(
            id = "llama3_2_3b",
            name = "Llama 3.2 3B Instruct",
            developer = "Meta AI",
            size = "3.0 Billion",
            type = ModelType.REASONING_LLM,
            paramSizeGb = 2.0,
            minRamGb = 6,
            isDownloaded = false,
            isRunning = false,
            description = "Meta's larger compact model — the strongest reasoner here. Best quality on 6GB+ devices; use as the teamwork reasoner.",
            cpuTokensPerSec = 5.0,
            gpuTokensPerSec = 18.0,
            accuracyScore = 89,
            reasoningScore = 86,
            energyEfficiency = "B+",
            repoId = "bartowski/Llama-3.2-3B-Instruct-GGUF",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            downloadUrl = hf("bartowski/Llama-3.2-3B-Instruct-GGUF", "Llama-3.2-3B-Instruct-Q4_K_M.gguf"),
            fileSizeBytes = 2_019_377_696L,
            contextLength = 4096,
            teamRole = TeamRole.REASONER
        )
    )

    fun byId(id: String): AiModel? = models.firstOrNull { it.id == id }
}
