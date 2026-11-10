# AgentX Suite — System Architecture Specification (v1.0.0)

## 1. Architectural Philosophy & Overview

**AgentX Suite** is an enterprise-grade, offline-first, multi-module Android ecosystem delivering private, high-performance artificial intelligence directly on mobile hardware. The suite comprises three specialized applications backed by a unified shared foundation:

```
                            ┌──────────────────────────────────────┐
                            │          AgentX Suite v1.0.0         │
                            └──────────────────┬───────────────────┘
                                               │
               ┌───────────────────────────────┼───────────────────────────────┐
               │                               │                               │
        ┌──────▼──────┐                 ┌──────▼──────┐                 ┌──────▼──────┐
        │    :app     │                 │  :medical   │                 │   :admin    │
        │   AgentX    │                 │AgentX Medical│                │AgentX Serve │
        │ Flagship AI │                 │Clinical Suite│                │Inference Svr│
        └──────┬──────┘                 └──────┬──────┘                 └──────┬──────┘
               │                               │                               │
               └───────────────────────┬───────┴───────────────────────────────┘
                                       │
                               ┌───────▼───────┐
                               │     :core     │
                               │ Shared Engine │
                               └───────┬───────┘
                                       │
                        ┌──────────────┴──────────────┐
                        │                             │
                 ┌──────▼──────┐               ┌──────▼──────┐
                 │ Kotlin JVM  │               │   Native    │
                 │ Architecture│               │libagentx_   │
                 │ Coroutines  │               │  llama.so   │
                 └─────────────┘               └─────────────┘
```

### Module Responsibilities:
- **`:core`**: Pure domain models, database abstractions, hardware profilers, cryptographic security stores, and the unified `InferenceEngine` interface. Houses the native JNI bridge (`LlamaNativeBridge`) connecting upstream `llama.cpp` to Android.
- **`:app` (AgentX)**: The flagship conversational AI assistant. Features dynamic multi-model hot-swapping, agent workflows, tool calling, multi-provider cloud fallback, and dynamic dark/light M3 UI.
- **`:medical` (AgentX Medical)**: Offline-first clinical education, structured pathophysiological reasoning, and validated medical calculators. Implements HIPAA zero-exfiltration principles with strict non-diagnostic disclaimers.
- **`:admin` (AgentX Serve)**: On-device inference server and API gateway. Runs an embedded Ktor HTTP server exposing OpenAI-compatible REST endpoints (`/v1/chat/completions`, `/v1/models`), multimodal image serving, reverse tunneling, and WebRTC P2P DataChannels.

---

## 2. Multi-Module Project Structure

```text
agentx-suite/
├── app/                  # Application ID: com.agentx
│   ├── src/main/java/com/agentx/
│   │   ├── ui/           # Jetpack Compose UI (Chat, Models, Settings, Agents)
│   │   │   └── theme/    # Theme.kt (CyberObsidianColorScheme & CyberLightColorScheme)
│   │   ├── viewmodel/    # ChatViewModel, SettingsViewModel, ModelsViewModel
│   │   └── MainActivity.kt
│   └── src/main/res/     # Adaptive vector drawables & layout resources
│
├── medical/              # Application ID: com.agentx.medical.debug
│   ├── src/main/java/com/agentx/medical/
│   │   ├── clinical/     # Multi-agent clinical supervisor & diagnostic planners
│   │   ├── calculators/  # Validated calculators (CHA2DS2-VASc, Wells, eGFR, MAP)
│   │   ├── ui/           # Clinical M3 Compose UI (Consult, Specialists, Records)
│   │   │   └── theme/    # ClinicalObsidianColorScheme & ClinicalLightColorScheme
│   │   └── MainActivity.kt
│   └── src/main/res/
│
├── admin/                # Application ID: com.agentx.admin.debug
│   ├── src/main/java/com/agentx/admin/
│   │   ├── server/       # KtorAgentXServer (OpenAI REST API, SSE streaming, Multimodal)
│   │   ├── tunnel/       # TunnelManager (Pinggy SSH reverse proxy, Cloudflare, ngrok)
│   │   ├── webrtc/       # WebRTC DataChannel signaling & P2P session broker
│   │   ├── ui/           # Server dashboard, active tunnels, model telemetry, client logs
│   │   └── service/      # AgentXServerService (Foreground service, exported=false)
│   └── src/main/res/
│
├── core/                 # Shared Library: com.agentx.core
│   ├── src/main/java/com/agentx/core/
│   │   ├── inference/    # InferenceEngine, LlamaInferenceEngine, CloudInferenceEngine
│   │   ├── nativebridge/ # LlamaNativeBridge (JNI extern "C" declarations)
│   │   ├── parser/       # GGUFMetadataParser (binary header, quant type, tensor info)
│   │   ├── security/     # SecureCredentialStore (Android Keystore AES-256-GCM)
│   │   ├── profiler/     # HardwareProfiler (CPU cores, RAM PSS, Thermal Status)
│   │   └── webrtc/       # P2P DataChannel interfaces and packet protocols
│   ├── src/main/cpp/     # CMakeLists.txt & agentx_llama_jni.cpp (pinned llama.cpp)
│   └── src/test/java/    # Unit tests (100% JVM coverage with fallback ciphers)
│
└── assets/               # Canonical vector and bitmap brand assets
```

