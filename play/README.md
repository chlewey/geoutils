# Google Play upload package

Everything needed to create the Geo-Utils listing in Play Console.
Package: `aps.ilecto.geoutils`.

## Files

| Play Console field | File |
|---|---|
| App bundle (release) | `app/build/outputs/bundle/release/app-release.aab` (build with `./gradlew bundleRelease`; not committed) |
| App icon, 512×512 | `icon-512.png` |
| Feature graphic, 1024×500 | `feature-graphic.png` |
| Phone screenshots, 1080×2160 (2:1, Play's maximum) | `screenshots/phone/01-orthographic.png`, `02-sinusoidal.png`, `03-view.png` |

Screenshots come from the debug build on an emulator (Pixel-like, 1080×2400),
cropped to 2:1 to remove the status and navigation bars. Retake them when the
UI changes. Play also wants 7-inch and 10-inch tablet screenshots only if you
want the app featured on tablets; they are optional.

## Listing (English, en-US)

**App name** (max 30): `Geo-Utils`

**Short description** (max 80):
`Make maps in 26 projections from base maps and KML. Export SVG or PNG.`

**Full description** (max 4000):

```
Geo-Utils turns a base map and your geographic data into maps in the projection you choose.

• Reproject a base map. NASA's Blue Marble is built in; the map is redrawn in the projection you pick.
• Draw KML on top. Open a KML file and see its points, lines and polygons projected onto the map.
• 26 projections: orthographic globe, azimuthal (equidistant and Lambert equal-area), Mercator, equirectangular and other cylindrical maps, Mollweide, sinusoidal, Equal Earth, Goode homolosine, Albers, Lambert conformal conic and more.
• Aim the map anywhere. Set the center longitude and latitude, jump to the poles or the equator, and rotate the map's azimuth.
• Geodesic tools: great-circle route between two points, distance rings and bearing lines from a point.
• Export the result as SVG or PNG.

Geo-Utils works offline. It has no account, no ads and no tracking.

The app is in early development; new projections and features are added regularly.

Blue Marble: NASA Earth Observatory.
```

**Category:** Maps & Navigation (alternative: Education)
**Tags:** maps, projections, KML, cartography, geography
**Contact website:** https://geoutils.ilecto.app

## Listing (Spanish, es-419)

**Nombre:** `Geo-Utils`

**Descripción breve:**
`Crea mapas en 26 proyecciones con mapas base y KML. Exporta SVG o PNG.`

**Descripción completa:**

```
Geo-Utils convierte un mapa base y tus datos geográficos en mapas en la proyección que elijas.

• Reproyecta un mapa base. Incluye Blue Marble de la NASA; el mapa se redibuja en la proyección elegida.
• Dibuja KML encima. Abre un archivo KML y ve sus puntos, líneas y polígonos proyectados sobre el mapa.
• 26 proyecciones: globo ortográfico, azimutales (equidistante y de área igual de Lambert), Mercator, equirrectangular y otras cilíndricas, Mollweide, sinusoidal, Equal Earth, homolosena de Goode, Albers, cónica conforme de Lambert y más.
• Apunta el mapa a cualquier lugar. Fija longitud y latitud del centro, salta a los polos o al ecuador y rota el acimut.
• Herramientas geodésicas: ruta de círculo máximo entre dos puntos, anillos de distancia y líneas de rumbo desde un punto.
• Exporta el resultado como SVG o PNG.

Geo-Utils funciona sin conexión. No tiene cuentas, anuncios ni rastreo.

La app está en desarrollo temprano; se agregan proyecciones y funciones con frecuencia.

Blue Marble: NASA Earth Observatory.
```

## Play Console forms (answers consistent with the code)

Check these against the current manifest before submitting.

- **Permissions in the manifest:** only `ACCESS_COARSE_LOCATION`. There is no
  `INTERNET` permission, so the app cannot send data off the device.
- **Data safety:** no data collected or shared. Approximate location is read on
  the device only, to center the map, and is not transmitted. No data leaves
  the device, so deletion requests do not apply.
- **Privacy policy URL:** Play requires one for apps that request location.
  Publish a short page (for example `https://geoutils.ilecto.app/privacy`)
  stating the above. This does not exist yet.
- **Ads:** none. **App access:** all features available without login.
- **Content rating (IARC):** no violence, no user-generated content shared
  between users, no purchases. Expect "Everyone".
- **Target audience:** 18+ / general (not directed to children).
- **Government/news/health/financial app declarations:** none apply.

## Upload steps

1. Play Console → Create app: name `Geo-Utils`, default language English (US),
   App, Free.
2. Complete the policy forms above (App content).
3. Store listing: paste the texts, upload icon, feature graphic, screenshots.
4. Testing → Internal testing → Create release, keep Play App Signing on,
   upload `app-release.aab`, add testers, roll out.
5. Next uploads need a higher `versionCode` (now 1) in `app/build.gradle.kts`.
