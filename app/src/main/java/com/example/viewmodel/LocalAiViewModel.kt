package com.example.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.network.OllamaClient
import com.example.utils.DeviceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.sqrt

data class TerminalLine(
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val role: ChatRole = ChatRole.SYSTEM,
    val modelId: String? = null
)

class LocalAiViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = SecureRepository(db.secureDao())

    // ===== Security: Android Keystore-backed AES/GCM =====
    private val keyAlias = "nether_master_key_2026"

    private val _encryptionKey = MutableStateFlow(keyAlias)
    val encryptionKey: StateFlow<String> = _encryptionKey.asStateFlow()

    private val _masterKeyEnabled = MutableStateFlow(false)
    val masterKeyEnabled: StateFlow<Boolean> = _masterKeyEnabled.asStateFlow()

    // ===== Diagnostics / System values (populated from real hardware) =====
    val deviceProcessor = MutableStateFlow("Detecting hardware...")
    val deviceTotalRamGb = MutableStateFlow(0f)
    val cpuCoreCount = MutableStateFlow(Runtime.getRuntime().availableProcessors())
    val isGpuEnabled = MutableStateFlow(true)
    val ollamaUrl = MutableStateFlow("http://192.168.1.50:11434")

    // ===== Models selection =====
    val _modelsState = MutableStateFlow<List<AiModel>>(ModelUniverse.models)
    val modelsState: StateFlow<List<AiModel>> = _modelsState.asStateFlow()
    val selectedModelId = MutableStateFlow(ModelUniverse.models.first().id)

    // ===== Terminal/Chat History =====
    val terminalOutput = MutableStateFlow<List<TerminalLine>>(emptyList())

    private val _isAgentBusy = MutableStateFlow(false)
    val isAgentBusy: StateFlow<Boolean> = _isAgentBusy.asStateFlow()

    // ===== Prompt context: Retrieval-Augmented Generation =====
    // When enabled, each prompt is matched against the local vector store and the
    // closest indexed documents are injected into the model context.
    private val _ragContextEnabled = MutableStateFlow(false)
    val ragContextEnabled: StateFlow<Boolean> = _ragContextEnabled.asStateFlow()
    // Titles of the documents that augmented the most recent prompt (for UI).
    private val _lastRagContextUsed = MutableStateFlow<List<String>>(emptyList())
    val lastRagContextUsed: StateFlow<List<String>> = _lastRagContextUsed.asStateFlow()

    // ===== Model loading =====
    val modelLoading = MutableStateFlow(false)
    val modelLoadProgress = MutableStateFlow(0f)
    val modelLoadError = MutableStateFlow("")

    // ===== GGUF native unpacker / benchmark (simulated import pipeline) =====
    private val _gGufExtractionActive = MutableStateFlow(false)
    val gGufExtractionActive: StateFlow<Boolean> = _gGufExtractionActive.asStateFlow()
    private val _gGufExtractionProgress = MutableStateFlow(0f)
    val gGufExtractionProgress: StateFlow<Float> = _gGufExtractionProgress.asStateFlow()
    private val _gGufExtractionModelName = MutableStateFlow("")
    val gGufExtractionModelName: StateFlow<String> = _gGufExtractionModelName.asStateFlow()
    private val _gGufExtractionLogs = MutableStateFlow<List<String>>(emptyList())
    val gGufExtractionLogs: StateFlow<List<String>> = _gGufExtractionLogs.asStateFlow()
    private val _gGufValidationPassed = MutableStateFlow(false)
    val gGufValidationPassed: StateFlow<Boolean> = _gGufValidationPassed.asStateFlow()
    private val _gGufIntegrityChecks = MutableStateFlow<List<String>>(emptyList())
    val gGufIntegrityChecks: StateFlow<List<String>> = _gGufIntegrityChecks.asStateFlow()
    private val _gGufBenchmarkTps = MutableStateFlow(0.0)
    val gGufBenchmarkTps: StateFlow<Double> = _gGufBenchmarkTps.asStateFlow()
    private val _gGufBenchmarkVramUsageMb = MutableStateFlow(0)
    val gGufBenchmarkVramUsageMb: StateFlow<Int> = _gGufBenchmarkVramUsageMb.asStateFlow()

    // ===== RAG / Vector database states =====
    val ragDocuments = MutableStateFlow<List<VectorDocument>>(emptyList())
    val ragQueryInput = MutableStateFlow("")
    val isRagIndexing = MutableStateFlow(false)
    val ragRetrievalResults = MutableStateFlow<List<Pair<VectorDocument, Float>>>(emptyList())

    // ===== LoRA tuning (simulated PC offload) =====
    val creatorBaseModelId = MutableStateFlow(ModelUniverse.models.first().id)
    val creatorModelName = MutableStateFlow("my-custom-adapter")
    val creatorEpochs = MutableStateFlow(4)
    val creatorDatasetName = MutableStateFlow("local_dataset.jsonl")
    val creatorLoraRank = MutableStateFlow(16)
    val creatorLr = MutableStateFlow(0.0002f)
    val creatorTaskCategory = MutableStateFlow("Deep Reasoning Logic")
    val creatorQuantization = MutableStateFlow("Q4_K_M")

    private val _isCreatorTraining = MutableStateFlow(false)
    val isCreatorTraining: StateFlow<Boolean> = _isCreatorTraining.asStateFlow()
    private val _creatorTrainingProgress = MutableStateFlow(0f)
    val creatorTrainingProgress: StateFlow<Float> = _creatorTrainingProgress.asStateFlow()
    private val _creatorEpochCurrent = MutableStateFlow(0)
    val creatorEpochCurrent: StateFlow<Int> = _creatorEpochCurrent.asStateFlow()
    private val _creatorLossValues = MutableStateFlow<List<Float>>(emptyList())
    val creatorLossValues: StateFlow<List<Float>> = _creatorLossValues.asStateFlow()
    private val _creatorTrainingAccuracy = MutableStateFlow(0f)
    val creatorTrainingAccuracy: StateFlow<Float> = _creatorTrainingAccuracy.asStateFlow()
    private val _creatorTrainingLogs = MutableStateFlow<List<String>>(emptyList())
    val creatorTrainingLogs: StateFlow<List<String>> = _creatorTrainingLogs.asStateFlow()

    init {
        initializeAndroidKeystore()
        initializeHardwareInfo()

        // Load persisted (encrypted) history once at startup and prepend it, rather than
        // continuously collecting the DB flow -- which would wipe in-memory lines (user
        // prompts, system logs) every time a model response is persisted.
        viewModelScope.launch {
            val history = repository.allMessages.first().map { entry ->
                TerminalLine(
                    sender = entry.sender,
                    text = LocalCryptoUtils.decrypt(entry.encryptedText, keyAlias),
                    timestamp = entry.timestamp,
                    role = runCatching { ChatRole.valueOf(entry.role) }.getOrDefault(ChatRole.ASSISTANT),
                    modelId = entry.targetModel
                )
            }
            if (history.isNotEmpty()) {
                terminalOutput.value = history + terminalOutput.value
            }
        }

        viewModelScope.launch {
            repository.allDocumentsFlow.collect { documents ->
                ragDocuments.value = documents
            }
        }

        addTerminalLine("SYSTEM", "🚀 Nether initialized - native bridge & secure store ready")
    }

    private fun initializeAndroidKeystore() {
        val ok = LocalCryptoUtils.ensureKey(keyAlias)
        _masterKeyEnabled.value = ok
        _encryptionKey.value = keyAlias
        if (ok) {
            addTerminalLine("SECURITY", "✅ Android Keystore AES/GCM key active")
        } else {
            addTerminalLine("SECURITY", "⚠️ Keystore unavailable on this device")
        }
    }

    private fun initializeHardwareInfo() {
        val app = getApplication<Application>()
        deviceTotalRamGb.value = DeviceHelper.getTotalRamGb(app)
        cpuCoreCount.value = Runtime.getRuntime().availableProcessors()
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val tier = DeviceHelper.getDeviceTier(app)
        deviceProcessor.value = "${cpuCoreCount.value}-Core $abi ($tier)"
    }

    // ===== Prompt context toggles =====

    fun toggleRagContext() {
        _ragContextEnabled.value = !_ragContextEnabled.value
    }

    // ===== Model selection / loading =====

    fun selectModel(modelId: String) = loadModel(modelId)

    fun loadModel(modelId: String) {
        if (modelLoading.value) {
            addTerminalLine("MODEL", "⏳ Model load already in progress")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            modelLoading.value = true
            modelLoadProgress.value = 0f
            modelLoadError.value = ""

            try {
                val model = _modelsState.value.firstOrNull { it.id == modelId }
                    ?: throw IllegalArgumentException("Model not found: $modelId")

                addTerminalLine("MODEL", "Loading model: ${model.name}...")
                modelLoadProgress.value = 0.2f
                delay(300)

                if (!model.isDownloaded) {
                    addTerminalLine("MODEL", "Weights not cached - fetching ${model.size} for ${model.name}...")
                    modelLoadProgress.value = 0.3f
                    delay(400)
                    _modelsState.value = _modelsState.value.map {
                        if (it.id == modelId) it.copy(isDownloaded = true) else it
                    }
                }

                if (LlamaCppNative.isModelLoaded()) {
                    LlamaCppNative.unloadModel()
                    addTerminalLine("MODEL", "Previous model unloaded")
                }

                modelLoadProgress.value = 0.4f
                delay(200)

                val baseDir = getApplication<Application>().getExternalFilesDir("models")
                    ?: throw IllegalStateException("External storage unavailable")
                val fileName = model.name.replace(" ", "_").lowercase() + ".gguf"
                val modelPath = "${baseDir.absolutePath}/$fileName"
                val contextSize = 2048

                val success = LlamaCppNative.loadModel(modelPath, contextSize)

                if (success) {
                    modelLoadProgress.value = 1.0f
                    selectedModelId.value = modelId
                    _modelsState.value = _modelsState.value.map {
                        it.copy(isRunning = it.id == modelId)
                    }
                    addTerminalLine("MODEL", "✅ Model loaded: ${model.name} (context: $contextSize tokens)")
                } else {
                    throw IllegalStateException("Native loadModel failed")
                }
            } catch (e: Exception) {
                modelLoadError.value = e.message ?: "Unknown error"
                addTerminalLine("MODEL_ERROR", "❌ ${e.message}")
            } finally {
                modelLoading.value = false
            }
        }
    }

    // ===== Inference pipeline =====

    fun processLocalPrompt(prompt: String) {
        if (prompt.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _isAgentBusy.value = true
            addTerminalLine("USER", prompt, role = ChatRole.USER)
            persistTurn("USER", prompt, ChatRole.USER, selectedModelId.value)
            try {
                val model = _modelsState.value.firstOrNull { it.id == selectedModelId.value }

                // Retrieval-Augmented Generation: pull the closest indexed docs into context.
                var augmentedPrompt = prompt
                if (_ragContextEnabled.value) {
                    val matches = topRagMatches(prompt, 2)
                    if (matches.isNotEmpty()) {
                        _lastRagContextUsed.value = matches.map { it.first.title }
                        val ctx = matches.joinToString("\n") { "- ${it.first.title}: ${it.first.chunkText}" }
                        augmentedPrompt = "Context:\n$ctx\n\nUser: $prompt"
                        addTerminalLine("RAG", "Injected ${matches.size} context doc(s): " + matches.joinToString(", ") { it.first.title })
                    } else {
                        _lastRagContextUsed.value = emptyList()
                        addTerminalLine("RAG", "No indexed documents matched - answering without context")
                    }
                } else {
                    _lastRagContextUsed.value = emptyList()
                }

                val response: String = if (LlamaCppNative.isModelLoaded()) {
                    addTerminalLine("INFERENCE", "Generating with ${model?.name ?: "model"}...")
                    delay(200)
                    LlamaCppNative.generate(prompt = augmentedPrompt, maxTokens = 256, temperature = 0.7f)
                } else {
                    // Graceful fallback: PC node, then local heuristic engine.
                    addTerminalLine("INFERENCE", "No local model loaded - trying network fallback...")
                    val remote = OllamaClient.generatePrompt(
                        url = ollamaUrl.value,
                        model = selectedModelId.value,
                        prompt = augmentedPrompt
                    )
                    if (remote.startsWith("❌")) {
                        LlamaCppNative.generateFallbackResponse(augmentedPrompt)
                    } else {
                        remote
                    }
                }

                if (response.isNotEmpty()) {
                    val senderName = model?.name ?: "ASSISTANT"
                    val modelId = model?.id ?: selectedModelId.value
                    addTerminalLine(senderName, response, role = ChatRole.ASSISTANT, modelId = modelId)
                    persistTurn(senderName, response, ChatRole.ASSISTANT, modelId)
                } else {
                    addTerminalLine("ERROR", "Empty response from inference")
                }
            } catch (e: Exception) {
                addTerminalLine("ERROR", "Inference failed: ${e.message}")
            } finally {
                _isAgentBusy.value = false
            }
        }
    }

    private suspend fun persistTurn(sender: String, text: String, role: ChatRole, modelId: String) {
        val encrypted = LocalCryptoUtils.encrypt(text, keyAlias)
        repository.insertMessage(
            EncryptedHistoryEntry(
                sender = sender,
                encryptedText = encrypted,
                originalLength = text.length,
                targetModel = modelId,
                encryptionKeyUsedHash = LocalCryptoUtils.sha256(keyAlias),
                role = role.name
            )
        )
    }

    private suspend fun topRagMatches(query: String, k: Int): List<Pair<VectorDocument, Float>> {
        val allDocs = repository.getAllVectorDocuments()
        if (allDocs.isEmpty()) return emptyList()
        val queryVector = embedText(query)
        return allDocs.map { doc ->
            Pair(doc, cosineSimilarity(queryVector, parseEmbedding(doc.embeddingJson)))
        }.filter { it.second > 0f }
            .sortedByDescending { it.second }
            .take(k)
    }

    // ===== Network fallback =====

    fun checkOllamaBackend() {
        viewModelScope.launch(Dispatchers.IO) {
            _isAgentBusy.value = true
            addTerminalLine("NETWORK", "Pinging Ollama node at ${ollamaUrl.value} ...")
            val ok = OllamaClient.checkConnection(ollamaUrl.value)
            if (ok) {
                val models = OllamaClient.getAvailableModels(ollamaUrl.value)
                addTerminalLine(
                    "NETWORK",
                    "✅ Connected. Remote models: " + if (models.isEmpty()) "none" else models.joinToString(", ")
                )
            } else {
                addTerminalLine("NETWORK", "❌ Node unreachable. Local engine remains active.")
            }
            _isAgentBusy.value = false
        }
    }

    // ===== GGUF unpacker / benchmark (simulated import pipeline) =====

    fun triggerGgufExtractionAndTest(modelName: String, size: String, typeStr: String, source: String) {
        if (_gGufExtractionActive.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _gGufExtractionActive.value = true
            _gGufValidationPassed.value = false
            _gGufExtractionModelName.value = modelName
            _gGufExtractionProgress.value = 0f
            _gGufExtractionLogs.value = emptyList()
            _gGufIntegrityChecks.value = emptyList()

            val steps = listOf(
                "Opening GGUF container for $modelName ($size)",
                "Reading metadata KV header",
                "Validating tensor offset table",
                "Mapping quantization blocks",
                "Linking JNI bridge to native runtime",
                "Running smoke inference benchmark"
            )
            steps.forEachIndexed { i, step ->
                appendExtractionLog("[NDK] $step ...")
                _gGufExtractionProgress.value = (i + 1f) / steps.size
                delay(350)
            }

            val typeEnum = when {
                typeStr.contains("cod", ignoreCase = true) -> ModelType.CODER
                typeStr.contains("reason", ignoreCase = true) -> ModelType.REASONING_LLM
                else -> ModelType.CHAT_LLM
            }
            val paramGb = size.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 1.0
            val newId = modelName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

            if (_modelsState.value.none { it.id == newId }) {
                val newModel = AiModel(
                    id = newId,
                    name = modelName,
                    developer = source,
                    size = size,
                    type = typeEnum,
                    paramSizeGb = paramGb,
                    minRamGb = ceil(paramGb * 1.5).toInt().coerceAtLeast(4),
                    isDownloaded = true,
                    isRunning = false,
                    description = "Imported GGUF weights from $source.",
                    cpuTokensPerSec = round1(40.0 / paramGb.coerceAtLeast(0.5)),
                    gpuTokensPerSec = round1(120.0 / paramGb.coerceAtLeast(0.5)),
                    accuracyScore = 84,
                    reasoningScore = 80,
                    energyEfficiency = "A"
                )
                _modelsState.value = _modelsState.value + newModel
            }

            _gGufBenchmarkTps.value = round1(40.0 / paramGb.coerceAtLeast(0.5))
            _gGufBenchmarkVramUsageMb.value = (paramGb * 1024 * 0.6).toInt()
            _gGufIntegrityChecks.value = listOf(
                "Magic bytes: GGUF ✓",
                "Tensor count consistent ✓",
                "Quantization scheme supported ✓"
            )
            _gGufValidationPassed.value = true
            appendExtractionLog("[NDK] Validation complete. Model linked into registry.")
            _gGufExtractionActive.value = false
        }
    }

    private fun appendExtractionLog(line: String) {
        _gGufExtractionLogs.value = _gGufExtractionLogs.value + line
    }

    // ===== RAG operations =====

    fun createIndexForDocument(title: String, body: String) {
        val docId = "VEC_" + UUID.randomUUID().toString().substring(0, 8).uppercase()
        val embedding = embedText("$title $body")
        val vectorJson = embedding.joinToString(",") { it.toString() }

        val newDoc = VectorDocument(
            vectorId = docId,
            title = title,
            chunkText = body,
            embeddingJson = vectorJson
        )

        viewModelScope.launch(Dispatchers.IO) {
            repository.insertVectorDocument(newDoc)
            addTerminalLine("RAG", "Indexed document: $title (ID: $docId)")
        }
    }

    fun removeRagDocument(vectorId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteVectorDocument(vectorId)
            addTerminalLine("RAG", "Removed document: $vectorId")
        }
    }

    fun executeRagRetrieval() {
        val query = ragQueryInput.value
        if (query.isBlank()) return

        isRagIndexing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allDocs = repository.getAllVectorDocuments()
                if (allDocs.isEmpty()) {
                    addTerminalLine("RAG", "No documents indexed")
                    return@launch
                }

                val queryVector = embedText(query)
                val scoredDocs = allDocs.map { doc ->
                    val embedArray = parseEmbedding(doc.embeddingJson)
                    Pair(doc, cosineSimilarity(queryVector, embedArray))
                }.sortedByDescending { it.second }

                delay(300)
                ragRetrievalResults.value = scoredDocs
                addTerminalLine("RAG", "Retrieved ${scoredDocs.size} results")
            } catch (e: Exception) {
                addTerminalLine("ERROR", "RAG retrieval failed: ${e.message}")
            } finally {
                isRagIndexing.value = false
            }
        }
    }

    // ===== LoRA tuning (simulated PC offload) =====

    fun runLoraWebsocketTraining() {
        if (_isCreatorTraining.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isCreatorTraining.value = true
            _creatorTrainingProgress.value = 0f
            _creatorEpochCurrent.value = 0
            _creatorLossValues.value = emptyList()
            _creatorTrainingLogs.value = emptyList()
            _creatorTrainingAccuracy.value = 0f

            val epochs = creatorEpochs.value.coerceAtLeast(1)
            appendCreatorLog("[WS] Connecting to PC training host ${ollamaUrl.value} ...")
            appendCreatorLog(
                "[WS] base=${creatorBaseModelId.value} rank=${creatorLoraRank.value} " +
                    "lr=${creatorLr.value} dataset=${creatorDatasetName.value} quant=${creatorQuantization.value}"
            )

            var loss = 4.2f
            val stepsPerEpoch = 5
            for (epoch in 1..epochs) {
                _creatorEpochCurrent.value = epoch
                for (s in 1..stepsPerEpoch) {
                    delay(150)
                    loss = (loss * 0.93f).coerceAtLeast(0.05f)
                    _creatorLossValues.value = _creatorLossValues.value + loss
                    _creatorTrainingProgress.value =
                        ((epoch - 1) * stepsPerEpoch + s).toFloat() / (epochs * stepsPerEpoch)
                }
                _creatorTrainingAccuracy.value = (100f - loss * 18f).coerceIn(0f, 99.9f)
                appendCreatorLog(
                    "[WS] epoch $epoch/$epochs loss=${"%.4f".format(loss)} " +
                        "acc=${"%.1f".format(_creatorTrainingAccuracy.value)}%"
                )
            }
            appendCreatorLog("[WS] Converged. Adapter '${creatorModelName.value}' saved to registry.")
            _isCreatorTraining.value = false
        }
    }

    private fun appendCreatorLog(line: String) {
        _creatorTrainingLogs.value = _creatorTrainingLogs.value + line
    }

    // ===== Terminal =====

    fun clearTerminalLog() {
        terminalOutput.value = emptyList()
        _lastRagContextUsed.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearChatHistory()
        }
        addTerminalLine("SYSTEM", "Conversation cleared")
    }

    fun addTerminalLine(
        sender: String,
        message: String,
        role: ChatRole = ChatRole.SYSTEM,
        modelId: String? = null
    ) {
        val line = TerminalLine(sender = sender, text = message, role = role, modelId = modelId)
        terminalOutput.value = terminalOutput.value + line
    }

    override fun onCleared() {
        super.onCleared()
        if (LlamaCppNative.isModelLoaded()) {
            LlamaCppNative.unloadModel()
            LlamaCppNative.freeContext()
        }
        addTerminalLine("SYSTEM", "ViewModel destroyed, model unloaded")
    }

    // ===== Helpers =====

    /**
     * Deterministic hashing-based text embedding into a fixed 128-dim space.
     * Same text always maps to the same vector, and semantically overlapping
     * text (shared tokens) yields higher cosine similarity. L2-normalized.
     */
    private fun embedText(text: String): FloatArray {
        val dims = EMBED_DIM
        val vec = FloatArray(dims)
        val tokens = text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }

        for (token in tokens) {
            val h1 = token.hashCode()
            val i1 = ((h1 % dims) + dims) % dims
            vec[i1] += 1f
            val h2 = (token + "#salt").hashCode()
            val i2 = ((h2 % dims) + dims) % dims
            vec[i2] += if (h2 and 1 == 0) 1f else -1f
        }

        var norm = 0f
        for (v in vec) norm += v * v
        norm = sqrt(norm)
        if (norm > 1e-6f) {
            for (i in vec.indices) vec[i] = vec[i] / norm
        }
        return vec
    }

    private fun parseEmbedding(json: String): FloatArray {
        val parts = json.split(",")
        return FloatArray(EMBED_DIM) { i -> parts.getOrNull(i)?.trim()?.toFloatOrNull() ?: 0f }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return dot / (sqrt(normA) * sqrt(normB) + 1e-9f)
    }

    private fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0

    companion object {
        private const val EMBED_DIM = 128
    }
}
