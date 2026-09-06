package com.luchowl.flybook.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.luchowl.flybook.data.Enricher
import com.luchowl.flybook.data.Flight
import com.luchowl.flybook.data.ReferenceData
import com.luchowl.flybook.ui.ScreenTopBar
import com.luchowl.flybook.util.Csv
import com.luchowl.flybook.util.Format
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoCompleteField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onChange(it)
                expanded = it.isNotBlank()
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .onFocusChanged { if (!it.isFocused) expanded = false },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val options = rankedSuggestions(suggestions, value)
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No matches", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { expanded = false },
                )
            } else {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, maxLines = 1) },
                        onClick = {
                            onChange(opt)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Code-first ranking for autocomplete suggestions like "FRA · Frankfurt (Germany)".
 * Exact code wins, then code prefix, then label prefix, then any-substring matches,
 * so typing a code surfaces the matching airport/airline instead of burying it
 * under countries/cities that merely contain the letters.
 */
internal fun rankedSuggestions(suggestions: List<String>, query: String, max: Int = MAX_SUGGESTIONS): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return suggestions.take(max)
    val upper = q.uppercase(Locale.ROOT)
    return suggestions
        .mapNotNull { label ->
            val li = label.uppercase(Locale.ROOT)
            if (!li.contains(upper)) {
                null
            } else {
                val code = label.substringBefore(" · ").uppercase(Locale.ROOT)
                val rank = when {
                    code == upper -> 0
                    code.startsWith(upper) -> 1
                    li.startsWith(upper) -> 2
                    else -> 3
                }
                label to rank
            }
        }
        .sortedWith(compareBy({ it.second }, { it.first }))
        .take(max)
        .map { it.first }
}

private const val MAX_SUGGESTIONS = 8

private fun airportSuggestions(ref: ReferenceData): List<String> =
    ref.airports.values
        .map { a -> a to a.iata.ifEmpty { a.icao } }
        .filter { (_, code) -> code.isNotEmpty() }
        .distinctBy { (_, code) -> code }
        .map { (a, code) ->
            buildString {
                append(code)
                if (a.city.isNotEmpty()) {
                    append(" · ").append(a.city)
                    if (a.country.isNotEmpty()) append(" (").append(a.country).append(")")
                } else if (a.country.isNotEmpty()) {
                    append(" · ").append(a.country)
                }
            }
        }

private fun airlineSuggestions(ref: ReferenceData): List<String> =
    ref.airlines.values
        .map { a -> a to a.iata.ifEmpty { a.icao } }
        .filter { (_, code) -> code.isNotEmpty() }
        .distinctBy { (_, code) -> code }
        .map { (a, code) -> "$code · ${a.name}" }

private fun planeSuggestions(ref: ReferenceData): List<String> =
    ref.planes.values
        .map { p -> p to p.icao.ifEmpty { p.iata } }
        .filter { (_, code) -> code.isNotEmpty() }
        .distinctBy { (_, code) -> code }
        .map { (p, code) -> "$code · ${p.name}" }

private val cabinClassOptions = listOf("Economy", "Premium Economy", "Business", "First")

