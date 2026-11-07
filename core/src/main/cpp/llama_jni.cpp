#include <jni.h>
#include <android/log.h>
#include <llama.h>

#include <atomic>
#include <algorithm>
#include <codecvt>
#include <locale>
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

static jstring utf8ToJString(JNIEnv * env, const char * bytes, size_t length) {
    if (bytes == nullptr || length == 0) {
        return env->NewStringUTF("");
    }
    try {
        std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t> converter;
        const std::u16string utf16 = converter.from_bytes(bytes, bytes + length);
        return env->NewString(reinterpret_cast<const jchar *>(utf16.data()), static_cast<jsize>(utf16.size()));
    } catch (...) {
        std::string safe(bytes, length);
        return env->NewStringUTF(safe.c_str());
    }
}

static size_t getCompleteUtf8Length(const std::string & s) {
    if (s.empty()) return 0;
    const size_t len = s.size();
    size_t i = len;
    while (i > 0 && i > (len > 4 ? len - 4 : 0)) {
        --i;
        unsigned char c = static_cast<unsigned char>(s[i]);
        if ((c & 0x80) == 0) {
            return len;
        }
        if ((c & 0xC0) == 0xC0) {
            size_t needed = 1;
            if ((c & 0xE0) == 0xC0) needed = 2;
            else if ((c & 0xF0) == 0xE0) needed = 3;
            else if ((c & 0xF8) == 0xF0) needed = 4;

            size_t available = len - i;
            if (available < needed) {
                return i;
            } else {
                return len;
            }
        }
    }
    return len;
}

static uint64_t readMemAvailableBytes() {
    FILE * f = fopen("/proc/meminfo", "r");
    if (!f) return 0;
    char line[256];
    unsigned long long memAvailableKb = 0;
    while (fgets(line, sizeof(line), f)) {
        if (sscanf(line, "MemAvailable: %llu kB", &memAvailableKb) == 1) {
            break;
        }
    }
    fclose(f);
    return memAvailableKb * 1024ULL;
}

static uint64_t readTotalPhysicalBytes() {
    const long pages = sysconf(_SC_PHYS_PAGES);
    const long pageSize = sysconf(_SC_PAGESIZE);
    return (pages > 0 && pageSize > 0) ? static_cast<uint64_t>(pages) * static_cast<uint64_t>(pageSize) : 0;
}

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
        LOGE("GGUF path is unreadable or empty: %s", path.c_str());
        return 0;
    }

    const uint64_t totalRam = readTotalPhysicalBytes();
    const uint64_t memAvailable = readMemAvailableBytes();

    LOGI("Checking model load for %s (%lld bytes). Total RAM: %llu MB, MemAvailable: %llu MB",
         path.c_str(), (long long)fileInfo.st_size,
         (unsigned long long)(totalRam / (1024ULL * 1024ULL)),
         (unsigned long long)(memAvailable / (1024ULL * 1024ULL)));

    if (totalRam > 0 && static_cast<uint64_t>(fileInfo.st_size) > totalRam * 80ULL / 100ULL) {
        LOGE("Refusing GGUF load: model file size (%lld bytes) exceeds 80%% of physical RAM (%llu bytes)",
             (long long)fileInfo.st_size, (unsigned long long)totalRam);
        return 0;
    }

    const uint64_t contextEstimate = static_cast<uint64_t>(
        std::clamp(static_cast<int>(contextLength), 256, 16384)) * 64ULL * 1024ULL;
    const uint64_t estimatedWorkingSet = static_cast<uint64_t>(fileInfo.st_size) + contextEstimate;

    auto * state = new AgentXContext();
    state->modelFileBytes = static_cast<jlong>(estimatedWorkingSet);

    llama_model_params modelParams = llama_model_default_params();
    modelParams.load_mode = LLAMA_LOAD_MODE_MMAP;
    modelParams.n_gpu_layers = std::max(0, static_cast<int>(gpuLayers));
    state->model = llama_model_load_from_file(path.c_str(), modelParams);
    if (state->model == nullptr) {
        LOGE("llama_model_load_from_file failed for: %s", path.c_str());
        delete state;
        return 0;
    }

    llama_context_params contextParams = llama_context_default_params();
    contextParams.n_ctx = static_cast<uint32_t>(std::clamp(static_cast<int>(contextLength), 256, 16384));
    contextParams.n_batch = std::min<uint32_t>(contextParams.n_ctx, 512);
    contextParams.n_ubatch = std::min<uint32_t>(contextParams.n_batch, 128);

    int threads = static_cast<int>(nThreads);
    if (threads <= 0) {
        const long onlineCpus = sysconf(_SC_NPROCESSORS_ONLN);
        threads = onlineCpus > 0 ? static_cast<int>(onlineCpus) : 4;
    }
    contextParams.n_threads = std::clamp(threads, 1, 16);
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

    LOGI("Loaded GGUF model with llama.cpp; estimated working set=%lld bytes, n_ctx=%u, n_batch=%u, threads=%u",
         (long long) state->modelFileBytes, contextParams.n_ctx, contextParams.n_batch, contextParams.n_threads);
    return static_cast<jlong>(reinterpret_cast<intptr_t>(state));
}

