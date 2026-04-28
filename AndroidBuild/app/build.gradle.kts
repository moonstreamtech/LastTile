plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.moonstreamtech.lasttile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.moonstreamtech.lasttile"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // Lock the APK to the locales we ship translations for. Android still
        // auto-picks the closest match for the device language at runtime, so
        // a Spanish phone gets values-es, a Brazilian phone gets values-pt-rBR,
        // and anything outside the list falls back to the default English
        // resources in `values/`.
        resourceConfigurations += listOf(
            "en",
            "tr",
            "es",
            "fr",
            "de",
            "it",
            "pt-rBR",
            "ru",
            "ja",
            "ko",
            "zh-rCN",
            "zh-rTW",
            "ar",
            "hi",
            "in",
            "vi",
            "th",
            "pl",
            "nl",
            "uk"
        )
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        // DEBUG keystore only — uses well-known Android default credentials.
        // Safe to commit. Release keystore lives outside the repo and is
        // injected via GitHub Secrets at build time.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.gms:play-services-games-v2:20.1.2")
    implementation("com.google.android.gms:play-services-tasks:18.0.2")
}
