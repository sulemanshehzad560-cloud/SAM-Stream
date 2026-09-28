package com.samstream.app.domain

import java.time.LocalDate

/**
 * Local rules on top of what providers report.
 * [trustedUploaders] are official distributor channels (e.g. "youtube:UC...") whose own uploads may be embedded.
 * [curatedPublicDomainCollections] are archive collections curated as public domain.
 */
data class RightsPolicy(
    val trustedUploaders: Map<String, String> = emptyMap(),
    val blockedSources: Set<String> = emptySet(),
    /** Individually verified sources (e.g. "ia:ElephantsDream") — modern open-licence films confirmed to come from their creators. */
    val trustedSources: Set<String> = emptySet(),
    val curatedPublicDomainCollections: Set<String> = setOf("feature_films"),
    val today: LocalDate = LocalDate.now(),
)

/**
 * Decides, per source, whether SAM Stream may play it:
 *  🟢 AUTHORIZED     — official distributor / licensed free service
 *  🟢 OPEN_LICENSE   — public domain or an open licence (Creative Commons)
 *  🟡 UNVERIFIED     — publicly visible but permission is unclear → never played
 *  🔴 BLOCKED        — embedding disabled, reported or otherwise excluded
 *
 * Being publicly viewable is never enough on its own: every playable verdict names the permission it relies on.
 */
object RightsEngine {

    /** In the US, films published before this year are in the public domain (95-year term). */
    fun publicDomainCutoffYear(today: LocalDate): Int = today.year - 95

    /** Release-group / rip markers that legitimate rights holders don't put in titles. */
    private val piracyMarkers = Regex(
        "(?i)(?<![a-z0-9])(4 ?80 ?p|7 ?20 ?p|10 ?80 ?p|2160 ?p|x ?26[45]|h\\.? ?26[45]|hevc|hd ?rip|dvd ?rip|br ?rip|blu-?ray|web-?dl|web-?rip|hdtv|hd ?cam|cam ?rip|dvd ?scr|ac3|aac|5\\.1|e-?subs?|dual audio|dubbed|yts|yify|rarbg|torrent)(?![a-z0-9])|(^|\\s)@\\w",
    )

    fun looksPirated(source: Source): Boolean =
        piracyMarkers.containsMatchIn(source.title) || piracyMarkers.containsMatchIn(source.streamRef.orEmpty().replace('_', ' ').replace('.', ' '))

