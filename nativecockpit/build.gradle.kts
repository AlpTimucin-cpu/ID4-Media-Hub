import java.io.File
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
    namespace = "tr.id4.mediahub.nativecockpit"
    compileSdk = 36

    defaultConfig {
        applicationId = "tr.id4.mediahub.nativecockpit"
        minSdk = 28
        targetSdk = 35
        versionCode = 15
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
            // Remains the explicitly marked UAT/simulation variant.
            isMinifyEnabled = false
        }
        getByName("release") {
            isDebuggable = false
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
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("com.google.dagger:hilt-android:2.58")
    ksp("com.google.dagger:hilt-compiler:2.58")
    implementation("org.maplibre.gl:android-sdk:13.7.1")
    implementation("org.maplibre.gl:android-sdk-turf:6.0.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-projected:1.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
