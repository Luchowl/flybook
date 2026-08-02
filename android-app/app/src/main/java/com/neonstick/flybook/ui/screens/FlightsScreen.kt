package com.neonstick.flybook.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neonstick.flybook.data.Flight
import com.neonstick.flybook.ui.FlybookViewModel
import com.neonstick.flybook.ui.PanelCard
import com.neonstick.flybook.ui.ScreenTopBar
import com.neonstick.flybook.ui.SectionHeader
import com.neonstick.flybook.ui.TagChip
import com.neonstick.flybook.ui.YearChip
import com.neonstick.flybook.util.Format
import com.neonstick.flybook.util.Stats

private val sortOptions = listOf(
    "date" to "Date",
    "aircraft" to "Aircraft",
    "airline" to "Airline",
    "distance" to "Distance",
    "duration" to "Duration",
)

private fun sortedAndFiltered(flights: List<Flight>, sortKey: String, selectedYear: Int): List<Flight> {
    val filtered = if (selectedYear == 0) flights
    else flights.filter { Format.year(it.flightDate) == selectedYear }
    return when (sortKey) {
        "aircraft" -> filtered.sortedBy { it.aircraftName.ifEmpty { it.aircraftType }.lowercase() }
        "airline" -> filtered.sortedBy { it.airlineName.ifEmpty { it.airlineIata }.lowercase() }
        "distance" -> filtered.sortedByDescending { it.distance }
        "duration" -> filtered.sortedByDescending { it.durationMinutes }
        else -> filtered.sortedByDescending { it.flightDate }
    }
}

private fun matchesQuery(f: Flight, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    fun m(vararg fields: String) = fields.any { it.contains(q, ignoreCase = true) }
    return m(
        f.registration,
        f.airlineName, f.airlineIata, f.airlineIcao,
        f.departureIata, f.departureIcao, f.departureName, f.departureCity, f.departureCountry,
        f.arrivalIata, f.arrivalIcao, f.arrivalName, f.arrivalCity, f.arrivalCountry,
        f.aircraftName, f.aircraftType,
        f.flightNumber,
        f.seatNumber,
    )
}

private data class FlightRowModel(
    val flight: Flight,
    val depCode: String,
    val arrCode: String,
    val dateStr: String,
    val subtitle: String,
    val aircraft: String,
    val registration: String,
    val timeStr: String,
    val distanceStr: String,
    val durationStr: String,
)

private fun buildFlightRowModel(f: Flight): FlightRowModel {
    val aircraft = f.aircraftName.ifEmpty { f.aircraftType }
    val subtitle = buildString {
        if (f.airlineName.isNotEmpty() || f.airlineIata.isNotEmpty()) {
            append("· ").append(f.airlineName.ifEmpty { f.airlineIata })
            if (f.flightNumber.isNotEmpty()) append(" ").append(f.flightNumber)
        }
    }
    return FlightRowModel(
        flight = f,
        depCode = Format.code(f.departureIata, f.departureIcao),
        arrCode = Format.code(f.arrivalIata, f.arrivalIcao),
        dateStr = Format.date(f.flightDate),
        subtitle = subtitle,
        aircraft = aircraft,
        registration = f.registration,
        timeStr = Format.time(f.depHour, f.depMinute),
        distanceStr = Format.distance(f.distance),
        durationStr = Format.duration(f.durationMinutes),
    )
}

