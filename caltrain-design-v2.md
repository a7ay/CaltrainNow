# CaltrainNow — Architecture & Design Document (v2)

## 1. Overview

**CaltrainNow** is an Android application that helps a commuter quickly find the next two Caltrain departures based on their current location and time. The app auto-detects travel direction (northbound vs southbound) by comparing proximity to home and work locations, and offers one-tap navigation to the departure station via Google Maps.

**All data and logic lives entirely on-device.** No backend server required.

---

## 2. Decisions Log

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Platform | Android (Kotlin) | User preference |
| Architecture | On-device only | Simple, no server costs, works offline |
| Database | Room (SQLite) | On-device, fast, first-class Android support |
| Schedule source | GTFS (structured data) | Reliable, machine-readable, industry standard |
| GTFS URL | `https://data.trilliumtransit.com/gtfs/caltrain-ca-us/caltrain-ca-us.zip` | Official URL from Caltrain developer resources |
| Direction logic | Auto-detect (home→work = NB, work→home = SB) | Zero-friction UX |
| Navigation | Google Maps intents (no API key needed) | Free, native navigation experience |
| Abstraction layer | ScheduleDataSource interface | Future flexibility at near-zero cost |
| Nearest Station Logic | Preferred Location Bias | Prioritize Home/Work stations within 1 mile of absolute nearest |

---

## 3. Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| Min SDK | 26 (Android 8.0) |
| Architecture | MVVM + Repository pattern |
| Database | Room (SQLite) |
| Networking | Retrofit + OkHttp (GTFS download only) |
| DI | Hilt |
| Location | Google Play Services (FusedLocationProvider) |
| Navigation | Google Maps Intents (external, no API key) |
| Async | Kotlin Coroutines + Flow |
| Build | Gradle (Kotlin DSL) |

---

## 4. Data Source — Caltrain GTFS

Caltrain publishes a GTFS (General Transit Feed Specification) zip file at:

```
https://data.trilliumtransit.com/gtfs/caltrain-ca-us/caltrain-ca-us.zip
```

The GTFS zip contains several CSV files. We use these:

| File | Purpose |
|------|---------|
| `stops.txt` | Station names, lat/long coordinates |
| `stop_times.txt` | Arrival/departure times per trip per stop |
| `trips.txt` | Trip ID → route, direction (0=NB, 1=SB), service ID |
| `routes.txt` | Route info (local, limited, express/bullet) |
| `calendar.txt` | Service ID → days of week + date range |
| `calendar_dates.txt` | Service exceptions (holidays, special schedules) |

---

## 5. Database Schema (Room)

### 5.1 Entity: `Station`
```
station_id    TEXT  PRIMARY KEY   -- GTFS stop_id
station_name  TEXT  NOT NULL      -- e.g. "San Francisco"
latitude      REAL  NOT NULL
longitude     REAL  NOT NULL
parent_id     TEXT  NULLABLE      -- parent station (GTFS uses child stops per platform)
```

### 5.2 Entity: `Trip`
```
trip_id       TEXT  PRIMARY KEY
route_id      TEXT  NOT NULL
service_id    TEXT  NOT NULL
direction     INT   NOT NULL      -- 0 = Northbound, 1 = Southbound
trip_headsign TEXT  NULLABLE      -- e.g. "San Francisco" or "San Jose"
```

### 5.3 Entity: `StopTime`
```
id            INT   PRIMARY KEY AUTOINCREMENT
trip_id       TEXT  NOT NULL  (FK → Trip)
station_id    TEXT  NOT NULL  (FK → Station)
arrival_time  TEXT  NOT NULL      -- HH:MM:SS (can exceed 24:00 for late-night)
departure_time TEXT NOT NULL
stop_sequence INT   NOT NULL
```

### 5.4 Entity: `ServiceCalendar`
```
service_id    TEXT  PRIMARY KEY
monday        INT   NOT NULL      -- 1 = active, 0 = inactive
tuesday       INT   NOT NULL
wednesday     INT   NOT NULL
thursday      INT   NOT NULL
friday        INT   NOT NULL
saturday      INT   NOT NULL
sunday        INT   NOT NULL
start_date    TEXT  NOT NULL      -- YYYYMMDD
end_date      TEXT  NOT NULL      -- YYYYMMDD
```

