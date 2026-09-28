package com.openreel.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.openreel.app.player.PlayerScreen
import com.openreel.app.ui.screens.HomeScreen
import com.openreel.app.ui.screens.SearchScreen
import com.openreel.app.ui.screens.SettingsScreen
import com.openreel.app.ui.screens.TitleScreen

@Composable
fun OpenReelRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            NavHost(navController = nav, startDestination = "home", modifier = Modifier.fillMaxSize()) {
                composable("home") { HomeScreen(vm, nav) }
                composable("search") { SearchScreen(vm, nav) }
                composable("title") { TitleScreen(vm, nav) }
                composable("player") { PlayerScreen(vm, nav) }
                composable("settings") { SettingsScreen(vm, nav) }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}
