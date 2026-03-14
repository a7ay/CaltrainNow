# CaltrainNow — Phase 2 UI Plan

## Decisions Summary

| Choice | Decision |
|---|---|
| Framework | Jetpack Compose |
| Theme | Material You (Material 3) with dynamic color |
| Home screen | Standard — trains + station + navigate button |
| Settings | Separate screen (Compose Navigation) |
| Refresh | Pull-to-refresh only |
| Direction | Auto-detect + manual NB/SB toggle override |

---

## Screens

### 1. Home Screen (`HomeScreen`)

The primary screen. Opens instantly and shows the next 2 trains.

```
┌─────────────────────────────────────────┐
│  CaltrainNow              ⚙️ (settings)  │
│─────────────────────────────────────────│
│                                         │
│  📍 Palo Alto Station          0.2 mi  │
│  ─────────────────────────────────────  │
│                                         │
│  ◀ NORTHBOUND ▶            🔄 toggle    │
│  Near home → heading to work            │
│                                         │
│  ┌─────────────────────────────────────┐│
│  │  🚆  8:55 AM          in 12 min    ││
│  │  Local · Train #115                 ││
│  │  Arrives SF: 9:46 AM               ││
│  │                                     ││
│  │  [  🧭 Navigate to Station  ]      ││
│  └─────────────────────────────────────┘│
│                                         │
│  ┌─────────────────────────────────────┐│
│  │  🚆  9:22 AM          in 39 min    ││
│  │  Express · Train #507               ││
│  │  Arrives SF: 10:04 AM              ││
│  │                                     ││
│  │  [  🧭 Navigate to Station  ]      ││
│  └─────────────────────────────────────┘│
│                                         │
│  Last refreshed: 8:43 AM               │
│  Pull down to refresh                   │
│                                         │
└─────────────────────────────────────────┘
```

**Components:**

| Component | Description |
|---|---|
| **Top bar** | App title + settings gear icon |
| **Station banner** | Nearest station name + walking distance |
| **Direction row** | NB/SB indicator + toggle button + reason text |
| **Train card ×2** | Departure time, countdown, route type, train #, arrival at destination, navigate button |
| **Status footer** | Last refresh timestamp |
| **Pull-to-refresh** | Wraps the entire content |

**States:**

| State | What shows |
|---|---|
| **Loading** | Centered spinner + "Finding your train..." |
| **Success** | Full layout above |
| **No trains** | Station banner + "No more trains today" message |
| **No schedule** | "Schedule not loaded" + "Download Schedule" button |
| **Location denied** | Prompt explaining why location is needed + button to open settings |
| **Error** | Error message + "Try Again" button |

---

### 2. Settings Screen (`SettingsScreen`)

Reached via gear icon. Manages home/work locations and schedule data.

```
┌─────────────────────────────────────────┐
│  ← Settings                             │
│─────────────────────────────────────────│
│                                         │
│  LOCATIONS                              │
│  ┌─────────────────────────────────────┐│
│  │  🏠 Home                            ││
│  │  Sunnyvale                          ││
│  │  37.3688, -122.0363                 ││
│  │                   [ Use Current 📍 ]││
│  └─────────────────────────────────────┘│
│                                         │
│  ┌─────────────────────────────────────┐│
│  │  🏢 Work                            ││
│  │  San Francisco                      ││
│  │  37.7764, -122.3943                 ││
│  │                   [ Use Current 📍 ]││
│  └─────────────────────────────────────┘│
│                                         │
│  SCHEDULE DATA                          │
│  ┌─────────────────────────────────────┐│
│  │  Downloaded: Feb 22, 2026           ││
│  │  31 stations · 142 trips            ││
│  │                                     ││
│  │  [ 🔄 Re-download Schedule ]        ││
│  └─────────────────────────────────────┘│
│                                         │
│  ABOUT                                  │
│  Version 1.0.0                          │
│  Schedule: Caltrain GTFS               │
│                                         │
└─────────────────────────────────────────┘
```

**Components:**

