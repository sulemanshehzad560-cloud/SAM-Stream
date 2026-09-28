package com.samstream.app.domain

import com.samstream.app.net.ArchiveApi
import com.samstream.app.net.TmdbApi
import com.samstream.app.net.YouTubeApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    @Test fun archiveSearchDocs() {
        val json = JSONObject("""
            {"response":{"numFound":2,"docs":[
              {"identifier":"night_of_the_living_dead","title":"Night of the Living Dead","year":"1968",
               "licenseurl":"http://creativecommons.org/publicdomain/mark/1.0/","collection":["feature_films","SciFi_Horror"],
               "subject":["horror","zombies"],"runtime":"1:35:44","description":"<p>Classic &amp; creepy</p>"},
              {"identifier":"sintel","title":"Sintel","date":"2010-09-27T00:00:00Z","licenseurl":"https://creativecommons.org/licenses/by/3.0/",
               "collection":"opensource_movies","subject":"animation; blender","runtime":"14:48"}
            ]}}
        """.trimIndent())
        val s = ArchiveApi.parseSearch(json)
        assertEquals(2, s.size)
        assertEquals("ia:night_of_the_living_dead", s[0].id)
        assertEquals(1968, s[0].year)
        assertEquals(95, s[0].durationMinutes)
        assertEquals("Classic & creepy", s[0].description)
        assertEquals(listOf("feature_films", "SciFi_Horror"), s[0].collections)
        assertEquals(2010, s[1].year)
        assertEquals(listOf("opensource_movies"), s[1].collections)
        assertEquals(listOf("animation", "blender"), s[1].subjects)
        assertEquals(14, s[1].durationMinutes)
    }

    @Test fun archiveSearchUrlEscapesQuery() {
        val url = ArchiveApi.searchUrl("The Mask!", "subject:(comedy)")
        assertTrue(url, url.contains("title%3A%28The+AND+Mask%29"))
        assertTrue(url.contains("subject%3A%28comedy%29"))
    }

    @Test fun archivePicksH264Mp4() {
        val json = JSONObject("""
            {"metadata":{"identifier":"x","licenseurl":"http://creativecommons.org/publicdomain/mark/1.0/","collection":"feature_films"},
             "files":[{"name":"x.ogv","format":"Ogg Video"},{"name":"x_512kb.mp4","format":"512Kb MPEG4","size":"100"},
                      {"name":"My Film.mp4","format":"h.264","size":"900","length":"5400.5"},{"name":"x.avi","format":"Cinepack"}]}
        """.trimIndent())
        val st = ArchiveApi.parseStream("x", json)!!
        assertEquals("https://archive.org/download/x/My%20Film.mp4", st.url)
        assertEquals(90, st.durationMinutes)
        assertEquals(listOf("feature_films"), st.collections)
        assertNull(ArchiveApi.parseStream("y", JSONObject("""{"files":[{"name":"a.ogv"}]}""")))
    }

    @Test fun youtubeVideos() {
        val json = JSONObject("""
            {"items":[{"id":"aqz-KE-bpKQ","snippet":{"title":"Big Buck Bunny 60fps 4K","channelId":"UCSMOQeBJ2RAnuFungnQOxLg","channelTitle":"Blender",
              "thumbnails":{"high":{"url":"https://i.ytimg.com/vi/aqz-KE-bpKQ/hqdefault.jpg"}}},
              "contentDetails":{"duration":"PT10M35S","regionRestriction":{"blocked":["cn"]}},"status":{"embeddable":true,"license":"creativeCommon"}}]}
        """.trimIndent())
        val v = YouTubeApi.parseVideos(json).single()
        assertEquals("yt:aqz-KE-bpKQ", v.id)
        assertEquals("creativeCommon", v.licenseTag)
        assertEquals(true, v.embeddable)
        assertEquals(setOf("CN"), v.blockedCountries)
        assertNull(v.allowedCountries)
        assertEquals(10, v.durationMinutes)
        assertEquals(96, YouTubeApi.isoMinutes("PT1H36M12S"))
    }

    @Test fun tmdbSearchAndFreeProviders() {
        val search = JSONObject("""{"results":[
            {"id":854,"media_type":"movie","title":"The Mask","release_date":"1994-07-29","poster_path":"/p.jpg","overview":"o"},
            {"id":1,"media_type":"person","name":"Jim Carrey"},
            {"id":2,"media_type":"tv","name":"The Mask: Animated Series","first_air_date":"1995-08-12"}]}""")
        val infos = TmdbApi.parseSearch(search)
        assertEquals(2, infos.size)
        assertEquals(MediaType.SERIES, infos[1].mediaType)
        assertEquals("https://image.tmdb.org/t/p/w342/p.jpg", infos[0].posterUrl)

        val details = JSONObject("""{"id":854,"title":"The Mask","release_date":"1994-07-29","genres":[{"id":35,"name":"Comedy"}],
            "watch/providers":{"results":{"US":{"link":"https://www.themoviedb.org/movie/854-the-mask/watch?locale=US",
              "free":[{"provider_id":73,"provider_name":"Tubi TV","logo_path":"/t.jpg"}],
              "ads":[{"provider_id":300,"provider_name":"Pluto TV"},{"provider_id":73,"provider_name":"Tubi TV"}],
              "flatrate":[{"provider_id":8,"provider_name":"Netflix"}]}}}}""")
        val full = TmdbApi.parseTitle(details, MediaType.MOVIE)!!
        assertEquals(listOf("Comedy"), full.genres)
        val svc = TmdbApi.parseFreeProviders(details, full, "us")
        assertEquals(listOf("Tubi TV", "Pluto TV"), svc.map { it.providerName })
        assertTrue(svc.all { it.playback == PlaybackKind.EXTERNAL_APP })
        assertTrue(TmdbApi.parseFreeProviders(details, full, "AE").isEmpty())
    }

    @Test fun pastedKeysLoseInvisibleCharacters() {
        val key = "abcdef0123456789abcdef0123456789"
        assertEquals(key, com.samstream.app.data.cleanKey(" \u200B${key.take(10)}\u00A0${key.drop(10)}\n"))
        val token = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJ4In0.sig-_part"
        assertEquals(token, com.samstream.app.data.cleanKey("$token\u200B "))
    }

    @Test fun tmdbDiscoverFreeMovies() {
        val (url, headers) = TmdbApi.discoverFreeUrl("abcdef0123456789abcdef0123456789", "ae")
        assertTrue(url.contains("watch_region=AE"))
        assertTrue(url.contains("with_watch_monetization_types=free%7Cads"))
        assertTrue(headers.isEmpty())
        val list = TmdbApi.parseDiscover(JSONObject("""{"results":[{"id":11,"title":"Star Wars","release_date":"1977-05-25","poster_path":"/p.jpg"},{"id":0,"title":"Bad"}]}"""))
        assertEquals(1, list.size)
        assertEquals(1977, list[0].year)
        assertEquals(MediaType.MOVIE, list[0].mediaType)
    }
}
