package com.agentx.core.ota

import kotlinx.serialization.Serializable

/**
 * Granular component classification for modular OTA updates.
 * Enables independently hot-patching medical intelligence, clinical guidelines,
 * models, prompt templates, and web assets without full APK reinstalls.
 */
@Serializable
enum class ComponentType {
    /**
     * Medical knowledge pack containing triage protocols, guideline revisions,
     * clinical formula calculators, and updated entity dictionaries.
     */
    INTELLIGENCE_PACK,

    /**
     * Specific clinical guideline datasets (e.g. NICE, GOLD, Surviving Sepsis, AHA/ACC).
     */
    CLINICAL_GUIDELINES,

    /**
     * Local GGUF language model weights (e.g. Medimind updates), LoRA adapters,
     * or multimodal vision projector binaries (mmproj).
     */
    MODEL_WEIGHTS,

    /**
     * System prompt templates, clinical persona parameters, or tool orchestration schemas.
     */
    PROMPT_TEMPLATES,

    /**
     * Embedded responsive Web Chat Client HTML/JS/CSS assets for browser clients.
     */
    WEB_CLIENT,

    /**
     * Native library acceleration modules or device-specific JNI binaries.
     */
    NATIVE_LIBRARY
}
