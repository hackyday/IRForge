package io.github.hackyday.irforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.hackyday.irforge.ForgeApp
import io.github.hackyday.irforge.data.Action
import io.github.hackyday.irforge.data.ButtonShape
import io.github.hackyday.irforge.data.Code
import io.github.hackyday.irforge.data.Page
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

private fun starts0(m: Map<String, Pair<Float, Float>>?, id: String): Pair<Float, Float>? = m?.get(id)

private val QUICK_EMOJI = listOf("🔌", "🔊", "🔉", "🔇", "▲", "▼", "◀", "▶", "OK", "⏯", "⏮", "⏭", "🏠", "↩", "☰", "ℹ", "⏱", "❄", "⚡", "💡")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(app: ForgeApp, nav: NavController, rid: String, initialSelect: String = "") {
    val remotes by app.store.remotes.collectAsState()
    val codes by app.store.codes.collectAsState()
    val macros by app.store.macros.collectAsState()
    val r = remotes.firstOrNull { it.id == rid }
    if (r == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    var pageIdx by remember(rid) { mutableStateOf(0) }
    var selectedIds by remember(rid) { mutableStateOf<Set<String>>(if (initialSelect.isNotEmpty()) setOf(initialSelect) else emptySet()) }
    var multiMode by remember(rid) { mutableStateOf(false) }
    var sheetForId by remember(rid) { mutableStateOf(initialSelect.ifEmpty { null }) }
    var dragAdj by remember { mutableStateOf<Map<String, Pair<Float, Float>>?>(null) }
    var dragStarts by remember { mutableStateOf<Map<String, Pair<Float, Float>>?>(null) }
    var dragPrimary by remember { mutableStateOf<String?>(null) }
    var dragMoved by remember { mutableStateOf(false) }
    var dragWasSelected by remember { mutableStateOf(false) }
    var resizeAdj by remember { mutableStateOf<Pair<String, Pair<Float, Float>>?>(null) }
    var gridSnap by remember { mutableStateOf(true) }
    var canvasW by remember { mutableStateOf(1080) }
    var showRemoteSettings by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showPages by remember { mutableStateOf(false) }
    var showGroupSheet by remember { mutableStateOf(false) }
    var showGroupAssign by remember { mutableStateOf(false) }
    var confirmGroupDelete by remember { mutableStateOf(false) }

    val page = r.pages.getOrNull(pageIdx) ?: r.pages.firstOrNull() ?: return
    val sheetButton = page.buttons.firstOrNull { it.id == sheetForId }

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
                selectedIds = setOf(b.id)
            }) { Icon(Icons.Filled.Add, "Add button") }
        },
    ) { pad ->
        androidx.compose.foundation.layout.Box(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Edit · ${r.name}", maxLines = 1) },
                    navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = {
                        IconButton(onClick = {
                            multiMode = !multiMode
                            if (!multiMode) selectedIds = emptySet()
                        }) {
                            Icon(
                                Icons.Filled.DoneAll,
                                "Multi-select",
                                tint = if (multiMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                                onClick = { pageIdx = i; selectedIds = emptySet(); sheetForId = null },
                                text = { Text(p.name) },
                            )
                        }
                    }
                }
                // One-tap bridge from imported codes to this remote's unassigned buttons.
                val unassigned = page.buttons.count { it.action == null }
                val hasExtrasPage = r.pages.any { it.name == "Extras" }
                var autoFillHidden by remember(rid) { mutableStateOf(false) }
                if (codes.isNotEmpty() && unassigned > 0 && !hasExtrasPage && !autoFillHidden) {
                    androidx.compose.material3.Card(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("$unassigned button(s) have no code yet", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "${codes.size} codes in the library",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(onClick = {
                                val (assigned, _) = assignCodesToRemote(r, codes)
                                app.store.upsertRemote(assigned)
                                autoFillHidden = true
                            }) { Text("Auto-fill") }
                            IconButton(onClick = { autoFillHidden = true }) { Icon(Icons.Filled.Close, "Dismiss") }
                        }
                    }
                }

                val density = LocalDensity.current
                var guideV by remember { mutableStateOf<Float?>(null) }
                var guideH by remember { mutableStateOf<Float?>(null) }
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .background(Brush.verticalGradient(listOf(parseColor(r.bgStart), parseColor(r.bgEnd, parseColor(r.bgStart)))))
                        .onSizeChanged { canvasW = it.width },
                ) {
                    val scale = canvasW.toFloat() / CANVAS_W
                    val currentScale by rememberUpdatedState(scale)
                    val currentButtons by rememberUpdatedState(page.buttons)
                    val currentMulti by rememberUpdatedState(multiMode)
                    fun hitTest(ux: Float, uy: Float) = currentButtons.lastOrNull {
                        ux >= it.x && ux <= it.x + it.w && uy >= it.y && uy <= it.y + it.h
                    }

                    /** Magnetic snap for the primary dragged button; guide lines report alignment. */
                    fun snapWithGuides(x: Float, y: Float, w: Float, h: Float): Pair<Float, Float> {
                        var nx = if (gridSnap) snap(x) else x
                        var ny = if (gridSnap) snap(y) else y
                        var gv: Float? = null
                        var gh: Float? = null
                        val tol = 12f
                        val ids = dragStarts?.keys ?: emptySet()
                        val xs = mutableListOf(40f, 540f, 1040f)
                        val ys = mutableListOf(40f)
                        for (o in currentButtons) {
                            if (o.id in ids) continue
                            xs.add(o.x); xs.add(o.x + o.w)
                            ys.add(o.y); ys.add(o.y + o.h)
                        }
                        for (cand in xs) {
                            if (kotlin.math.abs(nx - cand) <= tol) { nx = cand; gv = cand; break }
                            if (kotlin.math.abs(nx + w - cand) <= tol) { nx = cand - w; gv = cand; break }
                        }
                        for (cand in ys) {
                            if (kotlin.math.abs(ny - cand) <= tol) { ny = cand; gh = cand; break }
                            if (kotlin.math.abs(ny + h - cand) <= tol) { ny = cand - h; gh = cand; break }
                        }
                        guideV = gv
                        guideH = gh
                        return nx to ny
                    }

                    val baseMax = page.buttons.maxOfOrNull { b ->
                        val adj = resizeAdj?.takeIf { it.first == b.id }?.second
                        val dpos = dragAdj?.get(b.id)?.second
                        (adj?.second ?: b.h) + (dpos ?: b.y)
                    } ?: 600f
                    val canvasH = (baseMax + 140f) * scale

                    // Canvas surface: ALL gestures live here (one detector, hit-tested),
                    // so per-button recompositions can never kill an active drag.
                    // TAP detector declared first; the DRAG detector below is innermost
                    // and therefore wins the touch-slop race.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(with(density) { canvasH.toDp() })

                    ) {
                        // Grid appears while dragging or resizing.
                        if ((dragAdj != null || resizeAdj != null) && gridSnap) {
                            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                                val step = 40f * scale
                                var gx = 0f
                                while (gx <= size.width) {
                                    drawLine(Color.White.copy(alpha = 0.05f), androidx.compose.ui.geometry.Offset(gx, 0f), androidx.compose.ui.geometry.Offset(gx, size.height), 1f)
                                    gx += step
                                }
                                var gy = 0f
                                while (gy <= size.height) {
                                    drawLine(Color.White.copy(alpha = 0.05f), androidx.compose.ui.geometry.Offset(0f, gy), androidx.compose.ui.geometry.Offset(size.width, gy), 1f)
                                    gy += step
                                }
                            }
                        }
                        guideV?.let { vx ->
                            Box(
                                Modifier
                                    .offset { androidx.compose.ui.unit.IntOffset((vx * scale).toInt(), 0) }
                                    .fillMaxHeight().width(1.5.dp)
                                    .background(Color(0x88FF5252)),
                            )
                        }
                        guideH?.let { hy ->
                            Box(
                                Modifier
                                    .offset { androidx.compose.ui.unit.IntOffset(0, (hy * scale).toInt()) }
                                    .fillMaxWidth().height(1.5.dp)
                                    .background(Color(0x88FF5252)),
                            )
                        }
                        for (b in page.buttons) {
                            key(b.id) {
                                val drag = dragAdj?.get(b.id)
                                val rs = resizeAdj?.takeIf { it.first == b.id }?.second
                                val bx = drag?.first ?: b.x
                                val by = drag?.second ?: b.y
                                val bw = rs?.first ?: b.w
                                val bh = rs?.second ?: b.h
                                val display = b.copy(x = bx, y = by, w = bw, h = bh)
                                val isSel = b.id in selectedIds
                                val bwDp = with(density) { (bw * scale).toDp() }
                                val bhDp = with(density) { (bh * scale).toDp() }

                                Box(
                                    Modifier
                                        .offset { androidx.compose.ui.unit.IntOffset((bx * scale).toInt(), (by * scale).toInt()) }
                                        .size(width = bwDp, height = bhDp)
                                        .border(
                                            2.dp,
                                            if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shapeOf(b.shape),
                                        )
                                        .pointerInput(b.id, page.id) {
                                            // Drag: moves this button, or the whole selection in multi-mode.
                                            detectDragGestures(
                                                onDragStart = {
                                                    dragWasSelected = b.id in selectedIds
                                                    val groupIds = if (multiMode && dragWasSelected) selectedIds else setOf(b.id)
                                                    selectedIds = groupIds
                                                    dragPrimary = b.id
                                                    dragMoved = false
                                                    dragStarts = currentButtons.filter { it.id in groupIds }.associate { it.id to (it.x to it.y) }
                                                },
                                                onDrag = { change, amt ->
                                                    val starts = dragStarts ?: return@detectDragGestures
                                                    val primary = dragPrimary ?: return@detectDragGestures
                                                    val myStart = starts[primary] ?: return@detectDragGestures
                                                    change.consume()
                                                    dragMoved = true
                                                    val live = dragAdj?.get(primary) ?: myStart
                                                    val pc = change.positionChangeIgnoreConsumed()
                                                    val rawX = (live.first + pc.x / scale).coerceIn(0f, 1040f)
                                                    val rawY = (live.second + pc.y / scale).coerceIn(0f, 5000f)
                                                    val pb = page.buttons.first { it.id == primary }
                                                    val (sx, sy) = snapWithGuides(rawX, rawY, pb.w, pb.h)
                                                    val dx = sx - live.first
                                                    val dy = sy - live.second
                                                    dragAdj = starts.mapValues { (id, startPos) ->
                                                        val cur = dragAdj?.get(id) ?: startPos
                                                        if (id == primary) sx to sy
                                                        else (cur.first + dx).coerceIn(0f, 1040f) to (cur.second + dy).coerceIn(0f, 5000f)
                                                    }
                                                },
                                                onDragEnd = {
                                                    if (!dragMoved && multiMode) {
                                                        // Tap without movement in multi-select = toggle
                                                        selectedIds = if (dragWasSelected) selectedIds - b.id else selectedIds + b.id
                                                    }
                                                    if (dragMoved) {
                                                        dragAdj?.let { moves ->
                                                            app.store.updatePage(rid, page.id) { p ->
                                                                p.copy(buttons = p.buttons.map { bb ->
                                                                    moves[bb.id]?.let { bb.copy(x = it.first, y = it.second) } ?: bb
                                                                })
                                                            }
                                                        }
                                                    }
                                                    dragAdj = null
                                                    dragStarts = null
                                                    dragPrimary = null
                                                    guideV = null
                                                    guideH = null
                                                },
                                                onDragCancel = {
                                                    dragAdj = null
                                                    dragStarts = null
                                                    dragPrimary = null
                                                    guideV = null
                                                    guideH = null
                                                },
                                            )
                                        }
                                ) {
                                    RemoteButtonView(
                                        button = display,
                                        remote = r,
                                        fontSizePx = b.fontSize * scale,
                                        showUnassignedBadge = b.action == null,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }

                                // Edit chip: opens the properties sheet for the selected button
                                if (isSel && selectedIds.size == 1) {
                                    androidx.compose.material3.Surface(
                                        onClick = { sheetForId = b.id },
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        shadowElevation = 4.dp,
                                        modifier = Modifier
                                            .offset { androidx.compose.ui.unit.IntOffset(((bx + bw) * scale - 150).toInt().coerceAtLeast(0), ((by - 46) * scale).toInt().coerceAtLeast(0)) },
                                    ) {
                                        Text(
                                            "✏️ Proprietà",
                                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }

                                // Resize handle (single selection only)
                                if (isSel && selectedIds.size == 1) {
                                    Box(
                                        Modifier
                                            .offset { androidx.compose.ui.unit.IntOffset(((bx + bw) * scale - 26).toInt(), ((by + bh) * scale - 26).toInt()) }
                                            .size(26.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            .border(2.dp, Color.White, CircleShape)
                                            .pointerInput(b.id, page.id, gridSnap) {
                                                detectDragGestures(
                                                    onDrag = { change, amt ->
                                                        change.consume()
                                                        val cur = resizeAdj?.takeIf { it.first == b.id }?.second ?: (b.w to b.h)
                                                        val nw = (cur.first + amt.x / scale).coerceAtLeast(60f)
                                                        val nh = (cur.second + amt.y / scale).coerceAtLeast(50f)
                                                        resizeAdj = b.id to ((if (gridSnap) snap(nw) else nw) to (if (gridSnap) snap(nh) else nh))
                                                    },
                                                    onDragEnd = {
                                                        resizeAdj?.takeIf { it.first == b.id }?.let { (_, wh) ->
                                                            app.store.updateButton(rid, page.id, b.id) {
                                                                it.copy(w = wh.first, h = wh.second)
                                                            }
                                                        }
                                                        resizeAdj = null
                                                    },
                                                    onDragCancel = { resizeAdj = null },
                                                )
                                            },
                                    )
                                }
                            }
                        }
                        // Live coordinates while dragging
                        dragAdj?.let { moves ->
                            dragPrimary?.let { pid ->
                                val xy = moves[pid] ?: return@let
                                androidx.compose.material3.Surface(
                                    color = Color(0xCC12131A),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                                ) {
                                    Text(
                                        "x ${xy.first.toInt()}   y ${xy.second.toInt()}",
                                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }

                }

                // Floating multi-select action bar (overlays canvas — no layout shift)
                if (multiMode && selectedIds.isNotEmpty()) {
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(
                                "${selectedIds.size} selected",
                                Modifier.padding(start = 6.dp, end = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            if (selectedIds.size == 1) {
                                fun nudge(dx: Float, dy: Float) {
                                    val id = selectedIds.first()
                                    app.store.updatePage(rid, page.id) { p ->
                                        p.copy(buttons = p.buttons.map { bb ->
                                            if (bb.id == id) bb.copy(x = (bb.x + dx).coerceIn(0f, 1040f), y = (bb.y + dy).coerceIn(0f, 5000f)) else bb
                                        })
                                    }
                                }
                                IconButton(onClick = { nudge(0f, -10f) }) { Text("↑", style = MaterialTheme.typography.labelLarge) }
                                IconButton(onClick = { nudge(0f, 10f) }) { Text("↓", style = MaterialTheme.typography.labelLarge) }
                                IconButton(onClick = { nudge(-10f, 0f) }) { Text("←", style = MaterialTheme.typography.labelLarge) }
                                IconButton(onClick = { nudge(10f, 0f) }) { Text("→", style = MaterialTheme.typography.labelLarge) }
                            }
                            IconButton(onClick = { showGroupSheet = true }) { Icon(Icons.Filled.Palette, "Style selected") }
                            IconButton(onClick = { showGroupAssign = true }) { Icon(Icons.Filled.Link, "Assign code to selected") }
                            IconButton(onClick = { confirmGroupDelete = true }) { Icon(Icons.Filled.Delete, "Delete selected", tint = MaterialTheme.colorScheme.error) }
                            IconButton(onClick = { selectedIds = emptySet(); multiMode = false }) { Icon(Icons.Filled.Close, "Exit multi-select") }
                        }
                    }
                } else if (multiMode) {
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        Text(
                            "Multi-select: tap buttons to add or remove them",
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
            }

            // Group delete confirmation
            if (confirmGroupDelete && selectedIds.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { confirmGroupDelete = false },
                    title = { Text("Delete ${selectedIds.size} button(s)?") },
                    text = { Text("They will be removed from this page. Codes stay in the library.") },
                    confirmButton = {
                        TextButton(onClick = {
                            app.store.updatePage(rid, page.id) { p ->
                                p.copy(buttons = p.buttons.filterNot { it.id in selectedIds })
                            }
                            selectedIds = emptySet()
                            confirmGroupDelete = false
                        }) { Text("Delete", color = Color(0xFFEF5350)) }
                    },
                    dismissButton = { TextButton(onClick = { confirmGroupDelete = false }) { Text("Cancel") } },
                )
            }
        }
    }

    // Selected button property sheet — opened by LONG-PRESS only
    if (sheetButton != null) {
        ButtonPropsSheet(
            app = app,
            rid = rid,
            pageId = page.id,
            button = sheetButton,
            hasCodes = codes.isNotEmpty() || macros.isNotEmpty(),
            onDelete = {
                app.store.removeButton(rid, page.id, sheetButton.id)
                sheetForId = null
                selectedIds = selectedIds - sheetButton.id
            },
            onDismiss = { sheetForId = null },
        )
    }

    // Group (multi-select) property sheet
    if (showGroupSheet && selectedIds.isNotEmpty()) {
        GroupPropsSheet(
            app = app,
            rid = rid,
            pageId = page.id,
            ids = selectedIds,
            first = page.buttons.first { it.id in selectedIds },
            onDismiss = { showGroupSheet = false },
            onAssignClick = { showGroupAssign = true },
            onDeleteClick = { confirmGroupDelete = true },
            onAlign = { mode ->
                val sel = page.buttons.filter { it.id in selectedIds }
                if (sel.isNotEmpty()) {
                    app.store.updatePage(rid, page.id) { p ->
                        p.copy(buttons = p.buttons.map { bb ->
                            if (bb.id !in selectedIds) bb else when (mode) {
                                "left" -> bb.copy(x = sel.minOf { it.x })
                                "right" -> bb.copy(x = sel.maxOf { it.x + it.w } - bb.w)
                                "top" -> bb.copy(y = sel.minOf { it.y })
                                "bottom" -> bb.copy(y = sel.maxOf { it.y + it.h } - bb.h)
                                "size" -> bb.copy(w = sel.first().w, h = sel.first().h)
                                else -> bb
                            }
                        })
                    }
                }
            },
        )
    }

    // Group assign code
    if (showGroupAssign && selectedIds.isNotEmpty()) {
        ActionPickerDialog(
            app = app,
            forLongPress = false,
            onDismiss = { showGroupAssign = false },
            onPick = { action ->
                app.store.updatePage(rid, page.id) { p ->
                    p.copy(buttons = p.buttons.map { if (it.id in selectedIds) it.copy(action = action) else it })
                }
                showGroupAssign = false
            },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = button.x.toInt().toString(),
                    onValueChange = { v ->
                        v.toFloatOrNull()?.let { nx -> app.store.updateButton(rid, pageId, button.id) { it.copy(x = nx.coerceIn(0f, 1040f)) } }
                    },
                    label = { Text("X") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = button.y.toInt().toString(),
                    onValueChange = { v ->
                        v.toFloatOrNull()?.let { ny -> app.store.updateButton(rid, pageId, button.id) { it.copy(y = ny.coerceIn(0f, 5000f)) } }
                    },
                    label = { Text("Y") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
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
                        Text(s.name.lowercase().replaceFirstChar { it.uppercase() }, maxLines = 1, style = MaterialTheme.typography.labelSmall)
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
                Button(onClick = { pickAction = true; pickForLongPress = false }, enabled = hasCodes) { Text("Assign") }
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
                                    rr.copy(pages = rr.pages.filterNot { it.id == p.id })
                                }
                            }) { Icon(Icons.Filled.Delete, "Delete page") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    app.store.updateRemote(remote.id) { rr ->
                        rr.copy(pages = rr.pages + Page(id = newId(), name = "Page ${rr.pages.size + 1}"))
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
    codes: List<Code>,
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
                                    listOf(first.copy(buttons = t.slots.map { slotToButton(it) })) + remote.pages.drop(1)
                                } ?: listOf(Page(id = newId(), buttons = t.slots.map { slotToButton(it) })),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPropsSheet(
    app: ForgeApp,
    rid: String,
    pageId: String,
    ids: Set<String>,
    first: RButton,
    onDismiss: () -> Unit,
    onAssignClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAlign: (String) -> Unit,
) {
    fun editSelected(transform: (RButton) -> RButton) =
        app.store.updatePage(rid, pageId) { p ->
            p.copy(buttons = p.buttons.map { if (it.id in ids) transform(it) else it })
        }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Text("${ids.size} buttons selected", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))

            Text("Alignment", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("left" to "Left", "top" to "Top", "right" to "Right", "bottom" to "Bottom", "size" to "Size").forEach { (mode, label) ->
                    Button(
                        onClick = { onAlign(mode) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                    ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                }
            }
            Spacer(Modifier.height(8.dp))

            Text("Shape", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ButtonShape.entries.forEach { s ->
                    Button(
                        onClick = { editSelected { it.copy(shape = s) } },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = if (first.shape == s) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(s.name.lowercase().replaceFirstChar { it.uppercase() }, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Text("Fill color", style = MaterialTheme.typography.labelMedium)
            ColorSwatchRow(first.fillStart, allowCustom = false) { v ->
                editSelected { it.copy(fillStart = v) }
            }
            Text("Text color", style = MaterialTheme.typography.labelMedium)
            ColorSwatchRow(first.textColor, allowCustom = false) { v ->
                editSelected { it.copy(textColor = v) }
            }

            Text("Opacity", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = first.opacity,
                onValueChange = { v -> editSelected { it.copy(opacity = v) } },
                valueRange = 0.1f..1f,
            )
            Text("Font size", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = first.fontSize,
                onValueChange = { v -> editSelected { it.copy(fontSize = v.coerceIn(12f, 90f)) } },
                valueRange = 12f..90f,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { editSelected { it.copy(haptic = true) } }) { Text("Haptic ON") }
                TextButton(onClick = { editSelected { it.copy(haptic = false) } }) { Text("Haptic OFF") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { editSelected { it.copy(holdRepeat = true) } }) { Text("Hold repeat ON") }
                TextButton(onClick = { editSelected { it.copy(holdRepeat = false) } }) { Text("Hold repeat OFF") }
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onAssignClick) { Text("Assign code to all") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { editSelected { it.copy(action = null) } }) { Text("Clear actions") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDeleteClick) {
                Text("Delete ${ids.size} buttons", color = Color(0xFFEF5350))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
