package com.luchowl.flybook.util

import com.luchowl.flybook.data.Flight
import com.luchowl.flybook.data.ReferenceData
import com.luchowl.flybook.util.Format

data class FlightRoute(
    val depLat: Double, val depLon: Double,
    val arrLat: Double, val arrLon: Double,
)

data class OverallStats(
    val totalFlights: Int,
    val totalDistanceKm: Double,
    val totalDurationMinutes: Int,
    val uniqueAirlines: Int,
    val uniqueCountries: Int,
    val uniqueAirports: Int,
)

data class Achievement(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val unlocked: Boolean,
    val progress: Float,   // 0..1
    val current: String,
)

object Stats {

    fun overall(flights: List<Flight>): OverallStats {
        val airlines = HashSet<String>()
        val countries = HashSet<String>()
        val airports = HashSet<String>()
        var distance = 0.0
        var duration = 0
        for (f in flights) {
            if (f.airlineName.isNotEmpty()) airlines.add(f.airlineName)
            else if (f.airlineIata.isNotEmpty()) airlines.add(f.airlineIata)
            if (f.departureCountry.isNotEmpty()) countries.add(f.departureCountry)
            if (f.arrivalCountry.isNotEmpty()) countries.add(f.arrivalCountry)
            if (f.departureIata.isNotEmpty()) airports.add(f.departureIata)
            else if (f.departureIcao.isNotEmpty()) airports.add(f.departureIcao)
            if (f.arrivalIata.isNotEmpty()) airports.add(f.arrivalIata)
            else if (f.arrivalIcao.isNotEmpty()) airports.add(f.arrivalIcao)
            distance += f.distance
            duration += f.durationMinutes
        }
        return OverallStats(
            totalFlights = flights.size,
            totalDistanceKm = distance,
            totalDurationMinutes = duration,
            uniqueAirlines = airlines.size,
            uniqueCountries = countries.size,
            uniqueAirports = airports.size,
        )
    }

    /** 12 monthly counts for the given year (UTC). */
    fun monthlyCounts(flights: List<Flight>, year: Int): List<Int> {
        val counts = IntArray(12)
        for (f in flights) {
            if (Format.year(f.flightDate) != year) continue
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            cal.timeInMillis = f.flightDate
            counts[cal.get(java.util.Calendar.MONTH)]++
        }
        return counts.toList()
    }

    fun years(flights: List<Flight>): List<Int> =
        flights.map { Format.year(it.flightDate) }.toSet().sortedDescending()

    /** Monthly counts across ALL years (for the "All" filter). */
    fun monthlyCountsAll(flights: List<Flight>): List<Int> {
        val counts = IntArray(12)
        for (f in flights) {
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            cal.timeInMillis = f.flightDate
            counts[cal.get(java.util.Calendar.MONTH)]++
        }
        return counts.toList()
    }

    /** Routes with resolved coordinates, for the map. */
    fun mapRoutes(flights: List<Flight>, ref: ReferenceData): List<FlightRoute> {
        val out = mutableListOf<FlightRoute>()
        for (f in flights) {
            val dep = ref.airport(f.departureIata.ifEmpty { f.departureIcao }) ?: continue
            val arr = ref.airport(f.arrivalIata.ifEmpty { f.arrivalIcao }) ?: continue
            if (dep.lat == 0.0 && dep.lon == 0.0) continue
            if (arr.lat == 0.0 && arr.lon == 0.0) continue
            out += FlightRoute(dep.lat, dep.lon, arr.lat, arr.lon)
        }
        return out
    }

    // --- Nerd stats / rankings ---

    data class Ranked(val label: String, val count: Int)

    fun topAircraft(flights: List<Flight>): List<Ranked> {
        val m = HashMap<String, Int>()
        for (f in flights) {
            val k = f.aircraftName.ifEmpty { f.aircraftType }
            if (k.isNotEmpty()) m[k] = (m[k] ?: 0) + 1
        }
        return m.entries.map { Ranked(it.key, it.value) }.sortedByDescending { it.count }
    }

    fun topAirlines(flights: List<Flight>): List<Ranked> {
        val m = HashMap<String, Int>()
        for (f in flights) {
            val k = f.airlineName.ifEmpty { f.airlineIata }
            if (k.isNotEmpty()) m[k] = (m[k] ?: 0) + 1
        }
        return m.entries.map { Ranked(it.key, it.value) }.sortedByDescending { it.count }
    }

