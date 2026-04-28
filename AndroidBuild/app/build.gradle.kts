plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// AdMob credentials are injected at build time. Real IDs come from GitHub
// Secrets (ADMOB_APP_ID / ADMOB_BANNER_AD_UNIT_ID) and stay out of the repo.
// When either env var is missing or blank, the build falls back to Google's
// official test IDs so local and CI builds never serve real ads.
val ADMOB_TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
val ADMOB_TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

val resolvedAdmobAppId: String = System.getenv("ADMOB_APP_ID")
    ?.takeIf { it.isNotBlank() }
    ?: ADMOB_TEST_APP_ID
val resolvedAdmobBannerAdUnitId: String = System.getenv("ADMOB_BANNER_AD_UNIT_ID")
    ?.takeIf { it.isNotBlank() }
    ?: ADMOB_TEST_BANNER_AD_UNIT_ID
val admobMode: String = if (
    resolvedAdmobAppId == ADMOB_TEST_APP_ID ||
    resolvedAdmobBannerAdUnitId == ADMOB_TEST_BANNER_AD_UNIT_ID
) "TEST" else "PRODUCTION"
println("[Last Tile] AdMob mode: $admobMode (real IDs are never logged)")

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

        manifestPlaceholders["admobAppId"] = resolvedAdmobAppId
        buildConfigField(
            "String",
            "ADMOB_BANNER_AD_UNIT_ID",
            "\"$resolvedAdmobBannerAdUnitId\""
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation("com.google.android.gms:play-services-ads:23.6.0")
}
