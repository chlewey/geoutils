# geoutils

single activity app with a splash screen during load, room-ready, hilt-ready, that allow to manage kml files and other geographic data formats, and create maps in different projections. It should support image formats such as PNG, JPEG, WebP, vector formats as SVG for different functions such as:
 * input: plain orthoplane (equirectangular) projection, other possible projections, icons, overlays
 * output: maps at different scales and projection.

initial support should include (just output initially):
 * orthoplanal projection (input and output)
 * mercator (or web mercator, true mercator for later)
 * cylindrical equal-area (vg. Gall-Peters, Lambert, Behrmann, Hobo-Dyer)
 * central cylindrical
 * sinusoidal
 * mollweide
 * equal earth
 * goode homolosine
 * equidistant (azimutal)
 * lambert azimutal equiarea

Initial support will assume earth is a perfect sphere
 * allow coordinate system translations (v.g. changing center or chaning central great circle, i.e. equator)
 * allow define central great circle by two points
 * find shortest path between two points.
 * find rumb path between two points.

what the inputs and outputs do.
 * input one (equirrectangular?) bitmap and project it.
 * input one kml file and produce an svg file with the projection.
 * combine both: bitmap + kml -> projected bitmap (with imposed forms) or projected vector (with embeded projected bitmap)
