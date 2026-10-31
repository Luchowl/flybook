package com.luchowl.flybook.ui.globe

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Land polygons and country borders for the globe, from the bundled
 * `world-110m.json` (Natural Earth 1:110m, public domain). Each entry is a
 * ring/line of xyz unit-vector triples (see [GlobeMath]).
 */
internal class WorldOutline(
    val land: List<FloatArray>,
    val borders: List<FloatArray>,
) {
    companion object {
        @Volatile
        private var instance: WorldOutline? = null

        suspend fun get(context: Context): WorldOutline =
            instance ?: withContext(Dispatchers.IO) {
                instance ?: parse(
                    context.assets.open("world-110m.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
                ).also { instance = it }
            }

        internal fun parse(json: String): WorldOutline {
            val root = JSONObject(json)
            return WorldOutline(lines(root.getJSONArray("land")), lines(root.getJSONArray("borders")))
        }

        private fun lines(arr: JSONArray): List<FloatArray> = List(arr.length()) { i ->
            val flat = arr.getJSONArray(i)
            GlobeMath.packLonLat(DoubleArray(flat.length()) { flat.getDouble(it) })
        }
    }
}
