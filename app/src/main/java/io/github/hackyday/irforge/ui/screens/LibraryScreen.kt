package io.github.hackyday.irforge.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import io.github.hackyday.irforge.ForgeApp
import io.github.hackyday.irforge.data.Code
import io.github.hackyday.irforge.importer.Importers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(app: ForgeApp, nav: NavController) {
    val codes by app.store.codes.collectAsState()
    val remotes by app.store.remotes.collectAsState()
    var query by remember { mutableStateOf("") }
    var renameFor by remember { mutableStateOf<Code?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameTag by remember { mutableStateOf("") }
    var tagFor by remember { mutableStateOf<Code?>(null) }
    var tagText by remember { mutableStateOf("") }
    var addToRemoteFor by remember { mutableStateOf<Code?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Code library (${codes.size})") },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                                    val f = File(dir, "irforge_library.ir")
                                    f.writeText(Importers.exportFlipper(codes.map { Triple(it.name, it.freqHz, it.pattern) }))
                                    FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                                }.getOrNull()
                            }
                            if (uri == null) {
                                snackbar.showSnackbar("Export failed")
                            } else {
                                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, "Export .ir"))
                            }
                        }
                    }) { Icon(Icons.Filled.Share, "Export all as .ir") }
                },
            )
        },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { nav.navigate("import") },
                icon = { Icon(Icons.Filled.NoteAdd, null) },
                text = { Text("Import .ir files") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search codes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(codes.filter { it.name.contains(query, true) || it.tag.contains(query, true) }, key = { it.id }) { c ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    buildString {
                                        append("${c.freqHz / 1000} kHz · ${c.pattern.size} values · ${c.sourceFormat}")
                                        if (c.tag.isNotEmpty()) append(" · #${c.tag}")
                                        if (c.protocol != null) append(" · ${c.protocol}")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { addToRemoteFor = c }) { Icon(Icons.Filled.PlaylistAdd, "Add to a remote") }
                            IconButton(onClick = {
                                scope.launch {
                                    val err = app.irEngine.sendCode(c)
                                    if (err != null) snackbar.showSnackbar(err)
                                }
                            }) { Icon(Icons.AutoMirrored.Filled.Send, "Test send") }
                            IconButton(onClick = { renameFor = c; renameText = c.name; renameTag = c.tag }) { Icon(Icons.Filled.Edit, "Rename / tag") }
                            IconButton(onClick = { app.store.deleteCode(c.id) }) { Icon(Icons.Filled.Delete, "Delete") }
                        }
                    }
                }
            }
        }
    }

    addToRemoteFor?.let { code ->
        AlertDialog(
            onDismissRequest = { addToRemoteFor = null },
            title = { Text("Add \"${code.name}\" to a remote") },
            text = {
                Column {
                    if (remotes.isEmpty()) {
                        Text("No remotes yet — create one first (Remotes tab → +).")
                    } else {
                        Text(
                            "It becomes a new button on the remote's first page:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        for (r in remotes) {
                            Text(
                                "${r.name}  (${r.pages.firstOrNull()?.buttons?.size ?: 0} buttons)",
                                Modifier.fillMaxWidth().clickable {
                                    val page = r.pages.firstOrNull() ?: return@clickable
                                    val maxY = page.buttons.maxOfOrNull { it.y + it.h } ?: 0f
                                    val idx = page.buttons.size
                                    val b = io.github.hackyday.irforge.data.RButton(
                                        id = app.store.newId(),
                                        label = code.name.take(14),
                                        x = 40f + (idx % 3) * 340f,
                                        y = if (maxY > 0f) maxY + 40f else 40f,
                                        w = 300f, h = 160f,
                                        action = io.github.hackyday.irforge.data.Action(type = "code", id = code.id),
                                    )
                                    app.store.updatePage(r.id, page.id) { it.copy(buttons = it.buttons + b) }
                                    addToRemoteFor = null
                                    scope.launch { snackbar.showSnackbar("Added to \"${r.name}\" — Main page") }
                                }.padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { addToRemoteFor = null }) { Text("Cancel") } },
        )
    }

    renameFor?.let { c ->
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text("Rename / tag") },
            text = {
                Column {
                    OutlinedTextField(value = renameText, onValueChange = { renameText = it }, label = { Text("Name") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = renameTag, onValueChange = { renameTag = it }, label = { Text("Device tag (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    app.store.upsertCode(c.copy(name = renameText.ifBlank { c.name }, tag = renameTag)); renameFor = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameFor = null }) { Text("Cancel") } },
        )
    }
    tagFor?.let { c ->
        AlertDialog(
            onDismissRequest = { tagFor = null },
            title = { Text("Device tag") },
            text = { OutlinedTextField(value = tagText, onValueChange = { tagText = it }, label = { Text("e.g. Living room TV") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { app.store.upsertCode(c.copy(tag = tagText)); tagFor = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { tagFor = null }) { Text("Cancel") } },
        )
    }
}
