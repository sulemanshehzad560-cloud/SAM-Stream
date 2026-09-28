package com.samstream.app.data

import android.content.Context
import com.samstream.app.domain.Category
import com.samstream.app.domain.PlaybackKind
import com.samstream.app.domain.ProviderType
import com.samstream.app.domain.RightsEngine
import com.samstream.app.domain.RightsLevel
import com.samstream.app.domain.RightsPolicy
import com.samstream.app.domain.Source
import com.samstream.app.domain.Title
import com.samstream.app.domain.TitleInfo
import com.samstream.app.domain.Titles
import com.samstream.app.domain.VerifiedSource
import com.samstream.app.net.ArchiveApi
import com.samstream.app.net.Http
import com.samstream.app.net.TmdbApi
import com.samstream.app.net.YouTubeApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.time.LocalDate

data class SearchResult(val titles: List<Title>, val notes: List<String>)

/** What the player needs. [url] is a direct video URL for DIRECT_VIDEO, a video id for YOUTUBE_EMBED. */
data class PlayRequest(
    val sourceId: String,
    val title: String,
    val kind: PlaybackKind,
    val url: String,
    val providerName: String,
    val rightsNote: String,
    val thumbnail: String?,
    val startMs: Long,
)

class RightsChangedException(message: String) : Exception(message)

/**
 * Aggregates permitted sources: Internet Archive (always), YouTube Creative Commons / trusted channels (with key),
 * TMDB metadata + free/ad-supported services (with key). Every source passes through the RightsEngine.
 */
class Catalog(private val context: Context, private val prefs: Prefs) {

    @Volatile private var policy: RightsPolicy = parsePolicy(prefs.cachedPolicy ?: bundledPolicy())

    private fun bundledPolicy(): String = context.assets.open("rights_policy.json").bufferedReader().use { it.readText() }

    /** Refreshes trusted channels / takedowns from the repository copy of rights_policy.json. */
    suspend fun refreshPolicy(url: String) {
        runCatching { Http.getJson(url) }.getOrNull()?.let { json ->
            if (json.optInt("version") >= 1) {
                policy = parsePolicy(json.toString())
                prefs.cachedPolicy = json.toString()
            }
        }
    }

    private fun parsePolicy(text: String): RightsPolicy = runCatching {
        val o = JSONObject(text)
        val channels = o.optJSONObject("trustedYouTubeChannels")
        RightsPolicy(
            trustedUploaders = channels?.keys()?.asSequence()?.associate { "youtube:$it" to channels.getString(it) } ?: emptyMap(),
            blockedSources = o.optJSONArray("blockedSources")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            curatedPublicDomainCollections = o.optJSONArray("curatedPublicDomainCollections")
                ?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: setOf("feature_films"),
        )
    }.getOrDefault(RightsPolicy())

    fun isBlocked(sourceId: String): Boolean = sourceId in policy.blockedSources

    private val settings get() = prefs.settings.value

    private fun currentPolicy() = policy.copy(today = LocalDate.now())

    private fun verifyAll(sources: List<Source>, catalogue: List<TitleInfo>): List<VerifiedSource> {
        val p = currentPolicy()
        return sources.distinctBy { it.id }.map { s ->
            val (name, titleYear) = Titles.clean(s.title)
            val match = catalogue.firstOrNull { Titles.sameTitle(name, s.year ?: titleYear, it.name, it.year) }
            // A year in a YouTube upload's title ("The Mask (1994)") is evidence of a commercial release.
            val catalogueYear = match?.year ?: if (s.provider == ProviderType.YOUTUBE) titleYear else null
            VerifiedSource(s, RightsEngine.verify(s, p, settings.country, catalogueYear))
        }
    }

    /** Hides excluded sources, and unverified ones unless the user asked to see them (they're never playable). */
    private fun visible(titles: List<Title>, keepEmptyCatalogueTitles: Boolean): List<Title> {
        val showUnverified = settings.showUnverified
        return titles.map { t ->
            t.copy(sources = t.sources.filter { vs ->
                vs.verdict.level != RightsLevel.BLOCKED && (showUnverified || vs.verdict.level != RightsLevel.UNVERIFIED)
            })
        }.filter { it.sources.isNotEmpty() || (keepEmptyCatalogueTitles && it.tmdbId != null) }
    }

    // ---------- home ----------

    suspend fun browse(category: Category?, page: Int = 1): List<Title> = coroutineScope {
        val archive = async {
            runCatching {
                val url = ArchiveApi.searchUrl(null, category?.archiveFilter, rows = 60, page = page, collections = category?.browseCollections ?: "feature_films")
                ArchiveApi.parseSearch(Http.getJson(url))
            }
                .getOrDefault(emptyList())
        }
        val youtube = async { if (category != null) youtubeSearch(category.youtubeQuery) else emptyList() }
        val sources = archive.await() + youtube.await()
        val titles = Titles.merge(verifyAll(sources, emptyList()), emptyList())
        visible(titles, keepEmptyCatalogueTitles = false).filter { it.inApp.isNotEmpty() }
    }

    // ---------- search ----------

