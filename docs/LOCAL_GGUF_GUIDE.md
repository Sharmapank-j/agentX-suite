# AgentX Suite — Local GGUF Model & Vision Projector Guide (v1.0.0)

**Engine:** `llama.cpp` (Upstream b4088 with `mtmd` Multimodal Projector Support)  
**Execution Mode:** Pure On-Device NDK + JNI (CPU + optional GPU acceleration)  
**Modules:** AgentX (`:app`), AgentX Medical (`:medical`), AgentX Serve (`:admin`)  

---

## 1. Supported Model Families & Architectures

The AgentX native inference bridge (`libagentx_llama.so`) executes any GGUF model compatible with standard `llama.cpp`:

| Model Architecture | Recommended Size | Recommended Quantization | Memory Required | Best Use Case |
| :--- | :--- | :--- | :--- | :--- |
| **Llama 3.2** | 1B / 3B | `Q4_K_M` | 1.1 GB – 2.4 GB | General everyday reasoning, chat, offline code generation |
| **Qwen 2.5** | 1.5B / 3B / 7B | `Q4_K_M` / `Q4_0` | 1.2 GB – 4.5 GB | Multilingual conversations, structured JSON parsing |
| **SmolLM2** | 360M / 1.7B | `Q4_K_M` | 450 MB – 1.3 GB | Ultra-low RAM budget devices (3GB–4GB RAM phones) |
| **Gemma 2** | 2B | `Q4_K_M` | 1.8 GB | Mathematical explanations, creative synthesis |
| **BioMistral** | 7B | `Q4_K_M` | 4.8 GB | Medical domain knowledge, pharmacology reference |
| **Moondream2 / MiniCPM-V** | 1.5B – 3B | `Q4_K_M` + `mmproj` | 1.8 GB – 3.2 GB | **Multimodal Vision**: Offline image reasoning, document OCR |

---

## 2. Multimodal Local Vision Engine Setup

Starting in **v1.0.0**, AgentX supports offline visual understanding directly on mobile devices without sending images to the cloud:

1. **Obtain the Language Model GGUF**:
   Download a vision-capable language model (e.g. `moondream2-text-model-q4_k.gguf` or `llama-3.2-11b-vision-instruct-q4_k_m.gguf`).
2. **Obtain the Matching Multimodal Projector**:
   Download the matching `mmproj-*.gguf` file (e.g. `mmproj-moondream2-f16.gguf`).
3. **Directory Placement**:
   Place both files into the same folder on your device:
   - `/sdcard/Download/`
   - or `/sdcard/Documents/models/`
   AgentX's storage scanner automatically matches language weights with projector weights by filename pattern and binds them together in the Model Hub.
4. **Verification**:
   When loaded, the model card displays the **📷 Vision Capable** badge, and the chat screen enables the image attachment button.

---

## 3. Safe Model Hot-Swapping & Memory Management

Mobile operating systems enforce strict per-application memory limits. To prevent Out-Of-Memory (OOM) crashes:
- **Single-Flight Mutex**: Model loading and unloading operations run within a strict coroutine mutex.
- **Explicit Context Deallocation**: When switching models, the previous native `llama_context` and `llama_model` pointers are explicitly freed via `llama_free()` and `llama_free_model()` before new weights are loaded into RAM.
- **RAM Safety Bar**: The dynamic RAM gauge in the Models tab warns when memory exceeds 80% and offers an immediate **1-Tap Unload** button.

---

## 4. Hardware Profiling & On-Device Benchmarking

AgentX includes a built-in hardware profiler measuring real-time mobile SoC performance:
- **Time-To-First-Token (TTFT)**: Prompt ingestion latency measured in milliseconds.
- **Throughput**: Sustained tokens per second (`tok/s`).
- **RAM Delta**: Exact memory footprint difference before and after model allocation.
- **Thermal Status**: Monitored continuously via `HardwareProfiler` to alert when device throttling occurs.
