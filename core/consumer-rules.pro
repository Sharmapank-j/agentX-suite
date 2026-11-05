# ==============================================================================
# AgentX Core Library — Consumer ProGuard Rules
# ==============================================================================

# 1. Native JNI Bridge (llama.cpp)
-keepclasseswithmembernames class com.agentx.core.inference.llamacpp.LlamaCppNativeBridge {
    native <methods>;
}
-keep class com.agentx.core.inference.llamacpp.LlamaCppNativeBridge { *; }

# Keep native methods across all core classes
-keepclasseswithmembernames class * {
    native <methods>;
}

# 2. Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses, EnclosingMethod

-keep @kotlinx.serialization.Serializable class com.agentx.core.** { *; }

-keepclassmembers @kotlinx.serialization.Serializable class com.agentx.core.** {
    *** Companion;
}

-keepclasseswithmembers class com.agentx.core.** {
    kotlinx.serialization.KSerializer serializer();
}

# 3. Core Domain & Provider Models
-keep class com.agentx.core.inference.model.** { *; }
-keep class com.agentx.core.memory.** { *; }
-keep class com.agentx.core.rag.** { *; }
-keep class com.agentx.core.agent.tools.** { *; }
-keep class com.agentx.core.protocol.asp.** { *; }