### 5.5 Entity: `ServiceException`
```
id            INT   PRIMARY KEY AUTOINCREMENT
service_id    TEXT  NOT NULL
date          TEXT  NOT NULL      -- YYYYMMDD
exception_type INT  NOT NULL      -- 1 = added, 2 = removed
```

### 5.6 Entity: `ScheduleMetadata`
```
id            INT   PRIMARY KEY (always 1, singleton row)
downloaded_at TEXT  NOT NULL      -- ISO timestamp
gtfs_url      TEXT  NOT NULL
station_count INT   NOT NULL
trip_count    INT   NOT NULL
stop_time_count INT NOT NULL
```

---

## 6. User Configuration

Stored in **DataStore** (Jetpack):

```kotlin
data class UserConfig(
    val homeLatitude: Double,       // e.g. 37.3861
    val homeLongitude: Double,      // e.g. -122.0839
    val homeLabel: String,          // e.g. "Sunnyvale"
    val workLatitude: Double,       // e.g. 37.7749
    val workLongitude: Double,      // e.g. -122.4194
    val workLabel: String           // e.g. "San Francisco"
)
```

Set once via a settings screen. Current location is obtained at runtime via `FusedLocationProviderClient`.

---

## 7. Core Abstraction — ScheduleDataSource Interface

All lookup logic depends on this interface, not directly on Room. This keeps the core logic pure Kotlin and portable.

```kotlin
interface ScheduleDataSource {
    suspend fun getAllStations(): List<Station>
    suspend fun getStationById(stationId: String): Station?
    suspend fun getActiveServiceIds(date: LocalDate): List<String>
    suspend fun getNextDepartures(
        stationId: String,
        direction: Int,
        serviceIds: List<String>,
        afterTime: String,
        limit: Int
    ): List<StopTimeWithTrip>
    suspend fun getArrivalAtStation(tripId: String, stationId: String): StopTime?
    suspend fun getStationCount(): Int
    suspend fun getTripCount(): Int
    suspend fun getStopTimeCount(): Int
    suspend fun getTripCountByDirection(direction: Int): Int
    suspend fun hasOrphanedStopTimes(): Boolean
    suspend fun hasValidStopSequences(): Boolean
    suspend fun stationExists(name: String): Boolean
    suspend fun clearAll()
    suspend fun insertStations(stations: List<Station>)
    suspend fun insertTrips(trips: List<Trip>)
    suspend fun insertStopTimes(stopTimes: List<StopTime>)
    suspend fun insertServiceCalendars(calendars: List<ServiceCalendar>)
    suspend fun insertServiceExceptions(exceptions: List<ServiceException>)
    suspend fun insertMetadata(metadata: ScheduleMetadata)
}
```

Room implementation:

```kotlin
class RoomScheduleDataSource(
    private val db: CaltrainDatabase
) : ScheduleDataSource {
    // Delegates each method to the appropriate Room DAO
}
```

---

## 8. Core Logic — Pure Kotlin (No Android Dependencies)

### 8.1 `GtfsParser`

Parses raw GTFS CSV files into domain objects.

```kotlin
class GtfsParser {
    fun parseStops(reader: BufferedReader): List<Station>
    fun parseTrips(reader: BufferedReader): List<Trip>
    fun parseStopTimes(reader: BufferedReader): List<StopTime>
    fun parseCalendar(reader: BufferedReader): List<ServiceCalendar>
    fun parseCalendarDates(reader: BufferedReader): List<ServiceException>
}
```

### 8.2 `GeoUtils`

```kotlin
object GeoUtils {
    fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double
    fun findNearestStation(
        lat: Double, 
        lng: Double, 
        stations: List<Station>,
        preferredStationIds: Set<String> = emptySet(),
        biasThresholdMeters: Double = 1609.34
    ): Station?
}
```

