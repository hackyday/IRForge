package io.github.hackyday.irforge.ir

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * USB IR blaster support, no external libraries.
 *
 * Recognized hardware:
 *  - USB Infrared Toy v2 (04d8:fd08) — full transmit + capture via the IR Toy v2 protocol
 *    (verified against the Linux ir_toy.c LIRC driver: 21 µs units, big-endian u16,
 *    0xFFFF terminator, TXSTART = 26 24 25 03, carrier cmd 0x06 + divider).
 *  - FTDI FT232/245 (0403) — SIO vendor requests.
 *  - CH340/CH341 (1a86:7523/5523) — vendor requests, divisor algorithm ported from Linux ch341.c.
 *  - CP210x (10c4:ea60) — vendor requests.
 *  - Generic CDC-ACM serial (includes DIY ESP32/Arduino blasters, see README).
 *  - Prolific PL2303 is detected but reported unsupported.
 *
 * Non-IRToy devices use a documented plain-text serial protocol:
 *   "IR <freqHz> <us> <us> ...\n"  -> replies "OK" (see README for the Arduino sketch).
 */
class UsbIrSink(private val ctx: Context) : IrSink {

    override val key = "usb"
    override val label = "USB IR blaster"

    private val usbManager get() = ctx.getSystemService(Context.USB_SERVICE) as UsbManager

    data class Candidate(val device: UsbDevice, val driver: String, val kind: String)

    // ---------------- detection ----------------

    fun scan(): List<Candidate> = usbManager.deviceList.values.mapNotNull { classify(it) }

    private fun classify(d: UsbDevice): Candidate? {
        val vid = d.vendorId
        val pid = d.productId
        return when {
            vid == 0x04d8 && pid == 0xfd08 -> Candidate(d, "USB IR Toy v2", "irtoy")
            vid == 0x0403 -> Candidate(d, "FTDI FT232/245", "serial")
            vid == 0x1a86 && pid in intArrayOf(0x7523, 0x5523, 0x7522) -> Candidate(d, "CH340/CH341", "serial")
            vid == 0x10c4 && pid == 0xea60 -> Candidate(d, "CP210x", "serial")
            d.interfaceCount > 0 && d.getInterface(0).interfaceClass == UsbConstants.USB_CLASS_COMM ->
                Candidate(d, "CDC-ACM serial", "serial")
            vid == 0x067b -> Candidate(d, "Prolific PL2303 (unsupported)", "unsupported")
            else -> null
        }
    }

    override suspend fun available(): Boolean = withContext(Dispatchers.IO) {
        scan().any { it.kind != "unsupported" && usbManager.hasPermission(it.device) }
    }

    override suspend fun detail(): String = withContext(Dispatchers.IO) {
        val cands = scan()
        if (cands.isEmpty()) return@withContext "No USB IR blaster plugged in"
        cands.joinToString("\n") { c ->
            val dev = c.device
            val perm = if (usbManager.hasPermission(dev)) "granted" else "needs permission"
            "${c.driver} · ${String.format("%04x:%04x", vid(dev), pid(dev))} · USB permission: $perm"
        }
    }

    private fun vid(d: UsbDevice) = d.vendorId
    private fun pid(d: UsbDevice) = d.productId

