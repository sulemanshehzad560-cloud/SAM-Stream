package com.openreel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.openreel.app.ui.AppViewModel
import com.openreel.app.ui.OpenReelRoot
import com.openreel.app.ui.theme.OpenReelTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OpenReelTheme { OpenReelRoot(vm) } }
    }
}
