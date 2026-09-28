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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.samstream.app.ui.components.GlassButton
import com.samstream.app.ui.components.GlassIcon
import com.samstream.app.ui.components.GoldButton
import com.samstream.app.ui.components.SectionHeader
import com.samstream.app.ui.theme.Gold
import com.samstream.app.ui.theme.Hairline
import com.samstream.app.ui.theme.Ink
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
    val keyCheck by vm.keyCheck.collectAsStateWithLifecycle()
    val ytKeyCheck by vm.ytKeyCheck.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tmdb by remember { mutableStateOf(s.tmdbKey) }
    var youtube by remember { mutableStateOf(s.youtubeKey) }
    var countryMenu by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            GlassIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", { nav.popBackStack() })
            Spacer(Modifier.width(14.dp))
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
        }

        SectionHeader("Your country")
        Text("Availability and free services differ by country.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { countryMenu = true }, border = BorderStroke(1.dp, Hairline), shape = RoundedCornerShape(50)) {
            Text("🌍  ${Locale("", s.country).displayCountry} (${s.country})", color = Color.White)
            DropdownMenu(expanded = countryMenu, onDismissRequest = { countryMenu = false }) {
                (listOf(s.country) + countries).distinct().forEach { c ->
                    DropdownMenuItem(text = { Text("${Locale("", c).displayCountry} ($c)") }, onClick = { countryMenu = false; vm.updateSettings { it.copy(country = c) } })
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SectionHeader("More sources (optional)")
        Text(
            "Internet Archive works without setup. Free API keys add posters, free services in your country (via TMDB/JustWatch) and Creative Commons / official YouTube films.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(tmdb, { tmdb = it }, label = { Text("TMDB API key or read token") }, singleLine = true,
            shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, focusedLabelColor = Gold, cursorColor = Gold, unfocusedBorderColor = Hairline),
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassButton("Test key", { vm.testTmdbKey(tmdb) }, height = 44.dp)
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.themoviedb.org/settings/api"))) }) { Text("Get a free TMDB key", color = Gold) }
        }
        keyCheck?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("✗")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(youtube, { youtube = it }, label = { Text("YouTube Data API key") }, singleLine = true,
            shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, focusedLabelColor = Gold, cursorColor = Gold, unfocusedBorderColor = Hairline),
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassButton("Test key", { vm.testYoutubeKey(youtube) }, height = 44.dp)
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.cloud.google.com/apis/library/youtube.googleapis.com"))) }) {
                Text("Get a YouTube API key", color = Gold)
            }
        }
        ytKeyCheck?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("✗")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GoldButton("Save keys", { vm.updateSettings { it.copy(tmdbKey = tmdb, youtubeKey = youtube) }; vm.refreshHome() }, icon = null, height = 48.dp)

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Only 1995 and newer", style = MaterialTheme.typography.titleMedium)
                Text("Hides older films and series everywhere in the app.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(colors = SwitchDefaults.colors(checkedTrackColor = Gold, checkedThumbColor = Color(0xFF1C1400)), checked = s.modernOnly,
                onCheckedChange = { v -> vm.updateSettings { it.copy(modernOnly = v) } })
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Show unverified uploads", style = MaterialTheme.typography.titleMedium)
                Text("Lists 🟡 sources whose rights are unclear so you can see why. They are never played.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(colors = SwitchDefaults.colors(checkedTrackColor = Gold, checkedThumbColor = Color(0xFF1C1400)), checked = s.showUnverified, onCheckedChange = { v -> vm.updateSettings { it.copy(showUnverified = v) } })
        }

        Spacer(Modifier.height(8.dp))
        SectionHeader("How SAM Stream decides what to play")
        listOf(
            "🟢 Licensed / authorized — official distributor channels and free services that license the title. Played in-app when the owner allows embedding; otherwise opened in their app.",
            "🟢 Public domain / open licence — films whose copyright has expired, or which the creator released under Creative Commons.",
            "🟡 Rights unclear — publicly visible but no proof of permission (for example a modern film re-uploaded by an unknown channel). Never played.",
            "🔴 Excluded — embedding disabled by the owner, or removed after a rights report.",
            "Publicly available does not mean allowed to embed: every play button names the permission it relies on.",
        ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Spacer(Modifier.height(8.dp))
        SectionHeader("Credits")
        Text(
            "Films streamed from the Internet Archive (archive.org) and YouTube's embedded player. " +
                "This product uses the TMDB API but is not endorsed or certified by TMDB. Watch-provider data by JustWatch. " +
                "SAM Stream does not host any video.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("SAM Stream 1.3", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}
