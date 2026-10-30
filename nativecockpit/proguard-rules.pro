# :nativecockpit only — conservative, temporary R8 stabilization.
# Other modules (:app, :evlite and Mola/Fermata) are untouched.
# No device stack trace was provided: these rules do NOT prove a specific crash fix.
#
# While the startup crash is investigated, preserve shrinking and obfuscation but
# disable code optimization. Remove after retraced Release UAT is successful.
-dontoptimize
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable
-keepclasseswithmembers class * { native <methods>; }

# Hilt/Dagger Android entrypoints and generated injection members.
# Dagger/Hilt already package consumer ProGuard rules, so do not globally disable shrinking.
-keep class tr.id4.mediahub.nativecockpit.EyaApplication { *; }
-keep class tr.id4.mediahub.nativecockpit.MainActivity { *; }
-keep class tr.id4.mediahub.nativecockpit.car.EyaCarAppService { *; }
-keep class tr.id4.mediahub.nativecockpit.Hilt_* { *; }
-keep class tr.id4.mediahub.nativecockpit.car.Hilt_* { *; }
-keep class tr.id4.mediahub.nativecockpit.presentation.**.Hilt_* { *; }
-keep class tr.id4.mediahub.nativecockpit.**_Factory { *; }
-keep class tr.id4.mediahub.nativecockpit.**_MembersInjector { *; }
-keep class tr.id4.mediahub.nativecockpit.Dagger* { *; }

# Retrofit 3 (dynamic proxies, suspend and generic response signatures).
-keep class kotlin.coroutines.Continuation
-keep interface tr.id4.mediahub.nativecockpit.data.routing.OsrmService { *; }
-keep interface tr.id4.mediahub.nativecockpit.data.routing.PhotonService { *; }
-keep interface tr.id4.mediahub.nativecockpit.data.remote.EpdkService { *; }

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
-keep class tr.id4.mediahub.nativecockpit.data.obd.** { *; }
-keep class tr.id4.mediahub.nativecockpit.domain.usecase.Elm327Session { *; }
-keep class tr.id4.mediahub.nativecockpit.car.EyaCarMapSurface { *; }

# No blanket -dontwarn. Missing APIs should fail the build.
