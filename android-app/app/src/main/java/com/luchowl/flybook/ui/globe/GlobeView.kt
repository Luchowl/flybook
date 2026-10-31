package com.luchowl.flybook.ui.globe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luchowl.flybook.data.Airport
import com.luchowl.flybook.data.ReferenceData
import com.luchowl.flybook.util.FlightRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

private const val MIN_ZOOM = 0.8f
private const val MAX_ZOOM = 4f
private const val LABEL_ZOOM = 1.8f

private val OceanLight = Color(0xFF1B3358)
private val OceanDark = Color(0xFF0C1930)
private val Land = Color(0xFF2B4A70)
private val Border = Color(0xFF4A6B94)
private val Atmosphere = Color(0xFF60A5FA)

internal class GlobeAirport(val airport: Airport?, val code: String, val xyz: FloatArray, val flights: Int)

/**
 * One route arc. [mid] is the unit vector at the arc's midpoint and every point
 * lies within the half-angle whose sine is [sinHalf] of it, so the whole arc can
 * be culled with one dot product when it is behind the globe.
 */
internal class GlobeArc(val points: FloatArray, val mid: FloatArray, val sinHalf: Float, val flights: Int)

internal class GlobeScene(
    val arcs: List<GlobeArc>,
    val airports: List<GlobeAirport>,
    /** Busiest airports; only these get code labels so dense logbooks stay legible. */
    val labelled: Set<GlobeAirport>,
    val center: Pair<Double, Double>?,
) {
    companion object {
        val EMPTY = GlobeScene(emptyList(), emptyList(), emptySet(), null)
    }
}

private const val MAX_LABELS = 40

private typealias LatLon = Pair<Double, Double>

internal fun buildScene(routes: List<FlightRoute>, ref: ReferenceData?): GlobeScene {
    val airportByPos = HashMap<LatLon, Airport>()
    ref?.airports?.values?.forEach { airportByPos[it.lat to it.lon] = it }

    // A→B and B→A share one arc.
    val routeCounts = LinkedHashMap<Pair<LatLon, LatLon>, Int>()
    val airportCounts = LinkedHashMap<LatLon, Int>()
    for (r in routes) {
        val a = r.depLat to r.depLon
        val b = r.arrLat to r.arrLon
        val key = if (compareValuesBy(a, b, { it.first }, { it.second }) <= 0) a to b else b to a
        routeCounts[key] = (routeCounts[key] ?: 0) + 1
        airportCounts[a] = (airportCounts[a] ?: 0) + 1
        if (b != a) airportCounts[b] = (airportCounts[b] ?: 0) + 1
    }

    val arcs = routeCounts.map { (key, count) ->
        val (a, b) = key
        val va = GlobeMath.toXyz(a.first, a.second)
        val vb = GlobeMath.toXyz(b.first, b.second)
        val points = GlobeMath.arc(va, vb)
        val m = (points.size / 3 / 2) * 3
        val len = sqrt(points[m] * points[m] + points[m + 1] * points[m + 1] + points[m + 2] * points[m + 2])
        val mid = floatArrayOf(points[m] / len, points[m + 1] / len, points[m + 2] / len)
        val half = GlobeMath.angle(va, vb) / 2 + Math.toRadians(2.0) // + one sample step of slack
        GlobeArc(points, mid, kotlin.math.sin(half.coerceAtMost(Math.PI / 2)).toFloat(), count)
    }
    val airports = airportCounts.map { (pos, count) ->
        val ap = airportByPos[pos]
        val v = GlobeMath.toXyz(pos.first, pos.second)
        GlobeAirport(
            airport = ap,
            code = ap?.let { it.iata.ifEmpty { it.icao } } ?: "?",
            xyz = floatArrayOf(v[0].toFloat(), v[1].toFloat(), v[2].toFloat()),
            flights = count,
        )
    }
    val center = GlobeMath.centroid(airportCounts.keys.map { GlobeMath.toXyz(it.first, it.second) })
    val labelled = airports.sortedByDescending { it.flights }.take(MAX_LABELS).toSet()
    return GlobeScene(arcs, airports, labelled, center)
}

