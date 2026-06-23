#include <jni.h>
#include <string>
#include <android/log.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

#define LOG_TAG "LlamaCppNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Demo-only model context. This does not map GGUF weights or own llama.cpp state.
struct DemoModelState {
    uint32_t seed;
    float token_bias[16];
};

struct LlamaModelContext {
    char* model_path;
    DemoModelState demo_state;
    bool active;
};

// Global context pointer
static LlamaModelContext* g_ctx = nullptr;

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_data_LlamaCppNative_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    LOGI("JNI stringFromJNI called.");
    std::string hello = "⚙️ [NATIVE JNI] Initialized native demo engine successfully.";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_data_LlamaCppNative_loadModel(
        JNIEnv* env,
        jobject /* this */,
        jstring model_path_str) {
    
    const char* path = env->GetStringUTFChars(model_path_str, nullptr);
    LOGI("loadModel requested: %s", path);

    // If a model is already loaded, unload it first to prevent leaks
    if (g_ctx != nullptr) {
        LOGW("Model already loaded. Releasing previous demo context before loading new model.");
        if (g_ctx->model_path != nullptr) {
            free(g_ctx->model_path);
        }
        free(g_ctx);
        g_ctx = nullptr;
    }

    // Allocate new model context on the heap
    g_ctx = (LlamaModelContext*)malloc(sizeof(LlamaModelContext));
    if (g_ctx == nullptr) {
        LOGE("Failed to allocate memory for LlamaModelContext!");
        env->ReleaseStringUTFChars(model_path_str, path);
        return JNI_FALSE;
    }

    g_ctx->model_path = strdup(path);
    if (g_ctx->model_path == nullptr) {
        LOGE("Failed to duplicate model path for demo context!");
        free(g_ctx);
        g_ctx = nullptr;
        env->ReleaseStringUTFChars(model_path_str, path);
        return JNI_FALSE;
    }
    g_ctx->active = true;

    // Deterministic demo metadata only. Real GGUF loading should replace this with
    // actual llama.cpp model/context state rather than placeholder weight tensors.
    g_ctx->demo_state.seed = 0x4C4C414Du; // "LLAM"
    for (size_t i = 0; i < 16; i++) {
        g_ctx->demo_state.token_bias[i] = 0.05f * (float)(i % 7) - 0.15f;
    }

    LOGI("Initialized deterministic demo context for path '%s' (%zu bytes); no GGUF weights were memory-mapped.",
         path, sizeof(g_ctx->demo_state));
    
    env->ReleaseStringUTFChars(model_path_str, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_data_LlamaCppNative_generateText(
        JNIEnv* env,
        jobject /* this */,
        jstring prompt_str) {
    
    const char* prompt = env->GetStringUTFChars(prompt_str, nullptr);
    std::string prompt_s(prompt);
    
    LOGI("Text generation requested for prompt: '%s'", prompt);

    if (g_ctx == nullptr || !g_ctx->active) {
        LOGE("Error: No active model context. Cannot generate text!");
        std::string err_resp = "❌ [NATIVE DEMO ERROR] No active demo context. Please load a model path first.";
        env->ReleaseStringUTFChars(prompt_str, prompt);
        return env->NewStringUTF(err_resp.c_str());
    }

    // Demo response generation. This JNI layer does not perform llama.cpp inference yet.
    std::string response = "🤖 [Native C++ Demo Response Engine]\n";
    response += "Input Prompt: \"" + prompt_s + "\"\n\n";
    response += "Demo Processing Trace:\n";
    response += "1. Accepted the prompt through JNI and checked the active native context.\n";
    response += "2. Used a small deterministic demo state; no GGUF file was parsed or memory-mapped.\n";
    response += "3. Kept processing local to this JNI call without opening network sockets.\n\n";
    
    if (prompt_s.find("calc") != std::string::npos || prompt_s.find("math") != std::string::npos) {
        response += "Demo math response: This native stub recognized a calculation-style prompt and returned a deterministic local message.";
    } else if (prompt_s.find("email") != std::string::npos || prompt_s.find("write") != std::string::npos) {
        response += "Draft email demo generated locally:\n\nSubject: Demo Native Response\n\nDear recipient,\n\nThe native demo context received the request and produced this deterministic placeholder. Real GGUF-backed generation is not implemented in this JNI layer yet.\n\nBest regards,\nLocal Demo Agent";
    } else if (prompt_s.find("summary") != std::string::npos || prompt_s.find("notes") != std::string::npos) {
        response += "Executive synthesis demo:\n- **Topic**: Native JNI demo response\n- **Status**: Active placeholder context only\n- **Storage**: No GGUF model mapping performed by this native stub.";
    } else {
        response += "General demo response: The native layer is reachable and using deterministic placeholder state. Replace this with real llama.cpp model/context state before claiming GGUF inference.";
    }

    env->ReleaseStringUTFChars(prompt_str, prompt);
    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_data_LlamaCppNative_unloadModel(
        JNIEnv* env,
        jobject /* this */) {
    
    LOGI("unloadModel requested. Freeing native demo context.");
    if (g_ctx != nullptr) {
        if (g_ctx->model_path != nullptr) {
            free(g_ctx->model_path);
            LOGI("Freed associated duplicated path memory.");
        }
        free(g_ctx);
        g_ctx = nullptr;
        LOGI("Successfully deallocated LlamaModelContext structure. Memory returned to OS.");
    } else {
        LOGW("unloadModel called but model context was already null.");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_data_LlamaCppNative_freeContext(
        JNIEnv* env,
        jobject /* this */) {
    
    LOGI("freeContext requested. Direct demo context cleanup.");
    if (g_ctx != nullptr) {
        if (g_ctx->model_path != nullptr) {
            free(g_ctx->model_path);
            g_ctx->model_path = nullptr;
        }
        free(g_ctx);
        g_ctx = nullptr;
        LOGI("Successfully freed native context via freeContext.");
    } else {
        LOGW("freeContext called but context was already empty.");
    }
}
