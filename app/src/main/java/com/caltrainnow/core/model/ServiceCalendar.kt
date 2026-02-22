package com.caltrainnow.core.model

/**
 * Represents a service calendar from GTFS calendar.txt.
 * Defines which days of the week a service runs and its validity date range.
 */
data class ServiceCalendar(
    val serviceId: String,
    val monday: Boolean,
    val tuesday: Boolean,
    val wednesday: Boolean,
    val thursday: Boolean,
    val friday: Boolean,
    val saturday: Boolean,
    val sunday: Boolean,
    val startDate: String,    // YYYYMMDD
    val endDate: String       // YYYYMMDD
)
