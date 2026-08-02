package com.neonstick.flybook.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class Airport(
    val iata: String,
    val icao: String,
    val name: String,
    val city: String,
    val country: String,
    val lat: Double,
    val lon: Double,
)

data class Airline(
    val iata: String,
    val icao: String,
    val name: String,
)

data class Plane(
    val iata: String,
    val icao: String,
    val name: String,
)

class ReferenceData(
    val airports: Map<String, Airport>,
    val airlines: Map<String, Airline>,
    val planes: Map<String, Plane>,
) {
    fun airport(code: String?): Airport? =
        code?.let { airports[it.uppercase(Locale.ROOT)] }

    fun airline(code: String?): Airline? =
        code?.let { airlines[it.uppercase(Locale.ROOT)] }

    fun plane(code: String?): Plane? =
        code?.let { planes[it.uppercase(Locale.ROOT)] }

    fun airlineByName(name: String?): Airline? =
        name?.takeIf { it.isNotEmpty() }?.let { n ->
            airlines.values.firstOrNull { it.name.equals(n, ignoreCase = true) }
        }

    fun planeByName(name: String?): Plane? =
        name?.takeIf { it.isNotEmpty() }?.let { n ->
            planes.values.firstOrNull { it.name.equals(n, ignoreCase = true) }
        }

    companion object {
        @Volatile
        private var instance: ReferenceData? = null

        suspend fun get(context: Context): ReferenceData =
            instance ?: withContext(Dispatchers.IO) {
                instance ?: load(context).also { instance = it }
            }

        private fun parseAirports(json: String): Map<String, Airport> {
            val map = HashMap<String, Airport>()
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o: JSONObject = arr.getJSONObject(i)
                val iata = o.optString("iata", "")
                val icao = o.optString("icao", "")
                if (iata.isEmpty() && icao.isEmpty()) continue
                val a = Airport(
                    iata = iata,
                    icao = icao,
                    name = o.optString("name", ""),
                    city = o.optString("city", ""),
                    country = o.optString("country", ""),
                    lat = o.optDouble("latitude", 0.0),
                    lon = o.optDouble("longitude", 0.0),
                )
                if (iata.isNotEmpty()) {
                    map[iata.uppercase(Locale.ROOT)] = a
                    map[iata] = a
                }
                if (icao.isNotEmpty()) {
                    map[icao.uppercase(Locale.ROOT)] = a
                    map[icao] = a
                }
            }
            return map
        }

        private fun parseAirlines(json: String): Map<String, Airline> {
            val map = HashMap<String, Airline>()
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o: JSONObject = arr.getJSONObject(i)
                val iata = o.optString("iata", "")
                val icao = o.optString("icao", "")
                if (iata.isEmpty() && icao.isEmpty()) continue
                val a = Airline(iata = iata, icao = icao, name = o.optString("name", ""))
                if (iata.isNotEmpty()) map[iata.uppercase(Locale.ROOT)] = a
                if (icao.isNotEmpty()) map[icao.uppercase(Locale.ROOT)] = a
            }
            return map
        }

        private fun parsePlanes(json: String): Map<String, Plane> {
            val map = HashMap<String, Plane>()
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o: JSONObject = arr.getJSONObject(i)
                val iata = o.optString("iata", "")
                val icao = o.optString("icao", "")
                if (iata.isEmpty() && icao.isEmpty()) continue
                val p = Plane(iata = iata, icao = icao, name = o.optString("name", ""))
                if (iata.isNotEmpty()) map[iata.uppercase(Locale.ROOT)] = p
                if (icao.isNotEmpty()) map[icao.uppercase(Locale.ROOT)] = p
            }
            return map
        }

        private fun load(context: Context): ReferenceData {
            val airports = parseAirports(readAsset(context, "airports.json"))
            val airlines = parseAirlines(readAsset(context, "airlines.json"))
            val planes = parsePlanes(readAsset(context, "planes.json"))
            return ReferenceData(airports, airlines, planes)
        }

        private fun readAsset(context: Context, name: String): String =
            context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
