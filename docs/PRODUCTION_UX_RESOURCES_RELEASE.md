# EV Cockpit — Production Splash, Icon, Android Resources and Signed Release

Complete XML, Gradle and GitHub Actions sources prepared for GitHub mobile review. Public publishing requires the owner's existing keystore and completed real-device acceptance testing. The release workflow must run from the default branch after the feature is merged.

## nativecockpit/build.gradle.kts

```kotlin
import java.io.File
import java.io.DataOutputStream
import kotlin.math.sin
import kotlin.math.min
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// Local release signing credentials live in ignored local.properties.
// GitHub Actions supplies the same values as environment variables.
val localSigning = Properties().apply {
    val config = rootProject.file("local.properties")
    if (config.isFile) config.inputStream().use { load(it) }
}

fun signingValue(propertyKey: String, environmentKey: String): String? =
    providers.environmentVariable(environmentKey).orNull
        ?.takeIf { it.isNotBlank() }
        ?: localSigning.getProperty(propertyKey)?.trim()?.takeIf { it.isNotBlank() }

val storePath = signingValue("eya.release.storeFile", "EYA_RELEASE_STORE_FILE")
val storePasswordValue = signingValue("eya.release.storePassword", "EYA_RELEASE_STORE_PASSWORD")
val keyAliasValue = signingValue("eya.release.keyAlias", "EYA_RELEASE_KEY_ALIAS")
val keyPasswordValue = signingValue("eya.release.keyPassword", "EYA_RELEASE_KEY_PASSWORD")
val releaseStore = storePath?.let { path ->
    File(path).let { if (it.isAbsolute) it else rootProject.file(path) }
}
val signedReleaseReady = releaseStore?.isFile == true &&
    !storePasswordValue.isNullOrBlank() &&
    !keyAliasValue.isNullOrBlank() &&
    !keyPasswordValue.isNullOrBlank()

android {
    namespace = "com.evcockpit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.evcockpit"
        minSdk = 28
        targetSdk = 35
        versionCode = 23
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        // No debug-key fallback. This is populated only with real owner credentials.
        if (signedReleaseReady) {
            create("eyaProduction") {
                storeFile = releaseStore
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Debug variant for development; uses the same real GPS and OBD repositories.
            isMinifyEnabled = false
        }
        getByName("release") {
            isDebuggable = false
            // Production build: R8 code and resource shrinking enabled.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signedReleaseReady) {
                signingConfig = signingConfigs.getByName("eyaProduction")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = false }
}

// Fail closed: a signing-less artifact must NEVER be called a production APK.
gradle.taskGraph.whenReady {
    val buildingRelease = allTasks.any { task ->
        task.project.path == ":nativecockpit" &&
            task.name in setOf("assembleRelease", "packageRelease", "validateSigningRelease")
    }
    if (buildingRelease && !signedReleaseReady) {
        throw GradleException(
            "Production release signing is not configured. Set " +
                "eya.release.storeFile/storePassword/keyAlias/keyPassword " +
                "in ignored local.properties (or EYA_RELEASE_* environment variables)."
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.activity:activity-ktx:1.12.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("com.google.dagger:hilt-android:2.58")
    ksp("com.google.dagger:hilt-compiler:2.58")
    implementation("org.maplibre.gl:android-sdk:13.7.1")
    implementation("org.maplibre.gl:android-sdk-turf:6.0.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-projected:1.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

// Generate ten original, copyright-free mono WAV loops during Gradle builds.
// GitHub's text-only mobile workflow does not require uploading binary assets.
val generatedSoundRes = layout.buildDirectory.dir("generated/active-sound/res")
android.sourceSets.getByName("main").res.srcDir(generatedSoundRes)

val generateOriginalEngineWavAssets = tasks.register("generateOriginalEngineWavAssets") {
    val output = generatedSoundRes
    outputs.dir(output)
    doLast {
        val raw = output.get().asFile.resolve("raw").apply { mkdirs() }
        val sampleRate = 22_050
        val count = sampleRate * 2
        val profiles = linkedMapOf(
            "v8" to 64.0, "f1" to 175.0, "futuristic" to 115.0,
            "turbine" to 93.0, "stealth" to 42.0
        )
        for ((profile, fundamental) in profiles) {
            for (accelerating in listOf(false, true)) {
                val mode = if (accelerating) "accel" else "idle"
                val file = raw.resolve("${profile}_${mode}.wav")
                DataOutputStream(file.outputStream().buffered()).use { out ->
                    fun word(n: Int) {
                        out.writeByte(n and 255)
                        out.writeByte((n ushr 8) and 255)
                    }
                    fun dword(n: Int) {
                        word(n and 0xffff)
                        word(n ushr 16)
                    }
                    out.writeBytes("RIFF")
                    dword(36 + count * 2)
                    out.writeBytes("WAVEfmt ")
                    dword(16)
                    word(1)  // signed 16-bit PCM, mono
                    word(1)
                    dword(sampleRate)
                    dword(sampleRate * 2)
                    word(2)
                    word(16)
                    out.writeBytes("data")
                    dword(count * 2)
                    for (i in 0 until count) {
                        val t = i.toDouble() / sampleRate
                        val f = fundamental * if (accelerating) 1.75 else 1.0
                        val pulse = 1.0 + 0.10 * sin(
                            2.0 * Math.PI * (if (profile == "v8") 8.0 else 5.0) * t
                        )
                        val signal = (
                            0.52 * sin(2 * Math.PI * f * t) +
                            0.24 * sin(2 * Math.PI * 2 * f * t) +
                            0.12 * sin(2 * Math.PI * 3 * f * t)
                        ) * pulse
                        // Short fades avoid audible clicks at repeat boundaries.
                        val edge = min(i, count - 1 - i)
                        val fade = (edge.toDouble() / 256).coerceIn(0.0, 1.0)
                        val pcm = (signal * fade * 0.23 * 32767)
                            .toInt().coerceIn(-32768, 32767)
                        word(pcm and 0xffff)
                    }
                }
            }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(generateOriginalEngineWavAssets) }
```

