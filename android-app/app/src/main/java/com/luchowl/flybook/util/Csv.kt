package com.luchowl.flybook.util

import com.luchowl.flybook.data.Flight
import java.io.File
import java.util.UUID

/**
 * Minimal RFC-4180-ish CSV reader/writer for import/export.
 */
object Csv {

    fun serialize(flights: List<Flight>): String {
        val sb = StringBuilder()
        sb.appendLine("Flight Date,Flight Number,Airline,From,To,Aircraft,Seat,Class,Notes,Distance")
        for (f in flights) {
            val row = listOf(
                Format.iso(f.flightDate),
                f.flightNumber,
                f.airlineName.ifEmpty { f.airlineIata },
                Format.code(f.departureIata, f.departureIcao),
                Format.code(f.arrivalIata, f.arrivalIcao),
                f.aircraftName.ifEmpty { f.aircraftType },
                f.seatNumber,
                f.cabinClass,
                f.notes,
                Math.round(f.distance).toString(),
            )
            sb.appendLine(row.joinToString(",") { field(it) })
        }
        return sb.toString()
    }

    private fun field(value: String): String {
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    /**
     * Parses CSV text into a list of [StringArray]. Handles quoted fields.
     */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val current = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> {
                    when {
                        c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                            sb.append('"'); i++
                        }
                        c == '"' -> inQuotes = false
                        else -> sb.append(c)
                    }
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    current.add(sb.toString()); sb.setLength(0)
                }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    current.add(sb.toString()); sb.setLength(0)
                    if (current.any { it.isNotEmpty() }) rows.add(current.toList())
                    current.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || current.isNotEmpty()) {
            current.add(sb.toString())
            if (current.any { it.isNotEmpty() }) rows.add(current.toList())
        }
        return rows
    }

    fun toJson(flights: List<Flight>): String {
        val sb = StringBuilder()
        sb.append("[\n")
        flights.forEachIndexed { index, f ->
            sb.append("  {")
            sb.append("\"id\":\"").append(esc(f.id)).append("\"")
            sb.append(",\"flightDate\":\"").append(Format.iso(f.flightDate)).append("\"")
            sb.append(",\"flightNumber\":\"").append(esc(f.flightNumber)).append("\"")
            sb.append(",\"airlineIata\":\"").append(esc(f.airlineIata)).append("\"")
            sb.append(",\"airlineName\":\"").append(esc(f.airlineName)).append("\"")
            sb.append(",\"departureIata\":\"").append(esc(f.departureIata)).append("\"")
            sb.append(",\"arrivalIata\":\"").append(esc(f.arrivalIata)).append("\"")
            sb.append(",\"aircraftType\":\"").append(esc(f.aircraftType)).append("\"")
            sb.append(",\"seatNumber\":\"").append(esc(f.seatNumber)).append("\"")
            sb.append(",\"cabinClass\":\"").append(esc(f.cabinClass)).append("\"")
            sb.append(",\"notes\":\"").append(esc(f.notes)).append("\"")
            sb.append(",\"distance\":").append(f.distance)
            sb.append("}")
            if (index < flights.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun esc(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    /**
     * Exports the serialized CSV to a file and returns the File.
     */
    fun writeToFile(dir: File, name: String, content: String): File {
        val file = File(dir, name)
        file.writeText(content)
        return file
    }

    /**
     * Extracts a code from a variety of formats:
     *   "LYS", "GKA/AYGA", "Name (LYS/LFLL)", "LYON / LYON SAINT-EXUPERY (LYS/LFLL)",
     *   "LYS · Lyon (France)", "Montreal (YUL)", "A21N", "AC", "W6"
     * @param minLen minimum code length (airlines use 2, airports/aircraft use 3)
     */
    fun extractCode(value: String, minLen: Int = 3): String {
        val v = value.trim()
        if (v.isEmpty()) return ""
        val isCode = { s: String -> s.length in minLen..4 && s.all { it.isLetterOrDigit() } }

        // 1. bare code
        if (isCode(v)) return v.uppercase()

        // 2. parenthesized code groups, e.g. "Name (LYS/LFLL)" or "Name (221)"
        for (m in Regex("\\(([^)]*)\\)").findAll(v)) {
            val inner = m.groupValues[1].trim()
            for (part in inner.split('/', '·')) {
                val code = part.trim()
                if (isCode(code)) return code.uppercase()
            }
        }

        // 3. leading code before a separator, e.g. "LYS · Lyon" or "LYS/LFLL"
        val head = v.split(Regex("\\s*(/|·)\\s*")).firstOrNull()?.trim()
        if (head != null && isCode(head)) return head.uppercase()

        // 4. first token of a slash pair, e.g. "GKA/AYGA"
        val slashed = v.split('/').firstOrNull()?.trim()
        if (slashed != null && isCode(slashed)) return slashed.uppercase()

        return ""
    }

    /**
     * Parses a duration value into minutes. Accepts "2:30", "2:30:00", "2h 30m", or a bare number (minutes).
     */
    fun parseDurationMinutes(value: String): Int {
        val v = value.trim().lowercase()
        if (v.isEmpty()) return 0
        val hm = Regex("(\\d+):(\\d{1,2})").find(v)
        if (hm != null) {
            val h = hm.groupValues[1].toInt()
            val m = hm.groupValues[2].toInt()
            return h * 60 + m
        }
        val hours = Regex("(\\d+)\\s*h").find(v)
        val mins = Regex("(\\d+)\\s*m").find(v)
        if (hours != null || mins != null) {
            return (hours?.groupValues?.get(1)?.toInt() ?: 0) * 60 + (mins?.groupValues?.get(1)?.toInt() ?: 0)
        }
        return v.toDoubleOrNull()?.toInt() ?: 0
    }

    /**
     * Parses a time-of-day "HH:MM" or "HH:MM:SS" into (hour, minute), or null.
     */
    fun parseTimeOfDay(value: String): Pair<Int, Int>? {
        val m = Regex("(\\d{1,2}):(\\d{1,2})").find(value.trim())
        if (m == null) return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h in 0..23 && min in 0..59) return h to min
        return null
    }

    private val HEADER_ALIASES = mapOf(
        "date" to "date",
        "flight date" to "date",
        "flight number" to "flightNumber",
        "number" to "flightNumber",
        "airline" to "airline",
        "from" to "from",
        "departure" to "from",
        "to" to "to",
        "arrival" to "to",
        "aircraft" to "aircraft",
        "aircraft type" to "aircraft",
        "type" to "aircraft",
        "registration" to "registration",
        "reg" to "registration",
        "seat" to "seat",
        "seat number" to "seat",
        "class" to "cabinClass",
        "cabin class" to "cabinClass",
        "flight class" to "cabinClass",
        "notes" to "notes",
        "note" to "notes",
        "distance" to "distance",
        "duration" to "duration",
        "flight time" to "duration",
        "duration (min)" to "duration",
        "dep time" to "depTime",
        "departure time" to "depTime",
        "time out" to "depTime",
        "arr time" to "arrTime",
        "arrival time" to "arrTime",
        "time in" to "arrTime",
    )

    /**
     * Parses CSV text into Flight objects. Header-aware (matches our export format
     * and the web app's export), falling back to positional columns when needed.
     */
    fun parseFlights(text: String): List<Flight> {
        val rows = parse(text)
        if (rows.isEmpty()) return emptyList()

        var start = 0
        var colDate = 0
        var colNumber = 1
        var colAirline = 2
        var colFrom = 3
        var colTo = 4
        var colAircraft = 5
        var colSeat = 6
        var colClass = 7
        var colNotes = 8
        var colDistance = 9
        var colDuration = -1
        var colDepTime = -1
        var colArrTime = -1
        var colRegistration = -1

        // Detect header row
        if (rows[0].any { HEADER_ALIASES.containsKey(it.trim().lowercase()) }) {
            start = 1
            val map = mutableMapOf<String, Int>()
            rows[0].forEachIndexed { i, h -> map[HEADER_ALIASES[h.trim().lowercase()] ?: ""] = i }
            fun col(name: String) = map[name] ?: -1
            val g = { name: String, fallback: Int -> if (col(name) >= 0) col(name) else fallback }
            colDate = g("date", colDate)
            colNumber = g("flightNumber", colNumber)
            colAirline = g("airline", colAirline)
            colFrom = g("from", colFrom)
            colTo = g("to", colTo)
            colAircraft = g("aircraft", colAircraft)
            colSeat = g("seat", colSeat)
            colClass = g("cabinClass", colClass)
            colNotes = g("notes", colNotes)
            colDistance = g("distance", colDistance)
            colDuration = col("duration")
            colDepTime = col("depTime")
            colArrTime = col("arrTime")
            colRegistration = col("registration")
        }

        val out = mutableListOf<Flight>()
        for (i in start until rows.size) {
            val r = rows[i]
            if (r.size < 2) continue
            val get = { idx: Int -> if (idx in 0 until r.size) r[idx].trim() else "" }
            val date = Format.parseIso(get(colDate)) ?: continue
            val dep = extractCode(get(colFrom))
            val arr = extractCode(get(colTo))
            if (dep.isEmpty() || arr.isEmpty()) continue
            val airlineRaw = get(colAirline)
            val aircraftRaw = get(colAircraft)
            val airlineCode = extractCode(airlineRaw, 2) // airline IATA can be 2 chars (AC, W6)
            val aircraftCode = extractCode(aircraftRaw, 2)
            val durationMinutes = if (colDuration >= 0) parseDurationMinutes(get(colDuration)) else 0
            val depTime = if (colDepTime >= 0) parseTimeOfDay(get(colDepTime)) else null
            val arrTime = if (colArrTime >= 0) parseTimeOfDay(get(colArrTime)) else null
            out += Flight(
                id = UUID.randomUUID().toString(),
                flightDate = date,
                flightNumber = get(colNumber),
                airlineIata = airlineCode,
                airlineName = if (airlineCode.isEmpty()) airlineRaw else "",
                departureIata = dep,
                arrivalIata = arr,
                aircraftType = aircraftCode,
                aircraftName = if (aircraftCode.isEmpty()) aircraftRaw else "",
                registration = get(colRegistration),
                seatNumber = get(colSeat),
                cabinClass = get(colClass),
                notes = get(colNotes),
                distance = get(colDistance).toDoubleOrNull() ?: 0.0,
                durationMinutes = durationMinutes,
                depHour = depTime?.first ?: -1,
                depMinute = depTime?.second ?: 0,
                arrHour = arrTime?.first ?: -1,
                arrMinute = arrTime?.second ?: 0,
            )
        }
        return out
    }
}
