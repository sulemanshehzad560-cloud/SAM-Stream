package com.openreel.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitlesTest {
    @Test fun cleansYouTubeTitles() {
        assertEquals("The Mask" to 1994, Titles.clean("The Mask (1994) | Full Movie | Jim Carrey"))
        assertEquals("Night of the Living Dead" to 1968, Titles.clean("Night of the Living Dead (1968) - Full Movie HD"))
        assertEquals("Sintel" to null, Titles.clean("Sintel"))
    }

    @Test fun normalizesForMatching() {
        assertTrue(Titles.sameTitle("The General", 1926, "General", 1927))
        assertTrue(Titles.sameTitle("Amélie", null, "Amelie", 2001))
        assertTrue(!Titles.sameTitle("The Mask", 1994, "The Mask", 1961))
    }

    private fun vs(id: String, title: String, year: Int?, level: RightsLevel, playable: Boolean, provider: ProviderType = ProviderType.INTERNET_ARCHIVE) =
        VerifiedSource(
            Source(id = id, provider = provider, providerName = provider.label, title = title, year = year, pageUrl = "u",
                playback = if (provider == ProviderType.FREE_SERVICE) PlaybackKind.EXTERNAL_APP else PlaybackKind.DIRECT_VIDEO, streamRef = id),
            Verdict(level, "r", playable, true),
        )

    @Test fun mergesSourcesIntoCatalogueTitlesAndSortsPlayableFirst() {
        val info = TitleInfo(854, MediaType.MOVIE, "The Mask", 1994, "overview", "poster", null)
        val sources = listOf(
            vs("svc:1", "The Mask", 1994, RightsLevel.AUTHORIZED, false, ProviderType.FREE_SERVICE),
            vs("ia:nold", "Night of the Living Dead", 1968, RightsLevel.OPEN_LICENSE, true),
            vs("yt:nold", "Night Of The Living Dead (1968) Full Movie", null, RightsLevel.OPEN_LICENSE, true, ProviderType.YOUTUBE),
        )
        val titles = Titles.merge(sources, listOf(info))
        assertEquals(2, titles.size)
        // Watchable in-app first
        assertEquals("Night of the Living Dead", titles[0].name)
        assertEquals(2, titles[0].sources.size)
        assertEquals("The Mask", titles[1].name)
        assertEquals(1, titles[1].external.size)
        assertEquals("poster", titles[1].posterUrl)
    }

    @Test fun catalogueTitleWithoutSourcesIsKept() {
        val info = TitleInfo(1, MediaType.MOVIE, "Unknown Film", 2020, null, null, null)
        val t = Titles.merge(emptyList(), listOf(info)).single()
        assertTrue(!t.hasFreeLegalSource)
    }
}
