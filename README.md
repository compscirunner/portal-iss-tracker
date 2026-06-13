# ISS Tracker for Meta Portal

A kiosk-style International Space Station display for a sideloaded **Meta Portal**
(Android, no Google services). Inspired by [filbot/iss-tracker](https://github.com/filbot/iss-tracker)
(a Raspberry Pi / Python project) — this is a native Android re-imagining of the
same idea: a live ground track plus a people-in-space roster.

![flight-display style HUD over an equirectangular world map]

## What it shows

- **Live ground track** — the ISS plotted on an equirectangular world map, with a
  trailing breadcrumb of recent positions and a faint visibility-footprint circle.
- **Telemetry HUD** (Boeing-style: green labels / white values / magenta target):
  latitude, longitude, altitude (km), velocity (km/h), and a coarse offline
  "over region" label (continent or ocean), plus day/night visibility.
- **Humans in Space** — the current crew grouped by spacecraft (ISS, Tiangong, …).
- Auto-cycles between the map and the crew roster; **tap anywhere** to flip manually.
- Fullscreen, landscape, screen-kept-on — meant to be left running on the panel.

## Data sources (free, no API key)

| Feed | API | Notes |
|------|-----|-------|
| ISS position + telemetry | `https://api.wheretheiss.at/v1/satellites/25544` | HTTPS, ~5 s refresh |
| People in space | `http://api.open-notify.org/astros.json` | HTTP only → allowed via a scoped network-security-config |

No Google Play Services, Maps SDK, or login required — it's just `HttpURLConnection`,
`org.json`, and `Canvas` drawing, so it runs on a GMS-free Portal as-is.

## Build & deploy

Toolchain matches the other Portal apps on this box: **JDK 17/21, AGP 8.3.2,
Gradle 8.10.2, SDK platform-34**. `minSdk 24`, `targetSdk 34`
(targetSdk ≥ 23 avoids the Portal+ permissioncontroller crash).

```bash
# Build the debug APK
./gradlew assembleDebug

# Install + launch on a Portal (one-shot; --build to compile first)
./deploy.sh --build                       # default adb device
./deploy.sh -s 192.168.0.36:5555 --build  # aloha = Portal+ (1920×1080)
./deploy.sh -s 192.168.0.164:5555         # omni  = 2019 Portal (1280×800)
```

The map is letterboxed to fit either panel's aspect ratio, with the HUD in the
margins.

## Layout

```
app/src/main/java/com/portal/isstracker/
  MainActivity.java   kiosk lifecycle, polling cadence, view auto-cycle
  IssApi.java         wheretheiss.at + open-notify clients
  TrackerView.java    world map + ground track + telemetry HUD (Canvas)
  CrewView.java       people-in-space roster
  GeoRegion.java      offline bounding-box "over region" classifier
app/src/main/assets/world.jpg              equirectangular world map (public domain)
app/src/main/res/xml/network_security_config.xml   cleartext allow-list for open-notify
```

## Credits

- Concept: [filbot/iss-tracker](https://github.com/filbot/iss-tracker)
- World map: NASA-derived equirectangular projection (public domain, via Wikimedia Commons)
- ISS position: [Where the ISS at?](https://wheretheiss.at/) · Crew: [Open Notify](http://open-notify.org/)