| Component | Description |
|---|---|
| **Location cards** | Show label, coords. "Use Current" button captures GPS and saves. Optional: text fields for manual lat/lng or label editing. |
| **Schedule card** | Shows metadata (download date, counts). "Re-download" triggers full GTFS init flow with progress indicator. |
| **About section** | App version, data source |

---

## Navigation

Two screens, simple stack navigation:

```
HomeScreen  ──(gear icon)──►  SettingsScreen
                              │
                              ◄── (back arrow)
```

Using `NavHost` + `composable()` routes. No bottom nav needed — the app is intentionally single-purpose.

---

## Architecture: MVVM

```
┌──────────────┐     ┌──────────────┐     ┌──────────────────────┐
│  HomeScreen  │────►│ HomeViewModel│────►│  CaltrainRepository  │
│  (Compose)   │◄────│  (StateFlow) │◄────│  (Phase 1 core)      │
└──────────────┘     └──────────────┘     └──────────────────────┘

┌──────────────────┐  ┌───────────────────┐  ┌──────────────────┐
│  SettingsScreen  │─►│ SettingsViewModel │─►│ UserPrefsStore   │
│  (Compose)       │◄─│   (StateFlow)     │◄─│ (DataStore)      │
└──────────────────┘  └───────────────────┘  └──────────────────┘
```

### HomeViewModel

```kotlin
// UI State
data class HomeUiState(
    val isLoading: Boolean = true,
    val nearestStation: StationInfo? = null,
    val stationDistanceText: String = "",
    val direction: Direction = Direction.NORTHBOUND,
    val directionReason: String = "",
    val isDirectionOverridden: Boolean = false,
    val nextTrains: List<TrainDeparture> = emptyList(),
    val lastRefreshed: String = "",
    val error: String? = null,
    val scheduleLoaded: Boolean = false,
    val locationPermissionNeeded: Boolean = false
)

// Actions
sealed class HomeAction {
    object Refresh : HomeAction()
    object ToggleDirection : HomeAction()
    data class NavigateToStation(val lat: Double, val lng: Double) : HomeAction()
    object RequestLocationPermission : HomeAction()
    object DownloadSchedule : HomeAction()
}
```

### SettingsViewModel

```kotlin
data class SettingsUiState(
    val homeLabel: String = "",
    val homeLat: Double = 0.0,
    val homeLng: Double = 0.0,
    val workLabel: String = "",
    val workLat: Double = 0.0,
    val workLng: Double = 0.0,
    val scheduleMetadata: ScheduleMetadata? = null,
    val isDownloading: Boolean = false,
    val downloadResult: String? = null
)
```

---

## Material You Theming

### Color Scheme

Use Material 3 `dynamicColorScheme()` on Android 12+ with a Caltrain-inspired fallback:

```
Fallback seed color:  #E31837  (Caltrain red)
Primary:              #E31837  (red — action buttons, FABs)
Secondary:            #1A1A2E  (dark navy — cards, headers)
Tertiary:             #4CAF50  (green — on-time indicators)
Surface:              System dynamic or light gray
```

On Android 12+ devices, the theme automatically adapts to the user's wallpaper colors. On older devices, falls back to the Caltrain red/navy scheme.

### Typography

Material 3 defaults with these overrides:
- **headlineLarge**: Station name (bold)
- **titleMedium**: Train departure time (medium weight, slightly larger)
- **bodyLarge**: Countdown, route type
- **bodyMedium**: Direction reason, arrival time
- **labelSmall**: Last refreshed timestamp

### Train Cards

```
Elevation:        tonalElevation = 2.dp
Shape:            RoundedCornerShape(16.dp)
Countdown chip:   Surface variant background, "in X min" text
Navigate button:  FilledTonalButton with map icon
Route type badge: Small chip (colored by route — red=Express, blue=Local, etc.)
```

---

## Direction Toggle

The toggle sits in the direction row. Behavior:

1. **Auto mode (default):** Shows detected direction + reason text ("Near home → heading to work")
2. **User taps toggle:** Flips to opposite direction, reason changes to "Manual override"
3. **Immediately re-queries** the data source for trains in the new direction
4. **Persists until next refresh:** Pull-to-refresh resets to auto-detect (re-evaluates location)

