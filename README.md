# Galleria 🖼

**An Android art gallery: 60,000+ public-domain works from the Art Institute of Chicago — endless feed, search, a smooth "tile → artwork" transition, pinch-to-zoom and offline favorites.**

![CI](https://github.com/vihrea1337/galleria/actions/workflows/ci.yml/badge.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue)

<p align="center">
  <img src="docs/screenshots/feed.jpg" width="24%" alt="Feed: staggered grid of artworks with collection chips" />
  <img src="docs/screenshots/detail.jpg" width="24%" alt="Artwork screen: image, facts and the museum's description" />
  <img src="docs/screenshots/search.jpg" width="24%" alt="Search results for Hokusai" />
  <img src="docs/screenshots/fullscreen.jpg" width="24%" alt="Full-screen viewer with zoom" />
</p>
<p align="center">
  <img src="docs/screenshots/feed-dark.jpg" width="24%" alt="Feed in dark theme" />
  <img src="docs/screenshots/detail-dark.jpg" width="24%" alt="Artwork screen in dark theme, background tinted by the painting" />
  <img src="docs/screenshots/favorites.jpg" width="24%" alt="Favorites" />
</p>

## Features

- 🧱 **Endless staggered feed** (`LazyVerticalStaggeredGrid` + Paging 3) — tiles have the right height *before* the image loads (aspect ratio comes from the API), so the feed never jumps; a tiny blurred preview (`lqip`) fills the tile until the real image fades in
- 🔎 **Search with debounce** (400 ms) — stale requests are cancelled with `flatMapLatest`; clearing the field goes back instantly; collections (Impressionism, Japan, Portraits…) as chips
- ✨ **Shared element transition** — the tile image flies into place on the artwork screen; the already-downloaded thumbnail is reused as the placeholder for the large image, so there is no blank frame
- 🎨 **Color from data** — the artwork screen is tinted with the painting's dominant color (HSL from the API → ARGB), tuned for both light and dark theme
- 🔍 **Full-screen viewer** — pinch to zoom (up to 5×) around the fingers, pan within the image bounds, double-tap to zoom into a point, swipe down to close
- ♥ **Favorites in Room** — stored whole (title, image URLs, color, preview), so they open without network; remove with **Undo**
- ⚠️ Honest states: skeleton on first load, "No connection · Retry" for the page and the footer, "Nothing found", and the end of the feed (the API serves at most 1,000 results per query)
- 📱 Bottom bar on phones, navigation rail on tablets and in landscape (`NavigationSuiteScaffold`); 2–5 columns by window width
- 🌓 "Museum" theme — warm paper and burgundy, serif headings, dark theme; edge-to-edge, splash screen, adaptive + monochrome icon
- 🌐 English and Russian, per-app language on Android 13+

## Tech stack

| Category | Choice |
|---|---|
| UI | Jetpack Compose · Material 3 · Navigation Compose (type-safe routes) · Shared element transitions · Material 3 Adaptive |
| Architecture | MVVM + UDF: stateless screens `Screen(state, callbacks)`, ViewModels expose `StateFlow` / `Flow<PagingData>` |
| DI | Hilt |
| Async | Kotlin Coroutines + Flow |
| Paging | Paging 3 (`PagingSource` keyed by page number, `cachedIn(viewModelScope)`) |
| Images | Coil 3 on the same OkHttp client as the API (memory + disk cache, crossfade) |
| Network | Retrofit + OkHttp + kotlinx.serialization |
| Storage | Room (favorites) · `SavedStateHandle` (search and collection survive process death) |
| Quality | 27 JVM tests (mapper on real API responses, `PagingSource` with `TestPager`, ViewModels with virtual time and Turbine) · 6 Compose UI tests on an emulator in CI · Android Lint · R8 in release |

## Architecture

One `app` module with layers as packages — the focus of this project is UI (multi-module setup is shown in [SkyCast](https://github.com/vihrea1337/skycast)).

```mermaid
graph LR
    subgraph ui
        Feed[FeedScreen] --> FeedVM[FeedViewModel]
        Detail[DetailScreen] --> DetailVM[DetailViewModel]
        Fav[FavoritesScreen] --> FavVM[FavoritesViewModel]
    end
    FeedVM & DetailVM & FavVM --> Repo[ArtworkRepository]
    Repo --> Pager[Pager → ArtworkPagingSource] --> Api[ArticApi · Retrofit]
    Repo --> Api
    Repo --> Dao[FavoriteDao · Room]
    Api -. DTO .-> Mapper[ArtworkMapper · pure functions] -. Artwork .-> Repo
```

- **The model knows nothing about the API.** `ArtworkMapper` (pure Kotlin, tested with plain JUnit) builds IIIF image URLs, clamps extreme aspect ratios (scrolls, panoramas), converts HSL to ARGB, decodes the `data:` preview and turns the museum's HTML description into text.
- **Two navigation levels**: `Home` (tabs Gallery · Favorites) and `Detail(id)`. Tabs are state of `Home`, not destinations — both feeds live in the same animation scope, so the shared element works from either, and scroll positions survive tab switches.
- **The artwork screen is instant**: the tapped `Artwork` is remembered in the repository, so the image, title and color show immediately; only the details (facts, description) are fetched.

## Decisions & trade-offs

| Decision | Why |
|---|---|
| Page key = API page number, `initialLoadSize = pageSize` | Paging asks for 3× the page size on the first load by default; with page-number keys page 2 would then overlap page 1 and the feed would show duplicates |
| Stop at 990 results | The API rejects `page × limit > 1000`; the source never asks for it, and the UI says "That's all — refine the search" |
| Explicit Coil `memoryCacheKey` | Coil 3's default memory key includes the requested size, so the detail screen could not find the feed thumbnail by URL; an explicit key makes the thumbnail a real placeholder |
| Favorites stored whole, not as ids | Opens offline and doesn't depend on what the API returns later |
| Search + collection in `SavedStateHandle` | Survive rotation **and** process death |
| No offline cache for the feed | Galleria is about UI; offline-first sync is already shown in my other projects (SkyCast, Expenses, DevLog) |

## Build

```bash
./gradlew assembleDebug                 # debug APK
./gradlew test                          # JVM tests
./gradlew connectedDebugAndroidTest     # Compose UI tests (emulator or device)
./gradlew assembleRelease               # R8; signed if keystore.properties is present
```

Requires JDK 17+ and Android SDK 36. No API keys needed.

## Data & credits

Artwork data and images: [Art Institute of Chicago API](https://api.artic.edu/docs/). Only works in the **public domain** are shown (`is_public_domain = true`); the app identifies itself with the `AIC-User-Agent` header as the museum asks.

## License

[MIT](LICENSE)
