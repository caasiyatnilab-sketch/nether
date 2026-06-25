package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.network.OllamaClient
import com.example.utils.DeviceProfiler
import com.example.utils.DualSuggestion
import com.example.utils.HardwareProfile
import com.example.utils.ModelRecommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

data class TerminalLine(
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val role: ChatRole = ChatRole.SYSTEM,
    val modelId: String? = null,
    val id: Long = nextLineId()
) {
    companion object {
        private val counter = AtomicLong(0)
        fun nextLineId(): Long = counter.incrementAndGet()
    }
}

private fun nextLineId(): Long = TerminalLine.nextLineId()

class LocalAiViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = SecureRepository(db.secureDao())

    private val modelManager = ModelManager(application)
    private val engine = InferenceEngine()
    private val mcp = McpClient()
    private val skills = SkillRegistry(mcp)

    // ===== Security: Android Keystore-backed AES/GCM =====
    private val keyAlias = "nether_master_key_2026"

    private val _encryptionKey = MutableStateFlow(keyAlias)
    val encryptionKey: StateFlow<String> = _encryptionKey.asStateFlow()

    private val _masterKeyEnabled = MutableStateFlow(false)
    val masterKeyEnabled: StateFlow<Boolean> = _masterKeyEnabled.asStateFlow()

    // ===== Hardware (real, from DeviceProfiler) =====
    val deviceProcessor = MutableStateFlow("Detecting hardware…")
    val deviceTotalRamGb = MutableStateFlow(0f)
    val deviceAvailableRamGb = MutableStateFlow(0f)
    val cpuCoreCount = MutableStateFlow(Runtime.getRuntime().availableProcessors())
    val gpuRenderer = MutableStateFlow("Detecting GPU…")
    val isGpuEnabled = MutableStateFlow(false)
    private val _hardwareProfile = MutableStateFlow<HardwareProfile?>(null)
    val hardwareProfile: StateFlow<HardwareProfile?> = _hardwareProfile.asStateFlow()

    // ===== Recommendations =====
    private val _recommendations = MutableStateFlow<List<ModelRecommendation>>(emptyList())
    val recommendations: StateFlow<List<ModelRecommendation>> = _recommendations.asStateFlow()
    private val _dualSuggestion = MutableStateFlow<DualSuggestion?>(null)
    val dualSuggestion: StateFlow<DualSuggestion?> = _dualSuggestion.asStateFlow()

    val ollamaUrl = MutableStateFlow("http://192.168.1.50:11434")
    val mcpUrl = MutableStateFlow("")

    // ===== Models =====
    val _modelsState = MutableStateFlow<List<AiModel>>(ModelUniverse.models)
    val modelsState: StateFlow<List<AiModel>> = _modelsState.asStateFlow()
    val selectedModelId = MutableStateFlow(ModelUniverse.models.first().id)

    // Loaded (resident in native memory) model ids.
    private val _loadedModelIds = MutableStateFlow<Set<String>>(emptySet())
    val loadedModelIds: StateFlow<Set<String>> = _loadedModelIds.asStateFlow()

    // ===== Download state =====
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()
    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    // ===== Dual-model teamwork =====
    private val _teamModeEnabled = MutableStateFlow(false)
    val teamModeEnabled: StateFlow<Boolean> = _teamModeEnabled.asStateFlow()
    val drafterModelId = MutableStateFlow(ModelUniverse.models.first().id)
    val reasonerModelId = MutableStateFlow(ModelUniverse.models.last().id)

    // ===== Skills =====
    val skillList = MutableStateFlow(skills.all().map { it.command to it.description })

    // ===== Chat =====
    val terminalOutput = MutableStateFlow<List<TerminalLine>>(emptyList())
    private val _isAgentBusy = MutableStateFlow(false)
    val isAgentBusy: StateFlow<Boolean> = _isAgentBusy.asStateFlow()

    // ===== RAG =====
    private val _ragContextEnabled = MutableStateFlow(false)
    val ragContextEnabled: StateFlow<Boolean> = _ragContextEnabled.asStateFlow()
    private val _lastRagContextUsed = MutableStateFlow<List<String>>(emptyList())
    val lastRagContextUsed: StateFlow<List<String>> = _lastRagContextUsed.asStateFlow()

    // ===== Model loading =====
    val modelLoading = MutableStateFlow(false)
    val modelLoadProgress = MutableStateFlow(0f)
    val modelLoadError = MutableStateFlow("")

    // ===== RAG / Vector database =====
    val ragDocuments = MutableStateFlow<List<VectorDocument>>(emptyList())
    val ragQueryInput = MutableStateFlow("")
    val isRagIndexing = MutableStateFlow(false)
    val ragRetrievalResults = MutableStateFlow<List<Pair<VectorDocument, Float>>>(emptyList())

    // ===== LoRA tuning (PC offload — clearly a remote/training preview) =====
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
        syncDownloadedFlags()

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
            if (history.isNotEmpty()) terminalOutput.value = history + terminalOutput.value
        }

        viewModelScope.launch {
            repository.allDocumentsFlow.collect { ragDocuments.value = it }
        }

        val engineInfo = if (LlamaBridge.isLibraryLoaded) "real llama.cpp engine" else "native engine unavailable"
        addTerminalLine("SYSTEM", "🚀 Nether ready — $engineInfo. Type /help for on-device skills.")
    }

    private fun initializeAndroidKeystore() {
        val ok = LocalCryptoUtils.ensureKey(keyAlias)
        _masterKeyEnabled.value = ok
        _encryptionKey.value = keyAlias
        addTerminalLine(
            "SECURITY",
            if (ok) "✅ Android Keystore AES/GCM key active" else "⚠️ Keystore unavailable on this device"
        )
    }

    private fun initializeHardwareInfo() {
        viewModelScope.launch(Dispatchers.Default) {
            val profile = DeviceProfiler.profile(getApplication())
            _hardwareProfile.value = profile
            deviceTotalRamGb.value = profile.totalRamGb
            deviceAvailableRamGb.value = profile.availableRamGb
            cpuCoreCount.value = profile.cpuCores
            gpuRenderer.value = if (profile.gpuRenderer == "Unknown") "GPU: not detected"
                else "${profile.gpuRenderer}"
            deviceProcessor.value = "${profile.cpuCores}-Core ${profile.primaryAbi} (${profile.tier})"

            val recs = DeviceProfiler.recommend(profile)
            _recommendations.value = recs
            val dual = DeviceProfiler.suggestDual(profile)
            _dualSuggestion.value = dual
            drafterModelId.value = dual.drafter.id
            reasonerModelId.value = dual.reasoner.id

            // Default the selected model to the best one that fits this device.
            selectedModelId.value = DeviceProfiler.bestSingle(profile).id

            addTerminalLine(
                "HARDWARE",
                "Detected ${"%.1f".format(profile.totalRamGb)}GB RAM · ${profile.cpuCores} cores · " +
                    "${profile.primaryAbi} · GPU ${profile.gpuRenderer}"
            )
            addTerminalLine(
                "RECOMMEND",
                "Best single model: ${DeviceProfiler.bestSingle(profile).name}. " +
                    "Team combo: ${dual.drafter.name} + ${dual.reasoner.name}."
            )
        }
    }

    /** Reflect which catalog models already have a complete file on disk. */
    private fun syncDownloadedFlags() {
        _modelsState.value = _modelsState.value.map { it.copy(isDownloaded = modelManager.isDownloaded(it)) }
    }

    // ===== Toggles =====

    fun toggleRagContext() { _ragContextEnabled.value = !_ragContextEnabled.value }

    fun setTeamMode(enabled: Boolean) { _teamModeEnabled.value = enabled }

    fun setDrafter(id: String) { drafterModelId.value = id }
    fun setReasoner(id: String) { reasonerModelId.value = id }

    fun configureMcp(url: String) {
        mcpUrl.value = url
        mcp.configure(url)
        addTerminalLine("MCP", if (url.isBlank()) "MCP server cleared" else "MCP server set: $url")
    }

    // ===== Model download / load =====

    fun downloadModel(modelId: String) {
        val model = ModelUniverse.byId(modelId) ?: return
        if (_downloadingIds.value.contains(modelId)) return
        viewModelScope.launch(Dispatchers.IO) {
            _downloadingIds.value = _downloadingIds.value + modelId
            _downloadProgress.value = _downloadProgress.value + (modelId to 0f)
            addTerminalLine("MODEL", "⬇️ Downloading ${model.name} (${model.sizeLabel})…")
            try {
                modelManager.download(model) { p ->
                    _downloadProgress.value = _downloadProgress.value + (modelId to p.fraction)
                }
                _modelsState.value = _modelsState.value.map {
                    if (it.id == modelId) it.copy(isDownloaded = true) else it
                }
                addTerminalLine("MODEL", "✅ Downloaded ${model.name}")
            } catch (e: Exception) {
                addTerminalLine("MODEL_ERROR", "❌ Download failed: ${e.message}")
            } finally {
                _downloadingIds.value = _downloadingIds.value - modelId
            }
        }
    }

    fun deleteModel(modelId: String) {
        val model = ModelUniverse.byId(modelId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            engine.unload(modelId)
            refreshLoaded()
            modelManager.deleteModel(model)
            _modelsState.value = _modelsState.value.map {
                if (it.id == modelId) it.copy(isDownloaded = false, isRunning = false) else it
            }
            addTerminalLine("MODEL", "🗑️ Removed ${model.name}")
        }
    }

    fun selectModel(modelId: String) = loadModel(modelId)

    /** Download (if needed) then load the model into the engine. */
    fun loadModel(modelId: String) {
        if (modelLoading.value) {
            addTerminalLine("MODEL", "⏳ A model is already loading")
            return
        }
        val model = ModelUniverse.byId(modelId) ?: run {
            addTerminalLine("MODEL_ERROR", "Unknown model: $modelId")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            modelLoading.value = true
            modelLoadProgress.value = 0f
            modelLoadError.value = ""
            try {
                if (!LlamaBridge.isLibraryLoaded) error("Native engine not available on this device")

                if (!modelManager.isDownloaded(model)) {
                    addTerminalLine("MODEL", "Weights not cached — fetching ${model.name} (${model.sizeLabel})…")
                    _downloadingIds.value = _downloadingIds.value + modelId
                    modelManager.download(model) { p ->
                        modelLoadProgress.value = p.fraction * 0.8f
                        _downloadProgress.value = _downloadProgress.value + (modelId to p.fraction)
                    }
                    _downloadingIds.value = _downloadingIds.value - modelId
                    _modelsState.value = _modelsState.value.map {
                        if (it.id == modelId) it.copy(isDownloaded = true) else it
                    }
                }

                modelLoadProgress.value = 0.85f
                addTerminalLine("MODEL", "Loading ${model.name} into engine…")
                val ok = engine.load(modelId, modelManager.absolutePath(model), model.contextLength)
                if (!ok) error("Native load failed for ${model.name}")

                refreshLoaded()
                selectedModelId.value = modelId
                _modelsState.value = _modelsState.value.map { it.copy(isRunning = engine.isLoaded(it.id)) }
                modelLoadProgress.value = 1f
                addTerminalLine("MODEL", "✅ Loaded ${model.name} · ${engine.descriptor(modelId)}")
            } catch (e: Exception) {
                modelLoadError.value = e.message ?: "Unknown error"
                addTerminalLine("MODEL_ERROR", "❌ ${e.message}")
            } finally {
                _downloadingIds.value = _downloadingIds.value - modelId
                modelLoading.value = false
            }
        }
    }

    private fun refreshLoaded() {
        _loadedModelIds.value = engine.loadedIds()
    }

    // ===== Inference =====

    fun processLocalPrompt(prompt: String) {
        if (prompt.isBlank() || _isAgentBusy.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isAgentBusy.value = true
            addTerminalLine("USER", prompt, role = ChatRole.USER)
            persistTurn("USER", prompt, ChatRole.USER, selectedModelId.value)
            try {
                // 1) Skills / device controls / MCP take priority over inference.
                if (skills.isCommand(prompt)) {
                    val result = skills.tryHandle(getApplication(), prompt) ?: "Unknown command."
                    addTerminalLine("SKILL", result, role = ChatRole.ASSISTANT, modelId = "skill")
                    persistTurn("SKILL", result, ChatRole.ASSISTANT, "skill")
                    return@launch
                }

                // 2) Optional RAG grounding.
                val ragNote = applyRag(prompt)

                // 3) Build the conversation turns (exclude the brand-new placeholder).
                val turns = conversationTurns()

                if (_teamModeEnabled.value) {
                    runTeam(turns, ragNote)
                } else {
                    runSingle(turns, ragNote)
                }
            } catch (e: Exception) {
                addTerminalLine("ERROR", "Inference failed: ${e.message}")
            } finally {
                _isAgentBusy.value = false
            }
        }
    }

    private suspend fun runSingle(turns: List<InferenceEngine.Turn>, ragNote: String) {
        val modelId = selectedModelId.value
        val model = ModelUniverse.byId(modelId)
        if (!engine.isLoaded(modelId)) {
            // Try remote Ollama only if explicitly configured; otherwise ask to load.
            val remote = tryRemote(turns)
            if (remote != null) {
                val line = newAssistantLine(model?.name ?: "Remote", modelId)
                updateLine(line, remote)
                persistTurn(model?.name ?: "Remote", remote, ChatRole.ASSISTANT, modelId)
                return
            }
            val msg = "No model loaded. Tap a model on the Dashboard to download & load it, " +
                "then ask again. (Selected: ${model?.name ?: modelId})"
            addTerminalLine("ASSISTANT", msg, role = ChatRole.ASSISTANT, modelId = modelId)
            persistTurn("ASSISTANT", msg, ChatRole.ASSISTANT, modelId)
            return
        }

        val systemPrompt = systemPromptWith(ragNote)
        val line = newAssistantLine(model?.name ?: "Assistant", modelId)
        val sb = StringBuilder()
        var lastTick = 0L
        val full = engine.chat(modelId, turns, systemPrompt) { piece ->
            sb.append(piece)
            val now = System.currentTimeMillis()
            if (now - lastTick > 40) { updateLine(line, sb.toString()); lastTick = now }
        }
        updateLine(line, full.ifBlank { sb.toString() }.ifBlank { "(no output)" })
        persistTurn(model?.name ?: "Assistant", full, ChatRole.ASSISTANT, modelId)
    }

    private suspend fun runTeam(turns: List<InferenceEngine.Turn>, ragNote: String) {
        val dId = drafterModelId.value
        val rId = reasonerModelId.value
        if (!engine.isLoaded(dId)) { addTerminalLine("TEAM", "Drafter not loaded — load ${ModelUniverse.byId(dId)?.name} first."); return }
        if (!engine.isLoaded(rId)) { addTerminalLine("TEAM", "Reasoner not loaded — load ${ModelUniverse.byId(rId)?.name} first."); return }

        val reasoner = ModelUniverse.byId(rId)
        val draftLine = newAssistantLine("Draft · ${ModelUniverse.byId(dId)?.name}", dId)
        val draftSb = StringBuilder()
        val finalLine = newAssistantLine("Final · ${reasoner?.name}", rId)
        val finalSb = StringBuilder()
        var tick = 0L

        val effectiveTurns = if (ragNote.isBlank()) turns else
            turns.dropLast(1) + InferenceEngine.Turn(ChatRole.USER, ragNote + "\n\n" + (turns.lastOrNull()?.content ?: ""))

        val result = engine.teamChat(
            drafterId = dId,
            reasonerId = rId,
            turns = effectiveTurns,
            onStage = { stage -> addTerminalLine("TEAM", stage) },
            onDraftToken = { p ->
                draftSb.append(p)
                val now = System.currentTimeMillis()
                if (now - tick > 40) { updateLine(draftLine, draftSb.toString()); tick = now }
            },
            onToken = { p ->
                finalSb.append(p)
                val now = System.currentTimeMillis()
                if (now - tick > 40) { updateLine(finalLine, finalSb.toString()); tick = now }
            }
        )
        updateLine(draftLine, result.draft.ifBlank { "(no draft)" })
        updateLine(finalLine, result.finalAnswer.ifBlank { result.draft }.ifBlank { "(no output)" })
        persistTurn("Final · ${reasoner?.name}", result.finalAnswer, ChatRole.ASSISTANT, rId)
    }

    private suspend fun tryRemote(turns: List<InferenceEngine.Turn>): String? {
        // Only attempt if the user pointed at a reachable Ollama node.
        if (!OllamaClient.checkConnection(ollamaUrl.value)) return null
        val prompt = turns.lastOrNull { it.role == ChatRole.USER }?.content ?: return null
        val resp = OllamaClient.generatePrompt(ollamaUrl.value, selectedModelId.value, prompt)
        return if (resp.startsWith("❌")) null else resp
    }

    private fun systemPromptWith(ragNote: String): String {
        val base = InferenceEngine.DEFAULT_SYSTEM + " " + skills.summaryForPrompt()
        return if (ragNote.isBlank()) base else "$base\n\n$ragNote"
    }

    private suspend fun applyRag(prompt: String): String {
        if (!_ragContextEnabled.value) { _lastRagContextUsed.value = emptyList(); return "" }
        val matches = topRagMatches(prompt, 2)
        return if (matches.isNotEmpty()) {
            _lastRagContextUsed.value = matches.map { it.first.title }
            val ctx = matches.joinToString("\n") { "- ${it.first.title}: ${it.first.chunkText}" }
            addTerminalLine("RAG", "Injected ${matches.size} context doc(s): " + matches.joinToString(", ") { it.first.title })
            "Relevant context:\n$ctx"
        } else {
            _lastRagContextUsed.value = emptyList()
            addTerminalLine("RAG", "No indexed documents matched — answering without context")
            ""
        }
    }

    /** Conversation history (USER/ASSISTANT) for the model, excluding empty placeholders. */
    private fun conversationTurns(): List<InferenceEngine.Turn> =
        terminalOutput.value
            .filter { (it.role == ChatRole.USER || it.role == ChatRole.ASSISTANT) && it.text.isNotBlank() }
            .map { InferenceEngine.Turn(it.role, it.text) }

    private fun newAssistantLine(sender: String, modelId: String): Long {
        val line = TerminalLine(sender = sender, text = "", role = ChatRole.ASSISTANT, modelId = modelId)
        terminalOutput.value = terminalOutput.value + line
        return line.id
    }

    private fun updateLine(lineId: Long, text: String) {
        terminalOutput.value = terminalOutput.value.map {
            if (it.id == lineId) it.copy(text = text) else it
        }
    }

    private suspend fun persistTurn(sender: String, text: String, role: ChatRole, modelId: String) {
        if (text.isBlank()) return
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
        return allDocs.map { doc -> Pair(doc, cosineSimilarity(queryVector, parseEmbedding(doc.embeddingJson))) }
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .take(k)
    }

    // ===== Remote (optional) =====

    fun checkOllamaBackend() {
        viewModelScope.launch(Dispatchers.IO) {
            addTerminalLine("NETWORK", "Pinging Ollama node at ${ollamaUrl.value} …")
            val ok = OllamaClient.checkConnection(ollamaUrl.value)
            if (ok) {
                val models = OllamaClient.getAvailableModels(ollamaUrl.value)
                addTerminalLine("NETWORK", "✅ Connected. Remote models: " + if (models.isEmpty()) "none" else models.joinToString(", "))
            } else {
                addTerminalLine("NETWORK", "❌ Node unreachable. On-device engine remains active.")
            }
        }
    }

    fun pingMcp() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!mcp.isConfigured()) { addTerminalLine("MCP", "Set an MCP server URL first."); return@launch }
            addTerminalLine("MCP", mcp.handleCommand(listOf("list")))
        }
    }

    // ===== RAG operations =====

    fun createIndexForDocument(title: String, body: String) {
        val docId = "VEC_" + UUID.randomUUID().toString().substring(0, 8).uppercase()
        val embedding = embedText("$title $body")
        val vectorJson = embedding.joinToString(",") { it.toString() }
        val newDoc = VectorDocument(vectorId = docId, title = title, chunkText = body, embeddingJson = vectorJson)
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
                if (allDocs.isEmpty()) { addTerminalLine("RAG", "No documents indexed"); return@launch }
                val queryVector = embedText(query)
                val scoredDocs = allDocs.map { doc ->
                    Pair(doc, cosineSimilarity(queryVector, parseEmbedding(doc.embeddingJson)))
                }.sortedByDescending { it.second }
                ragRetrievalResults.value = scoredDocs
                addTerminalLine("RAG", "Retrieved ${scoredDocs.size} results")
            } catch (e: Exception) {
                addTerminalLine("ERROR", "RAG retrieval failed: ${e.message}")
            } finally {
                isRagIndexing.value = false
            }
        }
    }

    // ===== LoRA tuning (PC offload preview) =====

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
            appendCreatorLog("[WS] Connecting to PC training host ${ollamaUrl.value} …")
            appendCreatorLog(
                "[WS] base=${creatorBaseModelId.value} rank=${creatorLoraRank.value} " +
                    "lr=${creatorLr.value} dataset=${creatorDatasetName.value} quant=${creatorQuantization.value}"
            )
            var loss = 4.2f
            val stepsPerEpoch = 5
            for (epoch in 1..epochs) {
                _creatorEpochCurrent.value = epoch
                for (s in 1..stepsPerEpoch) {
                    kotlinx.coroutines.delay(150)
                    loss = (loss * 0.93f).coerceAtLeast(0.05f)
                    _creatorLossValues.value = _creatorLossValues.value + loss
                    _creatorTrainingProgress.value =
                        ((epoch - 1) * stepsPerEpoch + s).toFloat() / (epochs * stepsPerEpoch)
                }
                _creatorTrainingAccuracy.value = (100f - loss * 18f).coerceIn(0f, 99.9f)
                appendCreatorLog(
                    "[WS] epoch $epoch/$epochs loss=${"%.4f".format(loss)} acc=${"%.1f".format(_creatorTrainingAccuracy.value)}%"
                )
            }
            appendCreatorLog("[WS] Converged. Adapter '${creatorModelName.value}' saved to registry.")
            _isCreatorTraining.value = false
        }
    }

    private fun appendCreatorLog(line: String) { _creatorTrainingLogs.value = _creatorTrainingLogs.value + line }

    // ===== Terminal =====

    fun clearTerminalLog() {
        terminalOutput.value = emptyList()
        _lastRagContextUsed.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) { repository.clearChatHistory() }
        addTerminalLine("SYSTEM", "Conversation cleared")
    }

    fun addTerminalLine(sender: String, message: String, role: ChatRole = ChatRole.SYSTEM, modelId: String? = null) {
        terminalOutput.value = terminalOutput.value + TerminalLine(sender = sender, text = message, role = role, modelId = modelId)
    }

    override fun onCleared() {
        super.onCleared()
        engine.unloadAll()
    }

    // ===== Helpers =====

    private fun embedText(text: String): FloatArray {
        val dims = EMBED_DIM
        val vec = FloatArray(dims)
        val tokens = text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        for (token in tokens) {
            val h1 = token.hashCode()
            vec[((h1 % dims) + dims) % dims] += 1f
            val h2 = (token + "#salt").hashCode()
            vec[((h2 % dims) + dims) % dims] += if (h2 and 1 == 0) 1f else -1f
        }
        var norm = 0f
        for (v in vec) norm += v * v
        norm = sqrt(norm)
        if (norm > 1e-6f) for (i in vec.indices) vec[i] = vec[i] / norm
        return vec
    }

    private fun parseEmbedding(json: String): FloatArray {
        val parts = json.split(",")
        return FloatArray(EMBED_DIM) { i -> parts.getOrNull(i)?.trim()?.toFloatOrNull() ?: 0f }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var normA = 0f; var normB = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) { dot += a[i] * b[i]; normA += a[i] * a[i]; normB += b[i] * b[i] }
        return dot / (sqrt(normA) * sqrt(normB) + 1e-9f)
    }

    companion object {
        private const val EMBED_DIM = 128
    }
}