**Algorithm:**
1. Find the absolute nearest station using Haversine distance.
2. If `preferredStationIds` is provided, find the closest station among them.
3. If a preferred station is within `biasThresholdMeters` (default 1 mile) of the absolute nearest station, return the preferred station.
4. Otherwise, return the absolute nearest station.

### 8.3 `TimeUtils`

Handles GTFS time format, including after-midnight times like `25:30:00`.

```kotlin
object TimeUtils {
    fun parseGtfsTime(timeStr: String): GtfsTime
    fun isAfter(current: GtfsTime, departure: GtfsTime): Boolean
    fun minutesUntil(current: GtfsTime, departure: GtfsTime): Long
    fun currentGtfsTime(localTime: LocalTime): GtfsTime
}

data class GtfsTime(val totalMinutes: Int) {
    val hours: Int get() = totalMinutes / 60
    val minutes: Int get() = totalMinutes % 60
}
```

### 8.4 `DirectionResolver`

```kotlin
class DirectionResolver(private val userConfig: UserConfig) {
    fun resolve(
        currentLat: Double,
        currentLng: Double,
        stations: List<Station>
    ): DirectionResult

    data class DirectionResult(
        val direction: Direction,
        val reason: String,
        val destinationStationId: String
    )

    enum class Direction(val gtfsValue: Int) {
        NORTHBOUND(0),
        SOUTHBOUND(1)
    }
}
```

**Algorithm:**
1. Calculate distance from current location to home and to work.
2. If closer to home → heading to work → determine direction based on relative position of home/work stations on the Caltrain line.
3. If closer to work → heading home → opposite direction.

### 8.5 `ServiceResolver`

```kotlin
class ServiceResolver(private val dataSource: ScheduleDataSource) {
    suspend fun getActiveServiceIds(date: LocalDate): List<String>
}
```

Applies `calendar.txt` day-of-week rules and `calendar_dates.txt` exceptions.

### 8.6 `ScheduleValidator`

```kotlin
class ScheduleValidator(private val dataSource: ScheduleDataSource) {
    suspend fun validate(): ValidationResult

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>
    )
}
```

**Validation checks:**
- Station count > 0 (flag if < 20)
- Trip count > 0 for each direction (NB and SB)
- Every StopTime references a valid trip_id and station_id
- At least one active service calendar entry covers today's date
- Departure times are in ascending order per trip (stop_sequence)
- Known anchor stations exist: San Francisco, San Jose Diridon, Palo Alto, Mountain View, Millbrae
- No duplicate trip_id + station_id + stop_sequence combos

### 8.7 `LookupEngine`

The central query orchestrator. This is the "API" of the app.

```kotlin
class LookupEngine(
    private val dataSource: ScheduleDataSource,
    private val directionResolver: DirectionResolver,
    private val serviceResolver: ServiceResolver
) {
    suspend fun lookupNextTrains(
        currentLat: Double,
        currentLng: Double,
        currentDateTime: LocalDateTime
    ): TrainLookupResult
}
```

**Returns:**

```kotlin
data class TrainLookupResult(
    val nearestStation: StationInfo,
    val direction: Direction,
    val directionReason: String,
    val nextTrains: List<TrainDeparture>,   // up to 2
    val stationDistanceMeters: Double
)

data class StationInfo(
    val stationId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class TrainDeparture(
    val tripId: String,
    val departureTime: String,
    val arrivalTimeAtDestination: String?,
    val minutesUntilDeparture: Long,
    val headsign: String,
    val routeType: String,        // "Local", "Limited", "Express"
    val trainNumber: String
)
```

**Algorithm:**
1. Find nearest station (GeoUtils.findNearestStation) with Home/Work bias.
2. Determine direction (DirectionResolver.resolve).
3. Get active services for today (ServiceResolver.getActiveServiceIds).
4. Query next 2 departures from the data source.
5. Look up arrival time at destination station for each trip.
6. Return result.

---

## 9. Android-Specific Layer

These components depend on Android frameworks and wire the pure Kotlin core to the device.

### 9.1 `GtfsDownloader`

Downloads and unzips the GTFS file.

```kotlin
class GtfsDownloader(
    private val context: Context,
    private val httpClient: OkHttpClient
) {
    suspend fun download(url: String): File    // Returns path to unzipped directory
}
```

