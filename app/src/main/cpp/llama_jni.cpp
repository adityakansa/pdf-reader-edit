// FR-069: the one call the app makes into llama.cpp — load the model, feed one prompt, stream the
// answer back token by token. Pieces are handed to Kotlin as raw bytes because a token can end in the
// middle of a UTF-8 sequence; Kotlin decodes the running text. The sink returns false to cancel.

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <string>
#include <thread>
#include <vector>

#include "llama.h"

#define LOG_TAG "llama_jni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr jint kOk = 0;
constexpr jint kLoadFailed = -1;
constexpr jint kContextFailed = -2;
constexpr jint kTokenizeFailed = -3;
constexpr jint kPromptTooLong = -4;
constexpr jint kDecodeFailed = -5;

constexpr int32_t kPenaltyLastN = 64;

std::vector<llama_token> tokenize(const llama_vocab *vocab, const std::string &text) {
    int32_t n = -llama_tokenize(vocab, text.c_str(), (int32_t) text.size(), nullptr, 0, true, true);
    if (n <= 0) return {};
    std::vector<llama_token> tokens(n);
    if (llama_tokenize(vocab, text.c_str(), (int32_t) text.size(), tokens.data(), n, true, true) < 0) {
        return {};
    }
    return tokens;
}

std::string piece(const llama_vocab *vocab, llama_token token) {
    char buffer[256];
    int32_t n = llama_token_to_piece(vocab, token, buffer, sizeof(buffer), 0, false);
    if (n >= 0) return {buffer, (size_t) n};
    std::string big((size_t) -n, '\0');
    n = llama_token_to_piece(vocab, token, big.data(), (int32_t) big.size(), 0, false);
    return n >= 0 ? big.substr(0, (size_t) n) : std::string();
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_whats_web_scan_webscan_pdfreaderpdffileedit_ai_LlamaBridge_nativeGenerate(
        JNIEnv *env,
        jobject /* this */,
        jstring jModelPath,
        jstring jPrompt,
        jint maxTokens,
        jfloat temperature,
        jfloat repeatPenalty,
        jint contextTokens,
        jint threads,
        jobject sink) {
    jclass sinkClass = env->GetObjectClass(sink);
    jmethodID onToken = env->GetMethodID(sinkClass, "onToken", "([B)Z");

    const char *modelPath = env->GetStringUTFChars(jModelPath, nullptr);
    const char *promptChars = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(promptChars);
    env->ReleaseStringUTFChars(jPrompt, promptChars);

    llama_backend_init();

    llama_model_params modelParams = llama_model_default_params();
    llama_model *model = llama_model_load_from_file(modelPath, modelParams);
    env->ReleaseStringUTFChars(jModelPath, modelPath);
    if (model == nullptr) {
        LOGE("model load failed");
        return kLoadFailed;
    }

    const int nThreads = std::max(1, (int) threads);
    llama_context_params contextParams = llama_context_default_params();
    contextParams.n_ctx = (uint32_t) contextTokens;
    contextParams.n_batch = (uint32_t) contextTokens;
    contextParams.n_threads = nThreads;
    contextParams.n_threads_batch = nThreads;
    contextParams.no_perf = true;
    llama_context *ctx = llama_init_from_model(model, contextParams);
    if (ctx == nullptr) {
        llama_model_free(model);
        return kContextFailed;
    }

    const llama_vocab *vocab = llama_model_get_vocab(model);
    std::vector<llama_token> tokens = tokenize(vocab, prompt);
    jint status = kOk;
    if (tokens.empty()) {
        status = kTokenizeFailed;
    } else if ((int) tokens.size() + maxTokens > contextTokens) {
        status = kPromptTooLong;
    }

    llama_sampler *sampler = nullptr;
    if (status == kOk) {
        sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(sampler, llama_sampler_init_penalties(
                llama_vocab_n_tokens(vocab), kPenaltyLastN, repeatPenalty, 0.0f, 0.0f));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.95f, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

        if (llama_decode(ctx, llama_batch_get_one(tokens.data(), (int32_t) tokens.size())) != 0) {
            status = kDecodeFailed;
        }
    }

    for (int i = 0; status == kOk && i < maxTokens; ++i) {
        llama_token next = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, next)) break;

        std::string text = piece(vocab, next);
        jbyteArray bytes = env->NewByteArray((jsize) text.size());
        env->SetByteArrayRegion(bytes, 0, (jsize) text.size(), (const jbyte *) text.data());
        jboolean keepGoing = env->CallBooleanMethod(sink, onToken, bytes);
        env->DeleteLocalRef(bytes);
        if (env->ExceptionCheck() || !keepGoing) break;

        if (llama_decode(ctx, llama_batch_get_one(&next, 1)) != 0) status = kDecodeFailed;
    }

    if (sampler != nullptr) llama_sampler_free(sampler);
    llama_free(ctx);
    llama_model_free(model);
    return status;
}
