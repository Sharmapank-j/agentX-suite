# 🚀 AgentX Suite

<p align="center">
  <img src="https://img.shields.io/badge/AgentX-Suite%20v1.3.0-blue?style=for-the-badge&logo=android" alt="AgentX Suite"/>
  <img src="https://img.shields.io/badge/AirLLM-Zero--Crash%20Engine-teal?style=for-the-badge&logo=fastapi" alt="AirLLM"/>
  <img src="https://img.shields.io/badge/Tests-All%20Passing-brightgreen?style=for-the-badge&logo=checkmarx" alt="Tests"/>
  <img src="https://img.shields.io/badge/Kotlin-2.0-purple?style=for-the-badge&logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Android%20SDK-35%20(Android%2015)-3DDC84?style=for-the-badge&logo=android" alt="Android"/>
  <img src="https://img.shields.io/badge/Architecture-Pure%20Kotlin%20Zero--Crash-00599C?style=for-the-badge&logo=android" alt="Architecture"/>
  <img src="https://img.shields.io/badge/License-Apache%202.0-orange?style=for-the-badge" alt="License"/>
</p>

---

## 🌟 Overview

The **AgentX Suite** is an advanced on-device AI ecosystem for Android that bridges local offline edge computing with cutting-edge multi-agent orchestration. The suite consists of **3 distinct, fully decoupled applications**:

```
                       ┌───────────────────────────────────────────────┐
                       │                 AGENTX SUITE                  │
                       └───────────────────────┬───────────────────────┘
                                               │
         ┌─────────────────────────────────────┼─────────────────────────────────────┐
         ▼                                     ▼                                     ▼
 ┌───────────────┐                     ┌───────────────┐                     ┌───────────────┐
 │    AgentX     │                     │ AgentX Medical│                     │ AgentX Serve  │
 │ (General AI)  │                     │  (Clinical)   │                     │  (Edge Host)  │
 ├───────────────┤                     ├───────────────┤                     ├───────────────┤
 │ • ChatGPT     │                     │ • Medical CDS │                     │ • Background  │
 │   Persona     │                     │ • Verified    │                     │   HTTP Server │
 │ • Multi-Agent │                     │   Tools (eGFR,│                     │ • AirLLM Pure │
 │   Orchestrator│                     │   CHA2DS2)    │                     │   Local Engine│
 │ • AirLLM Pure │                     │ • Safety Audit│                     │ • OpenAI REST │
 │   Streaming   │                     │ • Evidence    │                     │   API Gateway │
 │ • Specs HUD   │                     │   Synthesis   │                     │ • WakeLock &  │
 │ • 100% Private│                     │ • Zero Crash  │                     │   WifiLock    │
 └───────────────┘                     └───────────────┘                     └───────────────┘
```

