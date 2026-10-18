plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "tr.id4.mediahub"
    compileSdk = 36
    defaultConfig {
        applicationId = "tr.id4.evguide.lite"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0-ev-lite-beta"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release { isMinifyEnabled = false }
        debug {
            isDebuggable = false
        }
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
