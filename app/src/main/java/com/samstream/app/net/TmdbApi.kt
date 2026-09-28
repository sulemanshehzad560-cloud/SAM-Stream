package com.samstream.app.net

import com.samstream.app.domain.MediaType
import com.samstream.app.domain.PlaybackKind
import com.samstream.app.domain.ProviderType
import com.samstream.app.domain.Source
import com.samstream.app.domain.TitleInfo
import org.json.JSONObject

/**
 * TMDB (needs a free API key): posters/overviews for any movie or series, plus which services stream it
 * FREE or with ADS in the user's country (watch-provider data by JustWatch). Those services are licensed,
 * so they're shown as authorized sources that open in the provider's own app/site.
 */
object TmdbApi {
    private const val IMG = "https://image.tmdb.org/t/p/"

    /** v3 keys go in the query string; v4 read-access tokens (long JWTs) go in the Authorization header. */
    fun auth(key: String): Pair<List<Pair<String, String>>, Map<String, String>> =
        if (key.length > 40) emptyList<Pair<String, String>>() to mapOf("Authorization" to "Bearer $key")
        else listOf("api_key" to key) to emptyMap()

    fun searchUrl(query: String, key: String, language: String = "en-US"): Pair<String, Map<String, String>> {
        val (params, headers) = auth(key)
        return Http.url("https://api.themoviedb.org/3/search/multi", params + listOf("query" to query, "include_adult" to "false", "language" to language)) to headers
    }

    fun detailsUrl(type: MediaType, id: Int, key: String): Pair<String, Map<String, String>> {
        val (params, headers) = auth(key)
        val path = if (type == MediaType.SERIES) "tv" else "movie"
        return Http.url("https://api.themoviedb.org/3/$path/$id", params + listOf("append_to_response" to "watch/providers")) to headers
    }

    fun parseSearch(json: JSONObject): List<TitleInfo> = json.optJSONArray("results").objects().mapNotNull { r ->
        val type = when (r.optString("media_type")) { "movie" -> MediaType.MOVIE; "tv" -> MediaType.SERIES; else -> return@mapNotNull null }
        parseTitle(r, type)
    }

    fun parseTitle(r: JSONObject, type: MediaType): TitleInfo? {
        val name = (r.strOrNull("title") ?: r.strOrNull("name")) ?: return null
        val date = r.strOrNull("release_date") ?: r.strOrNull("first_air_date")
        return TitleInfo(
            tmdbId = r.optInt("id").takeIf { it > 0 } ?: return null,
            mediaType = type,
            name = name,
            year = date?.take(4)?.toIntOrNull(),
            overview = r.strOrNull("overview"),
            posterUrl = r.strOrNull("poster_path")?.let { "${IMG}w342$it" },
            backdropUrl = r.strOrNull("backdrop_path")?.let { "${IMG}w780$it" },
            genres = r.optJSONArray("genres").objects().mapNotNull { it.strOrNull("name") },
        )
    }

    /** Free and ad-supported services for [country], as authorized external sources. */
    fun parseFreeProviders(details: JSONObject, info: TitleInfo, country: String): List<Source> {
        val region = details.optJSONObject("watch/providers")?.optJSONObject("results")?.optJSONObject(country.uppercase()) ?: return emptyList()
        val link = region.strOrNull("link") ?: "https://www.themoviedb.org/${if (info.mediaType == MediaType.SERIES) "tv" else "movie"}/${info.tmdbId}/watch"
        return listOf("free" to "Free", "ads" to "Free with ads").flatMap { (key, label) ->
            region.optJSONArray(key).objects().mapNotNull { p ->
                val name = p.strOrNull("provider_name") ?: return@mapNotNull null
                Source(
                    id = "svc:${p.optInt("provider_id")}:${info.tmdbId}",
                    provider = ProviderType.FREE_SERVICE,
                    providerName = name,
                    title = info.name,
                    year = info.year,
                    pageUrl = link,
                    playback = PlaybackKind.EXTERNAL_APP,
                    streamRef = link,
                    allowedCountries = setOf(country.uppercase()),
                    thumbnailUrl = p.strOrNull("logo_path")?.let { "${IMG}w92$it" },
                    description = label,
                    mediaType = info.mediaType,
                )
            }
        }.distinctBy { it.providerName }
    }
}
