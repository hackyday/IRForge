package io.github.hackyday.irforge.encoder

/**
 * Encodes IR protocols (as stored in Flipper "parsed" entries) into raw µs pulse/space
 * patterns. Bit order follows the protocol conventions (NEC LSB-first, SIRC MSB-first).
 */
object ProtocolEncoder {

    /** Parse a Flipper-style hex value like "1D FE 00 00" into bytes. */
    fun parseHexBytes(s: String): List<Int> =
        s.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
            .mapNotNull { it.removePrefix("0x").toUIntOrNull(16)?.toInt() }

    private fun bitsOf(value: Long, count: Int, lsbFirst: Boolean): List<Int> =
        (0 until count).map { i -> ((value shr (if (lsbFirst) i else (count - 1 - i))) and 1L).toInt() }

    /** Merge half-bit sequence into alternating mark/space pattern starting with a mark. */
    private fun halfbitsToPattern(halfbits: List<Boolean>, unitUs: Int): IntArray {
        val merged = ArrayList<Boolean>()
        for (h in halfbits) {
            if (merged.isEmpty() || merged.last() != h) merged.add(h)
        }
        val widths = ArrayList<Int>()
        var i = 0
        while (i < merged.size) {
            var j = i
            while (j < merged.size && merged[j] == merged[i]) j++
            widths.add((j - i) * unitUs)
            i = j
        }
        return if (merged.first()) widths.toIntArray()
        else intArrayOf(unitUs) + widths.toIntArray()
    }

    fun nec(addressBytes: List<Int>, commandBytes: List<Int>, extended: Boolean = false): IntArray {
        val addr = (addressBytes.getOrNull(0) ?: 0) or ((addressBytes.getOrNull(1) ?: 0) shl 8)
        val cmd = commandBytes.getOrNull(0) ?: 0
        // Frame layout: addr, ~addr, cmd, ~cmd (LSB-first), or addr16+cmd for extended.
        val invAddr = (addr.inv()) and 0xff
        val invCmd = (cmd.inv()) and 0xff
        val value = if (extended) {
            (addr and 0xffff).toLong() or ((cmd and 0xff).toLong() shl 16)
        } else {
            (addr and 0xff).toLong() or (invAddr.toLong() shl 8) or ((cmd and 0xff).toLong() shl 16) or (invCmd.toLong() shl 24)
        }
        val bits = bitsOf(value, if (extended) 24 else 32, lsbFirst = true)
        val p = mutableListOf(9000, 4500)
        for (b in bits) p.addAll(if (b == 1) listOf(560, 1690) else listOf(560, 560))
        p.add(560)
        return p.toIntArray()
    }

    fun samsung(addressBytes: List<Int>, commandBytes: List<Int>): IntArray {
        val addr = (addressBytes.getOrNull(0) ?: 0) or ((addressBytes.getOrNull(1) ?: 0) shl 8)
        val cmd = (commandBytes.getOrNull(0) ?: 0) or ((commandBytes.getOrNull(1) ?: 0) shl 8)
        val bits = bitsOf(addr.toLong(), 16, lsbFirst = true) + bitsOf(cmd.toLong(), 16, lsbFirst = true)
        val p = mutableListOf(4500, 4500)
        for (b in bits) p.addAll(if (b == 1) listOf(550, 1650) else listOf(550, 550))
        p.add(550)
        return p.toIntArray()
    }

    fun sirc(commandBytes: List<Int>, addressBytes: List<Int>, bits: Int = 15): IntArray {
        val cmd = (commandBytes.getOrNull(0) ?: 0) and 0x7f
        val addrBits = bits - 7
        val addr = (addressBytes.getOrNull(0) ?: 0) and ((1 shl addrBits) - 1)
        val value = cmd.toLong() or (addr.toLong() shl 7)
        // SIRC: 2.4 ms AGC mark, then data; bit0 = 600/600, bit1 = 1200/600, MSB first.
        val p = mutableListOf(2400, 600)
        for (b in bitsOf(value, bits, lsbFirst = false)) {
            p.add(if (b == 1) 1200 else 600)
            p.add(600)
        }
        return p.toIntArray()
    }

    fun rc5(address: Int, command: Int, toggle: Int = 0): IntArray {
        val field = 1
        val bits13 = listOf(field, toggle) + bitsOf(address.toLong(), 5, lsbFirst = false) +
                bitsOf(command.toLong(), 6, lsbFirst = false)
        // RC5 halves: '1' = space then mark; '0' = mark then space. Prepend AGC mark.
        val halfbits = mutableListOf<Boolean>(true)
        for (b in bits13) {
            if (b == 1) { halfbits.add(false); halfbits.add(true) } else { halfbits.add(true); halfbits.add(false) }
        }
        return halfbitsToPattern(halfbits, 889)
    }

    fun rc6(addressBytes: List<Int>, commandBytes: List<Int>): IntArray {
        val addr = (addressBytes.getOrNull(0) ?: 0) or ((addressBytes.getOrNull(1) ?: 0) shl 8)
        val cmd = commandBytes.getOrNull(0) ?: 0
        val unit = 444
        val p = mutableListOf(unit * 6, unit * 2, unit)          // leader + SB1 (mark)
        // SB2 '1' = space,mark; toggle bit doubled (encoding '01' or '10')
        p.add(unit); p.add(unit)                                  // SB2 = 1
        val toggleBits = listOf(0, 1)
        for (b in toggleBits) { p.add(if (b == 1) unit else unit * 2); p.add(if (b == 1) unit * 2 else unit) }
        for (b in bitsOf(addr.toLong(), 8, lsbFirst = true) + bitsOf(cmd.toLong(), 8, lsbFirst = true)) {
            p.add(if (b == 1) unit else unit * 2)
            p.add(if (b == 1) unit * 2 else unit)
        }
        return p.toIntArray()
    }

    /**
     * Dispatch on a Flipper protocol name. Returns null when unsupported.
     * Flipper hex values are 4 bytes; the first 1-2 bytes carry the meaning.
     * Standard NEC files store `addr ~addr cmd ~cmd` across the bytes; extended NEC
     * uses byte 1 as the high address byte instead.
     */
    fun encode(protocol: String, addressHex: String, commandHex: String): IntArray? {
        val a = parseHexBytes(addressHex)
        val c = parseHexBytes(commandHex)
        fun isInversePair(list: List<Int>): Boolean =
            list.size >= 2 && list[1] == ((list[0].inv()) and 0xff)
        return when (protocol.uppercase()) {
            "NEC" -> nec(a, c, extended = !(isInversePair(a) && isInversePair(c)) && (a.getOrNull(1) ?: 0) != 0)
            "NECEXT", "NEC42" -> nec(a, c, extended = true)
            "SAMSUNG" -> samsung(a, c)
            "SAMSUNG32" -> samsung(a, c)
            "SONY", "SIRC" -> sirc(c, a, 15)
            "SONY12", "SIRC12" -> sirc(c, a, 12)
            "SONY15", "SIRC15" -> sirc(c, a, 15)
            "SONY20", "SIRC20" -> sirc(c, a, 20)
            "RC5" -> rc5(a.getOrElse(0) { 0 }, c.getOrElse(0) { 0 })
            "RC5X" -> rc5(a.getOrElse(0) { 0 }, c.getOrElse(0) { 0 })
            "RC6" -> rc6(a, c)
            else -> null
        }
    }
}
