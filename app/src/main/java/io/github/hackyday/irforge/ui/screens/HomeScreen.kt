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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.hackyday.irforge.ForgeApp
import io.github.hackyday.irforge.data.Remote
import io.github.hackyday.irforge.editor.TEMPLATES
import io.github.hackyday.irforge.editor.blankTemplate
import io.github.hackyday.irforge.editor.gridTemplate
import io.github.hackyday.irforge.editor.templateById

private val DEVICE_ICONS = mapOf(
    "tv" to "📺", "stb" to "📡", "audio" to "🔊", "fan" to "🌀",
    "ac" to "❄", "projector" to "📽", "custom" to "🎮",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(app: ForgeApp, nav: NavController) {
    val remotes by app.store.remotes.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Remote?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("IRForge") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) { Icon(Icons.Filled.Add, "New remote") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (remotes.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("🎮", style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(12.dp))
                    Text("No remotes yet", fontWeight = FontWeight.Bold)
                    Text(
                        "Create one from a template, or import .ir files in the Codes tab.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(remotes, key = { it.id }) { remote ->
                        val buttons = remote.pages.sumOf { it.buttons.size }
                        Card(
                            Modifier.fillMaxWidth().clickable { nav.navigate("remote/${remote.id}") },
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(DEVICE_ICONS[remote.deviceType] ?: "🎮", Modifier.size(40.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(remote.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        "$buttons buttons · ${remote.pages.size} page(s)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { menuFor = remote }) { Icon(Icons.Filled.MoreVert, "Menu") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNew) NewRemoteDialog(app, onDone = { id -> showNew = false; if (id != null) nav.navigate("editor/$id") })
    menuFor?.let { r ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            confirmButton = {},
            title = { Text(r.name) },
            text = {
                Column {
                    TextButton(onClick = { menuFor = null; nav.navigate("editor/${r.id}") }) { Text("✏️ Edit layout") }
                    TextButton(onClick = {
                        menuFor = null
                        val copy = r.copy(
                            id = app.store.newId(),
                            name = r.name + " copy",
                            pages = r.pages.map { p -> p.copy(id = app.store.newId()) },
                        )
                        app.store.upsertRemote(copy)
                    }) { Text("📄 Duplicate") }
                    TextButton(onClick = { menuFor = null; app.store.deleteRemote(r.id) }) {
                        Text("🗑 Delete", color = Color(0xFFEF5350))
                    }
                }
            },
        )
    }
}

@Composable
private fun NewRemoteDialog(app: ForgeApp, onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf("tv") }
    val gridSel = remember { mutableStateOf("3x4") }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("New remote") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Remote name") }, singleLine = true)
                Text("Template", fontWeight = FontWeight.Bold)
                Column {
                    val options = TEMPLATES.map { Triple(it.id, it.icon + " " + it.name, it.id) } +
                        listOf(
                            Triple("grid:3x4", "▦ Grid 3×4", "grid:3x4"),
                            Triple("grid:4x6", "▦ Grid 4×6", "grid:4x6"),
                            Triple("blank", "✨ Blank canvas", "blank"),
                        )
                    options.chunked(2).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            for ((id, label, _) in rowItems) {
                                val isSel = selected == id
                                Card(
                                    Modifier.weight(1f).clickable { selected = id },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                        containerColor = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                ) {
                                    Text(label, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val template = templateById(selected)
                val remote = io.github.hackyday.irforge.editor.buildFromTemplate(template, name.ifBlank { "My remote" })
                app.store.upsertRemote(remote)
                onDone(remote.id)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}
