#include <jni.h>
#include <android/log.h>

#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <cstdint>

#include "llama.h"

#define LOG_TAG "LlamaCppNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ===== Real llama.cpp inference =====
// Handle-based: each loaded model is an independent context, so several models
// can be resident at once (memory permitting) for dual-model "teamwork".
namespace {

std::once_flag g_backend_once;

void ensure_backend() {
    std::call_once(g_backend_once, [] {
        llama_backend_init();
        llama_log_set([](ggml_log_level level, const char* text, void*) {
            if (level >= GGML_LOG_LEVEL_ERROR) LOGE("%s", text);
        }, nullptr);
    });
}

struct ModelHandle {
    llama_model*   model = nullptr;
    llama_context* ctx   = nullptr;
    const llama_vocab* vocab = nullptr;
    int n_ctx = 2048;
    std::string desc;
    std::mutex mtx;
};

ModelHandle* as_handle(jlong h) { return reinterpret_cast<ModelHandle*>(h); }

std::vector<llama_token> tokenize(const llama_vocab* vocab, const std::string& text, bool add_special) {
    int n_max = (int) text.size() + 16;
    std::vector<llama_token> out(n_max);
    int n = llama_tokenize(vocab, text.c_str(), (int) text.size(), out.data(), n_max, add_special, true);
    if (n < 0) {
        out.resize(-n);
        n = llama_tokenize(vocab, text.c_str(), (int) text.size(), out.data(), (int) out.size(), add_special, true);
    }
    out.resize(n < 0 ? 0 : n);
    return out;
}

std::string token_to_piece(const llama_vocab* vocab, llama_token tok) {
    char buf[256];
    int n = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
    if (n < 0) return std::string();
    return std::string(buf, n);
}

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_example_data_LlamaBridge_stringFromJNI(JNIEnv* env, jobject) {
    return env->NewStringUTF("llama.cpp (b4585)");
}

JNIEXPORT jlong JNICALL
Java_com_example_data_LlamaBridge_nativeLoadModel(
        JNIEnv* env, jobject, jstring model_path, jint context_size) {

    ensure_backend();

    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("loadModel: path=%s ctx=%d", path, context_size);

    auto* h = new ModelHandle();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU inference; no usable GPU backend under the emulator

    h->model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(model_path, path);

    if (!h->model) {
        LOGE("Failed to load model");
        delete h;
        return 0;
    }

    h->vocab = llama_model_get_vocab(h->model);

    int n_threads = (int) std::thread::hardware_concurrency();
    if (n_threads <= 0) n_threads = 4;

    h->n_ctx = context_size > 0 ? context_size : 2048;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = (uint32_t) h->n_ctx;
    cparams.n_batch         = 512;
    cparams.n_threads       = n_threads;
    cparams.n_threads_batch = n_threads;

    h->ctx = llama_init_from_model(h->model, cparams);
    if (!h->ctx) {
        LOGE("Failed to create context");
        llama_model_free(h->model);
        delete h;
        return 0;
    }

    char desc[256];
    llama_model_desc(h->model, desc, sizeof(desc));
    h->desc = desc;
    LOGI("Model loaded: %s | threads=%d n_ctx=%d", desc, n_threads, h->n_ctx);

    return reinterpret_cast<jlong>(h);
}

JNIEXPORT void JNICALL
Java_com_example_data_LlamaBridge_nativeFreeModel(JNIEnv*, jobject, jlong handle) {
    auto* h = as_handle(handle);
    if (!h) return;
    {
        std::lock_guard<std::mutex> lock(h->mtx);
        if (h->ctx)   llama_free(h->ctx);
        if (h->model) llama_model_free(h->model);
    }
    delete h;
    LOGI("Model freed");
}

JNIEXPORT jboolean JNICALL
Java_com_example_data_LlamaBridge_nativeIsLoaded(JNIEnv*, jobject, jlong handle) {
    auto* h = as_handle(handle);
    return (h && h->model && h->ctx) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_example_data_LlamaBridge_nativeModelDesc(JNIEnv* env, jobject, jlong handle) {
    auto* h = as_handle(handle);
    return env->NewStringUTF(h ? h->desc.c_str() : "");
}

JNIEXPORT jstring JNICALL
Java_com_example_data_LlamaBridge_nativeApplyChatTemplate(
        JNIEnv* env, jobject, jlong handle, jobjectArray roles, jobjectArray contents, jboolean add_ass) {

    auto* h = as_handle(handle);
    if (!h || !h->model) return env->NewStringUTF("");

    jsize n = env->GetArrayLength(roles);
    std::vector<std::string> role_str(n), content_str(n);
    for (jsize i = 0; i < n; i++) {
        auto r = (jstring) env->GetObjectArrayElement(roles, i);
        auto c = (jstring) env->GetObjectArrayElement(contents, i);
        const char* rc = env->GetStringUTFChars(r, nullptr);
        const char* cc = env->GetStringUTFChars(c, nullptr);
        role_str[i] = rc; content_str[i] = cc;
        env->ReleaseStringUTFChars(r, rc);
        env->ReleaseStringUTFChars(c, cc);
        env->DeleteLocalRef(r);
        env->DeleteLocalRef(c);
    }
    std::vector<llama_chat_message> msgs(n);
    for (jsize i = 0; i < n; i++) {
        msgs[i].role = role_str[i].c_str();
        msgs[i].content = content_str[i].c_str();
    }

    const char* tmpl = llama_model_chat_template(h->model, nullptr);

    std::vector<char> buf(8192);
    int len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(),
                                        add_ass == JNI_TRUE, buf.data(), (int) buf.size());
    if (len > (int) buf.size()) {
        buf.resize(len);
        len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(),
                                        add_ass == JNI_TRUE, buf.data(), (int) buf.size());
    }
    if (len < 0) return env->NewStringUTF("");
    return env->NewStringUTF(std::string(buf.data(), len).c_str());
}

