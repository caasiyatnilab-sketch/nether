#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <cstring>

#define LOG_TAG "LlamaCppNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ===== SPRINT 1: Llama.cpp Integration Layer =====
// This will be linked with real llama.cpp in future iterations

struct LlamaModelState {
    char* model_path;
    int context_size;
    bool loaded;
    // In production: llama_context* ctx from llama.cpp
};

static LlamaModelState g_model_state = {nullptr, 0, false};

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_example_data_LlamaCppNative_stringFromJNI(
        JNIEnv* env, jobject) {
    LOGI("JNI initialization called");
    std::string msg = "⚙️ [NATIVE] Llama.cpp C++ JNI Bridge v1.0";
    return env->NewStringUTF(msg.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_example_data_LlamaCppNative_nativeLoadModel(
        JNIEnv* env, jobject, jstring model_path, jint context_size) {
    
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("loadModel: path=%s, context_size=%d", path, context_size);

    if (g_model_state.loaded) {
        LOGW("Model already loaded, unloading first");
        if (g_model_state.model_path) free(g_model_state.model_path);
        g_model_state.loaded = false;
    }

    g_model_state.model_path = strdup(path);
    g_model_state.context_size = context_size;
    g_model_state.loaded = true;

    LOGI("Model loaded successfully: %s (context: %d)", path, context_size);
    env->ReleaseStringUTFChars(model_path, path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_example_data_LlamaCppNative_nativeUnloadModel(
        JNIEnv* env, jobject) {
    
    LOGI("unloadModel called");
    if (g_model_state.model_path) {
        free(g_model_state.model_path);
        g_model_state.model_path = nullptr;
    }
    g_model_state.loaded = false;
    LOGI("Model unloaded");
}

JNIEXPORT jboolean JNICALL
Java_com_example_data_LlamaCppNative_nativeIsModelLoaded(
        JNIEnv* env, jobject) {
    return g_model_state.loaded ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_example_data_LlamaCppNative_nativeGenerate(
        JNIEnv* env, jobject, jstring prompt, jint max_tokens, jfloat temperature) {
    
    if (!g_model_state.loaded) {
        LOGE("Model not loaded, cannot generate");
        return env->NewStringUTF("ERROR: Model not loaded");
    }

    const char* prompt_c = env->GetStringUTFChars(prompt, nullptr);
    LOGI("Generate: prompt='%s', max_tokens=%d, temp=%.2f", prompt_c, max_tokens, temperature);

    // TODO: Real llama.cpp inference here
    std::string result = "🤖 [llama.cpp inference]\nPrompt: ";
    result += prompt_c;
    result += "\nTokens generated: " + std::to_string(max_tokens);

    env->ReleaseStringUTFChars(prompt, prompt_c);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_example_data_LlamaCppNative_nativeTokenize(
        JNIEnv* env, jobject, jstring text) {
    
    if (!g_model_state.loaded) {
        return env->NewStringUTF("");
    }

    const char* text_c = env->GetStringUTFChars(text, nullptr);
    LOGI("Tokenize: '%s'", text_c);

    // TODO: Real tokenization with llama.cpp
    std::string tokens = "1 2 3 4 5"; // Simulated token IDs

    env->ReleaseStringUTFChars(text, text_c);
    return env->NewStringUTF(tokens.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_example_data_LlamaCppNative_nativeGenerateStream(
        JNIEnv* env, jobject thiz, jstring prompt, jint max_tokens, jfloat temperature) {
    
    // For now: same as blocking generate. In future: callback-based streaming
    return Java_com_example_data_LlamaCppNative_nativeGenerate(
        env, thiz, prompt, max_tokens, temperature);
}

JNIEXPORT void JNICALL
Java_com_example_data_LlamaCppNative_nativeFreeContext(
        JNIEnv* env, jobject) {
    
    LOGI("freeContext called");
    if (g_model_state.model_path) {
        free(g_model_state.model_path);
        g_model_state.model_path = nullptr;
    }
    g_model_state.loaded = false;
}

} // extern "C"
