package com.samstream.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.samstream.app.ui.AppViewModel
import java.util.Locale

private val countries = listOf("AE", "SA", "QA", "KW", "BH", "OM", "EG", "JO", "IN", "PK", "GB", "US", "CA", "AU", "DE", "FR")

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavHostController) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tmdb by remember { mutableStateOf(s.tmdbKey) }
    var youtube by remember { mutableStateOf(s.youtubeKey) }
    var countryMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        Text("Your country", style = MaterialTheme.typography.titleMedium)
        Text("Availability and free services differ by country.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { countryMenu = true }) {
            Text("${Locale("", s.country).displayCountry} (${s.country})")
            DropdownMenu(expanded = countryMenu, onDismissRequest = { countryMenu = false }) {
                (listOf(s.country) + countries).distinct().forEach { c ->
                    DropdownMenuItem(text = { Text("${Locale("", c).displayCountry} ($c)") }, onClick = { countryMenu = false; vm.updateSettings { it.copy(country = c) } })
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("More sources (optional)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Internet Archive works without setup. Free API keys add posters, free services in your country (via TMDB/JustWatch) and Creative Commons / official YouTube films.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(tmdb, { tmdb = it }, label = { Text("TMDB API key or read token") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.themoviedb.org/settings/api"))) }) { Text("Get a free TMDB key") }
        OutlinedTextField(youtube, { youtube = it }, label = { Text("YouTube Data API key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.cloud.google.com/apis/library/youtube.googleapis.com"))) }) {
            Text("Get a YouTube API key")
        }
        OutlinedButton(onClick = { vm.updateSettings { it.copy(tmdbKey = tmdb, youtubeKey = youtube) }; vm.refreshHome() }) { Text("Save keys") }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Show unverified uploads", style = MaterialTheme.typography.titleMedium)
                Text("Lists 🟡 sources whose rights are unclear so you can see why. They are never played.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = s.showUnverified, onCheckedChange = { v -> vm.updateSettings { it.copy(showUnverified = v) } })
        }

        Spacer(Modifier.height(8.dp))
        Text("How SAM Stream decides what to play", style = MaterialTheme.typography.titleMedium)
        listOf(
            "🟢 Licensed / authorized — official distributor channels and free services that license the title. Played in-app when the owner allows embedding; otherwise opened in their app.",
            "🟢 Public domain / open licence — films whose copyright has expired, or which the creator released under Creative Commons.",
            "🟡 Rights unclear — publicly visible but no proof of permission (for example a modern film re-uploaded by an unknown channel). Never played.",
            "🔴 Excluded — embedding disabled by the owner, or removed after a rights report.",
            "Publicly available does not mean allowed to embed: every play button names the permission it relies on.",
        ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Spacer(Modifier.height(8.dp))
        Text("Credits", style = MaterialTheme.typography.titleMedium)
        Text(
            "Films streamed from the Internet Archive (archive.org) and YouTube's embedded player. " +
                "This product uses the TMDB API but is not endorsed or certified by TMDB. Watch-provider data by JustWatch. " +
                "SAM Stream does not host any video.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("SAM Stream 1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}
