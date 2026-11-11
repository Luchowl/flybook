package com.luchowl.flybook.ui.globe

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luchowl.flybook.data.Airport
import com.luchowl.flybook.data.Flight
import com.luchowl.flybook.data.ReferenceData
import com.luchowl.flybook.util.FlightRoute
import com.luchowl.flybook.util.Format
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
private const val MAX_ZOOM = 24f
private const val LABEL_ZOOM = 1.8f
/** Double-tapping at or beyond this zoom goes back to the whole globe. */
private const val DOUBLE_TAP_RESET_ZOOM = 12f
/** From here on every airport on screen is labelled, not just the busiest ones. */
private const val ALL_LABELS_ZOOM = 5f

private val OceanLight = Color(0xFF1B3358)
private val OceanDark = Color(0xFF0C1930)
private val Land = Color(0xFF2B4A70)
private val Border = Color(0xFF4A6B94)
private val Atmosphere = Color(0xFF60A5FA)

internal class GlobeAirport(
    val airport: Airport?,
    val code: String,
    val xyz: FloatArray,
    val flights: Int,
    /** The flights behind [flights], in logbook order (may be shorter if routes carry no flight). */
    val flightList: List<Flight>,
)

/**
 * One route arc. [mid] is the unit vector at the arc's midpoint and every point
 * lies within the half-angle whose sine is [sinHalf] of it, so the whole arc can
 * be culled with one dot product when it is behind the globe.
 */
