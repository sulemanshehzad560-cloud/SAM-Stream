package com.samstream.app.domain

enum class ProviderType(val label: String) {
    INTERNET_ARCHIVE("Internet Archive"),
    YOUTUBE("YouTube"),
    FREE_SERVICE("Free streaming service"),
}

/** How a source is watched: inside the app (direct file or embedded official player) or in the provider's own app. */
enum class PlaybackKind { DIRECT_VIDEO, YOUTUBE_EMBED, EXTERNAL_APP }

enum class RightsLevel(val label: String, val emoji: String) {
    AUTHORIZED("Licensed / authorized", "🟢"),
    OPEN_LICENSE("Public domain / open licence", "🟢"),
    UNVERIFIED("Rights unclear — not played", "🟡"),
    BLOCKED("Excluded", "🔴"),
}

enum class MediaType { MOVIE, SERIES }

/** One place a title can be watched, with everything the rights engine needs to decide whether we may play it. */
data class Source(
    /** Stable id, e.g. "ia:night_of_the_living_dead" or "yt:dQw4w9WgXcQ". */
    val id: String,
    val provider: ProviderType,
    val providerName: String,
    val title: String,
    val year: Int? = null,
    val pageUrl: String,
    val playback: PlaybackKind,
    /** Archive identifier, YouTube video id, or external link — depending on [playback]. */
    val streamRef: String? = null,
    val licenseUrl: String? = null,
    /** YouTube's licence field: "creativeCommon" or "youtube". */
    val licenseTag: String? = null,
    /** False when the owner disabled embedding. Null = not reported. */
    val embeddable: Boolean? = null,
    val uploaderId: String? = null,
    val uploaderName: String? = null,
    val collections: List<String> = emptyList(),
    /** ISO country codes; null = no allow-list (available everywhere unless blocked). */
    val allowedCountries: Set<String>? = null,
    val blockedCountries: Set<String> = emptySet(),
    val durationMinutes: Int? = null,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val subjects: List<String> = emptyList(),
    val mediaType: MediaType = MediaType.MOVIE,
)

data class Verdict(
    val level: RightsLevel,
    val reason: String,
    val playableInApp: Boolean,
    val availableInCountry: Boolean,
)

data class VerifiedSource(val source: Source, val verdict: Verdict) {
    val canWatch: Boolean get() = verdict.availableInCountry &&
        (verdict.playableInApp || (verdict.level == RightsLevel.AUTHORIZED && source.playback == PlaybackKind.EXTERNAL_APP))
}

/** Metadata about a title from a catalogue such as TMDB. */
data class TitleInfo(
    val tmdbId: Int,
    val mediaType: MediaType,
    val name: String,
    val year: Int?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val genres: List<String> = emptyList(),
)

/** A movie or series with all the sources we found for it, best first. */
data class Title(
    val key: String,
    val name: String,
    val year: Int?,
    val mediaType: MediaType,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val genres: List<String>,
    val tmdbId: Int?,
    val sources: List<VerifiedSource>,
) {
    val inApp: List<VerifiedSource> get() = sources.filter { it.verdict.playableInApp }
    val external: List<VerifiedSource> get() = sources.filter { it.canWatch && !it.verdict.playableInApp }
    val hasFreeLegalSource: Boolean get() = sources.any { it.canWatch }
}
