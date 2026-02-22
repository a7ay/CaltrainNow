package com.caltrainnow.core.parser

import com.caltrainnow.core.model.*
import java.io.BufferedReader

/**
 * Parses GTFS CSV files into domain model objects.
 * Pure Kotlin — no Android dependencies.
 *
 * GTFS CSV format:
 * - First line is the header row with column names.
 * - Subsequent lines are comma-separated values.
 * - Values may be quoted if they contain commas.
 * - Files may have a UTF-8 BOM at the start.
 */
class GtfsParser {

    /**
     * Parse stops.txt into Station objects.
     *
     * Expected columns: stop_id, stop_name, stop_lat, stop_lon, location_type, parent_station
     */
    fun parseStops(reader: BufferedReader): List<Station> {
        return parseCsv(reader) { headers, values ->
            val columnMap = mapColumns(headers, values)
            Station(
                stationId = columnMap["stop_id"] ?: return@parseCsv null,
                stationName = columnMap["stop_name"] ?: return@parseCsv null,
                latitude = columnMap["stop_lat"]?.toDoubleOrNull() ?: return@parseCsv null,
                longitude = columnMap["stop_lon"]?.toDoubleOrNull() ?: return@parseCsv null,
                locationType = columnMap["location_type"]?.toIntOrNull() ?: 0,
                parentId = columnMap["parent_station"]?.ifBlank { null }
            )
        }
    }

    /**
     * Parse trips.txt into Trip objects.
     *
     * Expected columns: trip_id, route_id, service_id, direction_id, trip_headsign
     */
    fun parseTrips(reader: BufferedReader): List<Trip> {
        return parseCsv(reader) { headers, values ->
            val columnMap = mapColumns(headers, values)
            Trip(
                tripId = columnMap["trip_id"] ?: return@parseCsv null,
                routeId = columnMap["route_id"] ?: return@parseCsv null,
                serviceId = columnMap["service_id"] ?: return@parseCsv null,
                direction = columnMap["direction_id"]?.toIntOrNull() ?: return@parseCsv null,
                tripHeadsign = columnMap["trip_headsign"]?.ifBlank { null }
            )
        }
    }

    /**
     * Parse stop_times.txt into StopTime objects.
     *
     * Expected columns: trip_id, stop_id, arrival_time, departure_time, stop_sequence
     */
    fun parseStopTimes(reader: BufferedReader): List<StopTime> {
        return parseCsv(reader) { headers, values ->
            val columnMap = mapColumns(headers, values)
            StopTime(
                tripId = columnMap["trip_id"] ?: return@parseCsv null,
                stationId = columnMap["stop_id"] ?: return@parseCsv null,
                arrivalTime = columnMap["arrival_time"]?.trim() ?: return@parseCsv null,
                departureTime = columnMap["departure_time"]?.trim() ?: return@parseCsv null,
                stopSequence = columnMap["stop_sequence"]?.toIntOrNull() ?: return@parseCsv null
            )
        }
    }

    /**
     * Parse routes.txt into Route objects.
     *
     * Expected columns: route_id, route_short_name, route_long_name, route_type
     */
    fun parseRoutes(reader: BufferedReader): List<Route> {
        return parseCsv(reader) { headers, values ->
            val columnMap = mapColumns(headers, values)
            Route(
                routeId = columnMap["route_id"] ?: return@parseCsv null,
                routeShortName = columnMap["route_short_name"]?.ifBlank { null },
                routeLongName = columnMap["route_long_name"]?.ifBlank { null },
                routeType = columnMap["route_type"]?.toIntOrNull() ?: 2
            )
        }
    }

    /**
     * Parse calendar.txt into ServiceCalendar objects.
     *
     * Expected columns: service_id, monday..sunday, start_date, end_date
     */
    fun parseCalendar(reader: BufferedReader): List<ServiceCalendar> {
        return parseCsv(reader) { headers, values ->
            val columnMap = mapColumns(headers, values)
            ServiceCalendar(
                serviceId = columnMap["service_id"] ?: return@parseCsv null,
                monday = columnMap["monday"] == "1",
                tuesday = columnMap["tuesday"] == "1",
                wednesday = columnMap["wednesday"] == "1",
                thursday = columnMap["thursday"] == "1",
                friday = columnMap["friday"] == "1",
                saturday = columnMap["saturday"] == "1",
                sunday = columnMap["sunday"] == "1",
                startDate = columnMap["start_date"] ?: return@parseCsv null,
                endDate = columnMap["end_date"] ?: return@parseCsv null
            )
        }
    }

    /**
     * Parse calendar_dates.txt into ServiceException objects.
     *
     * Expected columns: service_id, date, exception_type
     */
    fun parseCalendarDates(reader: BufferedReader): List<ServiceException> {
        return parseCsv(reader) { headers, values ->
            val columnMap = mapColumns(headers, values)
            ServiceException(
                serviceId = columnMap["service_id"] ?: return@parseCsv null,
                date = columnMap["date"] ?: return@parseCsv null,
                exceptionType = columnMap["exception_type"]?.toIntOrNull() ?: return@parseCsv null
            )
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────

    /**
     * Generic CSV parser. Reads header row, then maps each data row
     * using the provided transform function.
     */
    private fun <T> parseCsv(
        reader: BufferedReader,
        transform: (headers: List<String>, values: List<String>) -> T?
    ): List<T> {
        val results = mutableListOf<T>()
        val headerLine = reader.readLine() ?: return results
        val headers = parseCsvLine(stripBom(headerLine))

        reader.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            try {
                val values = parseCsvLine(line)
                val item = transform(headers, values)
                if (item != null) {
                    results.add(item)
                }
            } catch (e: Exception) {
                // Skip malformed rows — logged in production
            }
        }
        return results
    }

    /**
     * Map column headers to values for a single row.
     */
    private fun mapColumns(headers: List<String>, values: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in headers.indices) {
            if (i < values.size) {
                map[headers[i].trim().lowercase()] = values[i].trim()
            }
        }
        return map
    }

    /**
     * Parse a single CSV line, handling quoted fields.
     */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        fields.add(current.toString())
        return fields
    }

    /**
     * Strip UTF-8 BOM if present at the start of the header line.
     */
    private fun stripBom(line: String): String {
        return if (line.startsWith("\uFEFF")) line.substring(1) else line
    }
}
