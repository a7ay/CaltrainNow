# CaltrainNow — Testing Strategy

## 1. Testing Layers Overview

```
┌─────────────────────────────────────────────┐
│         UI / Instrumented Tests             │  ← Android device/emulator
│         (Phase 2 — when UI is built)        │
├─────────────────────────────────────────────┤
│         Integration Tests                   │  ← Real Room DB, real GTFS data
│         (Repository + DB + Parser)          │
├─────────────────────────────────────────────┤
│         Unit Tests                          │  ← Pure Kotlin, no Android deps
│         (Parser, GeoUtils, TimeUtils,       │
│          Direction Logic, Query Logic)      │
└─────────────────────────────────────────────┘
```

We focus **heavily** on unit and integration tests for Phase 1 (the API layer), since there's no UI yet.

---

## 2. Unit Tests (Pure Kotlin — runs on JVM, no emulator needed)

### 2.1 GTFS Parser Tests (`GtfsParserTest`)

These validate that raw GTFS CSV files are parsed correctly into our domain objects.

| Test | What it verifies |
|------|-----------------|
| `parseStops_validFile_returnsAllStations` | Parses `stops.txt` with known row count, verifies station names and coordinates |
| `parseStops_handlesParentAndChildStops` | Parent stations (location_type=1) and child platforms (location_type=0) are grouped correctly |
| `parseStops_malformedRow_skipsAndLogs` | A row with missing fields is skipped without crashing |
| `parseStopTimes_validFile_returnsCorrectTimes` | Departure/arrival times parsed correctly, including sequence order |
| `parseStopTimes_handlesAfterMidnight` | Times like `25:30:00` are parsed and stored correctly (not rejected) |
| `parseTrips_validFile_returnsDirectionAndService` | direction_id (0/1) and service_id mapped correctly |
| `parseCalendar_validFile_returnsDayFlags` | Monday–Sunday flags and date ranges parsed correctly |
| `parseCalendarDates_handlesExceptions` | Exception type 1 (added) and 2 (removed) parsed correctly |
| `parseEmptyFile_returnsEmptyList` | Graceful handling of empty CSV |
| `parseFileWithBOM_handlesCorrectly` | UTF-8 BOM at start of file doesn't corrupt first column |

**Test data approach:** Create small fixture CSV files (5–10 rows each) in `src/test/resources/gtfs/` that mirror the real GTFS structure. This keeps tests fast and deterministic.

### 2.2 GeoUtils Tests (`GeoUtilsTest`)

| Test | What it verifies |
|------|-----------------|
| `haversineDistance_knownPoints_returnsCorrectDistance` | SF (37.7749, -122.4194) to SJ Diridon (37.3297, -121.9020) ≈ 48–49 km |
| `haversineDistance_samePoint_returnsZero` | Distance from a point to itself is 0 |
| `haversineDistance_antipodalPoints_returnsHalfEarth` | Sanity check for max distance |
| `findNearestStation_fromKnownLocation_returnsCorrect` | Standing at Palo Alto Caltrain lat/long → returns Palo Alto station |
| `findNearestStation_betweenTwoStations_returnsCloser` | Midpoint biased toward one station → returns that station |
| `findNearestStation_emptyList_returnsNull` | Graceful handling |

### 2.3 TimeUtils Tests (`TimeUtilsTest`)

| Test | What it verifies |
|------|-----------------|
| `parseGtfsTime_normalTime_returnsCorrect` | "08:30:00" → 8h 30m |
| `parseGtfsTime_afterMidnight_returnsCorrect` | "25:30:00" → 25h 30m (next day 1:30 AM on same service day) |
| `parseGtfsTime_midnight_returnsCorrect` | "24:00:00" → handled as valid |
| `isTimeAfter_currentBeforeDeparture_returnsTrue` | 8:00 AM current, 8:15 AM departure → true |
| `isTimeAfter_afterMidnightDeparture_handlesCorrectly` | 11:50 PM current, "25:30:00" departure → correctly identifies as future |
| `minutesUntilDeparture_returnsCorrect` | Current 8:00, departure 8:22 → 22 minutes |

### 2.4 Direction Logic Tests (`DirectionResolverTest`)

| Test | What it verifies |
|------|-----------------|
| `resolve_nearHome_returnsTowardWork` | User at home coords → returns direction toward work |
| `resolve_nearWork_returnsTowardHome` | User at work coords → returns direction toward home |
| `resolve_exactlyMidpoint_defaultsToNorthbound` | Edge case: equidistant → has a defined default |
| `resolve_atHomeStation_northOfWork_returnsSouthbound` | Home in SF, work in SJ → southbound |
| `resolve_atHomeStation_southOfWork_returnsNorthbound` | Home in SJ, work in SF → northbound |
| `resolve_determinesDirectionFromStationOrder` | Uses relative station positions on the line, not just lat/long |

