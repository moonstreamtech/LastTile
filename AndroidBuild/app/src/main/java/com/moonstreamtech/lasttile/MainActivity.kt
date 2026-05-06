package com.moonstreamtech.lasttile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.moonstreamtech.lasttile.ui.GameScreen
import com.moonstreamtech.lasttile.ui.LastTileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Run edge-to-edge so the window has a single deterministic mode
        // across stock Android and OEM skins (Huawei, Xiaomi, etc. that
        // sometimes mis-report system bar insets). The Compose root then
        // consumes safeDrawing insets explicitly, keeping touch coordinates
        // and rendered tile positions aligned even when the gesture pill
        // shows or hides at runtime.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        Log.d(
            "LastTile-window",
            "edge-to-edge enabled: decorFitsSystemWindows=false; controller=" +
                WindowCompat.getInsetsController(window, window.decorView)
        )
        setContent {
            LastTileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GameScreen()
                }
            }
        }

        // v0.1.11: Probe Play Store for an in-app update and register
        // the activity-result launcher that drives the FLEXIBLE
        // update flow. Must run during onCreate (before the activity
        // reaches STARTED) so registerForActivityResult is allowed.
        // Devices without Play Services fail the probe silently and
        // never surface the update badge.
        UpdateChecker.init(this)
    }
}