## nativecockpit/src/main/AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <!-- Android 13+: only request at runtime when posting a notification. -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="androidx.car.app.NAVIGATION_TEMPLATES" />
    <uses-permission android:name="androidx.car.app.ACCESS_SURFACE" />

    <queries>
        <package android:name="me.app.fermataX.auto.evcockpitpreview.debug" />
        <package android:name="me.app.fermataX.auto.evcockpitpreview" />
        <intent><action android:name="android.intent.action.TTS_SERVICE" /></intent>
        <package android:name="com.netflix.mediaclient" />
        <package android:name="com.disney.disneyplus" />
        <package android:name="com.amazon.avod.thirdpartyclient" />
    </queries>

    <application
        android:name=".EyaApplication"
        android:allowBackup="false"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher"
        android:usesCleartextTraffic="false"
        android:theme="@style/Theme.EvCockpit">
        <activity android:name=".MainActivity"
            android:theme="@style/Theme.EvCockpit.Starting"
            android:exported="true"
            android:supportsPictureInPicture="true"
            android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <meta-data android:name="com.google.android.gms.car.application"
            android:resource="@xml/automotive_app_desc" />
        <meta-data android:name="androidx.car.app.minCarApiLevel" android:value="1" />
        <service android:name=".car.EyaCarAppService"
            android:label="@string/app_name"
            android:icon="@mipmap/ic_launcher"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.car.app.CarAppService" />
                <category android:name="androidx.car.app.category.NAVIGATION" />
            </intent-filter>
        </service>
        <service
            android:name=".ObdForegroundService"
            android:exported="false"
            android:foregroundServiceType="connectedDevice" />
        <!-- Only an explicit user-initiated Bluetooth connection starts this service.
             No background auto-connect, no unrestricted background location service. -->
    </application>
</manifest>
```

## nativecockpit/src/main/res/values/colors.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ev_navy">#081320</color>
    <color name="ev_neon">#39FF88</color>
</resources>
```

## nativecockpit/src/main/res/values/styles.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.EvCockpit" parent="android:style/Theme.Material.NoActionBar">
        <item name="android:statusBarColor">@color/ev_navy</item>
        <item name="android:navigationBarColor">@color/ev_navy</item>
        <item name="android:windowLightStatusBar">false</item>
    </style>
    <style name="Theme.EvCockpit.Starting" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/ev_navy</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/ic_splash_animated</item>
        <item name="windowSplashScreenAnimationDuration">1200</item>
        <item name="postSplashScreenTheme">@style/Theme.EvCockpit</item>
    </style>
