# Changelog — AgentX Suite

All notable changes to the AgentX Suite (`:app`, `:medical`, `:admin`, `:core`) will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1.0] — 2026-11-18

### 🚀 Universal Serving, Medical Intelligence, and Modular Component OTA

#### 1. Universal Cross-Network Serving (AgentX Serve)
- **Universal Multi-Network Serving**: AgentX Serve now serves any device anywhere across 4G/5G/Wi-Fi without local subnet restrictions or router port-forwarding.
- **In-App SSH Reverse Tunneling**: Embedded pure-Java/Kotlin SSH reverse proxy (`TunnelManager`) connecting via port 443 SSL failover (`a.pinggy.io:443`), generating dynamic 2048-bit RSA keypairs in-memory.
- **Background IO Window Draining**: Dedicated coroutine stream draining preventing JSch SSH channel window exhaustion and eliminating server stalls.
- **5-Second NAT Keep-Alives**: Proactive keep-alive loop (`sendKeepAliveMsg()`) to prevent cellular carrier NAT timeouts.
- **Zero-Trust Token Validation & Security**: Injected IP-aware bearer authorization. Public WAN access using static developer keys is strictly rejected while allowing private LAN/loopback testing.
- **CORS Preflight Caching**: Cached OPTIONS preflight handshakes (`maxAge = 3600s`) preventing gateway flooding.
- **Embedded Web Chat Client**: Full responsive HTML/CSS/JS client served at `/` and `/chat`.

#### 2. Medical Intelligence Engine (AgentX Medical Phase 1)
- **21 Clinical Disciplines**: Specialized prompt directives and keyword mappings covering Cardiology, Pulmonology, Endocrinology, Nephrology, Surgery, Pediatrics, and more.
- **20+ Clinical Query Intents**: Structured clinical management, differential diagnosis, pharmacotherapy, resuscitation algorithms, and diagnostic evaluations.
- **25 Matrix Generation Matrix**: Seamless harmonization of 5 User Response Styles (Medical Student, Clinical Professional, Advanced, Research, Viva) with 5 Conversation Answer Depths (Brief, Standard, Detailed, Advanced, Expert).
- **Word-Boundary Safe Classifier**: Regex-bounded entity extraction and intent prioritization preventing keyword collisions (e.g. `overdose` correctly routing to `TOXICOLOGY` instead of `PHARMACOTHERAPY`).
- **Emergency ABCDE Triage Overrides**: Critical emergency alerts prepending resuscitation protocols for time-critical conditions.

#### 3. Modular Component-Level OTA Update Subsystem
- **Component-Level Updates**: Granular OTA updates for intelligence packs, clinical guidelines, model weights, prompt templates, and web assets without full APK rebuilds.
- **Cryptographic Verification**: Streaming SHA-256 integrity validation and directory traversal prevention in `OtaVerifier`.
- **Atomic Local Registry**: `OtaComponentRegistry` with sandboxed versioned directories (`baseDir/components/{id}/{versionCode}/`) and 1-click rollback.
- **Live In-Memory Hot-Reloading**: `DynamicMedicalKnowledgeManager` hot-reloading clinical guidelines and entity dictionaries in-memory immediately upon download.
- **Serve Hub Distribution**: AgentX Serve endpoints (`GET /ota/manifest.json`, `GET /ota/download/{id}`, `POST /ota/publish`).
- **Settings UI Dashboard**: Live component update card in AgentX Medical with auto-update toggle, Wi-Fi only restriction for large weights, and download progress tracking.

#### 4. Hardware Verification & Reliability
- **Tested Physical Hardware**: Verified end-to-end on Xiaomi Redmi Note 11 Pro+ 5G (`5eb1d795a3b4`, Android 13, `arm64-v8a`).
- **Battery Safeguards**: Reduced inference wake-lock timeout from 60 minutes to 10 minutes to protect battery health against edge-case process kills.
- **Full Unit Test Suite**: Clean pass across all 4 modules (`:core`, `:admin`, `:medical`, `:app`).

---

## [1.0.0] — 2026-11-10

### 🚀 Major Product, Branding, Engineering & Security Milestone

#### 1. Core Native Inference & Multimodal Local Vision Engine
- **Upstream `llama.cpp` Integration**: Built with `mtmd` (multimodal) enabled in native CMake build.
- **Multimodal Projector Support**: Full JNI bridge (`nativeLoadVisionProjector`, `nativeSupportsVision`, `nativeGenerateMultimodal`) supporting CLIP and vision projectors (`mmproj-*.gguf`).
- **Offline Image Comprehension**: Offline vision models (Moondream2, MiniCPM-V, LLaVA, Llama 3.2 Vision) execute directly on device without cloud relay.
- **Dynamic Projector Pairing**: Automatic detection and binding of multimodal projectors located alongside `.gguf` weights in `/sdcard/Download/` or internal storage.

#### 2. Local Multi-Model Dynamic Hub
- **Storage Discovery**: Automatic scanning and categorization across internal memory, application storage, and external download directories.
- **Safe Hot-Swapping**: Mutex-guarded atomic model switching ensuring complete native context deallocation (`llama_free`, `llama_free_model`) before new model allocation, eliminating OOM crashes.
- **RAM Safety Management**: Live visual RAM bar with safety tiers (<60% Safe, 60–80% Moderate, >80% Warning) and 1-tap model unload capability.

