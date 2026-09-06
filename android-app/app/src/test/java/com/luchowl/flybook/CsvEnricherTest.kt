package com.luchowl.flybook

import com.luchowl.flybook.data.Airline
import com.luchowl.flybook.data.Airport
import com.luchowl.flybook.data.Enricher
import com.luchowl.flybook.data.Flight
import com.luchowl.flybook.data.Plane
import com.luchowl.flybook.data.ReferenceData
import com.luchowl.flybook.ui.screens.rankedSuggestions
import com.luchowl.flybook.util.Csv
import com.luchowl.flybook.util.Format
import com.luchowl.flybook.util.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvEnricherTest {

    private fun ref(): ReferenceData {
        val airports = mapOf(
            "LYS" to Airport("LYS", "LFLL", "Lyon Saint-Exupery Airport", "Lyon", "France", 45.7256, 5.0811),
            "TIA" to Airport("TIA", "LATI", "Tirana Intl", "Tirana", "Albania", 41.4147, 19.7206),
            "LFLL" to Airport("LYS", "LFLL", "Lyon Saint-Exupery Airport", "Lyon", "France", 45.7256, 5.0811),
            "LATI" to Airport("TIA", "LATI", "Tirana Intl", "Tirana", "Albania", 41.4147, 19.7206),
        )
        val airlines = mapOf(
            "W6" to Airline("W6", "WZZ", "Wizz Air"),
            "WZZ" to Airline("W6", "WZZ", "Wizz Air"),
        )
        val planes = mapOf(
            "A21N" to Plane("321", "A21N", "Airbus A321neo"),
            "321" to Plane("321", "A21N", "Airbus A321neo"),
        )
        return ReferenceData(airports, airlines, planes)
    }

    @Test
    fun extractCode_handlesCombinedAirportStrings() {
        assertEquals("LYS", Csv.extractCode("LYON / LYON SAINT-EXUPERY (LYS/LFLL)"))
        assertEquals("TIA", Csv.extractCode("TIRANA / RINAS (TIA/LATI)"))
        assertEquals("YUL", Csv.extractCode("Montreal (YUL)"))
        assertEquals("LYS", Csv.extractCode("LYS · Lyon (France)"))
        assertEquals("GKA", Csv.extractCode("GKA/AYGA"))
        assertEquals("A21N", Csv.extractCode("A21N"))
        assertEquals("", Csv.extractCode(""))
    }

    @Test
    fun extractCode_handlesTwoLetterAirlineCodes() {
        assertEquals("AC", Csv.extractCode("AC", 2))
        assertEquals("W6", Csv.extractCode("W6", 2))
        assertEquals("LH", Csv.extractCode("LH", 2))
        assertEquals("", Csv.extractCode("Air Canada", 2))
        assertEquals("", Csv.extractCode("", 2))
    }

    @Test
    fun extractCode_formDropdownLabel_withTwoLetterAirline() {
        assertEquals("LH", Csv.extractCode("LH · Lufthansa", 2))
        assertEquals("W6", Csv.extractCode("W6 · Wizz Air", 2))
    }

    @Test
    fun rankedSuggestions_surfacesExactCodeBeforeSubstringMatches() {
        val suggestions = listOf(
            "LYS · Lyon (France)",
            "MRS · Marseille (France)",
            "NCE · Nice (France)",
            "TLS · Toulouse (France)",
            "FRA · Frankfurt (Germany)",
        )
        val result = rankedSuggestions(suggestions, "FRA")
        assertEquals("FRA · Frankfurt (Germany)", result.first())
    }

    @Test
    fun rankedSuggestions_codePrefixBeatsCountrySubstring() {
        val suggestions = listOf(
            "CDG · Paris (France)",
            "LYS · Lyon (France)",
            "FRA · Frankfurt (Germany)",
            "FRU · Bishkek (Kyrgyzstan)",
        )
        val result = rankedSuggestions(suggestions, "FR")
        assertEquals(listOf("FRA · Frankfurt (Germany)", "FRU · Bishkek (Kyrgyzstan)"), result.take(2))
        assertTrue("France airports should still be listed below the code matches", result.size > 2)
    }

    @Test
    fun parseFlights_twoLetterAirlineCode() {
        val csv = "Flight Date,Flight Number,Airline,From,To,Aircraft,Seat,Class,Notes,Distance\n" +
                "2024-05-27,AC801,AC,YUL,CDG,B789,14A,Business,,5900\n"
        val flights = Csv.parseFlights(csv)
        assertEquals(1, flights.size)
        assertEquals("AC", flights[0].airlineIata)
        assertEquals("YUL", flights[0].departureIata)
        assertEquals("CDG", flights[0].arrivalIata)
        assertEquals("B789", flights[0].aircraftType)
    }

    @Test
    fun parseFlights_extractsCodes_and_enrich_resolvesCoordinates() {
        val csv = "Flight Date,Flight Number,Airline,From,To,Aircraft,Seat,Class,Notes,Distance\n" +
                "2024-05-27,FH123,Wizz Air,LYON / LYON SAINT-EXUPERY (LYS/LFLL),TIRANA / RINAS (TIA/LATI),A21N,22A,Economy,hello,1200\n"

        val flights = Csv.parseFlights(csv)
        assertEquals(1, flights.size)
        assertEquals("LYS", flights[0].departureIata)
        assertEquals("TIA", flights[0].arrivalIata)
        assertEquals("A21N", flights[0].aircraftType)

        val enriched = Enricher.enrich(flights[0], ref())
        assertEquals("Lyon Saint-Exupery Airport", enriched.departureName)
        assertEquals("Tirana Intl", enriched.arrivalName)
        assertEquals("Wizz Air", enriched.airlineName)
        assertEquals("Airbus A321neo", enriched.aircraftName)
        assertTrue("distance should be positive", enriched.distance > 0)
        assertTrue("duration should be estimated from distance", enriched.durationMinutes > 0)
    }

    @Test
    fun parseDurationMinutes_handlesFormats() {
        assertEquals(150, Csv.parseDurationMinutes("2:30"))
        assertEquals(150, Csv.parseDurationMinutes("2:30:00"))
        assertEquals(150, Csv.parseDurationMinutes("2h 30m"))
        assertEquals(90, Csv.parseDurationMinutes("90"))
        assertEquals(0, Csv.parseDurationMinutes(""))
    }

    @Test
    fun normalizeCabinClass_mapsCodesToReadableNames() {
        assertEquals("Economy", Enricher.normalizeCabinClass("1"))
        assertEquals("Economy", Enricher.normalizeCabinClass("0"))
        assertEquals("Economy", Enricher.normalizeCabinClass("Y"))
        assertEquals("Economy", Enricher.normalizeCabinClass("economy"))
        assertEquals("Business", Enricher.normalizeCabinClass("B"))
        assertEquals("Business", Enricher.normalizeCabinClass("J"))
        assertEquals("First", Enricher.normalizeCabinClass("F"))
        assertEquals("Premium Economy", Enricher.normalizeCabinClass("PE"))
        assertEquals("", Enricher.normalizeCabinClass(""))
        assertEquals("MyClass", Enricher.normalizeCabinClass("MyClass"))
    }

    @Test
    fun topSeats_countsRepeatedSeats() {
        val flights = listOf(
            Flight(id = "1", flightDate = 0L, seatNumber = "22A"),
            Flight(id = "2", flightDate = 0L, seatNumber = "22A"),
            Flight(id = "3", flightDate = 0L, seatNumber = "1C"),
        )
        val seats = Stats.topSeats(flights)
        assertEquals(2, seats.size)
        assertEquals("22A", seats[0].label)
        assertEquals(2, seats[0].count)
    }

    @Test
    fun achievements_includeFunBadges() {
        val flights = (1..100).map {
            Flight(id = "$it", flightDate = 0L, seatNumber = "22A", depHour = if (it % 2 == 0) 6 else 23)
        }
        val achs = Stats.achievements(flights)
        assertTrue("early5 should be unlocked", achs.any { it.id == "early5" && it.unlocked })
        assertTrue("night5 should be unlocked", achs.any { it.id == "night5" && it.unlocked })
        assertTrue("seat5 should be unlocked", achs.any { it.id == "seat5" && it.unlocked })
        assertTrue("milestone100 should be unlocked", achs.any { it.id == "milestone100" && it.unlocked })
    }

    @Test
    fun topCabinClass_returnsMostFlown() {
        val flights = listOf(
            Flight(id = "1", flightDate = 0L, cabinClass = "Economy"),
            Flight(id = "2", flightDate = 0L, cabinClass = "Economy"),
            Flight(id = "3", flightDate = 0L, cabinClass = "Business"),
        )
        val top = Stats.topCabinClass(flights)
        assertEquals("Economy", top?.label)
        assertEquals(2, top?.count)
        assertEquals(null, Stats.topCabinClass(emptyList()))
    }

    @Test
    fun wideBodyCount_detectsNamesAndCodes() {
        val flights = listOf(
            Flight(id = "1", flightDate = 0L, aircraftName = "Airbus A380-800", aircraftType = ""),
            Flight(id = "2", flightDate = 0L, aircraftName = "Boeing 777-300ER", aircraftType = ""),
            Flight(id = "3", flightDate = 0L, aircraftName = "Airbus A320neo", aircraftType = ""),
            Flight(id = "4", flightDate = 0L, aircraftName = "", aircraftType = "A21N"),
            Flight(id = "5", flightDate = 0L, aircraftName = "", aircraftType = "B789"),
            Flight(id = "6", flightDate = 0L, aircraftName = "", aircraftType = "A388"),
            Flight(id = "7", flightDate = 0L, aircraftName = "", aircraftType = "E190"),
            Flight(id = "8", flightDate = 0L, aircraftName = "", aircraftType = "388"),
        )
        assertEquals(5, Stats.wideBodyCount(flights))
        assertEquals(listOf("1", "2", "5", "6", "8"), Stats.wideBodyFlights(flights).map { it.id })
    }

    @Test
    fun categoryFlightLists_matchTheirCounts() {
        val flights = (1..10).map {
            Flight(
                id = "$it", flightDate = 0L,
                depHour = when (it % 3) { 0 -> 6; 1 -> 23; else -> 12 },
                cabinClass = if (it % 2 == 0) "Business" else "Economy",
                aircraftType = if (it <= 3) "A388" else "A320",
            )
        }
        assertEquals(Stats.earlyFlights(flights), Stats.earlyFlightList(flights).size)
        assertEquals(Stats.nightFlights(flights), Stats.nightFlightList(flights).size)
        assertEquals(5, Stats.flightsForCabinClass(flights, "Business").size)
        assertEquals(3, Stats.wideBodyFlights(flights).size)
        assertEquals(10, Stats.flightsForYear(flights, 1970).size)
    }

    @Test
    fun csvExportImport_roundTrip_preservesRegistrationTimesAndDuration() {
        val original = Flight(
            id = "1",
            flightDate = Format.parseIso("2026-09-01")!!,
            flightNumber = "LH400",
            airlineIata = "LH",
            departureIata = "FRA",
            arrivalIata = "JFK",
            aircraftType = "A333",
            registration = "D-AIKF",
            seatNumber = "14A",
            cabinClass = "Business",
            depHour = 10,
            depMinute = 55,
            arrHour = 13,
            arrMinute = 20,
            distance = 6190.0,
        )
        val parsed = Csv.parseFlights(Csv.serialize(listOf(original))).single()
        assertEquals(original.flightDate, parsed.flightDate)
        assertEquals("D-AIKF", parsed.registration)
        assertEquals(10, parsed.depHour)
        assertEquals(55, parsed.depMinute)
        assertEquals(13, parsed.arrHour)
        assertEquals(20, parsed.arrMinute)
        assertEquals(6190.0, parsed.distance, 0.0)

        val enriched = Enricher.enrich(parsed, ref())
        assertEquals(145, enriched.durationMinutes)
    }

    @Test
    fun parseFlightdiary_format_timesAndRegistration() {
        val csv = "Date,Flight number,From,To,Dep time,Arr time,Duration,Airline,Aircraft,Registration,Seat number,Flight class,Note\n" +
                "2021-12-23,AC876,Montreal (YUL/CYUL),Lyon (LYS/LFLL),21:30:00,11:00:00,07:30:00,Air Canada (AC/ACA),Airbus A330-300 (A333),C-GEG,,0,\n"
        val flights = Csv.parseFlights(csv)
        assertEquals(1, flights.size)
        val f = flights[0]
        assertEquals("YUL", f.departureIata)
        assertEquals("LYS", f.arrivalIata)
        assertEquals("AC", f.airlineIata)
        assertEquals("A333", f.aircraftType)
        assertEquals("C-GEG", f.registration)
        assertEquals(21, f.depHour)
        assertEquals(30, f.depMinute)
        assertEquals(11, f.arrHour)
        assertEquals(0, f.arrMinute)
        assertEquals(450, f.durationMinutes)
    }
}
