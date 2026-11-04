# AgentX Development Guide

## Prerequisites
- Android Studio Ladybug or later
- Android SDK 35 (Platforms and Build Tools)
- JDK 21 (Eclipse Temurin / OpenJDK)
- Android NDK 26+ and CMake 3.22+ (Phases 3+)

## Project Setup
1. Clone the repository into your Android workspace.
2. Ensure `local.properties` points to your local Android SDK installation:
   ```properties
   sdk.dir=C:\\Users\\<User>\\AppData\\Local\\Android\\Sdk
   ```
3. Set your `JAVA_HOME` to JDK 21:
   ```properties
   org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot
   ```
4. Build the project using Gradle:
   ```bash
   ./gradlew assembleDebug
   ```

## Development Phases
- **Phase 1**: Foundation, Architecture & Material 3 Compose UI (Completed)
- **Phase 2**: Storage Access Framework (SAF) GGUF Model Management & Header Validation (Completed)
- **Phase 3**: llama.cpp JNI Native Bridge & Token Streaming (Next)
- **Phase 4**: Local Chat System with Context Window Budgeting
- **Phase 5**: Agent Runtime, Structured Tool Calling & Supervisor
- **Phase 6**: Multi-Agent Collaborative Pipelines
- **Phase 7**: Performance Profiling, Memory Safeguards & Release APK

---

## Phase 2 Architecture: GGUF Model Ingestion
- **Pure Kotlin Header Parser (`GgufMetadataParser`)**: Direct streaming reader for GGUF binary format (v2/v3). Validates magic `0x46554747` ("GGUF"), extracts architecture, context window, tensor count, and quantization type.
- **Model File Stager (`ModelFileManager`)**: Copies SAF `content://` files to app-managed storage (`context.getExternalFilesDir("models")`) to satisfy native POSIX filesystem paths for llama.cpp, equipped with a pre-flight storage check and chunked streaming copy progress.
