package net.interlecto.geoutils.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

class ProjectionsTest {

    private fun normal(type: ProjectionType, lon: Double = 0.0) = type.create(GeoPoint(lon, 0.0))

    private fun fwd(p: Projection, lon: Double, lat: Double) = p.forward(GeoPoint(lon, lat))!!

    @Test
    fun everyProjectionRoundTripsInNormalAndObliqueAspects() {
        val random = Random(7)
        for (type in ProjectionCatalog.all) {
            repeat(10) { aspect ->
                val projection = if (aspect == 0) {
                    type.create(GeoPoint(type.defaultLongitude, 0.0))
                } else {
                    type.create(
                        GeoPoint(random.nextDouble(-180.0, 180.0), random.nextDouble(-80.0, 80.0)),
                        random.nextDouble(-180.0, 180.0),
                    )
                }
                repeat(200) {
                    val g = GeoPoint(random.nextDouble(-180.0, 180.0), random.nextDouble(-89.0, 89.0))
                    val p = projection.forward(g) ?: return@repeat
                    // Skip points clamped by Mercator / central cylindrical.
                    val back = projection.inverse(p) ?: return@repeat
                    val error = g.toVec3().angleTo(back.toVec3())
                    val clamped = type == ProjectionCatalog.MERCATOR || type == ProjectionCatalog.CENTRAL_CYLINDRICAL
                    if (!clamped) assertTrue("${type.id}: $g came back as $back", error < 1e-6)
                }
            }
        }
    }

    @Test
    fun cylindricalReferenceValues() {
        val mercator = normal(ProjectionCatalog.MERCATOR)
        assertEquals(ln(tan(PI / 4 + PI / 8)), fwd(mercator, 0.0, 45.0).y, 1e-12)
        assertEquals(PI, mercator.bounds.maxY, 1e-9) // Web Mercator is square.

        val gallPeters = normal(ProjectionCatalog.GALL_PETERS)
        // Gall–Peters: width/height = π cos²45° = π/2.
        assertEquals(PI / 2, gallPeters.bounds.width / gallPeters.bounds.height, 1e-12)

        val behrmann = normal(ProjectionCatalog.BEHRMANN)
        assertEquals(1 / sqrt(0.75), behrmann.bounds.maxY, 1e-12)
    }

    @Test
    fun pseudocylindricalReferenceValues() {
        val sinusoidal = normal(ProjectionCatalog.SINUSOIDAL)
        assertEquals(PI / 4, fwd(sinusoidal, 90.0, 60.0).x, 1e-12)

        val mollweide = normal(ProjectionCatalog.MOLLWEIDE)
        assertEquals(2 * sqrt(2.0), mollweide.bounds.maxX, 1e-12)
        assertEquals(sqrt(2.0), fwd(mollweide, 0.0, 90.0).y, 1e-9)

        // Equal Earth: published extents for the unit sphere.
        val equalEarth = normal(ProjectionCatalog.EQUAL_EARTH)
        assertEquals(2.7066, equalEarth.bounds.maxX, 1e-4)
        assertEquals(1.3173, equalEarth.bounds.maxY, 1e-4)
    }

    @Test
    fun goodeLobesAndGaps() {
        val goode = normal(ProjectionCatalog.GOODE)
        // At the equator the lobes touch: sinusoidal x = λ.
        assertEquals(Math.toRadians(100.0), fwd(goode, 100.0, 0.0).x, 1e-12)
        // High in the north, the gap between the lobes at 40° W is outside the map.
        assertFalse(goode.inverse(Math.toRadians(-40.0), 1.2, DoubleArray(2)))
        // A point inside a lobe is on the map.
        assertTrue(goode.inverse(Math.toRadians(30.0), 1.2, DoubleArray(2)))
    }

    @Test
    fun azimuthalReferenceValues() {
        val equidistant = normal(ProjectionCatalog.AZIMUTHAL_EQUIDISTANT)
        val p = fwd(equidistant, 90.0, 0.0)
        assertEquals(PI / 2, hypot(p.x, p.y), 1e-12)

        val lambert = normal(ProjectionCatalog.LAMBERT_AZIMUTHAL)
        val q = fwd(lambert, 0.0, 90.0)
        assertEquals(sqrt(2.0), q.y, 1e-12)
    }

    @Test
    fun azimuthalMapsShowAlmostTheWholeSphere() {
        val equidistant = normal(ProjectionCatalog.AZIMUTHAL_EQUIDISTANT)
        assertTrue(equidistant.forward(GeoPoint(170.0, 5.0)) != null)
        assertTrue(equidistant.forward(GeoPoint(180.0, 0.0)) == null) // The antipode.
    }

    @Test
    fun cylindricalCenterLongitudeShiftsTheMap() {
        val shifted = ProjectionCatalog.EQUIRECTANGULAR.create(GeoPoint(10.0, 0.0))
        assertEquals(0.0, fwd(shifted, 10.0, 0.0).x, 1e-12)
        assertEquals(Math.toRadians(-20.0), fwd(shifted, -10.0, 30.0).x, 1e-12)
        assertEquals(Math.toRadians(30.0), fwd(shifted, -10.0, 30.0).y, 1e-12)
    }

    @Test
    fun rotatedFrameMovesTheCenterToTheMiddle() {
        // Oblique Mercator through Bogotá: Bogotá maps to the origin.
        val oblique = ProjectionCatalog.MERCATOR.create(GeoPoint(-74.08, 4.6), azimuthDeg = 30.0)
        val p = fwd(oblique, -74.08, 4.6)
        assertEquals(0.0, p.x, 1e-9)
        assertEquals(0.0, p.y, 1e-9)
    }

    @Test
    fun transverseMercatorFollowsTheCentralMeridian() {
        // Azimuth 90 turns the cylinder: the central meridian becomes the "equator".
        val transverse = ProjectionCatalog.MERCATOR.create(GeoPoint(0.0, 0.0), azimuthDeg = 90.0)
        val p = fwd(transverse, 0.0, 30.0)
        assertEquals(0.0, p.y, 1e-9)
        assertTrue(abs(p.x) > 0.5)
    }
}
