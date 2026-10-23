# Geo-Utils Roadmap

This roadmap for Geo-Utils starts with a small, end-to-end MVP and grows it in stages. Each
milestone should leave the app shippable. Where a later milestone affects
early design choices, the MVP notes say so, so the MVP doesn't need to be
rewritten later.

Conventions used throughout:

- **Sphere model.** The Earth is a perfect sphere (radius 6 371 km) until the
  ellipsoid milestone.
- **Coordinates.** Longitude/latitude in degrees, WGS84 axis order (lon, lat),
  the same order KML uses.
- **Equirectangular input.** A base map bitmap covers 360° × 180°: x = 0 is
  180° W, y = 0 is 90° N, and the aspect ratio is 2:1.

---

## Milestone 0: Project foundation

Goal: an empty but well-structured app that later milestones can build on.

- [x] Gradle (Kotlin DSL) project with a version catalog: AGP 9.0, Kotlin 2.3,
      Gradle 9.1, minSdk 26, targetSdk/compileSdk 36.
- [x] Single-activity app with Jetpack Compose and type-safe Navigation.
- [x] Splash screen (`androidx.core:core-splashscreen`) shown until startup
      work finishes.
- [x] Hilt wired up (application class, database and coroutine modules).
- [x] Room wired up: a `base_maps` table, schemas exported to `app/schemas`,
      and no destructive migrations.
- [x] Package layout under `net.interlecto.geoutils` (see the README):
  - `data`: Room, JSON, KML parsing, repositories.
  - `domain`: domain objects.
  - `geometry`: spherical math, projections, raster reprojection, SVG writer.
    Kept free of Android dependencies.
  - `ui`: fragments, screens, viewer.
  - `shared`: other extension libraries.
- [x] JVM unit tests (starting with `geometry`); GitHub Actions CI runs build,
      lint and tests.

## Milestone 1: MVP (v0.1)

Goal: **open a base map and a KML file, project them orthographically, and view
and download the result.**

### Inputs
- [x] Bundled Earth base map: NASA Blue Marble (public domain), shipped
      downsampled (for example 4096 × 2048 WebP) in `assets/`, with attribution
      shown in the app.
- [x] Open a user equirectangular bitmap (PNG, JPEG, WebP) through the Storage
      Access Framework. Warn when the aspect ratio isn't 2:1.
- [x] Open a KML file through the Storage Access Framework. Support
      `Placemark` with `Point`, `LineString`, `LinearRing`, `Polygon` and
      `MultiGeometry`, plus basic `Style` (line color and width, fill color).
      Ignore other elements without failing.

### Projection
- [x] `Projection` interface in `geometry`: forward `(lon, lat) → (x, y)`,
      inverse `(x, y) → (lon, lat)?`, a visibility test, and the boundary
      outline of the map. Later projections plug into this interface.
- [x] **Orthographic** projection (globe view) with a configurable center
      (lon₀, lat₀).
- [x] Equirectangular as an identity/reference projection, used in tests.

### Rendering
- [x] Raster reprojection by inverse mapping: for each output pixel, apply the
      inverse projection and sample the source bitmap (bilinear sampling, with
      longitude wrapping at the antimeridian).
- [x] Vector projection of the KML geometry: densify long segments along great
      circles, clip at the horizon (the back hemisphere for orthographic), and
      drop or split geometry that crosses it.
- [x] Output an **SVG** document: the projected raster embedded as a base64
      `<image>`, KML geometry as `<path>`/`<circle>` elements, the globe outline,
      and an optional graticule.

### Viewer and export
- [x] Zoomable, pannable viewer (pinch zoom, drag, double-tap to reset).
      Draw the same scene to a Compose `Canvas` rather than re-rendering the SVG
      for display.
- [x] Simple controls: choose base map, choose KML, set center lon/lat, render.
- [x] Download/share the result as **SVG** and as **PNG** (MediaStore or
      Storage Access Framework, plus the share sheet).

### Done when
- A KML of country outlines shows correctly on the Blue Marble globe, centered
  anywhere, with nothing visible from the far side.
- The exported SVG opens correctly in a browser and in Inkscape.

Status: implemented. The globe-with-countries check (Natural Earth 1:110m, four
centers including both poles) passes, and the SVG renders correctly in Chrome.
The app has been confirmed working on a device. Opening the SVG in Inkscape is
still to be checked.

## Milestone 2: More projections (v0.2)

