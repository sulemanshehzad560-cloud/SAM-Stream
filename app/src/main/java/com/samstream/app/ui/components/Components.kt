package com.samstream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.samstream.app.domain.RightsLevel
import com.samstream.app.domain.Title
import com.samstream.app.ui.theme.Amber
import com.samstream.app.ui.theme.BottomScrim
import com.samstream.app.ui.theme.Crimson
import com.samstream.app.ui.theme.Glass
import com.samstream.app.ui.theme.Gold
import com.samstream.app.ui.theme.GoldDeep
import com.samstream.app.ui.theme.GoldGradient
import com.samstream.app.ui.theme.Green
import com.samstream.app.ui.theme.Hairline
import com.samstream.app.ui.theme.InkHigh
import com.samstream.app.ui.theme.InkRaised

fun rightsColor(level: RightsLevel): Color = when (level) {
    RightsLevel.AUTHORIZED, RightsLevel.OPEN_LICENSE -> Green
    RightsLevel.UNVERIFIED -> Amber
    RightsLevel.BLOCKED -> Crimson
}

@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.16f), contentColor = color, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** The SAM Stream wordmark: a gold play tile next to "SAM STREAM". */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(GoldGradient), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFF1C1400), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("SAM", color = Gold, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.sp)
        Text(" STREAM", color = Color.White, fontWeight = FontWeight.Light, fontSize = 20.sp, letterSpacing = 3.sp)
    }
}

/** Main call to action: gold gradient pill. */
@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = Icons.Filled.PlayArrow, height: Dp = 52.dp) {
    Box(
        modifier
            .height(height)
            .shadow(14.dp, RoundedCornerShape(50), ambientColor = GoldDeep, spotColor = GoldDeep)
            .clip(RoundedCornerShape(50))
            .background(GoldGradient)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Color(0xFF1C1400)); Spacer(Modifier.width(6.dp)) }
            Text(text, color = Color(0xFF1C1400), style = MaterialTheme.typography.labelLarge, fontSize = 15.sp)
        }
    }
}

/** Secondary action: frosted glass pill. */
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, height: Dp = 52.dp) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Glass)
            .border(1.dp, Hairline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontSize = 15.sp)
            if (icon != null) { Spacer(Modifier.width(6.dp)); Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

/** Selectable pill used for categories and quick searches. */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .then(if (selected) Modifier.background(GoldGradient) else Modifier.background(InkHigh).border(1.dp, Hairline, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color(0xFF1C1400) else Color.White)
    }
}

/** Section title with a small gold accent bar and optional subtitle / trailing action. */
@Composable
fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(if (subtitle != null) 36.dp else 20.dp).clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(Gold, GoldDeep))))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing()
    }
}

/** Poster with a styled fallback: the title on a gradient card. */
@Composable
fun Poster(url: String?, name: String, modifier: Modifier = Modifier, corner: Dp = 14.dp) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(InkHigh, InkRaised, Color(0xFF2B1F10))))
            .border(1.dp, Hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall, maxLines = 4,
            color = Gold.copy(alpha = 0.85f), modifier = Modifier.padding(10.dp))
        if (url != null) AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** Poster card for grids and rows. */
@Composable
fun TitleCard(title: Title, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).shadow(10.dp, RoundedCornerShape(14.dp))) {
            Poster(title.posterUrl, title.name, Modifier.fillMaxSize())
            Box(Modifier.fillMaxWidth().height(56.dp).align(Alignment.BottomCenter).clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)).background(BottomScrim))
            when {
                title.inApp.isNotEmpty() -> FreeTag("FREE", Modifier.align(Alignment.TopStart).padding(7.dp))
                title.external.isNotEmpty() -> FreeTag("FREE ↗", Modifier.align(Alignment.TopStart).padding(7.dp))
            }
            title.inApp.firstOrNull()?.source?.durationMinutes?.let {
                Text("$it min", style = MaterialTheme.typography.labelSmall, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }
        Text(title.name, style = MaterialTheme.typography.titleSmall, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(
            listOfNotNull(title.year?.toString(), title.genres.firstOrNull()?.replaceFirstChar { it.uppercase() }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun FreeTag(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(GoldGradient).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color(0xFF1C1400))
    }
}

/** Round frosted icon button, for use over images. */
@Composable
fun GlassIcon(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(42.dp).clip(CircleShape).background(Color(0x66000000)).border(1.dp, Hairline, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(22.dp)) }
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
            title.sources.isNotEmpty() -> Badge("Only unverified uploads", Amber)
            else -> Badge("No free legal source", MaterialTheme.colorScheme.onSurfaceVariant)
        }
        inApp?.let { Text(it.source.provider.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
