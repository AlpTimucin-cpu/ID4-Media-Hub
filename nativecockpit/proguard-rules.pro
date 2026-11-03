# :nativecockpit only — conservative, temporary R8 stabilization.
# Other modules (:app, :evlite and MediaEngine/Fermata) are untouched.
# No device stack trace was provided: these rules do NOT prove a specific crash fix.
#
# These rules are dormant while native Release isMinifyEnabled=false.
# If shrinking is re-enabled, diagnose a retraced FATAL EXCEPTION before adding
# further keep rules. Layer ownership crashes require a source fix, not a keep rule.
-dontoptimize
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable
-keepclasseswithmembers class * { native <methods>; }

# Hilt/Dagger Android entrypoints and generated injection members.
# Dagger/Hilt already package consumer ProGuard rules, so do not globally disable shrinking.
-keep class com.evcockpit.EyaApplication { *; }
-keep class com.evcockpit.MainActivity { *; }
-keep class com.evcockpit.car.EyaCarAppService { *; }
-keep class com.evcockpit.Hilt_* { *; }
-keep class com.evcockpit.car.Hilt_* { *; }
-keep class com.evcockpit.presentation.**.Hilt_* { *; }
-keep class com.evcockpit.**_Factory { *; }
-keep class com.evcockpit.**_MembersInjector { *; }
-keep class com.evcockpit.Dagger* { *; }

# Retrofit 3 (dynamic proxies, suspend and generic response signatures).
-keep class kotlin.coroutines.Continuation
-keep interface com.evcockpit.data.routing.OsrmService { *; }
-keep interface com.evcockpit.data.routing.PhotonService { *; }
-keep interface com.evcockpit.data.remote.EpdkService { *; }

# Gson reflection over annotated DTO fields, plus library consumer rules.
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# MapLibre JNI, lifecycle and external surface entrypoints.
-keep class org.maplibre.android.MapLibre { *; }
-keep class org.maplibre.android.maps.NativeMapView { *; }
-keep class org.maplibre.android.maps.MapView { *; }
-keep class org.maplibre.android.maps.Style { *; }

# Vehicle diagnostic protocol, Bluetooth transports and Android Auto host surface.
-keep class com.evcockpit.data.obd.** { *; }
-keep class com.evcockpit.domain.usecase.Elm327Session { *; }
-keep class com.evcockpit.car.EyaCarMapSurface { *; }

# No blanket -dontwarn. Missing APIs should fail the build.
