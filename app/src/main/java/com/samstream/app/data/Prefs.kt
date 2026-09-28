package com.samstream.app.data

import android.content.Context
import com.samstream.app.BuildConfig
import com.samstream.app.domain.PlaybackKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class Settings(
    val country: String,
    val tmdbKey: String,
    val youtubeKey: String,
    val showUnverified: Boolean,
)

/** Something the user started watching, so it can be resumed from "Continue watching". */
data class Recent(
    val sourceId: String,
    val title: String,
    val thumbnail: String?,
    val playback: PlaybackKind,
    val streamRef: String,
    val providerName: String,
    val rightsNote: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("samstream", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _recents = MutableStateFlow(readRecents())
    val recents: StateFlow<List<Recent>> = _recents.asStateFlow()

    private fun readSettings() = Settings(
        country = sp.getString("country", null) ?: Locale.getDefault().country.ifBlank { "AE" },
        tmdbKey = sp.getString("tmdbKey", null) ?: BuildConfig.TMDB_API_KEY,
        youtubeKey = sp.getString("youtubeKey", null) ?: BuildConfig.YOUTUBE_API_KEY,
        showUnverified = sp.getBoolean("showUnverified", false),
    )

    fun update(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        sp.edit().putString("country", s.country.uppercase()).putString("tmdbKey", s.tmdbKey.trim())
            .putString("youtubeKey", s.youtubeKey.trim()).putBoolean("showUnverified", s.showUnverified).apply()
        _settings.value = s.copy(country = s.country.uppercase(), tmdbKey = s.tmdbKey.trim(), youtubeKey = s.youtubeKey.trim())
    }

    var cachedPolicy: String?
        get() = sp.getString("policy", null)
        set(v) = sp.edit().putString("policy", v).apply()

    private fun readRecents(): List<Recent> = runCatching {
        val arr = JSONArray(sp.getString("recents", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Recent(
                o.getString("sourceId"), o.getString("title"), o.optString("thumbnail").ifBlank { null },
                PlaybackKind.valueOf(o.getString("playback")), o.getString("streamRef"), o.optString("providerName"),
                o.optString("rightsNote"), o.optLong("positionMs"), o.optLong("durationMs"), o.optLong("updatedAt"),
            )
        }
    }.getOrDefault(emptyList())

    fun saveProgress(r: Recent) {
        val finished = r.durationMs > 0 && r.positionMs > r.durationMs * 0.95
        val list = (listOfNotNull(r.takeIf { !finished }) + _recents.value.filter { it.sourceId != r.sourceId }).take(20)
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("sourceId", it.sourceId).put("title", it.title).put("thumbnail", it.thumbnail ?: "")
                .put("playback", it.playback.name).put("streamRef", it.streamRef).put("providerName", it.providerName)
                .put("rightsNote", it.rightsNote).put("positionMs", it.positionMs).put("durationMs", it.durationMs).put("updatedAt", it.updatedAt))
        }
        sp.edit().putString("recents", arr.toString()).apply()
        _recents.value = list
    }

    fun positionFor(sourceId: String): Long = _recents.value.firstOrNull { it.sourceId == sourceId }?.positionMs ?: 0L

    fun removeRecent(sourceId: String) {
        val r = _recents.value.firstOrNull { it.sourceId == sourceId } ?: return
        // Saving it as "finished" drops it from the list.
        saveProgress(r.copy(positionMs = Long.MAX_VALUE, durationMs = 1))
    }
}
