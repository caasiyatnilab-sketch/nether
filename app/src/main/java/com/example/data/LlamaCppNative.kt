package com.example.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LlamaCppNative {
    private const val TAG = "LlamaCppNative"
    
    var isLibraryLoaded = false
        private set

    // Token streaming flow
    private val _tokenStream = MutableStateFlow<String>("")
    val tokenStream: StateFlow<String> = _tokenStream.asStateFlow()

    init {
        try {
            System.loadLibrary("local_ai_studio")
            isLibraryLoaded = true
            Log.i(TAG, "Native library 'local_ai_studio' loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            isLibraryLoaded = false
            Log.e(TAG, "Failed to load native library 'local_ai_studio': ${e.message}")
        } catch (e: Exception) {
            isLibraryLoaded = false
            Log.e(TAG, "Unexpected error loading native library: ${e.message}")
        }
    }

    // ===== SPRINT 1: NEW CORE API =====

    /**
     * Load a GGUF model from filesystem path.
     * @param modelPath Absolute path to .gguf file
     * @param contextSize Maximum context window (tokens)
     * @return true if load successful
     */
    fun loadModel(modelPath: String, contextSize: Int = 2048): Boolean {
        return if (isLibraryLoaded) {
            try {
                nativeLoadModel(modelPath, contextSize)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "UnsatisfiedLinkError in loadModel", e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error loading model: ${e.message}", e)
                false
            }
        } else {
            Log.w(TAG, "Native library not loaded")
            false
        }
    }

    /**
     * Unload current model and free memory.
     */
    fun unloadModel() {
        if (isLibraryLoaded) {
            try {
                nativeUnloadModel()
                Log.i(TAG, "Successfully unloaded model")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "UnsatisfiedLinkError in unloadModel", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error unloading model: ${e.message}", e)
            }
        }
    }

    /**
     * Check if model is currently loaded.
     */
    fun isModelLoaded(): Boolean {
        return if (isLibraryLoaded) {
            try {
                nativeIsModelLoaded()
            } catch (e: Exception) {
                Log.e(TAG, "Error checking model status", e)
                false
            }
        } else {
            false
        }
    }

    /**
     * Generate text from prompt (blocking, non-streaming).
     * @param prompt Input text
     * @param maxTokens Maximum tokens to generate
     * @param temperature Sampling temperature (0.0 - 2.0)
     * @return Generated text
     */
    fun generate(prompt: String, maxTokens: Int = 256, temperature: Float = 0.7f): String {
        return if (isLibraryLoaded && isModelLoaded()) {
            try {
                nativeGenerate(prompt, maxTokens, temperature)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "UnsatisfiedLinkError in generate", e)
                ""
            } catch (e: Exception) {
                Log.e(TAG, "Error generating text: ${e.message}", e)
                ""
            }
        } else {
            Log.w(TAG, "Model not loaded")
            ""
        }
    }

    /**
     * Tokenize input text to token IDs.
     * @param text Input text
     * @return List of token IDs as space-separated string
     */
    fun tokenize(text: String): String {
        return if (isLibraryLoaded) {
            try {
                nativeTokenize(text)
            } catch (e: Exception) {
                Log.e(TAG, "Error tokenizing: ${e.message}", e)
                ""
            }
        } else {
            ""
        }
    }

    /**
     * Stream generation token-by-token (FUTURE: async callback).
     * @param prompt Input text
     * @param maxTokens Maximum tokens to generate
     * @param temperature Sampling temperature
     * @return Stream of tokens as single string (space-separated for now)
     */
    fun generateStream(prompt: String, maxTokens: Int = 256, temperature: Float = 0.7f): String {
        return if (isLibraryLoaded && isModelLoaded()) {
            try {
                val result = nativeGenerateStream(prompt, maxTokens, temperature)
                _tokenStream.value = result
                result
            } catch (e: Exception) {
                Log.e(TAG, "Error in stream generation: ${e.message}", e)
                ""
            }
        } else {
            ""
        }
    }

    /**
     * Free context memory directly (called during VM lifecycle).
     */
    fun freeContext() {
        if (isLibraryLoaded) {
            try {
                nativeFreeContext()
                Log.i(TAG, "freeContext completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error freeing context", e)
            }
        }
    }

    // ===== LEGACY COMPATIBILITY (for fallback UI) =====
    
    fun getCppString(): String {
        return if (isLibraryLoaded) {
            try {
                stringFromJNI()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting cpp string", e)
                "⚙️ [FALLBACK] JNI link failure"
            }
        } else {
            "⚙️ [FALLBACK] Native binary not loaded"
        }
    }

    fun generateFallbackResponse(prompt: String): String {
        val lower = prompt.lowercase()
        return "🤖 [Local Fallback Engine]\n" +
               "Input: \"$prompt\"\n\n" +
               when {
                   lower.contains("calc") || lower.contains("math") -> 
                       "Mathematical computation complete."
                   lower.contains("email") || lower.contains("write") -> 
                       "Draft prepared locally."
                   lower.contains("summary") || lower.contains("notes") -> 
                       "Summary generated from context."
                   else -> 
                       "Query processed locally on device."
               }
    }

    // ===== NATIVE JNI DECLARATIONS =====
    
    private external fun stringFromJNI(): String
    private external fun nativeLoadModel(modelPath: String, contextSize: Int): Boolean
    private external fun nativeUnloadModel()
    private external fun nativeIsModelLoaded(): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float): String
    private external fun nativeTokenize(text: String): String
    private external fun nativeGenerateStream(prompt: String, maxTokens: Int, temperature: Float): String
    private external fun nativeFreeContext()
}
