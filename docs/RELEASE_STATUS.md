# AgentX Suite — verified project status

Last repository verification: 7 November 2026.

## Source and CI
- Default branch: `main`
- Latest source revision inspected: `5ccef43fb98dbe0abdf9eceb7e5d60c8906b11f9`
- Latest recorded CI run: [Run #48](https://github.com/Sharmapank-j/agentX-suite/actions/runs/42197108940)
- Run #48 completed successfully and produced debug APK artifacts for AgentX, AgentX Medical, and AgentX Serve.
- Workflow artifact: [agentx-suite-debug-apks](https://github.com/Sharmapank-j/agentX-suite/actions/runs/42197108940)

## Changes represented in the source
- PR #5: native llama.cpp / GGUF runtime integration across AgentX applications.
- PR #6: provider-specific Gemini and gateway credential isolation.
- The CI result verifies unit-test workflow and debug APK assembly only. It does not establish successful physical-device execution, real GGUF model loading/generation, GPU acceleration, or production readiness.

## Distribution
The currently published GitHub Release is still `v1.0.0`, published 4 November 2026. Its APK assets predate the latest source commits and must not be presented as the latest build. The workflow artifacts are newer debug builds but are not release assets.

A new versioned release must be created from the exact verified commit, with the matching three APKs and SHA-256 checksums. Do not overwrite or retarget the historical `v1.0.0` release.

## Acceptance items still requiring evidence
1. Install and launch all three latest APKs on a physical Android device.
2. Record package version codes, device/OS/ABI, and crash/logcat results.
3. Load a real local GGUF and observe generated tokens; test cancellation and unload.
4. Measure actual memory, latency, throughput, temperature, and battery.
5. Independently review medical calculator equations, boundaries, and clinical references.
6. Audit AgentX Serve authentication, TLS, CORS, request validation, rate limits, and public exposure controls.

Do not mark any item complete without recorded evidence.