</resources>
```

## nativecockpit/src/main/res/drawable/ic_launcher_foreground.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <!-- All essential artwork stays within the adaptive icon safe zone. -->
    <path android:fillColor="@android:color/transparent"
        android:strokeColor="#39FF88" android:strokeWidth="3.8"
        android:strokeLineCap="round" android:strokeLineJoin="round"
        android:pathData="M31,70 L35,55 Q37,51 42,51 L66,51 Q71,51 73,55 L77,70 L73,75 L35,75 Z"/>
    <path android:fillColor="@android:color/transparent"
        android:strokeColor="#39FF88" android:strokeWidth="3"
        android:strokeLineCap="round"
        android:pathData="M38,61 L70,61 M41,70 L44,70 M64,70 L67,70"/>
    <path android:fillColor="@android:color/transparent"
        android:strokeColor="#39FF88" android:strokeWidth="2.4"
        android:strokeLineCap="round"
        android:pathData="M34,39 Q42,28 53,36 T74,34"/>
    <path android:fillColor="#39FF88" android:pathData="M34,35 a4,4 0,1 0,8 0 a4,4 0,1 0,-8 0"/>
    <path android:fillColor="#39FF88" android:pathData="M68,33 a4,4 0,1 0,8 0 a4,4 0,1 0,-8 0"/>
</vector>
```

## nativecockpit/src/main/res/drawable/ic_launcher_legacy.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#081320" android:pathData="M0,0 H108 V108 H0 Z"/>
    <path android:fillColor="@android:color/transparent"
        android:strokeColor="#39FF88" android:strokeWidth="3.8"
        android:strokeLineCap="round" android:strokeLineJoin="round"
        android:pathData="M31,70 L35,55 Q37,51 42,51 L66,51 Q71,51 73,55 L77,70 L73,75 L35,75 Z M38,61 L70,61 M41,70 L44,70 M64,70 L67,70 M34,39 Q42,28 53,36 T74,34"/>
    <path android:fillColor="#39FF88" android:pathData="M34,35 a4,4 0,1 0,8 0 a4,4 0,1 0,-8 0 M68,33 a4,4 0,1 0,8 0 a4,4 0,1 0,-8 0"/>
</vector>
```

## nativecockpit/src/main/res/mipmap-anydpi-v26/ic_launcher.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ev_navy"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

## nativecockpit/src/main/res/drawable/ic_splash_mark.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="@android:color/transparent"
        android:strokeColor="#39FF88" android:strokeWidth="4"
        android:strokeLineCap="round"
        android:pathData="M33,65 L38,51 L70,51 L75,65 L72,71 L36,71 Z M42,61 L66,61 M39,66 L44,66 M64,66 L69,66"/>
    <group android:name="orbit" android:pivotX="54" android:pivotY="54">
        <path android:fillColor="@android:color/transparent"
            android:strokeColor="#39FF88" android:strokeWidth="4"
            android:strokeLineCap="round"
            android:pathData="M54,17 A37,37 0,0 1,91 54"/>
        <path android:fillColor="@android:color/transparent"
            android:strokeColor="#39FF88" android:strokeAlpha="0.35"
            android:strokeWidth="3"
            android:strokeLineCap="round"
            android:pathData="M54,91 A37,37 0,0 1,17 54"/>
    </group>
</vector>
```

## nativecockpit/src/main/res/drawable/ic_splash_animated.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:drawable="@drawable/ic_splash_mark">
    <target android:name="orbit" android:animation="@animator/splash_orbit"/>
</animated-vector>
```

## nativecockpit/src/main/res/animator/splash_orbit.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<objectAnimator xmlns:android="http://schemas.android.com/apk/res/android"
    android:propertyName="rotation"
    android:valueFrom="0"
    android:valueTo="360"
    android:valueType="floatType"
    android:duration="1200"/>
```

