# Add project specific ProGuard rules here.
# Most libraries used in this project (Room, Hilt, kotlinx.serialization, Coil,
# CameraX, Retrofit/OkHttp, WorkManager) ship their own consumer-rules.pro inside
# their AARs, which R8 picks up automatically — this file should stay close to
# empty. Only add a rule here when a real R8-stripped-release crash proves one is
# needed, with a comment linking to what broke. Do not pre-emptively keep classes
# "just in case" (Constitution rule 2: no speculative code).

# Kotlin metadata is required for reflection-based library integrations (Room,
# Hilt, kotlinx.serialization) to see suspend functions / data class signatures.
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keep class kotlin.Metadata { *; }
