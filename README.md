# CaltrainNow

An Android app that shows you the next available Caltrain departures to get you home or to the office. The app auto-detects your travel direction based on your current location and your configured home/work stations.

**All data and logic lives entirely on-device.** No backend server required.

---

## Features

- Nearest station auto-detection with home/work preference bias
- Auto-detected travel direction (northbound / southbound)
- Next 4 upcoming train departures with countdown timers
- Arrival time at destination for each train
- Daily weather summary (icon + high/low °F) for both departure and destination stations
- One-tap navigation to the departure station via Google Maps
- Offline-first — schedule stored locally via Room/SQLite (GTFS)

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material3
- **Architecture:** MVVM + Repository pattern
- **Database:** Room (SQLite)
- **DI:** Hilt
- **Networking:** OkHttp (GTFS download + weather API)
- **Location:** Google Play Services (FusedLocationProvider)
- **Preferences:** Jetpack DataStore
- **Async:** Kotlin Coroutines + Flow

---

## Changelog

### v1.3 — Daily Weather for Both Stations
- Added daily weather summary card on the Home screen showing departure and destination station weather side by side
- Weather data sourced from **Open-Meteo** (`api.open-meteo.com`) — free, open source, no API key required
- Shows WMO weather condition icon (emoji), description, and daily high/low in °F
- Weather is fetched once per day per station and cached via DataStore; subsequent opens reuse the cached value until midnight
- New files: `WeatherInfo.kt`, `WeatherService.kt`, `WeatherCache.kt`, `WeatherRow.kt`
- `TrainLookupResult` now includes `destinationStation: StationInfo?` so destination coordinates are available for the weather fetch

### v1.2 — Show 4 Upcoming Trains
- Increased the default number of displayed departures from 2 to 4
- Changed default `limit` parameter in `LookupEngine` and `ScheduleDataSource`

### v1.1 — Home/Work Location Bias
- Nearest station lookup now biases toward the user's configured home or work station when within 1 mile of the absolute nearest station
- Prevents snapping to a nearby but incorrect platform when commuting from a familiar stop

### v1.0 — Initial Release
- On-device GTFS schedule download, parse, and storage
- Nearest station detection via Haversine distance
- Auto-direction detection (northbound / southbound) based on home/work proximity
- Next 2 train departures with countdown and arrival time at destination
- One-tap Google Maps navigation to departure station
- Settings screen for home/work location configuration
- Pull-to-refresh for schedule and train lookup

---

## Setup

See [`caltrain-android-studio-setup.md`](caltrain-android-studio-setup.md) for build and run instructions.
