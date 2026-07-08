// JNI bridge from LlamaCppEngine.kt to llama.cpp.
//
// Targets a recent llama.cpp API (the post-`llama_vocab` / sampler-chain era).
// llama.cpp's C API moves fast: pin the submodule to a known tag and adjust the
// handful of symbols below if the compiler complains (the likely ones are noted
// inline). Single-turn: each generate() loads → completes → frees via close().

#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <string>
#include <thread>
#include <vector>
#include "llama.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "quiver-llama", __VA_ARGS__)

namespace {
struct Handle {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    // Flipped by nativeCancel from another thread; ggml's abort callback polls it
    // during compute so a wedged prefill/decode can be interrupted (the agent
    // watchdog can only cancel between tokens otherwise, never during prefill).
    std::atomic<bool> cancel{false};
};
bool g_backend_ready = false;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_kamboji_quiver_ai_engine_LlamaCppEngine_nativeLoad(
        JNIEnv* env, jobject, jstring jpath, jint nCtx) {
    if (!g_backend_ready) { llama_backend_init(); g_backend_ready = true; }

    const char* path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0; // CPU on-device
    llama_model* model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!model) { LOGE("model load failed"); return 0; }

    // Allocate the handle first so its cancel flag has a stable address for the
    // abort callback wired into the context below.
    Handle* h = new Handle();
    h->model = model;

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = nCtx > 0 ? (uint32_t) nCtx : 2048;
    // n_batch is the max tokens per llama_decode call and the whole prompt is
    // decoded in one call below — it must cover the full context, or prompts
    // longer than n_batch fail outright (the agent prompt is ~800 tokens).
    // n_ubatch (physical chunk) stays at its default; llama.cpp splits internally.
    cp.n_batch = cp.n_ctx;
    // Threads: the default (4) leaves cores idle during prefill — the compute-
    // bound first-token stage that gates the "Thinking…" state. Prefill (batch)
    // scales with cores, so give it all of them; per-token decode is more
    // memory-bound, so cap it at 4 to avoid big.LITTLE scheduling overhead.
    const int hw = std::max(1, (int) std::thread::hardware_concurrency());
    cp.n_threads = std::min(hw, 4);
    cp.n_threads_batch = hw;
    // Abort hook: returns true to make ggml bail out of the current compute, so a
    // cancelled/timed-out request stops even mid-prefill.
    cp.abort_callback = [](void* data) -> bool {
        return static_cast<std::atomic<bool>*>(data)->load(std::memory_order_relaxed);
    };
    cp.abort_callback_data = &h->cancel;

    h->ctx = llama_init_from_model(model, cp);
    if (!h->ctx) { llama_model_free(model); delete h; return 0; }

    return (jlong) h;
}

namespace {
// Length of the longest prefix of s that ends on a COMPLETE UTF-8 sequence.
// Token pieces routinely split multi-byte characters (Devanagari, Tamil,
// emoji…) across tokens; emitting a split sequence would corrupt the stream.
size_t utf8_complete_prefix(const std::string& s) {
    size_t n = s.size();
    size_t i = n;
    while (i > 0 && (static_cast<unsigned char>(s[i - 1]) & 0xC0) == 0x80 && n - i < 3) i--;
    if (i == 0) return n; // nothing but continuation bytes: flush as-is
    unsigned char lead = static_cast<unsigned char>(s[i - 1]);
    size_t need = lead >= 0xF0 ? 4 : lead >= 0xE0 ? 3 : lead >= 0xC0 ? 2 : 1;
    return (n - (i - 1)) < need ? i - 1 : n;
}

// Push bytes to the Kotlin TokenCallback as a byte[] (NOT NewStringUTF — that
// wants modified UTF-8 and crashes on real 4-byte sequences like emoji).
// Returns the callback's verdict: false = stop generating (cancelled).
bool emit_bytes(JNIEnv* env, jobject cb, jmethodID onToken, const char* data, size_t len) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(len));
    if (!arr) return false;
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(len), reinterpret_cast<const jbyte*>(data));
    jboolean keep = env->CallBooleanMethod(cb, onToken, arr);
    env->DeleteLocalRef(arr);
    if (env->ExceptionCheck()) { env->ExceptionClear(); return false; }
    return keep == JNI_TRUE;
}
}

