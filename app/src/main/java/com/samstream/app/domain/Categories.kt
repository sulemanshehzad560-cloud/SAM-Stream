package com.samstream.app.domain

/**
 * Home-screen rows. [browseCollections] are the Internet Archive collections browsed for the row — mostly the curated
 * public-domain Feature Films collection, which gives real films instead of random clips.
 */
enum class Category(
    val label: String,
    val emoji: String,
    val archiveFilter: String?,
    val youtubeQuery: String,
    val browseCollections: String = "feature_films",
) {
    ACTION("Action", "💥", "subject:(action OR adventure OR western OR war OR swashbuckler OR crime)", "action adventure full movie"),
    COMEDY("Comedy", "😂", "subject:(comedy)", "comedy full movie"),
    HORROR("Horror", "👻", "subject:(horror OR thriller OR mystery)", "horror full movie"),
    FAMILY("Family", "👨‍👩‍👧", "subject:(family OR animation OR cartoon OR children OR kids OR musical)", "family animated movie",
        browseCollections = "feature_films OR animationandcartoons"),
    SCIFI("Sci-Fi", "🚀", "subject:(\"science fiction\" OR sci-fi OR scifi OR fantasy)", "science fiction film"),
    CLASSICS("Classics", "🎞️", null, "classic film", browseCollections = "feature_films OR silent_films OR film_noir"),
    ARABIC("Arabic", "🇦🇪", "language:(arabic OR ara)", "فيلم كامل", browseCollections = "feature_films OR opensource_movies"),
    HINDI("Hindi", "🇮🇳", "language:(hindi OR hin)", "hindi full movie", browseCollections = "feature_films OR opensource_movies"),
    ENGLISH("English", "🇬🇧", "language:(english OR eng)", "full movie english"),
    SERIES("Series", "📺", null, "tv series full episode", browseCollections = "classic_tv"),
}
