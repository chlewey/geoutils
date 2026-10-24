package net.interlecto.geoutils.geometry

enum class ProjectionFamily { AZIMUTHAL, CYLINDRICAL, PSEUDOCYLINDRICAL }

/** Which aspect controls a projection accepts. */
enum class AspectControls {
    /** Center (longitude and latitude) and azimuth, by rotating the sphere. */
    ROTATE,

    /**
     * Central meridian and north-up/south-up only. For interrupted and other
     * discontinuous maps, whose cuts are laid out for the continents:
     * tilting the sphere under them gives incoherent results.
     */
    MERIDIAN_AND_FLIP,
}

/** A setting specific to one projection. Values are passed around as strings. */
sealed interface ProjectionParam {
    val key: String
    val label: String
    val default: String

    /** An angle in degrees, chosen with a slider. */
    data class Angle(
        override val key: String,
        override val label: String,
        val min: Double,
        val max: Double,
        val defaultDeg: Double,
    ) : ProjectionParam {
        override val default: String get() = defaultDeg.toString()
    }

    /** One of a few named options. */
    data class Choice(
        override val key: String,
        override val label: String,
        val options: List<Option>,
        override val default: String,
    ) : ProjectionParam {
        data class Option(val value: String, val label: String)
    }
}

/** Parameter values for one projection, falling back to the declared defaults. */
class ParamValues(private val params: List<ProjectionParam>, private val values: Map<String, String>) {

    fun angle(key: String): Double {
        val param = params.first { it.key == key } as ProjectionParam.Angle
        return values[key]?.toDoubleOrNull()?.coerceIn(param.min, param.max) ?: param.defaultDeg
    }

    fun choice(key: String): String {
        val param = params.first { it.key == key } as ProjectionParam.Choice
        return values[key]?.takeIf { v -> param.options.any { it.value == v } } ?: param.default
    }
}

/**
 * A projection the app offers: how to build it and which controls it takes.
 * [defaultLongitude] is the initial center for whole-sphere maps (azimuthal
 * ones start from the device location instead).
 */
class ProjectionType private constructor(
    val id: String,
    val displayName: String,
    val family: ProjectionFamily,
    val defaultLongitude: Double,
    val aspect: AspectControls,
    val params: List<ProjectionParam>,
    private val build: (center: GeoPoint, azimuthDeg: Double, values: ParamValues) -> Projection,
) {
    /**
     * Builds the projection. For [AspectControls.MERIDIAN_AND_FLIP] only the
     * center's longitude is used, and an azimuth of 180° means south up.
     */
    fun create(center: GeoPoint, azimuthDeg: Double = 0.0, values: Map<String, String> = emptyMap()): Projection =
        build(center, azimuthDeg, ParamValues(params, values))

    companion object {
        /** A projection aimed by rotating the sphere under its normal-aspect formulas. */
        fun rotating(
            id: String,
            displayName: String,
            family: ProjectionFamily,
            defaultLongitude: Double,
            params: List<ProjectionParam> = emptyList(),
            normal: (ParamValues) -> NormalProjection,
        ) = ProjectionType(id, displayName, family, defaultLongitude, AspectControls.ROTATE, params) { center, azimuth, values ->
            RotatedProjection(normal(values), center, azimuth)
        }

        /**
         * A projection tied to geography, such as an interrupted map. [build]
         * makes it at central meridian 0°; a different central meridian
         * slides the finished map sideways, wrapping at the edges, so the
         * lobes travel with their continents. It can also be shown south up.
         */
        fun meridianOnly(
            id: String,
            displayName: String,
            family: ProjectionFamily,
            defaultLongitude: Double,
            params: List<ProjectionParam> = emptyList(),
            build: (values: ParamValues) -> Projection,
        ) = ProjectionType(id, displayName, family, defaultLongitude, AspectControls.MERIDIAN_AND_FLIP, params) { center, azimuth, values ->
            val built = build(values)
            val map = if (center.lon == 0.0) built else ShiftedProjection(built, center.lon)
            if (azimuth.mod(360.0) == 180.0) FlippedProjection(map) else map
        }
    }
}

object ProjectionCatalog {

    /** Keeps the antimeridian cut in the Bering Strait, away from land. */
    private const val WORLD_CENTER = 10.0

