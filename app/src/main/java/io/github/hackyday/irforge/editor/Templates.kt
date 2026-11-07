package io.github.hackyday.irforge.editor

import io.github.hackyday.irforge.data.Action
import io.github.hackyday.irforge.data.ButtonShape
import io.github.hackyday.irforge.data.Code
import io.github.hackyday.irforge.data.Page
import io.github.hackyday.irforge.data.RButton
import io.github.hackyday.irforge.data.Remote
import io.github.hackyday.irforge.data.newId

/** Named button slots a template places on the canvas (canvas width = 1080 units). */
data class Slot(
    val key: String,
    val label: String,
    val icon: String,
    val x: Float, val y: Float, val w: Float, val h: Float,
    val fill: String,
    val textColor: String = "#FFFFFF",
    val fontSize: Float = 34f,
    val shape: ButtonShape = ButtonShape.ROUNDED,
)

data class Template(
    val id: String,
    val name: String,
    val icon: String,
    val deviceType: String,
    val bgStart: String,
    val bgEnd: String,
    val slots: List<Slot>,
)

private fun dpad(cx: Float, cy: Float, w: Float = 200f, h: Float = 140f, gap: Float = 20f) = listOf(
    Slot("up", "Up", "▲", cx, cy - h - gap, w, h, "#2B2E3A"),
    Slot("left", "Left", "◀", cx - w - gap, cy, w, h, "#2B2E3A"),
    Slot("ok", "OK", "", cx, cy, w, h, "#FF5252"),
    Slot("right", "Right", "▶", cx + w + gap, cy, w, h, "#2B2E3A"),
    Slot("down", "Down", "▼", cx, cy + h + gap, w, h, "#2B2E3A"),
)

private fun volRow(x: Float, y: Float) = listOf(
    Slot("vol_up", "Vol +", "🔊", x, y, 220f, 140f, "#2B2E3A"),
    Slot("vol_down", "Vol -", "🔉", x, y + 160f, 220f, 140f, "#2B2E3A"),
    Slot("mute", "Mute", "🔇", x + 240f, y + 80f, 220f, 140f, "#2B2E3A"),
)

private fun chRow(x: Float, y: Float) = listOf(
    Slot("ch_up", "Ch +", "⏶", x, y, 220f, 140f, "#2B2E3A"),
    Slot("ch_down", "Ch -", "⏷", x, y + 160f, 220f, 140f, "#2B2E3A"),
)

private val RED = "#C62828"

private fun powerSlot() = Slot("power", "Power", "⏻", 40f, 40f, 200f, 140f, RED)

