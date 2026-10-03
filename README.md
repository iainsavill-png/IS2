# RailBoard: UK live train departures for Android

A simple Android app (Kotlin and Jetpack Compose) that shows live departures from any
National Rail station in Great Britain, using National Rail's official
**Live Departure Board (LDBWS)** API.

## Features

- Search all ~2,600 National Rail stations by name or 3-letter CRS code (for example `RDG`, `PAD`, `KGX`)
- Optional "Calling at" filter, so you only see trains that stop at your destination
- Scheduled time, expected time (on time, late, delayed or cancelled), platform, operator and coach count
- Delay and cancellation reasons, plus station disruption messages
- Replacement bus services
- Pull to refresh, with automatic refresh every 60 seconds while the app is open
- Recent stations as one-tap chips
- Material 3 styling with dark mode and dynamic colour

## 1. Get a free API key

1. Register at <https://raildata.org.uk>.
2. Find the **Live Departure Board** product (LDBWS) in the catalogue and subscribe to it. This is free.
3. Open the product's **Specification** tab and copy the **Consumer key**.
   Also check the endpoint URL listed there. The app defaults to
   `https://api1.raildata.org.uk/1010-live-departure-board-dep1_2/LDBWS/api/20220120`.
   If yours is different, you can change it in the app's settings.

## 2. Build and run

You need Android Studio (Ladybug or newer) or JDK 17 with the Android SDK.

```bash
./gradlew assembleDebug        # APK ends up in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
```

Or open the folder in Android Studio and press **Run**.

On first launch the app opens **Settings** so you can paste your key. You can also bake a
key into your local debug builds by adding this line to `local.properties`. Don't commit it.

```
RDM_API_KEY=your-consumer-key
```

### Download a built APK

Every push runs the **Android build** GitHub Actions workflow
(`.github/workflows/android.yml`). Download `railboard-debug-apk` from the run's artifacts
and sideload it onto your phone.

## Project layout

```
app/src/main/java/uk/railboard/app/
├── MainActivity.kt
├── data/
│   ├── Ldbws.kt          # REST client and JSON models for GetDepartureBoard
│   ├── Stations.kt       # station search over the bundled list
│   └── Settings.kt       # API key, API URL and recent stations (SharedPreferences)
└── ui/
    ├── DeparturesViewModel.kt
    ├── DeparturesScreen.kt   # Compose UI
    └── theme/Theme.kt
app/src/main/resources/uk/railboard/app/data/stations.csv   # every GB station (CRS,Name)
```

The station list comes from
[davwheat/uk-railway-stations](https://github.com/davwheat/uk-railway-stations). To refresh it,
regenerate `stations.csv` from that repo's `stations.json`.

## Ideas for next steps

- Service details screen (`GetServiceDetails`) showing calling points
- Arrivals board (`GetArrivalBoard`)
- Home-screen widget for a favourite route
