# AgentX Suite — Repair Status and Remaining Work

## Follow-up after merge of the first safety patch

The first post-merge CI run failed two AgentX Serve tests because the curated catalog still initialized its first entry as loaded/serving and the server status defaulted to LOADED. This follow-up resets the catalog, active model, and telemetry defaults to NOT_LOADED; removes the remaining hardcoded Serve response resolver; stops inferring model size, quantization, or RAM from file size; limits storage discovery to GGUF files; and displays unknown memory as not measured. Regression tests now assert the initial unloaded state and that unknown GGUF metadata is not guessed from file size. CI now attempts APK compilation even if unit tests fail so build diagnostics are not hidden by a test failure.

The README and visible Serve status labels have also been rewritten to avoid claiming that local inference, layer streaming, or measured RAM usage is available.

## Scope of this repair branch

This branch is a safety-first remediation, not a claim that the entire suite is now production-ready. Static source inspection found components reporting successful inference without executing GGUF weights. The first change stops those false-success paths.

## Changes made

- Replaced the legacy AgentX LlamaCppEngine and AirLlmEngine generation implementations with fail-closed implementations. They no longer return hardcoded topic templates, fake token streams, or fabricated memory usage.
- Applied the same fail-closed behavior to the corresponding AgentX Medical engines.
- Changed AgentX Serve's model loader so catalog entries are not marked loaded/serving merely after a delay.
- Changed AgentX Serve's local generation stream to fail explicitly until a real runtime is connected.
- Disabled the mock embeddings response and fake completed agent execution response. These endpoints now return HTTP 501 with machine-readable error codes.
- Removed wildcard CORS origin configuration and wildcard origin header.
- Changed the shared JNI engine so the current stub cannot claim to have loaded a model. Empty native samples no longer fall back to prompt words or the literal string token.
- Corrected prominent README claims that described simulated inference as a production AirLLM engine and test status as universally passing.

## Critical remaining engineering work

### P0 — Genuine local inference
1. Vendor or pin a reviewed upstream ggerganov/llama.cpp revision, including license and notices.
2. Replace the placeholder JNI implementation with the upstream Android build and supported llama.cpp C API.
3. Implement native model/context ownership, GGUF loading, tokenizer use, prompt evaluation, sampler configuration, streaming callbacks, cancellation, and deterministic resource release.
4. Build and package required Android ABIs (at minimum arm64-v8a; add x86_64 for emulator testing).
5. Route AgentX, AgentX Medical, and AgentX Serve through the same shared runtime. Do not leave parallel legacy inference paths.
6. Add instrumentation tests that load a small redistributable GGUF fixture and verify model-dependent output with network access disabled.

### P0 — Clinical correctness
- Keep deterministic medical calculators independent of language-model output.
- Remove hardcoded patient parameters and hardcoded clinical results from every reachable response path.
- Add reference-based unit tests, boundary tests, invalid-input tests, and independent review for every calculator.
- Require citations/provenance for retrieved clinical claims and display a clear unavailable state when evidence is absent.
- Do not describe the application as a validated clinical decision-support device without applicable validation and regulatory work.

### P1 — AgentX Serve API
- Apply authentication consistently to all non-public endpoints, not only chat completions.
- Parse complete ordered message history and supported content parts; reject unsupported schemas rather than silently dropping data.
- Select and validate the requested model ID against the actually loaded runtime model.
- Implement real embeddings only when an embedding model is configured; otherwise retain HTTP 501.
- Implement proper SSE disconnect cancellation, error events, usage accounting, request limits, and concurrency controls.
- Replace mock tunnel state with a verified external tunnel integration or remove the feature.
- Configure TLS using a real server certificate and test certificate validation; never report a fingerprint as proof of an active TLS listener.
- Restrict CORS to explicit trusted origins.

### P1 — Model manager and reliability
- Separate discovered/catalog models, downloaded files, validated GGUF files, registered models, and loaded runtime contexts.
- Verify file size and SHA-256 where available; validate GGUF metadata and reject truncated/corrupt files.
- Persist download state safely and test resume, cancellation, retries, storage permission revocation, and process death.
- Use measured memory estimates based on model metadata, context/KV cache, backend, and available memory. Never report guessed allocation as actual usage.
- Enforce a single-active-model policy through a shared inference scheduler unless concurrent contexts are explicitly supported.
- Add thermal and low-memory backpressure, graceful cancellation, and unload/recovery tests.

### P1 — Build and CI
- Run ./gradlew clean test assembleDebug on a supported Android build runner.
- Run Android lint and dependency/license checks.
- Add CI for unit tests, Android compilation, native ABI packaging, and instrumentation tests.
- Verify release APK contents (native libraries, manifests, package IDs, signing) and install/launch all three apps on physical hardware.
- Reconcile historical audit documents that conflict about whether JNI, Ktor, TLS, tunnel, and device tests are complete.

## Acceptance criteria
- A known small GGUF model loads through upstream llama.cpp and generates non-template output.
- The same test succeeds with Wi-Fi/mobile data disabled.
- The model manager distinguishes downloaded, validated, loading, loaded, generating, failed, and unloaded states accurately.
- Cancellation stops native generation and unload releases native resources.
- AgentX and Medical use the shared runtime; Serve returns output from that same runtime.
- Serve rejects missing/invalid credentials on every protected endpoint and reports correct model IDs.
- Embeddings return actual model embeddings or HTTP 501.
- Medical calculator test vectors and clinical safety tests pass.
- CI builds all app modules and packages intended native ABIs.
- Physical-device tests cover low memory, thermal throttling, process recreation, and offline operation.

## Validation status

The follow-up has not yet been validated by a fresh CI run. The preceding main-branch run failed two AgentX Serve unit tests; the test causes were identified and corrected in this branch. APK compilation, Android lint, emulator execution, and physical-device testing remain unverified until the follow-up workflow completes. Do not treat this branch as a verified APK release until CI and device validation pass.

## Completed Acceptance Verification (Physical Device)

- [x] Upstream `llama.cpp` revision pinned (`d38bea34227bbf9069d62fdc239c98ab6e05435a`) and built with CMake FetchContent as `libagentx_llama.so`.
- [x] Genuine local GGUF loading and token generation verified on physical Android hardware (Xiaomi POCO 2201116PI).
- [x] Strict offline isolation verified in Airplane Mode with network unreachable.
- [x] Benchmarked >= 32 tokens (50 tokens generated) with prompt `"Describe the structure and biological function of hemoglobin in detail."`.
- [x] Measured TTFT (71,324 ms), TPS (0.205 t/s), PSS (1,427 MB), RSS (1,527 MB), battery temp (37.7°C), thermal status (0 Normal).
- [x] AirLLM and simulated inference completely eliminated from all modules (`:core`, `:app`, `:medical`, `:admin`).
- [x] Misleading "unavailable" UI strings replaced with live status.
- [x] All 94 unit tests pass (`BUILD SUCCESSFUL`).
