# OpenReel

**Free & legal movies and series. Search once, watch inside the app.**

OpenReel searches sources that *explicitly permit* free viewing, checks the rights of every source, and plays the
permitted ones in its own player. It never hosts video and never plays a source whose permission is unclear.

## Download

**[⬇️ Download OpenReel.apk (latest)](https://github.com/sulemanshehzad560-cloud/OpenReel/releases/latest/download/OpenReel.apk)**
— open the link in **Chrome** on your Android phone (8.0+), then open the file and allow "Install unknown apps".

## How it works

```
Search "The Mask"
  → Internet Archive  (public-domain & Creative Commons films, no key needed)
  → YouTube           (Creative Commons films + trusted official distributor channels, API key)
  → TMDB / JustWatch  (posters, and FREE / ad-supported services in your country, API key)
      ↓
  Rights engine: every source gets a verdict
      🟢 Licensed / authorized    → play in-app (or open the free service's app)
      🟢 Public domain / open      → play in-app
      🟡 Rights unclear            → never played
      🔴 Excluded                  → hidden (embedding disabled, taken down)
      ↓
  Grouped into titles:  The Mask (1994)
                          ▶ Watch free · Source: …
                          Free on Tubi ↗
```

### The rights engine (`domain/RightsEngine.kt`)
"Publicly available" is never treated as "allowed to embed". A source is playable only when one of these is true:

| Evidence | Verdict |
|---|---|
| Internet Archive item with a Public Domain mark, in the curated `feature_films` collection or published before the US public-domain cutoff (current year − 95) | 🟢 Public domain |
| Creative Commons licence set by the creator (Archive licence URL / YouTube `creativeCommon` licence) | 🟢 Open licence |
| YouTube upload from a **trusted official distributor channel** listed in `rights_policy.json`, embedding enabled | 🟢 Authorized |
| Service listed by TMDB/JustWatch as *free* or *ads* in your country | 🟢 Authorized (opens their app) |
| Public-domain claim on a modern film outside curated collections; CC claim on a known commercial release (e.g. "The Mask (1994) Full Movie" from an unknown channel); standard licence from an unverified channel; no licence at all | 🟡 Not played |
| Embedding disabled by the owner, or listed in `blockedSources` | 🔴 Excluded |

Country restrictions (YouTube region rules, TMDB per-country providers) are applied too. Rights are re-checked
against the provider's latest metadata when you press play.

### Live rights policy & takedowns
`app/src/main/assets/rights_policy.json` is also fetched from this repository's `main` branch at app start, so you can:
- add verified official distributor YouTube channels to `trustedYouTubeChannels` (only after verifying the channel really
  belongs to the rights holder), and
- remove any source immediately by adding its id (e.g. `"ia:some_item"`, `"yt:VIDEO_ID"`) to `blockedSources`

— without shipping an app update. The in-app **Report a rights problem** button opens a pre-filled GitHub issue here.

## Features
- Home: "What do you want to watch?", search, category chips (🍿 Tonight, 💥 Action, 😂 Comedy, 👻 Horror, 👨‍👩‍👧 Family, 🚀 Sci-Fi, 🎞️ Classics, 🇦🇪 Arabic, 🇮🇳 Hindi, 🇬🇧 English, 📺 Series), rotating "Tonight's free movies", Continue watching with progress.
- Search across all sources with one box; results show **▶ Free in app**, **Free on Tubi ↗** or **No free legal source**.
- Title page with every source, its rights badge and the exact reason ("Public domain — in Internet Archive's curated public-domain film collection").
- Full-screen landscape player (Media3/ExoPlayer) with resume; YouTube's official embedded player for YouTube sources.
- Settings: country, optional API keys, "show unverified uploads" (for transparency — still never played), credits.

## Setup (optional keys)
Internet Archive works out of the box. For posters, free services in your country and YouTube films:
- **TMDB**: free key at <https://www.themoviedb.org/settings/api> (v3 key or v4 read token).
- **YouTube Data API v3**: key from Google Cloud Console.

Enter them in the app's Settings, or add repository secrets `TMDB_API_KEY` / `YOUTUBE_API_KEY` so CI bakes them into the APK.

## Build
- CI (`.github/workflows/build-apk.yml`): unit tests, a live Internet Archive check, debug APK artifact, and a GitHub
  Release `v<versionName>` on every push to `main`. Bump `versionName`/`versionCode` in `app/build.gradle.kts` to release a new version.
- Locally: `./gradlew testDebugUnitTest assembleDebug` (JDK 17+, Android SDK).
- Signed release: secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Kotlin · Jetpack Compose · Media3 ExoPlayer · Coil · org.json · minSdk 26 / targetSdk 35.

## Before a Play Store launch
- Google Play's policy on apps that stream third-party content requires that you have rights to what you show; keep the
  rights engine strict and respond to reports quickly. Get a lawyer to review the approach for your target markets.
- Public-domain status is determined under US law; it differs in some countries (e.g. life+70 regimes).
- The free catalogue that is legally embeddable is mostly classic and independent film. Growing it with mainstream titles
  means **partnerships**: licensing deals or official channels of distributors (FilmRise-, Popcornflix-style) added to
  `trustedYouTubeChannels`, or FAST/AVOD partner APIs.
- Respect each API's terms: TMDB attribution (in Settings → Credits), YouTube API Services Terms (embedded player,
  no ad blocking), Internet Archive terms of use.
