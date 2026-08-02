package com.neonstick.flybook.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neonstick.flybook.data.Flight
import com.neonstick.flybook.ui.ScreenTopBar
import com.neonstick.flybook.util.Format
import com.neonstick.flybook.util.OverallStats
import com.neonstick.flybook.util.Stats
import java.util.Locale
import kotlin.math.roundToInt

private data class WrappedSlide(val emoji: String, val title: String, val bigValue: String, val description: String)

@Composable
fun WrappedScreen(flights: List<Flight>, onClose: () -> Unit) {
    val slides = remember(flights) { buildSlides(flights) }
    var index by remember { mutableIntStateOf(0) }
    val last = slides.lastIndex

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = "Your Flight Story",
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (slideInHorizontally(initialOffsetX = { it }) + fadeIn()) togetherWith
                            (slideOutHorizontally(targetOffsetX = { -it }) + fadeOut())
                },
                label = "wrappedSlide",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { if (index < last) index++ else onClose() },
            ) { i ->
                SlideContent(slides[i], Modifier.fillMaxSize())
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                slides.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (i == index) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == index) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                    )
                }
            }

            Button(
                onClick = { if (index < last) index++ else onClose() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(if (index == last) "Finish" else "Next")
            }
        }
    }
}

@Composable
private fun SlideContent(slide: WrappedSlide, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(400))
        scale.animateTo(1f, tween(700, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)))
    }
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            slide.emoji,
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.graphicsLayer {
                this.alpha = alpha.value
                scaleX = scale.value
                scaleY = scale.value
            },
        )
        Spacer(Modifier.height(24.dp))
        Text(
            slide.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            slide.bigValue,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            slide.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

private fun buildSlides(flights: List<Flight>): List<WrappedSlide> {
    if (flights.isEmpty()) {
        return listOf(
            WrappedSlide("🐣", "Your Flight Story", "0 flights", "Log your first flight to see your wrapped year")
        )
    }
    val s = Stats.overall(flights)
    val dist = s.totalDistanceKm
    val minutes = s.totalDurationMinutes
    val days = minutes / 1440.0

    val slides = mutableListOf<WrappedSlide>()
    slides += WrappedSlide(
        "✈️",
        "Your Flight Story",
        "${s.totalFlights}",
        "${s.totalFlights} flight${if (s.totalFlights == 1) "" else "s"} logged · ${s.uniqueAirlines} airlines · ${s.uniqueCountries} countries",
    )

    // Distance → around the Earth
    val earths = dist / 40075.0
    slides += WrappedSlide(
        "🌍",
        "Total Distance Flown",
        Format.distance(dist),
        when {
            earths >= 1 -> "That's %.1f trips around the Earth".format(Locale.US, earths)
            earths >= 0.1 -> "That's %.0f%% of the way around the Earth".format(Locale.US, earths * 100)
            else -> "That's %.1f%% of the way around the Earth".format(Locale.US, earths * 100)
        },
    )

    // Distance → to the Moon
    val moons = dist / 384400.0
    slides += WrappedSlide(
        "🌕",
        "To the Moon and Back",
        if (moons >= 0.1) "%.2f×".format(Locale.US, moons) else "%.1f%%".format(Locale.US, moons * 100),
        if (moons >= 0.1) "trips to the Moon (384,400 km)" else "of the way to the Moon",
    )

    // Time in the air
    slides += WrappedSlide(
        "⏱️",
        "Time in the Air",
        Format.duration(minutes),
        if (days >= 1) "That's ≈ ${days.roundToInt()} full days of continuous flying" else "A true frequent flyer",
    )

    val topAirline = Stats.topAirlines(flights).firstOrNull()
    slides += WrappedSlide(
        "🧑‍✈️",
        "Your Top Airline",
        topAirline?.label ?: "—",
        "${topAirline?.count ?: 0} flight${if (topAirline?.count == 1) "" else "s"} with this carrier",
    )

    val topAircraft = Stats.topAircraft(flights).firstOrNull()
    slides += WrappedSlide(
        "🛩️",
        "Your Top Aircraft",
        topAircraft?.label ?: "—",
        "${topAircraft?.count ?: 0} flight${if (topAircraft?.count == 1) "" else "s"} on this type",
    )

    val topRoute = Stats.topRoutes(flights).firstOrNull()
    slides += WrappedSlide(
        "🛣️",
        "Your Most-Flown Route",
        topRoute?.label ?: "—",
        "${topRoute?.count ?: 0} time${if (topRoute?.count == 1) "" else "s"} — a regular of the skies",
    )

    val topClass = Stats.topCabinClass(flights)
    slides += WrappedSlide(
        "💺",
        "Your Cabin Class",
        topClass?.label ?: "—",
        "${topClass?.count ?: 0} flight${if (topClass?.count == 1) "" else "s"} in this class",
    )

    slides += WrappedSlide(
        "🏆",
        "Your 2026 Flyer Title",
        flyerTitle(s),
        "You flew ${Format.distance(dist)} across ${s.uniqueAirports} airports. Keep exploring!",
    )
    return slides
}

private fun flyerTitle(s: OverallStats): String = when {
    s.totalDistanceKm >= 384400 -> "Moonwalker 🚀"
    s.totalDistanceKm >= 40075 -> "Circumnavigator 🌍"
    s.totalFlights >= 100 -> "Sky Legend 🏆"
    s.totalFlights >= 50 -> "Aviation Pro ✈️"
    s.totalFlights >= 10 -> "Airlines Enthusiast 🔟"
    s.totalFlights >= 1 -> "First Steps 🐣"
    else -> "—"
}
