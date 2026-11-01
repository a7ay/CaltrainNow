package com.caltrainnow.data.alerts

import com.google.transit.realtime.GtfsRealtime
import com.google.protobuf.util.JsonFormat
import com.caltrainnow.core.engine.RealtimeStopUpdate
import com.caltrainnow.core.engine.RealtimeTripUpdate
import com.caltrainnow.core.model.AlertEntitySelector
import com.caltrainnow.core.model.AlertPeriod
import com.caltrainnow.core.model.TransitAlert

object GtfsRealtimeMapper {
    fun parseServiceAlertsJson(json: String): GtfsRealtime.FeedMessage {
        val builder = GtfsRealtime.FeedMessage.newBuilder()
        JsonFormat.parser().ignoringUnknownFields().merge(json, builder)
        return builder.build()
    }

    fun parseTripUpdates(bytes: ByteArray): GtfsRealtime.FeedMessage =
        GtfsRealtime.FeedMessage.parseFrom(bytes)

    fun toAlerts(feed: GtfsRealtime.FeedMessage): List<TransitAlert> =
        feed.entityList.asSequence()
            .filter { it.hasAlert() }
            .map { entity ->
                val alert = entity.alert
                TransitAlert(
                    id = entity.id,
                    title = localizedText(alert.headerText.translationList)
                        ?: localizedText(alert.descriptionText.translationList)
                        ?: "Caltrain service alert",
                    description = localizedText(alert.descriptionText.translationList),
                    url = localizedText(alert.url.translationList),
                    severity = if (alert.hasSeverityLevel()) alert.severityLevel.name else null,
                    periods = alert.activePeriodList.map { period ->
                        AlertPeriod(
                            startEpochSeconds = if (period.hasStart()) period.start else null,
                            endEpochSeconds = if (period.hasEnd()) period.end else null
                        )
                    },
                    informedEntities = alert.informedEntityList.map { informed ->
                        val tripId = if (informed.hasTrip() && informed.trip.hasTripId()) {
                            informed.trip.tripId
                        } else null
                        AlertEntitySelector(
                            routeId = if (informed.hasRouteId()) informed.routeId else null,
                            stopId = if (informed.hasStopId()) informed.stopId else null,
                            directionId = if (informed.hasDirectionId()) informed.directionId else null,
                            tripId = tripId
                        )
                    }
                )
            }.toList()

    fun toTripUpdates(feed: GtfsRealtime.FeedMessage): List<RealtimeTripUpdate> =
        feed.entityList.asSequence()
            .filter { it.hasTripUpdate() && it.tripUpdate.hasTrip() && it.tripUpdate.trip.hasTripId() }
            .map { entity ->
                val update = entity.tripUpdate
                val trip = update.trip
                RealtimeTripUpdate(
                    tripId = trip.tripId,
                    serviceDate = if (trip.hasStartDate()) trip.startDate else null,
                    canceled = trip.scheduleRelationship ==
                        GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED,
                    delaySeconds = if (update.hasDelay()) update.delay else null,
                    stops = update.stopTimeUpdateList.map { stopUpdate ->
                        val arrival = stopUpdate.arrival
                        val departure = stopUpdate.departure
                        RealtimeStopUpdate(
                            stopId = if (stopUpdate.hasStopId()) stopUpdate.stopId else "",
                            arrivalDelaySeconds = if (stopUpdate.hasArrival() && arrival.hasDelay()) {
                                arrival.delay
                            } else null,
                            departureDelaySeconds = if (stopUpdate.hasDeparture() && departure.hasDelay()) {
                                departure.delay
                            } else null,
                            arrivalEpochSeconds = if (stopUpdate.hasArrival() && arrival.hasTime()) {
                                arrival.time
                            } else null,
                            departureEpochSeconds = if (stopUpdate.hasDeparture() && departure.hasTime()) {
                                departure.time
                            } else null
                        )
                    }
                )
            }.toList()

    private fun localizedText(translations: List<GtfsRealtime.TranslatedString.Translation>): String? {
        val selected = translations.firstOrNull { it.hasLanguage() && it.language.startsWith("en", true) }
            ?: translations.firstOrNull()
        return selected?.text?.takeIf { it.isNotBlank() }
    }
}