/** Nearest airport on the visible side within [maxDistPx] of [pos], or null. */
internal fun hitTest(
    scene: GlobeScene,
    pos: Offset,
    width: Float,
    height: Float,
    zoom: Float,
    centerLat: Float,
    centerLon: Float,
    maxDistPx: Float,
): GlobeAirport? {
    val r = globeRadius(width, height, zoom)
    val cam = GlobeCamera(centerLat.toDouble(), centerLon.toDouble())
    val cx = width / 2f
    val cy = height / 2f
    var best: GlobeAirport? = null
    var bestDist = maxDistPx
    for (a in scene.airports) {
        if (cam.depth(a.xyz, 0) <= 0f) continue
        val d = hypot(cx + cam.sx(a.xyz, 0) * r - pos.x, cy - cam.sy(a.xyz, 0) * r - pos.y)
        if (d < bestDist) { best = a; bestDist = d }
    }
    return best
}

private fun globeRadius(width: Float, height: Float, zoom: Float): Float =
    min(width, height) / 2f * 0.86f * zoom

private fun wrapLon(lon: Float): Float {
    var l = lon
    while (l > 180f) l -= 360f
    while (l < -180f) l += 360f
    return l
}

/**
 * Interactive 3D globe (orthographic projection) drawn with Compose Canvas.
 * Drag to spin (with inertia), pinch to zoom, double-tap to zoom in/out,
 * tap an airport for details.
 */