    fun topRoutes(flights: List<Flight>): List<Ranked> {
        val m = HashMap<String, Int>()
        for (f in flights) {
            val dep = Format.code(f.departureIata, f.departureIcao)
            val arr = Format.code(f.arrivalIata, f.arrivalIcao)
            if (dep != "?" && arr != "?") m["$dep → $arr"] = (m["$dep → $arr"] ?: 0) + 1
        }
        return m.entries.map { Ranked(it.key, it.value) }.sortedByDescending { it.count }
    }

    fun topAirports(flights: List<Flight>): List<Ranked> {
        val m = HashMap<String, Int>()
        for (f in flights) {
            val dep = Format.code(f.departureIata, f.departureIcao)
            val arr = Format.code(f.arrivalIata, f.arrivalIcao)
            if (dep != "?") m[dep] = (m[dep] ?: 0) + 1
            if (arr != "?") m[arr] = (m[arr] ?: 0) + 1
        }
        return m.entries.map { Ranked(it.key, it.value) }.sortedByDescending { it.count }
    }

    fun topRegistrations(flights: List<Flight>): List<Ranked> {
        val m = HashMap<String, Int>()
        for (f in flights) {
            if (f.registration.isNotEmpty()) m[f.registration] = (m[f.registration] ?: 0) + 1
        }
        return m.entries.map { Ranked(it.key, it.value) }.sortedByDescending { it.count }
    }

    fun topSeats(flights: List<Flight>): List<Ranked> {
        val m = HashMap<String, Int>()
        for (f in flights) {
            if (f.seatNumber.isNotEmpty()) m[f.seatNumber] = (m[f.seatNumber] ?: 0) + 1
        }
        return m.entries.map { Ranked(it.key, it.value) }.sortedByDescending { it.count }
    }

    fun topCabinClass(flights: List<Flight>): Ranked? {
        val m = HashMap<String, Int>()
        for (f in flights) {
            if (f.cabinClass.isNotEmpty()) m[f.cabinClass] = (m[f.cabinClass] ?: 0) + 1
        }
        return m.entries.map { Ranked(it.key, it.value) }.maxByOrNull { it.count }
    }

    fun topCountries(flights: List<Flight>): List<Ranked> {
        val m = HashMap<String, Int>()
        for (f in flights) {
            if (f.departureCountry.isNotEmpty()) m[f.departureCountry] = (m[f.departureCountry] ?: 0) + 1
            if (f.arrivalCountry.isNotEmpty()) m[f.arrivalCountry] = (m[f.arrivalCountry] ?: 0) + 1
        }
        return m.entries.map { Ranked(it.key, it.value) }.sortedByDescending { it.count }
    }

    fun longestFlight(flights: List<Flight>): Flight? = flights.maxByOrNull { it.distance }

    fun earlyFlightList(flights: List<Flight>): List<Flight> = flights.filter { it.depHour in 0..7 }

    fun earlyFlights(flights: List<Flight>): Int = earlyFlightList(flights).size

    fun nightFlightList(flights: List<Flight>): List<Flight> = flights.filter { it.depHour >= 22 }

    fun nightFlights(flights: List<Flight>): Int = nightFlightList(flights).size

    private val WIDE_BODY_MARKERS = listOf(
        "A30", "A33", "A34", "A35", "A38", "A310",  // Airbus wide-bodies (names/ICAO)
        "B74", "B76", "B77", "B78",                 // Boeing wide-bodies (names/ICAO)
        "747", "767", "777", "787",                 // Boeing wide-bodies (names)
        "DC10", "MD11", "L101", "IL86", "IL96",
    )

    private val WIDE_BODY_IATA = setOf(
        "300", "310",
        "330", "331", "332", "333", "338", "339", "30A", "30B",
        "342", "343", "345", "346",
        "351", "359",
        "388",
        "741", "742", "743", "744", "748",
        "762", "763", "764",
        "772", "773", "778", "779", "77E", "77F", "77L", "77W", "77X",
        "781", "787", "788", "789", "78J", "78X",
        "D10", "DC1", "M11", "MD1", "IL8", "IL9",
    )

    private fun isWideBody(f: Flight): Boolean {
        val type = f.aircraftType.uppercase()
        val name = f.aircraftName.uppercase()
        return WIDE_BODY_MARKERS.any { type.contains(it) || name.contains(it) } || type in WIDE_BODY_IATA
    }

