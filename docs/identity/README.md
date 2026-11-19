# Geo-Utils visual identity

The mark is the globe from [geoutils-legacy](https://github.com/chlewey/geoutils-legacy)
(its adaptive launcher foreground), simplified to a 120×120 master:

- `globe.svg`: the master. Ocean and land are two shapes with radial gradients;
  the legacy shading overlay (transparent to 50% black at the rim) is folded
  into the gradient stops. The coasts keep 380 of the 619 points of the
  legacy outlines (12 outlines), coordinates to 0.1 unit: points were removed
  one at a time, always the one whose removal changed the 120 px rendering
  least, measured on the pixels rather than on the geometry. At 120×120 it
  matches the legacy rendering in 98.2% of the pixels within ±16 levels per
  channel (96.2% within ±8), against 97.6% for a Douglas–Peucker
  simplification of the same size. `legacy-comparison.png` shows both side by
  side at 4×.
- Size versus fidelity: each coast point costs about 2.6 bytes of the
  gzipped SVG (about 7.5 uncompressed) on top of about 450 bytes for the rest.
  Coordinate precision matters more than the number of points: rounding to
  half a pixel caps the match at about 96%, and to whole pixels at about 94%,
  however many points are kept. Above about 1.65 KB gzipped the gains are
  negligible. Visvalingam–Whyatt (removing the point with the smallest
  triangle) ranks points almost the same way down to about 380 points.
- `play-icon-512.png`: Google Play store icon (512×512, opaque, no rounding;
  Play applies its own mask).

## Palette

| Use | Color |
|---|---|
| Ocean, center → rim (shading included) | `#0101FF` → `#0101B1` → `#000072` → `#000040` |
| Land, center → rim (shading included) | `#90FF60` → `#76C045` → `#5E882E` → `#455A1C` |
| Icon background (`@color/ocean`) | `#0B3D66` |
| Site background / `theme-color` | `#000814` |

The exact stops are in `globe.svg`; the app and the site copy them.

## Where it is used

- **Launcher icon** (`app/src/main/res/mipmap-anydpi/ic_launcher.xml`): background
  `@color/ocean`; foreground `drawable/ic_launcher_foreground.xml`, the globe at
  60% of the 108 dp canvas, centered (viewport 200, globe moved to 40,40), so it
  sits inside the 66 dp safe zone and fills about 90% of the visible area.
  Themed icons use `drawable/ic_launcher_monochrome.xml`: the disc with the
  land cut out plus a rim, so the outline survives where land meets the edge.
- **Splash screen**: the same foreground on `@color/ocean` (`values/themes.xml`).
- **Site** (`site/public/`): `favicon.svg` is the launcher's visible area as a
  rounded square (globe at 90%), also used as the header and 404 logo;
  `favicon.ico` (16, 32, 48), `icon-192.png` and `icon-512.png` are rasters
  of it; `icon-maskable-512.png` is full-bleed with the globe at 60% (inside
  the maskable safe zone); `apple-touch-icon.png` (180) is full-bleed with the
  globe at 90%. `site.webmanifest` lists them.

## File and folder icons

`app/src/main/res/drawable/ic_fileformat_*`, `ic_foldertype*` and
`ic_unknown_format` come from geoutils-legacy unchanged, except that their
size is 48 dp instead of the legacy `@dimen/icon_size` and a stray character
in `ic_foldertype` was fixed. `ic_fileformat_png`, `_jpeg` and `_tiff` are
labels only (drawn at 37.5% opacity), meant to be overlaid on an image's
thumbnail, not standalone icons.

## Regenerating the rasters

Made with ImageMagick 7 from the SVGs (render large, then downsample):

```
magick -background none -density 1200 favicon.svg -resize 32x32 -strip -depth 8 f32.png
magick f16.png f32.png f48.png favicon.ico
```

For the full-bleed icons, wrap the `globe.svg` content in a square of
`#0B3D66`: viewBox `-40 -40 200 200` puts the globe at 60%, and
`-6.667 -6.667 133.333 133.333` puts it at 90% (the launcher's visible area).
