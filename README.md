# AgentX Suite

AgentX Suite is an Android project with three applications sharing a Kotlin foundation:

- **AgentX (`:app`)** — general-purpose assistant.
- **AgentX Medical (`:medical`)** — medical education and clinical-support tools.
- **AgentX Serve (`:admin`)** — Android-hosted API/server interface for local or configured remote inference.

## Current repository state

**Source branch:** `main`  
**Latest verified source commit:** `5ccef43fb98dbe0abdf9eceb7e5d60c8906b11f9`  
**Latest verified CI run:** [Run #48](https://github.com/Sharmapank-j/agentX-suite/actions/runs/42197108940) — passed on 6 November 2026.  
**CI output:** Debug APKs for all three applications and unit-test reports.  
**Release page:** [GitHub Releases](https://github.com/Sharmapank-j/agentX-suite/releases). The published `v1.0.0` release is dated 4 November 2026 and does **not** contain the later source updates.

The CI run's APKs are downloadable as the [workflow artifact](https://github.com/Sharmapank-j/agentX-suite/actions/runs/42197108940). These are debug builds, not production-signed distribution packages. A successful CI run verifies the configured unit tests and APK assembly; it does not prove the apps have been exercised on a physical device or that a real GGUF model has generated tokens.

## Capability status

| Capability | Status |
|---|---|
| Three Android application modules | Present |
| Latest main source | Includes changes through commit `5ccef43` |
| Gemini / gateway credential isolation | Merged in PR #6 |
| llama.cpp / GGUF integration | Native runtime integration code merged in PR #5; real model load and generation still require device acceptance testing |
| Android ABI / hardware acceleration | Build/runtime coverage must be confirmed on target devices; do not assume GPU acceleration |
| CI unit tests and debug APK assembly | Passed in run #48 |
| Physical-device smoke tests | Not certified by CI; see device test records before claiming completion |
| Real GGUF inference on a phone | Not confirmed by CI alone |
| Medical calculator clinical validation | Requires independent equation, boundary, and clinical review |
| AgentX Serve exposure beyond trusted LAN | Security review and deployment hardening required |

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
./gradlew testDebugUnitTest --no-daemon --stacktrace
./gradlew assembleDebug --no-daemon
```

CI workflow: [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml).

A green CI run confirms the workflow completed; it is not a substitute for on-device tests. Before calling a release device-verified, record the device model, Android version, ABI, installed APK version codes, launch/crash checks, actual model load, generated output, cancellation, memory use, thermals, and battery impact.

## Releasing

Do not overwrite the old `v1.0.0` assets with APKs from a later commit. Create a new versioned release that points at the exact tested commit, attach the matching APKs, and provide SHA-256 checksums. Clearly identify debug vs. signed release builds and list device testing limitations. Release asset publication is a separate step from merging source and producing CI artifacts.

## Medical safety

AgentX Medical is an educational and development project, not a validated medical device. Do not use its output as a substitute for clinical assessment, local protocols, drug references, or clinician judgement. Deterministic calculators require independently reviewed equations, test vectors, boundary tests, and documented provenance before clinical use.

## Security and deployment

Before exposing AgentX Serve beyond a trusted development network, verify authentication on every protected endpoint, request validation, TLS certificate handling, CORS allow-lists, rate/concurrency limits, streaming cancellation, and secret storage. Do not expose a development server directly to the public internet.

## License

See [LICENSE](LICENSE) for the project license and third-party notices.
