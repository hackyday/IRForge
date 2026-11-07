package io.github.hackyday.irforge.editor

import io.github.hackyday.irforge.data.Action
import io.github.hackyday.irforge.data.ButtonShape
import io.github.hackyday.irforge.data.Code
import io.github.hackyday.irforge.data.Page
import io.github.hackyday.irforge.data.RButton
import io.github.hackyday.irforge.data.Remote
import io.github.hackyday.irforge.data.newId

/**
 * Templates modeled on REAL remote controls: power top-right, number pad,
 * twin volume/channel rockers with mute between, circular navigation ring with
 * center OK, colored shortcut keys, transport row. Canvas width = 1080 units.
 */
data class Slot(
    val key: String,
    val label: String,
    val icon: String,
    val x: Float, val y: Float, val w: Float, val h: Float,
    val fill: String,
    val textColor: String = "#FFFFFF",
    val fontSize: Float = 34f,
    val shape: ButtonShape = ButtonShape.ROUNDED,
    val holdRepeat: Boolean = false,
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

private const val DARK = "#2B2E3A"
private const val RED = "#C62828"
private const val GREEN = "#2E7D32"
private const val YELLOW = "#F9A825"
private const val BLUE = "#1565C0"

private fun power(x: Float = 780f, y: Float = 40f, w: Float = 260f, h: Float = 150f) =
    Slot("power", "Power", "🔌", x, y, w, h, RED)

/** Circular navigation ring with big center OK (like real remotes). */
private fun navRing(cx: Float, top: Float, r: Float = 180f): List<Slot> = listOf(
    Slot("up", "Up", "▲", cx - 90f, top, r, r, DARK, shape = ButtonShape.CIRCLE),
    Slot("left", "Left", "◀", cx - 310f, top + 200f, r, r, DARK, shape = ButtonShape.CIRCLE),
    Slot("ok", "OK", "✔", cx - 100f, top + 200f, 200f, 200f, "#3949AB", shape = ButtonShape.CIRCLE, fontSize = 38f),
    Slot("right", "Right", "▶", cx + 130f, top + 200f, r, r, DARK, shape = ButtonShape.CIRCLE),
    Slot("down", "Down", "▼", cx - 90f, top + 420f, r, r, DARK, shape = ButtonShape.CIRCLE),
)

/** Twin volume/channel rockers with mute centered between them. */
private fun volChRockers(y: Float): List<Slot> = listOf(
    Slot("vol_up", "Vol +", "🔊", 40f, y, 240f, 140f, DARK, holdRepeat = true),
    Slot("vol_down", "Vol -", "🔉", 40f, y + 160f, 240f, 140f, DARK, holdRepeat = true),
    Slot("mute", "Mute", "🔇", 420f, y + 80f, 240f, 140f, DARK),
    Slot("ch_up", "Ch +", "▲", 800f, y, 240f, 140f, DARK, holdRepeat = true),
    Slot("ch_down", "Ch -", "▼", 800f, y + 160f, 240f, 140f, DARK, holdRepeat = true),
)

private fun transportRow(y: Float, h: Float = 180f): List<Slot> = listOf(
    Slot("prev", "Prev", "⏮", 40f, y, 320f, h, DARK),
    Slot("play", "Play", "⏯", 380f, y, 320f, h, DARK),
    Slot("next", "Next", "⏭", 720f, y, 320f, h, DARK),
)

private fun coloredRow(y: Float, h: Float = 120f): List<Slot> = listOf(
    Slot("red", "Red", "●", 40f, y, 240f, h, RED),
    Slot("green", "Green", "●", 300f, y, 240f, h, GREEN),
    Slot("yellow", "Yellow", "●", 560f, y, 240f, h, YELLOW),
    Slot("blue", "Blue", "●", 820f, y, 220f, h, BLUE),
)

private fun numberPad(top: Float, h: Float = 150f): List<Slot> {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", ""))
    val out = ArrayList<Slot>(10)
    for ((ri, row) in rows.withIndex()) {
        for ((ci, d) in row.withIndex()) {
            if (d.isEmpty()) continue
            out.add(Slot("key_$d", d, "", (40 + ci * 340).toFloat(), top + ri * (h + 20), 320f, h, DARK, fontSize = 40f))
        }
    }
    return out
}

private val TV = Template(
    "tv", "TV", "📺", "tv", "#101216", "#181B22",
    listOf(
        Slot("source", "Input", "", 40f, 40f, 240f, 150f, DARK),
        Slot("info", "Info", "i", 300f, 40f, 240f, 150f, DARK),
        power(780f),
    ) + numberPad(230f) + volChRockers(930f) + navRing(540f, 1290f) +
        listOf(
            Slot("back", "Back", "↩", 240f, 1930f, 260f, 140f, DARK),
            Slot("home", "Home", "🏠", 580f, 1930f, 260f, 140f, DARK),
        )
)

private val STB = Template(
    "stb", "Set-top box", "📡", "stb", "#0E1420", "#141B2A",
    listOf(
        power(40f, 40f),
        Slot("guide", "Guide", "📰", 300f, 40f, 240f, 150f, DARK),
        Slot("menu", "Menu", "☰", 560f, 40f, 240f, 150f, DARK),
        Slot("info", "Info", "ℹ", 820f, 40f, 220f, 150f, DARK),
    ) + coloredRow(230f) + navRing(540f, 390f) +
        listOf(
            Slot("back", "Back", "↩", 240f, 1010f, 260f, 140f, DARK),
            Slot("exit", "Exit", "✕", 580f, 1010f, 260f, 140f, DARK),
        ) + volChRockers(1190f) + numberPad(1530f)
)

private val AUDIO = Template(
    "audio", "Audio / Receiver", "🔊", "audio", "#141018", "#1C1622",
    listOf(
        power(780f),
        Slot("source", "Source", "", 40f, 40f, 240f, 150f, DARK),
        Slot("aux", "AUX", "", 40f, 230f, 240f, 140f, DARK),
        Slot("hdmi", "HDMI", "", 300f, 230f, 240f, 140f, DARK),
        Slot("bt", "BT", "", 560f, 230f, 240f, 140f, DARK),
        Slot("usb", "USB", "", 820f, 230f, 220f, 140f, DARK),
        Slot("vol_up", "Vol +", "🔊", 320f, 420f, 440f, 190f, DARK, holdRepeat = true),
        Slot("mute", "Mute", "🔇", 320f, 650f, 440f, 140f, DARK),
        Slot("vol_down", "Vol -", "🔉", 320f, 830f, 440f, 190f, DARK, holdRepeat = true),
    ) + transportRow(1060f) +
        listOf(
            Slot("eq", "EQ", "🎚", 40f, 1290f, 500f, 150f, DARK),
            Slot("sound", "Sound", "🎵", 580f, 1290f, 460f, 150f, DARK),
        )
)

private val FAN = Template(
    "fan", "Fan", "🌀", "fan", "#0E1614", "#141F1C",
    listOf(
        power(780f),
        Slot("speed", "Speed", "⚡", 40f, 230f, 480f, 190f, DARK),
        Slot("osc", "Swing", "↔", 560f, 230f, 480f, 190f, DARK),
        Slot("mode", "Mode", "🔄", 40f, 460f, 480f, 190f, DARK),
        Slot("timer", "Timer", "⏱", 560f, 460f, 480f, 190f, DARK),
        Slot("light", "Light", "💡", 40f, 690f, 480f, 190f, DARK),
        Slot("swing", "Oscillate", "↕", 560f, 690f, 480f, 190f, DARK),
    )
)

private val PROJECTOR = Template(
    "projector", "Projector", "📽", "projector", "#161012", "#1F161A",
    listOf(
        power(780f),
        Slot("source", "Input", "", 40f, 40f, 240f, 150f, DARK),
        Slot("info", "Info", "ℹ", 300f, 40f, 240f, 150f, DARK),
    ) + navRing(540f, 230f) +
        listOf(
            Slot("menu", "Menu", "☰", 240f, 850f, 260f, 140f, DARK),
            Slot("exit", "Exit", "✕", 580f, 850f, 260f, 140f, DARK),
            Slot("vol_up", "Vol +", "🔊", 420f, 1030f, 240f, 140f, DARK, holdRepeat = true),
            Slot("vol_down", "Vol -", "🔉", 420f, 1190f, 240f, 140f, DARK, holdRepeat = true),
        )
)

private val AC = Template(
    "ac", "Air conditioner", "❄", "ac", "#0C1418", "#121D23",
    listOf(
        Slot("display", "Temp", "🌡", 40f, 40f, 700f, 150f, "#1A2B33", fontSize = 44f),
        power(780f),
        Slot("temp_down", "Temp -", "🌡-", 40f, 230f, 480f, 240f, DARK, fontSize = 44f, holdRepeat = true),
        Slot("temp_up", "Temp +", "🌡+", 560f, 230f, 480f, 240f, DARK, fontSize = 44f, holdRepeat = true),
        Slot("mode", "Mode", "🔄", 40f, 510f, 320f, 160f, DARK),
        Slot("fan", "Fan", "⚡", 400f, 510f, 320f, 160f, DARK),
        Slot("swing", "Swing", "↕", 760f, 510f, 280f, 160f, DARK),
        Slot("turbo", "Turbo", "💨", 40f, 710f, 480f, 150f, DARK),
        Slot("timer", "Timer", "⏱", 560f, 710f, 480f, 150f, DARK),
        Slot("sleep", "Sleep", "🌙", 40f, 900f, 320f, 150f, DARK),
        Slot("eco", "Eco", "🌱", 400f, 900f, 320f, 150f, DARK),
        Slot("light", "Light", "💡", 760f, 900f, 280f, 150f, DARK),
    )
)

val TEMPLATES: List<Template> = listOf(TV, STB, AUDIO, FAN, PROJECTOR, AC)

/** Generic numbered grids for any device. */
fun gridTemplate(cols: Int, rows: Int): Template {
    val slots = ArrayList<Slot>()
    val bw = (1000f / cols) - 30f
    val bh = 150f
    for (r in 0 until rows) for (c in 0 until cols) {
        val n = r * cols + c
        slots.add(Slot("b$n", "${n + 1}", "", 40f + c * (bw + 30f), 40f + r * (bh + 30f), bw, bh, DARK))
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
    holdRepeat = slot.holdRepeat,
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
    "info" to listOf("info"),
    "guide" to listOf("guide", "epg"),
    "play" to listOf("play", "play/pause", "pause"),
    "prev" to listOf("prev", "previous", "rewind"),
    "next" to listOf("next", "forward", "skip"),
    "source" to listOf("source", "input", "av", "signal", "wireless screen", "wireless_screen"),
    "aux" to listOf("aux"),
    "hdmi" to listOf("hdmi"),
    "bt" to listOf("bt", "bluetooth"),
    "usb" to listOf("usb"),
    "eq" to listOf("eq", "equalizer"),
    "sound" to listOf("sound", "audio", "dsp"),
    "temp_up" to listOf("temp up", "temp+", "temperature up", "warmer"),
    "temp_down" to listOf("temp down", "temp-", "temperature down", "cooler"),
    "mode" to listOf("mode", "cool", "heat"),
    "fan" to listOf("fan", "fan speed"),
    "swing" to listOf("swing", "osc", "oscillate"),
    "turbo" to listOf("turbo", "jet", "powerful"),
    "sleep" to listOf("sleep"),
    "eco" to listOf("eco", "economy"),
    "timer" to listOf("timer"),
    "light" to listOf("light", "lamp"),
    "display" to listOf("display", "led", "lcd"),
    "exit" to listOf("exit"),
    "red" to listOf("red"),
    "green" to listOf("green"),
    "yellow" to listOf("yellow"),
    "blue" to listOf("blue"),
)

private fun normalize(s: String) = s.trim().lowercase()
    .replace(Regex("[^a-z0-9+/ -]"), "").replace(Regex("\\s+"), " ")

/** Assign imported codes to template slots by name; leftovers go to an "Extras" grid page. */
fun assignCodesToRemote(remote: Remote, codes: List<Code>): Pair<Remote, Int> {
    var assigned = 0
    val used = HashSet<String>()
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
            RButton(
                id = newId(),
                label = c.name.take(14),
                x = 40f + (i % cols) * 340f,
                y = 40f + (i / cols) * 190f,
                w = 300f, h = 160f,
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
