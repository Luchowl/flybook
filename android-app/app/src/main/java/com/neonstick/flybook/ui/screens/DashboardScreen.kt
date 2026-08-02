package com.neonstick.flybook.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neonstick.flybook.ui.FlybookViewModel
import com.neonstick.flybook.ui.PanelCard
import com.neonstick.flybook.ui.ScreenTopBar
import com.neonstick.flybook.ui.SectionHeader
import com.neonstick.flybook.ui.StatTile
import com.neonstick.flybook.ui.YearChip
import com.neonstick.flybook.util.Achievement
import com.neonstick.flybook.util.Format
import com.neonstick.flybook.util.Stats
import kotlin.math.roundToInt

@Composable
private fun MonthlyChart(counts: List<Int>) {
    val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    val months = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
    val gradient = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        )
    )
    Row(modifier = Modifier.fillMaxWidth().height(128.dp), verticalAlignment = Alignment.Bottom) {
        counts.forEachIndexed { index, count ->
            val h = (count.toFloat() / max * 96).coerceAtLeast(if (count > 0) 6f else 3f)
            Column(
                modifier = Modifier.weight(1f).height(128.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (count > 0) FontWeight.SemiBold else FontWeight.Normal,
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp, vertical = 3.dp)
                        .fillMaxWidth(0.75f)
                        .height(h.dp)
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(gradient),
                )
                Text(months[index], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DashboardScreen(vm: FlybookViewModel = viewModel()) {
    val flights by vm.flights.collectAsStateWithLifecycle()

    var selectedYear by remember { mutableStateOf(0) } // 0 = All
    var selectedAch by remember { mutableStateOf<Achievement?>(null) }
    var showWrapped by remember { mutableStateOf(false) }

    // System back gesture closes the wrapped showcase like the in-app back arrow.
    BackHandler(enabled = showWrapped) { showWrapped = false }

    if (showWrapped) {
        WrappedScreen(flights, onClose = { showWrapped = false })
        return
    }

    val stats = remember(flights) { Stats.overall(flights) }
    val years = remember(flights) { Stats.years(flights) }
    val yearToUse = if (selectedYear == 0) years.firstOrNull() ?: 0 else selectedYear
    val monthly = remember(flights, selectedYear, yearToUse) {
        if (selectedYear == 0) Stats.monthlyCountsAll(flights) else Stats.monthlyCounts(flights, yearToUse)
    }
    val achievements = remember(flights) { Stats.achievements(flights) }
    val unlockedCount = achievements.count { it.unlocked }

    Scaffold(
        topBar = { ScreenTopBar(title = "Flybook") },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("${stats.totalFlights}", "Flights", Icons.Default.FlightTakeoff, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatTile(Format.distance(stats.totalDistanceKm), "Distance", Icons.Default.Timeline, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(Format.duration(stats.totalDurationMinutes), "Time in Air", Icons.Default.Schedule, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    StatTile("${stats.uniqueAirlines}", "Airlines", Icons.Default.Public, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("${stats.uniqueCountries}", "Countries", Icons.Default.Public, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatTile("${stats.uniqueAirports}", "Airports", Icons.Default.Place, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Flight Story (wrapped) showcase
            PanelCard(onClick = { showWrapped = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Your Flight Story", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Tap for a wrapped review — distance, trips to the Moon and more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Monthly chart
            PanelCard {
                SectionHeader("Monthly Flights") {
                    if (years.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf(0) + years) { y ->
                                YearChip(if (y == 0) "All" else "$y", selected = selectedYear == y, onClick = { selectedYear = y })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                MonthlyChart(monthly)
            }

            Spacer(Modifier.height(16.dp))

            // Achievements
            PanelCard {
                SectionHeader("Achievements") {
                    Text(
                        "$unlockedCount / ${achievements.size}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (flights.isEmpty()) {
                    Text("Log your first flight to start unlocking achievements", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(achievements) { ach ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(76.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedAch = ach }
                                    .padding(vertical = 4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (ach.unlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        ach.emoji,
                                        style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier.alpha(if (ach.unlocked) 1f else 0.30f),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    ach.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    color = if (ach.unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedAch?.let { ach ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedAch = null },
            title = { Text("${ach.emoji}  ${ach.name}") },
            text = {
                Column {
                    Text(ach.description)
                    Spacer(Modifier.height(10.dp))
                    Text("Progress: ${ach.current}", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    val pct = (ach.progress * 100).roundToInt().coerceIn(0, 100)
                    Text("${if (ach.unlocked) "Unlocked" else "Locked"} · $pct%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { selectedAch = null }) { Text("Close") }
            },
        )
    }
}
