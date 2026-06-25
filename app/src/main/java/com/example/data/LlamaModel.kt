package com.example.data

import android.util.Log

/**
 * A single loaded llama.cpp model instance, identified by an opaque native
 * handle. Multiple [LlamaModel]s can coexist (dual-model teamwork). Always
 * [close] when finished to release native memory.
 *
 * Generation is genuine inference — no simulated/echoed text.
 */
class LlamaModel private constructor(
    private val handle: Long,
    val descriptor: String
) {
    @Volatile
    private var closed = false

    val isLoaded: Boolean
        get() = !closed && LlamaBridge.nativeIsLoaded(handle)

    /**
     * Format a multi-turn conversation with the model's own chat template
     * (ChatML fallback). Pass parallel role/content lists, e.g.
     * roles = ["system", "user"]. [addAssistant] appends the assistant prefix
     * so generation begins in the reply.
     */
    fun applyChatTemplate(
        roles: List<String>,
        contents: List<String>,
        addAssistant: Boolean = true
    ): String {
        if (closed || roles.isEmpty()) return contents.lastOrNull().orEmpty()
        return runCatching {
            LlamaBridge.nativeApplyChatTemplate(
                handle, roles.toTypedArray(), contents.toTypedArray(), addAssistant
            )
        }.getOrDefault("").ifEmpty { contents.lastOrNull().orEmpty() }
    }

    /** Blocking full generation. */
    fun generate(prompt: String, maxTokens: Int = 256, temperature: Float = 0.7f): String {
        if (!isLoaded) return ""
        return runCatching {
            LlamaBridge.nativeGenerate(handle, prompt, maxTokens, temperature, null)
        }.getOrDefault("")
    }

    /** Streaming generation; [onToken] runs on the caller thread per piece. */
    fun generateStreaming(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        onToken: LlamaBridge.TokenCallback
    ): String {
        if (!isLoaded) return ""
        return runCatching {
            LlamaBridge.nativeGenerate(handle, prompt, maxTokens, temperature, onToken)
        }.getOrDefault("")
    }

    fun tokenCount(text: String): Int {
        if (closed) return 0
        val ids = runCatching { LlamaBridge.nativeTokenize(handle, text) }.getOrDefault("")
        return if (ids.isBlank()) 0 else ids.trim().split(" ").size
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        runCatching { LlamaBridge.nativeFreeModel(handle) }
    }

    companion object {
        private const val TAG = "LlamaModel"

        /** Load a GGUF file into a new native context. Returns null on failure. */
        fun load(modelPath: String, contextSize: Int = 2048): LlamaModel? {
            if (!LlamaBridge.isLibraryLoaded) {
                Log.e(TAG, "Native engine not available")
                return null
            }
            val handle = runCatching { LlamaBridge.nativeLoadModel(modelPath, contextSize) }
                .getOrDefault(0L)
            if (handle == 0L) {
                Log.e(TAG, "nativeLoadModel failed for $modelPath")
                return null
            }
            val desc = runCatching { LlamaBridge.nativeModelDesc(handle) }.getOrDefault("")
            return LlamaModel(handle, desc)
        }
    }
}
