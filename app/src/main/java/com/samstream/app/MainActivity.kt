package com.samstream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.samstream.app.ui.AppViewModel
import com.samstream.app.ui.SamStreamRoot
import com.samstream.app.ui.theme.SamStreamTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SamStreamTheme { SamStreamRoot(vm) } }
    }
}
