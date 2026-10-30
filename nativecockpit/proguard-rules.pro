# Elektrikli Yol Arkadaşım nativecockpit — R8 full-mode safety net.
# These rules complement the consumer rules supplied by MapLibre, Retrofit and Gson.
# Avoid -dontwarn ** or blanket -keep class retrofit2.** which hide errors and bloat APKs.

# Retrofit suspend services and JSON reflection require retained annotations/signatures.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class kotlin.coroutines.Continuation

# Retrofit 3 bundles consumer rules; retain only application-specific service interfaces.
-keep interface tr.id4.mediahub.nativecockpit.data.routing.OsrmService { *; }
-keep interface tr.id4.mediahub.nativecockpit.data.routing.PhotonService { *; }
-keep interface tr.id4.mediahub.nativecockpit.data.remote.EpdkService { *; }

# Gson consumer rules cover annotated DTOs. Keep explicit SerializedName fields as a safeguard.
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# MapLibre Android JNI/rendering entrypoints. Other MapLibre classes use SDK consumer rules.
-keepclasseswithmembers class * { native <methods>; }
-keep class org.maplibre.android.MapLibre { *; }
-keep class org.maplibre.android.maps.NativeMapView { *; }
-keep class org.maplibre.android.maps.MapView { *; }

# OBD uses Android Bluetooth API / ELM327 parser reflection-adjacent integrations.
-keep class tr.id4.mediahub.nativecockpit.data.obd.** { *; }

# Manifest Android Auto entrypoint; remaining Hilt entrypoints are generated/manifest kept.
-keep class tr.id4.mediahub.nativecockpit.car.EyaCarAppService { *; }

# No Moshi dependency, custom foreground service or reflective Retrofit DTO in this module.
