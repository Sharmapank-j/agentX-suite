# AgentX Suite: Phased Upgrade & Implementation Roadmap

> **Target Platform:** Android-first, offline-first, open-source AI ecosystem  
> **Development Environment:** Android Studio, Kotlin 2.0, Gradle Version Catalog, Android NDK (r26b+), CMake 3.22.1+, Jetpack Compose, Room  
> **Repository:** `https://github.com/Sharmapank-j/agentX-suite`  

---

## 1. Master Strategic Roadmap Overview

```
                                ┌───────────────────────────────────────────────┐
                                │             AGENTX ROADMAP PHASES             │
                                └───────────────────────┬───────────────────────┘
                                                        │
          ┌─────────────────────────────────────────────┼─────────────────────────────────────────────┐
          ▼                                             ▼                                             ▼
  [Phases 0, 1, 3, 5]                               [Phase 2]                                   [Phases 4, 6, 7, 8]
  ✅ FOUNDATIONS COMPLETE                    ⚡ NATIVE C++ INFERENCE                     🚀 ADVANCED ECOSYSTEM
  • Full Repository Audit                    • Android NDK & CMake                       • Multi-Device LAN Discovery (NSD)
  • Shared :core Architecture                • libllama.so (arm64-v8a)                   • Long-Term Memory (Episodic)
  • AgentX Serve Room Persistence            • JNI Tokenizer & KV Cache                  • MCP (Model Context Protocol)
  • 14 Validated Clinical Calculators        • Native GGUF Tensor Execution              • sqlite-vec On-Device RAG
  • Backgrounded UI Orchestration            • Bounded Low-Memory Offloading             • Zero-Config Pairing & ASP v1.0
```

---

## 2. Detailed Phase Breakdown & Milestones

### Phase 0: Repository & Architecture Audit
* **Status:** ✅ **COMPLETED**
* **Deliverables:**
  - Audited 20 core subsystems across `:app`, `:medical`, and `:admin`.
  - Authored `docs/FULL_ARCHITECTURE_AUDIT.md` and `docs/IMPLEMENTATION_STATUS.md`.
  - Identified simulated vs. genuine code paths; established baseline test pass rate (94/94 tests).

---

### Phase 1: Shared Modular Foundation (`:core`)
* **Status:** ✅ **COMPLETED**
* **Deliverables:**
  - Configured `:core` as a standalone Android Library module.
  - Defined universal typed inference models: `InferenceProvider`, `ModelInfo`, `GenerationRequest`, `GenerationEvent`, `GenerationProvenance`.
  - Implemented `ProviderRouter` enforcing zero-stream-mixing invariant.
  - Implemented `GgufMetadataParser` reading binary GGUF headers directly from disk.
  - Implemented `OpenAiCompatibleProvider` using Ktor Client HTTP/SSE.

---

### Phase 2: Genuine Native Inference (`llama.cpp` + NDK)
* **Status:** 🟡 **IN PROGRESS (Next Priority)**
* **Objective:** Replace Kotlin layer-streaming heuristic with genuine C++ GGUF tensor computation on device.
* **Work Packages:**
  1. **CMake & NDK Configuration**: Configure CMake build script in `core/src/main/cpp/CMakeLists.txt` targeting `arm64-v8a` and `x86_64`.
  2. **llama.cpp Source Tree**: Integrate `llama.cpp` submodule (ggml, ggml-cpu, llama).
  3. **JNI Bridge (`llama-jni.cpp`)**:
     - `nativeLoadModel(path, params)`: Maps GGUF file via `llama_load_model_from_file`.
     - `nativeInitContext(modelPtr, contextParams)`: Initializes context and KV cache (`llama_new_context_with_model`).
     - `nativeTokenize(contextPtr, text)`: Converts input prompt to integer token IDs.
     - `nativeEval(contextPtr, tokens)`: Evaluates prompt and processes logits.
     - `nativeSampleToken(contextPtr)`: Samples next token using temperature / top-p sampler.
     - `nativeFree(contextPtr, modelPtr)`: Deterministically releases native memory.
  4. **Kotlin Coroutine Flow Adapter**: Wraps native sampling loop in a non-blocking `Flow<GenerationEvent>` with cooperative cancellation.

