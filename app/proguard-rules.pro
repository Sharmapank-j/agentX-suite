# ==============================================================================
# AgentX Medical — R8 / ProGuard Optimization & Shrinking Rules
# ==============================================================================

# ── 1. JNI & llama.cpp Native Bridge ──────────────────────────────────────────
# Retain the native bridge class and all its native methods
-keepclassmembers class com.agentx.core.inference.LlamaCppBridge {
    public static <methods>;
    native <methods>;
}

-keep class com.agentx.core.inference.LlamaCppBridge { *; }

# Retain the functional interface invoked from C++ JNI callback loop
-keep interface com.agentx.core.inference.NativeTokenCallback {
    public *;
}
-keep class * implements com.agentx.core.inference.NativeTokenCallback {
    public *;
}

# Keep native method names matching JNI export convention
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── 2. Kotlinx Serialization ──────────────────────────────────────────────────
# Retain all @Serializable models, their companion objects, and generated serializers
-keepattributes *Annotation*, InnerClasses, EnclosingMethod

-keep @kotlinx.serialization.Serializable class * {
    *;
}

-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
}

-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer();
}

-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Retain specific core serializable models
-keep class com.agentx.domain.models.** { *; }
-keep class com.agentx.core.agent.core.** { *; }
-keep class com.agentx.core.agent.orchestrator.** { *; }
-keep class com.agentx.core.agent.execution.** { *; }
-keep class com.agentx.core.agent.tools.** { *; }

# ── 3. Room Database & SQLite Entities ─────────────────────────────────────────
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase { *; }

-keep @androidx.room.Entity class * { *; }
-keep class com.agentx.data.local.entity.** { *; }

-keep @androidx.room.Dao interface * { *; }
-keep class com.agentx.data.local.dao.** { *; }

-keepclassmembers class * {
    @androidx.room.Query *;
    @androidx.room.Insert *;
    @androidx.room.Delete *;
    @androidx.room.Update *;
}

# ── 4. Jetpack Compose & UI ───────────────────────────────────────────────────
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.ui.** { *; }

# ── 5. Coroutines ─────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── 6. Optimization Tweaks ────────────────────────────────────────────────────
# Suppress harmless warnings from optional dependencies
-dontwarn okio.**
-dontwarn java.lang.invoke.**
