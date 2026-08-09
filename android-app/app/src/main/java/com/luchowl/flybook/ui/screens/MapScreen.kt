package com.luchowl.flybook.ui.screens

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.preference.PreferenceManager
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luchowl.flybook.R
import com.luchowl.flybook.data.Airport
import com.luchowl.flybook.data.ReferenceData
import com.luchowl.flybook.ui.FlybookViewModel
import com.luchowl.flybook.ui.ScreenTopBar
import com.luchowl.flybook.util.FlightRoute
import com.luchowl.flybook.util.Stats
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.OverlayWithIW
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.infowindow.InfoWindow

private data class TileStyle(val id: String, val name: String, val source: org.osmdroid.tileprovider.tilesource.ITileSource)

private val CARTO_LIGHT = XYTileSource(
    "Carto Light", 0, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/light_all/",
        "https://b.basemaps.cartocdn.com/light_all/",
        "https://c.basemaps.cartocdn.com/light_all/",
        "https://d.basemaps.cartocdn.com/light_all/",
    ),
)

private val CARTO_DARK = XYTileSource(
    "Carto Dark", 0, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/",
    ),
)

private val OPEN_TOPO = XYTileSource(
    "OpenTopoMap", 0, 17, 256, ".png",
    arrayOf(
        "https://a.tile.opentopomap.org/",
        "https://b.tile.opentopomap.org/",
        "https://c.tile.opentopomap.org/",
    ),
)

private val tileStyles = listOf(
    TileStyle("standard", "Standard", TileSourceFactory.MAPNIK),
    TileStyle("satellite", "Satellite", TileSourceFactory.USGS_SAT),
    TileStyle("terrain", "Terrain", OPEN_TOPO),
    TileStyle("minimal", "Minimal", CARTO_LIGHT),
    TileStyle("dark", "Dark", CARTO_DARK),
)

private val lineColors = listOf(
    0xFF60A5FA.toInt(), // blue
    0xFF22D3EE.toInt(), // cyan
    0xFFF59E0B.toInt(), // amber
    0xFFF472B6.toInt(), // pink
    0xFF34D399.toInt(), // green
    0xFFA78BFA.toInt(), // violet
)

private object MapPrefs {
    private const val PREFS = "map_prefs"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun tileId(c: Context): String = prefs(c).getString("tile", "standard") ?: "standard"
    fun setTileId(c: Context, id: String) { prefs(c).edit().putString("tile", id).apply() }
    fun lineColor(c: Context): Int = prefs(c).getInt("line_color", 0xFF60A5FA.toInt())
    fun setLineColor(c: Context, color: Int) { prefs(c).edit().putInt("line_color", color).apply() }
    fun markerColor(c: Context): Int = prefs(c).getInt("marker_color", 0xFF22D3EE.toInt())
    fun setMarkerColor(c: Context, color: Int) { prefs(c).edit().putInt("marker_color", color).apply() }
}

