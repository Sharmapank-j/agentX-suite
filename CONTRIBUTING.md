# Contributing to AgentX Suite

We welcome contributions to the **AgentX Suite** (`AgentX`, `AgentX Medical`, and `AgentX Serve`)! Whether you are fixing bugs, implementing new clinical tools, enhancing local LLM performance, or optimizing Compose animations, your help is appreciated.

---

## 🏛️ Architecture Principles

1. **Local-First & Privacy Preserving**:
   - Always prioritize offline on-device computation via GGUF and llama.cpp JNI.
   - Never log, leak, or transmit user data or patient clinical information without explicit user consent.
2. **Defensive Stability & Null Safety**:
   - The native C++ bridge must strictly check pointers and catch JNI exceptions (`env->ExceptionCheck()`).
   - Coroutine cancellations must never crash the ART VM.
3. **Deterministic Tool Computations**:
   - Never allow LLMs to approximate clinical scores or mathematical equations. Always route calculations through deterministic Kotlin tools in `ClinicalToolExecutor`.
4. **Clean Multi-Agent Boundaries**:
   - Maintain clear separation between Planning (`HeuristicTaskPlanner`), Execution (`ClinicalAgentExecutor`), and Aggregation (`ClinicalResultAggregator`).

---

## 🛠️ Development Setup

1. **Prerequisites**:
   - Android Studio Ladybug / Meerkat (or newer)
   - JDK 17 or JDK 21 (Eclipse Adoptium recommended)
   - Android SDK 35 (Android 15)
   - Android NDK 26+ and CMake 3.22.1+

2. **Clone & Build**:
   ```bash
   git clone https://github.com/Sharmapank-j/agentX-suite.git
   cd agentX-suite
   ./gradlew assembleDebug
   ```

3. **Run Unit Tests**:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```
   *All 97 unit tests must pass before submitting a pull request.*

---

## 📋 Pull Request Guidelines

1. Fork the repository and create a feature branch (`git checkout -b feature/amazing-feature`).
2. Adhere to Kotlin Official Style Guide and Jetpack Compose best practices.
3. Ensure no memory leaks or ANRs on large GGUF model files.
4. Add unit tests for any new agents, tools, or view models.
5. Submit your PR with a clear description and testing evidence.
