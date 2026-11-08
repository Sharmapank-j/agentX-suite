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

static int detectPerformanceCores() {
    long maxFreq = 0;
    const long numCpus = sysconf(_SC_NPROCESSORS_CONF);
    if (numCpus <= 0) return 2;
    std::vector<long> freqs(numCpus, 0);
    for (int i = 0; i < numCpus; ++i) {
        char path[128];
        snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i);
        FILE * f = fopen(path, "r");
        if (f) {
            long freq = 0;
            if (fscanf(f, "%ld", &freq) == 1) {
                freqs[i] = freq;
                if (freq > maxFreq) maxFreq = freq;
            }
            fclose(f);
        }
    }
    if (maxFreq > 0) {
        int perfCount = 0;
        const long threshold = static_cast<long>(maxFreq * 0.85);
        for (int i = 0; i < numCpus; ++i) {
            if (freqs[i] >= threshold) perfCount++;
        }
        if (perfCount >= 1 && perfCount <= 4) return perfCount;
    }
    return (numCpus >= 8) ? 2 : std::min<int>(4, std::max<int>(1, static_cast<int>(numCpus)));
}

// Fill batch with tokens at consecutive positions starting from pos_0, output logits for the last token
static void batch_set_tokens(llama_batch_ext * batch, const llama_token * tokens, int32_t n_tokens, llama_pos pos_0) {
    if (batch == nullptr || tokens == nullptr || n_tokens <= 0) return;
    llama_batch_ext_clear(batch);
    int32_t last_idx = -1;
    for (int32_t i = 0; i < n_tokens; ++i) {
        const int32_t idx = llama_batch_ext_add_token(batch, 0, tokens[i]);
        const llama_pos pos = pos_0 + i;
        llama_batch_ext_set_pos(batch, idx, &pos);
        last_idx = idx;
    }
    if (last_idx >= 0) {
        llama_batch_ext_set_output_logits(batch, last_idx, true);
    }
}

struct ChatTurn {
    std::string role;
    std::string content;
};