---

### Phase 3: AgentX Serve Production Hardening
* **Status:** ✅ **COMPLETED**
* **Deliverables:**
  - Embedded Ktor / Socket server listening on ports `8080` (HTTP) and `8443` (TLS).
  - Room database `AdminDatabase` persisting API keys (`ApiKeyEntity`), client sessions, and audit logs.
  - Zero-trust authentication enforced by default (`isAuthEnforced = true`).
  - Text-only diagnostic CLI console tab for server statistics.
  - Live hardware telemetry (battery temp `36.7°C`, thermal state, charging status).
  - Verified live on physical Android 13 device (`Redmi Note 11 Pro+ 5G`).

---

### Phase 4: Device-to-Device Provider Integration
* **Status:** ✅ **COMPLETED (LAN & Localhost) / 🟡 Extended Multi-Node Discovery Pending**
* **Deliverables:**
  - Both `AgentX` and `AgentX Medical` connect to `AgentX Serve` via `http://127.0.0.1:8080` and LAN IPs.
  - Next milestone: Automatic mDNS / SSDP LAN Discovery via Android `NsdManager` to eliminate manual IP entry.

---

### Phase 5: AgentX Medical Clinical Hardening
* **Status:** ✅ **COMPLETED**
* **Deliverables:**
  - 14 validated deterministic calculators (eGFR, CHA2DS2-VASc, HAS-BLED, Anion Gap with Delta-Delta ratio, Corrected Calcium, Corrected Sodium, Osmolality/Gap, QTc, Wells DVT, Wells PE, CURB-65, MELD, APGAR, BMI/BSA).
  - Monotonic risk mapping corrected (CHA2DS2-VASc score 7 = 11.2%, score 8 = 12.5%, score 9 = 15.2%).
  - Hardened `DrugSafetyChecker` with explicit clinical disclaimer and zero false reassurance.
  - Tested across 15 automated test cases in `ClinicalCalculatorsTest`.
  - Verified live on physical Android device with staged 1.05 GB model.

---

### Phase 6: Advanced Intelligence (Memory, RAG, MCP)
* **Status:** ⚪ **PLANNED**
* **Work Packages:**
  1. **Long-Term Memory**: Implement profile memory, episodic memory, and project memory with user review/deletion controls.
  2. **Local RAG Pipeline**: Ingest PDF, DOCX, TXT; chunking; embedding generation; vector search.
  3. **Model Context Protocol (MCP)**: Implement official Kotlin MCP SDK client for external tool servers.
  4. **Document Intelligence**: Offline text extraction and structured synthesis.

---

### Phase 7: Multi-Device Ecosystem & ASP v1.0
* **Status:** ⚪ **PLANNED**
* **Work Packages:**
  1. **AgentX Serve Protocol (ASP) Specification**: Formal JSON schema contracts for device discovery, capabilities, model registry, inference, streaming, and telemetry.
  2. **Zero-Configuration LAN Discovery**: Register AgentX Serve as an `_agentx-serve._tcp` service via Android `NsdManager`. Client apps auto-discover active hosts on local Wi-Fi.
  3. **Multi-Node Routing**: Client route selector managing multiple active server nodes based on model capability, latency, and thermal load.

---

### Phase 8: Production Hardening & Release Engineering
* **Status:** ⚪ **PLANNED**
* **Work Packages:**
  1. **Release Keystore Signing**: Production signing configs for release APKs/AABs.
  2. **ProGuard / R8 Rules**: Optimized shrinking preserving JNI exports, Room entities, and Kotlinx serialization models.
  3. **Thermal & Battery Benchmarking**: Sustained load stress testing on physical hardware.
  4. **CI/CD Automation**: GitHub Actions releasing signed debug and release builds.
