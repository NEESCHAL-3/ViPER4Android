package com.nees.audio.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nees.audio.service.ViperService
import com.nees.audio.ui.navigation.ViperNavigation
import com.nees.audio.ui.theme.ViperTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViperService.startService(this)
        enableEdgeToEdge()
        setContent {
            ViperTheme {
                ViperNavigation()
            }
        }
    }
}
