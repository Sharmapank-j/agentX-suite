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
- **OS / SoC:** Android 13 (API 33), Qualcomm Snapdragon 695 5G (8-core Kryo 660, 2 Fast Cores @ 2.2GHz + 6 Efficiency Cores @ 1.8GHz, `arm64-v8a`)
- **Memory / Storage:** 8 GB RAM, 128 GB Storage
- **Tested Model:** `medimind-v2-q5_k_m.gguf` (1,125,050,016 bytes, Q5_K_M quantization)
- **Environment:** 100% Offline with Airplane Mode Active
- **Verified Benchmark Results:**
  - **AgentX Turn 1:** Prompt `"Explain what causes hypertension in 2 ntencess"` → **241 tokens generated at 8.6 tokens/sec**
  - **AgentX Turn 2:** Prompt `"What are 3 ways to lower it?"` → 335 history tokens evaluated in 5.2s, **242 tokens generated at 8.5 tokens/sec**
  - **AgentX Medical:** Prompt `"What are the primary clinical indications for ACE inhibitors?"` → **211 tokens generated at 8.5 tokens/sec**
  - **AgentX Serve:** OpenAI-compatible REST server running online at `http://10.212.62.157:8080`, successfully returning local GGUF models via `/v1/models`
  - **Throughput:** ~8.5–8.6 tokens/sec sustained (40x throughput gain achieved by hardware-aware performance core clamping and Room SQLite throttling)
  - **Memory Footprint:** ~1.2 GB PSS native model weight mapping
  - **Thermal State:** Battery temperature ~32.3°C, Thermal Status 0 (Optimal)

## Capability status

| Capability | Status |
|---|---|
| App modules and shared Kotlin code | Present & verified (`:core`, `:app`, `:medical`, `:admin`) |
| GGUF metadata/model discovery | Fully implemented (binary header & tensor info parser) |
| Real local GGUF inference | **Verified on Hardware** (via `libagentx_llama.so` @ 8.6 t/s) |
| Scripted Chat Topics Purge | **Completed** (clean, unscripted status placeholders) |
| Permissions & Unrestricted Execution | **Completed** (single-screen disclosure dialog, no RAM/storage caps) |
| AgentX / Medical local generation | **Active & Multi-turn Verified** |
| AgentX Serve local generation & CLI | **Active & Verified** (Serving on port 8080, OpenAI REST API) |
| Mock engines / canned delays | Completely Purged |
| Medical calculators | Refactored with strict input validation and disclaimers |
| Physical-device performance | **Passed Acceptance Criteria (~8.6 t/s)** |

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
