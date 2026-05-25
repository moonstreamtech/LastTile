package com.moonstreamtech.lasttile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
            // Lock the entire app to fontScale = 1.0 so the system
            // "Display size & text" accessibility slider can't enlarge
            // text inside the virtual-canvas game layer. The layer is
            // drawn at a fixed design dp and scaled with a single
            // graphicsLayer transform; if sp values still listened to
            // system font scale, large fonts would push board cells
            // and stat labels outside the design grid and break the
            // reference composition. The density.density value
            // (pixel density) is preserved — only fontScale is pinned.
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density,
                    fontScale = 1.0f
                )
            ) {
                LastTileTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        GameScreen()
                    }
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
