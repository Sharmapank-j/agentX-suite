#include <jni.h>
#include <android/log.h>
#include <llama.h>

#include <atomic>
#include <algorithm>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>
#include <sys/stat.h>
#include <unistd.h>

#define TAG "AgentX_LlamaCPP"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

struct AgentXContext {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    const llama_vocab * vocab = nullptr;
    std::atomic<bool> cancelled{false};
    std::mutex generationMutex;
    jlong modelFileBytes = 0;
};

static AgentXContext * fromHandle(jlong handle) {
    return reinterpret_cast<AgentXContext *>(static_cast<intptr_t>(handle));
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_agentx_core_inference_llamacpp_LlamaCppNativeBridge_nativeInit(JNIEnv *, jobject) {
    llama_backend_init();
    LOGI("Upstream llama.cpp backend initialized");
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_agentx_core_inference_llamacpp_LlamaCppNativeBridge_nativeLoadModel(
        JNIEnv * env, jobject, jstring modelPath, jint contextLength, jint nThreads, jint gpuLayers) {
    if (modelPath == nullptr) return 0;
    const char * rawPath = env->GetStringUTFChars(modelPath, nullptr);
    if (rawPath == nullptr) return 0;
    const std::string path(rawPath);
    env->ReleaseStringUTFChars(modelPath, rawPath);

    struct stat fileInfo {};
    if (stat(path.c_str(), &fileInfo) != 0 || fileInfo.st_size < 8) {
        LOGE("GGUF path is unreadable or empty");
        return 0;
    }

    const long pageSize = sysconf(_SC_PAGESIZE);
    const long availablePages = sysconf(_SC_AVPHYS_PAGES);
    const uint64_t availableBytes = pageSize > 0 && availablePages > 0
        ? static_cast<uint64_t>(pageSize) * static_cast<uint64_t>(availablePages) : 0;
    const uint64_t contextEstimate = static_cast<uint64_t>(
        std::clamp(static_cast<int>(contextLength), 256, 32768)) * 64ULL * 1024ULL;
    const uint64_t estimatedWorkingSet = static_cast<uint64_t>(fileInfo.st_size) + contextEstimate;
    if (availableBytes > 0 && estimatedWorkingSet > availableBytes * 8ULL / 10ULL) {
        LOGE("Refusing GGUF load: estimated working set exceeds 80%% of currently available RAM");
        return 0;
    }

    auto * state = new AgentXContext();
    state->modelFileBytes = static_cast<jlong>(estimatedWorkingSet);

    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = std::max(0, static_cast<int>(gpuLayers));
    state->model = llama_model_load_from_file(path.c_str(), modelParams);
    if (state->model == nullptr) {
        LOGE("llama_model_load_from_file failed");
        delete state;
        return 0;
    }

    llama_context_params contextParams = llama_context_default_params();
    contextParams.n_ctx = static_cast<uint32_t>(std::max(256, static_cast<int>(contextLength)));
    contextParams.n_batch = std::min<uint32_t>(contextParams.n_ctx, 512);
    contextParams.n_ubatch = std::min<uint32_t>(contextParams.n_batch, 128);
    contextParams.n_threads = std::max(1, static_cast<int>(nThreads));
    contextParams.n_threads_batch = contextParams.n_threads;
    state->context = llama_init_from_model(state->model, contextParams);
    if (state->context == nullptr) {
        LOGE("llama_init_from_model failed");
        llama_model_free(state->model);
        delete state;
        return 0;
    }

    state->vocab = llama_model_get_vocab(state->model);
    if (state->vocab == nullptr) {
        llama_free(state->context);
        llama_model_free(state->model);
        delete state;
        return 0;
    }

    LOGI("Loaded GGUF model with llama.cpp; estimated working set=%lld bytes", (long long) state->modelFileBytes);
    return static_cast<jlong>(reinterpret_cast<intptr_t>(state));
}

JNIEXPORT jint JNICALL
Java_com_agentx_core_inference_llamacpp_LlamaCppNativeBridge_nativeGenerate(
        JNIEnv * env, jobject, jlong handle, jstring systemPromptString, jstring userPromptString,
        jint maxTokens, jfloat temperature, jfloat topP, jobject callback) {
    AgentXContext * state = fromHandle(handle);
    if (state == nullptr || state->context == nullptr || userPromptString == nullptr || callback == nullptr) return -1;
    std::lock_guard<std::mutex> guard(state->generationMutex);
    state->cancelled.store(false);

    const char * rawUser = env->GetStringUTFChars(userPromptString, nullptr);
    if (rawUser == nullptr) return -1;
    const std::string userPrompt(rawUser);
    env->ReleaseStringUTFChars(userPromptString, rawUser);
    std::string systemPrompt;
    if (systemPromptString != nullptr) {
        const char * rawSystem = env->GetStringUTFChars(systemPromptString, nullptr);
        if (rawSystem != nullptr) {
            systemPrompt.assign(rawSystem);
            env->ReleaseStringUTFChars(systemPromptString, rawSystem);
        }
    }

    std::string prompt;
    const char * chatTemplate = llama_model_chat_template(state->model, nullptr);
    if (chatTemplate != nullptr) {
        llama_chat_message messages[2];
        size_t messageCount = 0;
        if (!systemPrompt.empty()) messages[messageCount++] = {"system", systemPrompt.c_str()};
        messages[messageCount++] = {"user", userPrompt.c_str()};
        const size_t capacity = (systemPrompt.size() + userPrompt.size()) * 4 + 4096;
        std::vector<char> formatted(capacity);
        const int32_t formattedLength = llama_chat_apply_template(
            chatTemplate, messages, messageCount, true, formatted.data(), static_cast<int32_t>(formatted.size()));
        if (formattedLength > 0 && static_cast<size_t>(formattedLength) < formatted.size()) {
            prompt.assign(formatted.data(), static_cast<size_t>(formattedLength));
        }
    }
    if (prompt.empty()) {
        if (!systemPrompt.empty()) prompt = "System: " + systemPrompt + "\n\n";
        prompt += "User: " + userPrompt + "\nAssistant:";
    }

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onToken = callbackClass == nullptr ? nullptr :
        env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)Z");
    if (onToken == nullptr) return -1;

    std::vector<llama_token> tokens(prompt.size() + 16);
    int32_t tokenCount = llama_tokenize(
        state->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
        tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (tokenCount < 0) {
        tokens.resize(static_cast<size_t>(-tokenCount));
        tokenCount = llama_tokenize(
            state->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    }
    if (tokenCount <= 0) {
        LOGE("llama_tokenize failed");
        return -1;
    }
    tokens.resize(static_cast<size_t>(tokenCount));

    llama_memory_clear(llama_get_memory(state->context), true);
    llama_batch batch = llama_batch_get_one(tokens.data(), tokenCount);
    if (llama_decode(state->context, batch) != 0) {
        LOGE("Prompt evaluation failed in llama_decode");
        return -1;
    }

    auto samplerParams = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(samplerParams);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(std::clamp(topP, 0.01f, 1.0f), 1));
    if (temperature > 0.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    }

    int generated = 0;
    char piece[4096];
    for (int i = 0; i < std::clamp(static_cast<int>(maxTokens), 1, 4096); ++i) {
        if (state->cancelled.load()) break;
        const llama_token token = llama_sampler_sample(sampler, state->context, -1);
        if (llama_vocab_is_eog(state->vocab, token)) break;

        const int pieceLength = llama_token_to_piece(
            state->vocab, token, piece, static_cast<int>(sizeof(piece)), 0, true);
        if (pieceLength < 0) {
            LOGE("llama_token_to_piece failed");
            break;
        }
        if (pieceLength > 0) {
            jstring text = env->NewStringUTF(std::string(piece, static_cast<size_t>(pieceLength)).c_str());
            if (text == nullptr) break;
            const jboolean keepGoing = env->CallBooleanMethod(callback, onToken, text);
            env->DeleteLocalRef(text);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                break;
            }
            ++generated;
            if (keepGoing != JNI_TRUE) break;
        }

        llama_batch next = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
        if (llama_decode(state->context, next) != 0) {
            LOGE("Token decode failed");
            break;
        }
    }

    llama_sampler_free(sampler);
    return generated;
}

JNIEXPORT void JNICALL
Java_com_agentx_core_inference_llamacpp_LlamaCppNativeBridge_nativeCancel(
        JNIEnv *, jobject, jlong handle) {
    AgentXContext * state = fromHandle(handle);
    if (state != nullptr) state->cancelled.store(true);
}

JNIEXPORT jlong JNICALL
Java_com_agentx_core_inference_llamacpp_LlamaCppNativeBridge_nativeGetMemoryUsage(
        JNIEnv *, jobject, jlong handle) {
    AgentXContext * state = fromHandle(handle);
    return state == nullptr ? 0 : state->modelFileBytes;
}

JNIEXPORT jboolean JNICALL
Java_com_agentx_core_inference_llamacpp_LlamaCppNativeBridge_nativeFree(
        JNIEnv *, jobject, jlong handle) {
    AgentXContext * state = fromHandle(handle);
    if (state == nullptr) return JNI_FALSE;
    std::lock_guard<std::mutex> guard(state->generationMutex);
    state->cancelled.store(true);
    if (state->context != nullptr) llama_free(state->context);
    if (state->model != nullptr) llama_model_free(state->model);
    delete state;
    return JNI_TRUE;
}

} // extern "C"
