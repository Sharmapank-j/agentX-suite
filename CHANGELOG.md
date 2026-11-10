# Changelog — AgentX Suite

All notable changes to the AgentX Suite (`:app`, `:medical`, `:admin`, `:core`) will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