@Composable
private fun FlightRow(model: FlightRowModel, onClick: () -> Unit, onDelete: () -> Unit) {
    PanelCard(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(88.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                    )
            )
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        model.depCode,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("  →  ", style = MaterialTheme.typography.titleMedium)
                    Text(
                        model.arrCode,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        model.dateStr,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (model.subtitle.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            model.subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (model.aircraft.isNotEmpty()) {
                        TagChip(model.aircraft, MaterialTheme.colorScheme.tertiary)
                    }
                    if (model.registration.isNotEmpty()) {
                        TagChip(model.registration, MaterialTheme.colorScheme.secondary)
                    }
                    Text(model.timeStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(model.distanceStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(model.durationStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun FlightDetailScreen(
    flight: Flight,
    onBack: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = "Flight Details",
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (onEdit != null) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Route header
            PanelCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Format.code(flight.departureIata, flight.departureIcao),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text("  →  ", style = MaterialTheme.typography.titleLarge)
                        Text(
                            Format.code(flight.arrivalIata, flight.arrivalIcao),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        buildString {
                            append(Format.date(flight.flightDate))
                            if (flight.airlineName.isNotEmpty() || flight.airlineIata.isNotEmpty()) {
                                append(" · ").append(flight.airlineName.ifEmpty { flight.airlineIata })
                            }
                            if (flight.flightNumber.isNotEmpty()) append(" · ").append(flight.flightNumber)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Departure / Arrival
            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Route")
                    Spacer(Modifier.height(2.dp))
                    Text("DEPARTURE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        buildString {
                            append(flight.departureName.ifEmpty { Format.code(flight.departureIata, flight.departureIcao) })
                            if (flight.departureCity.isNotEmpty()) append(", ").append(flight.departureCity)
                            if (flight.departureCountry.isNotEmpty()) append(", ").append(flight.departureCountry)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${Format.code(flight.departureIata, flight.departureIcao)}${if (flight.departureIcao.isNotEmpty()) " / ${flight.departureIcao}" else ""} · ${Format.time(flight.depHour, flight.depMinute)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text("ARRIVAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        buildString {
                            append(flight.arrivalName.ifEmpty { Format.code(flight.arrivalIata, flight.arrivalIcao) })
                            if (flight.arrivalCity.isNotEmpty()) append(", ").append(flight.arrivalCity)
                            if (flight.arrivalCountry.isNotEmpty()) append(", ").append(flight.arrivalCountry)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${Format.code(flight.arrivalIata, flight.arrivalIcao)}${if (flight.arrivalIcao.isNotEmpty()) " / ${flight.arrivalIcao}" else ""} · ${Format.time(flight.arrHour, flight.arrMinute)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Details
            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Details")
                    Spacer(Modifier.height(2.dp))
                    DetailRow("Airline", flight.airlineName.ifEmpty { flight.airlineIata })
                    DetailRow("Flight No.", flight.flightNumber)
                    DetailRow("Aircraft", flight.aircraftName.ifEmpty { flight.aircraftType })
                    DetailRow("Registration", flight.registration)
                    DetailRow("Departure", Format.time(flight.depHour, flight.depMinute))
                    DetailRow("Arrival", Format.time(flight.arrHour, flight.arrMinute))
                    DetailRow("Duration", Format.duration(flight.durationMinutes))
                    DetailRow("Distance", Format.distance(flight.distance))
                    DetailRow("Seat", flight.seatNumber)
                    DetailRow("Class", flight.cabinClass)
                    if (flight.notes.isNotBlank()) {
                        DetailRow("Notes", flight.notes)
                    }
                }
            }
        }
    }
}

@Composable
fun FlightsScreen(vm: FlybookViewModel = viewModel()) {
    val flights by vm.flights.collectAsStateWithLifecycle()
    val ref by vm.refData.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Flight?>(null) }
    var detail by remember { mutableStateOf<Flight?>(null) }
    var sortKey by remember { mutableStateOf("date") }
    var selectedYear by remember { mutableStateOf(0) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val years = remember(flights) { Stats.years(flights) }
    val display = remember(flights, sortKey, selectedYear, query) {
        sortedAndFiltered(flights, sortKey, selectedYear).filter { matchesQuery(it, query) }
    }
    val displayModels = remember(display) { display.map { buildFlightRowModel(it) } }
    val listState = rememberLazyListState()

    // Jump to the top so the new order is immediately visible
    LaunchedEffect(sortKey, selectedYear, query, flights) {
        listState.scrollToItem(0)
    }

    // System back gesture behaves like the in-app back arrow.
    BackHandler(enabled = showForm || detail != null) {
        if (showForm) {
            showForm = false
            editing = null
        } else {
            detail = null
        }
    }

    if (showForm) {
        FlightFormScreen(
            ref = ref,
            editing = editing,
            onSave = { f ->
                if (editing == null) vm.addFlight(f) else vm.updateFlight(f)
                showForm = false
                editing = null
                detail = null
            },
            onCancel = {
                showForm = false
                editing = null
            },
        )
        return
    }

    detail?.let { flight ->
        FlightDetailScreen(
            flight = flight,
            onBack = { detail = null },
            onEdit = {
                editing = flight
                showForm = true
            },
            onDelete = {
                vm.deleteFlight(flight.id)
                detail = null
            },
        )
        return
    }

    Scaffold(
        topBar = { ScreenTopBar(title = "My Flights") },
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                detail = null
                showForm = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add flight")
            }
        },
    ) { pad ->
        if (flights.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FlightTakeoff, contentDescription = null, modifier = Modifier.height(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No flights yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Tap + to add your first flight", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(pad)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search reg, airline, airport…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box {
                        YearChip(
                            label = "Sort: ${sortOptions.first { it.first == sortKey }.second}",
                            selected = false,
                            onClick = { sortMenuOpen = true },
                        )
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            sortOptions.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(if (key == sortKey) "✓ $label" else label) },
                                    onClick = {
                                        sortKey = key
                                        sortMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    if (years.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf(0) + years) { y ->
                                YearChip(if (y == 0) "All" else "$y", selected = selectedYear == y, onClick = { selectedYear = y })
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Text(
                    "${display.size} of ${flights.size} flights",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )

                if (display.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No matches for \"${query.trim()}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(displayModels, key = { it.flight.id }, contentType = { "flight" }) { model ->
                            FlightRow(
                                model = model,
                                onClick = { detail = model.flight },
                                onDelete = { vm.deleteFlight(model.flight.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