    fun wideBodyFlights(flights: List<Flight>): List<Flight> = flights.filter { isWideBody(it) }

    fun wideBodyCount(flights: List<Flight>): Int = wideBodyFlights(flights).size

    fun averageDistance(flights: List<Flight>): Double =
        if (flights.isEmpty()) 0.0 else flights.sumOf { it.distance } / flights.size

    fun averageDurationMinutes(flights: List<Flight>): Int =
        if (flights.isEmpty()) 0 else flights.sumOf { it.durationMinutes } / flights.size

    fun busiestYear(flights: List<Flight>): Pair<Int, Int>? {
        val m = HashMap<Int, Int>()
        for (f in flights) {
            val y = Format.year(f.flightDate)
            m[y] = (m[y] ?: 0) + 1
        }
        return m.entries.maxByOrNull { it.value }?.let { it.key to it.value }
    }

    // --- Drill-down filters (used by the ranking detail view) ---

    fun flightsForAircraft(flights: List<Flight>, label: String): List<Flight> =
        flights.filter { it.aircraftName.ifEmpty { it.aircraftType } == label }

    fun flightsForAirline(flights: List<Flight>, label: String): List<Flight> =
        flights.filter { it.airlineName.ifEmpty { it.airlineIata } == label }

    fun flightsForRoute(flights: List<Flight>, label: String): List<Flight> {
        val parts = label.split(" → ")
        if (parts.size != 2) return emptyList()
        return flights.filter {
            Format.code(it.departureIata, it.departureIcao) == parts[0] &&
                Format.code(it.arrivalIata, it.arrivalIcao) == parts[1]
        }
    }

    fun flightsForAirport(flights: List<Flight>, code: String): List<Flight> =
        flights.filter {
            Format.code(it.departureIata, it.departureIcao) == code ||
                Format.code(it.arrivalIata, it.arrivalIcao) == code
        }

    fun flightsForRegistration(flights: List<Flight>, reg: String): List<Flight> =
        flights.filter { it.registration == reg }

    fun flightsForSeat(flights: List<Flight>, seat: String): List<Flight> =
        flights.filter { it.seatNumber == seat }

    fun flightsForCountry(flights: List<Flight>, country: String): List<Flight> =
        flights.filter { it.departureCountry == country || it.arrivalCountry == country }

    fun flightsForCabinClass(flights: List<Flight>, cabinClass: String): List<Flight> =
        flights.filter { it.cabinClass == cabinClass }

    fun flightsForYear(flights: List<Flight>, year: Int): List<Flight> =
        flights.filter { Format.year(it.flightDate) == year }

