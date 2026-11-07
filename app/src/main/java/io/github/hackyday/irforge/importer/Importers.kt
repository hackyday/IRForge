package io.github.hackyday.irforge.importer

import io.github.hackyday.irforge.encoder.ProtocolEncoder

data class ImportedCode(
    val name: String,
    val freqHz: Int,
    val dutyCycle: Float,
    val pattern: List<Int>,
    val sourceFormat: String,
    val protocol: String? = null,
)

/**
 * Multi-format IR code importer. The native format is Flipper Zero `.ir` (also used by
 * Bruce firmware — "Filetype: Bruce IR File"), which is what the SmartLim dataset uses.
 * Bonus: LIRC .lircd.conf (RAW_CODES), Pronto hex, and generic CSV/JSON.
 */
object Importers {

    fun parse(fileName: String, text: String): List<ImportedCode> {
        val t = text.trim()
        val list = when {
            t.startsWith("Filetype:", ignoreCase = true) -> parseFlipper(t)
            t.contains("begin remote", ignoreCase = true) -> parseLirc(t)
            isPronto(t) -> listOfNotNull(parsePronto(t))
            t.startsWith("[") || t.startsWith("{") -> parseJsonCodes(t)
            else -> parseCsv(fileName, t)
        }
        return list.filter { it.pattern.isNotEmpty() && it.freqHz in 1000..1_000_000 }
    }

    // ---------------- Flipper / Bruce .ir ----------------

    fun parseFlipper(text: String): List<ImportedCode> {
        val out = ArrayList<ImportedCode>()
        var name = ""
        var type = "raw"
        var freq = 38000
        var duty = 0.33f
        var data = ""
        var protocol = ""
        var address = ""
        var command = ""

        fun flush() {
            if (name.isEmpty()) return
            when (type) {
                "raw" -> {
                    val pat = data.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
                        .mapNotNull { it.toIntOrNull() }
                    if (pat.size >= 2) out.add(ImportedCode(name, freq, duty, pat, "flipper-ir"))
                }
                "parsed" -> {
                    val pat = ProtocolEncoder.encode(protocol, address, command)
                    if (pat != null) out.add(ImportedCode(name, freq, duty, pat.toList(), "flipper-ir", protocol))
                }
            }
            name = ""; type = "raw"; freq = 38000; duty = 0.33f; data = ""; protocol = ""; address = ""; command = ""
        }

        for (rawLine in text.lines()) {
            val line = rawLine.substringBefore('#').trim()   // '#' starts a comment
            if (line.isEmpty()) continue
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val key = line.substring(0, idx).trim().lowercase()
            val value = line.substring(idx + 1).trim()
            when (key) {
                "name" -> { flush(); name = value }
                "type" -> type = value.lowercase()
                "frequency" -> freq = value.toIntOrNull() ?: freq
                "duty_cycle" -> duty = value.toFloatOrNull() ?: duty
                "data" -> data = value
                "protocol" -> protocol = value
                "address" -> address = value
                "command" -> command = value
            }
        }
        flush()
        return out
    }

    /** Export codes back to Flipper/Bruce .ir format (round-trip with the SmartLim files). */
    fun exportFlipper(codes: List<Triple<String, Int, List<Int>>>): String = buildString {
        append("Filetype: IR signals file\n")
        append("Version: 1\n")
        append("#\n# Exported by IRForge\n#\n")
        for ((name, freq, pat) in codes) {
            append("#\n# ${name}\n")
            append("name: ${name}\n")
            append("type: raw\n")
            append("frequency: ${freq}\n")
            append("duty_cycle: 0.33\n")
            append("data: ${pat.joinToString(" ")}\n")
        }
        append("#\n")
    }

    // ---------------- LIRC (.lircd.conf) ----------------

