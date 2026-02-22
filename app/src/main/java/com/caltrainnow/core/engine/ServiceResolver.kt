package com.caltrainnow.core.engine

import com.caltrainnow.core.model.ServiceCalendar
import com.caltrainnow.core.model.ServiceException
import com.caltrainnow.core.util.TimeUtils
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Determines which service IDs are active for a given date.
 * Combines calendar.txt (regular schedule) with calendar_dates.txt (exceptions).
 *
 * Pure Kotlin — no Android dependencies.
 */
class ServiceResolver {

    /**
     * Get the list of active service IDs for a specific date.
     *
     * @param date The date to check
     * @param calendars All service calendars from GTFS
     * @param exceptions All service exceptions from GTFS
     * @return List of active service IDs
     */
    fun getActiveServiceIds(
        date: LocalDate,
        calendars: List<ServiceCalendar>,
        exceptions: List<ServiceException>
    ): List<String> {
        val dateStr = date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)

        // Step 1: Find services active by regular calendar
        val activeByCalendar = calendars.filter { cal ->
            isDateInRange(date, cal.startDate, cal.endDate) &&
                isDayActive(date.dayOfWeek, cal)
        }.map { it.serviceId }.toMutableSet()

        // Step 2: Apply exceptions
        val exceptionsForDate = exceptions.filter { it.date == dateStr }

        for (exception in exceptionsForDate) {
            when (exception.exceptionType) {
                ServiceException.TYPE_ADDED -> {
                    // Service added for this date (e.g., special holiday service)
                    activeByCalendar.add(exception.serviceId)
                }
                ServiceException.TYPE_REMOVED -> {
                    // Service removed for this date (e.g., no service on holiday)
                    activeByCalendar.remove(exception.serviceId)
                }
            }
        }

        return activeByCalendar.toList()
    }

    /**
     * Check if a date falls within the service calendar's validity range.
     */
    private fun isDateInRange(date: LocalDate, startDateStr: String, endDateStr: String): Boolean {
        return try {
            val startDate = TimeUtils.parseGtfsDate(startDateStr)
            val endDate = TimeUtils.parseGtfsDate(endDateStr)
            !date.isBefore(startDate) && !date.isAfter(endDate)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if a day of the week is active in the service calendar.
     */
    private fun isDayActive(dayOfWeek: DayOfWeek, calendar: ServiceCalendar): Boolean {
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> calendar.monday
            DayOfWeek.TUESDAY -> calendar.tuesday
            DayOfWeek.WEDNESDAY -> calendar.wednesday
            DayOfWeek.THURSDAY -> calendar.thursday
            DayOfWeek.FRIDAY -> calendar.friday
            DayOfWeek.SATURDAY -> calendar.saturday
            DayOfWeek.SUNDAY -> calendar.sunday
        }
    }
}