    fun achievements(flights: List<Flight>): List<Achievement> {
        val s = overall(flights)
        val all = flights
        val countries = s.uniqueCountries
        val airlines = s.uniqueAirlines

        fun ach(id: String, name: String, emoji: String, desc: String, cur: Int, target: Int, curStr: String): Achievement =
            Achievement(id, name, emoji, desc, cur >= target, if (target == 0) 0f else (cur.toFloat() / target).coerceIn(0f, 1f), curStr)

        val list = mutableListOf<Achievement>()
        list += ach("first", "First Flight", "✈️", "Log your first flight", s.totalFlights, 1, "${s.totalFlights} flights")
        list += ach("milestone10", "Sky Veteran", "🔟", "Log 10+ flights", s.totalFlights, 10, "${s.totalFlights} flights")
        list += ach("milestone25", "Seasoned Flyer", "🥈", "Log 25+ flights", s.totalFlights, 25, "${s.totalFlights} flights")
        list += ach("milestone50", "Milestone Maker", "🌟", "Log your 50th flight", s.totalFlights, 50, "${s.totalFlights} flights")
        list += ach("dist10k", "Voyager", "📏", "Fly 10,000+ km", s.totalDistanceKm.toInt(), 10000, Format.distance(s.totalDistanceKm))
        list += ach("dist40k", "Circumnavigator", "🔄", "Fly 40,000+ km", s.totalDistanceKm.toInt(), 40000, Format.distance(s.totalDistanceKm))
        list += ach("dist100k", "Lunar Leap", "🚀", "Fly 100,000+ km", s.totalDistanceKm.toInt(), 100000, Format.distance(s.totalDistanceKm))
        list += ach("airlines5", "Airline Explorer", "🧑‍✈️", "Fly 5+ airlines", airlines, 5, "$airlines airlines")
        list += ach("countries5", "Intl Traveler", "🌍", "Visit 5+ countries", countries, 5, "$countries countries")
        list += ach("countries10", "Global Citizen", "🌐", "Visit 10+ countries", countries, 10, "$countries countries")
        list += ach("airports15", "Jet Setter", "🗺️", "Fly to 15+ airports", s.uniqueAirports, 15, "${s.uniqueAirports} airports")

        // Fun / lifestyle badges
        val early = all.count { it.depHour in 0..7 }
        val night = all.count { it.depHour >= 22 }
        val wideBody = wideBodyCount(all)
        val distinctAircraft = topAircraft(all).size
        val distinctRoutes = topRoutes(all).size
        val maxRouteCount = topRoutes(all).firstOrNull()?.count ?: 0
        val maxSeatCount = topSeats(all).firstOrNull()?.count ?: 0
        list += ach("early5", "Early Bird", "🌅", "Take 5+ flights before 8am", early, 5, "$early early flights")
        list += ach("night5", "Night Owl", "🌙", "Take 5+ flights after 10pm", night, 5, "$night night flights")
        list += ach("widebody10", "Wide-Body Connoisseur", "👑", "Fly 10+ wide-body flights", wideBody, 10, "$wideBody wide-body flights")
        list += ach("aircraft10", "Aircraft Collector", "🛩️", "Fly 10+ different aircraft types", distinctAircraft, 10, "$distinctAircraft types")
        list += ach("route5", "Route Regular", "🔁", "Fly the same route 5+ times", maxRouteCount, 5, "best route ×$maxRouteCount")
        list += ach("routes20", "Globe Trotter", "🌏", "Fly 20+ different routes", distinctRoutes, 20, "$distinctRoutes routes")
        list += ach("seat5", "Seat Spotter", "💺", "Log the same seat 5+ times", maxSeatCount, 5, "best seat ×$maxSeatCount")
        list += ach("milestone100", "Frequent Flyer", "💼", "Log 100+ flights", s.totalFlights, 100, "${s.totalFlights} flights")
        // Longest flight
        val longest = all.maxByOrNull { it.distance }
        list += Achievement(
            "longest", "Longest Haul", "⏳",
            "Longest flight distance",
            longest != null && longest.distance >= 1000,
            if (longest == null) 0f else (longest.distance / 1000.0).coerceIn(0.0, 1.0).toFloat(),
            longest?.let { Format.distance(it.distance) } ?: "—",
        )

        // Aspirational badges (high targets to keep chasing)
        val maxMonthsInYear = all.groupBy { Format.year(it.flightDate) }
            .values.maxOfOrNull { y -> y.map { Format.month(it.flightDate) }.toSet().size } ?: 0
        val distinctYears = all.map { Format.year(it.flightDate) }.toSet().size
        val totalHours = s.totalDurationMinutes / 60
        val longestDist = longest?.distance ?: 0.0
        val redEye = all.count { it.depHour in 0..3 }
        val maxFlightsInDay = all.groupBy { Format.iso(it.flightDate) }.values.maxOfOrNull { it.size } ?: 0
        list += ach("yearround", "Year-Round Flyer", "📆", "Fly in all 12 months of a year", maxMonthsInYear, 12, "$maxMonthsInYear/12 months")
        list += ach("years3", "Time Traveler", "⏰", "Log flights in 3+ different years", distinctYears, 3, "$distinctYears years")
        list += ach("hours100", "Marathon Man", "⏱️", "Spend 100+ hours in the air", totalHours, 100, Format.duration(s.totalDurationMinutes))
        list += ach("longest5k", "Sky-High", "🗻", "Fly a single flight of 5,000+ km", longestDist.toInt(), 5000, longest?.let { Format.distance(it.distance) } ?: "—")
        list += ach("route10", "Round Tripper", "🔄", "Fly the same route 10+ times", maxRouteCount, 10, "best route ×$maxRouteCount")
        list += ach("airports30", "Airport Hopper", "🚪", "Fly to 30+ airports", s.uniqueAirports, 30, "${s.uniqueAirports} airports")
        list += ach("redeye3", "Red-Eye Rider", "🌃", "Take 3+ flights departing between midnight and 4am", redEye, 3, "$redEye red-eye flights")
        list += ach("day3", "Day Tripper", "🏃", "Log 3+ flights in a single day", maxFlightsInDay, 3, "best day ×$maxFlightsInDay")
        return list
    }
}
