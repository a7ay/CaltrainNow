# CaltrainNow — 511 Alerts and Real-Time Trip Updates

**Status:** Implemented in the app; requires a configured 511 token and live-feed validation.

## 1. Product Context

CaltrainNow currently displays the next four scheduled trains for the detected or manually selected travel direction. Those times come from the locally stored Caltrain GTFS schedule. This feature adds current service information from 511 SF Bay without replacing the schedule lookup.

The feature has two related but distinct purposes:

1. Show active Caltrain service alerts relevant to the selected direction of travel.
2. Show trip-specific real-time changes, especially delays and cancellations, for each of the four scheduled trains already shown on Home.

This is an **in-app information feature**, not a push-notification feature.

## 2. Product Requirements

### Functional requirements

| ID    | Requirement                                                                                                                                                             |
| ----- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| PR-01 | Continue showing up to four scheduled departures from the existing local GTFS schedule, including when 511 is unreachable.                                              |
| PR-02 | Fetch active Caltrain service alerts from 511 and show alerts applicable to the selected northbound or southbound direction.                                            |
| PR-03 | Treat an alert applying to all Caltrain service as relevant in either direction. Respect alert route, trip, stop, direction, and active-time selectors when supplied.   |
| PR-04 | Fetch GTFS-Realtime Trip Updates and match updates to the four displayed departures.                                                                                    |
| PR-05 | Show a matched trip's cancellation or delay. Where stop-level updates exist, show the updated departure at the boarding station and updated arrival at the destination. |
| PR-06 | Never infer that a train is on time merely because no matching realtime update was found. Distinguish “no realtime update” from a reported zero-minute delay.           |
| PR-07 | Show when the realtime information was last successfully updated. If a request fails, retain the last cached result and its timestamp.                                  |
| PR-08 | Use one app-level 511 API token. Riders do not enter their own token. The token is supplied at build time and must not be committed to source control.                  |
| PR-09 | Attribute 511 as the realtime data provider in the app and comply with the applicable 511 terms.                                                                        |

### Non-functional requirements

- The scheduled train lookup must render independently of the realtime requests; 511 latency must not delay the four schedule cards.
- Network, authentication, parsing, and stale-feed errors must not hide scheduled trains or crash the app.
- Automatic realtime requests are limited to one refresh every 15 minutes, including after failed attempts. An explicit user tap on **Refresh** bypasses that cooldown. Do not run background polling.
- Do not send the API token to logs, analytics, crash reports, UI text, or checked-in configuration.
- Do not report a trip-specific delay unless the realtime trip can be matched safely to the displayed schedule trip.

## 3. 511 Data and Constraints

511 SF Bay documents a token-protected transit API. The API data is provided free of charge via token request; 511 requires attribution. Its documented default request limit is 60 requests per API token per 3600 seconds.

- **Service Alerts:** `/transit/servicealerts`, with JSON available via the optional `format` parameter.
- **Trip Updates:** `/transit/tripupdates`, documented as a GTFS-Realtime Protocol Buffer feed.
- Both feeds require an agency selection or the appropriate agency-specific feed. Resolve and verify Caltrain's 511 agency ID through 511's operator data; do not guess it.

Automatic refreshes every 15 minutes use at most four requests per hour per feed (up to eight requests per hour across the two feeds), below the documented limit. A manual refresh deliberately bypasses the cooldown and can consume additional quota; disable the button while a refresh is in progress and make the bypass explicit in code. Do not add background fetching while the app is not visible.

References:

