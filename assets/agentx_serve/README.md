# ⚡ AgentX Serve Edge Server App Assets

**Package ID:** `com.agentx.admin`  
**Application Role:** Android Local AI Edge Server & Admin Host  
**Target SDK:** 35 (Android 15) | Min SDK: 26 (Android 8.0)  

## Included Assets
- **`AgentX-Serve-v1.0.0-debug.apk`** (~16.4 MB): Standalone production debug APK for AgentX Serve.
  - [Download `AgentX-Serve-v1.0.0-debug.apk`](https://github.com/Sharmapank-j/agentX-suite/releases/download/v1.0.0/AgentX-Serve-v1.0.0-debug.apk)
  - On-Device Pure Kotlin AirLLM layer-streaming server engine ($\le 200$ MB RAM bounded footprint).
  - Curated open-source edge models catalog (`llama-3.2-3b`, `deepseek-r1-1.5b`, `qwen-2.5-coder-3b`, `biomistral-7b`, `phi-3.5-mini`).
  - Hosts OpenAI-compatible `/v1/chat/completions` and `/v1/models` REST API on port 8080 over local Wi-Fi / LAN.
  - Android 14/15 `connectedDevice` Foreground Service with WakeLock and WifiLock for 24/7 background operation.
  - Real-time admin dashboard with Local Models tab, live model test console, client metrics, token counters, and scrolling request logs.
- **`ic_launcher_foreground.xml`**: Cyber server stack with active LEDs and broadcasting radio waves vector drawable.
- **`ic_launcher_background.xml`**: Night slate `#090D16` ambient background vector drawable.
