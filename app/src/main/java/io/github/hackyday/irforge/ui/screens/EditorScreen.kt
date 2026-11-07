package io.github.hackyday.irforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.hackyday.irforge.ForgeApp
import io.github.hackyday.irforge.data.Action
import io.github.hackyday.irforge.data.ButtonShape
import io.github.hackyday.irforge.data.RButton
import io.github.hackyday.irforge.data.newId
import io.github.hackyday.irforge.editor.TEMPLATES
import io.github.hackyday.irforge.editor.assignCodesToRemote
import io.github.hackyday.irforge.editor.slotToButton
import io.github.hackyday.irforge.editor.templateById
import io.github.hackyday.irforge.ui.components.ColorSwatchRow
import io.github.hackyday.irforge.ui.components.RemoteButtonView
import io.github.hackyday.irforge.ui.components.parseColor
import io.github.hackyday.irforge.ui.components.shapeOf

private val QUICK_EMOJI = listOf("🔌", "🔊", "🔉", "🔇", "▲", "▼", "◀", "▶", "OK", "⏯", "⏮", "⏭", "🏠", "↩", "☰", "ℹ", "⏱", "❄", "⚡", "💡")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(app: ForgeApp, nav: NavController, rid: String) {
    val remotes by app.store.remotes.collectAsState()
    val codes by app.store.codes.collectAsState()
    val macros by app.store.macros.collectAsState()
    val r = remotes.firstOrNull { it.id == rid }
    if (r == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    var pageIdx by remember(rid) { mutableStateOf(0) }
    var selectedId by remember(rid) { mutableStateOf<String?>(null) }
    var dragAdj by remember { mutableStateOf<Pair<String, Pair<Float, Float>>?>(null) } // id -> (x,y)
    var resizeAdj by remember { mutableStateOf<Pair<String, Pair<Float, Float>>?>(null) } // id -> (w,h)
    var gridSnap by remember { mutableStateOf(true) }
    var canvasW by remember { mutableStateOf(1080) }
    var showMenu by remember { mutableStateOf(false) }
    var showRemoteSettings by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showPages by remember { mutableStateOf(false) }

    val page = r.pages.getOrNull(pageIdx) ?: r.pages.firstOrNull() ?: return
    val selected = page.buttons.firstOrNull { it.id == selectedId }

    fun snap(v: Float): Float = if (gridSnap) (v / 40f).toInt() * 40f else v

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val maxY = page.buttons.maxOfOrNull { it.y + it.h } ?: 0f
                val b = RButton(
                    id = newId(), label = "BTN", x = 40f,
                    y = if (maxY > 0f) snap(maxY + 40f) else 40f, w = 240f, h = 140f,
                )
                app.store.updatePage(rid, page.id) { it.copy(buttons = it.buttons + b) }
                selectedId = b.id
            }) { Icon(Icons.Filled.Add, "Add button") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TopAppBar(
                title = { Text("Edit · ${r.name}", maxLines = 1) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { gridSnap = !gridSnap }) {
                        Icon(Icons.Filled.GridOn, "Grid snap", tint = if (gridSnap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showPages = true }) { Text("🗂") }
                    IconButton(onClick = { showTemplates = true }) { Text("🧩") }
                    IconButton(onClick = { showRemoteSettings = true }) { Icon(Icons.Filled.Tune, "Remote settings") }
                },
            )
            if (r.pages.size > 1) {
                ScrollableTabRow(selectedTabIndex = pageIdx, edgePadding = 8.dp) {
                    r.pages.forEachIndexed { i, p ->
                        Tab(
                            selected = i == pageIdx,
                            onClick = { pageIdx = i; selectedId = null },
                            text = { Text(p.name) },
                        )
                    }
                }
            }
            val density = LocalDensity.current
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .background(Brush.verticalGradient(listOf(parseColor(r.bgStart), parseColor(r.bgEnd, parseColor(r.bgStart)))))
                    .pointerInput(page.id) {
                        detectTapGestures(onTap = { selectedId = null })
                    }
                    .onSizeChanged { canvasW = it.width },
            ) {
                val scale = canvasW.toFloat() / CANVAS_W
                val baseMax = page.buttons.maxOfOrNull { b ->
                    val adj = resizeAdj?.takeIf { it.first == b.id }?.second
                    (adj?.second ?: b.h) + (dragAdj?.takeIf { it.first == b.id }?.second?.second ?: b.y)
                } ?: 600f
                val canvasH = (baseMax + 140f) * scale
                Box(Modifier.fillMaxWidth().height(with(density) { canvasH.toDp() })) {
                    for (b in page.buttons) {
                        val drag = dragAdj?.takeIf { it.first == b.id }?.second
                        val rs = resizeAdj?.takeIf { it.first == b.id }?.second
                        val bx = drag?.first ?: b.x
                        val by = drag?.second ?: b.y
                        val bw = rs?.first ?: b.w
                        val bh = rs?.second ?: b.h
                        val display = b.copy(x = bx, y = by, w = bw, h = bh)
                        val isSel = b.id == selectedId
                        val bwDp = with(density) { (bw * scale).toDp() }
                        val bhDp = with(density) { (bh * scale).toDp() }

                        Box(
                            Modifier
                                .offset { androidx.compose.ui.unit.IntOffset((bx * scale).toInt(), (by * scale).toInt()) }
                                .size(width = bwDp, height = bhDp)
                                .then(
                                    if (isSel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shapeOf(b.shape))
                                    else Modifier,
                                )
                                .pointerInput(b.id, page.id, gridSnap) {
                                    detectDragGestures(
                                        onDragStart = { selectedId = b.id },
                                        onDrag = { change, amt ->
                                            change.consume()
                                            val cur = dragAdj?.takeIf { it.first == b.id }?.second ?: (b.x to b.y)
                                            dragAdj = b.id to ((cur.first + amt.x / scale) to (cur.second + amt.y / scale))
                                        },
                                        onDragEnd = {
                                            dragAdj?.takeIf { it.first == b.id }?.let { (_, xy) ->
                                                app.store.updateButton(rid, page.id, b.id) {
                                                    it.copy(x = snap(xy.first).coerceAtLeast(0f), y = snap(xy.second).coerceAtLeast(0f))
                                                }
                                            }
                                            dragAdj = null
                                        },
                                    )
                                }
                                .clickable { selectedId = b.id },
                        ) {
                            RemoteButtonView(button = display, remote = r, fontSizePx = b.fontSize * scale, modifier = Modifier.fillMaxSize())
                        }

                        // Resize handle (bottom-right of the selected button)
                        if (isSel) {
                            Box(
                                Modifier
                                    .offset { androidx.compose.ui.unit.IntOffset(((bx + bw) * scale - 18).toInt(), ((by + bh) * scale - 18).toInt()) }
                                    .size(20.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .pointerInput(b.id, page.id, gridSnap) {
                                        detectDragGestures(
                                            onDrag = { change, amt ->
                                                change.consume()
                                                val cur = resizeAdj?.takeIf { it.first == b.id }?.second ?: (b.w to b.h)
                                                resizeAdj = b.id to ((cur.first + amt.x / scale).coerceAtLeast(60f) to (cur.second + amt.y / scale).coerceAtLeast(50f))
                                            },
                                            onDragEnd = {
                                                resizeAdj?.takeIf { it.first == b.id }?.let { (_, wh) ->
                                                    app.store.updateButton(rid, page.id, b.id) {
                                                        it.copy(w = snap(wh.first), h = snap(wh.second))
                                                    }
                                                }
                                                resizeAdj = null
                                            },
                                        )
                                    },
                            )
                        }
                    }
                }
            }
        }
    }

    // Selected button property sheet
    if (selected != null) {
        ButtonPropsSheet(
            app = app,
            rid = rid,
            pageId = page.id,
            button = selected,
            hasCodes = codes.isNotEmpty() || macros.isNotEmpty(),
            onDelete = {
                app.store.removeButton(rid, page.id, selected.id)
                selectedId = null
            },
            onDismiss = { selectedId = null },
        )
    }

    if (showRemoteSettings && r != null) {
        RemoteSettingsDialog(app, r, onDismiss = { showRemoteSettings = false })
    }
    if (showPages) {
        PagesDialog(app, r, onDismiss = { showPages = false }, onSwitch = { i -> pageIdx = i })
    }
    if (showTemplates) {
        TemplateDialog(app, r, codes, onDismiss = { showTemplates = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ButtonPropsSheet(
    app: ForgeApp,
    rid: String,
    pageId: String,
    button: RButton,
    hasCodes: Boolean,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickAction by remember { mutableStateOf(false) }
    var pickForLongPress by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Text("Button properties", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = button.label,
                onValueChange = { v -> app.store.updateButton(rid, pageId, button.id) { it.copy(label = v) } },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text("Icon / emoji", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = button.icon,
                onValueChange = { v -> app.store.updateButton(rid, pageId, button.id) { it.copy(icon = v.take(4)) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QUICK_EMOJI.forEach { e ->
                    Text(
                        e,
                        Modifier
                            .clickable { app.store.updateButton(rid, pageId, button.id) { it.copy(icon = e) } }
                            .padding(4.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Shape", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ButtonShape.entries.forEach { s ->
                    Button(
                        onClick = { app.store.updateButton(rid, pageId, button.id) { it.copy(shape = s) } },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = if (button.shape == s) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(
                            s.name.lowercase().replaceFirstChar { it.uppercase() },
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Fill color", style = MaterialTheme.typography.labelMedium)
            ColorSwatchRow(button.fillStart) { v ->
                app.store.updateButton(rid, pageId, button.id) { it.copy(fillStart = v) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = button.gradient,
                    onCheckedChange = { v -> app.store.updateButton(rid, pageId, button.id) { it.copy(gradient = v) } },
                )
                Text("  Gradient (second color below)")
            }
            if (button.gradient) {
                ColorSwatchRow(button.fillEnd) { v ->
                    app.store.updateButton(rid, pageId, button.id) { it.copy(fillEnd = v) }
                }
            }
            Text("Text color", style = MaterialTheme.typography.labelMedium)
            ColorSwatchRow(button.textColor, allowCustom = false) { v ->
                app.store.updateButton(rid, pageId, button.id) { it.copy(textColor = v) }
            }
            Text("Opacity", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = button.opacity,
                onValueChange = { v -> app.store.updateButton(rid, pageId, button.id) { it.copy(opacity = v) } },
                valueRange = 0.1f..1f,
            )
            Text("Font size", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = button.fontSize,
                onValueChange = { v -> app.store.updateButton(rid, pageId, button.id) { it.copy(fontSize = v.coerceIn(12f, 90f)) } },
                valueRange = 12f..90f,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = button.haptic,
                    onCheckedChange = { v -> app.store.updateButton(rid, pageId, button.id) { it.copy(haptic = v) } },
                )
                Text("  Haptic feedback")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = button.holdRepeat,
                    onCheckedChange = { v -> app.store.updateButton(rid, pageId, button.id) { it.copy(holdRepeat = v) } },
                )
                Text("  Repeat while held (volume etc.)")
            }
            Spacer(Modifier.height(4.dp))
            Text("Action", style = MaterialTheme.typography.labelMedium)
            val actionDesc = when (button.action?.type) {
                "code" -> app.store.codeById(button.action.id)?.name?.let { "IR code: $it" } ?: "missing code"
                "macro" -> app.store.macros.value.firstOrNull { it.id == button.action.id }?.let { "Macro: ${it.name}" } ?: "missing macro"
                else -> "none assigned"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(actionDesc, Modifier.weight(1f))
                if (button.action != null) {
                    TextButton(onClick = {
                        app.store.updateButton(rid, pageId, button.id) { it.copy(action = null) }
                    }) { Text("None") }
                }
                Button(onClick = { pickAction = true }, enabled = hasCodes) { Text("Assign") }
            }
            Text("Repeats: ${button.action?.repeat ?: 1}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = (button.action?.repeat ?: 1).toFloat(),
                onValueChange = { v ->
                    val rep = v.toInt().coerceAtLeast(1)
                    app.store.updateButton(rid, pageId, button.id) { b ->
                        b.copy(action = b.action?.copy(repeat = rep) ?: Action("code", "", rep))
                    }
                },
                valueRange = 1f..10f,
                steps = 8,
            )
            Text("Long-press action", style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (button.longPress?.type) {
                        "code" -> app.store.codeById(button.longPress.id)?.name ?: "IR code"
                        "macro" -> app.store.macros.value.firstOrNull { it.id == button.longPress.id }?.name ?: "Macro"
                        else -> "none"
                    },
                    Modifier.weight(1f),
                )
                if (button.longPress != null) {
                    TextButton(onClick = {
                        app.store.updateButton(rid, pageId, button.id) { it.copy(longPress = null) }
                    }) { Text("None") }
                }
                Button(onClick = { pickAction = true; pickForLongPress = true }) { Text("Assign") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onDelete) { Text("🗑 Delete button", color = Color(0xFFEF5350)) }
                TextButton(onClick = {
                    val b = button.copy(id = newId(), x = button.x + 40f, y = button.y + 40f)
                    app.store.updatePage(rid, pageId) { it.copy(buttons = it.buttons + b) }
                }) { Text("⧉ Duplicate") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickAction) {
        ActionPickerDialog(
            app = app,
            forLongPress = pickForLongPress,
            onDismiss = { pickAction = false; pickForLongPress = false },
            onPick = { action ->
                if (pickForLongPress) {
                    app.store.updateButton(rid, pageId, button.id) { it.copy(longPress = action) }
                    pickForLongPress = false
                } else {
                    app.store.updateButton(rid, pageId, button.id) { it.copy(action = action) }
                }
                pickAction = false
            },
        )
    }
}

@Composable
fun ActionPickerDialog(app: ForgeApp, forLongPress: Boolean, onDismiss: () -> Unit, onPick: (Action) -> Unit) {
    val codes by app.store.codes.collectAsState()
    val macros by app.store.macros.collectAsState()
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (forLongPress) "Assign long-press action" else "Assign action") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { tab = 0 }) { Text("Codes (${codes.size})") }
                    Button(onClick = { tab = 1 }) { Text("Macros (${macros.size})") }
                }
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") }, singleLine = true)
                Column(Modifier.height(320.dp).verticalScroll(rememberScrollState())) {
                    if (tab == 0) {
                        for (c in codes.filter { it.name.contains(query, true) }) {
                            Text(
                                "🔌 ${c.name} · ${c.freqHz / 1000} kHz · ${c.pattern.size} vals",
                                Modifier.fillMaxWidth().clickable { onPick(Action("code", c.id)) }.padding(vertical = 8.dp),
                            )
                        }
                    } else {
                        for (m in macros.filter { it.name.contains(query, true) }) {
                            Text(
                                "${m.icon} ${m.name} · ${m.steps.size} steps",
                                Modifier.fillMaxWidth().clickable { onPick(Action("macro", m.id)) }.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RemoteSettingsDialog(app: ForgeApp, remote: io.github.hackyday.irforge.data.Remote, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(remote.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remote settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("Background color", style = MaterialTheme.typography.labelMedium)
                ColorSwatchRow(remote.bgStart) { v ->
                    app.store.updateRemote(remote.id) { it.copy(bgStart = v) }
                }
                Text("Background gradient end", style = MaterialTheme.typography.labelMedium)
                ColorSwatchRow(remote.bgEnd) { v ->
                    app.store.updateRemote(remote.id) { it.copy(bgEnd = v) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                app.store.updateRemote(remote.id) { it.copy(name = name.ifBlank { it.name }) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PagesDialog(app: ForgeApp, remote: io.github.hackyday.irforge.data.Remote, onDismiss: () -> Unit, onSwitch: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pages") },
        text = {
            Column {
                remote.pages.forEachIndexed { i, p ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("${i + 1}. ${p.name} (${p.buttons.size})", Modifier.weight(1f).clickable { onSwitch(i); onDismiss() })
                        if (remote.pages.size > 1) {
                            IconButton(onClick = {
                                app.store.updateRemote(remote.id) { rr ->
                                    val pages = rr.pages.filterNot { it.id == p.id }
                                    rr.copy(pages = pages)
                                }
                            }) { Icon(Icons.Filled.Delete, "Delete page") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    app.store.updateRemote(remote.id) { rr ->
                        rr.copy(pages = rr.pages + io.github.hackyday.irforge.data.Page(id = newId(), name = "Page ${rr.pages.size + 1}"))
                    }
                }) { Text("➕ Add page") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun TemplateDialog(
    app: ForgeApp,
    remote: io.github.hackyday.irforge.data.Remote,
    codes: List<io.github.hackyday.irforge.data.Code>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apply template") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Replaces page 1 of this remote with the template layout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val options = TEMPLATES.map { it.id to (it.icon + " " + it.name) } +
                    listOf("grid:3x4" to "▦ Grid 3×4", "grid:4x6" to "▦ Grid 4×6")
                for ((id, label) in options) {
                    Text(
                        label,
                        Modifier.fillMaxWidth().clickable {
                            val t = templateById(id)
                            val rebuilt = remote.copy(
                                deviceType = t.deviceType,
                                bgStart = t.bgStart,
                                bgEnd = t.bgEnd,
                                pages = remote.pages.firstOrNull()?.let { first ->
                                    listOf(first.copy(name = first.name, buttons = t.slots.map { slotToButton(it) })) +
                                        remote.pages.drop(1)
                                } ?: listOf(io.github.hackyday.irforge.data.Page(id = newId(), buttons = t.slots.map { slotToButton(it) })),
                            )
                            val (assigned, _) = if (codes.isNotEmpty()) assignCodesToRemote(rebuilt, codes) else rebuilt to 0
                            app.store.upsertRemote(assigned)
                            onDismiss()
                        }.padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
