package com.samstream.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
import com.samstream.app.ui.components.FreeTag
import com.samstream.app.ui.components.GlassButton
import com.samstream.app.ui.components.GlassIcon
import com.samstream.app.ui.components.GoldButton
import com.samstream.app.ui.components.Poster
import com.samstream.app.ui.components.SectionHeader
import com.samstream.app.ui.components.rightsColor
import com.samstream.app.ui.theme.Gold
import com.samstream.app.ui.theme.Hairline
import com.samstream.app.ui.theme.Ink
import com.samstream.app.ui.theme.InkHigh
import com.samstream.app.ui.theme.InkRaised

@Composable
fun TitleScreen(vm: AppViewModel, nav: NavHostController) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val t = selected
    if (t == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ---------- hero ----------
            Box(Modifier.fillMaxWidth().height(440.dp)) {
                val hero = t.backdropUrl ?: t.posterUrl
                if (hero != null) AsyncImage(hero, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x66000000), 0.35f to Color(0x22000000), 0.75f to Ink.copy(alpha = 0.85f), 1f to Ink)))
                Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
                    Poster(t.posterUrl, t.name, Modifier.width(112.dp).height(168.dp).shadow(18.dp, RoundedCornerShape(14.dp)))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.padding(bottom = 4.dp)) {
                        if (t.inApp.isNotEmpty() || t.external.isNotEmpty()) FreeTag(if (t.inApp.isNotEmpty()) "FREE · PLAYS HERE" else "FREE ON STREAMING")
                        Text(t.name, style = MaterialTheme.typography.headlineLarge, color = Color.White, maxLines = 3,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            listOfNotNull(
                                t.year?.toString(),
                                if (t.mediaType == MediaType.SERIES) "Series" else "Movie",
                                t.inApp.firstOrNull()?.source?.durationMinutes?.let { "$it min" },
                            ).joinToString("  •  "),
                            style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE6E0EE), modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 20.dp)) {
                if (t.genres.isNotEmpty()) {
                    Row(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        t.genres.take(4).forEach { g ->
                            Text(
                                g.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = Color.White,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(InkHigh).border(1.dp, Hairline, RoundedCornerShape(50))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                val best = t.inApp.firstOrNull()
                val ext = t.external.firstOrNull()
                Spacer(Modifier.height(20.dp))
                when {
                    best != null -> {
                        GoldButton("Watch free", { vm.play(best, t.name) { nav.navigate("player") } }, Modifier.fillMaxWidth(), height = 56.dp)
                        Text("Streams from ${best.source.providerName} · ${best.verdict.level.label}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally))
                    }
                    ext != null -> GoldButton("Watch free on ${ext.source.providerName}", { open(context, ext.source.pageUrl) }, Modifier.fillMaxWidth(),
                        icon = Icons.AutoMirrored.Outlined.OpenInNew, height = 56.dp)
                    else -> NoSource(t)
                }

                t.overview?.let {
                    Text("Story", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 26.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFD9D2E3), modifier = Modifier.padding(top = 6.dp))
                }

                if (t.sources.isNotEmpty()) {
                    SectionHeader("Where to watch", "Every source is rights-checked", Modifier.padding(top = 30.dp, bottom = 10.dp))
                    t.sources.forEach { vs -> SourceCard(vs, onPlay = { vm.play(vs, t.name) { nav.navigate("player") } }, context = context) }
                }

                Text(
                    "Public-domain status is determined under US law and can differ in other countries. " +
                        "If you own the rights to something shown here and want it removed, tell us and it will be taken down.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp),
                )
                TextButton(onClick = { report(context, t) }) { Text("Report a rights problem", color = Gold) }
                Spacer(Modifier.height(24.dp).navigationBarsPadding())
            }
        }

        GlassIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", { nav.popBackStack() }, Modifier.statusBarsPadding().padding(12.dp))
    }
}

@Composable
private fun NoSource(t: Title) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(InkRaised).border(1.dp, Hairline, RoundedCornerShape(18.dp)).padding(18.dp),
    ) {
        Text("No free, legal source right now", style = MaterialTheme.typography.titleMedium)
        Text(
            if (t.sources.isEmpty()) "We couldn't find ${t.name} from a source that allows free viewing in your country."
            else "The uploads we found don't show permission from the rights holder, so SAM Stream won't play them.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SourceCard(vs: VerifiedSource, onPlay: () -> Unit, context: Context) {
    val s = vs.source
    val v = vs.verdict
    val accent = rightsColor(v.level)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(InkRaised)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp)).height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.providerName, style = MaterialTheme.typography.titleMedium)
                    val meta = listOfNotNull(s.description?.takeIf { s.playback == PlaybackKind.EXTERNAL_APP }, s.durationMinutes?.let { "$it min" }).joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Badge("${v.level.emoji} ${v.level.label}", accent)
            }
            Text(v.reason, style = MaterialTheme.typography.bodySmall, color = Color(0xFFCFC8D9), modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    v.playableInApp -> GoldButton("Watch free", onPlay, height = 44.dp)
                    vs.canWatch && s.playback == PlaybackKind.EXTERNAL_APP ->
                        GoldButton("Open ${s.providerName}", { open(context, s.pageUrl) }, icon = Icons.AutoMirrored.Outlined.OpenInNew, height = 44.dp)
                    v.level == RightsLevel.UNVERIFIED -> Text("Not played until the rights are verified.", style = MaterialTheme.typography.bodySmall, color = accent)
                    else -> Unit
                }
                if (s.playback != PlaybackKind.EXTERNAL_APP) GlassButton("Source", { open(context, s.pageUrl) }, height = 44.dp)
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