---

## 3. Native Layer & llama.cpp Integration

### 3.1 Upstream CMake FetchContent Architecture
Local GGUF execution is powered by upstream `llama.cpp` compiled as `libagentx_llama.so` targeting Android ABIs:
- `arm64-v8a` (Primary production ABI, optimized for ARMv8.2-A+ with DotProd and FP16 compute).
- `x86_64` (Development and emulator validation).

### 3.2 JNI Lifecycle & Memory Discipline
The native interface (`agentx_llama_jni.cpp`) enforces strict lifecycle discipline:
1. **Model Weight Mapping**: Uses `mmap` to load model weights directly into virtual memory, allowing the kernel to page weights on demand and preventing mobile out-of-memory crashes.
2. **Context Instantiation**: Allocates a fixed context window (default 2048 to 4096 tokens) with KV cache optimization (`f16` or quantized `q8_0` KV cache).
3. **Hardware Thread Clamping**: Dynamically detects available performance cores via `sysconf(_SC_NPROCESSORS_ONLN)` and clamps inference threads to physical high-performance cores, avoiding thermal throttling on efficiency cores.
4. **Token Generation & Sampling**: Executes autoregressive sampling using configurable temperature, top-p, top-k, and repetition penalties. Emits tokens incrementally through a JNI callback directly into Kotlin coroutine `Flow<String>`.
5. **Deterministic Cleanup**: Native destructors explicitly free KV cache memory and unload models before new models are instantiated.

```
┌─────────────────────────────────────────────────────────────────┐
│                      LlamaNativeBridge.kt                       │
└────────────────────────────────┬────────────────────────────────┘
                                 │ JNI (Java Native Interface)
┌────────────────────────────────▼────────────────────────────────┐
│                    agentx_llama_jni.cpp                         │
├────────────────────────────────┬────────────────────────────────┤
│ llama_model_load_from_file()   │ Context & KV Cache Allocation  │
│ llama_tokenize()               │ Batch Evaluation (llama_decode)│
│ llama_sample_token()           │ Native Callback: onToken(str)  │
└────────────────────────────────┬────────────────────────────────┘
                                 │ Upstream C++ Library
┌────────────────────────────────▼────────────────────────────────┐
│                     llama.cpp Core Engine                       │
│              (ggml-alloc, ggml-backend, OpenMP)                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. Inference Pipeline & Multimodal Vision Serving

The suite provides a unified inference pipeline supporting both local GGUF weights, local multimodal vision projection, and remote cloud providers:

```
                          ┌──────────────────────────┐
                          │   Inference Request      │
                          │ (Prompt + Image Base64)  │
                          └────────────┬─────────────┘
                                       │
                                       ▼
                          ┌──────────────────────────┐
                          │     InferenceEngine      │
                          │        Dispatcher        │
                          └────────────┬─────────────┘
                                       │
               ┌───────────────────────┴───────────────────────┐
               │                                               │
        Local Mode Active                                Cloud Fallback
               │                                               │
               ▼                                               ▼
    ┌──────────────────────┐                       ┌──────────────────────┐
    │ LlamaInferenceEngine │                       │ CloudInferenceEngine │
    └──────────┬───────────┘                       │ (Gemini, Claude,     │
               │                                   │  OpenAI, DeepSeek)   │
      Is Vision Present?                           └──────────────────────┘
      ┌────────┴────────┐
     Yes                No
      │                  │
      ▼                  ▼
