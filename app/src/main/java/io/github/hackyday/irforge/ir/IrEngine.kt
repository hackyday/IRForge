package io.github.hackyday.irforge.ir

import android.content.Context
import io.github.hackyday.irforge.data.Code
import io.github.hackyday.irforge.data.Macro
import io.github.hackyday.irforge.data.MacroStep
import io.github.hackyday.irforge.data.Store
import kotlinx.coroutines.delay

/** A transmitter that can emit raw IR patterns. */
interface IrSink {
    val key: String          // "builtin" | "usb" | "aux"
    val label: String
    suspend fun available(): Boolean
    suspend fun detail(): String
    suspend fun transmit(freqHz: Int, pattern: IntArray)
}

data class TxStatus(
    val key: String,
    val label: String,
    val available: Boolean,
    val detail: String,
    val active: Boolean,
)

/** Central dispatcher: picks a sink per remote/settings preference and transmits. */
class IrEngine(private val ctx: Context, private val store: Store) {

    val builtin = BuiltinIrSink(ctx)
    val usb = UsbIrSink(ctx)
    val aux = AuxIrSink(ctx)

    private fun sinks() = listOf(builtin, usb, aux)

    private fun preferredKey(remoteId: String?): String {
        if (remoteId != null) {
            store.remoteById(remoteId)?.preferredTx?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        store.settings.value.preferredTx.takeIf { it.isNotEmpty() }?.let { return it }
        return ""
    }

    /** Best available sink honoring preference order: preference > builtin > usb > aux. */
    suspend fun pickSink(remoteId: String? = null): IrSink? {
        val pref = preferredKey(remoteId)
        val ordered = if (pref.isEmpty()) sinks() else sinks().sortedByDescending { it.key == pref }
        return ordered.firstOrNull { it.available() }
    }

    suspend fun statusList(remoteId: String? = null): List<TxStatus> {
        val pref = preferredKey(remoteId)
        val activeKey = pickSink(remoteId)?.key ?: ""
        return sinks().map { TxStatus(it.key, it.label, it.available(), it.detail(), it.key == activeKey && it.available()) }
            .let { list -> if (pref.isEmpty()) list else list.sortedByDescending { it.key == pref } }
    }

    suspend fun transmit(freqHz: Int, patternUs: IntArray, repeats: Int = 1, remoteId: String? = null): String? {
        val freq = store.settings.value.carrierOverrideHz.takeIf { it > 0 } ?: freqHz
        val pattern = sanitize(patternUs.toList())
        val sink = pickSink(remoteId) ?: return "No IR transmitter found"
        repeat(maxOf(1, repeats)) { n ->
            try {
                if (sink is AuxIrSink) {
                    val s = store.settings.value
                    sink.play(freq, pattern, s.auxMode == "envelope", s.auxChannelSwap)
                } else {
                    sink.transmit(freq, pattern)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return "Transmit failed via ${sink.label}: ${e.message ?: e.javaClass.simpleName}"
            }
            if (n < repeats) delay(45)
        }
        return null
    }

    suspend fun sendCode(code: Code, repeats: Int = 1, remoteId: String? = null): String? =
        transmit(
            code.freqHz, code.pattern.toIntArray(),
            repeats = maxOf(repeats, store.settings.value.defaultRepeats),
            remoteId = remoteId,
        )

    /**
     * Widget/broadcast path: only uses sinks that are ready RIGHT NOW (no USB
     * permission prompts — broadcast receivers must finish within ~10 s).
     */
    suspend fun sendCodeReadyNow(code: Code): String? {
        val freq = store.settings.value.carrierOverrideHz.takeIf { it > 0 } ?: code.freqHz
        val pattern = sanitize(code.pattern)
        return try {
            val usbReady = usb.scan().firstOrNull { it.kind != "unsupported" && usb.hasPermission(it) }
            when {
                builtin.available() -> builtin.transmit(freq, pattern)
                usbReady != null -> usb.transmitWith(usbReady, freq, pattern)
                aux.available() -> {
                    val s = store.settings.value
                    aux.play(freq, pattern, s.auxMode == "envelope", s.auxChannelSwap)
                }
                else -> return "No IR transmitter ready — open IRForge"
            }
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            "Transmit failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Runs a macro; onProgress(stepIndex, total). Returns error or null. */
    suspend fun runMacro(
        macro: Macro,
        remoteId: String? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): String? {
        val steps = macro.steps
        steps.forEachIndexed { i, step ->
            if (isCancelled()) return "Cancelled"
            onProgress(i, steps.size)
            when (step.type) {
                "delay" -> delay(step.ms.toLong().coerceIn(0, 60_000))
                "code" -> store.codeById(step.id)?.let {
                    val err = sendCode(it, repeats = 1, remoteId = remoteId)
                    if (err != null) return err
                    delay(60)
                }
            }
        }
        onProgress(steps.size, steps.size)
        return null
    }

    companion object {
        /** Sanity-check a pattern before transmitting. */
        fun sanitize(pattern: List<Int>): IntArray =
            pattern.filter { it in 1..2_000_000 }.map { it.coerceAtLeast(1) }.toIntArray()
    }
}
