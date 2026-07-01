// JNI bridge from LlamaCppEngine.kt to llama.cpp.
//
// Targets a recent llama.cpp API (the post-`llama_vocab` / sampler-chain era).
// llama.cpp's C API moves fast: pin the submodule to a known tag and adjust the
// handful of symbols below if the compiler complains (the likely ones are noted
// inline). Single-turn: each generate() loads → completes → frees via close().

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "llama.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "quiver-llama", __VA_ARGS__)

namespace {
struct Handle {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
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

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = nCtx > 0 ? (uint32_t) nCtx : 2048;
    cp.n_batch = 512;
    llama_context* ctx = llama_init_from_model(model, cp);
    if (!ctx) { llama_model_free(model); return 0; }

    return (jlong) new Handle{model, ctx};
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kamboji_quiver_ai_engine_LlamaCppEngine_nativeComplete(
        JNIEnv* env, jobject, jlong handle, jstring jprompt, jstring jgrammar, jint maxTokens) {
    Handle* h = reinterpret_cast<Handle*>(handle);
    if (!h) return env->NewStringUTF("");
    const llama_vocab* vocab = llama_model_get_vocab(h->model);

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
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string out;
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    llama_token cur = 0;
    for (int i = 0; i < maxTokens; i++) {
        if (llama_decode(h->ctx, batch)) break;
        cur = llama_sampler_sample(smpl, h->ctx, -1);
        if (llama_vocab_is_eog(vocab, cur)) break;
        char piece[256];
        int pn = llama_token_to_piece(vocab, cur, piece, sizeof(piece), 0, true);
        if (pn > 0) out.append(piece, pn);
        batch = llama_batch_get_one(&cur, 1);
    }

    if (grammar) env->ReleaseStringUTFChars(jgrammar, grammar);
    llama_sampler_free(smpl);
    llama_memory_clear(llama_get_memory(h->ctx), true); // older API: llama_kv_cache_clear(h->ctx)
    return env->NewStringUTF(out.c_str());
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
