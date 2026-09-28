package com.samstream.app.domain

import java.text.Normalizer

object Titles {
    private val yearInText = Regex("(?<![0-9])(19[0-9]{2}|20[0-9]{2})(?![0-9])")
    private val noise = Regex(
        "\\b(full movie|full film|full length|free movie|movie|film|hd|4k|1080p|720p|remastered|restored|colou?rized|english|subtitles?|subbed|dubbed|official)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** "The Mask (1994) | Full Movie | Jim Carrey" → ("The Mask", 1994). */
    fun clean(raw: String): Pair<String, Int?> {
        val year = yearInText.find(raw)?.value?.toInt()
        var t = raw.split('|', '•', '–', '—', '[').first()
        t = t.replace(Regex("\\(.*?\\)"), " ")
        t = t.replace(Regex("\\s-\\s.*$"), " ")
        t = t.replace(noise, " ")
        t = yearInText.replace(t, " ")
        t = t.replace(Regex("\\s+"), " ").trim(' ', ':', '-', ',', '.')
        return (t.ifEmpty { raw.trim() }) to year
    }

    /** Key used to decide that two sources are the same title. */
    fun normalize(name: String): String {
        val ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return ascii.lowercase()
            .replace("&", " and ")
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("^(the|a|an) "), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun sameTitle(aName: String, aYear: Int?, bName: String, bYear: Int?): Boolean =
        normalize(aName) == normalize(bName) && (aYear == null || bYear == null || kotlin.math.abs(aYear - bYear) <= 1)

    private val order = compareBy<VerifiedSource>(
        { !it.verdict.playableInApp },
        { !it.canWatch },
        { it.verdict.level.ordinal },
        { it.source.provider.ordinal },
    )

    /**
     * Groups verified sources (and optional catalogue entries) into titles.
     * Catalogue info wins for name/poster/overview; sources that match no catalogue entry form their own titles.
     */
    fun merge(sources: List<VerifiedSource>, catalogue: List<TitleInfo>): List<Title> {
        val titles = mutableListOf<Title>()
        val used = BooleanArray(sources.size)
        for (info in catalogue) {
            val matched = sources.indices.filter { i ->
                !used[i] && sameTitle(clean(sources[i].source.title).first, sources[i].source.year ?: clean(sources[i].source.title).second, info.name, info.year)
            }
            matched.forEach { used[it] = true }
            val own = sources.filterIndexed { i, _ -> i in matched }
            titles += Title(
                key = "tmdb:${info.mediaType}:${info.tmdbId}", name = info.name, year = info.year, mediaType = info.mediaType,
                overview = info.overview, posterUrl = info.posterUrl ?: own.firstNotNullOfOrNull { it.source.thumbnailUrl },
                backdropUrl = info.backdropUrl, genres = info.genres, tmdbId = info.tmdbId, sources = own.sortedWith(order),
            )
        }
        val rest = sources.indices.filter { !used[it] }.map { sources[it] }
        val groups = mutableListOf<MutableList<VerifiedSource>>()
        for (vs in rest) {
            val (name, y) = clean(vs.source.title)
            val year = vs.source.year ?: y
            val g = groups.firstOrNull { g -> val f = g.first(); sameTitle(clean(f.source.title).first, f.source.year ?: clean(f.source.title).second, name, year) }
            if (g != null) g += vs else groups += mutableListOf(vs)
        }
        for (g in groups) {
            val sorted = g.sortedWith(order)
            val first = sorted.first().source
            val (name, y) = clean(first.title)
            titles += Title(
                key = "src:${first.id}", name = name, year = first.year ?: y, mediaType = first.mediaType,
                overview = sorted.firstNotNullOfOrNull { it.source.description?.takeIf(String::isNotBlank) },
                posterUrl = sorted.firstNotNullOfOrNull { it.source.thumbnailUrl }, backdropUrl = null,
                genres = first.subjects.take(3), tmdbId = null, sources = sorted,
            )
        }
        // Titles you can actually watch come first; catalogue order (relevance) is kept within each group.
        return titles.sortedBy { if (it.inApp.isNotEmpty()) 0 else if (it.hasFreeLegalSource) 1 else 2 }
    }
}
