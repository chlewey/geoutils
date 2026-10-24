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
- **Earlier projects.** Items marked *(from …)* port ideas, code or data from
  the author's earlier map tools:
  [globe-prj](https://github.com/chlewey/globe-prj) (Python),
  [kml2svg](https://github.com/chlewey/kml2svg) (PHP) and
  [maps](https://github.com/chlewey/maps) (PHP). They are reimplemented here
  in Kotlin, not copied. Third-party data in them (GADM boundaries, KMLs of
  unknown origin) is not bundled.

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
- [x] Azimuth (rotation around the view axis) with an Off / ±180° / 0–360°
      selector; off by default.
- [x] With location permission (approximate), start centered on the device.
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

- [x] Equirectangular as a real output. The standard parallel is a
      constructor parameter; the UI offers plate carrée (0°) for now.
- [x] Mercator, cut at the web Mercator limit (±85.05°, a square map). On a
      sphere, web and spherical Mercator are the same projection; they only
      differ on the ellipsoid (see Later / ideas).
- [x] Cylindrical equal-area with presets: Lambert, Behrmann, Gall–Peters,
      Hobo–Dyer.
- [x] Central cylindrical.
- [x] Sinusoidal.
- [x] Mollweide.
- [x] Equal Earth.
- [x] Goode homolosine (interrupted, so this needs clipping at the interruptions).
- [x] Azimuthal equidistant.
- [x] Lambert azimuthal equal-area.
- [x] General antimeridian/boundary cutting for cylindrical and pseudocylindrical maps.
- [x] Projection picker in the UI, grouped by family. Parameters come as
      presets (the four cylindrical equal-area variants); free parameter
      editing comes with the registry in milestone 3.

### Aspect: center and orientation

Every projection is drawn in a **rotated coordinate system**. The user's
center (lon₀, lat₀) and azimuth α are moved to (0°, 0°) with north up, and
the projection's normal formulas then run in the new system:

- Build the frame at the center: c = the center as a unit vector, u = the
  direction of bearing α at c, and r = u × c (pointing right). This is the
  same frame `Orthographic` already uses for its center and azimuth.
- A point p on the sphere becomes p′ = (p·c, p·r, p·u), so its rotated
  coordinates are lon′ = atan2(p·r, p·c) and lat′ = asin(p·u).
- Project (lon′, lat′) with the normal-aspect formulas. For the inverse,
  apply the inverse projection, then p = x′·c + y′·r + z′·u.
- Cutting and clipping (antimeridian, interruptions, map boundary) happen in
  the rotated system, so the cut follows the meridian opposite the new center.
- This is one generic wrapper around any normal-aspect projection. The
  orthographic projection becomes an instance of it.

Defaults and controls for **cylindrical and pseudocylindrical** projections
(Mercator, equal-area cylindricals, central cylindrical, sinusoidal,
Mollweide, Equal Earth, Goode):

- [x] Initial center longitude chosen per projection: 10° E (keeps the
      antimeridian cut away from land, at the Bering Strait), 0°, or the
      user's own longitude.
- [x] Initial center latitude 0° and azimuth 0° (normal aspect).
- [x] The latitude and azimuth controls start **grayed out** (off, fixed at
      0°), like the orthographic azimuth control. Only the longitude is
      active.
- [x] When the user turns latitude or azimuth on and changes it, the map
      switches to an **oblique aspect**: the sphere is rotated so the chosen
      (lon, lat, azimuth) is at the center of the image, then projected as
      above. For example, a Mercator with azimuth 90° is a transverse
      Mercator.
- [x] Tests: a rotated projection with lat₀ = 0 and α = 0 equals the normal
      one shifted by lon₀; the rotated Orthographic matches the current one;
      forward/inverse round trips hold in oblique aspects.
- [x] Each projection keeps its own view (center, latitude and azimuth
      settings) while the app runs, so switching back and forth doesn't lose
      it.

Known issue (fixed in milestone 3): Goode homolosine is drawn through the
generic rotation wrapper, so latitude and azimuth tilt the sphere under its
lobes, which gives incoherent maps. Its lobes and cuts are laid out for the
continents; only the central meridian may move them.

Status: implemented. All 14 projections were checked visually with Blue Marble
and Natural Earth countries, including oblique Mollweide and transverse
Mercator, and pass forward/inverse round trips in normal and oblique aspects.

- [x] Round-trip tests (forward then inverse) and reference-point tests for each
      projection.

## Milestone 3: Extensible projections and coordinate transforms (v0.3)

Goal: adding a projection is cheap, and any projection can be re-aimed.

- [x] Projection registry: each projection declares an id, display name,
      family, default longitude, aspect controls (rotate, or central meridian
      and north/south up) and its own parameters (angles with ranges and
      defaults, or choices). The UI builds the parameter and aspect controls
      from that description.
- [x] Set a new "equator" (central great circle) from **two points**, on top
      of the rotated coordinate system from milestone 2 (View tab).
- [x] Geodesy tools in `geometry`, drawable as overlays (Tools tab):
  - [x] Great-circle (shortest) path, distance and initial bearing between
        two points.
  - [x] Rhumb line (loxodrome) path, distance and bearing between two points.
- [x] Contributor guide: [docs/ADDING_A_PROJECTION.md](docs/ADDING_A_PROJECTION.md).
- [x] Equirectangular's standard parallel is now a parameter (0°–70°).
- [x] **Discontinuous (interrupted) projections don't use the standard
      aspect parameters.** Goode homolosine, and any future interrupted or
      otherwise discontinuous projection, declares its own tailored
      parameters in the registry instead of the generic center/latitude/
      azimuth:
  - [x] **Central meridian:** allowed; it turns the sphere about the polar axis,
    so the whole map, lobes included, shifts with it.
  - [x] **Latitude and azimuth:** not available, since they give incoherent
    results with lobes.
  - [x] **Orientation:** at most a toggle between 0° and 180° (north up or
    south up). This turns the finished map in the plane, keeping the lobes
    tied to geography; it does not rotate the sphere.
  - [x] **Goode's Greenland option:** the standard northern interruption at
    40° W splits Greenland. Offer *divided* (standard), *extended* (the
    western lobe extended east so Greenland is whole), or *both* (Greenland
    whole in the extended lobe and also shown split). *Extended* moves the
    cut to 10° W north of 60° N (a stepped cut along 60° N), which keeps
    Greenland and Iceland whole and leaves West Africa and Ireland alone.
    *Both* draws the divided map plus Greenland's eastern part a second time
    in the extended position.

Status: implemented, apart from the conic projections below, which are the
next step. Hemispheric and two-hemisphere maps are done.

### More projection families

- [ ] **Conic:** Albers equal-area, Lambert conformal conic, equidistant
      conic, each with one or two standard parallels as parameters.
      Rotation works for conics as for cylindricals, but the cut runs from
      the apex and the map is a sector of an annulus.
- [x] **Polar shortcut for azimuthals:** one-click N / S buttons next to the
      center controls of the azimuthal projections (equidistant, Lambert
      equal-area). They set the center latitude to ±90° and keep the
      longitude, which then sets which meridian points down (or up).
- [x] **Hemispheric:** one hemisphere on its own, clipped to a disk at 90°
      from the center. Parametrized like the azimuthals (center longitude,
      latitude, azimuth), plus a quick **N / Equator / S** setting for the
      latitude (90°, 0°, −90°). Projections:
  - [x] Orthographic (the current one, which is already hemispheric).
  - [x] Azimuthal equidistant, hemisphere only (disk radius π/2).
  - [x] Lambert azimuthal equal-area, hemisphere only (disk radius √2).
  - [x] Mollweide, hemisphere only: the circle between ±90° from the central
        meridian, in the rotated frame.
  - [x] Nicolosi globular (new; hemispheric by nature).

  For the non-azimuthal ones (Mollweide, Nicolosi) the disk's horizontal
  line is the "equator" of the rotated frame.
- [x] **Double hemispheric:** any of the hemispheric projections above, as two
      disks tangent to each other in one image. This needs a projection made
      of several sub-maps, the same structure interrupted projections use
      (`Composite`). Two modes:
  - **Polar** (northern and southern hemispheres). Longitude is the meridian
    shared by both disks at the tangent point (the point on the equator at
    that longitude). Azimuth is the image angle of the line through both
    disk centers, which is that meridian (and the "equator" of each disk for
    Mollweide and Nicolosi); 0° puts the disks side by side. Latitude does
    not apply.
  - **Non-polar** (for example western and eastern hemispheres). Longitude
    and latitude are the tangent point; latitude is grayed out by default
    (0°). Azimuth is the bearing of the split great circle at the tangent
    point; 0° runs north–south, so the disks sit side by side with north up.
    With latitude 0° the longitude *is* the split meridian, so the default
    longitude is **30° W** (split at 30° W / 150° E), not the device's
    longitude.
  - Both modes are the same construction: a frame at the tangent point with
    the split circle as its "meridian" and the two disk centers 90° to
    either side. Polar mode is that frame with latitude fixed at 0° and the
    split fixed on the equator (bearing 90°), with the azimuth applied as a
    rotation of the finished image. Hence the azimuth of the two modes
    differs by 90°: polar names the line of centers, non-polar names the
    tangent.

### Overlay tools from earlier projects

- [ ] `Geodesy.destination(point, distance, bearing)`: the point reached
      along a great circle *(from kml2svg `move_coor`)*. The tools below
      build on it.
- [ ] **Range rings and azimuth rays** from a point: circles every N km (by
      default 1000 km) and bearings every 15° (Tools tab). On the azimuthal
      equidistant map they come out as concentric circles and straight
      lines *(from kml2svg `equi.php`)*.
- [ ] **Geodesic circle:** everything within a radius of a point, drawn as a
      closed polygon *(from kml2svg `circle()`)*.
- [ ] **Special parallels** in the graticule: tropics of Cancer and
      Capricorn and the polar circles, dashed, as an option
      *(from kml2svg `projection.php`)*.
- [ ] Candidate for the hemispheric family: the **circular equal-area
      globular** (`cirq_ea`), a hemisphere on a disk of radius √2 with
      circular-arc meridians *(from kml2svg `circ.php`)*. Check first that
      it really preserves area: its parallels are spaced linearly in φ.

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

### Export options

- [ ] Export dialog with configurable parameters before saving or sharing
      (SVG and PNG):
  - **Clip:** the region of the map to export. By default, whatever is
    shown on screen (the viewer's current zoom and pan, such as the
    width-fitted Mercator with its top and bottom cut off). Other choices:
    the whole map, or a custom rectangle.
  - **Scale:** output size, as a pixel width (height follows from the clip)
    or a multiple of the on-screen size. PNG renders at that size; SVG uses
    it for its dimensions and the embedded raster's resolution.
- [ ] The SVG clips to the chosen region (`viewBox`), and its embedded raster
      covers only that region.
- [ ] Simplify vector paths at the output resolution (Douglas–Peucker), so
      large KMLs give small SVGs *(idea from maps `reduce.php`, which only
      dropped collinear points)*.

### Overlays and KML

- [ ] **Bundled continents overlay:** `Continents.kml` *(from kml2svg)*, the
      author's own work (CC0), used here under the project's license. The
      continents with their subregions (Africa N/E/W/C, Asia N/E/S/SE/W/C, …)
      and the oceans (Atlantic, Indian), with styles per area. It is 2.5 MB
      of KML, so simplify it and store it compressed (KMZ) in `assets/`, and
      offer it in the KML picker next to "Open KML…", the way Blue Marble is
      offered as a base map.
- [ ] **KMZ input** (moved up from Later / ideas): a zip with `doc.kml` and
      its resources.
- [ ] Per layer, how segments between KML vertices are drawn: along great
      circles (as now, like Google Earth) or along parallels / rhumb lines,
      so borders traced on a parallel (49° N) stay on it
      *(from kml2svg `mkline2`)*.
- [ ] **Export overlays as KML:** routes, range rings and circles, besides
      SVG and PNG *(from globe-prj `as_kml`, kml2svg `equi.php`)*.

### Places (center presets)

- [ ] Room table of places: name, country, longitude, latitude, source
      (bundled, user or downloaded).
- [ ] Prefilled with an initial list, bundled as JSON in `assets/`: Moscow,
      London, New York, Tokyo, Sydney, Johannesburg, Lagos, Dhaka, Mecca,
      Buenos Aires.
- [ ] "Center on…" menu next to the center controls: pick a place, search by
      name. "My location" (GPS) sits at the top of the list as a place.
- [ ] A place sets the center the same way GPS does:
  - azimuthal projections: the place becomes the center (longitude and
    latitude);
  - cylindrical and pseudocylindrical projections: only the central
    meridian (longitude); latitude and azimuth stay as they are (off by
    default).
- [ ] Add a place from the current center, or by typing a name and
      coordinates; rename and delete user places.

## Milestone 5: Downloadable base maps (v0.5)

Goal: get base maps from a catalog rather than bundling them.

- [ ] Curated catalog (a JSON manifest) of public-domain/openly licensed
      equirectangular maps: Blue Marble monthly variants, Black Marble (night
      lights), Natural Earth rasters, planetary maps.
- [ ] Downloads through WorkManager: progress, resume, Wi-Fi-only option,
      checksum verification.
- [ ] Choice of resolution per entry, with a storage usage display.
- [ ] Attribution and license shown per map and embedded in exported files.
- [ ] Downloadable place lists (for example capitals, or all cities above a
      population threshold, from GeoNames or Natural Earth populated places),
      imported into the places table with their source and license.

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
- [ ] Spherical polygon tools, needed for tapping and for labels: point in
      polygon by winding number on the sphere, a polygon's center from its
      normal vector, and holes found by containment for KMLs that store
      rings as separate polygons *(from globe-prj `GeoTag.py`, kml2svg
      `fixmap.php`)*.
- [ ] Export the current view at a chosen resolution or as SVG (builds on
      the export options from milestone 4).

## Milestone 7: Thematic maps (v1.1)

Goal: color a KML's areas by data *(from maps)*.

- [ ] Load a CSV and join its rows to KML placemarks by name, with an alias
      table to match differing names (accents, abbreviations, codes)
      *(from maps `name2code`, kml2svg `[alias]` sections)*.
- [ ] Choropleth: pick a column, classes (equal intervals, quantiles), a
      color ramp and a legend; areas without data shown apart.
- [ ] Labels at each area's center (spherical polygon tools, milestone 6).
- [ ] Values in the exported SVG (each area's `<title>` with name and
      value).

## Milestone 8: KML topology editing (v1.2)

Goal: reshape KML areas with editing lines, for historical and fictional
maps *(from kml2svg `fixmap.php`; low priority)*.

- [ ] Edit commands drawn as lines in a special folder: split an area along
      a line, delete, drill (holes), reshape from another area, copy,
      rename/move between folders.
- [ ] Shared borders: an edit applies to every area that uses that border.
- [ ] Aliases and per-area styles from a configuration, statistics (areas,
      point counts), and a folder for edits that failed.
- [ ] Output as KML.

## Later / ideas

- Projected output combined as a raster (bitmap + KML burned into the image)
  in addition to SVG with an embedded bitmap.
- Ellipsoidal Earth (WGS84) for projections where it matters (Mercator, transverse
  Mercator).
- More input formats: GeoJSON, GPX, shapefile, and SVG icons for placemarks
  (KMZ moved to milestone 4).
- Input bitmaps in projections other than equirectangular (inverse-project
  them to equirectangular first).
- Tissot indicatrix and distortion heatmaps.
- Scale bar and printable map layouts.
- Initial center without location permission: when location is denied, center
  on the user's country instead. Take the country from the mobile network or
  SIM (`TelephonyManager.networkCountryIso`, no permission needed), falling
  back to the locale's region. Map it to a center point with a small bundled
  table of country centroids (about 250 rows, a few KB of JSON in `assets/`).
