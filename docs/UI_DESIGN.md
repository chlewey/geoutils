# UI design proposal

Status: **approved by the owner, not yet implemented** (2026-11-24). Covers the "next up"
items in `ROADMAP.md`: the settings page, a quick background color and the
groundwork for the identity redesign.

## 1. Where we are

One screen (`MapScreen`), Compose Material 3, one route (`MapRoute`):

```
┌──────────────────────────┐
│ Geo-Utils        [Export]│  TopAppBar, text button
├──────────────────────────┤
│                          │
│         viewer           │  fixed background #000814
│                          │
├──────────────────────────┤
│ Map │ View │ Tools       │  PrimaryTabRow
│ (scrolling controls,     │  panel up to 340 dp
│  max 340 dp)             │
└──────────────────────────┘
```

Observations:

- The panel takes up to 340 dp: on a small phone the map gets about half the
  screen, and in landscape almost nothing.
- The viewer background is hard-coded (`Color(0xFF000814)` in `MapScreen`),
  not a setting, and not used by the export.
- Language, theme and the A/B defaults have no place to live; the top bar has
  no overflow menu.
- `Theme.kt` has only `primary` and `secondary`; the rest is Material
  defaults, so a new identity would not propagate by changing a few values.

## 2. Principles

1. **Map first.** Controls serve the map and get out of the way.
2. **Single activity, Compose only** (as in CLAUDE.md): Settings is a second
   composable route, not an activity.
3. **Everything a user can change goes through tokens** (color scheme,
   typography, shapes in `ui/theme`), so the identity redesign is a change in
   one place.
4. **Preview equals export**: what the viewer shows (background included) is
   what SVG/PNG contain.

## 3. Main screen

### 3.1 Top bar

```
Geo-Utils                     [⤓ Export]  [⋮]
                                  │          └ Settings · About
                                  └ Save SVG/PNG · Share SVG/PNG
```

Export becomes an icon with a dropdown (same four actions). The new overflow
menu opens Settings.

### 3.2 Panel as a bottom sheet

Replace the fixed column by a `BottomSheetScaffold` with three detents:

| Detent | Shows | Map height |
|---|---|---|
| Peek (default) | tab row + the one control you use most for that tab (projection picker, center longitude, A/B) | ~75% |
| Half | the tab's controls | ~50% |
| Expanded | everything, scrollable | ~25% |

Tabs stay as they are (Map · View · Tools). In landscape and on tablets the
sheet becomes a side panel (about 360 dp wide) next to the viewer, using
`WindowSizeClass`.

### 3.3 Quick background color

A small round swatch in the viewer's top-right corner (always visible, 40 dp,
translucent ring so it shows on any color):

```
┌──────────────────────────┐
│                     (●)  │ ← tap
│          map             │
└──────────────────────────┘

(●) opens a popup:
  ● ● ● ● ● ●  ◌       presets, then transparent
  [ #000814        ]  custom hex field
```

Presets (names are provisional): night `#000814` (today's), black, white,
paper `#F4EFE6`, ocean `#0B3D66`, ice `#EAF2F8`. The last chip is
**transparent** (checkerboard), valid for PNG and SVG exports.

- Applies to the viewer and to exports (SVG background `<rect>`, PNG fill).
- Remembered with the map configuration; the initial value is a setting
  (section 4).
- A long press on the swatch toggles the last two used colors (fast
  light/dark flip without opening the popup).

## 4. Settings page

New route `SettingsRoute`, reached from the ⋮ menu; back arrow in the top
bar. A plain list of grouped rows, the standard Material 3 pattern; each
group is a function that adds rows, so new groups are easy to add.

```
←  Settings
────────────────────────────
APPEARANCE
  Language            System default ›
  Theme               System / Light / Dark ›
  Map background      ● night ›

DEFAULT POINTS
  Point A     lat [ 4.7110 ] lon [ -74.0721 ]
              [Use current center]
  Point B     lat [ 51.5074] lon [ -0.1278 ]
              [Use current center]
  Used as the initial A and B in Tools.

(later: export defaults, base maps, units, about)
```

Details:

- **Language**: "System default" plus the languages the app ships; applied
  with `AppCompatDelegate.setApplicationLocales` (per-app language, Android
  13+ also exposes it in system settings; with `localeConfig`). First step is
  to move any remaining hard-coded text into `strings.xml`; adding
  translations is separate (Spanish first).
- **Theme**: three-way choice (System, Light, Dark), shown as a segmented
  button (the app already has `SegmentedChoice`). The viewer background does
  not follow the theme; it has its own setting.
- **Default A and B**: reuse `PointPairEditor` validation (decimal comma
  accepted, ranges checked). "Use current center" fills from the map's center.
  Seeded with Bogotá and London until the user changes them.
- Storage: Jetpack DataStore (Preferences), exposed as a
  `SettingsRepository` with a `Flow`; Room stays for map content. Theme and
  language are read at the top of the tree (`GeoUtilsTheme`, activity).

## 5. Theme tokens (groundwork for the identity redesign)

- Complete the Material 3 color scheme in `Theme.kt` (primary, secondary,
  tertiary, containers, surfaces) for light and dark, derived from the
  palette in `docs/identity/README.md`. No dynamic color by default (the
  brand palette is the identity); offer it later as a switch.
- Put the palette and the viewer presets in one `Palette` object, so the
  redesign replaces values, not call sites.
- Define typography and shapes explicitly (today: defaults).
- Do **structure first, identity second**: sections 3 and 4 are built with
  tokens, and the redesign (new globe, palette, icons) then lands as a token
  swap plus new assets.

## 6. Proposed order

1. Theme tokens + `SettingsRepository` (DataStore) + theme setting wired in.
2. Settings route with Appearance and Default points; use A/B defaults in
   Tools.
3. Language (string externalization, per-app locales).
4. Background color: state, swatch, popup, export support.
5. Bottom-sheet layout and landscape side panel.
6. Identity redesign.

Each step is shippable by itself.

## 7. Open questions

- **Bottom sheet or keep the fixed panel?** The sheet gives the map more
  room but adds a gesture to learn; a cheaper alternative is a collapse
  button on the existing panel.
- **Transparent background** in PNG/SVG: wanted, or only solid colors?
- **Background per map or global?** Proposed: per map configuration, with a
  global default in Settings.
- **Languages** to ship first: Spanish and English?
- **Default A and B**: any other seed than Bogotá and London?
- **Identity direction**: keep and evolve the legacy globe, or a new mark?
  This decides the palette (section 5) and is best settled before step 6.
