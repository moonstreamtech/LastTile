package com.moonstreamtech.lasttile

import android.app.Application
import com.google.android.gms.games.PlayGamesSdk

class LastTileApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Safe with a placeholder APP_ID: the SDK initializes locally and
        // sign-in is deferred until a real client call. With the placeholder
        // present, online calls fail gracefully via the GpgsLeaderboard
        // wrapper instead of crashing the app.
        runCatching { PlayGamesSdk.initialize(this) }
    }
}