- [511 SF Bay Transit Data](https://511.org/open-data/transit)
- [511 Open Data FAQs](https://511.org/open-data/faqs)
- [Request or Retrieve a 511 Token](https://511.org/open-data/token)
- [GTFS-Realtime reference](https://gtfs.org/documentation/realtime/reference/)

## 4. Matching and Relevance Rules

### Service alerts

Use the GTFS-Realtime alert's active period and informed entities. Match its trip, route, stop, and direction selectors against the selected direction and the current journey's boarding/destination stations. Alerts with no narrower selectors are agency-wide and apply in both directions. Do not show expired alerts.

511 and the existing Caltrain GTFS schedule may use different identifiers. Before relying on route, stop, or direction IDs, compare 511's Caltrain identifiers with the identifiers already stored by the app. If an alert cannot be mapped reliably, do not silently present it as direction-specific; either show it as a clearly labeled general Caltrain alert or omit it if its agency/relevance cannot be established.

### Trip Updates

First attempt an exact match using the GTFS-Realtime `TripDescriptor` and the scheduled trip's GTFS `trip_id`, including service date and start time where present. The current display model already carries `tripId`, but it will need to retain additional schedule context such as route, boarding stop, destination stop, and service date for robust matching and stop-level updates.

The current schedule is downloaded from Caltrain's Trillium Transit GTFS URL, while realtime data is sourced from 511. **Identifier parity is an implementation gate:** verify that trip IDs, route IDs, and stop IDs are compatible with the 511 feed using representative weekday/weekend and both-direction samples. If exact IDs differ, use a fallback only if a stable, unambiguous mapping can be established (for example, matching service date, train number, direction, route, scheduled start time, and stops). Do not match on train number alone. If no reliable mapping exists, retain the schedule time and show that realtime status is unavailable for that train rather than fabricating a delay.

For a matched trip:

- A canceled relationship is shown as **Canceled**.
- A stop-level departure/arrival update is shown for the corresponding station when available.
- A trip-level delay may be shown as a delay amount, but should not be represented as a precise station time unless the feed supplies or supports that calculation.
- No matching update means **No realtime update**, not “On time.”

## 5. Implementation Design

### Data flow

1. `HomeViewModel` obtains the four scheduled trains and immediately updates the existing UI state.
2. In parallel, a realtime repository checks the persisted request timestamp/cache.
3. On automatic checks, fetch the Caltrain Service Alerts and Trip Updates feeds only if at least 15 minutes have elapsed since the last attempt. A manual refresh button forces both feed requests regardless of the cooldown.
4. Parse and normalize each response independently. A successful feed refresh updates that feed's cache and timestamp; a failure leaves its prior cached result intact.
5. Filter service alerts for the currently selected direction and match trip updates to the four displayed departures.
6. Expose the normalized alerts, per-train update status, and last-successful-update time in `HomeUiState`.
7. When the user toggles direction, re-filter/re-run matching against the displayed trains; do not fetch again if the 15-minute cooldown has not elapsed.

While Home is in the foreground, automatically check the feeds every 15 minutes. Pull-to-refresh refreshes the schedule and checks realtime freshness without bypassing the cooldown. The dedicated **Refresh** button forces a realtime fetch. There is no WorkManager job, background polling, or push notification in this feature.

### Proposed code changes

| Area | Proposed change |
|---|---|
| `core/model/` | Add normalized `TransitAlert` and `TrainRealtimeUpdate` models with source IDs, active interval, relevance selectors, status/delay, and update timestamps. |
| `core/model/TrainLookupResult.kt` | Preserve schedule context needed for exact realtime matching: route ID, boarding/destination stop IDs, direction, service date, and scheduled stop times as needed. Keep `tripId` as the primary key. |
| `data/alerts/` | Add a 511 client, Service Alerts JSON parser, GTFS-Realtime Trip Updates Protocol Buffer decoder, identifier/relevance matcher, and cache/freshness coordinator. Keep network and Android persistence outside the pure core where practical. |
| `data/repository/` | Add a `RealtimeTransitRepository` that coordinates the two feeds and returns cached/fresh normalized results without coupling schedule lookup to network success. |
| `data/preferences/` or a dedicated config provider | Read the app-level token from build configuration; persist cached normalized results and last-attempt/last-success timestamps locally. Use a separate cache from user home/work preferences. |
| `di/Modules.kt` | Provide the 511 API client/repository and cache through Hilt, reusing the existing `OkHttpClient`. |
| `HomeViewModel.kt` | Add non-blocking realtime loading, freshness checks, selected-direction filtering, and independent service-alert/trip-update error/stale state. |
| `HomeScreen.kt`, `TrainCard.kt`, components | Add a service-alert section and status/delay/cancellation treatments on the four existing train cards. Keep the schedule time visible as context when an updated time is supplied. |
| `app/build.gradle.kts` and local build config | Inject a token from an untracked local build property/environment variable into the app build. Add a GTFS-Realtime protobuf dependency (or generated bindings) for Trip Updates decoding. Allow an empty token in local/test builds so unit tests do not require credentials. |
| `README.md` / privacy documentation | Document 511 attribution and realtime data use. Before public release, review the privacy policy to accurately disclose requests to 511 and the app's existing location/network behavior. |

### Token handling and security

The agreed product choice is one app-level token rather than asking each rider for one. Supply it through an ignored local property for developer builds and a CI secret for release builds; do not check in the token or put it in source files. Never log a complete request URL because the token is a query parameter.

This protects the token from accidental source-control exposure but **does not make it secret in a distributed APK**: a user can extract a compiled token. Restrict requests to the required Caltrain endpoints, keep the 15-minute client cooldown, monitor quota, and document the residual risk. If extraction/abuse becomes unacceptable, move API calls behind a backend proxy; that would change the current no-backend architecture and is outside this implementation scope.

### UI behavior

- Place a compact **Service alerts** section after the direction row and before the train cards. Show only active relevant alerts, with readable title/body, severity when provided, and an optional source link if supplied.
- Each of the four train cards may show a **Delayed +N min**, updated station time, or **Canceled** badge when safely matched.
- Show a quiet status line such as **Live updates · updated 8:42 AM**. If data is stale or temporarily unavailable, preserve cached content and label its age; if no cache exists, say live updates are unavailable.
- An empty alerts list is a valid state and should not add an error banner.
- Missing/invalid build token or API failure disables only live data; scheduled trains remain available.

## 6. Acceptance Criteria

- The four scheduled trains render before realtime requests finish and still render with no network, invalid token, or malformed feed.r
- An active northbound-only service alert is not shown as southbound, and vice versa; an agency-wide alert can appear for either direction.
- Expired and unrelated alerts are excluded.
- A reliably matched delay or cancellation appears on the correct train card; unmatched updates never appear on a different train.
- A train with no matching update is not labeled “on time.”
- Automatic refresh, direction toggles, and pull-to-refresh respect the 15-minute cooldown; tapping the dedicated Refresh button forces a fetch.
- The displayed timestamps reflect each feed's last successful fetch, not the time a cached result was read or a failed request completed.
- Failed requests preserve prior cached data and do not trigger a retry loop.
- No API token is present in tracked files, logs, UI, or test fixtures.
- The Home UI identifies 511 as the source of live transit data.

## 7. Test Design

Use fixture-based unit tests for Service Alerts parsing, Trip Updates Protocol Buffer decoding, active-period evaluation, direction/station relevance, exact/fallback trip matching, stop-level delay selection, automatic cache cooldown, and forced-refresh behavior. Add ViewModel/UI tests for non-blocking scheduled results, stale-cache timestamps, no-update versus on-time semantics, cancellation, direction changes during refresh, and network/key failures. Keep live 511 calls out of CI; use sanitized fixtures and an optional manual smoke test with a developer token.

## 8. Implementation Gates and Open Questions

1. Obtain a 511 token through the official token request process and keep it out of Git.
2. Confirm the Caltrain agency ID used by 511.
3. Compare 511 realtime identifiers to the app's current static GTFS identifiers and prove trip matching on representative samples. This determines whether the existing schedule feed can be retained unchanged.
4. Confirm exact 511 Trip Updates response encoding and protobuf schema/version before selecting the Android decoder dependency.
5. Review 511 terms/attribution and update privacy disclosures before distributing a build with realtime requests.