┌──────────────┐   ┌──────────────┐
│  llama-clip  │   │ Text Token   │
│  Projector   │   │ Evaluation   │
│ (Image Embed)│   │ (KV Cache)   │
└──────┬───────┘   └──────┬───────┘
       └──────────┬───────┘
                  │
                  ▼
        ┌───────────────────┐
        │ Autoregressive    │
        │ Token Streaming   │
        │ (Flow<String>)    │
        └───────────────────┘
```

### Multimodal Vision Pipeline:
1. Incoming images (JPEG, PNG, WebP) are decoded into raw RGB bitmaps.
2. The `llama-clip` multimodal projector weights extract vision embeddings matching the language model's hidden dimension.
3. Projected visual tokens are prepended to the input sequence before language tokens are evaluated.
4. In AgentX Serve (`KtorAgentXServer.kt`), requests containing `image_url` or Base64 payloads are automatically routed to the multimodal engine when `localEngine.activeModel.supportsVision` is true.

---

## 5. Threading Model & Concurrency

To ensure ultra-smooth 60/120 FPS UI rendering during compute-heavy native inference:
- **`Dispatchers.Main`**: Dedicated solely to Jetpack Compose UI recomposition, state rendering, and user input handling.
- **`Dispatchers.IO`**: Handles disk read/writes (Room SQLite, GGUF file parsing, SharedPreferences) and network socket communication (Ktor server, WebRTC signaling, reverse tunnel).
- **Inference Concurrency Control (`Mutex`)**: Native llama context evaluation is strictly single-threaded per loaded instance. A Kotlin coroutine `Mutex` serializes inference requests, preventing concurrent native memory corruption.
- **Cancellation Cooperative Coroutines**: All token generation streams check `coroutineContext.isActive` on every token. When a user taps "Stop Generation" or navigates away, the native generator immediately aborts prompt evaluation and preserves KV cache integrity.

---

## 6. Network Architecture & P2P DataChannels

### 6.1 AgentX Serve Embedded Ktor Engine
- Runs on port `8080` (configurable) as an Android foreground service.
- Implements Server-Sent Events (SSE) for real-time token streaming (`data: {"choices": [{"delta": {"content": "..."}}]}`).
- Fully compatible with OpenAI API client libraries.

### 6.2 Reverse Tunnel Engine
- Integrated SSH reverse port forwarding client via `TunnelManager.kt`.
- Establishes outbound tunnels to public brokers (Pinggy, Cloudflare Named Tunnels, ngrok) without requiring router port-forwarding or public IP addresses.

### 6.3 WebRTC P2P Direct DataChannel
- **Zero-Relay Communication**: Connects client devices (AgentX) directly to the server device (AgentX Serve) via WebRTC DataChannels over UDP.
- **6-Digit Cloud Rendezvous**: STUN/TURN ICE candidate exchange is coordinated using a short 6-digit session code, enabling instant pairing across cellular and Wi-Fi networks.

---

## 7. Security Architecture

The AgentX security posture adheres to defense-in-depth principles:

### 7.1 Cryptographic Credential Storage (`SecureCredentialStore`)
- Replaces plaintext SharedPreferences with hardware-backed **AES-256-GCM** authenticated encryption.
- Keys are generated and stored in the **Android Keystore**, guarded against extraction even on rooted devices.
- Master keys use 256-bit AES with Galois/Counter Mode (GCM) providing both confidentiality and integrity authentication.
- Automatically handles transparent migration of legacy plaintext keys upon first launch.

### 7.2 Service Isolation & Manifest Hardening
- Background services (`AgentXServerService`) are explicitly marked `android:exported="false"` in `AndroidManifest.xml`, preventing arbitrary intent spoofing or unauthorized invocation by third-party applications on the host device.
- All internal communication uses strongly typed Kotlin interfaces and Room Data Access Objects.

### 7.3 Scoped Storage & Zero-Exfiltration
- Model files are accessed via Android's **Storage Access Framework (SAF)**, granting read-only access to user-selected directories without requiring broad, dangerous storage permissions.
- In AgentX Medical, zero network calls are executed in local mode, ensuring strict clinical privacy.
