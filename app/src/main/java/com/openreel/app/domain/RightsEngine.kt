package com.openreel.app.domain

import java.time.LocalDate

/**
 * Local rules on top of what providers report.
 * [trustedUploaders] are official distributor channels (e.g. "youtube:UC...") whose own uploads may be embedded.
 * [curatedPublicDomainCollections] are archive collections curated as public domain.
 */
data class RightsPolicy(
    val trustedUploaders: Map<String, String> = emptyMap(),
    val blockedSources: Set<String> = emptySet(),
    val curatedPublicDomainCollections: Set<String> = setOf("feature_films"),
    val today: LocalDate = LocalDate.now(),
)

/**
 * Decides, per source, whether OpenReel may play it:
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
        if (source.embeddable == false && source.playback != PlaybackKind.EXTERNAL_APP) {
            return verdict(RightsLevel.BLOCKED, "The owner has disabled embedding for this video.")
        }
        val cutoff = publicDomainCutoffYear(policy.today)
        // For public-domain claims, any evidence of a recent release date counts.
        val year = catalogueYear ?: source.year
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
                when {
                    isPublicDomain && (curated || !modernCommercial) -> verdict(
                        RightsLevel.OPEN_LICENSE,
                        if (curated) "Public domain — in Internet Archive's curated public-domain film collection."
                        else "Public domain${source.year?.let { " (published $it, before $cutoff)" } ?: ""}.",
                    )
                    isPublicDomain -> verdict(
                        RightsLevel.UNVERIFIED,
                        "Marked public domain by the uploader, but a $year film needs proof (e.g. copyright not renewed).",
                    )
                    isCreativeCommons && knownCommercialRelease && !curated -> verdict(
                        RightsLevel.UNVERIFIED,
                        "Creative Commons claimed for a $catalogueYear commercial film by an unverified uploader.",
                    )
                    isCreativeCommons -> verdict(RightsLevel.OPEN_LICENSE, "${licenseName(lic)} licence set by the creator.")
                    curated -> verdict(RightsLevel.OPEN_LICENSE, "In Internet Archive's curated public-domain Feature Films collection.")
                    else -> verdict(RightsLevel.UNVERIFIED, "No licence information on this upload.")
                }
            }
        }
    }

    fun licenseName(url: String): String {
        val m = Regex("licenses/([a-z\\-]+)/([0-9.]+)").find(url.lowercase()) ?: return "Creative Commons"
        return "CC ${m.groupValues[1].uppercase()} ${m.groupValues[2]}"
    }
}
