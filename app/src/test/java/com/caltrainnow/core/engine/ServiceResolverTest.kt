package com.caltrainnow.core.engine

import com.caltrainnow.core.model.ServiceCalendar
import com.caltrainnow.core.model.ServiceException
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ServiceResolverTest {

    private lateinit var resolver: ServiceResolver

    private val weekdayCalendar = ServiceCalendar(
        serviceId = "svc_weekday",
        monday = true, tuesday = true, wednesday = true,
        thursday = true, friday = true, saturday = false, sunday = false,
        startDate = "20250101", endDate = "20261231"
    )

    private val weekendCalendar = ServiceCalendar(
        serviceId = "svc_weekend",
        monday = false, tuesday = false, wednesday = false,
        thursday = false, friday = false, saturday = true, sunday = true,
        startDate = "20250101", endDate = "20261231"
    )

    private val calendars = listOf(weekdayCalendar, weekendCalendar)

    @Before
    fun setup() {
        resolver = ServiceResolver()
    }

    @Test
    fun `activeServices - weekday Monday - returns weekday service`() {
        val monday = LocalDate.of(2026, 2, 23) // Monday
        val active = resolver.getActiveServiceIds(monday, calendars, emptyList())
        assertThat(active).containsExactly("svc_weekday")
    }

    @Test
    fun `activeServices - weekday Friday - returns weekday service`() {
        val friday = LocalDate.of(2026, 2, 27) // Friday
        val active = resolver.getActiveServiceIds(friday, calendars, emptyList())
        assertThat(active).containsExactly("svc_weekday")
    }

    @Test
    fun `activeServices - Saturday - returns weekend service`() {
        val saturday = LocalDate.of(2026, 2, 28) // Saturday
        val active = resolver.getActiveServiceIds(saturday, calendars, emptyList())
        assertThat(active).containsExactly("svc_weekend")
    }

    @Test
    fun `activeServices - Sunday - returns weekend service`() {
        val sunday = LocalDate.of(2026, 3, 1) // Sunday
        val active = resolver.getActiveServiceIds(sunday, calendars, emptyList())
        assertThat(active).containsExactly("svc_weekend")
    }

    @Test
    fun `activeServices - outside date range - returns empty`() {
        val dateOutside = LocalDate.of(2028, 1, 5) // After endDate
        val active = resolver.getActiveServiceIds(dateOutside, calendars, emptyList())
        assertThat(active).isEmpty()
    }

    @Test
    fun `activeServices - with added exception - includes service`() {
        // Christmas 2025 (Thursday) — weekday service removed, weekend service added
        val christmas = LocalDate.of(2025, 12, 25)
        val exceptions = listOf(
            ServiceException(serviceId = "svc_weekday", date = "20251225", exceptionType = 2),
            ServiceException(serviceId = "svc_weekend", date = "20251225", exceptionType = 1)
        )
        val active = resolver.getActiveServiceIds(christmas, calendars, exceptions)
        assertThat(active).containsExactly("svc_weekend")
        assertThat(active).doesNotContain("svc_weekday")
    }

    @Test
    fun `activeServices - with removed exception - excludes service`() {
        val thursday = LocalDate.of(2025, 12, 25)
        val exceptions = listOf(
            ServiceException(serviceId = "svc_weekday", date = "20251225", exceptionType = 2)
        )
        val active = resolver.getActiveServiceIds(thursday, calendars, exceptions)
        assertThat(active).isEmpty() // Weekday removed, weekend not active on Thursday
    }

    @Test
    fun `activeServices - overlapping calendars - returns both`() {
        val bothActive = ServiceCalendar(
            serviceId = "svc_both",
            monday = true, tuesday = true, wednesday = true,
            thursday = true, friday = true, saturday = true, sunday = true,
            startDate = "20250101", endDate = "20261231"
        )
        val allCalendars = calendars + bothActive
        val monday = LocalDate.of(2026, 2, 23)
        val active = resolver.getActiveServiceIds(monday, allCalendars, emptyList())
        assertThat(active).containsExactly("svc_weekday", "svc_both")
    }

    @Test
    fun `activeServices - no calendars - returns empty`() {
        val active = resolver.getActiveServiceIds(LocalDate.now(), emptyList(), emptyList())
        assertThat(active).isEmpty()
    }
}
