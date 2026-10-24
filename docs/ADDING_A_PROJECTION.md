# Adding a projection

Projections live in `app/src/main/java/net/interlecto/geoutils/geometry/`,
which is plain Kotlin with no Android dependencies, so everything here can
be unit-tested on the JVM.

## 1. Write the normal-aspect formulas

Implement `NormalProjection` for the projection centered on (0°, 0°) with
north up. Angles are in radians and the sphere has radius 1.

```kotlin
object MyProjectionNormal : NormalProjection {
    override val bounds = Bounds(-PI, -PI / 2, PI, PI / 2)
    override val clip = Clip.Seams() // or Clip.Cap(minCos) for azimuthal maps

    override fun forward(lambda: Double, phi: Double, out: DoubleArray) {
        out[0] = /* x */
        out[1] = /* y */
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        // Return false when (x, y) is outside the map.
        out[0] = /* λ */
        out[1] = /* φ */
        return true
    }
}
```

- **`bounds`** must contain the whole map. The viewer and exports size the
  image from it.
- **`inverse`** must reject points outside the map. Raster reprojection
  calls it once per pixel, and anything it accepts gets painted.
- **`forward` and `inverse`** run millions of times per image, so don't
  allocate in them.

Put the class in the file for its family: `Azimuthal.kt`, `Cylindrical.kt`,
`Pseudocylindrical.kt`, `Conic.kt` or `Hemispheric.kt`. A conic extends
`ConicNormal`, which only needs the cone constant and the radius of each
parallel.

### Choosing the clip

| Kind of map | `clip` |
|---|---|
| Shows one hemisphere (orthographic, Nicolosi) | `Clip.Cap(0.0)` |
| Shows almost the whole sphere around a center (azimuthal equidistant, Lambert) | `Clip.Cap(cos(179°))` |
| Whole sphere cut at the antimeridian (cylindrical, pseudocylindrical) | `Clip.Seams()` |
| Interrupted (Goode) | `Clip.Seams(listOf(Seam.ANTIMERIDIAN, …interruptions))` |

An interruption is a `Seam`: a path from a pole made of segments along
meridians and parallels (see Goode's stepped "extended Greenland" cut). The
clipper cuts lines and polygons along seams and closes polygons along the map
edge, so the projection only needs to describe where the cuts are.

## 2. Register it

Add an entry to `ProjectionCatalog` and to its `all` list.

```kotlin
val MY_PROJECTION = ProjectionType.rotating(
    "my-projection",            // stable id, used in saved views and file names
    "My projection",            // shown in the picker
    ProjectionFamily.PSEUDOCYLINDRICAL,
    defaultLongitude = 10.0,    // initial central meridian for whole-sphere maps
) { MyProjectionNormal }
```

- **`ProjectionType.rotating`** gives the projection the generic aspect
  controls: center longitude, latitude and azimuth, applied by rotating the
  sphere. Use it for any continuous projection.
- **`ProjectionType.meridianOnly`** is for interrupted or otherwise
  discontinuous maps that are laid out for the continents. Rotating the
  sphere under them gives incoherent maps, so they only get a central
  meridian and a north-up/south-up toggle, plus their own parameters.
- **`ProjectionType.doubleHemispheric`** shows a hemispheric map twice, on
  two disks that touch (`doubleHemisphere`). Its controls are the tangent
  point, the azimuth of the split and a polar layout. Any `NormalProjection`
  that draws its hemisphere as a disk filling its bounds works; wrap a
  whole-sphere one in `HemisphereNormal` to keep only that disk.

### Parameters

Declare tailored parameters with `params`. The UI builds its controls from
them: a slider for `ProjectionParam.Angle`, and segmented buttons for
`ProjectionParam.Choice`. Read the values in the factory:

```kotlin
val MY_CONIC = ProjectionType.rotating(
    "my-conic", "My conic", ProjectionFamily.CYLINDRICAL, 10.0,
    params = listOf(ProjectionParam.Angle("parallel", "Standard parallel", 0.0, 80.0, 30.0)),
) { values -> MyConicNormal(values.angle("parallel")) }
```

## 3. Test it

`ProjectionsTest.everyProjectionRoundTripsInNormalAndObliqueAspects` already
covers every catalog entry: forward then inverse must return the starting
point, in the normal aspect and in random oblique ones. Also add
reference-value tests (published extents, or known points) next to the
existing ones in `ProjectionsTest`. For a new kind of seam, add a case to
`SeamClipperTest`.

Run them with:

```sh
./gradlew testDebugUnitTest
```

Then render it with real data: open the app, load a KML of country
outlines, and check the edges, the poles, and an oblique aspect.
