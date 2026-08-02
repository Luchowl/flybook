package com.neonstick.flybook.data

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Flight enrichment: computes distance, duration and fills in names from
 * reference data. Returns a new Flight.
 */
object Enricher {

    private const val EARTH_RADIUS_KM = 6371.0

    /** Maps numeric/abbreviated cabin class codes to readable names (e.g. "1" -> "Economy"). */
    fun normalizeCabinClass(raw: String): String {
        val v = raw.trim()
        if (v.isEmpty()) return ""
        return when (v.uppercase(Locale.ROOT)) {
            "0", "1", "Y", "M", "ECON", "ECO", "ECONOMY", "TOURIST", "COACH" -> "Economy"
            "B", "C", "J", "R", "BIZ", "BUSINESS", "CLUB", "PREMIER" -> "Business"
            "F", "FIRST", "SUITE" -> "First"
            "PE", "PY", "W", "PREMIUM", "PREMIUM ECONOMY", "PREMIUM Y" -> "Premium Economy"
            else -> v
        }
    }

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1.isNaN() || lon1.isNaN() || lat2.isNaN() || lon2.isNaN()) return 0.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * asin(sqrt(a))
        return EARTH_RADIUS_KM * c
    }

    fun durationMinutes(depHour: Int, depMinute: Int, arrHour: Int, arrMinute: Int): Int {
        if (depHour < 0 || arrHour < 0) return 0
        val dep = depHour * 60 + depMinute
        val arr = arrHour * 60 + arrMinute
        return if (arr >= dep) arr - dep else (24 * 60 - dep) + arr
    }

    fun enrich(flight: Flight, ref: ReferenceData): Flight {
        val dep = ref.airport(flight.departureIata.ifEmpty { flight.departureIcao })
        val arr = ref.airport(flight.arrivalIata.ifEmpty { flight.arrivalIcao })
        val airline = ref.airline(flight.airlineIata.ifEmpty { flight.airlineIcao })
            ?: ref.airlineByName(flight.airlineName)
        val plane = ref.plane(flight.aircraftType.ifEmpty { flight.aircraftName })
            ?: ref.planeByName(flight.aircraftName)

        var distance = flight.distance
        if (distance <= 0.0 && dep != null && arr != null) {
            distance = haversine(dep.lat, dep.lon, arr.lat, arr.lon).roundToInt().toDouble()
        }

        var duration = flight.durationMinutes
        if (duration <= 0 && flight.depHour >= 0 && flight.arrHour >= 0) {
            duration = durationMinutes(flight.depHour, flight.depMinute, flight.arrHour, flight.arrMinute)
        }
        // Estimate from distance at ~800 km/h when no times are available
        if (duration <= 0 && distance > 0) {
            duration = (distance / 800.0 * 60.0).roundToInt()
        }

        return flight.copy(
            departureIata = if (flight.departureIata.isEmpty()) dep?.iata ?: flight.departureIata else flight.departureIata,
            departureIcao = if (flight.departureIcao.isEmpty()) dep?.icao ?: flight.departureIcao else flight.departureIcao,
            departureName = dep?.name ?: flight.departureName,
            departureCity = dep?.city ?: flight.departureCity,
            departureCountry = dep?.country ?: flight.departureCountry,
            arrivalIata = if (flight.arrivalIata.isEmpty()) arr?.iata ?: flight.arrivalIata else flight.arrivalIata,
            arrivalIcao = if (flight.arrivalIcao.isEmpty()) arr?.icao ?: flight.arrivalIcao else flight.arrivalIcao,
            arrivalName = arr?.name ?: flight.arrivalName,
            arrivalCity = arr?.city ?: flight.arrivalCity,
            arrivalCountry = arr?.country ?: flight.arrivalCountry,
            airlineName = airline?.name ?: flight.airlineName,
            aircraftName = plane?.name ?: flight.aircraftName,
            cabinClass = normalizeCabinClass(flight.cabinClass),
            distance = distance,
            durationMinutes = duration,
        )
    }
}
