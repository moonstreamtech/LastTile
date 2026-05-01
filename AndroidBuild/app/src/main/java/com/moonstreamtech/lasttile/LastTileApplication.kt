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
        // somehow ships with empty AdMob ids. Layers 1 (gradle), 3 (CI
        // pre-build) and 4 (post-build AAB scan) catch the same condition
        // earlier; this one is the last safety net.
        AdConfig.verifyReleaseIntegrity()

        // Safe with a placeholder APP_ID: the SDK initializes locally and
        // sign-in is deferred until a real client call. With the placeholder
        // present, online calls fail gracefully via the GpgsLeaderboard
        // wrapper instead of crashing the app.
        runCatching { PlayGamesSdk.initialize(this) }

        // AdMob init is fire-and-forget. If the device has no Play Services
        // (e.g. some Huawei devices) the SDK reports failure via the callback
        // and the bottom banner stays blank — same graceful behaviour as GPGS.
        runCatching {
            MobileAds.initialize(this) {
                Log.i("LastTileApp", "MobileAds initialization complete.")
            }
        }.onFailure { e ->
            Log.w("LastTileApp", "MobileAds.initialize threw", e)
        }
    }
}
