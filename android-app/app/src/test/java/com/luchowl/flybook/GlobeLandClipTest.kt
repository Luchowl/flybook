package com.luchowl.flybook

import com.luchowl.flybook.data.Flight
import com.luchowl.flybook.ui.globe.GlobeCamera
import com.luchowl.flybook.ui.globe.GlobeMath
import com.luchowl.flybook.ui.globe.LandClipper
import com.luchowl.flybook.ui.globe.WorldOutline
import com.luchowl.flybook.ui.globe.buildScene
import com.luchowl.flybook.util.FlightRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class GlobeLandClipTest {

    private val world: WorldOutline by lazy {
        val f = listOf("src/main/assets/world-110m.json", "app/src/main/assets/world-110m.json")
            .map(::File).first { it.exists() }
        WorldOutline.parse(f.readText())
    }

    private fun isLand(lat: Double, lon: Double): Boolean =
        world.landLonLat.count { LandClipper.contains(it, lon.toFloat(), lat.toFloat()) } % 2 == 1

    /** Share of sampled on-screen points whose even-odd fill disagrees with the real land mask. */
    private fun mismatch(centerLat: Double, centerLon: Double, rnd: Random, samples: Int = 400): Double {
        val cam = GlobeCamera(centerLat, centerLon)
        val clipper = LandClipper()
        val polys = ArrayList<FloatArray>()
        var invert = false
        var antiLon = centerLon + 180.0
        if (antiLon > 180.0) antiLon -= 360.0
        for (k in world.land.indices) {
            if (!clipper.clip(world.land[k], cam, 0.05f)) continue
            if (LandClipper.contains(world.landLonLat[k], antiLon.toFloat(), (-centerLat).toFloat())) invert = !invert
            polys += clipper.out.data.copyOf(clipper.out.size)
            for (i in 0 until clipper.out.size) assertTrue(clipper.out.data[i].isFinite())
        }
        // Camera basis, as in GlobeCamera.
        val lat = Math.toRadians(centerLat); val lon = Math.toRadians(centerLon)
        val e = doubleArrayOf(-sin(lon), cos(lon), 0.0)
        val n = doubleArrayOf(-sin(lat) * cos(lon), -sin(lat) * sin(lon), cos(lat))
        val c = doubleArrayOf(cos(lat) * cos(lon), cos(lat) * sin(lon), sin(lat))
        var bad = 0
        repeat(samples) {
            var x: Double; var y: Double
            do { x = rnd.nextDouble(-1.0, 1.0); y = rnd.nextDouble(-1.0, 1.0) } while (x * x + y * y > 0.95 * 0.95)
            val d = sqrt(1 - x * x - y * y)
            val (pLat, pLon) = GlobeMath.toLatLon(DoubleArray(3) { x * e[it] + y * n[it] + d * c[it] })
            var filled = invert
            for (p in polys) if (LandClipper.contains(p, x.toFloat(), y.toFloat())) filled = !filled
            if (filled != isLand(pLat, pLon)) bad++
        }
        return bad.toDouble() / samples
    }

    /** Views whose far side is centred on a continent used to draw land and sea swapped. */
    @Test
    fun fillMatchesLandMaskWhenAntipodeIsOnLand() {
        val rnd = Random(11)
        val views = listOf(
            0.0 to -160.0,   // antipode in Africa
            -30.0 to -75.0,  // antipode in China
            85.0 to 10.0,    // antipode in Antarctica
            25.0 to -45.0,   // antipode in Australia
            -45.0 to 100.0,  // antipode in North America
            15.0 to 120.0,   // antipode in South America
        )
        for ((lat, lon) in views) {
            assertTrue("antipode of $lat,$lon should be land", isLand(-lat, if (lon + 180 > 180) lon - 180 else lon + 180))
            val m = mismatch(lat, lon, rnd, 2000)
            assertTrue("view $lat,$lon: ${m * 100}% of the disc filled wrongly", m < 0.02)
        }
    }

    @Test
    fun fillMatchesLandMaskFromRandomViews() {
        val rnd = Random(5)
        var worst = 0.0
        var total = 0.0
        repeat(400) {
            val lat = rnd.nextDouble(-85.0, 85.0); val lon = rnd.nextDouble(-180.0, 180.0)
            val m = mismatch(lat, lon, rnd)
            total += m
            if (m > worst) worst = m
            assertTrue("view $lat,$lon: ${m * 100}% of the disc filled wrongly", m < 0.04)
        }
        println("land clip: mean mismatch ${"%.3f".format(total / 4)}%, worst ${"%.2f".format(worst * 100)}%")
    }

    /** A slow spin must not make the filled area jump from one frame to the next. */
    @Test
    fun fillIsStableWhileSpinning() {
        var prev = -1.0
        var lon = -180.0
        while (lon < 180.0) {
            val share = landShare(20.0, lon)
            if (prev >= 0) assertTrue("land share jumped $prev → $share at lon $lon", kotlin.math.abs(share - prev) < 0.03)
            prev = share
            lon += 0.5
        }
    }

    private fun landShare(centerLat: Double, centerLon: Double): Double {
        val cam = GlobeCamera(centerLat, centerLon)
        val clipper = LandClipper()
        val polys = ArrayList<FloatArray>()
        var invert = false
        var antiLon = centerLon + 180.0
        if (antiLon > 180.0) antiLon -= 360.0
        for (k in world.land.indices) {
            if (!clipper.clip(world.land[k], cam, 0.05f)) continue
            if (LandClipper.contains(world.landLonLat[k], antiLon.toFloat(), (-centerLat).toFloat())) invert = !invert
            polys += clipper.out.data.copyOf(clipper.out.size)
        }
        var filled = 0; var all = 0
        var gy = -0.975
        while (gy < 1) {
            var gx = -0.975
            while (gx < 1) {
                if (gx * gx + gy * gy < 0.9) {
                    all++
                    var f = invert
                    for (p in polys) if (LandClipper.contains(p, gx.toFloat(), gy.toFloat())) f = !f
                    if (f) filled++
                }
                gx += 0.05
            }
            gy += 0.05
        }
        return filled.toDouble() / all
    }

    @Test
    fun airportsCarryTheirFlights() {
        fun flight(id: String) = Flight(id = id, flightDate = 0L)
        val lhr = 51.47 to -0.45; val jfk = 40.64 to -73.78; val cdg = 49.01 to 2.55
        val scene = buildScene(
            listOf(
                FlightRoute(lhr.first, lhr.second, jfk.first, jfk.second, flight("a")),
                FlightRoute(jfk.first, jfk.second, lhr.first, lhr.second, flight("b")),
                FlightRoute(lhr.first, lhr.second, cdg.first, cdg.second, flight("c")),
                FlightRoute(cdg.first, cdg.second, cdg.first, cdg.second, flight("d")), // local flight
            ),
            null,
        )
        val ids = scene.airports.map { it.flightList.map(Flight::id) }.sortedBy { it.size }
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d"), listOf("a", "b", "c")), ids)
        scene.airports.forEach { assertEquals(it.flights, it.flightList.size) }
    }
}