Goal: cover the projections listed in the README, all through the same
`Projection` interface.

- [ ] Equirectangular as a real output (with a configurable standard parallel).
- [ ] Mercator (web Mercator first, with a latitude cutoff; spherical "true"
      Mercator later).
- [ ] Cylindrical equal-area with presets: Lambert, Behrmann, Gall–Peters,
      Hobo–Dyer.
- [ ] Central cylindrical.
- [ ] Sinusoidal.
- [ ] Mollweide.
- [ ] Equal Earth.
- [ ] Goode homolosine (interrupted, so this needs clipping at the interruptions).
- [ ] Azimuthal equidistant.
- [ ] Lambert azimuthal equal-area.
- [ ] General antimeridian/boundary cutting for cylindrical and pseudocylindrical maps.
- [ ] Projection picker in the UI with per-projection parameters.
- [ ] Round-trip tests (forward then inverse) and reference-point tests for each
      projection.

## Milestone 3: Extensible projections and coordinate transforms (v0.3)

Goal: adding a projection is cheap, and any projection can be re-aimed.

- [ ] Projection registry: each projection declares an id, display name,
      parameters (with ranges and defaults) and capabilities (has an inverse,
      is interrupted, is azimuthal). The UI is generated from that description.
- [ ] Oblique/transverse aspects: rotate the sphere before projecting, so every
      projection gets a configurable center and orientation.
  - [ ] Set a new center point (lon₀, lat₀) and rotation.
  - [ ] Set a new "equator" (central great circle) from **two points**.
- [ ] Geodesy tools in `geometry`, drawable as overlays:
  - [ ] Great-circle (shortest) path and distance between two points.
  - [ ] Rhumb line (loxodrome) and distance between two points.
- [ ] Contributor guide: how to add a projection (interface, tests, registry entry).

## Milestone 4: Base map library (v0.4)

Goal: keep and manage more than one base map.

- [ ] Room entities for base maps: name, source, license/attribution, size,
      local file path, thumbnail, and body (Earth by default; leave room for other
      bodies such as the Moon or Mars).
- [ ] Import a user bitmap into the library: copy it into app storage and
      generate a thumbnail.
- [ ] Library screen: browse, rename, delete, set the default.
- [ ] Recent KML files and render settings saved as reusable "map
      configurations" (base map + overlays + projection + parameters).
- [ ] Large-image handling: tiled or downsampled decoding so 8K–21K source images
      don't exhaust memory.

## Milestone 5: Downloadable base maps (v0.5)

Goal: get base maps from a catalog rather than bundling them.

- [ ] Curated catalog (a JSON manifest) of public-domain/openly licensed
      equirectangular maps: Blue Marble monthly variants, Black Marble (night
      lights), Natural Earth rasters, planetary maps.
- [ ] Downloads through WorkManager: progress, resume, Wi-Fi-only option,
      checksum verification.
- [ ] Choice of resolution per entry, with a storage usage display.
- [ ] Attribution and license shown per map and embedded in exported files.

## Milestone 6: Interactive maps (v1.0)

Goal: the map is regenerated live as you move it, rather than zooming and
panning a fixed image.

- [ ] Drag to **rotate the globe/sphere** (change the projection center), with
      the display regenerated on every change.
- [ ] Gesture to rotate around the view axis (reorientation).
- [ ] Zoom that changes the projection scale rather than scaling pixels.
- [ ] Fast path for live interaction: GPU raster reprojection (AGSL
      `RuntimeShader` on API 33+, OpenGL ES fallback), a low-resolution preview
      while dragging, and a full-quality render when the gesture ends.
- [ ] Vector overlays re-projected each frame (cache the densified geometry and
      reuse it).
- [ ] Tap a feature to see its KML name and description; tap two points to draw a
      great-circle or rhumb path between them.
- [ ] Export the current view at a chosen resolution or as SVG.

## Later / ideas

- Projected output combined as a raster (bitmap + KML burned into the image)
  in addition to SVG with an embedded bitmap.
- Ellipsoidal Earth (WGS84) for projections where it matters (Mercator, transverse
  Mercator).
- More input formats: GeoJSON, GPX, KMZ, shapefile, and SVG icons for placemarks.
- Input bitmaps in projections other than equirectangular (inverse-project
  them to equirectangular first).
- Tissot indicatrix and distortion heatmaps.
- Scale bar and printable map layouts.