Implementation: A `SegmentedButton` or simple `IconButton` with arrow icon that flips.

```
  ◀ NORTHBOUND ▶          [🔄]
                            ^ tap to flip
```

Or a `SingleChoiceSegmentedButtonRow`:
```
  [ ◀ NB ]  [ SB ▶ ]
```

---

## Pull-to-Refresh Flow

```
User pulls down
  → HomeViewModel.refresh()
    → Get current GPS location
    → Reset direction override (back to auto-detect)
    → Call repository.lookupNextTrains(lat, lng)
    → Update UI state
    → Update lastRefreshed timestamp
```

Using `pullToRefresh` modifier from Material 3.

---

## First Launch Flow

```
App opens
  │
  ├─ Schedule loaded?
  │   ├─ NO → Show "Welcome" state with "Download Schedule" button
  │   │        User taps → Progress indicator → GTFS download + parse
  │   │        On success → proceed to location check
  │   │
  │   └─ YES → Check location permission
  │        ├─ NOT GRANTED → Show rationale + "Grant Permission" button
  │        │                 On grant → fetch location → lookup trains
  │        │
  │        └─ GRANTED → Fetch location → lookup trains → show results
```

---

## File Structure (New/Modified)

```
app/src/main/java/com/caltrainnow/
├── ui/
│   ├── MainActivity.kt          ← MODIFY (add NavHost, theme)
│   ├── theme/
│   │   ├── Theme.kt             ← NEW (Material You + fallback)
│   │   ├── Color.kt             ← NEW (Caltrain color palette)
│   │   └── Type.kt              ← NEW (typography scale)
│   ├── home/
│   │   ├── HomeScreen.kt        ← NEW (main composable)
│   │   ├── HomeViewModel.kt     ← NEW (state + actions)
│   │   └── components/
│   │       ├── TrainCard.kt     ← NEW (single train departure card)
│   │       ├── StationBanner.kt ← NEW (nearest station info)
│   │       └── DirectionRow.kt  ← NEW (NB/SB toggle)
│   ├── settings/
│   │   ├── SettingsScreen.kt    ← NEW
│   │   └── SettingsViewModel.kt ← NEW
│   └── navigation/
│       └── AppNavigation.kt     ← NEW (NavHost + routes)
├── data/
│   └── preferences/
│       └── UserPrefsStore.kt    ← NEW (DataStore wrapper)
└── di/
    └── Modules.kt               ← MODIFY (add prefs bindings)
```

**13 new files, 2 modified files.**

---

## Dependencies to Add

```kotlin
// Already in build.gradle.kts:
// Compose, Material 3, Hilt Navigation Compose, Activity Compose, DataStore

// May need to add:
implementation("androidx.compose.material3:material3:1.2.0")  // ensure latest M3
implementation("androidx.navigation:navigation-compose:2.7.6")
implementation("com.google.accompanist:accompanist-permissions:0.34.0")  // runtime permission handling
```

---

## Build Order

1. **Theme** (`Color.kt`, `Type.kt`, `Theme.kt`) — foundation for everything
2. **UserPrefsStore** — DataStore for home/work locations
3. **HomeViewModel** — state management, connects to repository
4. **Home components** (`StationBanner`, `DirectionRow`, `TrainCard`)
5. **HomeScreen** — assembles components + pull-to-refresh
6. **SettingsViewModel** + **SettingsScreen**
7. **AppNavigation** — NavHost wiring
8. **MainActivity** — final wiring with theme + nav
9. **DI updates** — bind new dependencies

---

## Edge Cases to Handle in UI

| Scenario | UI Response |
|---|---|
| GPS off / unavailable | Show error + "Enable Location" button |
| Permission denied permanently | Direct to app settings |
| No trains remaining today | "No more trains today. First train tomorrow: X:XX AM" |
| Schedule expired (>30 days old) | Warning banner on home screen |
| Network error during GTFS download | Error message + retry button in settings |
| Walking distance > 2 miles | Warning: "You may be far from a Caltrain station" |
| After-midnight trains | Display with correct 12h time (1:30 AM not 25:30) |
| Express skips destination station | Show "Does not stop at [destination]" instead of arrival time |
