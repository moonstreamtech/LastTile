package com.moonstreamtech.lasttile

import android.os.Bundle
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
        // consumes systemBars insets explicitly, keeping touch coordinates
        // and rendered tile positions aligned even when the gesture pill
        // shows or hides at runtime.
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
    }
}