val TEMPLATES: List<Template> = listOf(
    Template(
        "tv", "TV", "📺", "tv", "#101216", "#181B22",
        listOf(
            powerSlot(), Slot("home", "Home", "🏠", 40f, 900f, 220f, 140f, "#2B2E3A"),
            Slot("back", "Back", "↩", 280f, 900f, 220f, 140f, "#2B2E3A"),
            Slot("menu", "Menu", "☰", 520f, 900f, 220f, 140f, "#2B2E3A"),
            Slot("info", "Info", "ℹ", 760f, 900f, 260f, 140f, "#2B2E3A"),
        ) + volRow(280f, 40f) + chRow(780f, 40f) + dpad(440f, 560f)
    ),
    Template(
        "stb", "Set-top box", "📡", "stb", "#0E1420", "#141B2A",
        listOf(
            powerSlot(), Slot("guide", "Guide", "📰", 40f, 220f, 200f, 140f, "#2B2E3A"),
            Slot("menu", "Menu", "☰", 40f, 900f, 220f, 140f, "#2B2E3A"),
            Slot("exit", "Exit", "✕", 280f, 900f, 220f, 140f, "#2B2E3A"),
            Slot("info", "Info", "ℹ", 520f, 900f, 220f, 140f, "#2B2E3A"),
            Slot("back", "Back", "↩", 760f, 900f, 260f, 140f, "#2B2E3A"),
        ) + volRow(280f, 40f) + chRow(780f, 40f) + dpad(440f, 560f)
    ),
    Template(
        "audio", "Audio / Receiver", "🔊", "audio", "#141018", "#1C1622",
        listOf(
            powerSlot(),
            Slot("vol_up", "Vol +", "🔊", 280f, 40f, 220f, 140f, "#2B2E3A"),
            Slot("vol_down", "Vol -", "🔉", 280f, 220f, 220f, 140f, "#2B2E3A"),
            Slot("mute", "Mute", "🔇", 520f, 130f, 220f, 140f, "#2B2E3A"),
            Slot("play", "Play/Pause", "⏯", 40f, 420f, 300f, 150f, "#2B2E3A"),
            Slot("prev", "Prev", "⏮", 360f, 420f, 300f, 150f, "#2B2E3A"),
            Slot("next", "Next", "⏭", 680f, 420f, 300f, 150f, "#2B2E3A"),
            Slot("source", "Source", "🖴", 40f, 620f, 460f, 140f, "#2B2E3A"),
            Slot("eq", "EQ", "🎚", 520f, 620f, 460f, 140f, "#2B2E3A"),
        )
    ),
    Template(
        "fan", "Fan", "🌀", "fan", "#0E1614", "#141F1C",
        listOf(
            powerSlot(),
            Slot("speed", "Speed", "⚡", 280f, 40f, 220f, 140f, "#2B2E3A"),
            Slot("osc", "Swing", "↔", 520f, 40f, 220f, 140f, "#2B2E3A"),
            Slot("timer", "Timer", "⏱", 760f, 40f, 260f, 140f, "#2B2E3A"),
            Slot("mode", "Mode", "🅼", 40f, 220f, 220f, 140f, "#2B2E3A"),
            Slot("light", "Light", "💡", 280f, 220f, 220f, 140f, "#2B2E3A"),
        )
    ),
    Template(
        "projector", "Projector", "📽", "projector", "#161012", "#1F161A",
        listOf(
            powerSlot(), Slot("input", "Input", "🖜", 280f, 40f, 220f, 140f, "#2B2E3A"),
            Slot("menu", "Menu", "☰", 40f, 940f, 220f, 140f, "#2B2E3A"),
            Slot("exit", "Exit", "✕", 280f, 940f, 220f, 140f, "#2B2E3A"),
        ) + volRow(760f, 40f) + dpad(440f, 560f)
    ),
    Template(
        "ac", "Air conditioner", "❄", "ac", "#0C1418", "#121D23",
        listOf(
            powerSlot(),
            Slot("temp_up", "Temp +", "🌡+", 280f, 40f, 220f, 160f, "#2B2E3A"),
            Slot("temp_down", "Temp -", "🌡-", 280f, 220f, 220f, 160f, "#2B2E3A"),
            Slot("display", "Display", "[float]", 520f, 40f, 220f, 160f, "#2B2E3A"),
            Slot("mode", "Mode", "🅼", 40f, 220f, 220f, 160f, "#2B2E3A"),
            Slot("fan", "Fan", "⚡", 760f, 40f, 260f, 160f, "#2B2E3A"),
            Slot("swing", "Swing", "↕", 760f, 220f, 260f, 160f, "#2B2E3A"),
            Slot("turbo", "Turbo", "💨", 40f, 440f, 220f, 140f, "#2B2E3A"),
            Slot("sleep", "Sleep", "🌙", 280f, 440f, 220f, 140f, "#2B2E3A"),
            Slot("timer", "Timer", "⏱", 520f, 440f, 220f, 140f, "#2B2E3A"),
            Slot("eco", "Eco", "🌱", 760f, 440f, 260f, 140f, "#2B2E3A"),
        )
    ),
)

/** Generic numbered grids for any device. */
fun gridTemplate(cols: Int, rows: Int): Template {
    val slots = ArrayList<Slot>()
    val bw = (1000f / cols) - 30f
    val bh = 150f
    for (r in 0 until rows) for (c in 0 until cols) {
        val n = r * cols + c
        slots.add(Slot("b$n", "${n + 1}", "", 40f + c * (bw + 30f), 40f + r * (bh + 30f), bw, bh, "#2B2E3A"))
    }
    return Template("grid", "Grid ${cols}×$rows", "▦", "custom", "#101216", "#181B22", slots)
}

fun blankTemplate() = Template("blank", "Blank canvas", "✨", "custom", "#101216", "#181B22", emptyList())

fun templateById(id: String): Template = when {
    id.startsWith("grid:") -> {
        val (c, r) = id.removePrefix("grid:").split("x").map { it.toIntOrNull() ?: 3 }
        gridTemplate(c.coerceIn(1, 6), r.coerceIn(1, 8))
    }
    else -> TEMPLATES.firstOrNull { it.id == id } ?: blankTemplate()
}

fun slotToButton(slot: Slot): RButton = RButton(
    id = newId(),
    label = slot.label,
    icon = slot.icon,
    x = slot.x, y = slot.y, w = slot.w, h = slot.h,
    fillStart = slot.fill,
    fillEnd = slot.fill,
    textColor = slot.textColor,
    fontSize = slot.fontSize,
    shape = slot.shape,
)

