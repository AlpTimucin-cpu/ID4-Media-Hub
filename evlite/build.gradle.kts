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
        versionCode = 2
        versionName = "1.1-mola-tv-lite-beta"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release { isMinifyEnabled = false }
        debug {
            // Side-by-side beta prevents differing CI debug signing keys from
            // overwriting existing EV Lite and user charging records.
            applicationIdSuffix = ".tvbeta"
            isDebuggable = false
        }
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