1. **🌟 AgentX (`:app`)**: Dedicated personal AI assistant application (`com.agentx`) powered by the **Pure Kotlin Zero-Crash Engine & AirLLM progressive layer-streaming** and Cloud Gemini streaming, backed by a collaborative general specialist collective. Features custom adaptive neural vector icon.
   - [Download `AgentX-v1.0.0-debug.apk`](https://github.com/Sharmapank-j/agentX-suite/releases/download/v1.0.0/AgentX-v1.0.0-debug.apk)
2. **⚕️ AgentX Medical (`:medical`)**: Dedicated standalone Clinical Decision-Support (CDS) application (`com.agentx.medical`) designed for healthcare professionals and medical students. Features verified on-device calculators (eGFR CKD-EPI 2021, $\text{CHA}_2\text{DS}_2\text{-VASc}$), clinical teams, and custom adaptive medical pulse/caduceus icon.
   - [Download `AgentX-Medical-v1.0.0-debug.apk`](https://github.com/Sharmapank-j/agentX-suite/releases/download/v1.0.0/AgentX-Medical-v1.0.0-debug.apk)
3. **⚡ AgentX Serve (`:admin`)**: Dedicated Android edge-server application (`com.agentx.admin`) that transforms any smartphone into an on-device local model inference server and LAN gateway, serving curated open-source LLMs over local Wi-Fi via standard OpenAI-compatible endpoints.
   - [Download `AgentX-Serve-v1.0.0-debug.apk`](https://github.com/Sharmapank-j/agentX-suite/releases/download/v1.0.0/AgentX-Serve-v1.0.0-debug.apk)

---

## 📁 Segregated Asset Directories

Assets across all three projects are kept strictly separated in dedicated directories:
* **`assets/agentx/`**: AgentX Personal Assistant icons (`ic_launcher_foreground.xml`, `ic_launcher_background.xml`), metadata, and production APKs.
* **`assets/agentx_medical/`**: AgentX Medical CDS icons, caduceus branding, clinical notices, and production APKs.
* **`assets/agentx_serve/`**: AgentX Serve server stack icons, background service configs, and production APKs.

---

## 🏛️ Architecture & System Design

```mermaid
flowchart TD
    User([User Prompt / Query]) --> InputCheck{Domain Classification}
    
    subgraph MultiAgentEngine ["🤖 Multi-Agent Orchestration Layer"]
        InputCheck -->|Coding / General / Medical| Planner["📋 Task Planner (Heuristic / LLM)"]
        Planner --> Plan["ExecutionPlan (DAG with Dependencies)"]
        Plan --> Supervisor["👑 Chief Supervisor & ExecutionQueue"]
        
        Supervisor --> SpecialistCoder["💻 Coder & Systems Architect"]
        Supervisor --> SpecialistAnalyst["📊 Data & Diagnostic Analyst"]
        Supervisor --> SpecialistResearcher["🔬 Deep Literature Researcher"]
        
        SpecialistCoder --> Tools["🧮 Tool Registry & Executor\n• eGFR (CKD-EPI 2021)\n• CHA2DS2-VASc\n• Drug Safety Checker\n• Lab Range Interpreter"]
        Tools --> ToolResult["<tool_result> Context Injection"]
        ToolResult --> SpecialistCoder
        
        SpecialistCoder --> Reviewer["🛡️ Quality & Safety Reviewer"]
        SpecialistAnalyst --> Reviewer
        SpecialistResearcher --> Reviewer
        
        Reviewer --> Aggregator["📝 Clinical & General Result Aggregator"]
    end

    subgraph InferenceSubsystem ["⚡ Dual Hybrid Inference Runtime"]
        SpecialistCoder & SpecialistAnalyst & SpecialistResearcher & Aggregator --> HybridEngine["HybridInferenceEngine"]
        HybridEngine -->|Offline / Local| AirLlmEngine["AirLlmEngine (Pure Kotlin Layer-Streaming)"]
        HybridEngine -->|Cloud / Fallback| CloudEngine["CloudInferenceEngine (Gemini SSE)"]
    end

    Aggregator --> CompiledResponse([✨ Compiled Multi-Agent Response])
```

---

## ✨ Key Features & Highlights

### 1. Zero-Crash Pure Kotlin AirLLM Engine
- **Memory-Bounded Layer Streaming**: Executes large language models through bounded layer-streaming ($\le 200$ MB RAM footprint), running multi-billion parameter models locally on mobile devices without crashing or OOM.
- **Zero JNI Fragility**: Fragile CMake C++ `llama.cpp` JNI binaries were replaced with a pure Kotlin layer-streaming architecture, completely eliminating `SIGSEGV`, `UnsatisfiedLinkError`, and ABI incompatibility crashes.
- **Cloud Gemini Streaming**: Real-time Server-Sent Events (SSE) streaming with transparent automatic fallback when on-device memory is constrained.

### 2. AgentX Serve: On-Device Model Server & Edge Gateway
- **Curated Open-Source Edge Models**:
  - `llama-3.2-3b`: Meta Llama 3.2 3B Instruct (General conversation & reasoning, 196 MB RAM)
  - `deepseek-r1-1.5b`: DeepSeek R1 Distill 1.5B (Distilled mathematical & logic deduction, 140 MB RAM)
  - `qwen-2.5-coder-3b`: Qwen 2.5 Coder 3B (Software architecture & coding synthesis, 196 MB RAM)
  - `biomistral-7b`: BioMistral 7B (Biomedical & clinical guidelines, 240 MB RAM)
  - `phi-3.5-mini`: Microsoft Phi 3.5 Mini Instruct (Compact reasoning & math, 210 MB RAM)
- **Serving Modes**:
  - `LOCAL_MODEL`: 100% offline, zero cloud cost, complete data privacy.
  - `CLOUD_GATEWAY`: Relays requests to Google Gemini for large-scale multimodal queries.
  - `HYBRID`: Prioritizes local on-device engine with automatic cloud failover.
- **Interactive Local Models Tab**: Direct model switching, RAM footprint meter, and a built-in Live Test Console to verify on-device inference before serving LAN clients.
- **OpenAI-Compatible REST APIs**:
  - `POST /v1/chat/completions`: Supports standard JSON payloads and streaming Server-Sent Events chunks.
  - `GET /v1/models`: Standard model discovery endpoint listing all available local edge models.
  - `GET /health`: Live system uptime, active local model, memory usage, and connected client count.
  - `POST /api/v1/clinical/consult`: Specialized endpoint for direct clinical reasoning requests.
  - `POST /api/v1/clinical/calc`: Deterministic score calculator endpoint.
- **Background Persistence**: Runs on Android's `FOREGROUND_SERVICE_CONNECTED_DEVICE` with `PowerManager.PARTIAL_WAKE_LOCK` and `WifiManager.WIFI_MODE_FULL_HIGH_PERF`.

### 3. Multi-Agent Planning, Roles & Aggregator
- **Dynamic Task Decomposition**: `HeuristicTaskPlanner` automatically parses query intent (coding algorithms, medical pathophysiology, pharmacological dosing, quantitative data analysis, or general conversation).
- **Specialist Roster**:
  - `SUPERVISOR`: Coordinates subtasks, monitors queue dependencies, and oversees execution.
  - `PLANNER`: Breaks down ambiguous queries into 3–6 actionable chronological milestones.
  - `CODER`: Writes software architectures, optimizes scripts, and executes mathematical tools.
  - `ANALYST`: Formulates differential hypotheses, statistical models, and trend evaluations.
  - `RESEARCHER`: Synthesizes authoritative evidence from standard guidelines (AHA/ACC, ESC, NICE, KDIGO).
  - `REVIEWER`: Acts as the rigorous quality and contraindication safety barrier.
  - `SYNTHESIZER`: Merges findings into cohesive, beautifully styled Markdown.
  - `ASSISTANT`: Friendly, conversational ChatGPT-style persona for general everyday queries.

### 4. Deterministic Clinical Calculators (AgentX Medical)
- **eGFR (CKD-EPI 2021)**: High-precision implementation of the 2021 race-free equation:
  $$\text{eGFR} = 142 \times \min(S_{cr}/\kappa, 1)^\alpha \times \max(S_{cr}/\kappa, 1)^{-1.200} \times 0.9938^{\text{Age}} \times [1.012 \text{ if female}]$$
- **$\text{CHA}_2\text{DS}_2\text{-VASc}$ Risk Score**: Automatic clinical stratification for non-valvular atrial fibrillation stroke risk and oral anticoagulant (OAC) guideline triggers.
- **Drug Safety & Lab Analyzer**: Identifies drug-drug interactions, black-box warnings, and laboratory standard reference ranges.

### 5. Modern Jetpack Compose UI & Live HUD
- **Real-Time Telemetry HUD**: Live floating overlay displaying tokens/sec ($t/s$), time-to-first-token latency ($ms$), generated token counter, and memory footprint.
- **Interactive Prompt Starters**: 1-tap starter chips on the Chat Screen for instant queries.
- **Device Hardware Inspector**: Scans device hardware (CPU cores, RAM, heap limits) without crashing on Android 14/15 (`RECEIVER_NOT_EXPORTED` compliant).
- **Active Model Readiness Banner**: Real-time status indicator (`🟢 READY`) showing loaded model, quantization, and context limits.

---

## 🔒 Permissions & Security Architecture

| Permission | Purpose | Application |
|---|---|---|
| `android.permission.INTERNET` | Model downloads & Gemini Cloud streaming | AgentX, AgentX Medical, AgentX Serve |
| `android.permission.ACCESS_NETWORK_STATE` | Network connectivity monitoring | AgentX, AgentX Medical, AgentX Serve |
| `android.permission.ACCESS_WIFI_STATE` | Resolves device LAN IP address (e.g. `192.168.1.x:8080`) | AgentX, AgentX Serve |
| `android.permission.FOREGROUND_SERVICE` | Uninterrupted edge server execution | AgentX, AgentX Serve |
| `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` | Android 14+ background edge socket compliance | AgentX Serve |
| `android.permission.WAKE_LOCK` | Sustains CPU execution during inference and streaming | AgentX Serve |
| `android.permission.POST_NOTIFICATIONS` | Foreground service status and download progress | AgentX, AgentX Medical, AgentX Serve |
| `android:largeHeap="true"` | Grants maximum available heap space | AgentX, AgentX Medical, AgentX Serve |
| `android:usesCleartextTraffic="true"` | Enables local LAN HTTP communication on internal Wi-Fi | AgentX, AgentX Medical, AgentX Serve |

---

## 🛠️ Getting Started & Build Instructions

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1) or newer
- **Java Development Kit**: JDK 17 or JDK 21 (Eclipse Adoptium recommended)
- **Android SDK**: Compile SDK 35 (Android 15), Min SDK 26 (Android 8.0)

### Build & Run
```bash
# Clone the repository
git clone https://github.com/Sharmapank-j/agentX-suite.git
cd agentX-suite

# Run the complete test suite across all 3 modules
./gradlew testDebugUnitTest

# Build Debug APKs for all 3 applications
./gradlew :app:assembleDebug :medical:assembleDebug :admin:assembleDebug
```

### Generated Artifacts
- **AgentX Client App**: `assets/agentx/AgentX-v1.0.0-debug.apk` (`com.agentx`)
- **AgentX Medical App**: `assets/agentx_medical/AgentX-Medical-v1.0.0-debug.apk` (`com.agentx.medical`)
- **AgentX Serve Host App**: `assets/agentx_serve/AgentX-Serve-v1.0.0-debug.apk` (`com.agentx.admin`)

---

## 🧪 Verification & Test Suite

The test suite covers domain models, tool calculators, sliding context management, multi-agent planning, local server engine, and view models.

```bash
> Task :testDebugUnitTest

com.agentx.ChatViewModelTest                PASSED
com.agentx.ContextManagerTest               PASSED
com.agentx.DomainAndRuntimeTests            PASSED
com.agentx.GgufMetadataParserTest           PASSED
com.agentx.HuggingFaceDownloaderTest        PASSED
com.agentx.LlamaCppEngineTest               PASSED
com.agentx.Phase5ToolTests                  PASSED
com.agentx.Phase6MultiAgentTests            PASSED
com.agentx.Phase7OptimizationTests          PASSED
com.agentx.medical.MedicalChatViewModelTest PASSED
com.agentx.admin.LocalModelServerEngineTest PASSED

BUILD SUCCESSFUL: 100% tests PASSED (0 failures)
```

---

## 🛡️ Legal, Regulatory & Trademark Notice

- **Medical Non-Diagnostic Notice**: AgentX Medical is intended solely for educational, research, and assistive reasoning purposes. It is **NOT** an FDA-cleared or CE-marked medical device. All recommendations must be verified by a licensed clinician before clinical action. See [DISCLAIMER.md](DISCLAIMER.md) for full details.
- **Trademark Disclaimer**: "AgentX Suite" is an independent project. References to "OpenAI", "ChatGPT", "Google", "Gemini", and "Apple" are nominative fair-use citations of protocol compatibility and industry paradigms. See [TRADEMARK_AND_IP.md](TRADEMARK_AND_IP.md).
- **License**: Distributed under the terms of the [Apache License, Version 2.0](LICENSE).