### 2.5 Service Calendar Tests (`ServiceResolverTest`)

| Test | What it verifies |
|------|-----------------|
| `activeServices_weekday_returnsWeekdayServices` | Monday → returns service IDs with monday=1 |
| `activeServices_saturday_returnsWeekendServices` | Saturday → returns service IDs with saturday=1 |
| `activeServices_outsideDateRange_returnsEmpty` | Date before start_date or after end_date → excluded |
| `activeServices_withAddedException_includesService` | Holiday with exception_type=1 → service added |
| `activeServices_withRemovedException_excludesService` | Holiday with exception_type=2 → service removed |
| `activeServices_overlappingCalendars_returnsBoth` | Multiple valid service IDs for same day → all returned |

---

## 3. Integration Tests (Room + Repository — runs on Android emulator or device)

These use a real in-memory Room database to test the full flow.

### 3.1 Initialize Flow (`InitializeIntegrationTest`)

| Test | What it verifies |
|------|-----------------|
| `initialize_withValidGtfs_populatesAllTables` | After init, Station/Trip/StopTime/Calendar tables all have data |
| `initialize_wipesOldData_beforeInsertingNew` | Run init twice → row counts match single init (no duplicates) |
| `initialize_returnsCorrectCounts` | InitResult.stationCount, tripCount, stopTimeCount match DB queries |
| `initialize_storesMetadata` | ScheduleMetadata row has correct timestamp and counts |
| `initialize_withCorruptZip_failsGracefully` | Corrupt file → returns InitResult(success=false) with errors |
| `initialize_atomicTransaction_noPartialData` | If parse fails mid-way, DB has no partial inserts |

**Test data:** Bundle a small but valid GTFS zip in `src/androidTest/assets/test_gtfs.zip` with ~5 stations, ~10 trips, ~50 stop_times. This avoids network calls in tests.

### 3.2 Validation Flow (`ValidationIntegrationTest`)

| Test | What it verifies |
|------|-----------------|
| `validate_afterGoodInit_returnsValid` | Full init with good data → isValid=true, no errors |
| `validate_missingStations_returnsError` | Delete all stations → validation catches it |
| `validate_missingTripsInOneDirection_returnsError` | Delete all NB trips → validation catches "no northbound trips" |
| `validate_orphanedStopTimes_returnsError` | StopTime referencing non-existent trip_id → flagged |
| `validate_noActiveServiceToday_returnsWarning` | Calendar has no active service for today → warning (not error) |
| `validate_knownStationsExist_returnsValid` | "San Francisco", "San Jose Diridon", "Palo Alto" all present |
| `validate_stopSequenceOrder_isAscending` | Per trip, stop_sequence values increase monotonically |

### 3.3 Lookup Flow (`LookupIntegrationTest`)

| Test | What it verifies |
|------|-----------------|
| `lookup_nearPaloAlto_returnsNextTwoTrains` | Known location + weekday 8 AM → 2 departures returned |
| `lookup_returnsTrainsInChronologicalOrder` | First train departs before second |
| `lookup_nearHome_returnsCorrectDirection` | Near home location → direction matches expected |
| `lookup_nearWork_returnsOppositeDirection` | Near work location → opposite direction |
| `lookup_lastTrainOfDay_returnsFewerThanTwo` | 11:55 PM → may return 0 or 1 trains |
| `lookup_includesRouteType` | Result includes "Local", "Limited", or "Express" |
| `lookup_includesDestinationArrival` | Result includes arrival time at the destination station |
| `lookup_nearestStation_isCorrect` | Result's nearestStation matches expected for given coordinates |
| `lookup_weekendSchedule_usesWeekendServices` | Saturday date → queries weekend service IDs |
| `lookup_filtersExpiredTrips` | Trains that already departed are not returned |

---

## 4. End-to-End Validation Against Live Schedule

This is a **one-time or periodic** sanity check, not a CI test.

### 4.1 Golden File Test (`GoldenScheduleTest`)

1. Download the real GTFS zip and run initialize().
2. Query next trains from "Palo Alto" northbound on a known weekday at 8:00 AM.
3. Compare results against the published schedule on caltrain.com (the schedule tables we already fetched).
4. Assert that departure times match within 1 minute.

**Purpose:** Catches parsing bugs that unit tests with fixture data might miss. Run manually after each GTFS update.

### 4.2 Station Count Sanity Check

