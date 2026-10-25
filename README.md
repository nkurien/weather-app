# Weather

A colour-driven London weather page. One static `index.html`, no build, data from [Open-Meteo](https://open-meteo.com/).

## Run locally

```
npm run serve        # then open http://localhost:8080/#london
npm install && npm test
```

(Or just open `index.html` in a browser; it needs internet for the forecast.)

## Deploy to weather.nathankurien.com

1. Put this folder in its own GitHub repo (for example `nkurien/weather`).
2. Cloudflare dashboard, Workers & Pages, Create, Pages, connect the repo.
3. Build command: none. Build output directory: `/` (the repo root). Deploy.
4. In the Pages project, Custom domains, add `weather.nathankurien.com`. Cloudflare creates the DNS record if the domain is on Cloudflare.

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

**2. Optional live pill** ("London · 11° Light rain →"), in the style of the Spotify now-playing widget:

- Copy `integration/weather-pill.html` to `layouts/partials/weather-pill.html` in the Hugo repo.
- Include it where you want it, for example on the home page layout: `{{ partial "weather-pill.html" . }}`
- It uses PaperMod's `--border`, `--secondary` and `--primary` CSS variables; check these names against your PaperMod version.
- It calls Open-Meteo straight from the visitor's browser (no Cloudflare function needed) and fails silently, so it never breaks the page.

## Changing locations

Edit the `PLACES` array at the top of the script in `index.html` (id, name, latitude, longitude). The id is also the URL hash, so `/#greenwich` opens straight to Greenwich.

## Credits

Forecast data by [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0, free for non-commercial use). Keep the footer attribution.
