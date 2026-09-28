package com.samstream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.samstream.app.domain.RightsLevel
import com.samstream.app.domain.Title
import com.samstream.app.ui.theme.Amber
import com.samstream.app.ui.theme.Crimson
import com.samstream.app.ui.theme.Green

fun rightsColor(level: RightsLevel): Color = when (level) {
    RightsLevel.AUTHORIZED, RightsLevel.OPEN_LICENSE -> Green
    RightsLevel.UNVERIFIED -> Amber
    RightsLevel.BLOCKED -> Crimson
}

@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.18f), contentColor = color, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** Poster with a gradient-free fallback: the title on a tinted card. */
@Composable
fun Poster(url: String?, name: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Text(name, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, maxLines = 4,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        if (url != null) AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun TitleCard(title: Title, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(onClick = onClick)) {
        Box {
            Poster(title.posterUrl, title.name, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            if (title.inApp.isNotEmpty()) Badge("▶ FREE", Green, Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0xCC141018), RoundedCornerShape(6.dp)))
        }
        Text(title.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(
            listOfNotNull(title.year?.toString(), title.inApp.firstOrNull()?.source?.durationMinutes?.let { "$it min" }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One-line watch status for lists: "▶ Free in app", "Free on Tubi ↗", or why there's nothing. */
@Composable
fun WatchStatus(title: Title) {
    val inApp = title.inApp.firstOrNull()
    val ext = title.external.firstOrNull()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            inApp != null -> Badge("▶ Free in app", Green)
            ext != null -> Badge("Free on ${ext.source.providerName} ↗", Green)
            title.sources.isNotEmpty() -> Badge("🟡 Only unverified uploads", Amber)
            else -> Badge("No free legal source", MaterialTheme.colorScheme.onSurfaceVariant)
        }
        inApp?.let { Text(it.source.provider.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
