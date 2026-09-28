package com.samstream.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samstream.app.BuildConfig
import com.samstream.app.SamStreamApp
import com.samstream.app.data.PlayRequest
import com.samstream.app.data.Recent
import com.samstream.app.data.SearchResult
import com.samstream.app.data.Settings
import com.samstream.app.domain.Category
import com.samstream.app.domain.Title
import com.samstream.app.domain.VerifiedSource
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
    private val samStream = app as SamStreamApp
    private val catalog = samStream.catalog
    private val prefs = samStream.prefs

    val settings: StateFlow<Settings> = prefs.settings
    val recents: StateFlow<List<Recent>> = prefs.recents

    private val _category = MutableStateFlow<Category?>(null)
    val category: StateFlow<Category?> = _category.asStateFlow()

    private val _home = MutableStateFlow<Load<List<Title>>>(Load.Loading)
    val home: StateFlow<Load<List<Title>>> = _home.asStateFlow()

    /** "Free on streaming apps" row, filled from TMDB when a key is set. Empty when there's no key. */
    private val _services = MutableStateFlow<List<Title>>(emptyList())
    val services: StateFlow<List<Title>> = _services.asStateFlow()

    private val _tmdbProblem = MutableStateFlow<String?>(null)
    val tmdbProblem: StateFlow<String?> = _tmdbProblem.asStateFlow()

    private val _youtubeProblem = MutableStateFlow<String?>(null)
    val youtubeProblem: StateFlow<String?> = _youtubeProblem.asStateFlow()

    private val _ytKeyCheck = MutableStateFlow<String?>(null)
    val ytKeyCheck: StateFlow<String?> = _ytKeyCheck.asStateFlow()

    fun testYoutubeKey(key: String) = viewModelScope.launch {
        _ytKeyCheck.value = "Checking…"
        val problem = catalog.checkYoutubeKey(key)
        if (problem != null) { _ytKeyCheck.value = "✗ $problem"; return@launch }
        prefs.update { it.copy(youtubeKey = key) }
        _ytKeyCheck.value = "✓ Key works and is saved. Loading YouTube films…"
        loadHome()
        val found = (_home.value as? Load.Ready)?.value?.count { t -> t.inApp.any { it.source.provider == com.samstream.app.domain.ProviderType.YOUTUBE } } ?: 0
        _ytKeyCheck.value = catalog.youtubeProblem?.let { "✗ $it" }
            ?: "✓ Key works and is saved. $found YouTube films on Home now."
    }

    private val _keyCheck = MutableStateFlow<String?>(null)
    val keyCheck: StateFlow<String?> = _keyCheck.asStateFlow()

    fun testTmdbKey(key: String) = viewModelScope.launch {
        _keyCheck.value = "Checking…"
        val problem = catalog.checkTmdbKey(key)
        if (problem != null) { _keyCheck.value = "✗ $problem"; return@launch }
        prefs.update { it.copy(tmdbKey = key) }
        _keyCheck.value = "✓ Key works and is saved. Loading free movies…"
        loadServices()
        _keyCheck.value = if (_services.value.isEmpty())
            "✓ Key works and is saved. TMDB lists no free or free-with-ads streaming titles for ${settings.value.country} right now. Search still shows posters and details, and you can try another country."
        else "✓ Key works and is saved. ${_services.value.size} free titles added to Home."
    }

    private suspend fun loadServices() {
        _services.value = runCatching { catalog.freeOnServices() }.getOrDefault(emptyList())
        _tmdbProblem.value = if (settings.value.tmdbKey.isBlank()) null else catalog.tmdbProblem
    }

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
            launch { loadServices() }
            loadHome()
        }
    }

    fun selectCategory(c: Category?) {
        _category.value = c
        viewModelScope.launch { loadHome() }
    }

    fun refreshHome() = viewModelScope.launch {
        launch { loadServices() }
        loadHome()
    }

    private suspend fun loadHome() {
        _home.value = Load.Loading
        // Rotate "Tonight's Free Movies" daily through the most-watched titles.
        val page = if (_category.value == null) LocalDate.now().dayOfYear % 4 + 1 else 1
        _home.value = runCatching { catalog.browse(_category.value, page) }
            .fold({ Load.Ready(it) }, { Load.Failed("Couldn't load movies. Check your connection and try again.") })
        _youtubeProblem.value = if (settings.value.youtubeKey.isBlank()) null else catalog.youtubeProblem
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
        val oldCountry = settings.value.country
        val oldModern = settings.value.modernOnly
        prefs.update(transform)
        if (settings.value.country != oldCountry || settings.value.modernOnly != oldModern) viewModelScope.launch { loadServices() }
        if (settings.value.modernOnly != oldModern) refreshHome()
    }
}
