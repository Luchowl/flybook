package com.luchowl.flybook

import com.luchowl.flybook.ui.globe.GlobeCamera
import com.luchowl.flybook.ui.globe.GlobeMath
import com.luchowl.flybook.ui.globe.buildScene
import com.luchowl.flybook.util.FlightRoute
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.random.Random

class GlobeStressTest {

    /** Every point finite, radius within [1, 1 + lift], neighbours at most ~stepDeg apart. */
    private fun checkArc(label: String, lat1: Double, lon1: Double, lat2: Double, lon2: Double) {
        val a = GlobeMath.toXyz(lat1, lon1)
        val b = GlobeMath.toXyz(lat2, lon2)
        val arc = GlobeMath.arc(a, b)
        val n = arc.size / 3
        var prev: DoubleArray? = null
        for (i in 0 until n) {
            val x = arc[i * 3].toDouble(); val y = arc[i * 3 + 1].toDouble(); val z = arc[i * 3 + 2].toDouble()
            if (!x.isFinite() || !y.isFinite() || !z.isFinite()) fail("$label: non-finite point $i")
            val r = sqrt(x * x + y * y + z * z)
            if (r < 0.999 || r > 1.081) fail("$label: point $i radius $r off the globe/lift range")
            val u = doubleArrayOf(x / r, y / r, z / r)
            prev?.let {
                val step = Math.toDegrees(acos((it[0] * u[0] + it[1] * u[1] + it[2] * u[2]).coerceIn(-1.0, 1.0)))
                if (step > 2.5) fail("$label: jump of $step° between points ${i - 1} and $i")
            }
            prev = u
        }
        val end = doubleArrayOf(arc[(n - 1) * 3].toDouble(), arc[(n - 1) * 3 + 1].toDouble(), arc[(n - 1) * 3 + 2].toDouble())
        val endKm = GlobeMath.angle(end, b) * 6371.0
        if (endKm > 0.05) fail("$label: arc ends $endKm km from destination")
    }

    @Test fun sameAirport() = checkArc("FRA→FRA", 50.03, 8.57, 50.03, 8.57)
    @Test fun exactlyAntipodal() = checkArc("antipodes", 10.0, 20.0, -10.0, -160.0)
    @Test fun nearlyAntipodal() = checkArc("near antipodes", 10.0, 20.0, -10.0000001, -160.0)
    @Test fun madridWellington() = checkArc("MAD→WLG", 40.49, -3.57, -41.33, 174.81)
    @Test fun poleToPole() = checkArc("pole→pole", 90.0, 0.0, -90.0, 0.0)
    @Test fun acrossDateLine() = checkArc("NRT→SFO", 35.76, 140.39, 37.62, -122.38)
    @Test fun overNorthPole() = checkArc("ANC→ARN", 61.17, -149.99, 59.65, 17.92)
    @Test fun veryShortHop() = checkArc("PPW→WRY (2.7 km)", 59.3519, -2.9003, 59.3503, -2.9500)

    @Test
    fun thousandsOfRandomRoutes() {
        val rnd = Random(42)
        repeat(20_000) {
            checkArc(
                "random #$it",
                rnd.nextDouble(-90.0, 90.0), rnd.nextDouble(-180.0, 180.0),
                rnd.nextDouble(-90.0, 90.0), rnd.nextDouble(-180.0, 180.0),
            )
        }
    }

    @Test
    fun cameraAtExtremesStaysFinite() {
        val rnd = Random(7)
        val pts = FloatArray(3 * 1000) { 0f }.also { arr ->
            for (i in 0 until 1000) {
                val v = GlobeMath.toXyz(rnd.nextDouble(-90.0, 90.0), rnd.nextDouble(-180.0, 180.0))
                arr[i * 3] = v[0].toFloat(); arr[i * 3 + 1] = v[1].toFloat(); arr[i * 3 + 2] = v[2].toFloat()
            }
        }
        for (lat in listOf(-85.0, -45.0, 0.0, 45.0, 85.0)) for (lon in listOf(-180.0, -179.99, 0.0, 179.99, 180.0)) {
            val cam = GlobeCamera(lat, lon)
            for (i in 0 until 1000) {
                val s = cam.sx(pts, i * 3); val t = cam.sy(pts, i * 3); val d = cam.depth(pts, i * 3)
                assertTrue(s.isFinite() && t.isFinite() && d.isFinite())
                assertTrue("not on unit sphere", kotlin.math.abs(s * s + t * t + d * d - 1f) < 1e-4f)
            }
        }
    }

    @Test
    fun bigLogbookSceneBuildIsFast() {
        val rnd = Random(1)
        val airports = List(800) { rnd.nextDouble(-60.0, 70.0) to rnd.nextDouble(-180.0, 180.0) }
        val routes = List(10_000) {
            val a = airports[rnd.nextInt(airports.size)]
            val b = airports[rnd.nextInt(airports.size)]
            FlightRoute(a.first, a.second, b.first, b.second)
        }
        buildScene(routes.take(100), null) // warm-up
        val t0 = System.nanoTime()
        val scene = buildScene(routes, null)
        val ms = (System.nanoTime() - t0) / 1e6
        val pts = scene.arcs.sumOf { it.points.size / 3 }
        println("10k flights → ${scene.arcs.size} arcs, ${scene.airports.size} airports, $pts arc points, built in ${"%.0f".format(ms)} ms")
        assertTrue("scene build took $ms ms", ms < 2000)
    }

    /** The cull test must never hide an arc that has a visible point. */
    @Test
    fun arcCullingNeverHidesVisibleArcs() {
        val rnd = Random(3)
        val routes = List(3000) {
            FlightRoute(
                rnd.nextDouble(-89.0, 89.0), rnd.nextDouble(-180.0, 180.0),
                rnd.nextDouble(-89.0, 89.0), rnd.nextDouble(-180.0, 180.0),
            )
        } + FlightRoute(10.0, 20.0, -10.0, -160.0) // antipodal
        val scene = buildScene(routes, null)
        var culled = 0
        repeat(200) {
            val cam = GlobeCamera(rnd.nextDouble(-85.0, 85.0), rnd.nextDouble(-180.0, 180.0))
            for (arc in scene.arcs) {
                if (cam.depth(arc.mid, 0) >= -arc.sinHalf) continue
                culled++
                for (i in arc.points.indices step 3) {
                    if (cam.visible(arc.points, i)) fail("culled arc has a visible point")
                }
            }
        }
        assertTrue("culling should skip a good share of arcs, got $culled", culled > 200 * 3000 / 10)
    }
}
