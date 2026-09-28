package com.samstream.app.domain

import com.samstream.app.net.ArchiveApi
import com.samstream.app.net.Http
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Hits the real Internet Archive API. Runs only when SAMSTREAM_LIVE_TESTS=1 (CI does this as a separate step). */
class LiveApiTest {
    private val enabled = System.getenv("SAMSTREAM_LIVE_TESTS") == "1"

    @Test fun archiveSearchFindsPlayablePublicDomainFilm() = runBlocking {
        assumeTrue(enabled)
        val sources = ArchiveApi.parseSearch(Http.getJson(ArchiveApi.searchUrl("night of the living dead")))
        println("Archive returned ${sources.size} sources: " + sources.take(5).joinToString { "${it.id} ${it.year} ${it.licenseUrl} ${it.collections}" })
        assertTrue("no results", sources.isNotEmpty())
        val verified = sources.map { it to RightsEngine.verify(it, RightsPolicy(), "AE") }
        verified.take(10).forEach { (s, v) -> println("${s.id}: ${v.level} — ${v.reason}") }
        val playable = verified.firstOrNull { it.second.playableInApp }
        assertNotNull("nothing playable", playable)
        val id = playable!!.first.streamRef!!
        val stream = ArchiveApi.parseStream(id, Http.getJson(ArchiveApi.metadataUrl(id)))
        println("Stream for $id: $stream")
        assertNotNull("no mp4 stream", stream)
    }

    @Test fun browseHomeReturnsTitles() = runBlocking {
        assumeTrue(enabled)
        fun playable(category: Category?): List<Title> {
            val url = ArchiveApi.searchUrl(null, category?.archiveFilter, rows = 60, collections = category?.browseCollections ?: "feature_films")
            val sources = runCatching { runBlocking { ArchiveApi.parseSearch(Http.getJson(url)) } }.getOrDefault(emptyList())
            val verified = sources.map { VerifiedSource(it, RightsEngine.verify(it, RightsPolicy(), "AE")) }
            return Titles.merge(verified, emptyList()).filter { it.inApp.isNotEmpty() }
        }
        for (c in Category.entries) {
            val t = playable(c)
            println("Category ${c.label}: ${t.size} playable, e.g. " + t.take(5).joinToString { "${it.name} (${it.year})" })
        }
        val home = playable(null)
        println("Home: ${home.size} playable titles, e.g. " + home.take(10).joinToString { "${it.name} (${it.year})" })
        assertTrue(home.size >= 20)
    }
}
