# Geo-Utils landing page

Static landing page for Geo-Utils, served at https://geoutils.ilecto.app by a
Cloudflare Worker with static assets. Developed on the `landing` branch.

- `public/`: the site (`index.html`, English/Spanish toggle, `404.html`).
- `public/img/`: map images rendered by the app's own projection engine
  (`SceneBuilder` + `SvgWriter`) from the bundled Blue Marble base map.

Preview and deploy (needs `npx wrangler login` once):

    npx wrangler dev        # http://localhost:8787
    npx wrangler deploy
