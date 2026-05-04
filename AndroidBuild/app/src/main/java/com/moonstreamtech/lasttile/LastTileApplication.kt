package com.moonstreamtech.lasttile

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.games.PlayGamesSdk

class LastTileApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Layer 2 of the test-id security stack. Crashes the process
        // before MobileAds.initialize is ever invoked if a release build
        // somehow ships with empty or test-prefixed AdMob ids. Layers 1
        // (gradle), 3 (CI pre-build) and 4 (post-build AAB scan) catch
        // the same condition earlier; this one is the last safety net.
        AdConfig.verifyReleaseIntegrity()

        // Eager-preload a rewarded ad so the Shield "earn" dialog has
        // something to play instantly when the player taps. Survives
        // the whole Application lifecycle and re-loads itself after
        // each show.
        runCatching { RewardedAdManager.init(this) }
            .onFailure { e -> Log.w("LastTileApp", "RewardedAdManager.init threw", e) }

        // Safe with a placeholder APP_ID: the SDK initializes locally and
        // sign-in is deferred until a real client call. With the placeholder
        // present, online calls fail gracefully via the GpgsLeaderboard
        // wrapper instead of crashing the app. Sign-in itself is never
        // triggered here — GpgsLeaderboard performs the isAuthenticated /
        // signIn handshake on demand from user-driven actions.
        runCatching { PlayGamesSdk.initialize(this) }
            .onSuccess { Log.i("LastTileApp", "PlayGamesSdk.initialize success") }
            .onFailure { e -> Log.w("LastTileApp", "PlayGamesSdk.initialize failed", e) }

        // AdMob init is fire-and-forget. If the device has no Play Services
        // (e.g. some Huawei devices) the SDK reports failure via the callback
        // and the bottom banner stays blank — same graceful behaviour as GPGS.
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
