# Android, Retrofit, Gson, MapLibre & Bluetooth/OBD release rules.
# Dependencies' own consumer ProGuard rules remain in effect.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keepclasseswithmembers class * { native <methods>; }

# Retrofit service interfaces use annotations and a dynamic proxy.
-keep interface tr.id4.mediahub.nativecockpit.data.routing.OsrmService { *; }
-keep interface tr.id4.mediahub.nativecockpit.data.routing.PhotonService { *; }
-keep interface tr.id4.mediahub.nativecockpit.data.weather.OpenMeteoService { *; }
-keep interface tr.id4.mediahub.nativecockpit.data.remote.EpdkService { *; }

# Gson annotations may be read reflectively in future DTOs.
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# NativeMapView is reached through MapLibre's JNI boundary.
# The MapLibre SDK supplies its own consumer rules for the remaining classes.
-keep class org.maplibre.android.MapLibre { *; }
-keep class org.maplibre.android.maps.NativeMapView { *; }

# Keep the app-specific classic Bluetooth/ELM327 transports, parsers and
# Android Auto service. Android framework Bluetooth APIs cannot be shrunk.
-keep class tr.id4.mediahub.nativecockpit.data.obd.** { *; }
-keep class tr.id4.mediahub.nativecockpit.car.EyaCarAppService { *; }

# No Moshi dependency is currently used by nativecockpit.
# Do not hide missing classes with blanket -dontwarn rules.