extern "C"
JNIEXPORT void JNICALL
Java_com_kamboji_quiver_ai_engine_LlamaCppEngine_nativeComplete(
        JNIEnv* env, jobject, jlong handle, jstring jprompt, jstring jgrammar,
        jint maxTokens, jobject jcallback) {
    Handle* h = reinterpret_cast<Handle*>(handle);
    if (!h) return;
    h->cancel.store(false, std::memory_order_relaxed); // clear any prior cancel
    const llama_vocab* vocab = llama_model_get_vocab(h->model);

    jclass cbClass = env->GetObjectClass(jcallback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "([B)Z");
    if (!onToken) return;

    const char* prompt = env->GetStringUTFChars(jprompt, nullptr);
    std::string ptext(prompt);
    env->ReleaseStringUTFChars(jprompt, prompt);

    // Tokenize (first call with null buffer returns -count).
    int n = -llama_tokenize(vocab, ptext.c_str(), ptext.size(), nullptr, 0, true, true);
    std::vector<llama_token> tokens(n);
    llama_tokenize(vocab, ptext.c_str(), ptext.size(), tokens.data(), tokens.size(), true, true);

    // Sampler chain: optional grammar (forces valid JSON) + top-k + temp + dist.
    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    const char* grammar = jgrammar ? env->GetStringUTFChars(jgrammar, nullptr) : nullptr;
    if (grammar && grammar[0]) {
        llama_sampler_chain_add(smpl, llama_sampler_init_grammar(vocab, grammar, "root"));
    }
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    // Low temperature: the output is a tool-call plan, not creative prose —
    // hot sampling makes a 1B model wander out of the intended actions.
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.2f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string pending; // bytes not yet emitted (waiting for a UTF-8 boundary)
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    llama_token cur = 0;
    for (int i = 0; i < maxTokens; i++) {
        if (llama_decode(h->ctx, batch)) break;
        cur = llama_sampler_sample(smpl, h->ctx, -1);
        if (llama_vocab_is_eog(vocab, cur)) break;
        char piece[256];
        int pn = llama_token_to_piece(vocab, cur, piece, sizeof(piece), 0, true);
        if (pn > 0) {
            pending.append(piece, pn);
            size_t ok = utf8_complete_prefix(pending);
            if (ok > 0) {
                if (!emit_bytes(env, jcallback, onToken, pending.data(), ok)) break;
                pending.erase(0, ok);
            }
        }
        batch = llama_batch_get_one(&cur, 1);
    }
    if (!pending.empty()) emit_bytes(env, jcallback, onToken, pending.data(), pending.size());

    if (grammar) env->ReleaseStringUTFChars(jgrammar, grammar);
    llama_sampler_free(smpl);
    llama_memory_clear(llama_get_memory(h->ctx), true); // older API: llama_kv_cache_clear(h->ctx)
}

extern "C"
JNIEXPORT void JNICALL
Java_com_kamboji_quiver_ai_engine_LlamaCppEngine_nativeCancel(
        JNIEnv*, jobject, jlong handle) {
    Handle* h = reinterpret_cast<Handle*>(handle);
    if (h) h->cancel.store(true, std::memory_order_relaxed);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_kamboji_quiver_ai_engine_LlamaCppEngine_nativeFree(
        JNIEnv*, jobject, jlong handle) {
    Handle* h = reinterpret_cast<Handle*>(handle);
    if (!h) return;
    llama_free(h->ctx);
    llama_model_free(h->model);
    delete h;
}
