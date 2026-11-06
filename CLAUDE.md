# CLAUDE.md

Geo-Utils: an Android app for working with KML files and other geographic data and for making maps in different projections. It reprojects an equirectangular bitmap base map (e.g. NASA Blue Marble) and turns KML geometry into SVG. It is a single-activity Jetpack Compose app with a splash screen. See `README.md` and `ROADMAP.md`.

## Layout

- `app/` — the only module (Kotlin under `app/src/main/java/net`; bundled data in `app/src/main/assets`; unit tests in `app/src/test`).
- `keystore/` — signing material location; never commit real keystores or passwords.
- `.github/` — CI.

## Build

Gradle wrapper, Kotlin DSL, version catalog.

```
./gradlew assembleDebug      # Windows: gradlew.bat assembleDebug
./gradlew test
```

- compileSdk/targetSdk 36, minSdk 26.
- Stack: Jetpack Compose, Hilt (dependency injection), Room (local storage), kotlinx.serialization, KSP.
- Room schemas are exported by the Room Gradle plugin: schema changes need a migration.
- `settings.gradle.kts` uses `FAIL_ON_PROJECT_REPOS`; declare repositories there only.

## Conventions

- Keep the single-activity, Compose-only structure; add screens as composables, not new activities.