## nativecockpit/src/main/res/values-tr/strings.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="media_video_tab">Video</string>
    <string name="media_radio_tab">Radyo</string>
    <string name="media_engine_open">Medya motorunu aç</string>
    <string name="media_engine_unavailable_title">Medya motoru açılamadı</string>
    <string name="media_engine_not_installed">Ayrı medya motoru uygulaması yüklü değil. Video sekmesinden dahili tarayıcıyı kullanabilirsiniz.</string>
    <string name="media_engine_blocked">Medya motoru yüklü ancak Android başlatılmasına izin vermedi veya açılış etkinliği bulunamadı. Uygulama izinlerini ve iş profili kısıtlamalarını kontrol edin.</string>
    <string name="radio_description">Türkiye radyo kanalları oy sayısına göre sıralanır. Yalnızca HTTPS yayınları listelenir. Bu ekran kapatılınca yayın durur.</string>
    <string name="radio_search">Radyo veya müzik türü ara</string>
    <string name="radio_results">%1$d / %2$d kanal</string>
    <string name="radio_selected">Seçilen: %1$s</string>
    <string name="radio_playback_error">Radyo yayını oynatılamadı. Tekrar deneyin veya başka kanal seçin.</string>
    <string name="radio_load_error">Radyo kanalları yüklenemedi. İnternet bağlantısını kontrol edin.</string>
    <string name="radio_empty">Uyumlu radyo kanalı bulunamadı.</string>
    <string name="radio_play">Oynat</string>
    <string name="radio_pause">Duraklat</string>
    <string name="radio_stop">Durdur</string>
    <string name="radio_refresh">Yenile</string>
    <string name="radio_votes">%1$d oy</string>
    <string name="radio_attribution">Radyo rehberi: Radio Browser. Yayın bağlantıları zamanla değişebilir.</string>
    <string name="media_external_not_installed">Uygulama cihazda yüklü değil. Resmî uygulamayı yükledikten sonra tekrar deneyin.</string>
    <string name="media_external_launch_blocked">Uygulama cihazda yüklü ancak Android tarafından açılamadı. İş profili, izinler veya devre dışı bırakılmış uygulama ayarlarını kontrol edin.</string>
    <string name="ux_welcome_description">Gizliliğiniz önemli. Yalnızca ihtiyaç duyduğunuz izinleri açın.</string>
    <string name="ux_location_title">Navigasyon için konum</string>
    <string name="ux_location_reason">Gerçek konumunuzu ve rota yönlendirmesini gösterir. GPS yoksa konum uydurulmaz.</string>
    <string name="ux_bluetooth_title">OBD adaptörü için Bluetooth</string>
    <string name="ux_bluetooth_reason">Eşleştirilmiş ELM327 adaptörüne yalnızca siz bağlantı başlattığınızda erişilir.</string>
    <string name="ux_notification_title">Bağlantı durumu bildirimleri</string>
    <string name="ux_notification_reason">Bağlı cihaz hizmetinin durum bildirimlerini gösterir. Gizli arka plan sesi açmaz.</string>
    <string name="ux_ready_title">Hazırsınız</string>
    <string name="ux_ready_reason">Kokpiti açın. Atladığınız izinleri ilgili özelliği kullanırken verebilirsiniz.</string>
    <string name="ux_permission_denied">İzin verilmedi. Atlayıp Android Ayarlarından daha sonra etkinleştirebilirsiniz.</string>
    <string name="ux_already_granted">İzin zaten etkin.</string>
    <string name="ux_open_cockpit">EV Cockpit uygulamasını aç</string>
    <string name="ux_continue">Devam et</string>
    <string name="ux_allow_permission">İzin ver</string>
    <string name="ux_skip_permission">Şimdilik atla</string>
    <string name="ux_finish_later">Kurulumu tamamlamadan devam et</string>
    <string name="ux_obd_unavailable">OBD cihazı bağlı değil</string>
    <string name="ux_obd_help">Batarya verisi için ELM327 adaptörünü takın. Bağlantı için dokunun.</string>
    <string name="ux_battery_unavailable">Batarya verisi alınamadı</string>
    <string name="ux_battery_help">OBD bağlantısı açık ancak geçerli batarya ölçümü yok. Tanılamayı kontrol edin.</string>
    <string name="ux_gps_waiting">GPS sinyali bekleniyor</string>
    <string name="ux_gps_help">Açık alana çıkın, Konum hizmetini açın ve yeniden deneyin.</string>
    <string name="ux_sound_unavailable">Motor sesi kullanılamıyor</string>
    <string name="ux_sound_help">Ses dosyası veya oynatıcı kullanılamıyor. Ses ayarlarına gitmek için dokunun.</string>
    <string name="ux_tts_missing">Sesli asistan için Türkçe dil paketi gerekli</string>
    <string name="ux_tts_help">Android Ayarları içinde Türkçe metin okuma sesini indirin ve tekrar deneyin.</string>
    <string name="ux_open_tts_settings">Ses ayarlarını aç</string>
    <string name="ux_tts_loading">Türkçe ses hazırlanıyor…</string>
    <string name="ux_tts_ready">Türkçe ses hazır</string>
    <string name="ux_tts_test">Türkçe sesi test et</string>
