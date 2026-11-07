package io.github.hackyday.irforge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.hackyday.irforge.ForgeApp
import io.github.hackyday.irforge.data.Macro
import io.github.hackyday.irforge.data.MacroStep
import io.github.hackyday.irforge.data.newId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacrosScreen(app: ForgeApp, nav: NavController) {
    val macros by app.store.macros.collectAsState()
    var editFor by remember { mutableStateOf<Macro?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Macros & scenes") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editFor = Macro(id = newId(), name = "New macro") }) { Icon(Icons.Filled.Add, "New macro") }
        },
    ) { pad ->
        if (macros.isEmpty()) {
            Column(Modifier.padding(pad).fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("🎬", style = MaterialTheme.typography.displayLarge)
                Text("No macros yet")
                Text(
                    "Chain codes and delays into one-tap scenes (e.g. \"Movie night\": dim lights → TV on → HDMI 2).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(macros, key = { it.id }) { m ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(m.icon, Modifier.padding(end = 10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${m.steps.size} steps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { nav.navigate("runmacro/${m.id}") }) { Icon(Icons.Filled.PlayArrow, "Run") }
                            IconButton(onClick = { editFor = m }) { Text("✏️") }
                            IconButton(onClick = { app.store.deleteMacro(m.id) }) { Icon(Icons.Filled.Delete, "Delete") }
                        }
                    }
                }
            }
        }
    }

    editFor?.let { m -> MacroEditorDialog(app, m, onDismiss = { editFor = null }) }
}

@Composable
private fun MacroEditorDialog(app: ForgeApp, macro: Macro, onDismiss: () -> Unit) {
    val codes by app.store.codes.collectAsState()
    var name by remember { mutableStateOf(macro.name) }
    var icon by remember { mutableStateOf(macro.icon) }
    var steps by remember { mutableStateOf(macro.steps) }
    var showCodes by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Macro editor") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = icon, onValueChange = { icon = it.take(4) }, label = { Text("Icon") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("Steps", style = MaterialTheme.typography.labelLarge)
                if (steps.isEmpty()) Text("Add codes or delays below.", style = MaterialTheme.typography.bodySmall)
                steps.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            when (s.type) {
                                "delay" -> "⏱ wait ${s.ms} ms"
                                else -> "🔌 ${codes.firstOrNull { it.id == s.id }?.name ?: "?"}"
                            },
                            Modifier.weight(1f),
                        )
                        if (i > 0) TextButton(onClick = {
                            steps = steps.toMutableList().apply { add(i - 1, removeAt(i)) }
                        }) { Text("↑") }
                        if (i < steps.size - 1) TextButton(onClick = {
                            steps = steps.toMutableList().apply { add(i + 1, removeAt(i)) }
                        }) { Text("↓") }
                        IconButton(onClick = { steps = steps.filterIndexed { idx, _ -> idx != i } }) {
                            Icon(Icons.Filled.Delete, "Remove step")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { showCodes = true }) { Text("➕ Code") }
                    OutlinedButton(onClick = { steps = steps + MacroStep(type = "delay", ms = 300) }) { Text("➕ Delay 300 ms") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                app.store.upsertMacro(macro.copy(name = name.ifBlank { "Macro" }, icon = icon, steps = steps))
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showCodes) {
        ActionPickerDialog(app, forLongPress = false, onDismiss = { showCodes = false }, onPick = { a ->
            if (a.type == "code") steps = steps + MacroStep(type = "code", id = a.id)
            showCodes = false
        })
    }
}
