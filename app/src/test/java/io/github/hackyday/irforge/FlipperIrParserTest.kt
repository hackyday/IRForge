package io.github.hackyday.irforge

import io.github.hackyday.irforge.importer.Importers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlipperIrParserTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/fixtures/$name")!!.bufferedReader().readText()

    @Test
    fun `parses single-signal Bruce file`() {
        val signals = Importers.parse("Power.ir", fixture("Power.ir"))
        assertEquals(1, signals.size)
        val s = signals[0]
        assertEquals("BTN4", s.name)
        assertEquals(38000, s.freqHz)
        assertEquals(0.33f, s.dutyCycle, 0.001f)
        // 9ms burst + 4.5ms gap + 32 bits + trailing mark = 67 values
        assertEquals(67, s.pattern.size)
        assertEquals(9084, s.pattern[0])
        assertEquals(4440, s.pattern[1])
        assertTrue(s.pattern.drop(2).all { it in 400..1800 })
    }

    @Test
    fun `parses multi-signal combined file with comments`() {
        val signals = Importers.parse("smartlim_combined.ir", fixture("smartlim_combined.ir"))
        assertEquals("expected all 16 SmartLim buttons", 16, signals.size)
        val names = signals.map { it.name }
        assertTrue("Power" in names)
        assertTrue("Volume_up" in names)
        assertTrue("Volume_down" in names)
        assertTrue("Wireless_screen" in names)
        for (s in signals) {
            assertEquals(38000, s.freqHz)
            assertTrue("pattern too short: ${s.name}", s.pattern.size >= 30)
            assertEquals("patterns must start with a mark", true, s.pattern[0] > s.pattern[1])
        }
    }

    @Test
    fun `round-trips through export`() {
        val original = Importers.parse("smartlim_combined.ir", fixture("smartlim_combined.ir"))
        val exported = Importers.exportFlipper(original.map { Triple(it.name, it.freqHz, it.pattern) })
        val reparsed = Importers.parseFlipper(exported)
        assertEquals(original.size, reparsed.size)
        for (i in original.indices) {
            assertEquals(original[i].name, reparsed[i].name)
            assertEquals(original[i].pattern, reparsed[i].pattern)
            assertEquals(original[i].freqHz, reparsed[i].freqHz)
        }
    }

    @Test
    fun `handles Filetype IR signals file header`() {
        val text = """
            Filetype: IR signals file
            Version: 1
            #
            name: Test
            type: raw
            frequency: 36000
            duty_cycle: 0.5
            data: 100 200 100 200
            #
        """.trimIndent()
        val signals = Importers.parse("x.ir", text)
        assertEquals(1, signals.size)
        assertEquals("Test", signals[0].name)
        assertEquals(36000, signals[0].freqHz)
    }
}
