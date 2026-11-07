package io.github.hackyday.irforge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.hackyday.irforge.ForgeApp
import io.github.hackyday.irforge.data.Code
import io.github.hackyday.irforge.data.newId
import io.github.hackyday.irforge.ir.TxStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareScreen(app: ForgeApp, nav: NavController) {
    val settings by app.store.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var testMsg by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(testMsg) {
        testMsg?.let { snackbar.showSnackbar(it); testMsg = null }
    }

    var refreshTick by remember { mutableStateOf(0) }
    val statuses by produceState<List<TxStatus>?>(null, settings.preferredTx, refreshTick) {
        value = app.irEngine.statusList(null)
    }
    val usbCandidates by produceState(app.irEngine.usb.scan(), refreshTick) {
        value = app.irEngine.usb.scan()
    }

    var capturing by remember { mutableStateOf(false) }
    var captured by remember { mutableStateOf<Pair<Int, IntArray>?>(null) }
    var learnName by remember { mutableStateOf("Learned signal") }
    var showSaveLearn by remember { mutableStateOf(false) }
    var auxTestPlaying by remember { mutableStateOf(false) }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Transmitters", style = MaterialTheme.typography.titleMedium)
            for (s in statuses ?: emptyList()) {
                TxCard(
                    status = s,
                    onTest = {
                        scope.launch {
                            testMsg = try {
                                val sink = when (s.key) { "builtin" -> app.irEngine.builtin; "usb" -> app.irEngine.usb; else -> app.irEngine.aux }
                                sink.transmit(38_000, intArrayOf(9000, 4500, 560, 560, 560, 1690, 560, 560, 560, 1690, 560, 40000))
                                "Test signal sent via ${s.label}."
                            } catch (e: Exception) {
                                "Test failed: ${e.message}"
                            }
                        }
                    },
                    extra = {
                        when (s.key) {
                            "usb" -> Column {
                                if (usbCandidates.isEmpty()) {
                                    Text("Plug in a USB IR blaster (IR Toy v2, FTDI, CH340, CP210x, CDC-ACM).", style = MaterialTheme.typography.bodySmall)
                                }
                                for (c in usbCandidates) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.weight(1f)) {
                                            Text(c.driver, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                String.format("%04x:%04x", c.device.vendorId, c.device.productId),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (c.kind == "unsupported") {
                                            Text("unsupported", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                        } else if (!app.irEngine.usb.hasPermission(c)) {
                                            OutlinedButton(onClick = {
                                                scope.launch {
                                                    app.irEngine.usb.requestPermission(c)
                                                    refreshTick++
                                                }
                                            }) { Text("Grant USB") }
                                        } else {
                                            if (c.kind == "irtoy") {
                                                OutlinedButton(onClick = {
                                                    scope.launch {
                                                        capturing = true
                                                        try {
                                                            val pat = app.irEngine.usb.capture(c)
                                                            captured = 38_000 to pat
                                                            showSaveLearn = true
                                                        } catch (e: Exception) {
                                                            testMsg = "Capture failed: ${e.message}"
                                                        }
                                                        capturing = false
                                                    }
                                                }) { Text(if (capturing) "…" else "Learn") }
                                            }
                                        }
                                    }
                                }
                            }
                        "aux" -> Column {
                                Text(
                                    "Mode: dongle with built-in carrier → \"envelope\"; passive LED dongle → \"raw\" (96 kHz).",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(onClick = { app.store.updateSettings { it.copy(auxMode = if (it.auxMode == "envelope") "raw" else "envelope") } }) {
                                        Text("Mode: ${settings.auxMode}")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Switch(
                                        checked = settings.auxChannelSwap,
                                        onCheckedChange = { v -> app.store.updateSettings { it.copy(auxChannelSwap = v) } },
                                    )
                                    Text("Swap L/R")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            auxTestPlaying = true
                                            try {
                                                app.irEngine.aux.playTestTone(settings.auxMode == "envelope", settings.auxChannelSwap)
                                                testMsg = "Test tone played — check your IR dongle's LED."
                                            } catch (e: Exception) {
                                                testMsg = "Test tone failed: ${e.message}"
                                            }
                                            auxTestPlaying = false
                                        }
                                    }) { Text(if (auxTestPlaying) "…" else "Test tone") }
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedButton(onClick = { app.store.updateSettings { it.copy(auxTested = true) } }) {
                                        Text(if (settings.auxTested) "✓ Dongle confirmed" else "Mark dongle as working")
                                    }
                                }
                            }
                        else -> Unit
                        }
                    },
                )
            }

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Preferred transmitter", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Used unless a remote overrides it. Auto = built-in → USB → AUX.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("" to "Auto", "builtin" to "Built-in", "usb" to "USB", "aux" to "AUX").forEach { (k, label) ->
                            OutlinedButton(
                                onClick = { app.store.updateSettings { it.copy(preferredTx = k) } },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                            ) {
                                Text(if (settings.preferredTx == k) "● $label" else label, maxLines = 1)
                            }
                        }
                    }
                }
            }

        }
    }

    if (showSaveLearn && captured != null) {
        val (freq, pat) = captured!!
        AlertDialog(
            onDismissRequest = { showSaveLearn = false },
            title = { Text("Save learned signal") },
            text = {
                Column {
                    Text("Captured ${pat.size} timing values (~${pat.sum() / 1000} ms) @ ${freq / 1000} kHz")
                    OutlinedTextField(value = learnName, onValueChange = { learnName = it }, label = { Text("Name") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    app.store.upsertCode(
                        Code(
                            id = newId(), name = learnName.ifBlank { "Learned signal" },
                            freqHz = freq, pattern = pat.toList(), source = "learned", sourceFormat = "irtoy-capture",
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                    showSaveLearn = false
                    captured = null
                }) { Text("Save to library") }
            },
            dismissButton = { TextButton(onClick = { showSaveLearn = false }) { Text("Discard") } },
        )
    }
}

@Composable
private fun TxCard(
    status: TxStatus,
    onTest: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (status.key) { "builtin" -> "📱"; "usb" -> "🔌"; else -> "🎧" },
                )
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(status.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        status.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (status.active) "ACTIVE" else if (status.available) "ready" else "absent",
                    color = if (status.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onTest, enabled = status.available || status.key == "aux") { Text("Test blast") }
            }
            extra?.let { it() }
        }
    }
}
