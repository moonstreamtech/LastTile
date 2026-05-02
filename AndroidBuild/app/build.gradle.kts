import java.io.File
import java.io.FileInputStream
import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Google's test AdMob ids — used as fallbacks for DEBUG builds when
// env secrets are absent. They live ONLY in this .kts file (which is
// not packaged into the APK/AAB) and in BuildConfig fields generated
// for the DEBUG variant. Release variants never embed these strings,
// so the post-build AAB content scan (Layer 4) cannot trip on them.
val ADMOB_TEST_PUBLISHER_PREFIX = "ca-app-pub-3940256099942544"
val ADMOB_TEST_APP_ID = "$ADMOB_TEST_PUBLISHER_PREFIX~3347511713"
val ADMOB_TEST_BANNER_UNIT_ID = "$ADMOB_TEST_PUBLISHER_PREFIX/6300978111"
val ADMOB_TEST_REWARDED_UNIT_ID = "$ADMOB_TEST_PUBLISHER_PREFIX/5224354917"

// Real ids come exclusively from GitHub Secrets at build time.
val envAdmobAppId: String = (System.getenv("ADMOB_APP_ID") ?: "").trim()
val envAdmobBannerUnitId: String = (System.getenv("ADMOB_BANNER_UNIT_ID") ?: "").trim()
val envAdmobRewardedUnitId: String = (System.getenv("ADMOB_REWARDED_UNIT_ID") ?: "").trim()

// Diagnostic only — never logs the values themselves.
val admobMode = if (
    envAdmobAppId.isNotBlank() &&
    envAdmobBannerUnitId.isNotBlank() &&
    envAdmobRewardedUnitId.isNotBlank()
) "PRODUCTION" else "TEST"
println("[Last Tile] AdMob mode: $admobMode (real ids never logged)")

// LAYER 1 — build-time guard against shipping test ids. Inspects the
// task graph; if any release task is queued and any of the three real
// id env vars is blank or contains the test publisher prefix, the
// build aborts before the AAB/APK is produced.
gradle.taskGraph.whenReady {
    val releaseTaskSuffixes = listOf(
        ":app:bundleRelease",
        ":app:assembleRelease"
    )
    val isReleaseBuild = allTasks.any { task ->
        releaseTaskSuffixes.any { suffix -> task.path.endsWith(suffix) }
    }
    if (!isReleaseBuild) return@whenReady
    val checks = linkedMapOf(
        "ADMOB_APP_ID" to envAdmobAppId,
        "ADMOB_BANNER_UNIT_ID" to envAdmobBannerUnitId,
        "ADMOB_REWARDED_UNIT_ID" to envAdmobRewardedUnitId
    )
    checks.forEach { (name, value) ->
        if (value.isBlank()) {
            throw GradleException(
                "BUILD ABORTED: $name is empty in release build. " +
                    "Configure GitHub Secrets before running bundleRelease."
            )
        }
        if (value.contains(ADMOB_TEST_PUBLISHER_PREFIX)) {
            throw GradleException(
                "BUILD ABORTED: $name contains the test AdMob publisher prefix. " +
                    "Real AdMob ids are required for release builds."
            )
        }
    }
}

android {
    namespace = "com.moonstreamtech.lasttile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.moonstreamtech.lasttile"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"

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
        // RELEASE upload key. CI decodes a base64 keystore from the
        // RELEASE_KEYSTORE_BASE64 secret into a transient file under the
        // root project dir (gitignored). When the env var is missing
        // (every local dev build) we leave storeFile unset and the
        // release build silently falls back to "no signing" — bundleRelease
        // still produces an unsigned AAB instead of failing.
        create("release") {
            val keystoreBase64 = System.getenv("RELEASE_KEYSTORE_BASE64")
            val keystoreFile = File(rootProject.projectDir, "release.keystore")
            if (!keystoreBase64.isNullOrBlank()) {
                val bytes = Base64.getDecoder().decode(keystoreBase64)
                keystoreFile.writeBytes(bytes)
            }
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            // Debug variant: substitute test ids whenever the env vars
            // are absent so a local dev or CI debug build always has
            // working ad placeholders. The TEST_* literals appear ONLY
            // here — they never end up in release-variant artifacts.
            val debugAppId = envAdmobAppId.ifBlank { ADMOB_TEST_APP_ID }
            val debugBannerId = envAdmobBannerUnitId.ifBlank { ADMOB_TEST_BANNER_UNIT_ID }
            val debugRewardedId = envAdmobRewardedUnitId.ifBlank { ADMOB_TEST_REWARDED_UNIT_ID }
            manifestPlaceholders["admobAppId"] = debugAppId
            buildConfigField("String", "ADMOB_APP_ID", "\"$debugAppId\"")
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$debugBannerId\"")
            buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"$debugRewardedId\"")
        }
        getByName("release") {
            // Only attach the release signing config when the keystore was
            // actually materialised (i.e. CI with secrets present). Local
            // dev builds run without secrets and fall back to an unsigned
            // AAB rather than failing the build.
            signingConfig = signingConfigs.findByName("release")
                ?.takeIf { it.storeFile?.exists() == true }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release variant: env values only. The Layer 1 task-graph
            // guard above guarantees these are real ids when the
            // release task actually runs; the ifBlank for the manifest
            // placeholder only matters when the build aborts mid-flight
            // (it never produces a real release artifact).
            manifestPlaceholders["admobAppId"] = envAdmobAppId.ifBlank { ADMOB_TEST_APP_ID }
            buildConfigField("String", "ADMOB_APP_ID", "\"$envAdmobAppId\"")
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$envAdmobBannerUnitId\"")
            buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"$envAdmobRewardedUnitId\"")
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
