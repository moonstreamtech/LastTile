package com.moonstreamtech.lasttile

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds

class LastTileApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Layer 2 of the test-id security stack. Crashes the process
        // before MobileAds.initialize is ever invoked if a release build
        // somehow ships with empty or test-prefixed AdMob ids. Layers 1
        // (gradle), 3 (CI pre-build) and 4 (post-build AAB scan) catch
        // the same condition earlier; this one is the last safety net.
        AdConfig.verifyReleaseIntegrity()

        // v0.2.0: One-time promotion of the legacy tutorial_v1_seen flag
        // to tutorial_completed_once so existing players who finished
        // the v0.1.x tutorial aren't forced through the new mandatory
        // step 6 username flow. Cheap and idempotent.
        runCatching {
            val prefs = getSharedPreferences("lasttile_state", Context.MODE_PRIVATE)
            TutorialController.migrateLegacyTutorialFlag(prefs)
        }.onFailure { e ->
            Log.w("LastTileApp", "tutorial flag migration failed", e)
        }

        // Eager-preload a rewarded ad so the Shield "earn" dialog has
        // something to play instantly when the player taps. Survives
        // the whole Application lifecycle and re-loads itself after
        // each show.
        runCatching { RewardedAdManager.init(this) }
            .onFailure { e -> Log.w("LastTileApp", "RewardedAdManager.init threw", e) }

        // Firebase Anonymous Auth + Firestore bootstrap.
        // Runs in a background coroutine inside UserBootstrap; any failure
        // (no network, invalid google-services.json stub) is caught and
        // sets AuthState.Offline so the game works fully offline.
        runCatching { UserBootstrap.init(this) }
            .onFailure { e -> Log.w("LastTileApp", "UserBootstrap.init threw", e) }

        // AdMob init is fire-and-forget. If the device has no Play Services
        // (e.g. some Huawei devices) the SDK reports failure via the callback
        // and the bottom banner stays blank — leaderboard and gameplay are
        // unaffected because Firebase Anonymous Auth works without GPGS.
        val app = this
        runCatching {
            MobileAds.initialize(app) {
                Log.i("LastTileApp", "MobileAds initialization complete.")
                runCatching { RewardedAdManager.preload(app) }
            }
        }.onFailure { e ->
            Log.w("LastTileApp", "MobileAds.initialize threw", e)
        }
    }
}