    /** 3-arg registerReceiver(flags) needs API 26; ContextCompat handles 24+. */
    private fun Context.registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter) {
        androidx.core.content.ContextCompat.registerReceiver(
            this, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    // ---------------- permission ----------------

    fun hasPermission(c: Candidate) = usbManager.hasPermission(c.device)

    suspend fun requestPermission(c: Candidate): Boolean {
        if (usbManager.hasPermission(c.device)) return true
        val action = "io.github.hackyday.irforge.USB_PERMISSION"
        val granted = AtomicReference<Boolean?>(null)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == action) {
                    granted.set(usbManager.hasPermission(c.device))
                }
            }
        }
        ctx.registerReceiverCompat(receiver, IntentFilter(action))
        return try {
            val pi = PendingIntent.getBroadcast(
                ctx, 0, Intent(action).setPackage(ctx.packageName),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            usbManager.requestPermission(c.device, pi)
            withTimeoutOrNull(30_000) {
                while (granted.get() == null) kotlinx.coroutines.delay(150)
            }
            granted.get() == true
        } finally {
            ctx.unregisterReceiver(receiver)
        }
    }

    // ---------------- transmit ----------------

    override suspend fun transmit(freqHz: Int, pattern: IntArray) = withContext(Dispatchers.IO) {
        val c = scan().firstOrNull { it.kind != "unsupported" }
            ?: throw IllegalStateException("No USB IR blaster connected")
        transmitWith(c, freqHz, pattern)
    }

    suspend fun transmitWith(c: Candidate, freqHz: Int, pattern: IntArray) = withContext(Dispatchers.IO) {
        check(c.kind != "unsupported") { "${c.driver} is not supported" }
        if (!usbManager.hasPermission(c.device)) {
            check(requestPermission(c)) { "USB permission denied" }
        }
        val conn = usbManager.openDevice(c.device) ?: throw IllegalStateException("Failed to open USB device")
        try {
            val intf = pickInterface(c.device)
            check(conn.claimInterface(intf, true)) { "Failed to claim USB interface" }
            SerialPort.open(c, conn, intf).use { port ->
                when (c.kind) {
                    "irtoy" -> irToyTransmit(port, freqHz, pattern)
                    else -> genericSerialTransmit(port, freqHz, pattern)
                }
            }
        } finally {
            conn.close()
        }
    }

    private fun pickInterface(dev: UsbDevice): UsbInterface {
        for (i in 0 until dev.interfaceCount) {
            val f = dev.getInterface(i)
            if (f.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA ||
                f.endpointCount >= 1 && f.getEndpoint(0).type == UsbConstants.USB_ENDPOINT_XFER_BULK
            ) return f
        }
        return dev.getInterface(0)
    }

    /** USB IR Toy v2 firmware ≥2.0 protocol (matches Linux drivers/media/rc/ir_toy.c). */
    private fun irToyTransmit(port: SerialPort, freqHz: Int, pattern: IntArray) {
        // Drain and reset.
        port.drain()
        port.write(byteArrayOf(0xff.toByte(), 0xff.toByte(), 0, 0, 0, 0, 0))
        port.drain()

        // Version check: 'v' -> 'V' + 3 digits (e.g. 202 = v2.02).
        port.write(byteArrayOf('v'.code.toByte()))
        val ver = port.readBytes(4, 600)
        check(ver.size >= 4 && ver[0] == 'V'.code.toByte()) {
            "IR Toy not detected (no version reply). Is it a USB Infrared Toy v2+?"
        }

        // Enter sample mode ('s' -> 'S' + proto), then TXSTART.
        port.write(byteArrayOf('s'.code.toByte()))
        port.readBytes(3, 600)
        port.write(byteArrayOf(0x26, 0x24, 0x25, 0x03))

        // Carrier: divider from 48 MHz clock, byte 0x06 sets PWM frequency.
        val div = ((48_000_000.0 / (16.0 * freqHz)).roundToInt()).coerceIn(1, 256) - 1
        port.write(byteArrayOf(0x06, div.toByte(), 0))

        // Durations: big-endian u16 in 21 µs units, terminated by 0xFFFF.
        val out = java.io.ByteArrayOutputStream()
        for (us in pattern) {
            val v = ((us + UNIT_US / 2) / UNIT_US).coerceIn(1, 0xfffe)
            out.write((v shr 8) and 0xff)
            out.write(v and 0xff)
        }
        out.write(0xff); out.write(0xff)
        val data = out.toByteArray()

        // Flow control: device replies with free buffer space (1 byte) before each chunk.
        var sent = 0
        while (sent < data.size) {
            val free = port.readBytes(1, 400).firstOrNull()?.toInt()?.and(0xff) ?: 64
            val n = minOf(free, data.size - sent).coerceAtLeast(1)
            port.write(data.copyOfRange(sent, sent + n))
            sent += n
        }

        // Reply: 't' + 2-byte count, then 'C' (success) or 'F' (failure).
        val rep = port.readBytes(3, 1500)
        val ok = rep.size == 3 && rep[0] == 't'.code.toByte()
        val final = if (ok) port.readBytes(1, 1500) else byteArrayOf()
        check(final.firstOrNull() == 'C'.code.toByte()) {
            if (final.firstOrNull() == 'F'.code.toByte()) "IR Toy reported transmit failure"
            else "No completion reply from IR Toy"
        }
    }

    /** Plain-text protocol for DIY serial blasters (Arduino/ESP32 sketch in README). */
    private fun genericSerialTransmit(port: SerialPort, freqHz: Int, pattern: IntArray) {
        port.drain()
        val line = buildString {
            append("IR ").append(freqHz)
            for (us in pattern) append(' ').append(us)
            append('\n')
        }.toByteArray(Charsets.US_ASCII)
        port.write(line)
        val rep = port.readBytes(2, 2500)
        if (rep.size == 2 && rep.decodeToString() == "OK") return
        // No reply is tolerated (some firmware is fire-and-forget).
    }

    /** Records an IR signal (USB IR Toy v2 sample mode). Returns µs pulse/space pattern. */
    suspend fun capture(c: Candidate, maxMs: Long = 3000): IntArray = withContext(Dispatchers.IO) {
        check(c.kind == "irtoy") { "Capture requires a USB Infrared Toy v2" }
        if (!usbManager.hasPermission(c.device)) check(requestPermission(c)) { "USB permission denied" }
        val conn = usbManager.openDevice(c.device) ?: throw IllegalStateException("Failed to open USB device")
        var result: IntArray? = null
        try {
            val intf = pickInterface(c.device)
            conn.claimInterface(intf, true)
            SerialPort.open(c, conn, intf).use { port ->
                port.drain()
                port.write(byteArrayOf(0xff.toByte(), 0xff.toByte(), 0, 0, 0, 0, 0))
                port.write(byteArrayOf('v'.code.toByte()))
                port.readBytes(4, 600)
                port.write(byteArrayOf('s'.code.toByte()))
                val sm = port.readBytes(3, 600)
                check(sm.size >= 3 && sm[0] == 'S'.code.toByte()) { "Failed to enter sample mode" }

                val out = ArrayList<Int>(512)
                var buf = ByteArray(64)
                val deadline = System.currentTimeMillis() + maxMs
                var done = false
                while (!done && System.currentTimeMillis() < deadline) {
                    val n = port.read(buf, 300)
                    if (n <= 0) continue
                    var i = 0
                    while (i + 1 < n) {
                        val v = ((buf[i].toInt() and 0xff) shl 8) or (buf[i + 1].toInt() and 0xff)
                        i += 2
                        if (v == 0xffff) { // long-gap marker: continue as space
                            out.add(UNIT_US * 0xfffe)
                            done = true
                            break
                        }
                        out.add(v * UNIT_US)
                    }
                }
                port.write(byteArrayOf(0)) // SMODE_EXIT
                port.drain()
                if (out.isNotEmpty()) {
                    // Drop trailing space, keep pattern sane.
                    if (out.size % 2 == 0) out.removeAt(out.size - 1)
                    result = out.toIntArray()
                }
            }
        } finally {
            conn.close()
        }
        result ?: throw IllegalStateException("No IR signal captured — point a remote at the IR Toy and press a button")
    }

    companion object {
        const val UNIT_US = 21
    }
}

