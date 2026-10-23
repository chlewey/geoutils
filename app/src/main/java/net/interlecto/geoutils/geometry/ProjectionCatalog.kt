package net.interlecto.geoutils.geometry

enum class ProjectionFamily { AZIMUTHAL, CYLINDRICAL, PSEUDOCYLINDRICAL }

/**
 * A projection the app offers. [defaultLongitude] is the initial center for
 * whole-sphere maps (azimuthal ones start from the device location instead).
 */
class ProjectionType(
    val id: String,
    val displayName: String,
    val family: ProjectionFamily,
    val defaultLongitude: Double,
    private val normal: () -> NormalProjection,
) {
    fun create(center: GeoPoint, azimuthDeg: Double = 0.0): Projection =
        RotatedProjection(normal(), center, azimuthDeg)
}

object ProjectionCatalog {

    /** Keeps the antimeridian cut in the Bering Strait, away from land. */
    private const val WORLD_CENTER = 10.0

    val ORTHOGRAPHIC = ProjectionType("orthographic", "Orthographic", ProjectionFamily.AZIMUTHAL, 0.0) {
        OrthographicNormal
    }
    val AZIMUTHAL_EQUIDISTANT =
        ProjectionType("azimuthal-equidistant", "Azimuthal equidistant", ProjectionFamily.AZIMUTHAL, 0.0) {
            AzimuthalEquidistantNormal
        }
    val LAMBERT_AZIMUTHAL =
        ProjectionType("lambert-azimuthal", "Lambert azimuthal equal-area", ProjectionFamily.AZIMUTHAL, 0.0) {
            LambertAzimuthalEqualAreaNormal
        }
    val EQUIRECTANGULAR =
        ProjectionType("equirectangular", "Equirectangular", ProjectionFamily.CYLINDRICAL, WORLD_CENTER) {
            EquirectangularNormal()
        }
    val MERCATOR = ProjectionType("mercator", "Mercator", ProjectionFamily.CYLINDRICAL, 0.0) { MercatorNormal() }
    val LAMBERT_CYLINDRICAL =
        ProjectionType("lambert-cylindrical", "Lambert cylindrical equal-area", ProjectionFamily.CYLINDRICAL, WORLD_CENTER) {
            CylindricalEqualAreaNormal(0.0)
        }
    val BEHRMANN = ProjectionType("behrmann", "Behrmann", ProjectionFamily.CYLINDRICAL, WORLD_CENTER) {
        CylindricalEqualAreaNormal(30.0)
    }
    val HOBO_DYER = ProjectionType("hobo-dyer", "Hobo–Dyer", ProjectionFamily.CYLINDRICAL, WORLD_CENTER) {
        CylindricalEqualAreaNormal(37.5)
    }
    val GALL_PETERS = ProjectionType("gall-peters", "Gall–Peters", ProjectionFamily.CYLINDRICAL, WORLD_CENTER) {
        CylindricalEqualAreaNormal(45.0)
    }
    val CENTRAL_CYLINDRICAL =
        ProjectionType("central-cylindrical", "Central cylindrical", ProjectionFamily.CYLINDRICAL, WORLD_CENTER) {
            CentralCylindricalNormal()
        }
    val SINUSOIDAL = ProjectionType("sinusoidal", "Sinusoidal", ProjectionFamily.PSEUDOCYLINDRICAL, WORLD_CENTER) {
        SinusoidalNormal
    }
    val MOLLWEIDE = ProjectionType("mollweide", "Mollweide", ProjectionFamily.PSEUDOCYLINDRICAL, WORLD_CENTER) {
        MollweideNormal
    }
    val EQUAL_EARTH = ProjectionType("equal-earth", "Equal Earth", ProjectionFamily.PSEUDOCYLINDRICAL, WORLD_CENTER) {
        EqualEarthNormal
    }

    /** Its interruptions are placed for land at central meridian 0°. */
    val GOODE = ProjectionType("goode", "Goode homolosine", ProjectionFamily.PSEUDOCYLINDRICAL, 0.0) {
        GoodeHomolosineNormal
    }

    val all = listOf(
        ORTHOGRAPHIC, AZIMUTHAL_EQUIDISTANT, LAMBERT_AZIMUTHAL,
        EQUIRECTANGULAR, MERCATOR, LAMBERT_CYLINDRICAL, BEHRMANN, HOBO_DYER, GALL_PETERS, CENTRAL_CYLINDRICAL,
        SINUSOIDAL, MOLLWEIDE, EQUAL_EARTH, GOODE,
    )

    fun byId(id: String): ProjectionType = all.firstOrNull { it.id == id } ?: ORTHOGRAPHIC
}

/** Orthographic projection centered on [center], turned by [azimuthDeg]. */
fun Orthographic(center: GeoPoint, azimuthDeg: Double = 0.0): Projection =
    ProjectionCatalog.ORTHOGRAPHIC.create(center, azimuthDeg)

/** Plate carrée (equirectangular, standard parallel 0°) centered on [center]. */
fun Equirectangular(center: GeoPoint = GeoPoint(0.0, 0.0), azimuthDeg: Double = 0.0): Projection =
    RotatedProjection(EquirectangularNormal(), center, azimuthDeg)
