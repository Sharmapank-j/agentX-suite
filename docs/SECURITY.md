# AgentX Suite — Security Architecture & Hardening Guide (v1.0.0)

**Classification:** Official Security Architecture Specification  
**Version:** 1.0.0 (Production)  
**Modules Audited:** `:core`, `:app`, `:admin`, `:medical`  

---

## 1. Security Architecture Principles

AgentX Suite is engineered around five fundamental security principles:
1. **Zero-Trust Edge Serving**: No endpoint is open or unauthenticated by default. Every API request requires a valid API bearer key or authorized pairing session token persisted in an encrypted Room SQLite database.
2. **End-to-End Cryptographic Isolation**: Cross-network inference communications traverse either an end-to-end encrypted WebRTC P2P DataChannel (DTLS-SRTP) or an authenticated reverse tunnel socket (SSH/TLS 1.3).
3. **Hardware-Backed Credential Security**: Cloud API tokens (Gemini API keys, custom gateway secrets) are encrypted on-device via AES-256-GCM using keys stored inside the hardware **Android Keystore**, isolated from application space.
4. **Strict Path Traversal Immunity**: Model staging, scanning, and file removal operations canonicalize target file paths and strictly reject paths attempting to escape allowed application directories or traverse relative directories (`../`).
5. **Data Sovereign Medical Isolation**: AgentX Medical processes calculations and inferences offline by default. Zero clinical parameters, patient identifiers, or consult histories are transmitted to third-party telemetry services.

---

## 2. Threat Model & Mitigation Matrix

| Threat Category | Potential Attack Vector | Applied Mitigation | Verification Method |
| :--- | :--- | :--- | :--- |
| **Credential Extraction** | Inspection of `SharedPreferences` XML files on rooted/backup devices. | AES-256-GCM encryption with Android Keystore master key via `SecureCredentialStore`. Encrypted strings prefixed with `enc:v1:`. | `SecureCredentialStoreTest.kt` unit test verifies ciphertext generation and key isolation. |
| **Service Hijacking** | Malicious third-party apps triggering `AgentXServerService` foreground server. | Set `android:exported="false"` in `admin/src/main/AndroidManifest.xml`. Service can only be launched via explicit intents from within the AgentX Serve application. | Static manifest verification and intent resolution checks. |
| **Path Traversal (LFI)** | Malicious model filename (e.g. `../../system/bin/sh`) supplied during model import or file deletion. | `ModelFileManager` sanitizes names via regex `[^a-zA-Z0-9._-]` and enforces `file.canonicalPath.startsWith(modelsDir.canonicalPath)`. | Staging unit tests and path validation assertions. |
| **Man-in-the-Middle (MitM)** | Interception of LAN model streaming over unencrypted Wi-Fi. | Embedded `LocalTlsManager` self-signed TLSv1.3 gateway + WebRTC direct P2P DataChannel with mandatory DTLS negotiation. | Handshake logs and SSLContext validation. |
| **Denial-of-Service / Exhaustion** | Flooding edge server with concurrent inference requests leading to OOM. | Mutex-guarded single-flight inference in `LocalModelServerEngine` + thermal backpressure monitor throttling requests when battery temperature exceeds 42°C. | Concurrent load simulation tests. |
| **Medical Liability / Misdiagnosis** | AI hallucinating non-existent clinical dosages or replacing clinical judgement. | Deterministic validated mathematical calculators for all clinical scores + mandatory non-diagnostic disclaimers displayed in UI and returned on `/api/v1/clinical/consult`. | `ClinicalCalculatorsTest.kt` unit tests across boundary conditions. |

---

## 3. Cryptographic Implementation Details

### 3.1 Android Keystore AES-256-GCM (`SecureCredentialStore`)
```kotlin
// Hardware-backed Master Key Generation
val spec = KeyGenParameterSpec.Builder(
    KEY_ALIAS,
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    .build()
```
- **IV Generation**: SecureRandom 12-byte initialization vector generated per encryption operation.
- **Tag Length**: 128-bit authentication tag ensuring message integrity and tamper-proofing.
- **Backward Compatibility**: Automated transparent migration from legacy plaintext keys with zero user data loss.

### 3.2 In-App Reverse Tunnel Security
- Reverse tunnels (Pinggy / SSH) establish an outbound TCP/SSH session using ECDSA / Ed25519 host key authentication.
- No inbound ports are opened on carrier NATs or cellular firewalls, completely neutralizing inbound port scanning attacks.

### 3.3 WebRTC P2P DataChannel
- Direct UDP hole-punching via standard Google STUN infrastructure (`stun:stun.l.google.com:19302`).
- 6-digit cloud rendezvous exchanges ephemeral SDP offers and ICE candidates without exposing persistent device credentials or local IP topologies.

---

## 4. Privacy & Diagnostic Policies

1. **Zero Cloud Telemetry**: AgentX does not bundle Firebase Analytics, Google Crashlytics, Mixpanel, or third-party trackers.
2. **Log Scrubbing**: Logcat output never logs API keys, bearer tokens, pairing secrets, or private medical conversation transcripts.
3. **Local Storage Sanitization**: Model uninstallation completely zeros and deletes cached GGUF model files and projector weights from storage.