#### 3. Cross-Network Connectivity & In-App Reverse Tunneling
- **Pure-Kotlin In-App Reverse Proxy**: Integrated reverse tunnel engine in `TunnelManager` (Pinggy via SSH reverse port forwarding, Cloudflare, ngrok) generating instant public HTTPS endpoints.
- **6-Digit Cloud Rendezvous**: Ephemeral signaling broker exchanging pairing payloads and cryptographic session profiles without port forwarding or static IPs.
- **Production WebRTC P2P Direct DataChannel**: Automated STUN candidate exchange (`stun.l.google.com:19302`) for zero-relay, direct UDP token streaming between devices with graceful HTTPS/SSE fallback.
- **Dynamic Reconnection & Heartbeats**: Resilient keep-alive ping loop seamlessly handling cellular-to-Wi-Fi network transitions.

#### 4. Hardware Profiler & On-Device Benchmark
- **Live Chat Telemetry**: Real-time token metrics footer displaying `⚡ [Tokens] tok • [Speed] t/s • [TTFT]ms` on streamed assistant messages.
- **Hardware Benchmarking**: 1-tap benchmark suite measuring prompt evaluation speed, token generation throughput, and RAM deltas on mobile SoCs.
- **Thermal Backpressure Regulation**: Dynamic thermal state tracking (`OPTIMAL`, `MODERATE`, `THROTTLED`, `SEVERE`) with automated request throttling to protect device hardware.

#### 5. Brand Identity & Unified Design System
- **Unified Neural-X Mark**: Distinctive geometric AI silhouette sharing identical grid proportions across all three applications with domain-specific accents:
  - **AgentX (`:app`)**: Deep Neural Indigo (`#6366F1`) & Electric Cyan (`#06B6D4`).
  - **AgentX Medical (`:medical`)**: Clinical Teal (`#0D9488` / `#14B8A6`), Vital Mint (`#10B981`), and clinical cross datum bar.
  - **AgentX Serve (`:admin`)**: Infrastructure Emerald (`#059669`), Sapphire (`#2563EB`), broadcast telemetry arcs, and server chassis anchor.
- **Adaptive Vector Drawables**: High-resolution vector drawables (`ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `ic_launcher.xml`, `ic_launcher_round.xml`) conforming strictly to the 108dp Android adaptive icon grid.
- **Full Dark & Light Theme Support**: Semantic M3 color palettes with dynamic runtime theme switching in `MainActivity` based on user settings.
- **Calm & Roomy Interface**: Generous spacing, accessible 48dp touch targets, and model-aware UI controls hiding camera attachments when loaded models do not support vision.

#### 6. Enterprise Security Hardening
- **Hardware-Backed Credential Store**: `SecureCredentialStore` providing AES-256-GCM encryption for API keys and tokens backed by the **Android Keystore**, with transparent backward compatibility for legacy plaintext keys.
- **Edge Service Export Lockdown**: Explicitly declared `android:exported="false"` on `AgentXServerService` in `:admin`, eliminating unauthorized third-party IPC access.
- **Strict Path Traversal Immunity**: Canonicalized path checks and strict regex filtering preventing relative directory traversal on model import and deletion.
- **Zero Cloud Telemetry**: Complete sovereign offline privacy with zero external tracking libraries.

#### 7. Clinical Reliability & Medical Decision Support
- **14 Deterministic Clinical Calculators**: Cockcroft-Gault CrCl, eGFR (CKD-EPI), Anion Gap, Corrected Calcium, CHA2DS2-VASc, HAS-BLED, Corrected Sodium, Serum Osmolality, QTc (Bazett & Fridericia), Wells DVT, Wells PE, CURB-65, APGAR, and BMI/BSA (Mosteller).
- **Clinical Validation**: Mathematical validation across edge cases with clean Unicode medical formatting (`≥`, `–`, `⚠️`) and mandatory clinical disclaimers.

#### 8. Quality Engineering & CI/CD
- **Gated Release Automation**: `.github/workflows/android-ci.yml` strictly enforces release publication only on approved version tags (`v*`), preventing accidental release creation on arbitrary pushes to `main`.
- **Automated Checksums**: Continuous generation and verification of SHA-256 checksums (`SHA256SUMS.txt`) for all published APKs.
- **100% Automated Test Passing**: Full test suite across `:core`, `:app`, `:admin`, and `:medical` unit tests passing with zero failures.

---

## [0.9.0] — 2026-11-09
- Initial implementation of Native Cross-Network Support (WebRTC signaling, in-app reverse tunneling UI, and 6-digit cloud rendezvous).
- Roomy, calm chat UI layout with responsive IME software keyboard padding.
- Device testing and live execution verification on POCO X4 Pro 5G.

---

## [0.8.0] — 2026-11-08
- Tactile haptic feedback integration across chat interactions and button toggles.
- Context attention management and long-term conversation memory subsystems.
- MCP (Model Context Protocol) tool runtime and HTTP client integration.