internal class GlobeArc(
    /** Unit vectors along the great circle. */
    val points: FloatArray,
    /** Height above the surface of each point, as a fraction of the radius. */
    val lift: FloatArray,
    val mid: FloatArray,
    val sinHalf: Float,
    val flights: Int,
)

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
    val airportFlights = HashMap<LatLon, MutableList<Flight>>()
    for (r in routes) {
        val a = r.depLat to r.depLon
        val b = r.arrLat to r.arrLon
        val key = if (compareValuesBy(a, b, { it.first }, { it.second }) <= 0) a to b else b to a
        routeCounts[key] = (routeCounts[key] ?: 0) + 1
        airportCounts[a] = (airportCounts[a] ?: 0) + 1
        if (b != a) airportCounts[b] = (airportCounts[b] ?: 0) + 1
        r.flight?.let { f ->
            airportFlights.getOrPut(a) { mutableListOf() } += f
            if (b != a) airportFlights.getOrPut(b) { mutableListOf() } += f
        }
    }

    val arcs = routeCounts.map { (key, count) ->
        val (a, b) = key
        val va = GlobeMath.toXyz(a.first, a.second)
        val vb = GlobeMath.toXyz(b.first, b.second)
        val points = GlobeMath.arc(va, vb)
        // Split the lifted points into direction + height, so the height can be
        // tapered per frame (see liftScale).
        val lift = FloatArray(points.size / 3)
        for (i in lift.indices) {
            val j = i * 3
            val len = sqrt(points[j] * points[j] + points[j + 1] * points[j + 1] + points[j + 2] * points[j + 2])
            points[j] /= len; points[j + 1] /= len; points[j + 2] /= len
            lift[i] = len - 1f
        }
        val m = (points.size / 3 / 2) * 3
        val mid = floatArrayOf(points[m], points[m + 1], points[m + 2])
        val half = GlobeMath.angle(va, vb) / 2 + Math.toRadians(2.0) // + one sample step of slack
        GlobeArc(points, lift, mid, kotlin.math.sin(half.coerceAtMost(Math.PI / 2)).toFloat(), count)
    }
    val airports = airportCounts.map { (pos, count) ->
        val ap = airportByPos[pos]
        val v = GlobeMath.toXyz(pos.first, pos.second)
        GlobeAirport(
            airport = ap,
            code = ap?.let { it.iata.ifEmpty { it.icao } } ?: "?",
            xyz = floatArrayOf(v[0].toFloat(), v[1].toFloat(), v[2].toFloat()),
            flights = count,
            flightList = airportFlights[pos] ?: emptyList(),
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
    onFlightClick: (Flight) -> Unit = {},
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
    var expanded by remember { mutableStateOf(false) }

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
    var zoomAnim by remember { mutableStateOf<Job?>(null) }

    fun rotateBy(dx: Float, dy: Float, radius: Float) {
        val degPerPx = Math.toDegrees(1.0 / radius).toFloat()
        centerLon = wrapLon(centerLon - dx * degPerPx)
        centerLat = (centerLat + dy * degPerPx).coerceIn(-85f, 85f)
    }

    /**
     * Sets the zoom while keeping the spot under [anchor] (canvas px) in place,
     * so pinching and double-tapping zoom into what the fingers are on.
     */
    fun zoomTo(target: Float, anchor: Offset, width: Float, height: Float) {
        val old = zoom
        val new = target.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (new == old) return
        zoom = new
        val off = anchor - Offset(width / 2f, height / 2f)
        val oldR = globeRadius(width, height, old)
        if (off.getDistance() < oldR) {
            // Zooming pushes that spot away from the centre by off·(ratio − 1): drag it back.
            val k = new / old - 1f
            rotateBy(-off.x * k, -off.y * k, globeRadius(width, height, new))
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    // Measured on first use: far zooms label every airport on screen, not just the busiest.
    val labels = remember(scene, textMeasurer) { HashMap<GlobeAirport, TextLayoutResult>() }
    val selectedLabel = remember(selected, textMeasurer) {
        selected?.let { textMeasurer.measure(it.code, labelStyle) }
    }
    val scratch = remember { LineScratch() }
    val landPath = remember { Path() }
    val clipper = remember { LandClipper() }
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
                        zoomAnim?.cancel()
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
                                if (zoomChange != 1f) {
                                    zoomTo(zoom * zoomChange, event.calculateCentroid(), size.width.toFloat(), size.height.toFloat())
                                }
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
                                // Each double-tap zooms in a step on the tapped spot; once close, back out.
                                val from = zoom
                                val to = if (from < DOUBLE_TAP_RESET_ZOOM) (from * 2.5f).coerceAtMost(MAX_ZOOM) else 1f
                                val anchor = if (to > from) down.position else Offset(size.width / 2f, size.height / 2f)
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                zoomAnim = scope.launch {
                                    animate(from, to, animationSpec = tween(300)) { v, _ -> zoomTo(v, anchor, w, h) }
                                }
                                lastTapTime = -1_000_000L
                            } else {
                                val hit = hitTest(
                                    scene, down.position, size.width.toFloat(), size.height.toFloat(),
                                    zoom, centerLat, centerLon, 24.dp.toPx(),
                                )
                                if (hit !== selected) expanded = false
                                selected = hit
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
                drawPath(buildLandPath(landPath, clipper, w, cam, -centerLat, wrapLon(centerLon + 180f), c, r), Land)
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
            val allLabels = zoom >= ALL_LABELS_ZOOM
            val margin = 24.dp.toPx()
            scene.airports.forEach { a ->
                if (cam.depth(a.xyz, 0) <= 0.02f) return@forEach
                val p = Offset(c.x + cam.sx(a.xyz, 0) * r, c.y - cam.sy(a.xyz, 0) * r)
                if (p.x < -margin || p.y < -margin || p.x > size.width + margin || p.y > size.height + margin) return@forEach
                val dot = ((2.5f + min(sqrt(a.flights.toFloat()), 4f) * 0.6f) * dotScale).dp.toPx()
                drawCircle(Color.White, dot + 1.dp.toPx(), p)
                drawCircle(marker, dot, p)
                if (a === selected) {
                    drawCircle(Color.White, dot + 5.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
                }
                val label = when {
                    a === selected -> selectedLabel
                    showLabels && (allLabels || a in scene.labelled) ->
                        labels.getOrPut(a) { textMeasurer.measure(a.code, labelStyle) }
                    else -> null
                }
                label?.let { drawText(it, topLeft = Offset(p.x + dot + 3.dp.toPx(), p.y - it.size.height / 2f)) }
            }
        }

        selected?.let { a ->
            AirportCard(
                airport = a,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                onFlightClick = onFlightClick,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}

/** Card for the tapped airport; tap it to list the flights that used the airport. */
@Composable
private fun AirportCard(
    airport: GlobeAirport,
    expanded: Boolean,
    onToggle: () -> Unit,
    onFlightClick: (Flight) -> Unit,
    modifier: Modifier = Modifier,
) {
    val a = airport
    val canExpand = a.flightList.isNotEmpty()
    Surface(
        onClick = onToggle,
        enabled = canExpand,
        modifier = modifier.widthIn(max = 420.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.animateContentSize()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f, fill = expanded)) {
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
                if (canExpand) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (expanded) "Hide flights" else "Show flights",
                        modifier = Modifier.padding(start = 12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded && canExpand) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                    items(a.flightList, key = { it.id }) { f -> AirportFlightRow(f) { onFlightClick(f) } }
                }
            }
        }
    }
}

@Composable
private fun AirportFlightRow(f: Flight, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${Format.code(f.departureIata, f.departureIcao)} → ${Format.code(f.arrivalIata, f.arrivalIcao)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                listOf(Format.date(f.flightDate), f.flightNumber, f.airlineName.ifEmpty { f.airlineIata })
                    .filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val trip = listOf(Format.duration(f.durationMinutes), Format.distance(f.distance)).filter { it != "—" }
        if (trip.isNotEmpty()) {
            Text(
                trip.joinToString(" · "),
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
 * Filled land, clipped to the visible hemisphere by [LandClipper]. Uses an even-odd
 * fill and adds the whole disc when the point opposite the camera ([antiLat],
 * [antiLon]) lies on a drawn landmass, which would otherwise be filled inside out.
 */
private fun buildLandPath(
    path: Path,
    clipper: LandClipper,
    world: WorldOutline,
    cam: GlobeCamera,
    antiLat: Float,
    antiLon: Float,
    c: Offset,
    r: Float,
): Path {
    path.rewind()
    path.fillType = PathFillType.EvenOdd
    // Keep limb arcs within ~half a pixel of the true circle.
    val maxStep = (2f / sqrt(r)).coerceIn(0.01f, 0.15f)
    // Rings are cut at the date line; stay off that seam so the test below is unambiguous.
    val aLon = antiLon.coerceIn(-179.999f, 179.999f)
    var invert = false
    for (k in world.land.indices) {
        if (!clipper.clip(world.land[k], cam, maxStep)) continue
        if (LandClipper.contains(world.landLonLat[k], aLon, antiLat)) invert = !invert
        val pts = clipper.out
        var i = 0
        while (i < pts.size) {
            val px = c.x + pts.data[i] * r
            val py = c.y - pts.data[i + 1] * r
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            i += 2
        }
        path.close()
    }
    if (invert) path.addOval(Rect(c, r))
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
    lift: FloatArray? = null,
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
            val rr = if (lift == null) r else r * (1f + lift[i / 3] * liftScale(cam.depth(line, i)))
            val px = c.x + cam.sx(line, i) * rr
            val py = c.y - cam.sy(line, i) * rr
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
 * How much of an arc's lift to show at a given depth (1 = facing the viewer,
 * 0 = on the horizon). Easing the lift out towards the horizon makes arcs sink
 * into the rim instead of stopping in mid-air outside the globe.
 */
internal fun liftScale(depth: Float): Float {
    val t = (depth / 0.6f).coerceIn(0f, 1f)
    return t * (2f - t)
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
        appendFrontLine(scratch.buckets[min(arc.flights, WIDTH_BUCKETS) - 1], arc.points, cam, c, r, minStep, stride, arc.lift)
    }
    val alpha = (0.9f * sqrt(60f / arcs.size)).coerceIn(0.2f, 0.9f)
    val base = if (arcs.size > 300) 0.9f else 1.4f
    val step = if (arcs.size > 300) 0.25f else 0.35f
    scratch.buckets.forEachIndexed { i, buf ->
        drawSegments(buf, scratch.paint, color.copy(alpha = alpha), (base + (i + 1) * step).dp.toPx())
    }
}
