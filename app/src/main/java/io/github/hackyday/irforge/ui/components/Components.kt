package io.github.hackyday.irforge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hackyday.irforge.data.ButtonShape
import io.github.hackyday.irforge.data.RButton
import io.github.hackyday.irforge.data.Remote
import io.github.hackyday.irforge.data.Settings

fun Color.Companion.fromHexOrNull(hex: String): Color? {
    val h = hex.removePrefix("#").trim()
    return when (h.length) {
        6 -> runCatching { Color(android.graphics.Color.parseColor("#$h")) }.getOrNull()
        8 -> runCatching { Color(android.graphics.Color.parseColor("#$h")) }.getOrNull()
        else -> null
    }
}

fun parseColor(hex: String, fallback: Color = Color(0xFF2B2E3A)): Color =
    Color.fromHexOrNull(hex) ?: fallback

fun shapeOf(s: ButtonShape): Shape = when (s) {
    ButtonShape.ROUNDED -> RoundedCornerShape(18.dp)
    ButtonShape.SQUARE -> RoundedCornerShape(2.dp)
    ButtonShape.CIRCLE -> CircleShape
    ButtonShape.PILL -> RoundedCornerShape(50)
}

/** The single renderer for remote buttons — used by play view, editor and previews. */
@Composable
fun RemoteButtonView(
    button: RButton,
    remote: Remote,
    fontSizePx: Float = 34f,
    modifier: Modifier = Modifier,
) {
    val start = parseColor(button.fillStart)
    val end = parseColor(button.fillEnd, start)
    val fill = if (button.gradient) Brush.verticalGradient(listOf(start, end)) else Brush.horizontalGradient(listOf(start, start))
    val text = parseColor(button.textColor, Color.White)
    val border = parseColor(button.borderColor, Color.Transparent)
    val shape = shapeOf(button.shape)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val labelSp = with(density) { (fontSizePx.coerceAtLeast(6f)).toSp() }
    val iconSp = with(density) { (fontSizePx * 0.9f).coerceAtLeast(6f).toSp() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fill, shape)
            .border(BorderStroke((button.borderWidth / 3f).dp.coerceAtLeast(0.dp), if (button.borderWidth > 0f) border else Color.Transparent), shape)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (button.icon.isNotEmpty()) {
                Text(button.icon, fontSize = iconSp, color = text)
            }
            if (button.label.isNotEmpty()) {
                Text(
                    button.label,
                    color = text,
                    fontSize = labelSp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

object PressFeedback {
    /** Wraps haptics respecting user settings. */
    fun haptic(ctx: android.content.Context, settings: Settings) {
        if (!settings.haptics) return
        val v = ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
        if (android.os.Build.VERSION.SDK_INT >= 26) v.vibrate(android.os.VibrationEffect.createOneShot(18, 120))
        else @Suppress("DEPRECATION") v.vibrate(18)
    }
}

/** Simple swatch palette for the editors. */
val SWATCHES = listOf(
    "#C62828", "#E53935", "#FF5252", "#F9A825", "#FFD54F", "#8BC34A",
    "#2E7D32", "#00897B", "#00ACC1", "#039BE5", "#1E88E5", "#3949AB",
    "#5E35B1", "#8E24AA", "#D81B60", "#455A64", "#2B2E3A", "#12131A",
    "#FFFFFF", "#9E9E9E",
)

@Composable
fun ColorSwatchRow(selected: String, allowCustom: Boolean = true, onPick: (String) -> Unit) {
    androidx.compose.foundation.layout.Column {
        Row(
            Modifier.padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (i in 0 until 10) {
                val hex = SWATCHES[i]
                SwatchDot(hex, selected.equals(hex, true)) { onPick(hex) }
            }
        }
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (i in 10 until SWATCHES.size) {
                val hex = SWATCHES[i]
                SwatchDot(hex, selected.equals(hex, true)) { onPick(hex) }
            }
        }
        if (allowCustom) {
            androidx.compose.material3.OutlinedTextField(
                value = selected,
                onValueChange = { v -> onPick(v) },
                label = { Text("Hex (#RRGGBB)") },
                singleLine = true,
                modifier = Modifier.padding(horizontal = 4.dp).size(width = 220.dp, height = 60.dp),
            )
        }
    }
}

@Composable
private fun SwatchDot(hex: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .background(parseColor(hex), CircleShape)
            .border(
                BorderStroke(if (isSelected) 3.dp else 1.dp, if (isSelected) Color.White else Color(0x33FFFFFF)),
                CircleShape,
            )
            .clickable { onClick() },
    )
}