    suspend fun search(query: String): SearchResult = coroutineScope {
        val notes = mutableListOf<String>()
        val archive = async { runCatching { ArchiveApi.parseSearch(Http.getJson(ArchiveApi.searchUrl(query, rows = 40))) } }
        val youtube = async { youtubeSearch(query) }
        val tmdb = async { tmdbSearch(query) }

        val archiveSources = archive.await().getOrElse { notes += "Internet Archive is not responding right now."; emptyList() }
        val catalogue = tmdb.await()
        // Free/ad-supported services for the best catalogue matches.
        val services = catalogue.take(6).map { info -> async { freeServices(info) } }.awaitAll()
        val enriched = services.map { it.first } + catalogue.drop(6)
        val serviceSources = services.flatMap { it.second }

        if (settings.tmdbKey.isBlank()) notes += "Add a free TMDB key in Settings to see posters and free services like Tubi or Pluto TV in your country."
        if (settings.youtubeKey.isBlank()) notes += "Add a YouTube API key in Settings to include Creative Commons and official-channel films."

        val merged = Titles.merge(verifyAll(archiveSources + youtube.await() + serviceSources, enriched), enriched)
        SearchResult(visible(merged, keepEmptyCatalogueTitles = true), notes)
    }

    private suspend fun youtubeSearch(query: String): List<Source> {
        val key = settings.youtubeKey.ifBlank { return emptyList() }
        return runCatching {
            val ids = mutableListOf<String>()
            ids += YouTubeApi.parseSearchIds(Http.getJson(YouTubeApi.searchUrl(query, key, creativeCommonsOnly = true)))
            // Official distributor channels: their own uploads under the standard YouTube licence.
            currentPolicy().trustedUploaders.keys.take(5).forEach { k ->
                val channel = k.removePrefix("youtube:")
                ids += runCatching { YouTubeApi.parseSearchIds(Http.getJson(YouTubeApi.searchUrl(query, key, false, channel))) }.getOrDefault(emptyList())
            }
            if (ids.isEmpty()) emptyList() else YouTubeApi.parseVideos(Http.getJson(YouTubeApi.videosUrl(ids.distinct().take(50), key)))
        }.getOrDefault(emptyList())
    }

    private suspend fun tmdbSearch(query: String): List<TitleInfo> {
        val key = settings.tmdbKey.ifBlank { return emptyList() }
        return runCatching {
            val (url, headers) = TmdbApi.searchUrl(query, key)
            TmdbApi.parseSearch(Http.getJson(url, headers)).take(12)
        }.getOrDefault(emptyList())
    }

    private suspend fun freeServices(info: TitleInfo): Pair<TitleInfo, List<Source>> {
        val key = settings.tmdbKey.ifBlank { return info to emptyList() }
        return runCatching {
            val (url, headers) = TmdbApi.detailsUrl(info.mediaType, info.tmdbId, key)
            val json = Http.getJson(url, headers)
            val full = TmdbApi.parseTitle(json, info.mediaType) ?: info
            full to TmdbApi.parseFreeProviders(json, full, settings.country)
        }.getOrDefault(info to emptyList())
    }

    /** Detail view: adds catalogue metadata and free services for a title found only in an archive/YouTube source. */
    suspend fun enrich(title: Title): Title {
        if (title.tmdbId != null || settings.tmdbKey.isBlank()) return title
        val info = tmdbSearch(title.name).firstOrNull { Titles.sameTitle(it.name, it.year, title.name, title.year) } ?: return title
        val (full, services) = freeServices(info)
        val verified = verifyAll(title.sources.map { it.source } + services, listOf(full))
        return Titles.merge(verified, listOf(full)).firstOrNull()?.let { visible(listOf(it), true).firstOrNull() } ?: title
    }

    // ---------- playback ----------

    /** Re-checks the rights at play time against the provider's latest metadata, then returns what to play. */
    suspend fun prepare(vs: VerifiedSource, displayTitle: String): PlayRequest {
        val s = vs.source
        require(vs.verdict.playableInApp) { "This source can't be played in SAM Stream." }
        val start = prefs.positionFor(s.id)
        return when (s.playback) {
            PlaybackKind.DIRECT_VIDEO -> {
                val id = s.streamRef ?: error("Missing identifier")
                val stream = ArchiveApi.parseStream(id, Http.getJson(ArchiveApi.metadataUrl(id)))
                    ?: throw RightsChangedException("No playable video file was found for this title.")
                val fresh = s.copy(licenseUrl = stream.licenseUrl ?: s.licenseUrl, collections = stream.collections.ifEmpty { s.collections })
                val v = RightsEngine.verify(fresh, currentPolicy(), settings.country, s.year)
                if (!v.playableInApp) throw RightsChangedException("The rights information for this upload changed: ${v.reason}")
                PlayRequest(s.id, displayTitle, s.playback, stream.url, s.providerName, v.reason, s.thumbnailUrl, start)
            }
            PlaybackKind.YOUTUBE_EMBED ->
                PlayRequest(s.id, displayTitle, s.playback, s.streamRef ?: error("Missing video id"), s.providerName, vs.verdict.reason, s.thumbnailUrl, start)
            PlaybackKind.EXTERNAL_APP -> error("External sources open in the provider's app.")
        }
    }
}