    /**
     * @param catalogueYear release year of the matching commercial title (e.g. from TMDB, or a year in the upload's title).
     *   Used to catch re-uploads of modern films that falsely claim an open licence.
     */
    fun verify(source: Source, policy: RightsPolicy, country: String, catalogueYear: Int? = null): Verdict {
        val cc = country.uppercase()
        val available = (source.allowedCountries == null || cc in source.allowedCountries) && cc !in source.blockedCountries
        fun verdict(level: RightsLevel, reason: String) = Verdict(
            level = level,
            reason = reason + if (!available) " Not available in your country ($cc)." else "",
            playableInApp = available && source.playback != PlaybackKind.EXTERNAL_APP && source.streamRef != null &&
                (level == RightsLevel.AUTHORIZED || level == RightsLevel.OPEN_LICENSE),
            availableInCountry = available,
        )

        if (source.id in policy.blockedSources) return verdict(RightsLevel.BLOCKED, "Removed after a rights report.")
        if (source.id in policy.trustedSources) return verdict(RightsLevel.AUTHORIZED, "Individually verified by SAM Stream: released by the rights holder.")
        if (source.embeddable == false && source.playback != PlaybackKind.EXTERNAL_APP) {
            return verdict(RightsLevel.BLOCKED, "The owner has disabled embedding for this video.")
        }
        val cutoff = publicDomainCutoffYear(policy.today)
        // For public-domain claims, any evidence of a recent release date counts.
        val year = catalogueYear ?: source.year ?: Titles.clean(source.title).second
        val modernCommercial = year != null && year >= cutoff
        // For Creative Commons claims only an external catalogue match (a known commercial release) counts:
        // new independent films are legitimately CC-licensed by their creators.
        val knownCommercialRelease = catalogueYear != null && catalogueYear >= cutoff

        return when (source.provider) {
            ProviderType.FREE_SERVICE -> verdict(
                RightsLevel.AUTHORIZED,
                "Offered free by ${source.providerName} in ${cc} (listing via TMDB/JustWatch). Opens in their app or website.",
            )

            ProviderType.YOUTUBE -> {
                val trusted = source.uploaderId?.let { policy.trustedUploaders["youtube:$it"] }
                when {
                    trusted != null -> verdict(RightsLevel.AUTHORIZED, "Uploaded by the official channel of $trusted; embedding enabled.")
                    looksPirated(source) -> verdict(RightsLevel.UNVERIFIED, "The title looks like a ripped copy (release-group markers), not an upload by the rights holder.")
                    source.licenseTag == "creativeCommon" && knownCommercialRelease -> verdict(
                        RightsLevel.UNVERIFIED,
                        "A $catalogueYear commercial film marked Creative Commons by an unverified channel — probably not the rights holder.",
                    )
                    source.licenseTag == "creativeCommon" -> verdict(
                        RightsLevel.OPEN_LICENSE,
                        "Creative Commons (CC BY) licence chosen by the uploader; embedding enabled.",
                    )
                    else -> verdict(RightsLevel.UNVERIFIED, "Standard YouTube licence from a channel that is not a verified distributor.")
                }
            }

            ProviderType.INTERNET_ARCHIVE -> {
                val lic = source.licenseUrl?.lowercase().orEmpty()
                val curated = source.collections.any { it.lowercase() in policy.curatedPublicDomainCollections.map(String::lowercase) }
                val isPublicDomain = "publicdomain" in lic
                val isCreativeCommons = "creativecommons.org/licenses" in lic
                val old = year != null && year < cutoff
                val foreign = !isEnglish(source.language)
                when {
                    !curated && looksPirated(source) -> verdict(
                        RightsLevel.UNVERIFIED,
                        "The upload looks like a ripped copy (release-group markers), not a release by the rights holder.",
                    )
                    old && (isPublicDomain || isCreativeCommons || curated) -> verdict(
                        RightsLevel.OPEN_LICENSE,
                        "Public domain: published in $year, more than 95 years ago.",
                    )
                    // The US restored copyright in foreign films in 1996 (URAA), even if formalities were missed.
                    foreign -> verdict(
                        RightsLevel.UNVERIFIED,
                        "Non-US film${year?.let { " from $it" } ?: " of unknown date"} — likely still under copyright; needs confirmation from the rights holder.",
                    )
                    // Since 1978 works are protected without registration or notice, so an uploader's claim isn't proof.
                    year != null && year >= 1978 -> verdict(
                        RightsLevel.UNVERIFIED,
                        "A $year film is protected by copyright automatically; the uploader's ${if (isCreativeCommons) "Creative Commons" else "public-domain"} claim isn't proof.",
                    )
                    curated && (isPublicDomain || lic.isEmpty()) -> verdict(
                        RightsLevel.OPEN_LICENSE,
                        "US film${year?.let { " from $it" } ?: ""} in Internet Archive's public-domain Feature Films collection (copyright not renewed or published without notice).",
                    )
                    isCreativeCommons && knownCommercialRelease -> verdict(
                        RightsLevel.UNVERIFIED,
                        "Creative Commons claimed for a $catalogueYear commercial film by an unverified uploader.",
                    )
                    isCreativeCommons && modernCommercial -> verdict(
                        RightsLevel.UNVERIFIED,
                        "Creative Commons claimed for a $year film by an unverified uploader — not played until the creator is confirmed.",
                    )
                    isCreativeCommons -> verdict(RightsLevel.OPEN_LICENSE, "${licenseName(lic)} licence set by the creator.")
                    isPublicDomain -> verdict(RightsLevel.UNVERIFIED, "Marked public domain by the uploader, but the release date can't be confirmed.")
                    else -> verdict(RightsLevel.UNVERIFIED, "No licence information on this upload.")
                }
            }
        }
    }

    private val englishNames = setOf("english", "eng", "en", "en-us", "en-gb", "english (us)", "english (uk)")

    /** Unknown language is treated as English: most Internet Archive film items without a language field are US films. */
    fun isEnglish(language: String?): Boolean = language.isNullOrBlank() || language.trim().lowercase() in englishNames

    fun licenseName(url: String): String {
        val m = Regex("licenses/([a-z\\-]+)/([0-9.]+)").find(url.lowercase()) ?: return "Creative Commons"
        return "CC ${m.groupValues[1].uppercase()} ${m.groupValues[2]}"
    }
}
