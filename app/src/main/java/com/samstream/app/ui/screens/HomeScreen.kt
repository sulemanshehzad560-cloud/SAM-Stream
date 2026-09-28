package com.samstream.app.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.samstream.app.domain.Category
import com.samstream.app.domain.MediaType
import com.samstream.app.domain.Title
import com.samstream.app.ui.AppViewModel
import com.samstream.app.ui.Load
import com.samstream.app.ui.components.BrandMark
import com.samstream.app.ui.components.FreeTag
import com.samstream.app.ui.components.GlassButton
import com.samstream.app.ui.components.GlassIcon
import com.samstream.app.ui.components.GoldButton
import com.samstream.app.ui.components.Pill
import com.samstream.app.ui.components.Poster
import com.samstream.app.ui.components.SectionHeader
import com.samstream.app.ui.components.TitleCard
import com.samstream.app.ui.theme.BottomScrim
import com.samstream.app.ui.theme.Gold
import com.samstream.app.ui.theme.Hairline
import com.samstream.app.ui.theme.Ink
import com.samstream.app.ui.theme.InkHigh
import com.samstream.app.ui.theme.TopScrim
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavHostController) {
    val home by vm.home.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val services by vm.services.collectAsStateWithLifecycle()
    val tmdbProblem by vm.tmdbProblem.collectAsStateWithLifecycle()
    val youtubeProblem by vm.youtubeProblem.collectAsStateWithLifecycle()
    val keyProblem = listOfNotNull(tmdbProblem, youtubeProblem).joinToString("\n\n").ifBlank { null }
    val settings by vm.settings.collectAsStateWithLifecycle()

    val titles = (home as? Load.Ready)?.value.orEmpty()
    val heroes = titles.filter { it.posterUrl != null || it.backdropUrl != null }.take(5)
    val openTitle: (Title) -> Unit = { vm.open(it); nav.navigate("title") }

    Box(Modifier.fillMaxSize().background(Ink)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            // ---------- hero ----------
            item(key = "hero") {
                if (heroes.isNotEmpty()) HeroPager(heroes, onOpen = openTitle, onPlay = { t ->
                    t.inApp.firstOrNull()?.let { vm.play(it, t.name) { nav.navigate("player") } } ?: openTitle(t)
                })
                else Box(Modifier.fillMaxWidth().height(170.dp).background(Brush.verticalGradient(listOf(Color(0xFF2B1F10), Ink))))
            }

            // ---------- search ----------
            item(key = "search") {
                Row(
                    Modifier.padding(horizontal = 16.dp).padding(top = 18.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(50)).background(InkHigh).border(1.dp, Hairline, RoundedCornerShape(50))
                        .clickable { nav.navigate("search") }.padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, null, tint = Gold)
                    Spacer(Modifier.width(12.dp))
                    Text("Search free movies & series", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ---------- categories ----------
            item(key = "chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    item { Pill("🍿 Tonight", category == null, { vm.selectCategory(null) }) }
                    items(Category.entries) { c -> Pill("${c.emoji} ${c.label}", category == c, { vm.selectCategory(c) }) }
                }
            }

            if (keyProblem != null) item(key = "keyProblem") {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.errorContainer).clickable { nav.navigate("settings") }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(10.dp))
                    Text(keyProblem, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }

            // ---------- continue watching ----------
            if (recents.isNotEmpty() && category == null) item(key = "recents") {
                Column(Modifier.padding(top = 26.dp)) {
                    SectionHeader("Continue watching", modifier = Modifier.padding(horizontal = 16.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                        items(recents, key = { it.sourceId }) { r ->
                            Column(Modifier.width(200.dp).clickable { vm.resume(r) { nav.navigate("player") } }) {
                                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                                    Poster(r.thumbnail, r.title, Modifier.fillMaxSize(), corner = 12.dp)
                                    Box(Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
                                    }
                                }
                                if (r.durationMs > 0) LinearProgressIndicator(
                                    progress = { (r.positionMs.toFloat() / r.durationMs).coerceIn(0f, 1f) },
                                    color = Gold, trackColor = InkHigh,
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                                )
                                Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }

            // ---------- free on streaming apps (TMDB) ----------
            if (category == null && services.isNotEmpty()) item(key = "services") {
                Column(Modifier.padding(top = 26.dp)) {
                    SectionHeader(
                        "Free on streaming apps",
                        "Licensed free or with ads in ${Locale("", settings.country).displayCountry} · opens in their app",
                        Modifier.padding(horizontal = 16.dp),
                    )
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                        items(services, key = { it.key }) { t -> TitleCard(t, onClick = { openTitle(t) }, modifier = Modifier.width(118.dp)) }
                    }
                }
            }

            // ---------- main catalogue ----------
            item(key = "mainHeader") {
                SectionHeader(
                    if (category == null) "Tonight's free movies" else "${category!!.emoji} Free ${category!!.label.lowercase()}",
                    "Free & legal · plays right here",
                    Modifier.padding(horizontal = 16.dp).padding(top = 28.dp, bottom = 12.dp),
                )
            }

            when (val h = home) {
                is Load.Loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
                }
                is Load.Failed -> item(key = "failed") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        Text("📡", style = MaterialTheme.typography.displaySmall)
                        Text(h.message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                        GoldButton("Try again", { vm.refreshHome() }, icon = null, height = 46.dp)
                    }
                }
                is Load.Ready -> {
                    if (h.value.isEmpty()) item(key = "empty") {
                        Text(
                            "We haven't found free, verified titles in this category yet. As more licensed sources are added they'll appear here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        )
                    }
                    h.value.chunked(3).forEachIndexed { i, row ->
                        item(key = "row$i") {
                            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { t -> TitleCard(t, onClick = { openTitle(t) }, modifier = Modifier.weight(1f)) }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }

            item(key = "footer") {
                Text(
                    "SAM Stream only plays titles whose owners allow it: public domain, open licences and official channels. Sources: Internet Archive, YouTube, TMDB.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp).navigationBarsPadding(),
                )
            }
        }

        // Floating top bar over the hero.
        Row(
            Modifier.fillMaxWidth().background(TopScrim).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(Modifier.weight(1f))
            GlassIcon(Icons.Outlined.Search, "Search", { nav.navigate("search") })
            Spacer(Modifier.width(10.dp))
            GlassIcon(Icons.Outlined.Settings, "Settings", { nav.navigate("settings") })
        }
    }
}

@Composable
private fun HeroPager(heroes: List<Title>, onOpen: (Title) -> Unit, onPlay: (Title) -> Unit) {
    val pager = rememberPagerState(pageCount = { heroes.size })
    LaunchedEffect(pager, heroes.size) {
        if (heroes.size < 2) return@LaunchedEffect
        while (true) {
            delay(6_000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % heroes.size)
        }
    }
    Box(Modifier.fillMaxWidth().height(520.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val t = heroes[page]
            Box(Modifier.fillMaxSize().clickable { onOpen(t) }) {
                AsyncImage(
                    model = t.backdropUrl ?: t.posterUrl, contentDescription = t.name,
                    contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(BottomScrim))
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0x88000000), Color.Transparent))))
                Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
                    FreeTag(if (t.inApp.isNotEmpty()) "FREE TONIGHT" else "FREE")
                    Text(
                        t.name, style = MaterialTheme.typography.displaySmall, color = Color.White, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        listOfNotNull(
                            t.year?.toString(),
                            if (t.mediaType == MediaType.SERIES) "Series" else "Movie",
                            t.inApp.firstOrNull()?.source?.durationMinutes?.let { "$it min" },
                            t.genres.firstOrNull()?.replaceFirstChar { it.uppercase() },
                        ).joinToString("  •  "),
                        style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE6E0EE), modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (t.inApp.isNotEmpty()) GoldButton("Watch free", { onPlay(t) })
                        else if (t.external.isNotEmpty()) GoldButton("Where to watch", { onOpen(t) }, icon = Icons.AutoMirrored.Outlined.OpenInNew)
                        GlassButton("Details", { onOpen(t) }, icon = Icons.Outlined.Info)
                    }
                }
            }
        }
        if (heroes.size > 1) Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(heroes.size) { i ->
                val selected = pager.currentPage == i
                val w by animateDpAsState(if (selected) 22.dp else 6.dp, label = "dot")
                Box(Modifier.height(6.dp).width(w).clip(RoundedCornerShape(3.dp)).background(if (selected) Gold else Color(0x66FFFFFF)))
            }
        }
    }
}