    val ORTHOGRAPHIC = ProjectionType.rotating("orthographic", "Orthographic", ProjectionFamily.AZIMUTHAL, 0.0) {
        OrthographicNormal
    }
    val AZIMUTHAL_EQUIDISTANT = ProjectionType.rotating(
        "azimuthal-equidistant", "Azimuthal equidistant", ProjectionFamily.AZIMUTHAL, 0.0,
    ) { AzimuthalEquidistantNormal }
    val LAMBERT_AZIMUTHAL = ProjectionType.rotating(
        "lambert-azimuthal", "Lambert azimuthal equal-area", ProjectionFamily.AZIMUTHAL, 0.0,
    ) { LambertAzimuthalEqualAreaNormal }

    val EQUIRECTANGULAR = ProjectionType.rotating(
        "equirectangular", "Equirectangular", ProjectionFamily.CYLINDRICAL, WORLD_CENTER,
        params = listOf(ProjectionParam.Angle("standardParallel", "Standard parallel", 0.0, 70.0, 0.0)),
    ) { values -> EquirectangularNormal(values.angle("standardParallel")) }
    val MERCATOR = ProjectionType.rotating("mercator", "Mercator", ProjectionFamily.CYLINDRICAL, 0.0) { MercatorNormal() }
    val LAMBERT_CYLINDRICAL = ProjectionType.rotating(
        "lambert-cylindrical", "Lambert cylindrical equal-area", ProjectionFamily.CYLINDRICAL, WORLD_CENTER,
    ) { CylindricalEqualAreaNormal(0.0) }
    val BEHRMANN = ProjectionType.rotating("behrmann", "Behrmann", ProjectionFamily.CYLINDRICAL, WORLD_CENTER) {
        CylindricalEqualAreaNormal(30.0)
    }
    val HOBO_DYER = ProjectionType.rotating("hobo-dyer", "Hobo–Dyer", ProjectionFamily.CYLINDRICAL, WORLD_CENTER) {
        CylindricalEqualAreaNormal(37.5)
    }
    val GALL_PETERS = ProjectionType.rotating("gall-peters", "Gall–Peters", ProjectionFamily.CYLINDRICAL, WORLD_CENTER) {
        CylindricalEqualAreaNormal(45.0)
    }
    val CENTRAL_CYLINDRICAL = ProjectionType.rotating(
        "central-cylindrical", "Central cylindrical", ProjectionFamily.CYLINDRICAL, WORLD_CENTER,
    ) { CentralCylindricalNormal() }

    val SINUSOIDAL = ProjectionType.rotating("sinusoidal", "Sinusoidal", ProjectionFamily.PSEUDOCYLINDRICAL, WORLD_CENTER) {
        SinusoidalNormal
    }
    val MOLLWEIDE = ProjectionType.rotating("mollweide", "Mollweide", ProjectionFamily.PSEUDOCYLINDRICAL, WORLD_CENTER) {
        MollweideNormal
    }
    val EQUAL_EARTH = ProjectionType.rotating("equal-earth", "Equal Earth", ProjectionFamily.PSEUDOCYLINDRICAL, WORLD_CENTER) {
        EqualEarthNormal
    }

    /** Interrupted; its cuts are laid out on the oceans and move with the map. */
    val GOODE = ProjectionType.meridianOnly(
        "goode", "Goode homolosine", ProjectionFamily.PSEUDOCYLINDRICAL, 0.0,
        params = listOf(
            ProjectionParam.Choice(
                "greenland",
                "Greenland",
                listOf(
                    ProjectionParam.Choice.Option("divided", "Divided"),
                    ProjectionParam.Choice.Option("extended", "Extended"),
                    ProjectionParam.Choice.Option("both", "Both"),
                ),
                default = "divided",
            ),
        ),
    ) { values ->
        val center = GeoPoint(0.0, 0.0)
        when (values.choice("greenland")) {
            "extended" -> RotatedProjection(GoodeHomolosineNormal(extendedGreenland = true), center)
            // Divided, plus the part of Greenland east of 40° W drawn a second
            // time where the extended lobe would put it, next to the rest.
            "both" -> CompositeProjection(
                listOf(
                    RotatedProjection(GoodeHomolosineNormal(), center),
                    RotatedProjection(
                        GoodeHomolosineNormal(extendedGreenland = true),
                        center,
                        region = GoodeHomolosineNormal.GREENLAND_BOX,
                    ),
                ),
            )
            else -> RotatedProjection(GoodeHomolosineNormal(), center)
        }
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