@Composable
internal fun GlobeView(
    routes: List<FlightRoute>,
    ref: ReferenceData?,
    lineColor: Int,
    markerColor: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val world by produceState<WorldOutline?>(null) { value = WorldOutline.get(context) }
    val graticule = remember { GlobeMath.graticule() }
    // Built off the main thread: a 10k-flight logbook means ~10k arcs / ~460k points.
    val scene by produceState(GlobeScene.EMPTY, routes, ref) {
        value = withContext(Dispatchers.Default) { buildScene(routes, ref) }
    }

    var centerLat by rememberSaveable { mutableFloatStateOf(25f) }
    var centerLon by rememberSaveable { mutableFloatStateOf(10f) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var centered by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<GlobeAirport?>(null) }

    LaunchedEffect(scene) {
        if (!centered) {
            scene.center?.let { (lat, lon) ->
                centerLat = lat.toFloat().coerceIn(-35f, 35f)
                centerLon = lon.toFloat()
                centered = true
            }
        }
        selected = null
    }

    val scope = rememberCoroutineScope()
    var fling by remember { mutableStateOf<Job?>(null) }

    fun rotateBy(dx: Float, dy: Float, radius: Float) {
        val degPerPx = Math.toDegrees(1.0 / radius).toFloat()
        centerLon = wrapLon(centerLon - dx * degPerPx)
        centerLat = (centerLat + dy * degPerPx).coerceIn(-85f, 85f)
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    val labels = remember(scene, textMeasurer) {
        scene.labelled.associateWith { textMeasurer.measure(it.code, labelStyle) }
    }
    val selectedLabel = remember(selected, textMeasurer) {
        selected?.let { textMeasurer.measure(it.code, labelStyle) }
    }
    val scratch = remember { LineScratch() }
    val route = Color(lineColor)
    val marker = Color(markerColor)

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                // One handler for drag, pinch, tap and double-tap: a touch only counts as a
                // tap if it never moved past touch slop, used one finger and didn't just
                // stop a fling (so drags can't also select an airport).
                .pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    val doubleTapMs = viewConfiguration.doubleTapTimeoutMillis
                    val doubleTapDist = 48.dp.toPx()
                    var lastTapTime = -1_000_000L
                    var lastTapPos = Offset.Zero
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val stoppedFling = fling?.isActive == true
                        fling?.cancel()
                        val tracker = VelocityTracker()
                        var moving = false
                        var travelled = Offset.Zero
                        var multiTouch = false
                        var upTime = down.uptimeMillis
                        do {
                            val event = awaitPointerEvent()
                            upTime = event.changes.first().uptimeMillis
                            if (event.changes.size > 1) multiTouch = true
                            val zoomChange = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (!moving) {
                                travelled += pan
                                moving = travelled.getDistance() > slop || zoomChange != 1f
                            }
                            if (moving) {
                                zoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                rotateBy(pan.x, pan.y, globeRadius(size.width.toFloat(), size.height.toFloat(), zoom))
                                event.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
                            }
                            event.changes.firstOrNull()?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                        } while (event.changes.any { it.pressed })

                        if (!moving && !multiTouch && !stoppedFling) {
                            // Like Android: measured from the previous tap's release to this press.
                            val isDouble = down.uptimeMillis - lastTapTime < doubleTapMs &&
                                (down.position - lastTapPos).getDistance() < doubleTapDist
                            if (isDouble) {
                                zoom = if (zoom < 1.8f) 2.5f else 1f
                                lastTapTime = -1_000_000L
                            } else {
                                selected = hitTest(
                                    scene, down.position, size.width.toFloat(), size.height.toFloat(),
                                    zoom, centerLat, centerLon, 24.dp.toPx(),
                                )
                                lastTapTime = upTime
                                lastTapPos = down.position
                            }
                        }

                        if (moving && !multiTouch) {
                            val v = tracker.calculateVelocity()
                            val r = globeRadius(size.width.toFloat(), size.height.toFloat(), zoom)
                            fling = scope.launch {
                                var vx = v.x
                                var vy = v.y
                                var last = withFrameNanos { it }
                                while (isActive && hypot(vx, vy) > 30f) {
                                    val now = withFrameNanos { it }
                                    val dt = (now - last) / 1_000_000_000f
                                    last = now
                                    rotateBy(vx * dt, vy * dt, r)
                                    val decay = exp(-3.5f * dt)
                                    vx *= decay
                                    vy *= decay
                                }
                            }
                        }
                    }
                },
        ) {
            val r = globeRadius(size.width, size.height, zoom)
            val c = center
            val cam = GlobeCamera(centerLat.toDouble(), centerLon.toDouble())

            drawBackdrop(c, r)
            drawFrontLines(graticule, cam, c, r, Color.White.copy(alpha = 0.06f), 0.75.dp.toPx(), scratch)
            world?.let { w ->
                drawPath(landPath(w.land, cam, c, r), Land)
                drawFrontLines(w.borders, cam, c, r, Border.copy(alpha = 0.7f), 0.6.dp.toPx(), scratch)
            }
            drawShading(c, r)

            drawArcs(scene.arcs, cam, c, r, route, scratch)

            val showLabels = zoom >= LABEL_ZOOM || scene.airports.size <= 12
            // Shrink dots on dense logbooks so they don't merge into blobs.
            val dotScale = when {
                scene.airports.size > 150 -> 0.55f
                scene.airports.size > 50 -> 0.75f
                else -> 1f
            }
            scene.airports.forEach { a ->
                if (cam.depth(a.xyz, 0) <= 0.02f) return@forEach
                val p = Offset(c.x + cam.sx(a.xyz, 0) * r, c.y - cam.sy(a.xyz, 0) * r)
                val dot = ((2.5f + min(sqrt(a.flights.toFloat()), 4f) * 0.6f) * dotScale).dp.toPx()
                drawCircle(Color.White, dot + 1.dp.toPx(), p)
                drawCircle(marker, dot, p)
                if (a === selected) {
                    drawCircle(Color.White, dot + 5.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
                }
                val label = if (a === selected) selectedLabel else if (showLabels) labels[a] else null
                label?.let { drawText(it, topLeft = Offset(p.x + dot + 3.dp.toPx(), p.y - it.size.height / 2f)) }
            }
        }

        selected?.let { a ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 6.dp,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        listOfNotNull(a.code, a.airport?.name?.takeIf { it.isNotEmpty() }).joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val place = listOfNotNull(
                        a.airport?.city?.takeIf { it.isNotEmpty() },
                        a.airport?.country?.takeIf { it.isNotEmpty() },
                    ).joinToString(", ")
                    Text(
                        listOf(place, "${a.flights} flight${if (a.flights == 1) "" else "s"}")
                            .filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawBackdrop(c: Offset, r: Float) {
    val glowR = r * 1.12f
    drawCircle(
        Brush.radialGradient(
            0f to Atmosphere.copy(alpha = 0.35f),
            (r / glowR) to Atmosphere.copy(alpha = 0.25f),
            1f to Color.Transparent,
            center = c,
            radius = glowR,
        ),
        glowR,
        c,
    )
    drawCircle(
        Brush.radialGradient(listOf(OceanLight, OceanDark), center = c + Offset(-r * 0.35f, -r * 0.4f), radius = r * 1.6f),
        r,
        c,
    )
}

private fun DrawScope.drawShading(c: Offset, r: Float) {
    drawCircle(
        Brush.radialGradient(
            0f to Color.Transparent,
            0.55f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.45f),
            center = c + Offset(-r * 0.3f, -r * 0.35f),
            radius = r * 1.5f,
        ),
        r,
        c,
    )
    drawCircle(Atmosphere.copy(alpha = 0.45f), r, c, style = Stroke(1.dp.toPx()))
}

/**
 * Filled land: points on the far side are pushed out onto the limb, so a
 * polygon that crosses the horizon is closed along the globe's edge.
 * Rings with no visible point are skipped (otherwise a ring around the
 * hidden pole would trace the whole limb and flood the disc).
 */
private fun landPath(rings: List<FloatArray>, cam: GlobeCamera, c: Offset, r: Float): Path {
    val path = Path()
    for (ring in rings) {
        var anyVisible = false
        var i = 0
        while (i < ring.size) {
            if (cam.depth(ring, i) >= 0f) { anyVisible = true; break }
            i += 3
        }
        if (!anyVisible) continue
        var first = true
        i = 0
        while (i < ring.size) {
            var x = cam.sx(ring, i)
            var y = cam.sy(ring, i)
            if (cam.depth(ring, i) < 0f) {
                val len = sqrt(x * x + y * y)
                if (len < 1e-6f) { i += 3; continue }
                x /= len; y /= len
            }
            val px = c.x + x * r
            val py = c.y - y * r
            if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
            i += 3
        }
        path.close()
    }
    return path
}

/** Growable buffer of line segments (x0, y0, x1, y1), reused across frames. */
internal class SegmentBuffer {
    var data = FloatArray(4096)
    var size = 0

    fun reset() { size = 0 }

    fun add(x0: Float, y0: Float, x1: Float, y1: Float) {
        if (size + 4 > data.size) data = data.copyOf(data.size * 2)
        data[size] = x0; data[size + 1] = y0; data[size + 2] = x1; data[size + 3] = y1
        size += 4
    }
}

/**
 * Appends the visible runs of [line] as segments, skipping anything behind the
 * globe. Only every [stride]-th sample is projected (the last one always is),
 * and points closer than [minStepPx] to the previous one are merged (except
 * run ends): a small globe doesn't need every 2° sample.
 */
private fun appendFrontLine(
    out: SegmentBuffer,
    line: FloatArray,
    cam: GlobeCamera,
    c: Offset,
    r: Float,
    minStepPx: Float,
    stride: Int = 1,
) {
    val minD2 = minStepPx * minStepPx
    val lastIdx = line.size - 3
    if (lastIdx < 0) return
    var penDown = false
    var lastX = 0f
    var lastY = 0f
    var i = 0
    var vis = cam.visible(line, 0)
    while (true) {
        val next = if (i >= lastIdx) -1 else min(i + 3 * stride, lastIdx)
        val nextVis = next >= 0 && cam.visible(line, next)
        if (vis) {
            val px = c.x + cam.sx(line, i) * r
            val py = c.y - cam.sy(line, i) * r
            if (!penDown) {
                penDown = true
                lastX = px; lastY = py
            } else {
                val dx = px - lastX
                val dy = py - lastY
                if (!nextVis || dx * dx + dy * dy >= minD2) {
                    out.add(lastX, lastY, px, py)
                    lastX = px; lastY = py
                }
            }
        } else {
            penDown = false
        }
        if (next < 0) break
        i = next
        vis = nextVis
    }
}

/**
 * Draws segments with Canvas.drawLines: hardware-accelerated lines need no path
 * tessellation, which dominates frame time when thousands of arcs move every frame.
 */
private fun DrawScope.drawSegments(buf: SegmentBuffer, paint: android.graphics.Paint, color: Color, width: Float) {
    if (buf.size == 0) return
    paint.color = color.toArgb()
    paint.strokeWidth = width
    drawContext.canvas.nativeCanvas.drawLines(buf.data, 0, buf.size, paint)
}

/** Reusable buffers/paint so drawing a frame allocates nothing per route. */
internal class LineScratch {
    val buckets = Array(WIDTH_BUCKETS) { SegmentBuffer() }
    val lines = SegmentBuffer()
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        // BUTT, not ROUND: with translucent lines, round caps overlap at every joint
        // and blend twice, which makes routes look dotted.
        strokeCap = android.graphics.Paint.Cap.BUTT
    }
}

/** Strokes polylines, skipping the parts behind the globe. */
private fun DrawScope.drawFrontLines(
    lines: List<FloatArray>,
    cam: GlobeCamera,
    c: Offset,
    r: Float,
    color: Color,
    width: Float,
    scratch: LineScratch,
) {
    val buf = scratch.lines
    buf.reset()
    val minStep = 1.5.dp.toPx()
    for (line in lines) appendFrontLine(buf, line, cam, c, r, minStep)
    drawSegments(buf, scratch.paint, color, width)
}

private const val WIDTH_BUCKETS = 6

/**
 * Route arcs, batched by stroke width (instead of one draw call per route) and
 * culled when entirely behind the globe. Lines get thinner and more transparent
 * as the number of routes grows so dense logbooks show structure instead of a
 * solid wall.
 */
private fun DrawScope.drawArcs(arcs: List<GlobeArc>, cam: GlobeCamera, c: Offset, r: Float, color: Color, scratch: LineScratch) {
    if (arcs.isEmpty()) return
    scratch.buckets.forEach { it.reset() }
    // Level of detail: dense logbooks draw thin faint lines, so coarser chords
    // look the same while keeping the segment count (GPU cost) bounded.
    val minStep = (1.5f * sqrt(arcs.size / 250f).coerceIn(1f, 6f)).dp.toPx()
    // Arc samples are ~2° apart, i.e. r·0.035 px on screen: skip the ones LOD would merge anyway.
    val stride = (minStep / (r * 0.0349f)).toInt().coerceIn(1, 8)
    for (arc in arcs) {
        if (cam.depth(arc.mid, 0) < -arc.sinHalf) continue // whole arc on the far side
        appendFrontLine(scratch.buckets[min(arc.flights, WIDTH_BUCKETS) - 1], arc.points, cam, c, r, minStep, stride)
    }
    val alpha = (0.9f * sqrt(60f / arcs.size)).coerceIn(0.2f, 0.9f)
    val base = if (arcs.size > 300) 0.9f else 1.4f
    val step = if (arcs.size > 300) 0.25f else 0.35f
    scratch.buckets.forEachIndexed { i, buf ->
        drawSegments(buf, scratch.paint, color.copy(alpha = alpha), (base + (i + 1) * step).dp.toPx())
    }
}
