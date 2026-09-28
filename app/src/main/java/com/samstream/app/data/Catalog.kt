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
import com.samstream.app.net.HttpException
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
            trustedSources = o.optJSONArray("trustedSources")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            curatedPublicDomainCollections = o.optJSONArray("curatedPublicDomainCollections")
                ?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: setOf("feature_films"),
        )
    }.getOrDefault(RightsPolicy())

    fun isBlocked(sourceId: String): Boolean = sourceId in policy.blockedSources

    private val settings get() = prefs.settings.value

    /** Last TMDB problem in plain words, so a bad key is visible instead of silently returning nothing. */
    @Volatile var tmdbProblem: String? = null
        private set

    private fun tmdbMessage(e: Throwable): String = when ((e as? HttpException)?.code) {
        401 -> "TMDB rejected your key. In Settings, paste the \"API Read Access Token\" (long) or \"API Key\" (32 characters) again, then tap Test key."
        404 -> "TMDB couldn't find that item."
        429 -> "TMDB is busy (too many requests). Try again in a minute."
        null -> "Couldn't reach TMDB. Check your connection."
        else -> "TMDB error ${(e as HttpException).code}."
    }

    private suspend fun <T> tmdbCall(block: suspend () -> T): T? = try {
        block().also { tmdbProblem = null }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        tmdbProblem = tmdbMessage(e); null
    }

    /** Last YouTube problem in plain words (bad key, quota used up, key restricted…). */
    @Volatile var youtubeProblem: String? = null
        private set

    private fun youtubeMessage(e: Throwable): String {
        val h = e as? HttpException ?: return "Couldn't reach YouTube. Check your connection."
        val r = h.reasons
        return when {
            "quota" in r -> "YouTube's free daily limit for your key is used up (each search uses 100 of 10,000 units). It resets at midnight Pacific time (11 am UAE)."
            "android_app_blocked" in r || "ios_app_blocked" in r || "referer" in r || "ip_address_blocked" in r || "ipblocked" in r ->
                "Your YouTube key is restricted. In Google Cloud → Credentials → your key, set \"Application restrictions\" to None, then save."
            "service_blocked" in r || "api_key_service_blocked" in r ->
                "Your key isn't allowed to use YouTube. In Google Cloud → Credentials → your key, add \"YouTube Data API v3\" under API restrictions (or choose Don't restrict)."
            "accessnotconfigured" in r || "has not been used" in r || "is disabled" in r || "service_disabled" in r ->
                "YouTube Data API v3 isn't turned on for this key's project. In Google Cloud → APIs & Services → Library, open YouTube Data API v3 and tap Enable."
            "keyinvalid" in r || "api key not valid" in r || "api_key_invalid" in r ->
                "YouTube says this key isn't valid. Copy the key again from Google Cloud → Credentials (it starts with AIza, 39 characters)."
            else -> "YouTube error ${h.code}${r.substringBefore(' ').takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}."
        }
    }

    /** Checks a YouTube key. Returns null when it works, otherwise what's wrong. */
    suspend fun checkYoutubeKey(key: String): String? {
        val k = cleanKey(key)
        if (k.isEmpty()) return "Paste your YouTube key first."
        return try {
            Http.getJson(YouTubeApi.checkUrl(k)); youtubeProblem = null; null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            youtubeMessage(e)
        }
    }

    private val minYear: Int? get() = if (settings.modernOnly) MIN_YEAR else null

    /** Checks a key before saving it. Returns null when it works, otherwise what's wrong. */
    suspend fun checkTmdbKey(key: String): String? {
        val k = cleanKey(key)
        if (k.isEmpty()) return "Paste your TMDB key first."
        return try {
            val (url, headers) = TmdbApi.checkUrl(k)
            Http.getJson(url, headers); null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            tmdbMessage(e)
        }
    }

    /** Home row: popular movies that a licensed service offers free / with ads in the user's country. */
    suspend fun freeOnServices(): List<Title> = coroutineScope {
        val key = settings.tmdbKey.ifBlank { return@coroutineScope emptyList<Title>() }
        val infos = tmdbCall {
            val (url, headers) = TmdbApi.discoverFreeUrl(key, settings.country, minYear = minYear)
            TmdbApi.parseDiscover(Http.getJson(url, headers))
        }.orEmpty().take(18)
        val withServices = infos.map { async { freeServices(it) } }.awaitAll()
        val catalogue = withServices.map { it.first }
        val merged = Titles.merge(verifyAll(withServices.flatMap { it.second }, catalogue), catalogue)
        visible(merged, keepEmptyCatalogueTitles = false).filter { it.hasFreeLegalSource }
    }

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
            .filter { modernEnough(it) }
    }

    /**
     * "Only 1995 and newer": drops titles released earlier. A title with no known year is kept only when it comes from
     * YouTube or a streaming service (new uploads); undated Internet Archive items are almost always old films.
     */
    private fun modernEnough(t: Title): Boolean {
        val min = minYear ?: return true
        val year = t.year ?: return t.tmdbId != null || t.sources.any { it.source.provider != ProviderType.INTERNET_ARCHIVE }
        return year >= min
    }

    // ---------- home ----------

    suspend fun browse(category: Category?, page: Int = 1): List<Title> = coroutineScope {
        val archive = async {
            runCatching {
                val url = ArchiveApi.searchUrl(null, category?.archiveFilter, rows = 60, page = page, collections = category?.browseCollections ?: "feature_films", minYear = minYear)
                ArchiveApi.parseSearch(Http.getJson(url))
            }
                .getOrDefault(emptyList())
        }
        val youtube = async { youtubeSearch(category?.youtubeQuery ?: "full movie") }
        val sources = archive.await() + youtube.await()
        val titles = Titles.merge(verifyAll(sources, emptyList()), emptyList())
        visible(titles, keepEmptyCatalogueTitles = false).filter { it.inApp.isNotEmpty() }
    }

    // ---------- search ----------

    suspend fun search(query: String): SearchResult = coroutineScope {
        val notes = mutableListOf<String>()
        val archive = async { runCatching { ArchiveApi.parseSearch(Http.getJson(ArchiveApi.searchUrl(query, rows = 40, minYear = minYear))) } }
        val youtube = async { youtubeSearch(query) }
        val tmdb = async { tmdbSearch(query) }

        val archiveSources = archive.await().getOrElse { notes += "Internet Archive is not responding right now."; emptyList() }
        val catalogue = tmdb.await()
        // Free/ad-supported services for the best catalogue matches.
        val services = catalogue.take(6).map { info -> async { freeServices(info) } }.awaitAll()
        val enriched = services.map { it.first } + catalogue.drop(6)
        val serviceSources = services.flatMap { it.second }

        if (settings.tmdbKey.isBlank()) notes += "Add a free TMDB key in Settings to see posters and free services like Tubi or Pluto TV in your country."
        else tmdbProblem?.let { notes += it }
        if (settings.youtubeKey.isBlank()) notes += "Add a YouTube API key in Settings to include Creative Commons and official-channel films."
        else youtubeProblem?.let { notes += it }

        val merged = Titles.merge(verifyAll(archiveSources + youtube.await() + serviceSources, enriched), enriched)
        SearchResult(visible(merged, keepEmptyCatalogueTitles = true), notes)
    }

    private suspend fun youtubeSearch(query: String): List<Source> {
        val key = settings.youtubeKey.ifBlank { return emptyList() }
        return try {
            val ids = mutableListOf<String>()
            ids += YouTubeApi.parseSearchIds(Http.getJson(YouTubeApi.searchUrl(query, key, creativeCommonsOnly = true)))
            // Official distributor channels: their own uploads under the standard YouTube licence.
            currentPolicy().trustedUploaders.keys.take(5).forEach { k ->
                val channel = k.removePrefix("youtube:")
                ids += runCatching { YouTubeApi.parseSearchIds(Http.getJson(YouTubeApi.searchUrl(query, key, false, channel))) }.getOrDefault(emptyList())
            }
            (if (ids.isEmpty()) emptyList<Source>() else YouTubeApi.parseVideos(Http.getJson(YouTubeApi.videosUrl(ids.distinct().take(50), key))))
                .also { youtubeProblem = null }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            youtubeProblem = youtubeMessage(e); emptyList()
        }
    }

    private suspend fun tmdbSearch(query: String): List<TitleInfo> {
        val key = settings.tmdbKey.ifBlank { return emptyList() }
        return tmdbCall {
            val (url, headers) = TmdbApi.searchUrl(query, key)
            TmdbApi.parseSearch(Http.getJson(url, headers)).take(12)
        }.orEmpty()
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