@Composable
fun MapScreen(vm: FlybookViewModel = viewModel()) {
    val flights by vm.flights.collectAsStateWithLifecycle()
    val ref by vm.refData.collectAsStateWithLifecycle()
    val routes = remember(flights, ref) { ref?.let { Stats.mapRoutes(flights, it) } ?: emptyList() }
    val loading = ref == null

    val context = LocalContext.current
    var tileId by rememberSaveable { mutableStateOf(MapPrefs.tileId(context)) }
    var lineColor by rememberSaveable { mutableIntStateOf(MapPrefs.lineColor(context)) }
    var markerColor by rememberSaveable { mutableIntStateOf(MapPrefs.markerColor(context)) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showSettings) { showSettings = false }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = "Flight Map",
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = "Map settings")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            OsmdroidMap(routes, ref, tileId, lineColor, markerColor, Modifier.fillMaxSize())

            if (showSettings) {
                MapSettingsPanel(
                    tileId = tileId,
                    lineColor = lineColor,
                    markerColor = markerColor,
                    onTile = { tileId = it; MapPrefs.setTileId(context, it) },
                    onColor = { lineColor = it; MapPrefs.setLineColor(context, it) },
                    onMarkerColor = { markerColor = it; MapPrefs.setMarkerColor(context, it) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 8.dp)
                        .width(210.dp),
                )
            }

            when {
                loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                routes.isEmpty() -> {
                    Text(
                        "No flight routes yet.\nAdd flights with airport codes to see your routes.",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MapSettingsPanel(
    tileId: String,
    lineColor: Int,
    markerColor: Int,
    onTile: (String) -> Unit,
    onColor: (Int) -> Unit,
    onMarkerColor: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Map style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            tileStyles.forEach { style ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTile(style.id) }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        style.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (tileId == style.id) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (tileId == style.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (tileId == style.id) {
                        Spacer(Modifier.weight(1f))
                        Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text("Route color", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            ColorSwatches(selected = lineColor, onSelect = onColor)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text("Marker color", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            ColorSwatches(selected = markerColor, onSelect = onMarkerColor)
        }
    }
}

@Composable
private fun ColorSwatches(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        lineColors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(
                        width = if (color == selected) 2.5.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (color == selected) {
                    Text("✓", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun OsmdroidMap(
    routes: List<FlightRoute>,
    ref: ReferenceData?,
    tileId: String,
    lineColor: Int,
    markerColor: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val airportByPos = remember(ref) {
        HashMap<Pair<Double, Double>, Airport>().apply {
            ref?.airports?.values?.forEach { put(it.lat to it.lon, it) }
        }
    }
    val mapView = remember {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = "FlybookApp/1.0 (Android; flight log)"
        MapView(context).apply {
            setTileSource(tileStyles.first { it.id == tileId }.source)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 2.0
            maxZoomLevel = 19.0
            controller.setZoom(2.0)
            controller.setCenter(GeoPoint(25.0, 15.0))
        }
    }
    var cameraSet by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(false) }
    var appliedTile by remember { mutableStateOf<String?>(null) }

    DisposableEffect(mapView) {
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        update = {
            if (!resumed) {
                it.onResume()
                resumed = true
            }
            val style = tileStyles.first { it.id == tileId }
            if (appliedTile != tileId) {
                it.setTileSource(style.source)
                appliedTile = tileId
            }
            updateRoutes(it, routes, lineColor, markerColor, airportByPos)
            if (!cameraSet && routes.isNotEmpty()) {
                fitCamera(it, routes)
                cameraSet = true
            }
        },
        modifier = modifier,
    )
}

private fun airportLabel(a: Airport?): String =
    a?.let { it.iata.ifEmpty { it.icao } } ?: "?"

/** Small info bubble shown when tapping a route line or an airport marker. */
private class MapInfoWindow(mapView: MapView) : InfoWindow(R.layout.map_info_bubble, mapView) {
    override fun onOpen(item: Any) {
        val overlay = item as OverlayWithIW
        mView.findViewById<TextView>(R.id.bubble_title).text = overlay.title ?: ""
        mView.findViewById<TextView>(R.id.bubble_description).text = overlay.snippet ?: ""
    }

    override fun onClose() {}
}

private fun makeMarkerIcon(resources: Resources, color: Int): Drawable {
    val density = resources.displayMetrics.density
    val size = (14f * density).toInt().coerceAtLeast(14)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = size / 2f
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = android.graphics.Color.WHITE
    c.drawCircle(r, r, r, p)
    p.color = color
    c.drawCircle(r, r, r - 1.5f * density, p)
    return BitmapDrawable(resources, bmp)
}

private fun updateRoutes(
    map: MapView,
    routes: List<FlightRoute>,
    lineColor: Int,
    markerColor: Int,
    airportByPos: Map<Pair<Double, Double>, Airport>,
) {
    map.overlays.clear()
    map.overlays.add(CopyrightOverlay(map.context))
    val infoWindow = MapInfoWindow(map)

    // Tapping empty map closes any open info bubble.
    map.overlays.add(
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                InfoWindow.closeAllInfoWindowsOn(map)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
    )

    // Route count per unique route (same departure/arrival flown multiple times)
    val counts = HashMap<String, Int>()
    routes.forEach { r ->
        val k = "${r.depLat},${r.depLon}|${r.arrLat},${r.arrLon}"
        counts[k] = (counts[k] ?: 0) + 1
    }

    val markerIcon = makeMarkerIcon(map.context.resources, markerColor)
    val seenMarkers = HashSet<Pair<Double, Double>>()
    routes.forEach { r ->
        val dep = airportByPos[r.depLat to r.depLon]
        val arr = airportByPos[r.arrLat to r.arrLon]
        val depCode = airportLabel(dep)
        val arrCode = airportLabel(arr)

        val line = Polyline(map).apply {
            setPoints(listOf(GeoPoint(r.depLat, r.depLon), GeoPoint(r.arrLat, r.arrLon)))
            isGeodesic = true
            outlinePaint.color = lineColor
            outlinePaint.strokeWidth = 3f
            val key = "${r.depLat},${r.depLon}|${r.arrLat},${r.arrLon}"
            val count = counts[key] ?: 1
            title = "$depCode → $arrCode"
            snippet = "$count flight${if (count == 1) "" else "s"}"
            setInfoWindow(infoWindow)
        }
        map.overlays.add(line)

        listOf(r.depLat to r.depLon, r.arrLat to r.arrLon).forEach { pt ->
            if (!seenMarkers.add(pt)) return@forEach
            val a = airportByPos[pt]
            val marker = Marker(map).apply {
                position = GeoPoint(pt.first, pt.second)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = markerIcon
                title = airportLabel(a)
                snippet = buildString {
                    if (a != null) {
                        if (a.city.isNotEmpty()) append(a.city)
                        if (a.country.isNotEmpty()) {
                            if (a.city.isNotEmpty()) append(", ")
                            append(a.country)
                        }
                    }
                }
                setInfoWindow(infoWindow)
            }
            map.overlays.add(marker)
        }
    }
    map.invalidate()
}

private fun fitCamera(map: MapView, routes: List<FlightRoute>) {
    val pts = routes.flatMap { listOf(GeoPoint(it.depLat, it.depLon), GeoPoint(it.arrLat, it.arrLon)) }
    if (pts.isEmpty()) return
    map.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), false)
}
