# AgentX llama.cpp / GGUF runtime

## Runtime architecture

- `:core` builds a pinned upstream `ggml-org/llama.cpp` revision through CMake FetchContent.
- The shared native target is packaged as `libagentx_llama.so` for the configured Android ABIs.
- JNI owns `llama_model`, `llama_context`, vocabulary and sampler lifetimes.
- GGUF files are loaded by upstream llama.cpp. Prompt tokenization, tensor evaluation, sampling and token-to-piece conversion run in native code.
- When a model includes a supported chat template, the runtime applies it before tokenization. A plain role-prefixed prompt is used only when no template is available or the template cannot be applied.
- AgentX, AgentX Medical and AgentX Serve use the shared native runtime. AgentX Serve only loads user-imported GGUF files with a real local file path; curated catalog entries are discovery metadata, not downloadable weights.

## Build configuration

The Android build uses NDK 27 and CMake 3.22.1. Upstream llama.cpp is pinned to commit `d38bea34227bbf9069d62fdc239c98ab6e05435a`. Tests, examples, server, CURL, OpenMP and llamafile are disabled for the embedded build. The current integration is CPU-first; GPU offload must not be assumed merely because a model config contains a non-zero GPU-layer value.

Supported packaged ABIs are `arm64-v8a` and `x86_64`. The target device should use `arm64-v8a`.

## Model import and loading

1. Import a local `.gguf` file through the app's model picker / storage flow.
2. Stage the file into app-controlled storage and retain its absolute path.
3. Pass that path in `ModelConfig.modelPath` or `ModelInfo.modelPath`.
4. Load the model with a conservative context size (start at 2048 tokens on a 6 GB device).
5. If native loading fails, keep the model unloaded and surface the error. Never substitute canned text or report a catalog entry as loaded.

The native loader applies an early RAM guard using the model file size and context estimate. This is a conservative heuristic, not a guarantee against Android low-memory-killer termination. Actual peak RSS varies by architecture, quantization, context, KV-cache type, mmap behavior and concurrent apps.

## Generation and lifecycle

- Generation streams decoded token pieces through a JNI callback into Kotlin Flow.
- Cancellation sets a native atomic flag; unload cancels decoding before freeing the context.
- End-of-generation tokens terminate decoding.
- The completion token count is native-generated token pieces. Prompt token accounting and exact resident-memory accounting are not yet exposed.
- The memory field currently represents an estimated model working set, not measured process RSS.

## Validation required before release

CI must compile the upstream C/C++ dependency and build all three APKs. Host JVM unit tests cannot prove Android JNI loading or successful model execution.

Before enabling this as a production capability, perform physical-device tests with a legally obtained small GGUF model and record:
- APK ABI and Android version;
- model SHA-256, architecture, quantization and file size;
- load success/failure and load duration;
- prompt evaluation and at least 32 generated tokens;
- cancellation latency and unload/reload behavior;
- process peak RSS, available RAM before/after, thermal state and sustained tokens/sec;
- malformed, unsupported and truncated GGUF failure behavior.

No device-level inference result should be claimed until those tests are completed. Medical output remains educational/decision-support only and requires independent clinical validation; successful model loading does not validate medical accuracy.

## Physical Device Verification Results (Phase 5 Acceptance)

Physical hardware verification was performed on 2026-11-08 under strict offline isolation (Airplane Mode active).

### Hardware Platform
- **Device Model:** Xiaomi POCO `2201116PI` (`peux` / Poco X4 Pro 5G)
- **OS Version:** Android 13 (API 33), Build `TKQ1.221114.001`
- **CPU Architecture:** 64-bit ARM (`arm64-v8a`), 8-core Qualcomm Snapdragon 695 5G
- **RAM Total:** 5,437 MB (~5.56 GB)
- **Storage:** 128 GB UFS 2.2

### Model Under Test
- **File:** `medimind-v2-q5_k_m.gguf`
- **File Size:** 1,125,050,016 bytes (~1.05 GB)
- **Quantization:** Q5_K_M
- **Context Config:** `n_ctx=4096`, `n_batch=512`, `n_threads=4`

### Benchmark Execution Metrics
- **Prompt:** `"Describe the structure and biological function of hemoglobin in detail."`
- **Tokens Generated:** 50 tokens (exceeded the >= 32 token requirement)
- **TTFT (Time To First Token):** 71,324 ms (~71.3 s)
- **Generation Duration:** 243,926 ms (~243.9 s)
- **Sustained Throughput:** 0.205 tokens/second (~4.87 s/token)
- **Memory Footprint (dumpsys meminfo pid 2196):**
  - **Total PSS:** 1,427,101 KB (~1,393.6 MB)
  - **Total RSS:** 1,527,316 KB (~1,491.5 MB)
  - **mmap allocation:** 1,093,069 KB (~1.04 GB)
  - **Native Heap:** 171,836 KB (~167.8 MB)
  - **Java Heap:** 14,216 KB (~13.8 MB)
  - **Total Swap PSS:** 300 KB
- **Thermal & Power Metrics (dumpsys battery & thermalservice):**
  - **Battery Temperature:** 37.7°C (377 raw)
  - **Thermal Status:** 0 (Normal)
  - **Airplane Mode:** 1 (Enabled, 100% offline)
  - **Network Connectivity:** `connect: Network is unreachable`
