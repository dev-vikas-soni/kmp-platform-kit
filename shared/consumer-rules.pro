# ── KMP Platform Kit - ProGuard / R8 Consumer Rules ──────────────────────
# These rules are bundled inside the AAR and automatically applied
# when the host app builds with R8 (minifyEnabled = true).
# They ensure the SDK works correctly after shrinking.

# ── SDK public API surface ──────────────────────────────────────────
# Keep only public classes/members that host apps call directly.
-keep class com.droidunplugged.kmp_platform_kit.core.SDKInitializer { public *; }
-keep class com.droidunplugged.kmp_platform_kit.core.SDKCredentials { public *; }
-keep class com.droidunplugged.kmp_platform_kit.core.SDKState { public *; }
-keep class com.droidunplugged.kmp_platform_kit.core.SDKState$* { public *; }
-keep class com.droidunplugged.kmp_platform_kit.core.SDKInfo { public *; }
-keep class com.droidunplugged.kmp_platform_kit.core.SDKConfig { public *; }
-keep class com.droidunplugged.kmp_platform_kit.core.ApiResult { public *; }
-keep class com.droidunplugged.kmp_platform_kit.core.ApiResult$* { public *; }
-keep class com.droidunplugged.kmp_platform_kit.core.RetryConfig { public *; }

# Keep all facade objects (public API entry points per feature)
-keep class com.droidunplugged.kmp_platform_kit.features.**.facade.** { public *; }

# Keep all public model classes exposed to host apps
-keep class com.droidunplugged.kmp_platform_kit.features.**.models.Inventories { *; }
-keep class com.droidunplugged.kmp_platform_kit.features.**.models.InventoryListModel { *; }
-keep class com.droidunplugged.kmp_platform_kit.models.** { *; }

# ── Koin DI - needs reflection ─────────────────────────────────────
-keep class org.koin.core.** { *; }
-keep class org.koin.dsl.** { *; }
-dontwarn org.koin.**

# ── kotlinx.serialization - keep @Serializable classes ─────────────
-keepattributes *Annotation*
-keepclassmembers class com.droidunplugged.kmp_platform_kit.**.models.** { *; }
-keep class com.droidunplugged.kmp_platform_kit.**.models.**$$serializer { *; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# ── Ktor - keep only what the SDK actually uses ────────────────────
-keep class io.ktor.client.HttpClient { *; }
-keep class io.ktor.client.engine.** { *; }
-keep class io.ktor.client.plugins.** { *; }
-keep class io.ktor.client.request.** { *; }
-keep class io.ktor.client.statement.** { *; }
-keep class io.ktor.http.** { *; }
-keep class io.ktor.utils.io.** { *; }
-dontwarn io.ktor.**

# ── OkHttp (transitive from Ktor on Android) ──────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
