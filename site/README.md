# Geo-Utils landing page

Static landing page for Geo-Utils, served at https://geoutils.ilecto.app by a
Cloudflare Worker with static assets (`wrangler.jsonc`, Worker `geoutils-site`).

- `public/`: the site (`index.html`, English/Spanish toggle, `404.html`).
- `public/favicon.*`, `icon-*.png`, `apple-touch-icon.png`, `mask-icon.svg`,
  `site.webmanifest`: the site icons, and `public/img/og.jpg`: the social
  card (1200×630). All are made from the globe in `docs/identity/`; its README
  says how. `_headers` caches the icons and images for a day.
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
