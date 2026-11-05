# AgentX Suite

AgentX Suite is an Android project with three applications sharing a Kotlin foundation:

- **AgentX (`:app`)** — general-purpose assistant.
- **AgentX Medical (`:medical`)** — medical education and clinical-support tools.
- **AgentX Serve (`:admin`)** — Android-hosted API/server interface for local or configured remote inference.

## Engineering status

**Status: Under active development**

The repository previously contained simulated responses that could be mistaken for local model inference. Those paths have been disabled. **The current source does not yet include a working llama.cpp/GGUF inference runtime.** Local model loading and generation are intentionally unavailable until native inference is implemented and validated.

| Capability | Current status |
|---|---|
| App modules and shared Kotlin code | Present; build verification required |
| GGUF metadata/model discovery | Partial; discovery is not proof of runtime compatibility |
| Real local GGUF inference | Not implemented |
| AgentX / Medical local generation | Disabled until native runtime integration |
| AgentX Serve local generation | Disabled; returns an explicit unavailable error |
| Mock embeddings / fake agent success | Disabled; unavailable endpoints return HTTP 501 |
| Cloud providers | Separate code paths; require valid user configuration |
| Medical calculators | Require individual reference-based validation; not represented as clinically validated |
| Physical-device performance | Not yet verified |

Do not interpret older APKs, screenshots, historical test reports, or feature descriptions as evidence that the current source has those capabilities.

## Architecture

```text
AgentX Suite
├── :app       General assistant
├── :medical   Medical education / clinical-support app
├── :admin     Local server and API gateway app
└── :core      Shared inference abstractions, GGUF utilities and provider code
```

The intended local inference architecture is a single shared native llama.cpp runtime used by all three applications. The existing JNI bridge is currently a stub. A production implementation still needs upstream llama.cpp integration, Android ABI builds, GGUF loading, tokenization, evaluation, sampling, streaming, cancellation, resource release, and offline device tests.

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
