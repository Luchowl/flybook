package com.luchowl.flybook

import com.luchowl.flybook.ui.globe.GlobeCamera
import com.luchowl.flybook.ui.globe.GlobeMath
import com.luchowl.flybook.ui.globe.WorldOutline
import com.luchowl.flybook.util.FlightRoute
import com.luchowl.flybook.ui.globe.buildScene
import com.luchowl.flybook.ui.globe.hitTest
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

class GlobeMathTest {

    private fun pt(lat: Double, lon: Double): FloatArray =
        GlobeMath.toXyz(lat, lon).map { it.toFloat() }.toFloatArray()

    @Test
    fun cameraCenterProjectsToOriginFacingViewer() {
        val cam = GlobeCamera(48.0, 11.0)
        val p = pt(48.0, 11.0)
        assertEquals(0f, cam.sx(p, 0), 1e-5f)
        assertEquals(0f, cam.sy(p, 0), 1e-5f)
        assertEquals(1f, cam.depth(p, 0), 1e-5f)
    }

    @Test
    fun cameraOrientationEastRightNorthUpAntipodeHidden() {
        val cam = GlobeCamera(0.0, 0.0)
        assertTrue(cam.sx(pt(0.0, 30.0), 0) > 0f)   // east is right
        assertTrue(cam.sy(pt(30.0, 0.0), 0) > 0f)   // north is up
        assertFalse(cam.visible(pt(0.0, 180.0), 0)) // far side hidden
    }

    @Test
    fun arcEndpointsMatchAndMidpointIsLifted() {
        val a = GlobeMath.toXyz(50.03, 8.57)    // FRA
        val b = GlobeMath.toXyz(40.64, -73.78)  // JFK
        val arc = GlobeMath.arc(a, b)
        val n = arc.size / 3
        assertEquals(a[0].toFloat(), arc[0], 1e-5f)
        assertEquals(b[2].toFloat(), arc[(n - 1) * 3 + 2], 1e-5f)
        val m = (n / 2) * 3
        val r = sqrt(arc[m] * arc[m] + arc[m + 1] * arc[m + 1] + arc[m + 2] * arc[m + 2])
        assertTrue("mid-route lift, got $r", r > 1.01f)
    }

    @Test
    fun liftedArcBehindHorizonIsHidden() {
        // Raised high enough to clear the limb, but still behind the horizon: hidden.
        val cam = GlobeCamera(0.0, 0.0)
        val p = pt(0.0, 95.0).map { it * 1.2f }.toFloatArray()
        assertTrue(cam.depth(p, 0) < 0f)
        assertFalse(cam.visible(p, 0))
    }

    @Test
    fun sceneMergesReturnFlightsIntoOneArc() {
        val fraJfk = FlightRoute(50.03, 8.57, 40.64, -73.78)
        val jfkFra = FlightRoute(40.64, -73.78, 50.03, 8.57)
        val scene = buildScene(listOf(fraJfk, jfkFra, fraJfk), null)
        assertEquals(1, scene.arcs.size)
        assertEquals(3, scene.arcs[0].flights)
        assertEquals(2, scene.airports.size)
        assertTrue(scene.airports.all { it.flights == 3 })
    }

    @Test
    fun bundledWorldOutlineParses() {
        val f = listOf("src/main/assets/world-110m.json", "app/src/main/assets/world-110m.json")
            .map(::File).first { it.exists() }
        val w = WorldOutline.parse(f.readText())
        assertTrue(w.land.size > 100)
        assertTrue(w.borders.size > 100)
        assertTrue(w.land.all { it.size % 3 == 0 && it.size >= 9 })
    }

    @Test
    fun hitTestPicksNearestVisibleAirportOnly() {
        // FRA (front, at centre) and a point on the far side.
        val scene = buildScene(listOf(FlightRoute(50.0, 8.0, -50.0, -172.0), FlightRoute(50.0, 8.0, 52.0, 13.0)), null)
        val w = 1000f; val h = 1000f
        val hitCentre = hitTest(scene, Offset(500f, 500f), w, h, 1f, 50f, 8f, 30f)
        assertEquals(50.0, hitCentre!!.xyz.let { com.luchowl.flybook.ui.globe.GlobeMath.toLatLon(doubleArrayOf(it[0].toDouble(), it[1].toDouble(), it[2].toDouble())).first }, 1e-3)
        // The far-side airport projects near the centre too (antipode) but must not be hit.
        val antipodeOnly = buildScene(listOf(FlightRoute(-50.0, -172.0, -51.0, -172.0)), null)
        assertNull(hitTest(antipodeOnly, Offset(500f, 500f), w, h, 1f, 50f, 8f, 30f))
        // Too far from any dot.
        assertNull(hitTest(scene, Offset(10f, 10f), w, h, 1f, 50f, 8f, 30f))
    }
}
