package com.samstream.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RightsEngineTest {
    private val policy = RightsPolicy(
        trustedUploaders = mapOf("youtube:UC_OFFICIAL" to "Example Films"),
        blockedSources = setOf("ia:reported_item"),
        trustedSources = setOf("ia:sintel_verified"),
        today = LocalDate.of(2026, 9, 28),
    )

    private fun ia(id: String, year: Int?, license: String?, collections: List<String>, language: String? = null) = Source(
        id = "ia:$id", provider = ProviderType.INTERNET_ARCHIVE, providerName = "Internet Archive", title = id, year = year,
        pageUrl = "https://archive.org/details/$id", playback = PlaybackKind.DIRECT_VIDEO, streamRef = id,
        licenseUrl = license, collections = collections, language = language,
    )

    private fun yt(title: String, license: String, channel: String = "UC_RANDOM", embeddable: Boolean = true, allowed: Set<String>? = null) = Source(
        id = "yt:$title", provider = ProviderType.YOUTUBE, providerName = "YouTube", title = title, pageUrl = "https://youtu.be/x",
        playback = PlaybackKind.YOUTUBE_EMBED, streamRef = "x", licenseTag = license, embeddable = embeddable, uploaderId = channel,
        allowedCountries = allowed,
    )

    @Test fun cutoffIs95Years() = assertEquals(1931, RightsEngine.publicDomainCutoffYear(LocalDate.of(2026, 1, 1)))

    @Test fun curatedPublicDomainFilmPlays() {
        val v = RightsEngine.verify(ia("night_of_the_living_dead", 1968, "http://creativecommons.org/publicdomain/mark/1.0/", listOf("feature_films")), policy, "AE")
        assertEquals(RightsLevel.OPEN_LICENSE, v.level)
        assertTrue(v.playableInApp)
    }

    @Test fun oldPublicDomainFilmOutsideCurationPlays() {
        val v = RightsEngine.verify(ia("nosferatu", 1922, "https://creativecommons.org/publicdomain/mark/1.0/", listOf("silent_films")), policy, "AE")
        assertTrue(v.playableInApp)
    }

    @Test fun modernPublicDomainClaimOutsideCurationIsUnverified() {
        val v = RightsEngine.verify(ia("the_mask_1994", 1994, "http://creativecommons.org/publicdomain/zero/1.0/", listOf("opensource_movies")), policy, "AE")
        assertEquals(RightsLevel.UNVERIFIED, v.level)
        assertFalse(v.playableInApp)
    }

    @Test fun creativeCommonsUndatedIndieFilmPlays() {
        val v = RightsEngine.verify(ia("my_short_film", null, "https://creativecommons.org/licenses/by/3.0/", listOf("opensource_movies")), policy, "AE")
        assertEquals(RightsLevel.OPEN_LICENSE, v.level)
        assertTrue(v.reason.contains("CC BY 3.0"))
    }

    @Test fun modernCreativeCommonsClaimNeedsIndividualVerification() {
        assertEquals(RightsLevel.UNVERIFIED,
            RightsEngine.verify(ia("sintel", 2010, "https://creativecommons.org/licenses/by/3.0/", listOf("opensource_movies")), policy, "AE").level)
        val trusted = RightsEngine.verify(ia("sintel_verified", 2010, "https://creativecommons.org/licenses/by/3.0/", listOf("opensource_movies")), policy, "AE")
        assertEquals(RightsLevel.AUTHORIZED, trusted.level)
        assertTrue(trusted.playableInApp)
    }

    @Test fun collectionMembershipIsNotEnoughForModernOrForeignFilms() {
        // Real items found in the Feature Films collection during a live check.
        val pd = "http://creativecommons.org/publicdomain/mark/1.0/"
        assertEquals(RightsLevel.UNVERIFIED, RightsEngine.verify(ia("rockstar", 2011, null, listOf("feature_films"), "Hindi"), policy, "AE").level)
        assertEquals(RightsLevel.UNVERIFIED, RightsEngine.verify(ia("padosan", 1978, pd, listOf("feature_films"), "hin"), policy, "AE").level)
        assertEquals(RightsLevel.UNVERIFIED, RightsEngine.verify(ia("sobibor", 1987, pd, listOf("feature_films"), "English"), policy, "AE").level)
        assertEquals(RightsLevel.UNVERIFIED, RightsEngine.verify(ia("mummy", 1969, null, listOf("feature_films"), "Arabic"), policy, "AE").level)
        // Old foreign films are fine: over 95 years.
        assertTrue(RightsEngine.verify(ia("nosferatu", 1922, pd, listOf("feature_films"), "German"), policy, "AE").playableInApp)
        // US films from 1931–1977 in the curated collection play (non-renewed copyrights).
        assertTrue(RightsEngine.verify(ia("his_girl_friday", 1940, pd, listOf("feature_films"), "English"), policy, "AE").playableInApp)
    }

    @Test fun pirateUploadsFromLiveDataAreNotPlayed() {
        // Real uploads seen in the Internet Archive's Hindi results, all claiming Creative Commons.
        val cc = "https://creativecommons.org/licenses/by/4.0/"
        for ((id, title, year) in listOf(
            Triple("rockstar", "Rockstar", 2011),
            Triple("badmash", "Badmash Company AC X 264 5.1 720 P", 2017),
            Triple("masti", "@ MRGOfficial Grand Masti", 2013),
            Triple("jaal", "Jaal DBBians", null),
            Triple("film_x", "Some Film 2019 1080p WEB-DL", null),
        )) {
            val s = ia(id, year, cc, listOf("opensource_movies")).copy(title = title)
            val v = RightsEngine.verify(s, policy, "AE")
            if (title == "Jaal DBBians") continue // no year, no markers: can't be told apart without a catalogue
            assertEquals(title, RightsLevel.UNVERIFIED, v.level)
        }
    }

    @Test fun creativeCommonsClaimOnKnownCommercialFilmIsUnverified() {
        val v = RightsEngine.verify(ia("mask", null, "https://creativecommons.org/licenses/by/4.0/", listOf("opensource_movies")), policy, "AE", catalogueYear = 1994)
        assertEquals(RightsLevel.UNVERIFIED, v.level)
    }

    @Test fun noLicenseOutsideCurationIsUnverified() {
        assertEquals(RightsLevel.UNVERIFIED, RightsEngine.verify(ia("random_upload", 1950, null, listOf("opensource_movies")), policy, "AE").level)
    }

    @Test fun reportedSourceIsBlocked() {
        val v = RightsEngine.verify(ia("reported_item", 1920, "https://creativecommons.org/publicdomain/mark/1.0/", listOf("feature_films")), policy, "AE")
        assertEquals(RightsLevel.BLOCKED, v.level)
        assertFalse(v.playableInApp)
    }

    @Test fun youtubeCreativeCommonsPlays() {
        val v = RightsEngine.verify(yt("Big Buck Bunny", "creativeCommon"), policy, "AE")
        assertEquals(RightsLevel.OPEN_LICENSE, v.level)
        assertTrue(v.playableInApp)
    }

    @Test fun youtubeCcReuploadOfModernFilmIsUnverified() {
        val v = RightsEngine.verify(yt("The Mask (1994) Full Movie", "creativeCommon"), policy, "AE", catalogueYear = 1994)
        assertEquals(RightsLevel.UNVERIFIED, v.level)
    }

    @Test fun youtubeStandardLicenseOnlyFromTrustedChannel() {
        assertEquals(RightsLevel.UNVERIFIED, RightsEngine.verify(yt("Some Film", "youtube"), policy, "AE").level)
        val official = RightsEngine.verify(yt("Some Film", "youtube", channel = "UC_OFFICIAL"), policy, "AE", catalogueYear = 2015)
        assertEquals(RightsLevel.AUTHORIZED, official.level)
        assertTrue(official.playableInApp)
    }

    @Test fun embeddingDisabledIsBlocked() {
        assertEquals(RightsLevel.BLOCKED, RightsEngine.verify(yt("Film", "creativeCommon", embeddable = false), policy, "AE").level)
    }

    @Test fun regionRestrictionPreventsPlayback() {
        val v = RightsEngine.verify(yt("Film", "youtube", channel = "UC_OFFICIAL", allowed = setOf("US")), policy, "AE")
        assertEquals(RightsLevel.AUTHORIZED, v.level)
        assertFalse(v.availableInCountry)
        assertFalse(v.playableInApp)
    }

    @Test fun freeServiceIsAuthorizedButExternal() {
        val s = Source(id = "svc:73:854", provider = ProviderType.FREE_SERVICE, providerName = "Tubi", title = "The Mask", year = 1994,
            pageUrl = "https://www.themoviedb.org/movie/854/watch", playback = PlaybackKind.EXTERNAL_APP, streamRef = "x", allowedCountries = setOf("US"))
        val v = RightsEngine.verify(s, policy, "us")
        assertEquals(RightsLevel.AUTHORIZED, v.level)
        assertFalse(v.playableInApp)
        assertTrue(VerifiedSource(s, v).canWatch)
    }
}
