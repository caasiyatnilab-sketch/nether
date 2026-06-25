package com.example.data

import android.util.Log

/**
 * Thin JNI surface over the vendored llama.cpp engine (`liblocal_ai_studio.so`).
 *
 * This is real on-device inference: GGUF weights are memory-mapped, tokenized
 * with the model's own vocabulary, decoded through the transformer and sampled
 * token-by-token. The handle-based API lets multiple models be resident at once
 * (memory permitting) which powers dual-model "teamwork".
 */
object LlamaBridge {
    private const val TAG = "LlamaBridge"

    /** Callback for streaming generation. Return false to stop early. */
    fun interface TokenCallback {
        fun onToken(token: String): Boolean
    }

    var isLibraryLoaded = false
        private set

    init {
        try {
            System.loadLibrary("local_ai_studio")
            isLibraryLoaded = true
            Log.i(TAG, "Native engine loaded: ${runCatching { stringFromJNI() }.getOrDefault("?")}")
        } catch (e: UnsatisfiedLinkError) {
            isLibraryLoaded = false
            Log.e(TAG, "Failed to load native engine: ${e.message}")
        }
    }

    fun buildInfo(): String =
        if (isLibraryLoaded) runCatching { stringFromJNI() }.getOrDefault("") else ""

    external fun stringFromJNI(): String
    external fun nativeLoadModel(modelPath: String, contextSize: Int): Long
    external fun nativeFreeModel(handle: Long)
    external fun nativeIsLoaded(handle: Long): Boolean
    external fun nativeModelDesc(handle: Long): String
    external fun nativeApplyChatTemplate(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        addAssistant: Boolean
    ): String
    external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: TokenCallback?
    ): String
    external fun nativeTokenize(handle: Long, text: String): String
}