fun buildFromTemplate(template: Template, name: String): Remote = Remote(
    id = newId(),
    name = name,
    deviceType = template.deviceType,
    bgStart = template.bgStart,
    bgEnd = template.bgEnd,
    pages = listOf(Page(id = "p1", name = "Main", buttons = template.slots.map { slotToButton(it) })),
)

// ---------------- generate from library ----------------

private val SLOT_ALIASES: Map<String, List<String>> = mapOf(
    "power" to listOf("power", "on/off", "standby", "btn4"),
    "vol_up" to listOf("vol up", "volume up", "vol+", "volume+", "vol_up"),
    "vol_down" to listOf("vol down", "volume down", "vol-", "volume-", "vol_down"),
    "mute" to listOf("mute", "silence"),
    "ch_up" to listOf("ch up", "channel up", "ch+", "channel+", "prog+"),
    "ch_down" to listOf("ch down", "channel down", "ch-", "channel-", "prog-"),
    "up" to listOf("up", "arrow up"),
    "down" to listOf("down", "arrow down"),
    "left" to listOf("left", "arrow left"),
    "right" to listOf("right", "arrow right"),
    "ok" to listOf("ok", "enter", "select", "confirm"),
    "home" to listOf("home", "smart"),
    "back" to listOf("back", "return", "exit"),
    "menu" to listOf("menu", "settings"),
    "info" to listOf("info", "info"),
    "guide" to listOf("guide", "epg"),
    "play" to listOf("play", "play/pause", "pause"),
    "prev" to listOf("prev", "previous", "rewind"),
    "next" to listOf("next", "forward", "skip"),
    "source" to listOf("source", "input", "av"),
    "temp_up" to listOf("temp up", "temp+", "temperature up", "warmer"),
    "temp_down" to listOf("temp down", "temp-", "temperature down", "cooler"),
    "mode" to listOf("mode", "cool", "heat"),
    "fan" to listOf("fan", "fan speed", "speed"),
    "swing" to listOf("swing", "osc", "oscillate"),
)

private fun normalize(s: String) = s.trim().lowercase()
    .replace(Regex("[^a-z0-9+/ -]"), "").replace(Regex("\\s+"), " ")

/** Assign imported codes to template slots by name; leftovers go to an "Extras" grid page. */
fun assignCodesToRemote(remote: Remote, codes: List<Code>): Pair<Remote, Int> {
    var assigned = 0
    val used = HashSet<String>()
    // Reconstruct which template produced this remote by matching slot layout coordinates.
    val templateSlots = remoteTemplateSlotKeys(remote)
    val buttons = remote.pages.firstOrNull()?.buttons ?: return remote to 0
    val newButtons = buttons.map { b ->
        val key = templateSlots[b.id] ?: return@map b
        val norm = normalize(b.label)
        val code = codes.firstOrNull { c ->
            val cn = normalize(c.name)
            (SLOT_ALIASES[key]?.any { cn == it || cn.startsWith(it) || cn.replace(" ", "") == it.replace(" ", "") } == true)
        } ?: codes.firstOrNull { c -> normalize(c.name) == norm }
        if (code != null && used.add(code.id)) {
            assigned++
            b.copy(action = Action(type = "code", id = code.id))
        } else b
    }
    val unmatched = codes.filter { it.id !in used }
    val pagesOut = mutableListOf(remote.pages.first().copy(buttons = newButtons))
    if (unmatched.isNotEmpty()) {
        val cols = 3
        val extras = unmatched.mapIndexed { i, c ->
            val bw = 300f
            RButton(
                id = newId(),
                label = c.name.take(14),
                x = 40f + (i % cols) * 340f,
                y = 40f + (i / cols) * 190f,
                w = bw, h = 160f,
                action = Action(type = "code", id = c.id),
            )
        }
        pagesOut.add(Page(id = newId(), name = "Extras", buttons = extras))
    }
    return remote.copy(pages = pagesOut) to assigned
}

private fun remoteTemplateSlotKeys(remote: Remote): Map<String, String> {
    // Reconstruct which template produced this remote by matching slot layout coordinates.
    for (t in TEMPLATES) {
        val buttons = remote.pages.firstOrNull()?.buttons ?: continue
        if (buttons.size == t.slots.size &&
            buttons.zip(t.slots).all { (b, s) -> b.label == s.label && b.x == s.x }
        ) return t.slots.associate { s -> (buttons.first { it.label == s.label && it.x == s.x }).id to s.key }
    }
    // Fall back to label→key mapping.
    val map = HashMap<String, String>()
    for (t in TEMPLATES) for (s in t.slots) {
        remote.pages.firstOrNull()?.buttons?.firstOrNull { it.label == s.label }?.let { map[it.id] = s.key }
    }
    return map
}
