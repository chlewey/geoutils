# CLAUDE.md

Geo-Utils: an Android app for working with KML files and other geographic data and for making maps in different projections. It reprojects an equirectangular bitmap base map (e.g. NASA Blue Marble) and turns KML geometry into SVG. It is a single-activity Jetpack Compose app with a splash screen. See `README.md` and `ROADMAP.md`.

## Layout

- `app/` — the only module (Kotlin under `app/src/main/java/net`; bundled data in `app/src/main/assets`; unit tests in `app/src/test`).
  - `geometry/` — projections, clipping and geodesy; plain Kotlin with no Android imports, so it is unit-tested on the JVM.
  - `domain/render/` — `SceneBuilder` (projected scene), `SvgWriter`; `ui/` — Compose screens.
- `docs/ADDING_A_PROJECTION.md` — how to add a projection (normal-aspect formulas, catalog entry, tests).
- `site/` — landing page for https://geoutils.ilecto.app (Cloudflare Worker with static assets; deploy with `npx wrangler deploy` from `site/`).
- `keystore/` — signing material location; never commit real keystores or passwords.
- `.github/` — CI: build, lint and unit tests on pushes to `main` and on pull requests.

## Build

Gradle wrapper, Kotlin DSL, version catalog.

```
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
./gradlew testDebugUnitTest    # JVM unit tests
./gradlew lintDebug
```

- compileSdk/targetSdk 36, minSdk 26.
- Stack: Jetpack Compose, Hilt (dependency injection), Room (local storage), kotlinx.serialization, KSP.
- Room schemas are exported by the Room Gradle plugin: schema changes need a migration.
- `settings.gradle.kts` uses `FAIL_ON_PROJECT_REPOS`; declare repositories there only.

## Conventions

- Keep the single-activity, Compose-only structure; add screens as composables, not new activities.
- Roadmap work goes on `develop`; `main` gets it through pull requests. Mark items done in `ROADMAP.md` as they land.
- New projections are registered in `ProjectionCatalog`; `ProjectionsTest` round-trips every catalog entry automatically.
- The Earth is a sphere (radius 6 371 km); coordinates are (lon, lat) in degrees, as in KML.
- Don't bundle third-party data of unknown license; bundled data must be public domain or openly licensed, with attribution.
