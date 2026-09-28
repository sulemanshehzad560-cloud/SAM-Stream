package com.samstream.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.samstream.app.domain.MediaType
import com.samstream.app.ui.AppViewModel
import com.samstream.app.ui.Load
import com.samstream.app.ui.components.Poster
import com.samstream.app.ui.components.WatchStatus

@Composable
fun SearchScreen(vm: AppViewModel, nav: NavHostController) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            OutlinedTextField(
                value = query, onValueChange = vm::setQuery, singleLine = true,
                placeholder = { Text("The Mask, Charlie Chaplin, Sintel…") },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Outlined.Close, "Clear") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.submitSearch() }),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        when (val r = results) {
            null -> Hint()
            is Load.Loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is Load.Failed -> Text(r.message, modifier = Modifier.padding(24.dp))
            is Load.Ready -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (r.value.titles.isEmpty()) item {
                    Text("No free, legal source found for “$query”.", style = MaterialTheme.typography.titleMedium)
                    Text("SAM Stream only lists titles whose rights holders allow free viewing.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(r.value.titles, key = { it.key }) { t ->
                    Row(Modifier.fillMaxWidth().clickable { vm.open(t); nav.navigate("title") }) {
                        Poster(t.posterUrl, t.name, Modifier.width(72.dp).height(108.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(t.year?.toString(), if (t.mediaType == MediaType.SERIES) "Series" else "Movie", t.genres.firstOrNull()).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            WatchStatus(t)
                        }
                    }
                }
                r.value.notes.forEach { note ->
                    item { Text("ℹ️ $note", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun Hint() {
    Column(Modifier.padding(24.dp)) {
        Text("Search once, watch here.", style = MaterialTheme.typography.titleMedium)
        Text(
            "We check every source before showing a play button: public domain, Creative Commons, official channels and free licensed services only.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
        )
    }
}
