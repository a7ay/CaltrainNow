package com.caltrainnow.core.model

/**
 * Represents a service exception from GTFS calendar_dates.txt.
 * Used for holidays and special schedule days.
 */
data class ServiceException(
    val id: Long = 0,
    val serviceId: String,
    val date: String,             // YYYYMMDD
    val exceptionType: Int        // 1 = service added, 2 = service removed
) {
    companion object {
        const val TYPE_ADDED = 1
        const val TYPE_REMOVED = 2
    }
}
