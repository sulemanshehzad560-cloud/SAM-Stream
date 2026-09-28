package com.openreel.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openreel.app.BuildConfig
import com.openreel.app.OpenReelApp
import com.openreel.app.data.PlayRequest
import com.openreel.app.data.Recent
import com.openreel.app.data.SearchResult
import com.openreel.app.data.Settings
import com.openreel.app.domain.Category
import com.openreel.app.domain.Title
import com.openreel.app.domain.VerifiedSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val openReel = app as OpenReelApp
    private val catalog = openReel.catalog
    private val prefs = openReel.prefs

    val settings: StateFlow<Settings> = prefs.settings
    val recents: StateFlow<List<Recent>> = prefs.recents

    private val _category = MutableStateFlow<Category?>(null)
    val category: StateFlow<Category?> = _category.asStateFlow()

    private val _home = MutableStateFlow<Load<List<Title>>>(Load.Loading)
    val home: StateFlow<Load<List<Title>>> = _home.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<Load<SearchResult>?>(null)
    val results: StateFlow<Load<SearchResult>?> = _results.asStateFlow()

    private val _selected = MutableStateFlow<Title?>(null)
    val selected: StateFlow<Title?> = _selected.asStateFlow()

    private val _nowPlaying = MutableStateFlow<PlayRequest?>(null)
    val nowPlaying: StateFlow<PlayRequest?> = _nowPlaying.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            catalog.refreshPolicy("https://raw.githubusercontent.com/${BuildConfig.REPO_SLUG}/main/app/src/main/assets/rights_policy.json")
            loadHome()
        }
    }

    fun selectCategory(c: Category?) {
        _category.value = c
        viewModelScope.launch { loadHome() }
    }

    fun refreshHome() = viewModelScope.launch { loadHome() }

    private suspend fun loadHome() {
        _home.value = Load.Loading
        // Rotate "Tonight's Free Movies" daily through the most-watched titles.
        val page = if (_category.value == null) LocalDate.now().dayOfYear % 4 + 1 else 1
        _home.value = runCatching { catalog.browse(_category.value, page) }
            .fold({ Load.Ready(it) }, { Load.Failed("Couldn't load movies. Check your connection and try again.") })
    }

    fun setQuery(q: String) {
        _query.value = q
        searchJob?.cancel()
        if (q.isBlank()) { _results.value = null; return }
        searchJob = viewModelScope.launch {
            delay(600)
            runSearch(q)
        }
    }

    fun submitSearch() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(q) }
    }

    private suspend fun runSearch(q: String) {
        _results.value = Load.Loading
        _results.value = runCatching { catalog.search(q) }
            .fold({ Load.Ready(it) }, { Load.Failed("Search failed. Check your connection and try again.") })
    }

    fun open(title: Title) {
        _selected.value = title
        viewModelScope.launch {
            val enriched = runCatching { catalog.enrich(title) }.getOrDefault(title)
            if (_selected.value?.key == title.key) _selected.value = enriched
        }
    }

    /** Prepares a stream (re-checking rights), then [onReady] navigates to the player. */
    fun play(vs: VerifiedSource, title: String, onReady: () -> Unit) {
        viewModelScope.launch {
            _message.value = "Checking rights and preparing the stream…"
            runCatching { catalog.prepare(vs, title) }
                .onSuccess { _nowPlaying.value = it; _message.value = null; onReady() }
                .onFailure { _message.value = it.message ?: "This title can't be played right now." }
        }
    }

    fun resume(r: Recent, onReady: () -> Unit) {
        // Honour takedowns that arrived after the user started watching.
        if (catalog.isBlocked(r.sourceId)) {
            prefs.removeRecent(r.sourceId)
            _message.value = "This title was removed after a rights report."
            return
        }
        _nowPlaying.value = PlayRequest(r.sourceId, r.title, r.playback, r.streamRef, r.providerName, r.rightsNote, r.thumbnail, r.positionMs)
        onReady()
    }

    fun saveProgress(req: PlayRequest, positionMs: Long, durationMs: Long) {
        prefs.saveProgress(
            Recent(req.sourceId, req.title, req.thumbnail, req.kind, req.url, req.providerName, req.rightsNote,
                positionMs, durationMs, System.currentTimeMillis()),
        )
    }

    fun removeRecent(r: Recent) = prefs.removeRecent(r.sourceId)

    fun updateSettings(transform: (Settings) -> Settings) {
        prefs.update(transform)
    }
}
