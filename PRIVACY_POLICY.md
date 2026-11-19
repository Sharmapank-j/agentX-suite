# Privacy Policy — AgentX Suite

**Effective Date:** November 20, 2026  
**Version:** 1.1.0  
**Repository:** [https://github.com/Sharmapank-j/agentX-suite](https://github.com/Sharmapank-j/agentX-suite)  
**Maintained by:** Pankaj ([ps2180809@gmail.com](mailto:ps2180809@gmail.com))

---

## 1. Executive Summary & Core Privacy Philosophy

The **AgentX Suite**—encompassing **AgentX** (`com.agentx`), **AgentX Medical** (`com.agentx.medical`), **AgentX Serve** (`com.agentx.admin`), and the **AgentX Core Library** (`com.agentx:core`)—is architected from the ground up on the principles of **Data Sovereignty, Zero-Telemetry, and Local-First Edge Intelligence**.

We believe that your personal data, clinical consults, research queries, and computational workflows belong strictly to you. Unlike conventional AI assistants and cloud services:
* **We do not collect, monitor, store, or sell your personal data.**
* **We do not track your prompts, chat histories, or model outputs.**
* **We do not embed third-party tracking, analytics, crash-telemetry, or advertising SDKs.**
* **All primary artificial intelligence inference executes 100% locally on your device hardware.**

---

## 2. Information We Do NOT Collect (Zero-Telemetry Commitment)

The AgentX Suite contains **zero telemetry libraries**. Specifically, we never harvest, transmit, or record:

1. **User Communications & Prompts:** Your text queries, conversational context, uploaded prompt images, model completions, and agent reasoning chains remain solely on your device.
2. **Clinical & Health Information:** Any medical scenarios, clinical differential diagnoses, triage decisions, patient vitals, pharmacological lookups, or laboratory calculations evaluated within AgentX Medical are processed locally. No health data is ever collected or accessible to the developers.
3. **Biometric & Photographic Data:** Camera captures or gallery photos selected for local vision processing are loaded into local memory and are never transmitted to our servers.
4. **Persistent Identifiers:** We do not collect or track hardware identifiers (such as IMEI, Android ID, MAC address, or Google Advertising ID).
5. **Geolocation Data:** We do not request or track GPS or fine/coarse network location data.
6. **Third-Party Telemetry SDKs:** We do not use Google Firebase Analytics, Google Crashlytics, Meta/Facebook SDK, Mixpanel, AppsFlyer, Adjust, or any advertising network.

---

## 3. Data Processed Locally on Your Device

All core functionality runs within the sandboxed environment of your physical Android device:

### 3.1 Local Native Inference (`llama.cpp`)
* Language models in `.gguf` format and multimodal vision projectors (`mmproj-*.gguf`) execute directly on your device's CPU/GPU via native C++ ARM NEON and DotProd instructions.
* Model evaluation occurs in volatile device RAM and is never routed through an external server.

### 3.2 Local Database Storage
* Active conversations, message threads, custom agent definitions, and clinical consultation logs are stored in private, local **Room SQLite databases** (`agentx.db`, `agentx_medical.db`, `agentx_admin.db`).
* These databases reside in the private Android app-data sandbox (`/data/data/<package_name>/databases/`) and cannot be accessed by other applications on non-rooted devices.

### 3.3 Hardware-Backed Credential Security (`Android Keystore`)
* If you choose to enter cloud API tokens (e.g., Google Gemini API keys) or edge server bearer credentials, they are encrypted using **AES-256-GCM** via the hardware-backed **Android Keystore System** (`SecureCredentialStore`).
* Encryption keys are generated and isolated inside the secure hardware enclave (TEE/SE) of your device and are never exposed in plaintext or transmitted across networks.

### 3.4 Vector Embeddings & Long-Term Memory
* Episodic memories, user profile notes, and project contexts are embedded and indexed locally on your device. Memory retrieval operations execute entirely offline without third-party vector databases.

---

## 4. Optional Features Involving Network Communication

AgentX is fully functional in an air-gapped, offline environment. Network connectivity is utilized **only** when you explicitly activate specific network-dependent features:

### 4.1 Optional Cloud Vision & Cloud LLM Inference (Google Gemini)
* **User-Initiated Action:** You may optionally supply your own Google Gemini API key to run cloud multimodal vision or language inference.
* **Data Flow:** When this mode is active, your prompt text and selected image are transmitted directly over an encrypted TLS 1.3 HTTPS connection from your device to Google's API endpoints (`generativelanguage.googleapis.com`).
* **Applicable Terms:** This communication occurs directly between your device and Google, governed by [Google's Privacy Policy](https://policies.google.com/privacy) and Google AI Terms of Service. AgentX developers do not proxy, inspect, log, or intermediate this traffic.

### 4.2 AgentX Serve — Local Edge Server & Reverse Tunneling
* **Local Area Network (LAN) Serving:** When AgentX Serve is active, it runs an embedded Ktor HTTP/SSE server on your local Wi-Fi network (default port `8080`). All API requests between your local devices stay within your private local subnet.
* **In-App Reverse Tunneling (Pinggy):** If you explicitly enable public WAN access, the server establishes an outbound encrypted SSH tunnel (port 443 SSL failover to `a.pinggy.io`) to provide a temporary public URL. All incoming requests are guarded by mandatory Bearer Token authentication. No payload data is retained by the tunnel proxy.
* **WebRTC P2P Direct DataChannel:** When pairing devices using the 6-digit cloud rendezvous system, an ephemeral signaling broker exchanges STUN/ICE candidates (`stun:stun.l.google.com:19302`). Once connected, token streaming occurs **directly peer-to-peer between devices** over DTLS-SRTP encrypted UDP channels with zero server relay.

### 4.3 Modular Component-Level OTA Updates
* **Update Verification:** When you manually check for or download intelligence packs, clinical guidelines, or model weights, the app requests the version manifest from your specified update host (e.g., your local AgentX Serve hub or a user-configured URL).
* **Payload Verification:** Downloaded components are verified on-device using cryptographic SHA-256 checksums and path-traversal validation before being atomically applied. No telemetry, user profiling, or personal data is transmitted during the update check.

---

## 5. Android Device Permissions & Usage Justification

In compliance with Google Play Developer Policies and Android security best practices, the AgentX Suite requests only the permissions strictly necessary for its local operations:

| Permission | Package(s) | Technical Purpose | Privacy Protection |
| :--- | :--- | :--- | :--- |
| `READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` | All | Used to discover, load, and manage user-supplied `.gguf` model weights and vision projectors stored in `/sdcard/Download` or custom storage paths. | Scoped strictly to model files (`.gguf`, `.bin`). Never reads, modifies, or transmits personal documents, photos, or media. |
| `CAMERA` | AgentX, Medical | Allows capturing a photo directly within the app for local multimodal vision analysis. | Optional. The camera is active only while taking a photo. Images are processed locally or sent directly to your configured cloud provider; never collected by us. |
| `INTERNET` | All | Enables local LAN edge serving, WebRTC P2P DataChannels, reverse tunneling, optional user-configured Gemini cloud API calls, and OTA manifest downloads. | No background telemetry or undisclosed data transmission occurs. |
| `ACCESS_NETWORK_STATE` | All | Monitors active network state (Wi-Fi vs. Cellular) to prevent heavy model downloads over metered connections and manage WebRTC reconnection. | Used solely for in-memory connection logic; never logged or shared. |
| `FOREGROUND_SERVICE` | Serve (`:admin`) | Keeps the OpenAI-compatible HTTP/SSE server running in the background when the user turns on the serving switch. | Displays a permanent, dismissable system notification whenever the server is active. Automatic 10-minute idle sleep protection prevents battery drain. |
| `WAKE_LOCK` | Serve, Core | Prevents CPU sleep during heavy mathematical tensor calculations or active streaming inference. | Released immediately when inference concludes or the server is paused. |
| `POST_NOTIFICATIONS` | All (Android 13+) | Displays foreground service status for AgentX Serve and completion alerts for background model downloads. | No marketing, promotional, or unrequested notifications are ever dispatched. |
| `VIBRATE` | All | Provides tactile haptic feedback when pressing keys or receiving token streams. | Operates purely as a local hardware actuator. |

---

## 6. Healthcare, Medical Decision Support & HIPAA Considerations

**AgentX Medical is an educational and clinical decision-support development tool. It is not a certified medical device under FDA (21 CFR Part 820) or EU MDR regulations.** (See [DISCLAIMER.md](DISCLAIMER.md) for complete clinical terms).

Regarding health data privacy:
* **No Business Associate or Covered Entity Relationship:** Because the AgentX Suite does not collect, receive, maintain, or transmit Protected Health Information (PHI), neither the author nor any contributor acts as a Business Associate or Covered Entity under the Health Insurance Portability and Accountability Act (HIPAA) or the Health Information Technology for Economic and Clinical Health (HITECH) Act.
* **Practitioner Data Sovereignty:** Healthcare providers, researchers, and students who utilize AgentX Medical retain 100% custody and control of any patient parameters, lab values, or clinical notes entered into the software.
* **Offline HIPAA Compliance:** By maintaining local-first execution with no internet relay, clinical users can safely analyze medical queries on device without violating patient confidentiality, institutional data governance policies, or HIPAA Technical Safeguards.

---

## 7. Data Retention, Portability & Deletion Rights

You have complete, absolute control over all data stored by the AgentX Suite:

1. **In-App Deletion:**
   * **Clear Chats:** Individual conversations or all consultation histories can be permanently deleted at any time via the Drawer or Settings screen.
   * **Clear Memory:** In-app controls allow instant wiping of semantic memories, profile notes, and vector cache.
   * **Remove Models:** Downloaded model weights can be deleted directly from device storage via the Models screen.
2. **System-Level Uninstallation:**
   * Uninstalling the application completely purges all SQLite databases, user preferences, cached artifacts, and Android Keystore master keys from your device.
3. **Data Portability:**
   * You can export conversation logs and benchmark metrics directly to local text/JSON files on your device at your discretion.

---

## 8. Children's Privacy (COPPA & GDPR Article 8)

The AgentX Suite does not knowingly solicit or collect personal information from individuals under the age of 13 (or under 16 in applicable European jurisdictions). Since the software operates locally without accounts or online registration, no user age profiling is performed. If you are under the legal age of consent, please use this software only under the supervision of a parent, guardian, or qualified educator.

---

## 9. Security Architecture & Threat Mitigations

For comprehensive information regarding our cryptographic protections, Android Keystore implementation, path traversal immunity, and edge service hardening, please consult our official [SECURITY.md](SECURITY.md) specification.

Key highlights include:
* **Encrypted API Keys:** AES-256-GCM via the Android Keystore.
* **Internal Service Isolation:** Edge server services are explicitly non-exported (`android:exported="false"`), preventing unauthorized IPC invocation.
* **Canonical Path Validation:** Strict regex filtering and canonical directory verification eliminate Local File Inclusion (LFI) and directory traversal vulnerabilities.

---

## 10. Open Source Transparency & License

The AgentX Suite is published as open-source software under the **Apache 2.0 License** (with upstream `llama.cpp` components under the **MIT License**). All source code—including native C++ JNI bindings, database schemas, network protocols, and cryptographic storage classes—is publicly auditable at [https://github.com/Sharmapank-j/agentX-suite](https://github.com/Sharmapank-j/agentX-suite).

---

## 11. Changes to This Privacy Policy

We may update this Privacy Policy periodically to reflect new features, updated Android platform guidelines, or regulatory requirements. Any modifications will be documented with an updated version number and effective date in this file and committed directly to the public GitHub repository. Your continued use of the software signifies your acceptance of any updated terms.

---

## 12. Contact & Inquiries

If you have questions, feedback, or concerns regarding this Privacy Policy or the privacy practices of the AgentX Suite, please reach out to:

* **Maintainer:** Pankaj Sharma
* **Email:** [ps2180809@gmail.com](mailto:ps2180809@gmail.com)
* **GitHub Issues:** [https://github.com/Sharmapank-j/agentX-suite/issues](https://github.com/Sharmapank-j/agentX-suite/issues)
* **Project Repository:** [https://github.com/Sharmapank-j/agentX-suite](https://github.com/Sharmapank-j/agentX-suite)
