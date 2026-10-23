# Geo-Utils

An Android app for working with KML files and other geographic data, and for
making maps in different projections.

It's a single-activity app (Jetpack Compose) with a splash screen during
loading, using Hilt for dependency injection and Room for local storage.

See [ROADMAP.md](ROADMAP.md) for the planned milestones.

## What it does

- **Project a bitmap:** take an equirectangular base map (for example NASA's
  Blue Marble) and reproject it.
- **Project vector data:** take a KML file and produce an SVG of its geometry
  in the chosen projection.
- **Combine both:** base map + KML gives either
  - a projected bitmap with the shapes drawn onto it, or
  - a projected vector file (SVG) with the projected bitmap embedded.

## Formats

| Role   | Formats                                                                 |
|--------|-------------------------------------------------------------------------|
| Input  | Equirectangular bitmaps (PNG, JPEG, WebP), KML. Later: other projections, icons, overlays. |
| Output | SVG, PNG, JPEG, WebP, at different scales and projections.             |

## Projections

Planned initial set (output first; input in equirectangular only):

- Orthographic (globe view)
- Equirectangular (plate carrée)
- Mercator (web Mercator first; spherical "true" Mercator later)
- Cylindrical equal-area (Lambert, Behrmann, Gall–Peters, Hobo–Dyer)
- Central cylindrical
- Sinusoidal
- Mollweide
- Equal Earth
- Goode homolosine
- Azimuthal equidistant
- Lambert azimuthal equal-area

## Spherical model and tools

The first versions assume the Earth is a perfect sphere. On that basis the app
will:

- change the coordinate system, for example by moving the center point or
  choosing a different central great circle (the "equator");
- define the central great circle from two points;
- find the shortest path (great circle) between two points;
- find the rhumb line (loxodrome) between two points.

## Project structure

Base package: `net.interlecto.geoutils`

```
net/interlecto/geoutils/
├── data/       Room database (entities, DAOs), JSON parsing, repositories
├── domain/     domain objects (models)
├── geometry/   project-specific math: spherical geometry, projections
├── ui/         fragments, screens and other interface elements
└── shared/     other extension libraries and helpers
```

## Status

Milestone 1 (MVP) is implemented: open the bundled Blue Marble or your own
equirectangular image, overlay a KML file, view it as an orthographic globe
centered anywhere, zoom and pan, and save or share it as SVG or PNG. See
[ROADMAP.md](ROADMAP.md) for what's next.

## Building

Requirements: JDK 17 and the Android SDK (platform 36).

```sh
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # JVM unit tests
./gradlew lintDebug            # Android lint
./gradlew installDebug         # install on a connected device
```

## License

To be decided.

The bundled base map is NASA's Blue Marble Next Generation (topography and
bathymetry, December 2004), downsampled to 4096 × 2048. It's public domain;
NASA Earth Observatory asks for credit, which the app shows with the map:
<https://visibleearth.nasa.gov/images/73909>.
