package com.example.data

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrates real on-device inference over one or more [LlamaModel] instances.
 *
 * - Single mode: one model answers, streaming tokens.
 * - Team mode: a fast *drafter* produces a first answer, then a stronger
 *   *reasoner* reviews and refines it — two genuinely-loaded models cooperating.
 *
 * All text comes from llama.cpp decoding; nothing here is simulated.
 */
class InferenceEngine {

    private val loaded = ConcurrentHashMap<String, LlamaModel>()

    fun isLoaded(modelId: String): Boolean = loaded[modelId]?.isLoaded == true

    fun loadedIds(): Set<String> = loaded.keys.toSet()

    fun descriptor(modelId: String): String = loaded[modelId]?.descriptor ?: ""

    /** Load a GGUF into a named slot. Returns true on success. */
    fun load(modelId: String, path: String, contextSize: Int): Boolean {
        unload(modelId)
        val m = LlamaModel.load(path, contextSize) ?: return false
        loaded[modelId] = m
        Log.i(TAG, "Loaded $modelId: ${m.descriptor}")
        return true
    }

    fun unload(modelId: String) {
        loaded.remove(modelId)?.close()
    }

    fun unloadAll() {
        loaded.values.forEach { it.close() }
        loaded.clear()
    }

    /**
     * Single-model streaming chat. [turns] is the conversation so far
     * (oldest first); a system prompt is prepended. Returns the full reply.
     */
    fun chat(
        modelId: String,
        turns: List<Turn>,
        systemPrompt: String = DEFAULT_SYSTEM,
        maxTokens: Int = 320,
        temperature: Float = 0.7f,
        onToken: (String) -> Unit
    ): String {
        val model = loaded[modelId] ?: return ""
        val prompt = buildPrompt(model, systemPrompt, turns)
        return model.generateStreaming(prompt, maxTokens, temperature) { piece ->
            onToken(piece); true
        }
    }

    /**
     * Dual-model "teamwork". The drafter answers, then the reasoner refines.
     * [onStage] reports progress; [onToken] streams the *final* (reasoner) reply.
     * Returns the refined answer (or the draft if no reasoner is loaded).
     */
    fun teamChat(
        drafterId: String,
        reasonerId: String,
        turns: List<Turn>,
        maxTokens: Int = 320,
        onStage: (String) -> Unit,
        onDraftToken: (String) -> Unit,
        onToken: (String) -> Unit
    ): TeamResult {
        val drafter = loaded[drafterId]
        val reasoner = loaded[reasonerId]
        val userMsg = turns.lastOrNull { it.role == ChatRole.USER }?.content.orEmpty()

        if (drafter == null) return TeamResult("", "")

        onStage("Drafting with ${descriptorShort(drafterId)}…")
        val draftPrompt = buildPrompt(drafter, DRAFTER_SYSTEM, turns)
        val draft = drafter.generateStreaming(draftPrompt, maxTokens, 0.7f) { p ->
            onDraftToken(p); true
        }.trim()

        if (reasoner == null) {
            // No second model: the draft is the answer.
            onToken(draft)
            return TeamResult(draft, draft)
        }

        onStage("Refining with ${descriptorShort(reasonerId)}…")
        val refineTurns = listOf(
            Turn(
                ChatRole.USER,
                "User question:\n$userMsg\n\nA draft answer from another assistant:\n\"\"\"\n$draft\n\"\"\"\n\n" +
                    "Improve it: fix any errors, add missing detail, and make it clear and concise. " +
                    "Reply with ONLY the final improved answer."
            )
        )
        val refinePrompt = buildPrompt(reasoner, REASONER_SYSTEM, refineTurns)
        val refined = reasoner.generateStreaming(refinePrompt, maxTokens, 0.5f) { p ->
            onToken(p); true
        }.trim()

        return TeamResult(draft, refined.ifBlank { draft })
    }

    private fun descriptorShort(modelId: String): String =
        ModelUniverse.byId(modelId)?.name ?: modelId

    /**
     * Build a prompt using the model's own chat template. Keeps only the most
     * recent turns that comfortably fit, plus the system message.
     */
    private fun buildPrompt(model: LlamaModel, system: String, turns: List<Turn>): String {
        val recent = turns.takeLast(MAX_TURNS)
        val roles = ArrayList<String>()
        val contents = ArrayList<String>()
        if (system.isNotBlank()) {
            roles.add("system"); contents.add(system)
        }
        for (t in recent) {
            roles.add(if (t.role == ChatRole.USER) "user" else "assistant")
            contents.add(t.content)
        }
        return model.applyChatTemplate(roles, contents, addAssistant = true)
    }

    data class Turn(val role: ChatRole, val content: String)
    data class TeamResult(val draft: String, val finalAnswer: String)

    companion object {
        private const val TAG = "InferenceEngine"
        private const val MAX_TURNS = 12

        const val DEFAULT_SYSTEM =
            "You are Nether, a helpful AI assistant running entirely on the user's device. " +
                "Answer clearly and concisely."
        const val DRAFTER_SYSTEM =
            "You are a fast assistant. Give a direct first-draft answer to the user's question."
        const val REASONER_SYSTEM =
            "You are a careful senior reviewer. You improve draft answers for correctness, " +
                "completeness, and clarity. Reply with only the final improved answer."
    }
}