static jint internalGenerate(
        JNIEnv * env, jlong handle, jstring systemPromptString, jstring userPromptString,
        jint maxTokens, jfloat temperature, jfloat topP, jint topK, jfloat repetitionPenalty,
        jint seed, jobjectArray stopSequencesArray, jobject callback) {
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

    std::vector<std::string> stopSequences;
    if (stopSequencesArray != nullptr) {
        const jsize numStops = env->GetArrayLength(stopSequencesArray);
        for (jsize s = 0; s < numStops; ++s) {
            auto stopStr = static_cast<jstring>(env->GetObjectArrayElement(stopSequencesArray, s));
            if (stopStr != nullptr) {
                const char * rawStop = env->GetStringUTFChars(stopStr, nullptr);
                if (rawStop != nullptr) {
                    if (strlen(rawStop) > 0) {
                        stopSequences.emplace_back(rawStop);
                    }
                    env->ReleaseStringUTFChars(stopStr, rawStop);
                }
                env->DeleteLocalRef(stopStr);
            }
        }
    }

    std::string prompt;
    const bool alreadyFormatted = userPrompt.find("<|") != std::string::npos ||
                                  userPrompt.find("[INST]") != std::string::npos ||
                                  userPrompt.find("<start_of_turn>") != std::string::npos;
    if (!alreadyFormatted) {
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
    }
    if (prompt.empty()) {
        if (alreadyFormatted) {
            prompt = userPrompt;
        } else {
            if (!systemPrompt.empty()) prompt = "System: " + systemPrompt + "\n\n";
            prompt += "User: " + userPrompt + "\nAssistant:";
        }
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

    const uint32_t n_ctx = llama_n_ctx(state->context);
    if (static_cast<uint32_t>(tokenCount) >= n_ctx) {
        const int32_t maxAllowed = std::max<int32_t>(16, static_cast<int32_t>(n_ctx) - 64);
        LOGI("Prompt length (%d) exceeds context window (%u); truncating to latest %d tokens",
             tokenCount, n_ctx, maxAllowed);
        tokens.erase(tokens.begin(), tokens.begin() + (tokenCount - maxAllowed));
        tokenCount = static_cast<int32_t>(tokens.size());
    }

    llama_memory_clear(llama_get_memory(state->context), true);

    const int32_t n_batch = static_cast<int32_t>(llama_n_batch(state->context));
    for (int32_t i = 0; i < tokenCount; i += n_batch) {
        if (state->cancelled.load()) return 0;
        const int32_t n_eval = std::min(tokenCount - i, n_batch);
        llama_batch batch = llama_batch_get_one(tokens.data() + i, n_eval);
        if (llama_decode(state->context, batch) != 0) {
            LOGE("Prompt evaluation failed in llama_decode at chunk offset %d (batch size %d)", i, n_eval);
            return -1;
        }
    }

    auto samplerParams = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(samplerParams);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(std::max(1, static_cast<int>(topK))));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(std::clamp(topP, 0.01f, 1.0f), 1));
    if (repetitionPenalty > 1.0f) {
        const int32_t n_vocab = llama_vocab_n_tokens(state->vocab);
        llama_sampler_chain_add(sampler, llama_sampler_init_penalties(n_vocab, 64, repetitionPenalty, 0.0f, 0.0f));
    }
    if (temperature > 0.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        const uint32_t s = seed > 0 ? static_cast<uint32_t>(seed) : LLAMA_DEFAULT_SEED;
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(s));
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    }

    int generated = 0;
    char piece[4096];
    std::string utf8Buffer;
    std::string totalGeneratedText;

    for (int i = 0; i < std::clamp(static_cast<int>(maxTokens), 1, 4096); ++i) {
        if (state->cancelled.load()) break;
        if (static_cast<uint32_t>(tokenCount + generated) >= n_ctx - 1) {
            LOGI("Context window limit reached (%u). Gracefully finishing generation.", n_ctx);
            break;
        }

        const llama_token token = llama_sampler_sample(sampler, state->context, -1);
        if (llama_vocab_is_eog(state->vocab, token)) break;

        const int pieceLength = llama_token_to_piece(
            state->vocab, token, piece, static_cast<int>(sizeof(piece)), 0, true);
        if (pieceLength < 0) {
            LOGE("llama_token_to_piece failed");
            break;
        }
        if (pieceLength > 0) {
            utf8Buffer.append(piece, static_cast<size_t>(pieceLength));
            const size_t completeLen = getCompleteUtf8Length(utf8Buffer);
            if (completeLen > 0) {
                std::string pieceStr(utf8Buffer.data(), completeLen);
                totalGeneratedText.append(pieceStr);

                // Stop sequence check
                bool hitStop = false;
                for (const auto & stopSeq : stopSequences) {
                    if (!stopSeq.empty() && totalGeneratedText.find(stopSeq) != std::string::npos) {
                        hitStop = true;
                        break;
                    }
                }
                if (hitStop) {
                    LOGI("Stop sequence encountered; terminating generation.");
                    break;
                }

                jstring text = utf8ToJString(env, utf8Buffer.data(), completeLen);
                utf8Buffer.erase(0, completeLen);
                if (text != nullptr) {
                    const jboolean keepGoing = env->CallBooleanMethod(callback, onToken, text);
                    env->DeleteLocalRef(text);
                    if (env->ExceptionCheck()) {
                        env->ExceptionClear();
                        break;
                    }
                    ++generated;
                    if (keepGoing != JNI_TRUE) break;
                }
            } else {
                ++generated;
            }
        }

        llama_batch next = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
        if (llama_decode(state->context, next) != 0) {
            LOGI("Decode stopped at step %d", generated);
            break;
        }
    }

    if (!utf8Buffer.empty()) {
        jstring text = utf8ToJString(env, utf8Buffer.data(), utf8Buffer.size());
        if (text != nullptr) {
            env->CallBooleanMethod(callback, onToken, text);
            env->DeleteLocalRef(text);
            if (env->ExceptionCheck()) env->ExceptionClear();
        }
        utf8Buffer.clear();
    }

    llama_sampler_free(sampler);
    return generated;
}

// Full 12-argument JNI method
JNIEXPORT jint JNICALL
Java_com_agentx_core_inference_llamacpp_LlamaCppNativeBridge_nativeGenerate(
        JNIEnv * env, jobject, jlong handle, jstring systemPromptString, jstring userPromptString,
        jint maxTokens, jfloat temperature, jfloat topP, jint topK, jfloat repetitionPenalty,
        jint seed, jobjectArray stopSequencesArray, jobject callback) {
    return internalGenerate(env, handle, systemPromptString, userPromptString,
                            maxTokens, temperature, topP, topK, repetitionPenalty,
                            seed, stopSequencesArray, callback);
}

// Backward-compatible 7-argument overload
JNIEXPORT jint JNICALL
Java_com_agentx_core_inference_llamacpp_LlamaCppNativeBridge_nativeGenerateLegacy(
        JNIEnv * env, jobject, jlong handle, jstring systemPromptString, jstring userPromptString,
        jint maxTokens, jfloat temperature, jfloat topP, jobject callback) {
    return internalGenerate(env, handle, systemPromptString, userPromptString,
                            maxTokens, temperature, topP, 40, 1.1f, 0, nullptr, callback);
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
