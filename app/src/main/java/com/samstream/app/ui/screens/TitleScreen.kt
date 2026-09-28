package com.samstream.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.samstream.app.domain.MediaType
import com.samstream.app.domain.PlaybackKind
import com.samstream.app.domain.RightsLevel
import com.samstream.app.domain.Title
import com.samstream.app.domain.VerifiedSource
import com.samstream.app.ui.AppViewModel
import com.samstream.app.ui.components.Badge
import com.samstream.app.ui.components.Poster
import com.samstream.app.ui.components.rightsColor

@Composable
fun TitleScreen(vm: AppViewModel, nav: NavHostController) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val t = selected
    if (t == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            val hero = t.backdropUrl ?: t.posterUrl
            if (hero != null) AsyncImage(hero, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66141018), Color(0xFF141018)))))
            IconButton(
                onClick = { nav.popBackStack() },
                modifier = Modifier.statusBarsPadding().padding(8.dp).background(Color(0x88000000), CircleShape),
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.Bottom) {
                Poster(t.posterUrl, t.name, Modifier.width(96.dp).height(144.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(t.name, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        listOfNotNull(t.year?.toString(), if (t.mediaType == MediaType.SERIES) "Series" else "Movie", t.genres.take(2).joinToString(", ").ifBlank { null })
                            .joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            val best = t.inApp.firstOrNull()
            if (best != null) {
                Button(
                    onClick = { vm.play(best, t.name) { nav.navigate("player") } },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Watch free", fontWeight = FontWeight.Bold)
                }
                Text("Source: ${best.source.providerName}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            } else if (t.external.isEmpty()) {
                NoSource(t)
            }

            t.overview?.let { Text(it, modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium) }

            if (t.sources.isNotEmpty()) {
                Text("Where to watch", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
                t.sources.forEach { vs -> SourceCard(vs, onPlay = { vm.play(vs, t.name) { nav.navigate("player") } }, context = context) }
            }

            Text(
                "Public-domain status is determined under US law and can differ in other countries. " +
                    "If you own the rights to something shown here and want it removed, tell us and it will be taken down.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp),
            )
            TextButton(onClick = { report(context, t) }) { Text("Report a rights problem") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NoSource(t: Title) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("No free, legal source right now", style = MaterialTheme.typography.titleMedium)
            Text(
                if (t.sources.isEmpty()) "We couldn't find ${t.name} from a source that allows free viewing in your country."
                else "The uploads we found don't show permission from the rights holder, so SAM Stream won't play them.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SourceCard(vs: VerifiedSource, onPlay: () -> Unit, context: Context) {
    val s = vs.source
    val v = vs.verdict
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.providerName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(s.description?.takeIf { s.playback == PlaybackKind.EXTERNAL_APP }, s.durationMinutes?.let { "$it min" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Badge("${v.level.emoji} ${v.level.label}", rightsColor(v.level))
            }
            Text(v.reason, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    v.playableInApp -> Button(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, null); Text(" Watch free") }
                    vs.canWatch && s.playback == PlaybackKind.EXTERNAL_APP -> Button(onClick = { open(context, s.pageUrl) }) {
                        Text("Open ${s.providerName} "); Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                    }
                    v.level == RightsLevel.UNVERIFIED -> Text("Not played until the rights are verified.", style = MaterialTheme.typography.bodySmall,
                        color = rightsColor(v.level))
                    else -> Unit
                }
                if (s.playback != PlaybackKind.EXTERNAL_APP) OutlinedButton(onClick = { open(context, s.pageUrl) }) { Text("Source page") }
            }
        }
    }
}

private fun open(context: Context, url: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: ActivityNotFoundException) { }
}

private fun report(context: Context, t: Title) {
    val body = "**Title:** ${t.name} (${t.year ?: "?"})\n\n**Sources:**\n" +
        t.sources.joinToString("\n") { "- `${it.source.id}` ${it.source.pageUrl}" } +
        "\n\n**What's the problem?** (e.g. I am the rights holder and did not authorise this)\n"
    val url = "https://github.com/${com.samstream.app.BuildConfig.REPO_SLUG}/issues/new?labels=rights-report&title=" +
        Uri.encode("Rights report: ${t.name}") + "&body=" + Uri.encode(body)
    open(context, url)
}
