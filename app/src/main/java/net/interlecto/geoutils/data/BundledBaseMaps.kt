package net.interlecto.geoutils.data

import net.interlecto.geoutils.data.image.RasterLoader
import net.interlecto.geoutils.domain.model.BaseMap

/** Base maps shipped in `assets/basemaps`, added to the library on startup. */
object BundledBaseMaps {

    /**
     * NASA Blue Marble Next Generation with topography and bathymetry,
     * December 2004, downsampled from 5400 × 2700. Public domain.
     * Source: https://visibleearth.nasa.gov/images/73909
     */
    val BLUE_MARBLE = BaseMap(
        name = "Blue Marble",
        uri = RasterLoader.ASSET_URI_PREFIX + "basemaps/blue_marble_200412_4096.webp",
        attribution = "Blue Marble: NASA Earth Observatory",
        widthPx = 4096,
        heightPx = 2048,
    )

    val all = listOf(BLUE_MARBLE)
}
