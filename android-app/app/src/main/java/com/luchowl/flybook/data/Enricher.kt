package com.luchowl.flybook.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
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

    private val CSERIES_CODES = setOf("CS1", "CS3", "BCS1", "BCS3")

    /**
     * CSeries is now Airbus A220; older data still carries the old name/codes.
     * Returns (name, icao) for the A220 equivalent, or null when not a CSeries.
     */
    private fun cseriesToA220(type: String, name: String): Pair<String, String>? {
        val t = type.uppercase(Locale.ROOT)
        val n = name.uppercase(Locale.ROOT)
        if (t !in CSERIES_CODES && "CS100" !in n && "CS300" !in n && "CSERIES" !in n) return null
        val is100 = t in setOf("CS1", "BCS1") || "CS100" in n
        return if (is100) "Airbus A220-100" to "BCS1" else "Airbus A220-300" to "BCS3"
    }

    /**
     * Flight duration in minutes, converting local dep/arr times to UTC with each
     * airport's timezone. IANA zones (Airport.tz) resolve DST per date; without zone
     * data this falls back to assuming both airports share a timezone.
     */
    fun durationMinutes(flightDate: Long, depHour: Int, depMinute: Int, arrHour: Int, arrMinute: Int,
                        dep: Airport?, arr: Airport?): Int {
        if (depHour !in 0..23 || arrHour !in 0..23 || depMinute !in 0..59 || arrMinute !in 0..59) return 0
        val depZone = zoneOf(dep)
        val arrZone = zoneOf(arr)
        if (depZone != null && arrZone != null) {
            val date = LocalDate.ofInstant(Instant.ofEpochMilli(flightDate), ZoneOffset.UTC)
            val depInstant = ZonedDateTime.of(date, LocalTime.of(depHour, depMinute), depZone).toInstant()
            fun arrAt(d: LocalDate): Instant =
                ZonedDateTime.of(d, LocalTime.of(arrHour, arrMinute), arrZone).toInstant()
            val arrInstant = if (arrAt(date) > depInstant) arrAt(date) else arrAt(date.plusDays(1))
            return Duration.between(depInstant, arrInstant).toMinutes().toInt()
        }
        // ponytail: no zone data — assume one shared timezone, wrap to next day if arr < dep
        val depMin = depHour * 60 + depMinute
        val arrMin = arrHour * 60 + arrMinute
        return if (arrMin >= depMin) arrMin - depMin else (24 * 60 - depMin) + arrMin
    }

    private fun zoneOf(a: Airport?): ZoneId? {
        if (a == null) return null
        if (a.tz.isNotEmpty()) {
            try { return ZoneId.of(a.tz) } catch (e: Exception) { /* fall back to fixed offset */ }
        }
        return a.tzOffset?.let { ZoneOffset.ofTotalSeconds((it * 3600.0).roundToInt()) }
    }

    fun enrich(flight: Flight, ref: ReferenceData): Flight {
        val dep = ref.airport(flight.departureIata.ifEmpty { flight.departureIcao })
        val arr = ref.airport(flight.arrivalIata.ifEmpty { flight.arrivalIcao })
        val airline = ref.airline(flight.airlineIata.ifEmpty { flight.airlineIcao })
            ?: ref.airlineByName(flight.airlineName)
        val plane = ref.plane(flight.aircraftType.ifEmpty { flight.aircraftName })
            ?: ref.planeByName(flight.aircraftName)
        val a220 = cseriesToA220(flight.aircraftType, flight.aircraftName.ifEmpty { plane?.name.orEmpty() })

        var distance = flight.distance
        if (distance <= 0.0 && dep != null && arr != null) {
            distance = haversine(dep.lat, dep.lon, arr.lat, arr.lon).roundToInt().toDouble()
        }

        var duration = flight.durationMinutes
        if (duration <= 0 && flight.depHour >= 0 && flight.arrHour >= 0) {
            duration = durationMinutes(
                flight.flightDate, flight.depHour, flight.depMinute,
                flight.arrHour, flight.arrMinute, dep, arr,
            )
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
            aircraftType = a220?.second ?: flight.aircraftType,
            aircraftName = a220?.first ?: plane?.name ?: flight.aircraftName,
            cabinClass = normalizeCabinClass(flight.cabinClass),
            distance = distance,
            durationMinutes = duration,
        )
    }
}
