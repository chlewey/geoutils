# Geo-Utils landing page

Static landing page for Geo-Utils, served at https://geoutils.ilecto.app by a
Cloudflare Worker with static assets (`wrangler.jsonc`, Worker `geoutils-site`).

- `public/`: the site (`index.html`, English/Spanish toggle, `404.html`).
- `public/img/`: map images rendered by the app's own projection engine
  (`SceneBuilder` + `SvgWriter`) from the bundled Blue Marble base map.

Preview and deploy (needs `npx wrangler login` once):

    npx wrangler dev        # http://localhost:8787
    npx wrangler deploy

The images are SVGs from `SvgWriter` with the projected raster embedded as
WebP. To add or refresh one, render it with a throwaway JVM test that calls
`ProjectionCatalog.byId(…).create(…)`, `SceneBuilder.build(…)` and
`SvgWriter.write(…)` (don't commit the test), then embed the raster and copy
the SVG into `public/img/`. Keep each image around 300 KB.
