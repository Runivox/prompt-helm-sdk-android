# Consumer ProGuard / R8 rules for app.prompthelm:sdk-android
#
# These rules are applied automatically when an Android app that
# minifies its release build depends on this SDK. They preserve
# the public API surface and the kotlinx.serialization metadata
# needed for @Serializable models to round-trip correctly.

# --- Public SDK surface ---
-keep public class app.prompthelm.sdk.** { public *; }
-keep public interface app.prompthelm.sdk.** { *; }
-keep public enum app.prompthelm.sdk.** { *; }

# --- Sealed hierarchy for stream events ---
-keep class app.prompthelm.sdk.StreamEvent { *; }
-keep class app.prompthelm.sdk.StreamEvent$* { *; }

# --- Exception hierarchy (must keep names so callers can `catch` by type) ---
-keep public class app.prompthelm.sdk.PromptHelmException { *; }
-keep public class app.prompthelm.sdk.AuthenticationException { *; }
-keep public class app.prompthelm.sdk.AuthorizationException { *; }
-keep public class app.prompthelm.sdk.NotFoundException { *; }
-keep public class app.prompthelm.sdk.RateLimitException { *; }
-keep public class app.prompthelm.sdk.ApiException { *; }
-keep public class app.prompthelm.sdk.PromptHelmTimeoutException { *; }

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class app.prompthelm.sdk.** {
    static <1>$Companion Companion;
    static **$$serializer INSTANCE;
}
-keepclasseswithmembers class app.prompthelm.sdk.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.prompthelm.sdk.**$$serializer { *; }

# --- Ktor (defensive — Ktor ships its own consumer rules but we belt-and-suspenders) ---
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
