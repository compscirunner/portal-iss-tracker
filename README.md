# ISS Tracker for Meta Portal

A kiosk-style, NASA-branded International Space Station dashboard for a sideloaded
**Meta Portal** (Android, no Google services). Inspired by
[filbot/iss-tracker](https://github.com/filbot/iss-tracker) (a Raspberry Pi /
Python project) — this is a native Android re-imagining: a **live video feed from
the ISS**, a **live orbital ground track**, and the **people-in-space roster**, all
on one screen.

## What it shows

- **Live ISS video (hero panel)** — Sen's real 4K cameras on the ISS
  (the SpaceTV-1 payload), streamed from YouTube. Dark when the station is on the
  night side of Earth — which the map's telemetry explains.
- **Live orbital ground track** — the ISS on an equirectangular world map with the
  predicted ground-track path (solid where it's been, dashed NASA-red ahead) and a
  visibility-footprint circle.
- **Telemetry HUD** — latitude, longitude, altitude (km), velocity (km/h), a coarse
  offline "over region" label (continent/ocean), and day/night visibility.
- **Humans in Space** — the current crew grouped by spacecraft (ISS, Tiangong, …).
- NASA-blue title bars with the meatball insignia; fullscreen, landscape,
  screen-kept-on — meant to be left running on the panel.

## Data sources (free, no API key)

| Feed | Source | Notes |
|------|--------|-------|
| ISS position + telemetry | `https://api.wheretheiss.at/v1/satellites/25544` | HTTPS, ~5 s refresh |
| People in space | `http://api.open-notify.org/astros.json` | HTTP only → scoped cleartext allow-list |
| Live ISS video | [Sen](https://www.sen.com/live) on YouTube (`@Sen`) | embeddable; played in a WebView |

Position/crew/map are pure platform (`HttpURLConnection` + `org.json` + `Canvas`);
the video is a `WebView`. No Google Play Services, Maps SDK, or login required.

## The live-feed wrinkle (Meta Portal WebView)

Embedding the ISS video took some fighting with the Portal's **forked WebView**
(`com.facebook.portal.webview`). Findings, in case they're useful elsewhere:

- **NASA's own stream blocks embedding** (YouTube error 150). **Sen allows embeds**
  (`playableInEmbed: true`), so that's the source.
- YouTube's **mobile player throws a non-recoverable MSE/DRM error** in this WebView
  fork → force a **desktop Chrome user-agent** so it serves the desktop player.
- The player needs a **valid embedding origin + Referer**: a `data:`-URL iframe has a
  null origin (error 152) and a top-level load sends no referer (error 150). The fix
  is a **tiny localhost HTTP server** (`FeedView`) that serves the iframe page, so it
  gets a real `http://127.0.0.1` origin.
- With all three, the WebView plays it on the **Qualcomm hardware VP9 decoder**.
- **Caveat:** the live video id is hard-coded in `FeedView` (Sen's current broadcast).
  When Sen restarts the stream the id changes and the feed needs updating — resolving
  it at runtime from `youtube.com/@Sen/live` is the obvious follow-up.

NASA's old public HLS (`nasa-i.akamaihd.net`) was also tried (would play natively via
ExoPlayer), but it 404s from the device — deprecated since NASA TV shut down in 2024.

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

## Layout

```
app/src/main/java/com/portal/isstracker/
  MainActivity.java   kiosk lifecycle, polling cadence, side-by-side layout
  IssApi.java         wheretheiss.at + open-notify clients
  FeedView.java       live ISS video (WebView + localhost embed server)
  TrackerView.java    world map + predicted ground track + telemetry HUD (Canvas)
  OrbitTrack.java     circular-orbit model for the ground-track path
  CrewView.java       people-in-space roster
  GeoRegion.java      offline bounding-box "over region" classifier
app/src/main/assets/world.jpg   equirectangular world map (public domain, Wikimedia)
app/src/main/assets/nasa.png    NASA meatball insignia (public domain)
app/src/main/res/xml/network_security_config.xml   cleartext allow-list (open-notify, localhost)
```

On-screen: live video fills the left ~64%; the map+HUD and crew roster stack in the
right column.

## Credits

- Concept: [filbot/iss-tracker](https://github.com/filbot/iss-tracker)
- Live ISS video: [Sen](https://www.sen.com/) (SpaceTV-1 cameras aboard the ISS)
- World map & NASA insignia: public domain (NASA, via Wikimedia Commons)
- ISS position: [Where the ISS at?](https://wheretheiss.at/) · Crew: [Open Notify](http://open-notify.org/)
