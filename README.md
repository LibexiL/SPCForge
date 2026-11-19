# SPCForge

An Android app for everything on the **NOAA Storm Prediction Center** website ([spc.noaa.gov](https://www.spc.noaa.gov))
and the **National Hurricane Center** ([nhc.noaa.gov](https://www.nhc.noaa.gov)), laid out for a phone instead of a desktop browser.

**Contents:** [Install](#install) · [What's in it](#whats-in-it) · [Where the data comes from](#where-the-data-comes-from) ·
[Building from source](#building-from-source) · [Changelog](CHANGELOG.md)

---

## Install

You need **Android 8.0 or newer**.

1. On your phone, open the [**Releases**](https://github.com/LibexiL/SPCForge/releases) page and download
   **`SPCForge-….apk`** from the newest release.
2. Open the downloaded file. If Android asks, allow installs from your browser (**Settings → Allow from this source**), then tap **Install**.
   - If **Google Play Protect** says it doesn't recognise the app, tap **More details → Install anyway**.
3. Open **SPCForge**. Tap **Use my location** on the Home tab if you want your own risk level and the watches that cover you
   (optional – everything else works without it).

**Updating:** SPCForge looks for a new version about once a day and offers it (**More → Settings → Check for updates now**
looks straight away). Your settings are kept.

---

## What's in it

| Tab | What you get |
|---|---|
| **Home** | Active hurricanes and tropical storms (with a banner if one is within 600 miles of you), today's risk at your location (Marginal → High, plus tornado / wind / hail %), Day 2 and Day 3 at your location, a banner when a watch or mesoscale discussion covers you, the Day 1 map with the highest risk today, every active watch and MD, today's storm-report counts, Days 2–3 maps |
| **Outlooks** | Day 1 / 2 / 3: categorical, tornado, wind and hail maps (Day 3: categorical + probabilistic), the risk-area table with population and cities, the full forecast discussion. Days 4–8: all five maps and the discussion |
| **Watches** | Every active tornado and severe-thunderstorm watch (expiry, states, counties, PDS), and every mesoscale discussion (concerning, chance of a watch). Tap one for its graphic, full text and probabilities |
| **Reports** | Today's and yesterday's storm reports: SPC's map, totals, and a list you can filter by tornado / wind / hail / significant; tap a report to see the spot in your maps app |
| **Tropics** | Every active tropical cyclone (Atlantic, eastern and central Pacific) with category, winds, pressure, movement and distance from you. Tap one for the forecast cone, key messages, wind-arrival and wind-probability maps, storm surge and rainfall graphics, and the public advisory, discussion, forecast advisory and wind probabilities. Below that, each basin's 7-day and 2-day tropical weather outlook, its text and the Tropical Weather Discussion |
| **More** | Fire weather outlooks (Day 1, 2, 3–8), thunderstorm outlooks, SPC mesoanalysis (10 sectors × 30 fields: CAPE, shear, SRH, STP, SCP, upper-air…), settings |

Every graphic opens full screen with pinch and double-tap zoom. Discussion text is re-wrapped to fit a phone screen
(switch to **As issued** for the original fixed-width text). The ↗ button at the top opens the same page on spc.noaa.gov,
and ↻ reloads everything.

## Where the data comes from

- **spc.noaa.gov** – all maps, graphics, discussion text, watch and MD pages, storm reports (CSV) and mesoanalysis images.
- **api.weather.gov** (NWS) – the list of active watches with their counties and end times, and the watches covering your location.
- **nhc.noaa.gov** (National Hurricane Center) – `CurrentStorms.json` for active storms, storm graphics, advisory and
  discussion text, tropical weather outlook maps and text.
- **Iowa Environmental Mesonet** – SPC outlook polygons (to work out the risk at your location) and the active mesoscale
  discussion list; also a backup source for SPC and NHC product text.

SPCForge is not an official NOAA / NWS app. For life-safety decisions, always follow your local NWS office and warnings.

## Building from source

Only **Java 17+** is needed (plus `curl`, `unzip`, `zip`). The Android build tools are downloaded once into `.tools/`
and checked against fixed SHA-256 fingerprints – no Android Studio or SDK install.

```bash
bash build.sh          # builds build/SPCForge-<version>.apk
bash build.sh test     # runs the SPC and NHC parser tests on the JVM
```

Every push is built by GitHub Actions. To ship a new version, bump `android:versionCode` and `android:versionName`
in `app/AndroidManifest.xml`, add a `## <version>` entry to `CHANGELOG.md`, and push to `main`: the workflow sees a
version with no release yet and publishes `v<version>` with the APK attached (the app's updater reads it).

### Project layout

```
app/src/com/libexil/spcforge/
  core/        SPC / NHC / NWS / IEM fetching and parsing (plain Kotlin, tested on the JVM)
  screens/     Home, Outlooks, Watches & MDs, Reports, Tropics, More, Mesoanalysis, Settings
  ui/          widgets, icons, image loading and the zoomable viewer
  MainActivity.kt, Updater.kt, App.kt
tests/         parser tests (bash build.sh test)
```

## License

MIT – see [LICENSE](LICENSE).
