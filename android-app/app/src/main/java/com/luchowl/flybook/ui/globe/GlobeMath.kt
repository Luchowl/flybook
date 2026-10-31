package com.luchowl.flybook.ui.globe

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure math for the orthographic globe. Geometry is kept as unit vectors
 * (x toward lat 0/lon 0, y toward lon 90°E, z toward the north pole) packed
 * into flat FloatArrays of xyz triples, so a frame only needs dot products.
 */
internal object GlobeMath {

    fun toXyz(latDeg: Double, lonDeg: Double): DoubleArray {
        val lat = Math.toRadians(latDeg)
        val lon = Math.toRadians(lonDeg)
        return doubleArrayOf(cos(lat) * cos(lon), cos(lat) * sin(lon), sin(lat))
    }

    fun toLatLon(v: DoubleArray): Pair<Double, Double> {
        val len = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return Math.toDegrees(asin((v[2] / len).coerceIn(-1.0, 1.0))) to Math.toDegrees(atan2(v[1], v[0]))
    }

    /** Packs a flat [lon, lat, lon, lat, …] array into xyz triples. */
    fun packLonLat(lonLat: DoubleArray): FloatArray {
        val n = lonLat.size / 2
        val out = FloatArray(n * 3)
        for (i in 0 until n) {
            val v = toXyz(lonLat[i * 2 + 1], lonLat[i * 2])
            out[i * 3] = v[0].toFloat(); out[i * 3 + 1] = v[1].toFloat(); out[i * 3 + 2] = v[2].toFloat()
        }
        return out
    }

    /** Central angle between two unit vectors, in radians (atan2 form: precise near 0 and π). */
    fun angle(a: DoubleArray, b: DoubleArray): Double {
        val cx = a[1] * b[2] - a[2] * b[1]
        val cy = a[2] * b[0] - a[0] * b[2]
        val cz = a[0] * b[1] - a[1] * b[0]
        val dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        return atan2(sqrt(cx * cx + cy * cy + cz * cz), dot)
    }

    /** A unit vector perpendicular to [a]. */
    private fun perpendicular(a: DoubleArray): DoubleArray {
        // Cross with whichever axis is least aligned with a.
        val axis = if (abs(a[2]) < 0.9) doubleArrayOf(0.0, 0.0, 1.0) else doubleArrayOf(1.0, 0.0, 0.0)
        val x = a[1] * axis[2] - a[2] * axis[1]
        val y = a[2] * axis[0] - a[0] * axis[2]
        val z = a[0] * axis[1] - a[1] * axis[0]
        val len = sqrt(x * x + y * y + z * z)
        return doubleArrayOf(x / len, y / len, z / len)
    }

    /**
     * Great-circle arc from [a] to [b] as xyz triples, sampled every ~[stepDeg].
     * Each point is lifted off the surface by up to [maxLift] (as a fraction of
     * the radius, peaking mid-route and scaled by route length) so arcs read as 3D.
     */
    fun arc(a: DoubleArray, b: DoubleArray, stepDeg: Double = 2.0, maxLift: Double = 0.08): FloatArray {
        val omega = angle(a, b)
        val steps = max(1, ceil(Math.toDegrees(omega) / stepDeg).toInt())
        val lift = maxLift * (omega / PI)
        val out = FloatArray((steps + 1) * 3)
        val sinO = sin(omega)
        // Antipodal endpoints have no unique great circle (slerp divides by ~0):
        // rotate a by omega toward any perpendicular direction instead.
        val antipodal = sinO < 1e-6 && omega > PI / 2
        val u = if (antipodal) perpendicular(a) else null
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val r = 1.0 + lift * sin(PI * t)
            val p = when {
                u != null -> DoubleArray(3) { k -> cos(t * omega) * a[k] + sin(t * omega) * u[k] }
                sinO < 1e-6 -> DoubleArray(3) { k -> (1 - t) * a[k] + t * b[k] } // same/adjacent point
                else -> {
                    val wa = sin((1 - t) * omega) / sinO
                    val wb = sin(t * omega) / sinO
                    DoubleArray(3) { k -> wa * a[k] + wb * b[k] }
                }
            }
            out[i * 3] = (p[0] * r).toFloat()
            out[i * 3 + 1] = (p[1] * r).toFloat()
            out[i * 3 + 2] = (p[2] * r).toFloat()
        }
        return out
    }

    /** Meridians and parallels every [everyDeg], sampled every 2°. */
    fun graticule(everyDeg: Int = 30): List<FloatArray> {
        val lines = mutableListOf<FloatArray>()
        for (lon in -180 until 180 step everyDeg) {
            lines += packLonLat(DoubleArray(91 * 2) { i -> if (i % 2 == 0) lon.toDouble() else -90.0 + (i / 2) * 2.0 })
        }
        for (lat in -90 + everyDeg until 90 step everyDeg) {
            lines += packLonLat(DoubleArray(181 * 2) { i -> if (i % 2 == 0) -180.0 + (i / 2) * 2.0 else lat.toDouble() })
        }
        return lines
    }

    /** Mean direction of the given points, or null when there are none. */
    fun centroid(points: List<DoubleArray>): Pair<Double, Double>? {
        if (points.isEmpty()) return null
        var x = 0.0; var y = 0.0; var z = 0.0
        points.forEach { x += it[0]; y += it[1]; z += it[2] }
        if (x * x + y * y + z * z < 1e-12) return toLatLon(points.first())
        return toLatLon(doubleArrayOf(x, y, z))
    }
}

/**
 * Orthographic camera looking at (centerLat, centerLon) from outside the globe.
 * [sx]/[sy] are screen offsets in units of the globe radius (sy points north/up),
 * [depth] > 0 means the point faces the viewer.
 */
internal class GlobeCamera(centerLat: Double, centerLon: Double) {
    private val ex: Float; private val ey: Float
    private val nx: Float; private val ny: Float; private val nz: Float
    private val cx: Float; private val cy: Float; private val cz: Float

    init {
        val lat = Math.toRadians(centerLat)
        val lon = Math.toRadians(centerLon)
        ex = -sin(lon).toFloat(); ey = cos(lon).toFloat()
        nx = (-sin(lat) * cos(lon)).toFloat(); ny = (-sin(lat) * sin(lon)).toFloat(); nz = cos(lat).toFloat()
        cx = (cos(lat) * cos(lon)).toFloat(); cy = (cos(lat) * sin(lon)).toFloat(); cz = sin(lat).toFloat()
    }

    fun sx(p: FloatArray, i: Int): Float = p[i] * ex + p[i + 1] * ey
    fun sy(p: FloatArray, i: Int): Float = p[i] * nx + p[i + 1] * ny + p[i + 2] * nz
    fun depth(p: FloatArray, i: Int): Float = p[i] * cx + p[i + 1] * cy + p[i + 2] * cz

    /**
     * Whether a point is on the near hemisphere. Lifted arc points behind the
     * horizon are hidden too, even when they would clear the limb: drawing them
     * makes long-haul arcs poke out of the globe as hairpins.
     */
    fun visible(p: FloatArray, i: Int): Boolean = depth(p, i) >= 0f
}
