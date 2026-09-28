package com.samstream.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class HttpException(val code: Int, message: String) : Exception(message)

/** Tiny JSON-over-HTTPS client with a short in-memory cache. Plain JVM so it also runs in unit tests. */
object Http {
    private data class Cached(val at: Long, val body: String)
    private val cache = ConcurrentHashMap<String, Cached>()
    private const val TTL_MS = 10 * 60 * 1000L

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun url(base: String, params: List<Pair<String, String>>): String =
        base + "?" + params.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        cache[url]?.takeIf { System.currentTimeMillis() - it.at < TTL_MS }?.let { return@withContext JSONObject(it.body) }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "SAMStream/1.0 (Android; +https://github.com/sulemanshehzad560-cloud)")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HttpException(code, "HTTP $code for ${url.substringBefore('?')}")
            cache[url] = Cached(System.currentTimeMillis(), body)
            JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }
}

// ---- small org.json helpers: APIs return a field as a string OR an array of strings ----

fun JSONObject.strOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else when (val v = get(key)) {
        is JSONArray -> if (v.length() == 0) null else v.optString(0)
        else -> v.toString()
    }?.takeIf { it.isNotBlank() }

fun JSONObject.strList(key: String): List<String> =
    if (!has(key) || isNull(key)) emptyList() else when (val v = get(key)) {
        is JSONArray -> (0 until v.length()).mapNotNull { v.optString(it).takeIf(String::isNotBlank) }
        else -> v.toString().split(';').map { it.trim() }.filter { it.isNotEmpty() }
    }

fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
