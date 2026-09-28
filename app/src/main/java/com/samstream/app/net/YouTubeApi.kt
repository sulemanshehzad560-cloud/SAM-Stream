package com.samstream.app.net

import com.samstream.app.domain.PlaybackKind
import com.samstream.app.domain.ProviderType
import com.samstream.app.domain.Source
import org.json.JSONObject

/**
 * YouTube Data API v3 (needs an API key). Only two kinds of videos are ever considered:
 *  - Creative Commons licensed long videos with embedding enabled (searched with videoLicense=creativeCommon), and
 *  - uploads from trusted official distributor channels (see assets/rights_policy.json).
 * Playback always uses YouTube's own embedded player, so ads/analytics stay with the rights holder.
 */
object YouTubeApi {
    fun searchUrl(query: String, key: String, creativeCommonsOnly: Boolean = true, channelId: String? = null): String =
        Http.url(
            "https://www.googleapis.com/youtube/v3/search",
            listOfNotNull(
                "part" to "id", "type" to "video", "maxResults" to "15", "q" to query,
                "videoEmbeddable" to "true", "videoDuration" to "long", "key" to key,
                if (creativeCommonsOnly) "videoLicense" to "creativeCommon" else null,
                channelId?.let { "channelId" to it },
            ),
        )

    fun parseSearchIds(json: JSONObject): List<String> =
        json.optJSONArray("items").objects().mapNotNull { it.optJSONObject("id")?.strOrNull("videoId") }

    fun videosUrl(ids: List<String>, key: String): String = Http.url(
        "https://www.googleapis.com/youtube/v3/videos",
        listOf("part" to "snippet,contentDetails,status", "id" to ids.joinToString(","), "key" to key),
    )

    fun parseVideos(json: JSONObject): List<Source> = json.optJSONArray("items").objects().mapNotNull { v ->
        val id = v.strOrNull("id") ?: return@mapNotNull null
        val sn = v.optJSONObject("snippet") ?: JSONObject()
        val cd = v.optJSONObject("contentDetails") ?: JSONObject()
        val st = v.optJSONObject("status") ?: JSONObject()
        val region = cd.optJSONObject("regionRestriction")
        val thumbs = sn.optJSONObject("thumbnails")
        Source(
            id = "yt:$id",
            provider = ProviderType.YOUTUBE,
            providerName = sn.strOrNull("channelTitle")?.let { "YouTube · $it" } ?: "YouTube",
            title = sn.strOrNull("title") ?: return@mapNotNull null,
            year = null,
            pageUrl = "https://www.youtube.com/watch?v=$id",
            playback = PlaybackKind.YOUTUBE_EMBED,
            streamRef = id,
            licenseTag = st.strOrNull("license"),
            embeddable = if (st.has("embeddable")) st.optBoolean("embeddable") else null,
            uploaderId = sn.strOrNull("channelId"),
            uploaderName = sn.strOrNull("channelTitle"),
            allowedCountries = region?.let { r -> r.strList("allowed").map(String::uppercase).toSet().takeIf { r.has("allowed") } },
            blockedCountries = region?.strList("blocked")?.map(String::uppercase)?.toSet() ?: emptySet(),
            durationMinutes = cd.strOrNull("duration")?.let(::isoMinutes),
            thumbnailUrl = listOf("maxres", "high", "medium", "default").firstNotNullOfOrNull { thumbs?.optJSONObject(it)?.strOrNull("url") },
            description = sn.strOrNull("description")?.take(600),
        )
    }

    /** "PT1H36M12S" → 96. */
    fun isoMinutes(iso: String): Int? {
        val m = Regex("P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").matchEntire(iso) ?: return null
        val (d, h, min, _) = m.destructured
        return (d.toIntOrNull() ?: 0) * 1440 + (h.toIntOrNull() ?: 0) * 60 + (min.toIntOrNull() ?: 0)
    }
}
