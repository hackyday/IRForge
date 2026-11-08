package io.github.hackyday.irforge.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.navigation.NavController
import io.github.hackyday.irforge.ForgeApp
import io.github.hackyday.irforge.data.Code
import io.github.hackyday.irforge.data.newId
import io.github.hackyday.irforge.editor.buildFromTemplate
import io.github.hackyday.irforge.editor.templateById
import io.github.hackyday.irforge.importer.ImportedCode
import io.github.hackyday.irforge.importer.Importers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun ImportCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, Modifier.padding(end = 14.dp), style = MaterialTheme.typography.headlineMedium)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

data class ImportCandidate(
    val name: String,
    val freqHz: Int,
    val pattern: List<Int>,
    val format: String,
    val duplicate: Boolean,
    var selected: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(app: ForgeApp, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val codes by app.store.codes.collectAsState()
    var candidates by remember { mutableStateOf<List<ImportCandidate>>(emptyList()) }
    var autoRemote by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var renameIdx by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }

    fun readText(uri: Uri): String? = runCatching {
        ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

    fun parseUris(uris: List<Uri>) {
        busy = true
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                uris.flatMap { uri ->
                    val name0 = uri.lastPathSegment?.substringAfterLast('/') ?: "signal"
                    val text = readText(uri) ?: return@flatMap emptyList<ImportedCode>()
                    Importers.parse(name0, text).map { it.copy(name = it.name.ifBlank { name0.removeSuffix(".ir") }) }
                }
            }
            candidates = parsed.map { c ->
                ImportCandidate(
                    c.name, c.freqHz, c.pattern, c.sourceFormat,
                    duplicate = codes.any { it.pattern == c.pattern },
                )
            }
            busy = false
            if (parsed.isEmpty()) snackbar.showSnackbar("No IR signals found in the selected files")
        }
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) parseUris(uris)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                buildList {
                    val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, uri)
                    root?.listFiles()?.filter { it.isFile }?.forEach { f ->
                        val n = f.name ?: return@forEach
                        if (n.endsWith(".ir", true) || n.endsWith(".conf", true) || n.endsWith(".hex", true) ||
                            n.endsWith(".csv", true) || n.endsWith(".json", true) || n.endsWith(".txt", true)
                        ) {
                            val text = readText(f.uri) ?: return@forEach
                            addAll(Importers.parse(n, text).map { it.copy(name = it.name.ifBlank { n.removeSuffix(".ir") }) })
                        }
                    }
                }
            }
            candidates = found.map { c ->
                ImportCandidate(c.name, c.freqHz, c.pattern, c.sourceFormat, duplicate = codes.any { it.pattern == c.pattern })
            }
            busy = false
            if (found.isEmpty()) snackbar.showSnackbar("No importable IR files found in that folder")
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TopAppBar(title = { Text("Import .ir files") })
            if (candidates.isEmpty()) {
                Text(
                    "Step 1 — choose where your codes come from",
                    style = MaterialTheme.typography.titleSmall,
                )
                ImportCard("📁", "Pick files", "One or more .ir / LIRC / Pronto / CSV / JSON files") {
                    pickFiles.launch(arrayOf("*/*"))
                }
                ImportCard("🗂", "Pick a folder", "Batch-import every IR file inside a folder") {
                    pickFolder.launch(null)
                }
                ImportCard("🧞", "SmartLim demo", "16 ready-made buttons — try the app in one tap") {
                    scope.launch {
                        val (n, remoteId) = io.github.hackyday.irforge.importer.DemoImporter.load(ctx, app.store)
                        snackbar.showSnackbar(if (n > 0) "Demo imported: $n new codes" else "Demo codes were already in your library")
                        remoteId?.let { id -> nav.navigate("editor/$id") }
                    }
                }
                Text(
                    "Tip: you can also share a .ir file to IRForge from any file manager.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Step 2 — review & import",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${candidates.count { it.selected }} of ${candidates.size} selected",
                    style = MaterialTheme.typography.labelLarge,
                )
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    candidates.forEachIndexed { i, c ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = c.selected,
                                    onCheckedChange = { on -> candidates = candidates.toMutableList().also { it[i] = c.copy(selected = on) } },
                                )
                                Column(Modifier.weight(1f).clickable { renameIdx = i; renameText = c.name }) {
                                    Text(c.name, maxLines = 1)
                                    Text(
                                        "${c.freqHz / 1000} kHz · ${c.pattern.size} vals · ${c.format}" +
                                            if (c.duplicate) " · ⚠ already in library" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (c.duplicate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = autoRemote, onCheckedChange = { autoRemote = it })
                            Column {
                                Text("Also create a remote with these codes", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Codes are matched to the template buttons automatically",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Button(
                            onClick = {
                                val sel = candidates.filter { it.selected }
                                val savedList = ArrayList<Code>(sel.size)
                                for (c in sel) {
                                    val code = Code(
                                        id = newId(), name = c.name, freqHz = c.freqHz,
                                        pattern = c.pattern, source = "import", sourceFormat = c.format,
                                        createdAt = System.currentTimeMillis(),
                                    )
                                    app.store.upsertCode(code)
                                    savedList.add(code)
                                }
                                var newRemoteId: String? = null
                                if (autoRemote && savedList.isNotEmpty()) {
                                    val remote = buildFromTemplate(templateById("tv"), savedList.first().name.ifBlank { "Imported remote" })
                                    val (assigned, _) = io.github.hackyday.irforge.editor.assignCodesToRemote(remote, savedList)
                                    app.store.upsertRemote(assigned)
                                    newRemoteId = assigned.id
                                }
                                candidates = emptyList()
                                scope.launch { snackbar.showSnackbar("Imported ${sel.size} codes") }
                                newRemoteId?.let { id -> nav.navigate("editor/$id") }
                            },
                            enabled = candidates.any { it.selected },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Import ${candidates.count { it.selected }} codes") }
                        TextButton(onClick = { candidates = emptyList() }, modifier = Modifier.fillMaxWidth()) {
                            Text("← Choose different files")
                        }
                    }
                }
            }
            if (busy) Text("Parsing…", color = MaterialTheme.colorScheme.secondary)
        }
    }

    renameIdx?.let { i ->
        AlertDialog(
            onDismissRequest = { renameIdx = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    candidates = candidates.toMutableList().also { it[i] = it[i].copy(name = renameText) }
                    renameIdx = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameIdx = null }) { Text("Cancel") } },
        )
    }
}
