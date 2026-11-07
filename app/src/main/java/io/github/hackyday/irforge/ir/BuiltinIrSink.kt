package io.github.hackyday.irforge.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The phone's built-in IR emitter via ConsumerIrManager (API 19+). */
class BuiltinIrSink(private val ctx: Context) : IrSink {
    override val key = "builtin"
    override val label = "Built-in IR blaster"

    private val cim: ConsumerIrManager? =
        ctx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    override suspend fun available(): Boolean = cim?.hasIrEmitter() == true

    override suspend fun detail(): String {
        val m = cim ?: return "ConsumerIrManager unavailable on this device"
        if (!m.hasIrEmitter()) return "No built-in IR emitter"
        val ranges = m.carrierFrequencies
            .joinToString(", ") { "${it.minFrequency / 1000}–${it.maxFrequency / 1000} kHz" }
        return "IR emitter present · carriers: ${ranges.ifEmpty { "unknown" }}"
    }

    override suspend fun transmit(freqHz: Int, pattern: IntArray) = withContext(Dispatchers.IO) {
        val m = cim ?: throw IllegalStateException("No IR emitter")
        m.transmit(freqHz, pattern)
    }
}
