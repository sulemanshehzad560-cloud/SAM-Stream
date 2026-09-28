package com.samstream.app.net

import com.samstream.app.domain.MediaType
import com.samstream.app.domain.PlaybackKind
import com.samstream.app.domain.ProviderType
import com.samstream.app.domain.Source
import org.json.JSONObject

/**
 * Internet Archive (archive.org): public-domain and openly licensed films, streamed directly from archive.org.
 * No API key. Rights are decided per item by the RightsEngine from its licence URL and collection.
 */
object ArchiveApi {
    /** Public-domain-curated collection plus collections where uploads commonly carry an explicit licence. */
    private const val COLLECTIONS =
        "feature_films OR opensource_movies OR animationandcartoons OR classic_tv OR silent_films OR comedy_films OR scifi_horror OR film_noir OR short_films"

    /** Keeps the home feed family-friendly: adult/exploitation titles are only reachable by explicit search. */
    private const val EXCLUDE_ADULT = "-subject:(sex OR sexual OR erotic OR erotica OR adult OR nudity OR nude OR exploitation OR sexploitation)"

    /**
     * @param text free-text title search (null for browsing)
     * @param filter extra Lucene clause, e.g. a category's subject filter
     * @param collections restricts to these collections; defaults to all film collections for search
     */
    fun searchUrl(text: String?, filter: String? = null, rows: Int = 50, page: Int = 1, collections: String = COLLECTIONS, minYear: Int? = null): String {
        val q = buildString {
            append("mediatype:(movies) AND collection:($collections)")
            if (!text.isNullOrBlank()) {
                val words = text.trim().replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (words.isNotEmpty()) append(" AND title:(${words.joinToString(" AND ")})")
            } else {
                append(" AND $EXCLUDE_ADULT")
            }
            if (!filter.isNullOrBlank()) append(" AND ($filter)")
            if (minYear != null) append(" AND year:[$minYear TO 2100]")
        }
        val fields = listOf("identifier", "title", "year", "date", "description", "licenseurl", "collection", "subject", "runtime", "language")
        return Http.url(
            "https://archive.org/advancedsearch.php",
            listOf("q" to q) + fields.map { "fl[]" to it } +
                listOf("sort[]" to "downloads desc", "rows" to rows.toString(), "page" to page.toString(), "output" to "json"),
        )
    }

    fun parseSearch(json: JSONObject): List<Source> =
        json.optJSONObject("response")?.optJSONArray("docs").objects().mapNotNull(::toSource)

    private fun toSource(d: JSONObject): Source? {
        val id = d.strOrNull("identifier") ?: return null
        val title = d.strOrNull("title") ?: return null
        val year = (d.strOrNull("year") ?: d.strOrNull("date"))?.let { Regex("(1[89]|20)\\d{2}").find(it)?.value?.toInt() }
        return Source(
            id = "ia:$id",
            provider = ProviderType.INTERNET_ARCHIVE,
            providerName = "Internet Archive",
            title = title,
            year = year,
            pageUrl = "https://archive.org/details/$id",
            playback = PlaybackKind.DIRECT_VIDEO,
            streamRef = id,
            licenseUrl = d.strOrNull("licenseurl"),
            collections = d.strList("collection"),
            durationMinutes = d.strOrNull("runtime")?.let(::parseRuntime),
            thumbnailUrl = "https://archive.org/services/img/$id",
            description = d.strOrNull("description")?.let(::stripHtml)?.take(600),
            subjects = d.strList("subject").take(6),
            language = d.strOrNull("language"),
            mediaType = if (d.strList("collection").any { it.equals("classic_tv", true) }) MediaType.SERIES else MediaType.MOVIE,
        )
    }

    /** "1:35:44", "95 min", "01:35" or "5744.3" (seconds) → minutes. */
    fun parseRuntime(raw: String): Int? {
        val s = raw.trim().lowercase()
        Regex("^(\\d+):(\\d{1,2}):(\\d{1,2})").find(s)?.let { val (h, m, _) = it.destructured; return h.toInt() * 60 + m.toInt() }
        Regex("^(\\d+):(\\d{1,2})$").find(s)?.let { val (m, _) = it.destructured; return m.toInt() }
        Regex("(\\d+)\\s*min").find(s)?.let { return it.groupValues[1].toInt() }
        s.toDoubleOrNull()?.let { return (it / 60).toInt().takeIf { m -> m > 0 } }
        return null
    }

    fun stripHtml(s: String): String = s.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace("&amp;", "&")
        .replace(Regex("\\s+"), " ").trim()

    fun metadataUrl(identifier: String) = "https://archive.org/metadata/${Http.enc(identifier)}"

    data class Stream(val url: String, val licenseUrl: String?, val collections: List<String>, val durationMinutes: Int?)

    /** Picks the best browser-playable MP4 from an item's file list. */
    fun parseStream(identifier: String, json: JSONObject): Stream? {
        val files = json.optJSONArray("files").objects()
        fun rank(f: JSONObject): Int {
            val name = f.optString("name").lowercase()
            val format = f.optString("format").lowercase()
            if (!name.endsWith(".mp4") && !name.endsWith(".m4v")) return -1
            return when {
                format.contains("h.264") && !format.contains("hd") -> 5
                format.contains("h.264") -> 4
                format == "mpeg4" -> 3
                format.contains("512kb") -> 2
                else -> 1
            }
        }
        val best = files.filter { rank(it) > 0 }.maxWithOrNull(compareBy<JSONObject> { rank(it) }.thenBy { it.optString("size").toLongOrNull() ?: 0L })
            ?: return null
        val name = best.optString("name")
        val meta = json.optJSONObject("metadata") ?: JSONObject()
        val path = name.split('/').joinToString("/") { Http.enc(it).replace("+", "%20") }
        return Stream(
            url = "https://archive.org/download/${Http.enc(identifier)}/$path",
            licenseUrl = meta.strOrNull("licenseurl"),
            collections = meta.strList("collection"),
            durationMinutes = best.optString("length").takeIf { it.isNotBlank() }?.let(::parseRuntime),
        )
    }
}
