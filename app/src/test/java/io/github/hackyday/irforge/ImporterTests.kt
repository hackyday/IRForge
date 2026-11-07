package io.github.hackyday.irforge

import io.github.hackyday.irforge.encoder.ProtocolEncoder
import io.github.hackyday.irforge.importer.Importers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolEncoderTest {

    /** Decodes a NEC-style pattern back to (address, command), LSB-first. */
    private fun decodeNec(pattern: List<Int>): Pair<Int, Int> {
        val bits = pattern.drop(2).dropLast(1).chunked(2).map { (mark, space) -> if (space > mark * 2) 1 else 0 }
        assertEquals(32, bits.size)
        var v = 0L
        for (i in bits.indices) v = v or (bits[i].toLong() shl i)
        val addr = (v and 0xff).toInt()
        val addrInv = ((v shr 8) and 0xff).toInt()
        val cmd = ((v shr 16) and 0xff).toInt()
        val cmdInv = ((v shr 24) and 0xff).toInt()
        assertEquals("address inversion mismatch", (addr.inv() and 0xff), addrInv)
        assertEquals("command inversion mismatch", (cmd.inv() and 0xff), cmdInv)
        return addr to cmd
    }

    @Test
    fun `nec encodes valid frame with header and inversion`() {
        val p = ProtocolEncoder.nec(listOf(0x1D), listOf(0x52)).toList()
        assertEquals(9000, p[0])
        assertEquals(4500, p[1])
        assertEquals(67, p.size)  // header + 32 bits + trailing mark
        val (addr, cmd) = decodeNec(p)
        assertEquals(0x1D, addr)
        assertEquals(0x52, cmd)
    }

    @Test
    fun `nec round-trips all bits`() {
        for (addr in intArrayOf(0x00, 0xFF, 0x1D, 0x80)) {
            for (cmd in intArrayOf(0x00, 0xA1, 0x52)) {
                val (a, c) = decodeNec(ProtocolEncoder.nec(listOf(addr), listOf(cmd)).toList())
                assertEquals(addr, a)
                assertEquals(cmd, c)
            }
        }
    }

    @Test
    fun `flipper parsed NEC entry decodes to raw`() {
        val text = """
            Filetype: IR signals file
            Version: 1
            name: Power
            type: parsed
            protocol: NEC
            address: 1D E2 00 00
            command: 52 AD 00 00
        """.trimIndent()
        val signals = Importers.parse("x.ir", text)
        assertEquals(1, signals.size)
        assertEquals("flipper-ir", signals[0].sourceFormat)
        assertEquals(67, signals[0].pattern.size)
        val (addr, cmd) = decodeNec(signals[0].pattern)
        assertEquals(0x1D, addr)
        assertEquals(0x52, cmd)
    }

    @Test
    fun `samsung and sirc produce plausible frames`() {
        val sam = ProtocolEncoder.samsung(listOf(0x07), listOf(0x02)).toList()
        assertEquals(4500, sam[0])
        assertEquals(4500, sam[1])
        assertEquals(2 + 32 * 2 + 1, sam.size)
        val sony = ProtocolEncoder.sirc(listOf(0x1D), listOf(0x01), 15).toList()
        assertEquals(2400, sony[0])
        assertEquals(600, sony[1])
        assertEquals(2 + 15 * 2, sony.size)
    }

    @Test
    fun `unknown protocol returns null`() {
        assertNull(ProtocolEncoder.encode("Bogus", "01 00 00 00", "02 00 00 00"))
        assertNotNull(ProtocolEncoder.encode("RC5", "00 00 00 00", "0C 00 00 00"))
    }
}

class ProntoParserTest {

    @Test
    fun `decodes standard pronto hex`() {
        // 0000 raw osc, 006D → 4145146/109 = 38028 Hz, 2 pairs: 100, 50, 80, 50 cycles
        val pronto = "0000 006D 0002 0000 0064 0032 0050 0032"
        val s = Importers.parse("p.pronto", pronto)
        assertEquals(1, s.size)
        assertEquals(38028, s[0].freqHz)
        assertEquals((100L * 1_000_000 / 38028).toInt(), s[0].pattern[0])
        assertEquals((50L * 1_000_000 / 38028).toInt(), s[0].pattern[1])
    }

    @Test
    fun `rejects non-pronto text`() {
        assertTrue(Importers.parse("a.txt", "hello world this is not pronto").isEmpty())
    }
}

class LircParserTest {

    @Test
    fun `parses raw codes block`() {
        val conf = """
            begin remote
              name  livingroom_tv
              flags RAW_CODES
              frequency 38000
              begin codes
                  KEY_POWER    9000 4500 560 1690 560 560 560 1690
                  KEY_VOLUP    9000 4500 560 560 560 1690 560 560
              end codes
            end remote
        """.trimIndent()
        val s = Importers.parse("tv.lircd.conf", conf)
        assertEquals(2, s.size)
        assertEquals("KEY_POWER", s[0].name)
        assertEquals(38000, s[0].freqHz)
        assertEquals(listOf(9000, 4500, 560, 1690, 560, 560, 560, 1690), s[0].pattern)
    }

    @Test
    fun `skips hex protocol entries`() {
        val conf = """
            begin remote
              name x
              begin codes
                  KEY_1 0x1EC0
              end codes
            end remote
        """.trimIndent()
        assertTrue(Importers.parse("x.conf", conf).isEmpty())
    }
}

class CsvJsonParserTest {

    @Test
    fun `parses csv rows`() {
        val csv = """
            # name,frequency,pattern...
            Power,38000,9000 4500 560 560
            Mute,38000,9000 4500 560 1690
        """.trimIndent()
        val s = Importers.parse("r.csv", csv)
        assertEquals(2, s.size)
        assertEquals("Power", s[0].name)
        assertEquals(38000, s[0].freqHz)
        assertEquals(4, s[0].pattern.size)
    }

    @Test
    fun `parses json array`() {
        val json = """[{"name":"Power","frequency":38000,"data":[9000,4500,560,560]}]"""
        val s = Importers.parse("c.json", json)
        assertEquals(1, s.size)
        assertEquals("Power", s[0].name)
        assertEquals(listOf(9000, 4500, 560, 560), s[0].pattern)
    }
}