// Real generation. Streams pieces to callback.onToken(String):boolean (false to
// stop). callback may be null. Returns the full generated text.
JNIEXPORT jstring JNICALL
Java_com_example_data_LlamaBridge_nativeGenerate(
        JNIEnv* env, jobject, jlong handle, jstring prompt, jint max_tokens,
        jfloat temperature, jobject callback) {

    auto* h = as_handle(handle);
    if (!h || !h->model || !h->ctx) return env->NewStringUTF("");

    std::lock_guard<std::mutex> lock(h->mtx);

    const char* prompt_c = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_s = prompt_c;
    env->ReleaseStringUTFChars(prompt, prompt_c);

    llama_kv_cache_clear(h->ctx);

    std::vector<llama_token> tokens = tokenize(h->vocab, prompt_s, true);
    if (tokens.empty()) return env->NewStringUTF("");
    if ((int) tokens.size() >= h->n_ctx - 4) tokens.resize(h->n_ctx - 4);

    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.95f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    jmethodID on_token = nullptr;
    if (callback != nullptr) {
        jclass cb_cls = env->GetObjectClass(callback);
        on_token = env->GetMethodID(cb_cls, "onToken", "(Ljava/lang/String;)Z");
        env->DeleteLocalRef(cb_cls);
    }

    std::string result;
    llama_batch batch = llama_batch_get_one(tokens.data(), (int) tokens.size());
    if (llama_decode(h->ctx, batch) != 0) {
        LOGE("decode(prompt) failed");
        llama_sampler_free(smpl);
        return env->NewStringUTF("");
    }

    const int budget = max_tokens > 0 ? max_tokens : 256;
    int n_decoded = 0;
    for (; n_decoded < budget; n_decoded++) {
        llama_token id = llama_sampler_sample(smpl, h->ctx, -1);
        if (llama_vocab_is_eog(h->vocab, id)) break;

        std::string piece = token_to_piece(h->vocab, id);
        result += piece;

        if (on_token != nullptr && !piece.empty()) {
            jstring jpiece = env->NewStringUTF(piece.c_str());
            jboolean cont = env->CallBooleanMethod(callback, on_token, jpiece);
            env->DeleteLocalRef(jpiece);
            if (env->ExceptionCheck()) { env->ExceptionClear(); break; }
            if (cont == JNI_FALSE) break;
        }

        llama_token next_tok = id;
        llama_batch next = llama_batch_get_one(&next_tok, 1);
        if (llama_decode(h->ctx, next) != 0) { LOGE("decode(token) failed"); break; }
    }

    llama_sampler_free(smpl);
    LOGI("generated %d tokens", n_decoded);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_example_data_LlamaBridge_nativeTokenize(JNIEnv* env, jobject, jlong handle, jstring text) {
    auto* h = as_handle(handle);
    if (!h || !h->model) return env->NewStringUTF("");
    const char* tc = env->GetStringUTFChars(text, nullptr);
    std::string s = tc;
    env->ReleaseStringUTFChars(text, tc);
    std::vector<llama_token> toks = tokenize(h->vocab, s, false);
    std::string out;
    for (size_t i = 0; i < toks.size(); i++) {
        if (i) out += ' ';
        out += std::to_string(toks[i]);
    }
    return env->NewStringUTF(out.c_str());
}

} // extern "C"
