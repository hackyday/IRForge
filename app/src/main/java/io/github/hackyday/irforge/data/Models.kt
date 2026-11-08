package io.github.hackyday.irforge.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** Id generator usable from pure code (templates, tests) without a Store instance. */
fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

@Serializable
data class Code(
    val id: String,
    val name: String,
    val freqHz: Int,
    val dutyCycle: Float = 0.33f,
    val pattern: List<Int>,          // µs mark/space pairs (odd length: trailing mark ok)
    val source: String = "import",   // import | learned | generated
    val sourceFormat: String = "flipper-ir",
    val protocol: String? = null,
    val tag: String = "",
    val createdAt: Long = 0L,
)

@Serializable
enum class ButtonShape { ROUNDED, SQUARE, CIRCLE, PILL }

@Serializable
data class Action(
    val type: String,     // "code" | "macro"
    val id: String,       // code id or macro id
    val repeat: Int = 1,  // extra repeats of the code
)

@Serializable
data class RButton(
    val id: String,
    val label: String = "BTN",
    val x: Float, val y: Float, val w: Float, val h: Float,   // editor units (canvas width 1080)
    val shape: ButtonShape = ButtonShape.ROUNDED,
    val fillStart: String = "#2B2E3A",
    val fillEnd: String = "#1B1D26",
    val gradient: Boolean = false,
    val textColor: String = "#FFFFFF",
    val borderColor: String = "#33FFFFFF",
    val borderWidth: Float = 1f,
    val opacity: Float = 1f,
    val fontSize: Float = 34f,
    val icon: String = "",           // emoji or empty
    val haptic: Boolean = true,
    val action: Action? = null,      // null = unassigned
    val longPress: Action? = null,
    val holdRepeat: Boolean = false, // re-send while held
)

@Serializable
data class Page(
    val id: String,
    val name: String = "Page 1",
    val buttons: List<RButton> = emptyList(),
    val gridSnap: Int = 40,          // editor units; 0 = free placement
)

@Serializable
data class Remote(
    val id: String,
    val name: String,
    val deviceType: String = "custom", // tv, stb, audio, fan, ac, projector, custom
    val bgStart: String = "#12131A",
    val bgEnd: String = "#1B1D26",
    val pages: List<Page> = listOf(Page(id = "p1")),
    val favorites: List<String> = emptyList(),  // button ids for widget
    val preferredTx: String = "",    // "", "builtin", "usb", "aux"
    val updatedAt: Long = 0L,
)

@Serializable
data class MacroStep(
    val type: String,     // "code" | "delay"
    val id: String = "",  // code id when type=code
    val ms: Int = 300,    // delay ms when type=delay
)

@Serializable
data class Macro(
    val id: String,
    val name: String,
    val icon: String = "🎬",
    val steps: List<MacroStep> = emptyList(),
)

@Serializable
data class WidgetCfg(
    val remoteId: String = "",
    val buttonIds: List<String> = emptyList(),
)

@Serializable
data class Settings(
    val themeMode: String = "dark",   // system | light | dark
    val haptics: Boolean = true,
    val defaultRepeats: Int = 1,
    val carrierOverrideHz: Int = 0,   // 0 = off
    val preferredTx: String = "",     // "" auto | builtin | usb | aux
    val auxMode: String = "envelope", // envelope | raw
    val auxChannelSwap: Boolean = false,
    val auxTested: Boolean = false,
    val firstRunDone: Boolean = false,
)
