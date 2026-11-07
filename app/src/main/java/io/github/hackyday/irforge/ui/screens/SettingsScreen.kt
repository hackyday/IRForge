package io.github.hackyday.irforge.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.hackyday.irforge.ForgeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: ForgeApp, nav: NavController) {
    val settings by app.store.settings.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var carrierText by remember(settings.carrierOverrideHz) { mutableStateOf(if (settings.carrierOverrideHz == 0) "" else settings.carrierOverrideHz.toString()) }
    var repeatsText by remember(settings.defaultRepeats) { mutableStateOf(settings.defaultRepeats.toString()) }

    val exportDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(app.store.exportAll().toByteArray()) }
                }
                snackbar.showSnackbar("Backup saved")
            }
        }
    }
    val importDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
                val ok = text != null && app.store.restoreAll(text)
                snackbar.showSnackbar(if (ok) "Backup restored" else "Invalid backup file")
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TopAppBar(title = { Text("Settings") })

            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = settings.themeMode == "system", onClick = { app.store.updateSettings { it.copy(themeMode = "system") } })
                        Text("System")
                        Spacer(Modifier.fillMaxWidth(0.1f))
                        RadioButton(selected = settings.themeMode == "dark", onClick = { app.store.updateSettings { it.copy(themeMode = "dark") } })
                        Text("Dark")
                        RadioButton(selected = settings.themeMode == "light", onClick = { app.store.updateSettings { it.copy(themeMode = "light") } })
                        Text("Light")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = settings.haptics, onCheckedChange = { v -> app.store.updateSettings { it.copy(haptics = v) } })
                        Text("  Global haptic feedback")
                    }
                }
            }

            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Transmission", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = repeatsText,
                        onValueChange = { v ->
                            repeatsText = v.filter { it.isDigit() }.take(2)
                            app.store.updateSettings { it.copy(defaultRepeats = repeatsText.toIntOrNull()?.coerceIn(1, 20) ?: 1) }
                        },
                        label = { Text("Default repeats per code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = carrierText,
                        onValueChange = { v ->
                            carrierText = v.filter { it.isDigit() }.take(7)
                            app.store.updateSettings { it.copy(carrierOverrideHz = carrierText.toIntOrNull() ?: 0) }
                        },
                        label = { Text("Carrier override (Hz, empty = off)") },
                        supportingText = { Text("Force a frequency for stubborn devices, e.g. 36000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup & restore", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Everything (remotes, codes, macros, settings) as one JSON file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { exportDoc.launch("irforge-backup.json") }) { Text("⬆ Export") }
                        OutlinedButton(onClick = { importDoc.launch(arrayOf("application/json", "*/*")) }) { Text("⬇ Restore") }
                    }
                }
            }

            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("About", style = MaterialTheme.typography.titleSmall)
                    Text("IRForge v1.0.0", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Universal IR remote · built-in / USB / AUX blasters · Flipper .ir import · github.com/hackyday/IRForge",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