    fun parseLirc(text: String): List<ImportedCode> {
        val out = ArrayList<ImportedCode>()
        var freq = 38000
        var inCodes = false
        for (raw in text.lines()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val lower = line.lowercase()
            when {
                lower.startsWith("frequency") -> freq = line.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull() ?: freq
                lower.startsWith("begin codes") -> inCodes = true
                lower.startsWith("end codes") -> inCodes = false
                inCodes -> {
                    val tok = line.split(Regex("\\s+"))
                    if (tok.size < 2) continue
                    val key = tok[0]
                    // 0x-prefixed tokens are hex (protocol-encoded codes); plain tokens are decimal µs.
                    val nums = tok.drop(1).mapNotNull { t ->
                        if (t.startsWith("0x") || t.startsWith("0X")) t.substring(2).toLongOrNull(16) else t.toLongOrNull()
                    }
                    if (nums.size >= 2) {
                        // RAW_CODES: pulse/space µs starting with a pulse. Hex-protocol entries
                        // (2 short tokens) are skipped — they need a protocol decoder.
                        val plausible = nums.all { it in 1..150_000 } && nums.size >= 4
                        if (plausible) {
                            out.add(ImportedCode(key, freq, 0.33f, nums.map { it.toInt() }, "lirc"))
                        }
                    }
                }
            }
        }
        return out
    }

    // ---------------- Pronto hex ----------------

    private fun isPronto(t: String): Boolean {
        val words = t.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        if (words.size < 6) return false
        return words.all { it.matches(Regex("^[0-9A-Fa-f]{1,4}$")) } &&
            words[0].removePrefix("0x").toIntOrNull(16) == 0
    }

    fun parsePronto(text: String): ImportedCode? {
        val words = text.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
            .map { it.removePrefix("0x").toInt(16) }
        if (words.size < 6 || words[0] != 0) return null
        val freq = 4_145_146 / words[1].coerceAtLeast(1)
        val once = words[2]
        val body = words.drop(4)
        val pat = ArrayList<Int>()
        val count = minOf(once * 2, body.size)
        for (i in 0 until count) {
            val cycles = body[i].coerceAtLeast(1)
            pat.add((cycles.toLong() * 1_000_000 / freq).toInt().coerceIn(1, 200_000))
        }
        if (pat.size % 2 == 0) pat.add(pat.lastOrNull() ?: 560)
        return ImportedCode("Pronto signal", freq, 0.33f, pat, "pronto")
    }

    // ---------------- CSV / JSON ----------------

    fun parseCsv(fileName: String, text: String): List<ImportedCode> {
        val out = ArrayList<ImportedCode>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val tok = line.split(Regex("[,;]"))
            if (tok.size < 3) continue
            val name = tok[0].trim().ifEmpty { fileName }
            val freq = tok[1].trim().toIntOrNull() ?: continue
            val pat = tok.drop(2).joinToString(" ").trim().split(Regex("[\\s]+"))
                .mapNotNull { it.toFloatOrNull()?.toInt() }
            if (pat.size >= 2) out.add(ImportedCode(name, freq, 0.33f, pat, "csv"))
        }
        return out
    }

    fun parseJsonCodes(text: String): List<ImportedCode> = runCatching {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(text)
        val arr = when {
            root is kotlinx.serialization.json.JsonArray -> root
            root is kotlinx.serialization.json.JsonObject -> root["codes"] as? kotlinx.serialization.json.JsonArray
                ?: root["signals"] as? kotlinx.serialization.json.JsonArray
                ?: kotlinx.serialization.json.JsonArray(emptyList())
            else -> return emptyList()
        }
        arr.mapNotNull { el ->
            val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val name = (o["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "code"
            val freq = (o["frequency"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
                ?: (o["freq"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 38000
            val duty = (o["duty_cycle"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toFloatOrNull() ?: 0.33f
            val data = o["data"] as? kotlinx.serialization.json.JsonArray
                ?: o["pattern"] as? kotlinx.serialization.json.JsonArray ?: return@mapNotNull null
            val pat = data.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }
            if (pat.size >= 2) ImportedCode(name, freq, duty, pat, "json") else null
        }
    }.getOrDefault(emptyList())
}
