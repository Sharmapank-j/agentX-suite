# ProGuard rules for AgentX Medical
-keep class com.agentx.medical.core.inference.LlamaCppEngine { *; }
-keepclassmembers class * {
    native <methods>;
}
