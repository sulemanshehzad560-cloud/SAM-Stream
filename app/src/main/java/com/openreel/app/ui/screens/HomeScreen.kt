package com.openreel.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.openreel.app.domain.Category
import com.openreel.app.ui.AppViewModel
import com.openreel.app.ui.Load
import com.openreel.app.ui.components.Poster
import com.openreel.app.ui.components.TitleCard

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavHostController) {
    val home by vm.home.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(3) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("OpenReel", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Outlined.Settings, "Settings") }
                }
                Text("What do you want to watch?", style = MaterialTheme.typography.headlineMedium)
                Surface(
                    onClick = { nav.navigate("search") },
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text("Search movies & series", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item(span = { GridItemSpan(3) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = category == null, onClick = { vm.selectCategory(null) }, label = { Text("🍿 Tonight") }) }
                items(Category.entries) { c ->
                    FilterChip(selected = category == c, onClick = { vm.selectCategory(c) }, label = { Text("${c.emoji} ${c.label}") })
                }
            }
        }

        if (recents.isNotEmpty() && category == null) {
            item(span = { GridItemSpan(3) }) {
                Column {
                    Text("Continue watching", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        items(recents, key = { it.sourceId }) { r ->
                            Column(Modifier.width(160.dp).clickable { vm.resume(r) { nav.navigate("player") } }) {
                                Poster(r.thumbnail, r.title, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                                if (r.durationMs > 0) LinearProgressIndicator(
                                    progress = { (r.positionMs.toFloat() / r.durationMs).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                                Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(3) }) {
            Column {
                Text(
                    if (category == null) "Tonight's free movies" else "${category!!.emoji} Free ${category!!.label.lowercase()}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("Free & legal · plays right here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when (val h = home) {
            is Load.Loading -> item(span = { GridItemSpan(3) }) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            is Load.Failed -> item(span = { GridItemSpan(3) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(h.message)
                    Button(onClick = { vm.refreshHome() }, modifier = Modifier.padding(top = 12.dp)) { Text("Try again") }
                }
            }
            is Load.Ready -> {
                if (h.value.isEmpty()) item(span = { GridItemSpan(3) }) {
                    Text(
                        "We haven't found free, verified titles in this category yet. As more licensed sources are added they'll appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
                items(h.value, key = { it.key }) { t ->
                    TitleCard(t, onClick = { vm.open(t); nav.navigate("title") })
                }
            }
        }
        item(span = { GridItemSpan(3) }) {
            Text(
                "OpenReel only plays titles whose owners allow it: public domain, open licences and official channels. Sources: Internet Archive, YouTube, TMDB.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal, modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}
