package com.samstream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.samstream.app.domain.MediaType
import com.samstream.app.ui.AppViewModel
import com.samstream.app.ui.Load
import com.samstream.app.ui.components.GlassIcon
import com.samstream.app.ui.components.Pill
import com.samstream.app.ui.components.Poster
import com.samstream.app.ui.components.WatchStatus
import com.samstream.app.ui.theme.Gold
import com.samstream.app.ui.theme.Hairline
import com.samstream.app.ui.theme.Ink
import com.samstream.app.ui.theme.InkHigh
import com.samstream.app.ui.theme.InkRaised

@Composable
fun SearchScreen(vm: AppViewModel, nav: NavHostController) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", { nav.popBackStack() })
            Spacer(Modifier.width(10.dp))
            TextField(
                value = query, onValueChange = vm::setQuery, singleLine = true,
                placeholder = { Text("Movies & series from 1995 on…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Gold) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Outlined.Close, "Clear") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.submitSearch() }),
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = InkHigh, unfocusedContainerColor = InkHigh,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Gold,
                ),
                modifier = Modifier.weight(1f).border(1.dp, Hairline, RoundedCornerShape(50)).focusRequester(focus),
            )
        }
        when (val r = results) {
            null -> Hint(onPick = { vm.setQuery(it); vm.submitSearch() })
            is Load.Loading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
            is Load.Failed -> Text(r.message, modifier = Modifier.padding(24.dp))
            is Load.Ready -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                r.value.notes.forEach { note ->
                    item {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(InkRaised).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Info, null, tint = Gold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (r.value.titles.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎬", style = MaterialTheme.typography.displaySmall)
                        Text("No free, legal source for “$query”", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                        Text("SAM Stream only lists titles whose rights holders allow free viewing.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                } else item {
                    Text("${r.value.titles.size} results", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(r.value.titles, key = { it.key }) { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(InkRaised).border(1.dp, Hairline, RoundedCornerShape(18.dp))
                            .clickable { vm.open(t); nav.navigate("title") }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Poster(t.posterUrl, t.name, Modifier.width(78.dp).height(117.dp), corner = 12.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(t.year?.toString(), if (t.mediaType == MediaType.SERIES) "Series" else "Movie",
                                    t.genres.firstOrNull()?.replaceFirstChar { it.uppercase() }).joinToString("  •  "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp),
                            )
                            t.overview?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFCFC8D9), maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            WatchStatus(t)
                        }
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hint(onPick: (String) -> Unit) {
    Column(Modifier.padding(20.dp)) {
        Text("Search once, watch here.", style = MaterialTheme.typography.headlineSmall)
        Text(
            "We check every source before showing a play button: public domain, Creative Commons, official channels and free licensed services only.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
        )
        Text("TRY", style = MaterialTheme.typography.labelSmall, color = Gold, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Sintel", "Big Buck Bunny", "Tears of Steel", "Cosmos Laundromat", "Spring", "Agent 327", "Sprite Fright", "Charge")
                .forEach { q -> Pill(q, false, { onPick(q) }) }
        }
    }
}