private fun utcDateMillis(y: Int, m: Int, d: Int): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(y, m, d, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightFormScreen(
    ref: ReferenceData?,
    editing: Flight?,
    onSave: (Flight) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val today = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

    var flightDate by remember { mutableStateOf(editing?.flightDate ?: utcDateMillis(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH))) }
    var flightNumber by remember { mutableStateOf(editing?.flightNumber?.uppercase(Locale.ROOT) ?: "") }
    var airline by remember { mutableStateOf(editing?.let { it.airlineName.ifEmpty { it.airlineIata } } ?: "") }
    var dep by remember { mutableStateOf(editing?.let { Format.code(it.departureIata, it.departureIcao) } ?: "") }
    var arr by remember { mutableStateOf(editing?.let { Format.code(it.arrivalIata, it.arrivalIcao) } ?: "") }
    var aircraft by remember { mutableStateOf(editing?.let { it.aircraftName.ifEmpty { it.aircraftType } } ?: "") }
    var registration by remember { mutableStateOf(editing?.registration ?: "") }
    var seat by remember { mutableStateOf(editing?.seatNumber ?: "") }
    var cabinClass by remember { mutableStateOf(editing?.cabinClass ?: "") }
    var notes by remember { mutableStateOf(editing?.notes ?: "") }
    var depHour by remember { mutableStateOf(if ((editing?.depHour ?: -1) >= 0) "${editing!!.depHour}" else "") }
    var depMin by remember { mutableStateOf(if ((editing?.depMinute ?: -1) >= 0) "${editing!!.depMinute}" else "") }
    var arrHour by remember { mutableStateOf(if ((editing?.arrHour ?: -1) >= 0) "${editing!!.arrHour}" else "") }
    var arrMin by remember { mutableStateOf(if ((editing?.arrMinute ?: -1) >= 0) "${editing!!.arrMinute}" else "") }

    val airportOpts = remember(ref) { ref?.let { airportSuggestions(it) } ?: emptyList() }
    val airlineOpts = remember(ref) { ref?.let { airlineSuggestions(it) } ?: emptyList() }
    val planeOpts = remember(ref) { ref?.let { planeSuggestions(it) } ?: emptyList() }

    val dateLabel = remember(flightDate) { Format.iso(flightDate) }

    fun showDatePicker() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = flightDate }
        android.app.DatePickerDialog(
            context,
            { _, y, m, d -> flightDate = utcDateMillis(y, m, d) },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    fun save() {
        val depCode = Csv.extractCode(dep)
        val arrCode = Csv.extractCode(arr)
        val airlineCode = Csv.extractCode(airline, 2) // airline IATA can be 2 chars (LH, AC, W6)
        val aircraftCode = Csv.extractCode(aircraft, 2)
        if (depCode.isBlank() || arrCode.isBlank()) return

        val flight = Flight(
            id = editing?.id ?: UUID.randomUUID().toString(),
            flightDate = flightDate,
            flightNumber = flightNumber.trim().uppercase(Locale.ROOT),
            airlineIata = airlineCode,
            airlineName = if (airlineCode.isEmpty()) airline.trim() else "",
            departureIata = depCode,
            arrivalIata = arrCode,
            aircraftType = aircraftCode,
            aircraftName = if (aircraftCode.isEmpty()) aircraft.trim() else "",
            registration = registration.trim().uppercase(),
            seatNumber = seat.trim(),
            cabinClass = cabinClass.trim(),
            notes = notes.trim(),
            depHour = depHour.toIntOrNull() ?: -1,
            depMinute = depMin.toIntOrNull() ?: 0,
            arrHour = arrHour.toIntOrNull() ?: -1,
            arrMinute = arrMin.toIntOrNull() ?: 0,
        )
        onSave(flight)
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = if (editing == null) "Add Flight" else "Edit Flight",
                navigationIcon = {
                    IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = dateLabel,
                onValueChange = {},
                label = { Text("Flight Date") },
                readOnly = true,
                trailingIcon = { TextButton(onClick = { showDatePicker() }) { Text("Pick") } },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = flightNumber,
                onValueChange = { flightNumber = it.uppercase(Locale.ROOT) },
                label = { Text("Flight Number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            AutoCompleteField("Airline", airline, { airline = it }, airlineOpts)
            AutoCompleteField("From", dep, { dep = it }, airportOpts)
            AutoCompleteField("To", arr, { arr = it }, airportOpts)
            AutoCompleteField("Aircraft", aircraft, { aircraft = it }, planeOpts)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = registration,
                    onValueChange = { registration = it },
                    label = { Text("Registration") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = seat,
                    onValueChange = { seat = it },
                    label = { Text("Seat") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            AutoCompleteField("Class", cabinClass, { cabinClass = Enricher.normalizeCabinClass(it) }, cabinClassOptions)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = depHour,
                    onValueChange = { depHour = it },
                    label = { Text("Dep HH") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = depMin,
                    onValueChange = { depMin = it },
                    label = { Text("Dep MM") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = arrHour,
                    onValueChange = { arrHour = it },
                    label = { Text("Arr HH") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = arrMin,
                    onValueChange = { arrMin = it },
                    label = { Text("Arr MM") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
            )

            Button(
                onClick = { save() },
                enabled = Csv.extractCode(dep).isNotBlank() && Csv.extractCode(arr).isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (editing == null) "Add Flight" else "Save Changes")
            }
        }
    }
}
