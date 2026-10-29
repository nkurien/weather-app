# Dotter Weather

A colour-driven weather page, London first. One static `index.html`, no build, no backend, no runtime dependencies. Forecast and place search come from [Open-Meteo](https://open-meteo.com/) straight from the visitor's browser.

## What it does

- Hour-by-hour temperature curve with rain bars, and the week as rows of dots, one per hour (size is temperature, solid is cloudier, a tail is rain, faint is night). Drag across either to scrub through time.
- The whole page sits on a sky driven by sun position and conditions (cloud, rain, fog, snow, storm), and glides when you scrub or change place. Honours `prefers-reduced-motion`.
- **Add any city.** The `+` chip opens a search; picking a result adds a chip. Added places are kept in the browser and can be removed with the `x`.
- **The moon.** At night the sky shows the moon at its real phase, tilt and position, and a line under the summary reads like "Waxing gibbous · 78% lit · sets 03:12".

## Run locally

```
npm run serve        # then open http://localhost:8080/#london
npm install && npm test
```

(Or just open `index.html` in a browser; it needs internet for the forecast.) If port 8080 is taken, run `python3 -m http.server 8081` instead.

## Install as an app (PWA)

`manifest.webmanifest`, `sw.js` and `icons/` make the site installable and usable offline. On Android Chrome use menu, Install app. On iOS Safari use Share, Add to Home Screen (iOS has no install prompt). Offline it opens from cache and shows the last forecast it fetched. The service worker only registers over https or on localhost. To force every visitor onto a new shell, bump `VERSION` in `sw.js`. Icons are rendered from `icons/icon.svg` with `rsvg-convert -w 512 icons/icon.svg -o icons/icon-512.png` (and 192, and 180 for `apple-touch-icon.png`).

## Android app and widget

The site is wrapped with [Capacitor](https://capacitorjs.com/); the generated project is in `android/` and bundles the web files, so the app works offline.

```
npm run android:sync     # copy the site into www/ and sync it into android/
npx cap open android     # open in Android Studio, then Run (device or emulator)
```

Needs Android Studio (which installs the SDK) and a JDK. The home-screen widget is native Java in `android/app/src/main/java/com/nathankurien/dotter/WeatherWidgetProvider.java`: it fetches Open-Meteo itself, draws a sky gradient from sun position and conditions (a simplified version of `basePalette`/`skyFor` in `index.html`), and keeps the last response for offline. Each widget keeps its own place, chosen when you add it (the twelve preset cities, or search any place) and changeable later via long-press, Reconfigure (`WeatherWidgetConfigActivity.java`). Long-press the home screen, Widgets, Dotter.

## Tests

`npm test` runs `tests/smoke.js` twice, in normal and reduced-motion mode (89 checks each). It uses jsdom with synthetic forecast and geocoding responses, so it never touches the network. It covers data grouping (including a 25-hour clock-change day), scrubbing, place switching, place search and removal, hostile URL hashes, and the moon maths. It cannot judge layout or looks, so check changes in a real browser too, at 360 px and desktop width.

## How it works (technical notes)

- **Forecast:** `api.open-meteo.com/v1/forecast`, 7 days hourly, default `best_match` model blend, `timezone=auto`, mph. Every place is shown in its own local time. Days are grouped by date string, never by index arithmetic, because clock changes make 23 and 25 hour days.
- **Place search:** `geocoding-api.open-meteo.com/v1/search` (no key, CORS open). Typing waits 250 ms, newer searches cancel older ones, and the only thing sent is the text typed. Places within about 1 km of an existing chip select that chip instead of duplicating it.
- **Places and sharing:** an added place's id is its URL hash, `#Name,lat,lon` (for example `#Lisbon%2C%20PT,38.72,-9.13`), so a link works for someone who has not added it. The hash is untrusted input: it is validated (coordinate ranges, stripped markup) and only ever written as text. Up to 12 added places are saved in `localStorage` under `wx-custom`; the last chosen place is `wx-place`. Both are optional and wrapped in try/catch.
- **The moon:** phase, position and rise/set are calculated in the page from the date alone (low-precision astronomical formulas, about 0.3 degrees), so they need no data source and work for any city. Forecast times are local strings with no zone, so they are converted to UTC with `Intl` per hour. Rise and set are found by stepping the altitude every 5 minutes. The disc is tilted using the parallactic angle, so it is lit on the right in London and on the left in Sydney. It is drawn clear of the text: in the margins on wide screens, beside the temperature on phones.
- **Rendering:** state is split into `S` (what the user chose), `T` (target visuals for the selected hour) and `D` (what is drawn, eased towards `T` each frame). The frame loop stops when nothing is moving and pauses when the tab is hidden.
- **External requests:** only the Open-Meteo forecast and geocoding APIs and the Fraunces Google Font (with a serif fallback).

## Link it from the Hugo site

Do this in the `personal-website` repo, and run `hugo server` locally before pushing.

**1. Menu entry** in `config.toml`:

```toml
[[menu.main]]
  identifier = "weather"
  name = "Weather"
  url = "https://weather.nathankurien.com/"
  weight = 40
```

Adjust `weight` to place it in the nav.

**2. A small live header icon** (recommended): a sky-coloured dot plus the temperature, "● 11°", with "London · 11° Light rain" as the tooltip and screen-reader label.

- Copy `integration/weather-icon.html` to `layouts/partials/weather-icon.html` in the Hugo repo.
- PaperMod's header lives in the theme, which should not be edited. Copy the theme's `layouts/partials/header.html` to the same path in the root `layouts/` and include `{{ partial "weather-icon.html" . }}` next to the theme toggle there.
- The link works without JavaScript (the dot alone). The script adds the temperature with one Open-Meteo request straight from the visitor's browser, cached in `sessionStorage` for 10 minutes, and fails silently.
- To show another place, change `data-name`, `data-lat`, `data-lon` and the `#hash` in the link.
- It uses PaperMod's `--secondary` and `--primary` CSS variables; check these names against your PaperMod version.

**3. Alternative: a wider pill** ("London · 11° Light rain →"), in the style of the Spotify now-playing widget. Use `integration/weather-pill.html` the same way, for example on the home page layout: `{{ partial "weather-pill.html" . }}`. It also uses `--border`.

## Changing the default places

The twelve default chips (London first, then major cities around the world) are the `PLACES` array at the top of the script in `index.html` (id, name, latitude, longitude). The id is also the URL hash, so `/#tokyo` opens straight to Tokyo. Added places are appended to this list at runtime and are never written back to the file.

## Credits

Forecast data and place search by [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0, free for non-commercial use), with place names from [GeoNames](https://www.geonames.org/). Keep the footer attribution.
