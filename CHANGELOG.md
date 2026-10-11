# Changelog

## 1.1.0 — 2026-10-11

### Added
- Deep zoom: IIIF tiles of the original loaded on demand (own Canvas renderer), "detail %" badge.
- Search by color (8 swatches), museum autocomplete suggestions.
- Artwork of the day: hero card in the feed and a Glance home-screen widget (pin from the app).
- Immersive slideshow: vertical pager, Ken Burns, blurred backdrop, auto-advance.
- Artist screen (years, biography, all works); "More by the artist" and "Similar in color" rows,
  "On view" badge, parallax and painting-colored accent on the artwork screen.
- HTTP cache for API responses with stale-on-error (no network / 5xx).
- Navigation rail from 600 dp width, compact header on wide screens.

### Fixed
- Search fired on every letter while typing (400 ms pause was shorter than the gap between letters
  on a phone) and a big pull-to-refresh spinner jumped on every new query.
- Dark theme: the artwork title and icons were black on black.
- Zoom gesture produced NaN offsets when fingers were lifted.

### Changed
- Room schema 2 (AutoMigration from 1): original image size and IIIF id stored with favorites.

## 1.0.0 — 2026-10-11

First version: feed with Paging 3, search, collections, artwork screen with shared element transition,
full-screen zoom, favorites in Room, ru/en, dark theme.
