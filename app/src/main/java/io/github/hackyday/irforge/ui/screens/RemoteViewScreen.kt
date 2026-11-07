package io.github.hackyday.irforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.hackyday.irforge.ForgeApp
import io.github.hackyday.irforge.data.Action
import io.github.hackyday.irforge.data.RButton
import io.github.hackyday.irforge.ui.components.PressFeedback
import io.github.hackyday.irforge.ui.components.RemoteButtonView
import io.github.hackyday.irforge.ui.components.parseColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Canvas is authored at 1080 units wide; scaled to real screen width at render time. */
const val CANVAS_W = 1080f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteViewScreen(app: ForgeApp, nav: NavController, rid: String) {
    val remotes by app.store.remotes.collectAsState()
    val r = remotes.firstOrNull { it.id == rid }
    if (r == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val settings by app.store.settings.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageIdx by remember(rid) { mutableStateOf(0) }
    var txMenu by remember { mutableStateOf(false) }
    var favDialog by remember { mutableStateOf(false) }
    var errDialog by remember { mutableStateOf<String?>(null) }
    var canvasW by remember { mutableStateOf(1080) }

    val page = r.pages.getOrNull(pageIdx) ?: r.pages.firstOrNull() ?: return

    val txLabel by produceState("TX…", rid) {
        value = app.irEngine.pickSink(rid)?.let {
            when (it.key) { "builtin" -> "📱 IR"; "usb" -> "USB IR"; "aux" -> "AUX IR"; else -> "TX" }
        } ?: "No TX"
    }

    suspend fun sendAction(action: Action?) {
        val a = action ?: run {
            errDialog = "No action assigned to this button yet.\n\nTap the pencil (edit) above, select the button, then \"Assign\" a code or macro."
            return
        }
        when (a.type) {
            "code" -> app.store.codeById(a.id)?.let { code ->
                val err = app.irEngine.sendCode(code, repeats = a.repeat.coerceAtLeast(1), remoteId = rid)
                if (err != null) errDialog = err
            } ?: run { errDialog = "This button's code was deleted from the library." }
            "macro" -> app.store.macros.value.firstOrNull { it.id == a.id }?.let { macro ->
                val err = app.irEngine.runMacro(macro, remoteId = rid)
                if (err != null) errDialog = err
            }
        }
    }

    Scaffold { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TopAppBar(
                title = { Text(r.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { favDialog = true }) {
                        Icon(
                            if (page.buttons.any { it.id in r.favorites }) Icons.Filled.Star else Icons.Filled.StarBorder,
                            "Favorites",
                        )
                    }
                    Box {
                        TextButton(onClick = { txMenu = true }) { Text(txLabel) }
                        DropdownMenu(expanded = txMenu, onDismissRequest = { txMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit favorites for widget") },
                                onClick = { txMenu = false; favDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Hardware / transmitter settings") },
                                onClick = { txMenu = false; nav.navigate("hardware") },
                            )
                        }
                    }
                    IconButton(onClick = { nav.navigate("editor/$rid") }) { Icon(Icons.Filled.Edit, "Edit") }
                },
            )
            if (r.pages.size > 1) {
                ScrollableTabRow(selectedTabIndex = pageIdx, edgePadding = 8.dp) {
                    r.pages.forEachIndexed { i, p ->
                        Tab(selected = i == pageIdx, onClick = { pageIdx = i }, text = { Text(p.name) })
                    }
                }
            }
            val density = LocalDensity.current
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .background(Brush.verticalGradient(listOf(parseColor(r.bgStart), parseColor(r.bgEnd, parseColor(r.bgStart)))))
                    .onSizeChanged { canvasW = it.width },
            ) {
                val scale = canvasW.toFloat() / CANVAS_W
                val canvasH = ((page.buttons.maxOfOrNull { it.y + it.h } ?: 600f) + 60f) * scale
                Box(Modifier.fillMaxWidth().height(with(density) { canvasH.toDp() })) {
                    for (b in page.buttons) {
                        RemotePlayButton(
                            button = b,
                            remote = r,
                            scale = scale,
                            settings = settings,
                            onSend = { action -> sendAction(action) },
                            onPressStart = { if (b.haptic) PressFeedback.haptic(ctx, settings) },
                        )
                    }
                }
            }
        }
    }

    errDialog?.let { msg ->
        AlertDialog(
            onDismissRequest = { errDialog = null },
            confirmButton = { TextButton(onClick = { errDialog = null }) { Text("OK") } },
            title = { Text("Transmit") },
            text = { Text(msg) },
        )
    }

    if (favDialog) {
        AlertDialog(
            onDismissRequest = { favDialog = false },
            confirmButton = { TextButton(onClick = { favDialog = false }) { Text("Done") } },
            title = { Text("Widget favorites") },
            text = {
                Column {
                    Text(
                        "Picked buttons appear on the home-screen widget.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    for (b in r.pages.flatMap { p -> p.buttons }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = b.id in r.favorites,
                                onCheckedChange = { on ->
                                    app.store.updateRemote(rid) { rr ->
                                        rr.copy(favorites = if (on) rr.favorites + b.id else rr.favorites - b.id)
                                    }
                                },
                            )
                            Text("${b.icon} ${b.label}")
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun RemotePlayButton(
    button: RButton,
    remote: io.github.hackyday.irforge.data.Remote,
    scale: Float,
    settings: io.github.hackyday.irforge.data.Settings,
    onSend: suspend (Action?) -> Unit,
    onPressStart: () -> Unit,
) {
    val interaction = remember(button.id) { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val wDp = with(density) { (button.w * scale).toDp() }
    val hDp = with(density) { (button.h * scale).toDp() }

    val gestureModifier = if (button.holdRepeat) {
        Modifier.clickable(interactionSource = interaction, indication = null) { /* send handled below */ }
    } else {
        Modifier.pointerInput(button.id, button.action?.id, button.longPress?.id) {
            detectTapGestures(
                onLongPress = {
                    if (button.longPress != null) { onPressStart(); scope.launch { onSend(button.longPress) } }
                },
                onTap = { onPressStart(); scope.launch { onSend(button.action) } },
            )
        }
    }

    Box(
        Modifier
            .offset { androidx.compose.ui.unit.IntOffset((button.x * scale).toInt(), (button.y * scale).toInt()) }
            .size(width = wDp, height = hDp)
            .graphicsLayer { alpha = button.opacity.coerceIn(0.05f, 1f) }
            .then(gestureModifier),
    ) {
        RemoteButtonView(
            button = button,
            remote = remote,
            fontSizePx = button.fontSize * scale,
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (button.holdRepeat && button.action != null) {
        LaunchedEffect(pressed, button.id) {
            if (pressed) {
                onPressStart()
                onSend(button.action)
                while (true) {
                    delay(150)
                    onSend(button.action)
                }
            }
        }
    }
}
