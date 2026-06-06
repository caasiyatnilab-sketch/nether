package com.example.data

enum class ModelType {
    CODER,
    REASONING_LLM,
    CHAT_LLM
}

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
    val energyEfficiency: String
)

object ModelUniverse {
    val models = listOf(
        AiModel(
            id = "deepseek_r1_1.5b",
            name = "DeepSeek R1 (1.5B)",
            developer = "DeepSeek AI",
            size = "1.5 Billion",
            type = ModelType.REASONING_LLM,
            paramSizeGb = 0.9,
            minRamGb = 4,
            isDownloaded = true,
            isRunning = true,
            description = "High-efficiency local deep reasoning model. Outstanding logic and math performance on compact devices.",
            cpuTokensPerSec = 14.2,
            gpuTokensPerSec = 38.5,
            accuracyScore = 88,
            reasoningScore = 91,
            energyEfficiency = "A++"
        ),
        AiModel(
            id = "llama3_8b_instruct",
            name = "Llama 3 (8B Instruct)",
            developer = "Meta AI",
            size = "8.0 Billion",
            type = ModelType.CHAT_LLM,
            paramSizeGb = 4.7,
            minRamGb = 8,
            isDownloaded = true,
            isRunning = false,
            description = "SOTA general instruction-following model. Highly verbose, natural conversations and creative outlines.",
            cpuTokensPerSec = 4.8,
            gpuTokensPerSec = 18.2,
            accuracyScore = 92,
            reasoningScore = 85,
            energyEfficiency = "A"
        ),
        AiModel(
            id = "phi3_3.8b_mini",
            name = "Phi-3 Mini (3.8B)",
            developer = "Microsoft",
            size = "3.8 Billion",
            type = ModelType.CHAT_LLM,
            paramSizeGb = 2.2,
            minRamGb = 6,
            isDownloaded = false,
            isRunning = false,
            description = "Extremely lightweight and fast model. Exceling in local tasks like summarization, entity extraction, and Q&A.",
            cpuTokensPerSec = 8.5,
            gpuTokensPerSec = 28.0,
            accuracyScore = 85,
            reasoningScore = 78,
            energyEfficiency = "A+"
        ),
        AiModel(
            id = "qwen2.5_coder_1.5b",
            name = "Qwen 2.5 Coder (1.5B)",
            developer = "Alibaba",
            size = "1.5 Billion",
            type = ModelType.CODER,
            paramSizeGb = 1.0,
            minRamGb = 4,
            isDownloaded = false,
            isRunning = false,
            description = "Specialized coding LLM. Formats elegant, bug-free, structured code snippets, scripting commands, and terminal syntax directly.",
            cpuTokensPerSec = 12.0,
            gpuTokensPerSec = 34.5,
            accuracyScore = 82,
            reasoningScore = 72,
            energyEfficiency = "A++"
        )
    )
}
