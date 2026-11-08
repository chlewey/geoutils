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

Output projections (input bitmaps are equirectangular):

- **Hemispheric:** orthographic (globe view), azimuthal equidistant, Lambert
  azimuthal equal-area and Mollweide limited to one hemisphere, Nicolosi
  globular
- **Azimuthal:** azimuthal equidistant, Lambert azimuthal equal-area, with
  one-tap polar aspects
- **Cylindrical:** equirectangular (plate carrée), Mercator, cylindrical
  equal-area (Lambert, Behrmann, Hobo–Dyer, Gall–Peters), central cylindrical
- **Pseudocylindrical:** sinusoidal, Mollweide, Equal Earth, Goode homolosine
- **Conic:** Albers equal-area, Lambert conformal conic, equidistant conic,
  each with two standard parallels
- **Two hemispheres:** any hemispheric map on two touching disks, split along
  a meridian (by default 30° W / 150° E) or any great circle, or as northern
  and southern hemispheres

Continuous projections can be aimed at any center and turned by an azimuth:
the sphere is rotated so the chosen point is at the middle of the map, then
projected. Cylindrical and pseudocylindrical maps start in the normal aspect
(latitude and azimuth off); turning those on gives oblique and transverse
aspects. Interrupted maps (Goode) stay tied to geography: only the central
meridian and north/south up change, plus Goode's Greenland option (divided,
extended or both).

## Spherical model and tools

The first versions assume the Earth is a perfect sphere. On that basis the app
can:

- change the coordinate system, for example by moving the center point or
  choosing a different central great circle (the "equator");
- define the central great circle from two points;
- find the shortest path (great circle) between two points;
- find the rhumb line (loxodrome) between two points;
- draw distance rings and bearing lines from a point, and the area within a
  radius of it;
- draw the tropics and the polar circles.

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

Milestones 1 to 3 are implemented. Open the bundled Blue Marble or your own
equirectangular image and overlay a KML file. Project them in any of 14
projections (orthographic, azimuthal, cylindrical and pseudocylindrical),
aimed at any center and azimuth, including oblique and transverse aspects,
or with the equator through two chosen points. Draw great-circle and rhumb
routes with their distances. Zoom and pan, and save or share the result as
SVG or PNG. See [ROADMAP.md](ROADMAP.md) for what's next.

To add a projection, see [docs/ADDING_A_PROJECTION.md](docs/ADDING_A_PROJECTION.md).

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