### 9.2 `CaltrainRepository`

Orchestrates the full init flow and exposes the LookupEngine to the ViewModel.

```kotlin
class CaltrainRepository(
    private val downloader: GtfsDownloader,
    private val parser: GtfsParser,
    private val dataSource: ScheduleDataSource,
    private val validator: ScheduleValidator,
    private val lookupEngine: LookupEngine
) {
    suspend fun initialize(): InitResult
    suspend fun validate(): ValidationResult
    suspend fun lookupNextTrains(lat: Double, lng: Double, dateTime: LocalDateTime): TrainLookupResult
}
```

**Initialize flow (safe):**
1. Download GTFS zip → save to cache directory.
2. Parse all CSV files into domain objects (in memory).
3. Validate parsed data (check counts, references, anchor stations).
4. If valid: clear existing DB → insert all new data in a single transaction.
5. Store metadata (timestamp, counts).
6. If invalid: keep existing data, return errors.

### 9.3 `LocationProvider`

```kotlin
class LocationProvider(
    private val fusedClient: FusedLocationProviderClient
) {
    suspend fun getCurrentLocation(): LatLng
}
```

### 9.4 `NavigationUtils`

Builds Google Maps intent URIs. No API key needed.

```kotlin
object NavigationUtils {
    fun getNavigationIntent(
        destinationLat: Double,
        destinationLng: Double,
        mode: TravelMode = TravelMode.WALKING
    ): Intent {
        val uri = Uri.parse(
            "google.navigation:q=$destinationLat,$destinationLng&mode=${mode.code}"
        )
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
    }

    enum class TravelMode(val code: String) {
        WALKING("w"),
        DRIVING("d")
    }
}
```

---

## 10. Project Structure

```
app/
├── src/main/java/com/caltrainnow/
│   ├── CaltrainApp.kt                        # Application class (Hilt)
│   │
│   ├── core/                                  # ★ Pure Kotlin — no Android imports
│   │   ├── model/
│   │   │   ├── Station.kt
│   │   │   ├── Trip.kt
│   │   │   ├── StopTime.kt
│   │   │   ├── ServiceCalendar.kt
│   │   │   ├── ServiceException.kt
│   │   │   ├── ScheduleMetadata.kt
│   │   │   ├── TrainLookupResult.kt
│   │   │   ├── TrainDeparture.kt
│   │   │   ├── InitResult.kt
│   │   │   └── ValidationResult.kt
│   │   ├── datasource/
│   │   │   └── ScheduleDataSource.kt         # Interface
│   │   ├── parser/
│   │   │   └── GtfsParser.kt
│   │   ├── engine/
│   │   │   ├── LookupEngine.kt
│   │   │   ├── DirectionResolver.kt
│   │   │   └── ServiceResolver.kt
│   │   ├── validation/
│   │   │   └── ScheduleValidator.kt
│   │   └── util/
│   │       ├── GeoUtils.kt
│   │       └── TimeUtils.kt
│   │
│   ├── data/                                  # Android-specific data layer
│   │   ├── db/
│   │   │   ├── CaltrainDatabase.kt            # Room database definition
│   │   │   ├── RoomScheduleDataSource.kt      # Implements ScheduleDataSource
│   │   │   └── dao/
│   │   │       ├── StationDao.kt
│   │   │       ├── TripDao.kt
│   │   │       ├── StopTimeDao.kt
│   │   │       └── ServiceDao.kt
│   │   ├── gtfs/
│   │   │   └── GtfsDownloader.kt              # HTTP download + unzip
│   │   ├── location/
│   │   │   └── LocationProvider.kt            # FusedLocationProvider wrapper
│   │   └── repository/
│   │       └── CaltrainRepository.kt          # Orchestrates init + lookup
│   │
│   ├── di/                                    # Hilt dependency injection modules
│   │   ├── DatabaseModule.kt
│   │   ├── NetworkModule.kt
│   │   └── AppModule.kt
│   │
│   ├── ui/                                    # Phase 2
│   │   ├── home/
│   │   │   ├── HomeScreen.kt
│   │   │   └── HomeViewModel.kt
│   │   ├── settings/
│   │   │   ├── SettingsScreen.kt
│   │   │   └── SettingsViewModel.kt
│   │   └── theme/
│   │       └── Theme.kt
│   │
│   └── util/
│       └── NavigationUtils.kt                 # Google Maps intent builder
│
├── src/test/                                  # JVM unit tests (no Android)
│   ├── resources/
│   │   └── gtfs/                              # Small fixture CSVs
│   │       ├── stops.txt
│   │       ├── stop_times.txt
│   │       ├── trips.txt
│   │       ├── routes.txt
│   │       ├── calendar.txt
│   │       └── calendar_dates.txt
│   └── kotlin/com/caltrainnow/core/
│       ├── parser/GtfsParserTest.kt
│       ├── util/GeoUtilsTest.kt
│       ├── util/TimeUtilsTest.kt
│       ├── engine/DirectionResolverTest.kt
│       ├── engine/ServiceResolverTest.kt
│       ├── engine/LookupEngineTest.kt
│       └── validation/ScheduleValidatorTest.kt
│
├── src/androidTest/                           # Instrumented tests (emulator)
│   ├── assets/
│   │   └── test_gtfs.zip                      # Small valid GTFS for testing
│   └── kotlin/com/caltrainnow/
│       ├── data/InitializeIntegrationTest.kt
│       ├── data/ValidationIntegrationTest.kt
│       └── data/LookupIntegrationTest.kt
```

