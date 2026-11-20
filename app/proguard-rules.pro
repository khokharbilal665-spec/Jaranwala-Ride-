# ==============================================================================
# Jaranwala Ride Production ProGuard / R8 Rules
# ==============================================================================

# 1. Source attributes & crash diagnostics
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions
-renamesourcefileattribute SourceFile

# Preserve Kotlin Metadata for reflection (Moshi KotlinJsonAdapterFactory, Firestore, Coroutines)
-keep class kotlin.Metadata { *; }

# Strip debug/verbose logs in release builds so App Check tokens & diagnostics do not leak
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# ==============================================================================
# 2. Firebase App Check, reCAPTCHA Enterprise & Play Integrity (Production)
# ==============================================================================
-keep class com.google.firebase.appcheck.** { *; }
-keepclassmembers class com.google.firebase.appcheck.** { *; }
-keep class * implements com.google.firebase.appcheck.AppCheckProviderFactory { *; }
-keep class * implements com.google.firebase.appcheck.AppCheckProvider { *; }
-keep class com.google.android.recaptcha.** { *; }
-keepclassmembers class com.google.android.recaptcha.** { *; }
-keep class com.google.android.play.core.integrity.** { *; }
-dontwarn com.google.firebase.appcheck.**
-dontwarn com.google.android.recaptcha.**
-dontwarn com.google.android.play.core.**

# ==============================================================================
# 3. Firebase Core, Firestore, Auth, Messaging & AI Logic
# ==============================================================================
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep <methods>;
    @androidx.annotation.Keep <fields>;
}
-keep class * implements com.google.firebase.components.ComponentRegistrar
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.DocumentId <fields>;
    @com.google.firebase.firestore.ServerTimestamp <fields>;
    @com.google.firebase.firestore.IgnoreExtraProperties <fields>;
}
-keep class com.google.firebase.messaging.** { *; }
-keep class com.example.util.JaranwalaFirebaseMessagingService { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# App Firestore & Domain Models (preserve no-arg constructors & fields for Firestore toObject())
-keep class com.example.data.* { *; }
-keepclassmembers class com.example.data.* { *; }

# ==============================================================================
# 4. Retrofit 2 & OkHttp 4 (R8 Full Mode Compatible)
# ==============================================================================
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation class <1>
-keep interface com.example.pricing.OsrmRoutingApi { *; }
-keep interface com.example.pricing.NominatimGeocodingApi { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn kotlin.Unit

# ==============================================================================
# 5. Moshi Serialization (KSP Codegen + KotlinJsonAdapterFactory)
# ==============================================================================
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.Json <methods>;
}
-keep class **JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}
-keep class com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory { *; }
-keep class com.example.pricing.OsrmRouteResponse { *; }
-keep class com.example.pricing.OsrmRoute { *; }
-keep class com.example.pricing.NominatimPlaceDto { *; }
-dontwarn com.squareup.moshi.**

# ==============================================================================
# 6. Room Local Persistence
# ==============================================================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.example.data.local.** { *; }
-dontwarn androidx.room.paging.**

# ==============================================================================
# 7. Credential Manager, Google Sign-In, Biometric & OSMDroid
# ==============================================================================
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.biometric.** { *; }
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ==============================================================================
# 8. Kotlin Coroutines & Coil
# ==============================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
-dontwarn coil.**