static std::vector<ChatTurn> parsePromptTurns(const std::string & systemPrompt, const std::string & userPrompt) {
    std::vector<ChatTurn> turns;
    if (!systemPrompt.empty()) {
        turns.push_back({"system", systemPrompt});
    }

    const std::string uTag = "User:";
    const std::string aTag = "Assistant:";
    const std::string sTag = "System:";

    const bool hasRoleMarkers = (userPrompt.find(uTag) != std::string::npos ||
                                 userPrompt.find(aTag) != std::string::npos);

    if (!hasRoleMarkers) {
        turns.push_back({"user", userPrompt});
        return turns;
    }

    std::string currentRole = "user";
    std::string currentContent;

    std::vector<std::string> lines;
    size_t start = 0;
    while (start < userPrompt.size()) {
        size_t end = userPrompt.find('\n', start);
        if (end == std::string::npos) end = userPrompt.size();
        lines.push_back(userPrompt.substr(start, end - start));
        start = end + 1;
    }

    for (const auto & line : lines) {
        std::string trimmed = line;
        size_t first = trimmed.find_first_not_of(" \t\r");
        if (first != std::string::npos) trimmed = trimmed.substr(first);

        if (trimmed.rfind("User:", 0) == 0) {
            if (!currentContent.empty()) {
                turns.push_back({currentRole, currentContent});
                currentContent.clear();
            }
            currentRole = "user";
            currentContent = trimmed.substr(5);
            if (!currentContent.empty() && currentContent[0] == ' ') currentContent.erase(0, 1);
        } else if (trimmed.rfind("Assistant:", 0) == 0) {
            if (!currentContent.empty()) {
                turns.push_back({currentRole, currentContent});
                currentContent.clear();
            }
            currentRole = "assistant";
            currentContent = trimmed.substr(10);
            if (!currentContent.empty() && currentContent[0] == ' ') currentContent.erase(0, 1);
        } else if (trimmed.rfind("System:", 0) == 0) {
            if (!currentContent.empty()) {
                turns.push_back({currentRole, currentContent});
                currentContent.clear();
            }
            std::string sysContent = trimmed.substr(7);
            if (!sysContent.empty() && sysContent[0] == ' ') sysContent.erase(0, 1);
            // Avoid duplicating system instructions if already inserted
            if (turns.empty() || turns.front().role != "system") {
                currentRole = "system";
                currentContent = sysContent;
            }
        } else {
            if (!currentContent.empty()) currentContent += "\n";
            currentContent += line;
        }
    }
    if (!currentContent.empty()) {
        turns.push_back({currentRole, currentContent});
    }
    return turns;
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

    LOGI("Loading GGUF model: %s (%lld bytes). Total RAM: %llu MB, MemAvailable: %llu MB (Unrestricted execution mode)",
         path.c_str(), (long long)fileInfo.st_size,
         (unsigned long long)(totalRam / (1024ULL * 1024ULL)),
         (unsigned long long)(memAvailable / (1024ULL * 1024ULL)));

    // Unrestricted loading: No refusal checks based on RAM or storage limits.

    const uint64_t contextEstimate = static_cast<uint64_t>(
        std::clamp(static_cast<int>(contextLength), 128, 131072)) * 64ULL * 1024ULL;
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
    // Mobile safety clamp: on mobile devices, context must never exceed 4096 tokens
    // to avoid allocating multiple gigabytes of KV-cache that trigger OS Out-Of-Memory (LMK) kills.
    const int requestedCtx = static_cast<int>(contextLength);
    const int safeCtx = (requestedCtx <= 0) ? 2048 : std::clamp(requestedCtx, 128, 4096);
    contextParams.n_ctx = static_cast<uint32_t>(safeCtx);
    contextParams.n_batch = std::min<uint32_t>(contextParams.n_ctx, 512);
    contextParams.n_ubatch = std::min<uint32_t>(contextParams.n_batch, 256);

    // Mobile threading: snap to hardware-detected performance cores to prevent severe
    // thread synchronization barrier stalls across weak asymmetric Cortex-A55 efficiency cores on mobile SoCs.
    const int perfCores = detectPerformanceCores();
    int threads = static_cast<int>(nThreads);
    if (threads <= 0 || threads > perfCores) {
        threads = perfCores;
    }
    contextParams.n_threads = std::clamp(threads, 1, 4);
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

    LOGI("Loaded GGUF model with llama.cpp; working set=%lld bytes, n_ctx=%u, n_batch=%u, threads=%u",
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

    // Determine prompt formatting
    std::string prompt;
    const bool isPreformatted = (userPrompt.find("<|start_header_id|>") != std::string::npos ||
                                 userPrompt.find("<|im_start|>") != std::string::npos ||
                                 userPrompt.find("[INST]") != std::string::npos);

    if (isPreformatted) {
        prompt = userPrompt;
    } else {
        const auto turns = parsePromptTurns(systemPrompt, userPrompt);
        const char * chatTemplate = llama_model_chat_template(state->model, nullptr);

        if (chatTemplate != nullptr) {
            std::vector<llama_chat_message> chatMsgs;
            chatMsgs.reserve(turns.size());
            for (const auto & t : turns) {
                chatMsgs.push_back({ t.role.c_str(), t.content.c_str() });
            }

            size_t capacity = 4096;
            for (const auto & t : turns) capacity += t.content.size() * 3;
            std::vector<char> formatted(capacity);
            int32_t formattedLength = llama_chat_apply_template(
                chatTemplate, chatMsgs.data(), chatMsgs.size(), true, formatted.data(), static_cast<int32_t>(formatted.size()));
            if (formattedLength > static_cast<int32_t>(formatted.size())) {
                formatted.resize(static_cast<size_t>(formattedLength) + 1);
                formattedLength = llama_chat_apply_template(
                    chatTemplate, chatMsgs.data(), chatMsgs.size(), true, formatted.data(), static_cast<int32_t>(formatted.size()));
            }
            if (formattedLength > 0 && static_cast<size_t>(formattedLength) < formatted.size()) {
                prompt.assign(formatted.data(), static_cast<size_t>(formattedLength));
                LOGI("Applied native model chat template (%d bytes)", formattedLength);
            }
        }

        if (prompt.empty()) {
            if (!systemPrompt.empty()) prompt += "System: " + systemPrompt + "\n\n";
            for (const auto & t : turns) {
                if (t.role == "system") continue;
                if (t.role == "user") prompt += "User: " + t.content + "\n";
                else if (t.role == "assistant") prompt += "Assistant: " + t.content + "\n";
            }
            prompt += "Assistant:";
            LOGI("Using standard fallback template (%zu bytes)", prompt.size());
        }
    }

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onToken = callbackClass == nullptr ? nullptr :
        env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)Z");
    if (onToken == nullptr) return -1;

    // Tokenize the prompt using official two-pass sizing
    const int n_prompt = -llama_tokenize(
        state->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
        nullptr, 0, true, true);
    if (n_prompt <= 0) {
        LOGE("llama_tokenize size query failed for prompt length %zu", prompt.size());
        return -1;
    }

    std::vector<llama_token> promptTokens(static_cast<size_t>(n_prompt));
    if (llama_tokenize(
            state->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            promptTokens.data(), static_cast<int32_t>(promptTokens.size()), true, true) < 0) {
        LOGE("llama_tokenize failed to tokenize prompt");
        return -1;
    }

    const uint32_t n_ctx = llama_n_ctx(state->context);
    if (static_cast<uint32_t>(promptTokens.size()) >= n_ctx) {
        const int32_t maxAllowed = std::max<int32_t>(16, static_cast<int32_t>(n_ctx) - 64);
        LOGI("Prompt length (%zu) exceeds context window (%u); truncating to latest %d tokens",
             promptTokens.size(), n_ctx, maxAllowed);
        promptTokens.erase(promptTokens.begin(), promptTokens.begin() + (promptTokens.size() - maxAllowed));
    }

    // Reset KV cache for clean generation session
    llama_memory_t mem = llama_get_memory(state->context);
    if (mem != nullptr) {
        llama_memory_clear(mem, true);
    }

    llama_batch_ext * batch = llama_batch_ext_init(state->context);
    if (batch == nullptr) {
        LOGE("llama_batch_ext_init failed");
        return -1;
    }

    const int32_t n_batch = static_cast<int32_t>(llama_n_batch(state->context));
    int32_t n_pos = 0;

    LOGI("Tokenized prompt into %zu tokens. Evaluating prompt (n_batch=%d)...", promptTokens.size(), n_batch);

    // Evaluate prompt in chunks of n_batch
    for (int32_t i = 0; i < (int32_t)promptTokens.size(); i += n_batch) {
        if (state->cancelled.load()) {
            llama_batch_ext_free(batch);
            return 0;
        }
        const int32_t n_eval = std::min((int32_t)promptTokens.size() - i, n_batch);
        LOGI("Evaluating prompt chunk %d to %d (%d tokens)...", i, i + n_eval, n_eval);
        batch_set_tokens(batch, promptTokens.data() + i, n_eval, i);
        if (llama_process(state->context, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) {
            LOGE("Prompt evaluation failed in llama_process at chunk offset %d", i);
            llama_batch_ext_free(batch);
            return -1;
        }
        n_pos = i + n_eval;
    }
    LOGI("Prompt evaluation finished successfully (%zu tokens at n_pos=%d).", promptTokens.size(), n_pos);

    auto samplerParams = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(samplerParams);
    if (repetitionPenalty > 1.0f) {
        const int32_t n_vocab = llama_vocab_n_tokens(state->vocab);
        llama_sampler_chain_add(sampler, llama_sampler_init_penalties(n_vocab, 64, repetitionPenalty, 0.0f, 0.0f));
    }
    if (topK > 0) {
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(std::max(1, static_cast<int>(topK))));
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(std::clamp(topP, 0.01f, 1.0f), 1));
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

    const int maxGen = std::clamp(static_cast<int>(maxTokens), 1, 4096);
    LOGI("Starting token generation loop (maxTokens=%d, promptTokens=%zu, n_pos=%d, n_ctx=%u)...",
         maxGen, promptTokens.size(), n_pos, n_ctx);

    for (int i = 0; i < maxGen; ++i) {
        if (state->cancelled.load()) {
            LOGI("Generation cancelled by user at step %d", i);
            break;
        }
        if (static_cast<uint32_t>(n_pos) >= n_ctx - 1) {
            LOGI("Context window limit reached (%u). Gracefully finishing generation.", n_ctx);
            break;
        }

        const llama_token token = llama_sampler_sample(sampler, state->context, -1);
        llama_sampler_accept(sampler, token);
        if (i == 0) {
            LOGI("First token sampled from model: %d", token);
        }

        if (llama_vocab_is_eog(state->vocab, token)) {
            LOGI("EOG token (%d) reached at generation step %d", token, i);
            break;
        }

        const int pieceLength = llama_token_to_piece(
            state->vocab, token, piece, static_cast<int>(sizeof(piece)), 0, true);
        if (pieceLength < 0) {
            LOGE("llama_token_to_piece buffer overflow (needed %d) for token %d", -pieceLength, token);
        } else if (pieceLength > 0) {
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
                    if (generated % 10 == 0) {
                        LOGI("Streaming generation: %d tokens emitted so far", generated);
                    }
                    if (keepGoing != JNI_TRUE) break;
                }
            } else {
                ++generated;
            }
        }

        // Prepare next batch with the single newly sampled token at position n_pos
        batch_set_tokens(batch, &token, 1, n_pos);
        n_pos += 1;

        if (llama_process(state->context, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) {
            LOGE("llama_process decode failed at step %d", generated);
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

    LOGI("Finished native generation. Emitted tokens: %d", generated);
    llama_batch_ext_free(batch);
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