</resources>
```

## .github/workflows/release.yml

```yaml
name: EV Cockpit · Signed GitHub Release

on:
  workflow_dispatch:
    inputs:
      release_tag:
        description: "Release tag matching Android versionName (example v1.0.0)"
        required: true
        default: "v1.0.0"
        type: string
      confirm_device_tests:
        description: "I tested the owner-signed APK on a real phone and verified GPS, OBD, voice and launch."
        required: true
        default: false
        type: boolean

permissions:
  contents: write

concurrency:
  group: ev-cockpit-production-release
  cancel-in-progress: false

jobs:
  signed-public-release:
    if: github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    timeout-minutes: 65
    steps:
      - name: Require explicit device acceptance
        shell: bash
        env:
          ACCEPTED: "${{ inputs.confirm_device_tests }}"
          TAG: "${{ inputs.release_tag }}"
        run: |
          set -euo pipefail
          if [ "$ACCEPTED" != "true" ]; then
            echo "::error::Physical-device acceptance must be confirmed before publishing."
            exit 1
          fi
          if [[ ! "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
            echo "::error::Use a semantic release tag such as v1.0.0"
            exit 1
          fi
          echo "RELEASE_TAG=$TAG" >> "$GITHUB_ENV"

      - name: Checkout maintained production application
        uses: actions/checkout@v4
        with:
          ref: feature/ev-cockpit
          persist-credentials: true

      - name: Verify requested tag matches Android version
        shell: bash
        run: |
          set -euo pipefail
          version="$(printf '%s' "$RELEASE_TAG" | cut -c 2-)"
          grep -Fq "versionName = \"$version\"" nativecockpit/build.gradle.kts || {
            echo "::error::Tag does not match nativecockpit versionName"
            exit 1
          }
          if git ls-remote --tags origin "refs/tags/$RELEASE_TAG" | grep -q .; then
            echo "::error::Release tag already exists; never overwrite a published APK."
            exit 1
          fi
          echo "SOURCE_SHA=$(git rev-parse HEAD)" >> "$GITHUB_ENV"

      - name: Java 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Android SDK 36
        shell: bash
        run: |
          set -euo pipefail
          sdkmanager_bin="$(find "$ANDROID_HOME/cmdline-tools" -type f -path '*/bin/sdkmanager' -print -quit)"
          test -x "$sdkmanager_bin"
          "$sdkmanager_bin" "platforms;android-36" "build-tools;36.0.0"

      - name: Gradle 8.14.3
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "8.14.3"

      - name: Validate real hardware and product safety
        run: |
          set -euo pipefail
          python3 scripts/check_production_hardware.py
          python3 scripts/check_final_release_hardening.py
          python3 scripts/check_welcome_greeting.py
          gradle :nativecockpit:testDebugUnitTest --stacktrace --no-daemon

      - name: Restore existing owner signing identity
        shell: bash
        env:
          EYA_SIGNING_BUNDLE_BASE64: ${{ secrets.EYA_SIGNING_BUNDLE_BASE64 }}
        run: |
          set -euo pipefail
          if [ -z "$EYA_SIGNING_BUNDLE_BASE64" ]; then
            echo "::error::Missing EYA_SIGNING_BUNDLE_BASE64. Public release blocked."
            exit 1
          fi
          umask 077
          python3 - <<'PY'
          import base64, hashlib, json, os
          from pathlib import Path
          payload = json.loads(base64.b64decode(
              os.environ["EYA_SIGNING_BUNDLE_BASE64"], validate=True
          ).decode("utf-8"))
          if payload.get("schema") != "eya-signing-v1":
              raise ValueError("Unsupported signing bundle")
          data = base64.b64decode(payload["keystore_base64"], validate=True)
          if hashlib.sha256(data).hexdigest() != payload["keystore_sha256"]:
              raise ValueError("Owner keystore checksum mismatch")
          path = Path(os.environ["RUNNER_TEMP"]) / "ev-cockpit-release.jks"
          path.write_bytes(data)
          path.chmod(0o600)
          values = {
              "EYA_RELEASE_STORE_FILE": str(path),
              "EYA_RELEASE_STORE_PASSWORD": payload["store_password"],
              "EYA_RELEASE_KEY_ALIAS": payload["key_alias"],
              "EYA_RELEASE_KEY_PASSWORD": payload["key_password"]
          }
          for key, value in values.items():
              if not isinstance(value, str) or not value or "\n" in value or "\r" in value:
                  raise ValueError("Invalid signing property: " + key)
              if key != "EYA_RELEASE_STORE_FILE":
                  print("::add-mask::" + value)
          with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as fp:
              for key, value in values.items():
                  fp.write(key + "=" + value + "\n")
          PY

      - name: Build minified signed Release APK
        shell: bash
        run: |
          set -euo pipefail
          keytool -list -keystore "$EYA_RELEASE_STORE_FILE" \
            -storepass:env EYA_RELEASE_STORE_PASSWORD \
            -alias "$EYA_RELEASE_KEY_ALIAS" >/dev/null
          gradle :nativecockpit:assembleRelease --stacktrace --no-daemon

      - name: Verify APK signing certificate against owner identity
        shell: bash
        run: |
          set -euo pipefail
          apk="nativecockpit/build/outputs/apk/release/nativecockpit-release.apk"
          test -s "$apk"
          signer="$ANDROID_HOME/build-tools/36.0.0/apksigner"
          "$signer" verify --verbose "$apk"
          "$signer" verify --print-certs "$apk" > "$RUNNER_TEMP/apk-signature.txt"
          keytool -exportcert \
            -keystore "$EYA_RELEASE_STORE_FILE" \
            -storepass:env EYA_RELEASE_STORE_PASSWORD \
            -alias "$EYA_RELEASE_KEY_ALIAS" \
            -file "$RUNNER_TEMP/owner-cert.der" >/dev/null
          python3 - <<'PY'
          import hashlib, os, re
          from pathlib import Path
          tmp = Path(os.environ["RUNNER_TEMP"])
          message = (tmp / "apk-signature.txt").read_text(encoding="utf-8")
          found = re.search(
              r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F:]+)",
              message
          )
          if not found:
              raise SystemExit("APK signature fingerprint not found")
          actual = found.group(1).replace(":", "").lower()
          expected = hashlib.sha256((tmp / "owner-cert.der").read_bytes()).hexdigest()
          if actual != expected:
              raise SystemExit("The APK was not signed with the owner certificate")
          PY
          "$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging "$apk" |
            grep -q "package: name='com.evcockpit'"
          cp "$apk" "EV-Cockpit-$RELEASE_TAG.apk"
          sha256sum "EV-Cockpit-$RELEASE_TAG.apk" > "EV-Cockpit-$RELEASE_TAG.apk.sha256"

      - name: Publish verified APK on GitHub Releases
        shell: bash
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          set -euo pipefail
          gh release create "$RELEASE_TAG" \
            "EV-Cockpit-$RELEASE_TAG.apk" \
            "EV-Cockpit-$RELEASE_TAG.apk.sha256" \
            --repo "$GITHUB_REPOSITORY" \
            --target "$SOURCE_SHA" \
            --title "EV Cockpit $RELEASE_TAG" \
            --notes "Owner-signed Android release. Verify the SHA-256 checksum before installation. Real-device operation depends on GPS permissions, paired ELM327 hardware and supported speech services." \
            --latest

      - name: Erase signing material
        if: always()
        shell: bash
        run: |
          rm -f "$RUNNER_TEMP/ev-cockpit-release.jks" \
            "$RUNNER_TEMP/owner-cert.der" "$RUNNER_TEMP/apk-signature.txt"
```

