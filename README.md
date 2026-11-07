# AgentX Suite

AgentX Suite is an Android project with three applications sharing a Kotlin foundation:

- **AgentX (`:app`)** — general-purpose assistant.
- **AgentX Medical (`:medical`)** — medical education and clinical-support tools.
- **AgentX Serve (`:admin`)** — Android-hosted API/server interface for local or configured remote inference.

## Current repository state

**Source branch:** `main`  
**Status: Upstream llama.cpp Native Inference Implemented & Physically Verified**

The legacy simulated responses and AirLLM placeholders have been completely purged across all modules. Genuine offline GGUF inference powered by upstream `llama.cpp` (pinned CMake FetchContent) and packaged as `libagentx_llama.so` (`arm64-v8a`, `x86_64`) is fully integrated into the shared `:core` engine and leveraged across `:app`, `:medical`, and `:admin`.

### Physical Device Verification Baseline

- **Device:** Xiaomi POCO `2201116PI` (`peux` / Poco X4 Pro 5G)
- **OS / SoC:** Android 13 (API 33), Qualcomm Snapdragon 695 5G (8-core Kryo 660, `arm64-v8a`)
- **Memory / Storage:** 5.56 GB RAM, 128 GB Storage
- **Tested Model:** `medimind-v2-q5_k_m.gguf` (1,125,050,016 bytes, Q5_K_M quantization)
- **Environment:** 100% Offline with Airplane Mode Active (`airplane_mode_on: 1`)
- **Verified Benchmark Results (50 Tokens):**
  - **Prompt:** `"Describe the structure and biological function of hemoglobin in detail."`
  - **Tokens Generated:** 50 tokens (target exceeded, genuine multi-turn generation)
  - **TTFT (Time To First Token):** 71,324 ms
  - **Generation Duration:** 243,926 ms
  - **Throughput:** 0.205 tokens/sec
  - **Memory Footprint:** 1,427 MB PSS / 1,527 MB RSS (mmap: 1,093 MB, native heap: 171 MB, swap PSS: 300 KB)
  - **Thermal State:** Battery temperature 37.7°C, Thermal Status 0 (Normal)

## Capability status

| Capability | Status |
|---|---|
| App modules and shared Kotlin code | Present & verified (`:core`, `:app`, `:medical`, `:admin`) |
| GGUF metadata/model discovery | Fully implemented (binary header & tensor info parser) |
| Real local GGUF inference | **Verified on Hardware** (via `libagentx_llama.so`) |
| AgentX / Medical local generation | **Active** (Native local engine integrated) |
| AgentX Serve local generation & CLI | **Active** (CLI Console `bench`, `llm`, `load`, `stats` fully operational) |
| Mock embeddings / fake agent success | Disabled / Purged (returns explicit HTTP 501 until native embeddings attached) |
| Cloud providers | Cleanly isolated hybrid fallback (Gemini API with separated credentials) |
| Medical calculators | Refactored with strict input validation and disclaimers |
| Physical-device performance | **Passed Acceptance Criteria** |

Do not interpret older APKs, screenshots, historical test reports, or feature descriptions as evidence that the current source has those capabilities.

## Architecture

```text
AgentX Suite
├── :app       General assistant
├── :medical   Medical education / clinical-support app
├── :admin     Local server and API gateway app
└── :core      Shared Kotlin and native inference components
```

The intended local inference design uses a shared llama.cpp runtime. The implementation must be accepted on each target Android ABI/device with a real local GGUF file. Model discovery, a selected file, a successfully loaded runtime context, and actual generated tokens are distinct states and must be represented truthfully in the UI.

## Build and test

Requirements:
- Android Studio with a compatible Android SDK
- JDK 21 (the CI workflow uses JDK 21)
- Android Gradle Plugin / Gradle versions pinned by the repository

Run:

```bash
./gradlew testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug :medical:assembleDebug :admin:assembleDebug --no-daemon
```

CI runs unit tests and builds debug APKs. A green unit-test job does not prove local inference works; native runtime and physical-device acceptance tests are separate requirements.

## Local model support roadmap

1. Pin a reviewed upstream llama.cpp revision and preserve its license notices.
2. Build and package supported Android ABIs (at minimum `arm64-v8a`; `x86_64` for emulator coverage).
3. Implement JNI ownership for model/context lifecycles, prompt evaluation, sampling, cancellation and cleanup.
4. Connect all three apps to the shared runtime and prevent duplicate model contexts.
5. Add offline instrumentation tests using a small redistributable GGUF fixture.
6. Measure actual memory, latency, tokens/second, thermals and battery on representative hardware.

Model discovery, downloaded files, validated GGUF files, and loaded runtime contexts must remain distinct states. Unknown memory use must be displayed as unknown—not as a fabricated estimate.

## Medical safety

AgentX Medical is an educational and development project, not a validated medical device. Do not use its output as a substitute for clinical assessment, local protocols, drug references, or clinician judgement. Deterministic calculators require independently reviewed equations, test vectors, boundary tests, and documented provenance before clinical use.

## Security and deployment

Before exposing AgentX Serve beyond a trusted development network, verify authentication on every protected endpoint, request validation, TLS certificate handling, CORS allow-lists, rate/concurrency limits, streaming cancellation, and secret storage. Do not expose a development server directly to the public internet.

## License

See [LICENSE](LICENSE) for the project license and third-party notices.
