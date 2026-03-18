package com.caltrainnow.core.model

/**
 * Daily weather snapshot for a station — icon, high, and low temp for the day.
 * Sourced from Open-Meteo (WMO weather codes, temperatures in °F).
 */
data class WeatherInfo(
    val weatherCode: Int,
    val tempHighF: Double,
    val tempLowF: Double
) {
    fun weatherEmoji(): String = when (weatherCode) {
        0          -> "☀️"
        in 1..2    -> "⛅"
        3          -> "☁️"
        in 45..48  -> "🌫️"
        in 51..57  -> "🌦️"
        in 61..67  -> "🌧️"
        in 71..77  -> "❄️"
        in 80..82  -> "🌧️"
        in 85..86  -> "❄️"
        in 95..99  -> "⛈️"
        else       -> "🌡️"
    }

    fun weatherDescription(): String = when (weatherCode) {
        0          -> "Clear"
        in 1..2    -> "Partly Cloudy"
        3          -> "Overcast"
        in 45..48  -> "Foggy"
        in 51..57  -> "Drizzle"
        in 61..67  -> "Rain"
        in 71..77  -> "Snow"
        in 80..82  -> "Showers"
        in 85..86  -> "Snow Showers"
        in 95..99  -> "Thunderstorm"
        else       -> "—"
    }
}