### Key Structural Principle

```
core/  → Pure Kotlin. Zero Android imports. Testable on JVM.
         Contains: models, ScheduleDataSource interface, parser,
         lookup engine, direction resolver, service resolver,
         validator, geo utils, time utils.

data/  → Android-specific. Depends on Room, OkHttp, FusedLocation.
         Contains: Room DB, DAOs, RoomScheduleDataSource (implements
         the interface), downloader, location provider, repository.
```

The `core/` package could be extracted into a standalone Kotlin module or KMP library in the future with zero changes.

---

## 11. Data Flow

### 11.1 Initialize (first launch or manual refresh)

```
User taps "Update Schedule"
        │
        ▼
CaltrainRepository.initialize()
        │
        ├─► GtfsDownloader.download(GTFS_URL)
        │       → Downloads zip to cache dir
        │       → Unzips to temp directory
        │
        ├─► GtfsParser.parse*(readers)
        │       → Parses CSVs into domain objects in memory
        │
        ├─► ScheduleValidator.validate(parsedData)
        │       → Checks counts, references, anchor stations
        │       → Returns ValidationResult
        │
        ├─► If valid:
        │       RoomScheduleDataSource.clearAll()
        │       RoomScheduleDataSource.insertStations(...)
        │       RoomScheduleDataSource.insertTrips(...)
        │       RoomScheduleDataSource.insertStopTimes(...)
        │       RoomScheduleDataSource.insertServiceCalendars(...)
        │       RoomScheduleDataSource.insertServiceExceptions(...)
        │       RoomScheduleDataSource.insertMetadata(...)
        │       (All in a single @Transaction)
        │
        └─► Return InitResult(success, counts, errors)
```

### 11.2 Lookup (every time user opens app or refreshes)

```
App launches / user pulls to refresh
        │
        ▼
LocationProvider.getCurrentLocation()
        │ returns (lat, lng)
        ▼
LookupEngine.lookupNextTrains(lat, lng, now)
        │
        ├─► GeoUtils.findNearestStation(lat, lng, allStations, preferredStationIds)
        │       → Returns closest station (with Home/Work bias)
        │
        ├─► DirectionResolver.resolve(lat, lng, stations)
        │       → Compares distance to home vs work
        │       → Returns NORTHBOUND or SOUTHBOUND
        │
        ├─► ServiceResolver.getActiveServiceIds(today)
        │       → Checks calendar + exceptions for today
        │       → Returns list of active service IDs
        │
        ├─► ScheduleDataSource.getNextDepartures(
        │       stationId, direction, serviceIds, currentTime, limit=2)
        │       → SQL query, returns next 2 departures
        │
        ├─► For each departure:
        │       ScheduleDataSource.getArrivalAtStation(tripId, destStationId)
        │       → Looks up when this train arrives at home/work station
        │
        └─► Return TrainLookupResult
                │
                ▼
        UI displays:
        ┌─────────────────────────────────┐
        │  📍 Nearest: Palo Alto (320m)   │
        │  → Heading to work (Northbound) │
        │                                 │
        │  🚂 8:43 AM  Express #507       │
        │     Arrives SF 9:22 AM (13 min) │
        │     [Navigate to Station]       │
        │                                 │
        │  🚂 8:55 AM  Local #115         │
        │     Arrives SF 9:46 AM (25 min) │
        │     [Navigate to Station]       │
        └─────────────────────────────────┘
```

