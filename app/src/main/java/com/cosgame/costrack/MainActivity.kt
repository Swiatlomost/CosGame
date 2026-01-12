package com.cosgame.costrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cosgame.costrack.ui.CosGameApp
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry point for CosGame app.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CosGameApp()
        }
    }
}
