package com.luchowl.flybook.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AirlineSeatReclineExtra
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luchowl.flybook.data.Flight
import com.luchowl.flybook.ui.FlybookViewModel
import com.luchowl.flybook.ui.PanelCard
import com.luchowl.flybook.ui.ScreenTopBar
import com.luchowl.flybook.ui.SectionHeader
import com.luchowl.flybook.ui.StatTile
import com.luchowl.flybook.ui.TagChip
import com.luchowl.flybook.util.Format
import com.luchowl.flybook.util.Stats

@Composable
private fun RankingRow(rank: Int, name: String, count: Int, highlight: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            modifier = Modifier.width(26.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
        Text(
            "$count",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RankingPanel(
    title: String,
    items: List<Stats.Ranked>,
    max: Int = 6,
    onItemClick: ((Stats.Ranked) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    var showAll by remember { mutableStateOf(false) }
    val visible = if (showAll) items else items.take(max)
    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SectionHeader(title) {
                if (items.size > max) {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "Show less" else "See all (${items.size})")
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            visible.forEachIndexed { i, it ->
                RankingRow(i + 1, it.label, it.count, highlight = i == 0, onClick = onItemClick?.let { click -> { click(it) } })
            }
        }
    }
}

@Composable
private fun HighlightTile(
    icon: ImageVector,
    name: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.12f))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.width(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
            }
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompactFlightRow(flight: Flight, onClick: () -> Unit) {
    PanelCard(onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Format.code(flight.departureIata, flight.departureIcao),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(" → ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        Format.code(flight.arrivalIata, flight.arrivalIcao),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    buildString {
                        append(Format.date(flight.flightDate))
                        if (flight.airlineName.isNotEmpty() || flight.airlineIata.isNotEmpty()) {
                            append(" · ").append(flight.airlineName.ifEmpty { flight.airlineIata })
                        }
                        if (flight.flightNumber.isNotEmpty()) append(" · ").append(flight.flightNumber)
                        if (flight.registration.isNotEmpty()) append(" · ").append(flight.registration)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.distance(flight.distance), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text(Format.duration(flight.durationMinutes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RankingDetailScreen(
    title: String,
    flights: List<Flight>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onBack: () -> Unit,
    onFlightClick: (Flight) -> Unit,
) {
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        if (flights.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("No flights", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(pad)) {
                Text(
                    "${flights.size} flight${if (flights.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(flights, key = { it.id }) { f -> CompactFlightRow(f, onClick = { onFlightClick(f) }) }
                }
            }
        }
    }
}

@Composable
fun StatsScreen(vm: FlybookViewModel = viewModel()) {
    val flights by vm.flights.collectAsStateWithLifecycle()

    val aircraft = remember(flights) { Stats.topAircraft(flights) }
    val airlines = remember(flights) { Stats.topAirlines(flights) }
    val routes = remember(flights) { Stats.topRoutes(flights) }
    val airports = remember(flights) { Stats.topAirports(flights) }
    val registrations = remember(flights) { Stats.topRegistrations(flights) }
    val seats = remember(flights) { Stats.topSeats(flights) }
    val countries = remember(flights) { Stats.topCountries(flights) }
    val longest = remember(flights) { Stats.longestFlight(flights) }
    val early = remember(flights) { Stats.earlyFlightList(flights) }
    val night = remember(flights) { Stats.nightFlightList(flights) }
    val wideBody = remember(flights) { Stats.wideBodyFlights(flights) }
    val avgDistance = remember(flights) { Stats.averageDistance(flights) }
    val avgDuration = remember(flights) { Stats.averageDurationMinutes(flights) }
    val busiestYear = remember(flights) { Stats.busiestYear(flights) }

    var detail by remember { mutableStateOf<Pair<String, List<Flight>>?>(null) }
    var flightDetail by remember { mutableStateOf<Flight?>(null) }
    val rankingListState = rememberLazyListState()
    val scrollState = rememberScrollState()

    // System back gesture behaves like the in-app back arrow.
    BackHandler(enabled = flightDetail != null || detail != null) {
        if (flightDetail != null) flightDetail = null else detail = null
    }

    flightDetail?.let { f ->
        FlightDetailScreen(
            flight = f,
            onBack = { flightDetail = null },
            onDelete = {
                vm.deleteFlight(f.id)
                flightDetail = null
            },
        )
        return
    }

    detail?.let { (title, list) ->
        RankingDetailScreen(title, list, rankingListState, onBack = { detail = null }, onFlightClick = { flightDetail = it })
        return
    }

    Scaffold(
        topBar = { ScreenTopBar(title = "Stats") },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (flights.isEmpty()) {
                Text("Log or import flights to see your records.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            // Most-flown highlights (tap to drill in)
            val topAircraft = aircraft.firstOrNull()
            val topAirline = airlines.firstOrNull()
            val topRoute = routes.firstOrNull()
            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Most Flown")
                    Spacer(Modifier.height(2.dp))
                    HighlightTile(
                        Icons.Default.FlightTakeoff, topAircraft?.label ?: "—",
                        "Aircraft · ${topAircraft?.count ?: 0} flights",
                        MaterialTheme.colorScheme.primary,
                        Modifier.fillMaxWidth(),
                        onClick = if (topAircraft != null) {
                            { detail = "Aircraft · ${topAircraft.label}" to Stats.flightsForAircraft(flights, topAircraft.label) }
                        } else null,
                    )
                    HighlightTile(
                        Icons.Default.AirlineSeatReclineExtra, topAirline?.label ?: "—",
                        "Airline · ${topAirline?.count ?: 0} flights",
                        MaterialTheme.colorScheme.tertiary,
                        Modifier.fillMaxWidth(),
                        onClick = if (topAirline != null) {
                            { detail = "Airline · ${topAirline.label}" to Stats.flightsForAirline(flights, topAirline.label) }
                        } else null,
                    )
                    HighlightTile(
                        Icons.Default.Route, topRoute?.label ?: "—",
                        "Route · ${topRoute?.count ?: 0} flights",
                        MaterialTheme.colorScheme.secondary,
                        Modifier.fillMaxWidth(),
                        onClick = if (topRoute != null) {
                            { detail = "Route · ${topRoute.label}" to Stats.flightsForRoute(flights, topRoute.label) }
                        } else null,
                    )
                }
            }

            // Highlights (early/night, wide-body, busiest year) — tap to see the flights
            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Highlights")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("${early.size}", "Early (before 8am)", Icons.Default.Schedule, MaterialTheme.colorScheme.primary, Modifier.weight(1f),
                            onClick = { detail = "Early flights (before 8am)" to early })
                        StatTile("${night.size}", "Night (after 10pm)", Icons.Default.Schedule, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f),
                            onClick = { detail = "Night flights (after 10pm)" to night })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("${wideBody.size}", "Wide-body", Icons.Default.FlightTakeoff, MaterialTheme.colorScheme.secondary, Modifier.weight(1f),
                            onClick = { detail = "Wide-body flights" to wideBody })
                        busiestYear?.let { (year, count) ->
                            StatTile("$year", "Busiest year ($count flts)", Icons.Default.EmojiEvents, MaterialTheme.colorScheme.primary, Modifier.weight(1f),
                                onClick = { detail = "Flights in $year" to Stats.flightsForYear(flights, year) })
                        } ?: StatTile("—", "Busiest year", Icons.Default.EmojiEvents, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val topClass = Stats.topCabinClass(flights)
                        StatTile(
                            topClass?.label ?: "—",
                            if (topClass != null) "Most flown class (${topClass.count} ${if (topClass.count == 1) "time" else "times"})" else "Most flown class",
                            Icons.Default.AirlineSeatReclineExtra, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f),
                            onClick = topClass?.let { c -> { detail = "${c.label} class" to Stats.flightsForCabinClass(flights, c.label) } },
                        )
                    }
                }
            }

            // Averages — read-only summary (no drill-down needed)
            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Averages")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(Format.distance(avgDistance), "Avg distance", Icons.Default.Public, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                        StatTile(Format.duration(avgDuration), "Avg time in air", Icons.Default.Schedule, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("${flights.size}", "Total flights", Icons.Default.Place, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        StatTile("${Stats.overall(flights).uniqueAirports}", "Unique airports", Icons.Default.Place, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    }
                }
            }

            // Longest haul — tap for the full flight record
            longest?.let { f ->
                PanelCard(onClick = { flightDetail = f }) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionHeader("Longest Haul")
                        Text(
                            "${Format.code(f.departureIata, f.departureIcao)} → ${Format.code(f.arrivalIata, f.arrivalIcao)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${Format.distance(f.distance)} · ${Format.date(f.flightDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            RankingPanel("Aircraft", aircraft, onItemClick = { r -> detail = "Aircraft · ${r.label}" to Stats.flightsForAircraft(flights, r.label) })
            RankingPanel("Routes", routes, onItemClick = { r -> detail = "Route · ${r.label}" to Stats.flightsForRoute(flights, r.label) })
            RankingPanel("Airlines", airlines, onItemClick = { r -> detail = "Airline · ${r.label}" to Stats.flightsForAirline(flights, r.label) })
            RankingPanel("Airports", airports, onItemClick = { r -> detail = "Airport · ${r.label}" to Stats.flightsForAirport(flights, r.label) })
            RankingPanel("Registrations (Tails)", registrations, onItemClick = { r -> detail = "Tail · ${r.label}" to Stats.flightsForRegistration(flights, r.label) })
            RankingPanel("Seats", seats.filter { it.count > 1 }, onItemClick = { r -> detail = "Seat · ${r.label}" to Stats.flightsForSeat(flights, r.label) })
            RankingPanel("Countries", countries, onItemClick = { r -> detail = "Country · ${r.label}" to Stats.flightsForCountry(flights, r.label) })
        }
    }
}