### 11.3 Navigate (one-tap)

```
User taps "Navigate to Station"
        │
        ▼
NavigationUtils.getNavigationIntent(stationLat, stationLng, WALKING)
        │
        ▼
Opens Google Maps with walking directions to station
```

---

## 12. Build Phases

### Phase 1: Core Logic + Data Layer (current scope)
- Pure Kotlin core: parser, geo utils, time utils, direction resolver, service resolver, lookup engine, validator
- ScheduleDataSource interface + Room implementation
- GTFS downloader
- Unit tests for all core logic
- Integration tests for init → validate → lookup flow

### Phase 2: Android UI
- Home screen showing next 2 trains with countdown timers
- Settings screen for home/work locations (map picker or address entry)
- Pull-to-refresh for schedule update
- One-tap "Navigate to Station" button
- Location permission handling

### Phase 3: Enhancements
- Home screen widget (at-a-glance next train)
- Push notification X minutes before departure
- Favorite stations override auto-detect
- Schedule staleness check (prompt refresh if data is old)
- Weekend/holiday schedule indicator in UI

---

## 13. Key Design Decisions & Rationale

| Decision | Rationale |
|----------|-----------|
| GTFS over PDF | Structured, machine-readable, industry standard. PDF parsing is fragile. |
| Room/SQLite | No server costs, works offline, fast rewards, first-class Android support. |
| Google Maps intents | Free (no API key), native navigation experience, one line of code. |
| ScheduleDataSource interface | Separates pure Kotlin logic from Room. Enables future portability (KMP, server) at near-zero cost now. |
| Safe init (parse → validate → swap) | Prevents data loss if download is corrupt. Old schedule preserved until new one is confirmed valid. |
| Auto-detect direction | Reduces friction — open the app, see your trains immediately. |
| core/ vs data/ package split | core/ has zero Android imports, testable on JVM. data/ wires to Android frameworks. |
| Preferred Location Bias | Improves UX for commuters by locking to Home/Work stations when in proximity (1 mile). |

---

## 14. GTFS Edge Cases to Handle

| Edge case | How we handle it |
|-----------|-----------------|
| After-midnight times (e.g. `25:30:00`) | GtfsTime stores total minutes, not hours:minutes. TimeUtils handles comparison across midnight. |
| Child stops per platform | Group by parent_station. Match nearest by parent, not platform. |
| Express trains skip stations | StopTime only exists for stations the train actually stops at. Lookup query naturally excludes skipped stations. |
| Weekend/holiday schedules | ServiceResolver checks day-of-week from calendar.txt, then applies calendar_dates.txt exceptions. |
| GTFS file has BOM | GtfsParser strips UTF-8 BOM from first column if present. |
| Empty/missing optional files | calendar_dates.txt may be empty. Parser returns empty list, no crash. |
| Duplicate stop_sequences | Validator flags as error. |
| Schedule becomes stale | ScheduleMetadata tracks download timestamp. UI can warn if data is > 30 days old. |

---

## 15. Open Questions for Phase 2

1. **UI framework:** Jetpack Compose (modern) vs XML Views (traditional)?
2. **Location picker for settings:** Manual lat/long entry, address search, or map tap?
3. **Refresh trigger:** Manual only, or auto-check on app launch if data > N days old?
4. **Train type display:** Show route type (Local/Limited/Express) as color-coded badges?
