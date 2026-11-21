# gRide

## EVERYTHING HERE IS GENERATED USING CLAUDE (mainly Opus 5.5)

An Android app for public transport in Augsburg / AVV: live departures, trip planning, a map with clickable stops, and traffic news.
It ships two UI styles you can switch between in **Settings → Design**:

- **Material 3 Expressive**: `MaterialExpressiveTheme` with the expressive motion scheme, connected toggle buttons, shape-morphing buttons, `ShortNavigationBar` and `LoadingIndicator`.
- **Liquid Glass**: refracting glass panels from [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (`io.github.kyant0:backdrop`). Glass controls grow by +10dp in width when pressed (`tween(140)`). On release they finish the grow first, then spring back (`spring(0.5f, 300f, 0.001f)`). While pressed they light up with white at `0.28 × press`.

## Features

- **Start**: full-screen map with the stops around you (tap a stop for its departures) and a sheet with *Plan a trip* plus your favourite and recent trips. Swipe the sheet down to leave only its top visible, and tap it to bring it back.
- **Departures**: search a stop, or pick one from favourites, history or *Stops nearby*. Departures show realtime delays (+0 green, +n red), bay, direction, line and mode filters, and a departure time. They auto-refresh every 30 s.
- **Trip planning**: from/to (stops, addresses, places, or your current location), leave now / depart at / arrive by, and filters for train, tram, bus and on-demand. Results show the duration, times and line chain, and the selected route is drawn on the map. *Earlier* and *Later* load more connections.
- **Departure details**: tap any departure to see the vehicle's whole run: where it came from, the stops it has passed (dimmed), your stop, the stops still ahead, the last stop, and the route on a small map.
- **Journey details**: a timeline with platforms, delays, intermediate stops and walking legs, with the route on the map.
- **Traffic news**: links to the official swa, AVV and city of Augsburg news pages (the AVV news feed is no longer open to other apps). Cancellations and delays still show up live in the departures.
- **Settings**: design style, theme (system/light/dark), dynamic colours, auto-refresh, and clearing history.

## Build

Open the folder in Android Studio and run the `app` configuration. From a terminal:

```bash
./gradlew assembleDebug
```

You need JDK 25 (Android Studio's bundled JBR works) and Android SDK 37. minSdk is 33, which glass refraction needs (RuntimeShader).

## Releases and in-app updates

The app checks the latest release of [trishamine/gRide](https://github.com/trishamine/gRide/releases) when it starts (at most every 6 hours) and from **Settings → Updates**. When a newer version exists, it shows the release notes, downloads the APK and opens Android's install prompt.

To publish a new version:

```bash
git tag v1.2.0
git push origin v1.2.0
```

The `Release` workflow (`.github/workflows/release.yml`) builds a signed APK with that version and attaches it to a new GitHub release.

Signing: every release must be signed with the same key, or Android won't install it over the old version.
- Locally, `keystore.properties` points to the keystore. Neither file is in git.
- On GitHub, the workflow reads the repository secrets `KEYSTORE_BASE64` (the keystore, base64-encoded) and `KEYSTORE_PASSWORD`.
- Keep a backup of the keystore. If it's lost, existing installs can't be updated anymore.

## Structure

| Path | Purpose |
| --- | --- |
| `data/TransitApi.kt` | Client for the Transitous API: stop search, map/nearby stops, departures, vehicle runs, trip planning |
| `data/Store.kt` | Favourites, history and settings (SharedPreferences) |
| `data/LocationProvider.kt` | Device location via `LocationManager` (no Play Services) |
| `ui/AppViewModel.kt` | Navigation, loading state, map state |
| `ui/GRideApp.kt` | App shell: shared map wallpaper → pages → floating navigation bar (glass backdrop layers) |
| `ui/glass/Glass.kt` | `GlassSurface` and the press animation |
| `ui/components/` | Line badges, stop sign, adaptive buttons, cards and segmented control |
| `ui/map/GRideMap.kt` | osmdroid map with stop markers, route polylines, dark-mode tiles |
| `ui/screens/` | Start, Departures, Stop board, Search, Trip, Journey, Traffic news, Settings |

## How the glass layers work

The map, or a soft gradient on list pages, is recorded into a `LayerBackdrop` (the *wallpaper*). Pages are drawn on top, and their glass panels refract the wallpaper. Pages are also recorded into a second layer. The floating navigation bar refracts both layers (`rememberCombinedBackdrop`), so content scrolling underneath shows through it.

## Data sources

- Timetables, realtime data, search and routing: [Transitous](https://transitous.org) (MOTIS), based on the official German DELFI open data. Non-commercial use only. gRide keeps its requests low: responses are cached, map stops load once per area, auto-refresh runs every 60 s and only while the app is open, and on HTTP 429 the app pauses requests for as long as the server asks.
- Until November 2026 gRide used the Bahnland Bayern EFA API. That endpoint is now closed to third-party apps ("not an open data endpoint"). Official access is available through Bayern's mobility data platform.
- Map: © OpenStreetMap contributors (`tile.openstreetmap.org`, cached by osmdroid).
