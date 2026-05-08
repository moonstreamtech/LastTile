package com.moonstreamtech.lasttile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.moonstreamtech.lasttile.ui.GameScreen
import com.moonstreamtech.lasttile.ui.LastTileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // enableEdgeToEdge() draws the window behind the system bars and
        // installs the Android 15-aware default inset behaviour. The
        // Compose root then consumes WindowInsets.safeDrawing explicitly
        // so touch coordinates and rendered tile positions stay aligned
        // across stock Android and OEM skins (Huawei, Xiaomi, OnePlus
        // — which historically misreported inset metrics under the
        // older setDecorFitsSystemWindows call). Must run BEFORE
        // super.onCreate so the activity attaches to a window that is
        // already in edge-to-edge mode.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Log.d(
            "LastTile-window",
            "edge-to-edge enabled via enableEdgeToEdge(); controller=" +
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
