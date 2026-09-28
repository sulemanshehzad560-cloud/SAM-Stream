package com.openreel.app.domain

enum class Category(val label: String, val emoji: String, val archiveFilter: String, val youtubeQuery: String) {
    ACTION("Action", "💥", "subject:(action OR adventure OR western OR war OR swashbuckler)", "action adventure full movie"),
    COMEDY("Comedy", "😂", "subject:(comedy)", "comedy full movie"),
    HORROR("Horror", "👻", "subject:(horror OR thriller)", "horror full movie"),
    FAMILY("Family", "👨‍👩‍👧", "subject:(family OR animation OR cartoon OR children OR kids)", "family animated movie"),
    SCIFI("Sci-Fi", "🚀", "subject:(\"science fiction\" OR sci-fi OR scifi)", "science fiction film"),
    CLASSICS("Classics", "🎞️", "collection:(feature_films OR film_noir OR silent_films)", "classic film"),
    ARABIC("Arabic", "🇦🇪", "language:(arabic OR ara)", "فيلم كامل"),
    HINDI("Hindi", "🇮🇳", "language:(hindi OR hin)", "hindi full movie"),
    ENGLISH("English", "🇬🇧", "language:(english OR eng)", "full movie english"),
    SERIES("Series", "📺", "collection:(classic_tv)", "tv series full episode"),
}