// =====================================================================
// Serial port abstraction + drivers
// =====================================================================

class SerialPort private constructor(
    private val conn: UsbDeviceConnection,
    private val out: UsbEndpoint?,
    private val inEp: UsbEndpoint?,
) : AutoCloseable {

    fun write(data: ByteArray): Int {
        val ep = out ?: throw IllegalStateException("No USB bulk OUT endpoint")
        var sent = 0
        while (sent < data.size) {
            val n = conn.bulkTransfer(ep, data.copyOfRange(sent, data.size), data.size - sent, 1500)
            if (n < 0) throw IllegalStateException("USB write failed")
            sent += n
        }
        return sent
    }

    fun read(buf: ByteArray, timeoutMs: Int): Int {
        val ep = inEp ?: return -1
        return conn.bulkTransfer(ep, buf, buf.size, timeoutMs)
    }

    fun readBytes(len: Int, timeoutMs: Int): ByteArray {
        val buf = ByteArray(len)
        var got = 0
        val deadline = System.currentTimeMillis() + timeoutMs
        while (got < len && System.currentTimeMillis() < deadline) {
            val n = read(buf.copyOfRange(got, len), (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(50))
            if (n > 0) got += n
        }
        return buf.copyOf(got)
    }

    fun drain() {
        val buf = ByteArray(512)
        while (read(buf, 40) > 0) { /* discard */ }
    }

    override fun close() { /* connection closed by caller via .use on UsbDeviceConnection */ }

    companion object {
        private const val REQTYPE_CLASS_INTERFACE_OUT = 0x21
        private const val REQTYPE_VENDOR_DEVICE_OUT = 0x40
        private const val REQTYPE_VENDOR_DEVICE_IN = 0xC0
        private const val REQTYPE_VENDOR_INTERFACE_OUT = 0x41

        private fun UsbDeviceConnection.controlOut(reqType: Int, request: Int, value: Int, index: Int, data: ByteArray? = null): Int =
            controlTransfer(reqType, request, value, index, data, data?.size ?: 0, 1200)

        private fun UsbDeviceConnection.controlIn(reqType: Int, request: Int, value: Int, index: Int, len: Int): ByteArray {
            val b = ByteArray(len)
            val n = controlTransfer(reqType, request, value, index, b, len, 1200)
            return if (n >= 0) b.copyOf(n) else ByteArray(0)
        }

        fun open(c: UsbIrSink.Candidate, conn: UsbDeviceConnection, intf: UsbInterface): SerialPort {
            var outEp: UsbEndpoint? = null
            var inEp: UsbEndpoint? = null
            for (i in 0 until intf.endpointCount) {
                val e = intf.getEndpoint(i)
                if (e.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (e.direction == UsbConstants.USB_DIR_OUT) outEp = e else inEp = e
                }
            }

            when {
                c.driver.startsWith("FTDI") -> {
                    // SIO reset, 8N1, 115200, flow off, DTR+RTS, purge, latency 1ms.
                    conn.controlOut(REQTYPE_VENDOR_DEVICE_OUT, 0x00, 0x0000, 0)          // SIO_RESET
                    conn.controlOut(REQTYPE_VENDOR_DEVICE_OUT, 0x04, 0x0008, 0)          // SET_DATA: 8N1
                    val div = (3_000_000.0 / 115200).toInt().coerceIn(1, 0x3fff)         // 26 -> 115384 bps
                    conn.controlOut(REQTYPE_VENDOR_DEVICE_OUT, 0x03, div, 0)             // SET_BAUDRATE
                    conn.controlOut(REQTYPE_VENDOR_DEVICE_OUT, 0x02, 0x0000, 0)          // SET_FLOW_CTRL: none
                    conn.controlOut(REQTYPE_VENDOR_DEVICE_OUT, 0x01, 0x0303, 0)          // SET_MODEM_CTRL: DTR|RTS
                    conn.controlOut(REQTYPE_VENDOR_DEVICE_OUT, 0x00, 0x0001, 0)          // purge RX
                    conn.controlOut(REQTYPE_VENDOR_DEVICE_OUT, 0x00, 0x0002, 0)          // purge TX
                    conn.controlOut(REQTYPE_VENDOR_DEVICE_OUT, 0x09, 0x0001, 0)          // latency timer 1ms
                }
                c.driver.startsWith("CH340") -> {
                    val version = conn.controlIn(REQTYPE_VENDOR_DEVICE_IN, 0x5F, 0, 0, 2)
                    conn.controlOut(REQTYPE_VENDOR_DEVICE_OUT, 0xA1, 0, 0)               // SERIAL_INIT
                    val div = ch341Divisor(115200)
                    var valDiv = if ((version.firstOrNull() ?: 0) > 0x27) div or 0x8000 else div
                    conn.controlOut(REQTYPE_VENDOR_DEVICE_OUT, 0x9A, 0x1312, valDiv)     // REG_DIVISOR|REG_PRESCALER
                    val lcr = 0x80 or 0x40 or 0x03                                       // RX|TX|CS8 (8N1)
                    if ((version.firstOrNull() ?: 0) >= 0x30) {
                        conn.controlOut(REQTYPE_VENDOR_DEVICE_OUT, 0x9A, 0x2518, lcr)    // REG_LCR2|REG_LCR
                    }
                    conn.controlOut(REQTYPE_VENDOR_DEVICE_OUT, 0xA4, (0x60.inv()) and 0xffff, 0) // DTR|RTS
                }
                c.driver.startsWith("CP210x") -> {
                    conn.controlOut(REQTYPE_VENDOR_INTERFACE_OUT, 0x00, 0x0001, 0)       // IFC_ENABLE
                    val baud = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(115200).array()
                    conn.controlOut(REQTYPE_VENDOR_INTERFACE_OUT, 0x1E, 0, 0, baud)      // SET_BAUDRATE
                    conn.controlOut(REQTYPE_VENDOR_INTERFACE_OUT, 0x03, 0x0800, 0)       // SET_LINE_CONTROL: 8N1
                    conn.controlOut(REQTYPE_VENDOR_INTERFACE_OUT, 0x07, 0x0303, 0)       // SET_MHS: DTR|RTS
                }
                else -> { // CDC-ACM
                    val lc = java.nio.ByteBuffer.allocate(7).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        .putInt(115200).put(0.toByte()).put(0.toByte()).put(8.toByte()).array()
                    conn.controlOut(REQTYPE_CLASS_INTERFACE_OUT, 0x20, 0, intf.id, lc)   // SET_LINE_CODING
                    conn.controlOut(REQTYPE_CLASS_INTERFACE_OUT, 0x22, 0x03, intf.id)    // SET_CONTROL_LINE_STATE: DTR|RTS
                }
            }
            return SerialPort(conn, outEp, inEp)
        }

        /** Divisor encoding ported from Linux drivers/usb/serial/ch341.c (ch341_get_divisor). */
        private fun ch341Divisor(speed: Int): Int {
            val clkRate = 48_000_000
            fun clkDiv(ps: Int, fact: Int) = 1 shl (12 - 3 * ps - fact)
            val minRates = (0..3).map { ps -> clkRate / (clkDiv(ps, 1) * 255) }
            var ps = 3
            while (ps >= 0 && speed < minRates[ps]) ps--
            check(ps >= 0) { "baud too low" }
            var fact = 1
            var clkDiv = clkDiv(ps, fact)
            var div = clkRate / (clkDiv * speed)
            if (div < 9 || div > 255) { div /= 2; clkDiv *= 2; fact = 0 }
            check(div >= 2) { "baud too high" }
            val err = { d: Int -> abs(clkRate / (clkDiv * d) - speed) }
            if (div < 255 && err(div + 1) < err(div)) div++
            if (div % 2 == 0) { div /= 2; clkDiv *= 2; fact = 0 }
            return ((0x100 - div) shl 8) or (fact shl 2) or ps
        }
    }
}
