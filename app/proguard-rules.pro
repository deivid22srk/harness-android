# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Our serializable models.
-keep,includedescriptorclasses class dev.harness.**$$serializer { *; }
-keepclassmembers class dev.harness.** {
    *** Companion;
}
-keepclasseswithmembers class dev.harness.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Pi RPC events are reflection-free but keep names stable for debugging.
-keepnames class dev.harness.core.pi.**
