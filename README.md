# AgentX Suite v1.1.0 — Production AI Ecosystem for Android

[![Android CI](https://github.com/Sharmapank-j/agentX-suite/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Sharmapank-j/agentX-suite/actions/workflows/android-ci.yml)
[![Version](https://img.shields.io/badge/version-v1.1.0-00E5FF.svg)](CHANGELOG.md)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Architecture](https://img.shields.io/badge/architecture-Multi--Module%20Kotlin-8E24AA.svg)](docs/ARCHITECTURE.md)
[![Security](https://img.shields.io/badge/security-Keystore%20AES--256--GCM-00C853.svg)](docs/SECURITY.md)

**AgentX Suite** is a production-grade, offline-first artificial intelligence ecosystem engineered for Android devices. Powered by upstream `llama.cpp` native compilation (`libagentx_llama.so`), the suite executes real quantized GGUF large language and multimodal vision models locally on mobile hardware with zero cloud dependency, zero data exfiltration, and microsecond latency.

---

## The Three Applications

| Application | Role & Package ID | Key Capabilities |
| :--- | :--- | :--- |
| **AgentX** | Flagship AI Assistant<br>`com.agentx` | • Dynamic local GGUF model hub & safe hot-swapping<br>• Multimodal vision engine (text + image reasoning)<br>• Multi-agent workflows & extensible tool calling<br>• Hardware profiler (TTFT, token/s, RAM PSS, thermals)<br>• Dynamic M3 Cyber Obsidian / Cyber Light UI |
| **AgentX Medical** | Clinical AI Suite<br>`com.agentx.medical.debug` | • HIPAA-compliant offline-first clinical intelligence & education<br>• 21 medical specialties & 20+ query intents with dynamic prompt matrix (5 styles × 5 depths)<br>• Modular Component OTA updates for hot-patching clinical guidelines & formulas<br>• Deterministic calculators (CHA2DS2-VASc, Wells, eGFR, MAP, qSOFA)<br>• M3 Clinical Obsidian / Clinical Light UI |
| **AgentX Serve** | Universal Edge AI Server<br>`com.agentx.admin.debug` | • Embedded Ktor OpenAI-compatible REST server (`:8080`)<br>• Universal Cross-Network Serving (any device, 4G/5G/Wi-Fi across CGNAT)<br>• In-App SSH reverse-tunnel broker with ephemeral RSA key generation & window draining<br>• OTA Component Distribution Hub (`/ota/manifest.json`, `/ota/download/{id}`)<br>• Embedded responsive Web Chat Client & M3 Cyber HUD UI |

---

## Key Platform Innovations in v1.1.0

### 1. Universal Cross-Network Serving (AgentX Serve)
- **Universal Multi-Network Access**: Serve AI models to any device anywhere (cellular 4G/5G, foreign Wi-Fi, or corporate networks) bypassing carrier-grade NATs (CGNAT) without router port-forwarding.
- **Embedded Reverse SSH Tunneling**: In-app JSch tunnel engine auto-negotiating port 443 SSL failover (`a.pinggy.io:443`) with ephemeral in-memory 2048-bit RSA keypairs.
- **Background IO Window Draining**: Dedicated coroutine stream draining preventing SSH channel window exhaustion and eliminating connection freeze.
- **Zero-Trust Token Security**: IP-aware bearer authorization. Public WAN access using static developer keys is strictly rejected while allowing private LAN/loopback testing.

### 2. Medical Intelligence Engine (AgentX Medical Phase 1)
- **21 Clinical Disciplines**: Specialized prompt directives and keyword mappings covering Cardiology, Pulmonology, Endocrinology, Nephrology, Surgery, Pediatrics, and more.
- **20+ Clinical Query Intents**: Structured clinical management, differential diagnosis, pharmacotherapy, resuscitation algorithms, and diagnostic evaluations.
- **25 Matrix Generation Matrix**: Harmonization of 5 User Response Styles (Medical Student, Clinical Professional, Advanced, Research, Viva) with 5 Conversation Answer Depths (Brief, Standard, Detailed, Advanced, Expert).
- **Word-Boundary Safe Classifier**: Regex-bounded entity extraction and intent prioritization preventing keyword collisions.
- **Emergency ABCDE Triage Overrides**: Critical emergency alerts prepending resuscitation protocols for time-critical conditions.

### 3. Modular Component-Level OTA Updates
- **Hot-Patching Without APK Reinstalls**: Update medical intelligence packs, clinical guidelines, model weights, prompt templates, and web assets independently.
- **Cryptographic Verification**: Streaming SHA-256 integrity validation and directory traversal prevention.
- **Atomic Local Registry & Rollback**: Sandboxed version directories with 1-click rollback to compiled built-ins.
- **Live In-Memory Hot-Reloading**: `DynamicMedicalKnowledgeManager` hot-reloads clinical guidelines and entity dictionaries in-memory immediately upon download.
- **OTA Distribution Hub**: AgentX Serve exposes `/ota/manifest.json` and `/ota/download/{id}` for ecosystem client devices.

---

## Brand Identity & Design System

AgentX Suite v1.0.0 introduces a cohesive, recognizable brand identity system across all three applications:

- **Neural-X Mark**: A precision geometric symbol combining neural network topology with an architectural X motif.
- **Adaptive Vector Launcher Icons**: Fully compliant with Android 108dp icon viewports and 72dp safe circles across all densities (MDPI to XXXHDPI).
- **Three Distinguishable Brand Colorways**:
  - **AgentX Flagship**: Cyber Cyan (`#00E5FF`) on Deep Obsidian (`#0D1117`).
  - **AgentX Medical**: Clinical Teal (`#00B4D8`) & Caduceus Green (`#00E676`) on Hospital Slate (`#0B131E`).
  - **AgentX Serve**: Terminal Amber (`#FF9100`) & Matrix Neon (`#00E676`) on Carbon Black (`#0C0E12`).
- **Complete Guidelines**: Detailed in [docs/BRAND_GUIDELINES.md](docs/BRAND_GUIDELINES.md) and [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md).

---

## Physical Device Verification Baseline

Real hardware testing performed on physical Android hardware with 100% offline airplane mode:

- **Device:** Xiaomi POCO `2201116PI` (Poco X4 Pro 5G)
- **OS / SoC:** Android 13 (API 33), Qualcomm Snapdragon 695 5G (8-core Kryo 660: 2 Fast Cores @ 2.2GHz + 6 Efficiency Cores @ 1.8GHz, `arm64-v8a`)
- **Memory / Storage:** 8 GB RAM, 128 GB Storage
- **Tested Model:** `medimind-v2-q5_k_m.gguf` (1,125,050,016 bytes, Q5_K_M quantization)
- **Verified Benchmark Metrics:**
  - **AgentX Turn 1:** Prompt `"Explain what causes hypertension in 2 sentences"` → **241 tokens generated at 8.6 tokens/sec**
  - **AgentX Turn 2:** Prompt `"What are 3 ways to lower it?"` → 335 history tokens evaluated in 5.2s, **242 tokens generated at 8.5 tokens/sec**
  - **AgentX Medical:** Prompt `"What are the primary clinical indications for ACE inhibitors?"` → **211 tokens generated at 8.5 tokens/sec**
  - **AgentX Serve:** OpenAI REST server active at `http://10.212.62.157:8080`, returning local GGUF models via `/v1/models` and streaming tokens via `/v1/chat/completions`
  - **Sustained Throughput:** ~8.5–8.6 tokens/sec
  - **Memory Footprint:** ~1.2 GB PSS native virtual memory mapping
  - **Thermals:** Battery temp ~32.3°C, Thermal Status 0 (Optimal)

---

## Engineering & Security Highlights

1. **Hardware-Backed Cryptographic Storage (`SecureCredentialStore`)**:
   - Upgraded SharedPreferences storage to hardware-backed **AES-256-GCM** authenticated encryption using the Android Keystore.
   - Prevents credential exposure in backups and device memory. Transparently migrates legacy plaintext keys.
2. **Multimodal Local Vision Engine**:
   - Native integration with `llama-clip` projector weights.
   - Process image and text inputs jointly in both the client chat UI and through Ktor OpenAI server endpoints.
3. **WebRTC P2P Direct DataChannel**:
   - Zero-relay, lowest-latency UDP direct streaming between client and server devices across different networks using STUN/TURN candidate exchange.
4. **Active Reverse Tunnel Engine**:
   - Pure-Kotlin SSH reverse port-forwarding to Pinggy/Cloudflare, enabling remote server access without public IP or router configuration.
5. **Hardened Manifest Boundaries**:
   - Foreground server services explicitly secured with `android:exported="false"`.

---

## Multi-Module Architecture

```text
AgentX Suite
├── :core      Shared domain, LlamaNativeBridge (JNI), SecureCredentialStore, GGUF Parser
├── :app       AgentX assistant (dynamic model hub, multimodal chat, agents)
├── :medical   AgentX Medical (clinical multi-agent runtime, validated calculators)
└── :admin     AgentX Serve (OpenAI REST server, reverse tunnels, WebRTC broker)
```

For complete technical specifications, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Documentation Index

- [Brand Guidelines](docs/BRAND_GUIDELINES.md) — Logo marks, color palette, typography, icon geometry, and asset specifications.
- [Design System](docs/DESIGN_SYSTEM.md) — Reusable components, theme tokens, typography, and responsive layouts.
- [Security Architecture](SECURITY.md) — Threat models, Keystore encryption, manifest boundaries, and clinical safeguards.
- [Privacy Policy](PRIVACY_POLICY.md) — Local-first privacy architecture, zero-telemetry commitment, and permission justifications.
- [Regulatory & Clinical Disclaimer](DISCLAIMER.md) — Non-diagnostic status, clinical validation protocols, and liability limits.
- [Local GGUF Guide](docs/LOCAL_GGUF_GUIDE.md) — Local model deployment, quantization formats, and memory tuning.
- [Networking & P2P Guide](docs/NETWORKING.md) — Embedded Ktor server, reverse tunneling, and WebRTC streaming.
- [Changelog](CHANGELOG.md) — Comprehensive v1.1.0 release log.

---

## Privacy & Data Sovereignty

AgentX Suite is engineered on the principle that your intelligence belongs solely to you:
- **Zero Telemetry:** No user analytics, no crash trackers, no advertising SDKs, and no background reporting.
- **Local-First Execution:** Neural inference executes directly on physical device silicon via upstream `llama.cpp`.
- **Encrypted Local Storage:** All keys and tokens are protected via AES-256-GCM backed by the **Android Keystore System**.
- **Clinical Confidentiality:** Patient queries and medical calculations never leave your hardware.

For detailed privacy guarantees, data retention rights, and permission justifications, please review our official [Privacy Policy](PRIVACY_POLICY.md).

---

## Building and Testing

### Prerequisites
- Android Studio Ladybug or later
- JDK 21
- Android SDK Platform 34 (Build Tools 34.0.0)
- CMake 3.22.1+ and NDK 26.1+

### Build Commands

```bash
# Execute unit tests across all 4 modules
./gradlew testDebugUnitTest

# Assemble debug APKs for all three applications
./gradlew :app:assembleDebug :medical:assembleDebug :admin:assembleDebug
```

Built APK outputs:
- `app/build/outputs/apk/debug/app-debug.apk` (`AgentX-v1.1.0-debug.apk`)
- `medical/build/outputs/apk/debug/medical-debug.apk` (`AgentX-Medical-v1.1.0-debug.apk`)
- `admin/build/outputs/apk/debug/admin-debug.apk` (`AgentX-Serve-v1.1.0-debug.apk`)

---

## Medical Disclaimer

**AgentX Medical is an educational and clinical decision-support development tool. It is not a certified medical device and does not provide formal medical diagnoses or replace licensed clinical judgement.** All clinical calculators are deterministic computational aids; clinicians must verify results against authoritative institutional protocols and clinical references.

---

## License

This project is licensed under the Apache 2.0 License. Upstream `llama.cpp` components are licensed under the MIT License. See [LICENSE](LICENSE) for complete details.