After initializing with real GTFS data:
- Assert station count is between 25–35 (Caltrain currently has ~31 stations).
- Assert all "anchor" stations exist: San Francisco, Millbrae, Palo Alto, Mountain View, Sunnyvale, San Jose Diridon, Tamien, Gilroy.

### 4.3 Round-Trip Consistency Check

For each station, verify that at least one northbound AND one southbound trip stops there on weekdays (except terminus stations which only have one direction).

---

## 5. Test Data Strategy

```
src/
├── test/                          # JVM unit tests (no Android)
│   ├── resources/
│   │   └── gtfs/                  # Small fixture CSVs
│   │       ├── stops.txt          # 5 stations
│   │       ├── stop_times.txt     # 50 stop times
│   │       ├── trips.txt          # 10 trips (5 NB, 5 SB)
│   │       ├── routes.txt         # 3 routes (Local, Limited, Express)
│   │       ├── calendar.txt       # 2 services (weekday, weekend)
│   │       └── calendar_dates.txt # 2 exceptions
│   └── kotlin/
│       └── com/caltrainnow/
│           ├── GtfsParserTest.kt
│           ├── GeoUtilsTest.kt
│           ├── TimeUtilsTest.kt
│           ├── DirectionResolverTest.kt
│           └── ServiceResolverTest.kt
│
├── androidTest/                   # Instrumented tests (emulator)
│   ├── assets/
│   │   └── test_gtfs.zip         # Complete small GTFS zip
│   └── kotlin/
│       └── com/caltrainnow/
│           ├── InitializeIntegrationTest.kt
│           ├── ValidationIntegrationTest.kt
│           └── LookupIntegrationTest.kt
```

### Fixture Data Principles

1. **Minimal but complete** — enough to test all code paths, small enough to reason about.
2. **Deterministic** — fixed dates, fixed times, no dependency on "today."
3. **Cover edge cases** — include at least one after-midnight time, one express (skip station), one weekend-only service.
4. **Stations chosen for testing:** Use 5 real stations in order: San Francisco, Millbrae, Palo Alto, Mountain View, San Jose Diridon. This covers terminus, mid-line, and enough stops to test direction/nearest logic.

---

## 6. Testing Tools & Libraries

| Tool | Purpose |
|------|---------|
| **JUnit 5** | Test framework for unit tests |
| **JUnit 4 + AndroidX Test** | Instrumented test framework |
| **Room In-Memory DB** | `Room.inMemoryDatabaseBuilder()` for integration tests — fast, no cleanup |
| **Turbine** | Testing Kotlin Flows (if we expose reactive queries) |
| **MockK** | Mocking for unit tests (mock location provider, network downloader) |
| **Robolectric** (optional) | Run some Android tests on JVM without emulator |
| **Truth / AssertJ** | Fluent assertions for readability |

---

## 7. CI Pipeline (Recommended)

```
┌──────────┐     ┌──────────────┐     ┌────────────────────┐
│  Build   │ ──► │  Unit Tests  │ ──► │ Integration Tests   │
│  (Gradle)│     │  (JVM)       │     │ (Android Emulator)  │
└──────────┘     └──────────────┘     └────────────────────┘
                     ~10 sec               ~60 sec
```

- **Unit tests** run on every commit — fast, no emulator.
- **Integration tests** run on PR merge or nightly — require emulator.
- **Golden file tests** run manually after GTFS updates.

---

## 8. What We're NOT Testing in Phase 1

| Area | Why deferred |
|------|-------------|
| UI tests (Espresso/Compose) | No UI yet — Phase 2 |
| Network download (real HTTP) | Mock the downloader in tests; test real download manually |
| Google Maps intent | Just a URI builder — test the URI string, not the intent launch |
| Location provider | Mock `FusedLocationProviderClient` — test with hardcoded lat/long |
| Performance/load testing | Not needed for single-user on-device app |

---

## 9. Key Test Scenarios Matrix

A quick reference showing which tests cover which risks:

| Risk | Test Coverage |
|------|-------------|
| GTFS format changes | Parser unit tests + golden file test |
| Wrong station matched | GeoUtils unit tests + lookup integration |
| Wrong direction detected | DirectionResolver unit tests + lookup integration |
| Weekend/holiday schedule wrong | ServiceResolver unit tests + lookup integration |
| After-midnight trains missed | TimeUtils unit tests + lookup integration |
| Corrupt download | Initialize integration test (corrupt zip) |
| Partial data in DB | Initialize integration test (atomic transaction) |
| Express trains shown at skip stations | Lookup integration test (express doesn't stop at all stations) |
| No trains remaining today | Lookup integration test (late night edge case) |
| DB migration breaks data | Room migration tests (Phase 2+, if schema changes) |
