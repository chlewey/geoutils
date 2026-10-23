# geoutils

An Android app for working with KML files and other geographic data, and for
making maps in different projections.

It's a single-activity app (Jetpack Compose) with a splash screen during
loading, using Hilt for dependency injection and Room for local storage.

See [ROADMAP.md](ROADMAP.md) for the planned milestones.

## What it does

- **Project a bitmap:** take an equirectangular base map (for example NASA's
  Blue Marble) and reproject it.
- **Project vector data:** take a KML file and produce an SVG of its geometry
  in the chosen projection.
- **Combine both:** base map + KML gives either
  - a projected bitmap with the shapes drawn onto it, or
  - a projected vector file (SVG) with the projected bitmap embedded.

## Formats

| Role   | Formats                                                                 |
|--------|-------------------------------------------------------------------------|
| Input  | Equirectangular bitmaps (PNG, JPEG, WebP), KML. Later: other projections, icons, overlays. |
| Output | SVG, PNG, JPEG, WebP, at different scales and projections.             |

## Projections

Planned initial set (output first; input in equirectangular only):

- Orthographic (globe view)
- Equirectangular (plate carrée)
- Mercator (web Mercator first; spherical "true" Mercator later)
- Cylindrical equal-area (Lambert, Behrmann, Gall–Peters, Hobo–Dyer)
- Central cylindrical
- Sinusoidal
- Mollweide
- Equal Earth
- Goode homolosine
- Azimuthal equidistant
- Lambert azimuthal equal-area

## Spherical model and tools

The first versions assume the Earth is a perfect sphere. On that basis the app
will:

- change the coordinate system, for example by moving the center point or
  choosing a different central great circle (the "equator");
- define the central great circle from two points;
- find the shortest path (great circle) between two points;
- find the rhumb line (loxodrome) between two points.

## Project structure

Base package: `net.interlecto.geoutils`

```
net/interlecto/geoutils/
├── data/       Room database (entities, DAOs), JSON parsing, repositories
├── domain/     domain objects (models)
├── geometry/   project-specific math: spherical geometry, projections
├── ui/         fragments, screens and other interface elements
└── shared/     other extension libraries and helpers
```

## Status

Early planning: there's no code yet. The MVP is described in
[ROADMAP.md](ROADMAP.md#milestone-1-mvp-v01).

## License

To be decided. NASA Blue Marble imagery is public domain; attribution to
NASA Earth Observatory is still requested.
