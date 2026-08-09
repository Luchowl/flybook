package com.luchowl.flybook.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luchowl.flybook.ui.FlybookViewModel
import com.luchowl.flybook.ui.PanelCard
import com.luchowl.flybook.ui.ScreenTopBar
import com.luchowl.flybook.util.Csv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(vm: FlybookViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flights by vm.flights.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = "Importing…"
            val parsed = withContext(Dispatchers.IO) {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                if (text.isNullOrBlank()) emptyList() else Csv.parseFlights(text)
            }
            if (parsed.isNotEmpty()) {
                vm.importFlights(parsed)
                message = "Imported ${parsed.size} flights"
            } else {
                message = "No valid flights found in file"
            }
        }
    }

    fun export(fileName: String, content: String, mime: String) {
        val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export Flybook data"))
    }

    Scaffold(
        topBar = { ScreenTopBar(title = "Settings") },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            message?.let {
                PanelCard(contentPadding = PaddingValues(14.dp)) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Export", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Back up or move your logbook.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { export("flybook-export.csv", Csv.serialize(flights), "text/csv") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Export as CSV")
                    }
                    OutlinedButton(onClick = { export("flybook-export.json", Csv.toJson(flights), "application/json") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Export as JSON")
                    }
                }
            }

            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Import", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Import flights from a CSV file following the my.flightradar24.com export format (also compatible with this app's own export).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { importLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Import CSV")
                    }
                }
            }

            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sample Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Don't have any flights yet? Load a bundled set of sample flights to explore Flybook, then delete them whenever you like.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                message = "Loading sample data…"
                                val parsed = withContext(Dispatchers.IO) {
                                    Csv.parseFlights(context.assets.open("sample-flights.csv").bufferedReader(Charsets.UTF_8).use { it.readText() })
                                }
                                if (parsed.isNotEmpty()) {
                                    vm.importFlights(parsed)
                                    message = "Loaded ${parsed.size} sample flights"
                                } else {
                                    message = "Failed to load sample data"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Load sample flights")
                    }
                }
            }

            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Danger Zone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Delete all flights", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val versionName = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
                    Text(
                        "Flybook v${versionName ?: "1.0.0"} · MIT License",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "All data is stored locally on this device. No account or internet required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Source code",
                        modifier = Modifier
                            .clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Luchowl/flybook"))
                                )
                            }
                            .padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all flights?") },
            text = { Text("This cannot be undone. Consider exporting your data first.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAll()
                    confirmClear = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}
