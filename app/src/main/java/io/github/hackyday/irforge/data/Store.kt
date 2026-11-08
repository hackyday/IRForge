package io.github.hackyday.irforge.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class WidgetPrefsEntry(val appWidgetId: Int, val cfg: WidgetCfg)

class Store(private val ctx: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val dir: File get() = ctx.filesDir.apply { mkdirs() }

    // Single background writer: serializes every disk write off the main thread.
    private val writeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private val _codes = MutableStateFlow(loadList<Code>("codes.json"))
    val codes = _codes.asStateFlow()

    private val _remotes = MutableStateFlow(sanitizeRemotes(loadList<Remote>("remotes.json")))
    val remotes = _remotes.asStateFlow()

    private val _macros = MutableStateFlow(loadList<Macro>("macros.json"))
    val macros = _macros.asStateFlow()

    private val _settings = MutableStateFlow(
        runCatching { json.decodeFromString<Settings>(File(dir, "settings.json").takeIf { it.exists() }?.readText() ?: "") }
            .getOrDefault(Settings())
    )
    val settings = _settings.asStateFlow()

    private val _widgetPrefs = MutableStateFlow(loadList<WidgetPrefsEntry>("widgets.json"))
    val widgetPrefs = _widgetPrefs.asStateFlow()

    private inline fun <reified R> loadList(name: String): MutableList<R> =
        runCatching { json.decodeFromString<List<R>>(File(dir, name).takeIf { it.exists() }?.readText() ?: "[]") }
            .getOrElse {
                // Corrupt file: back it up for the user instead of silently wiping it.
                val f = File(dir, name)
                if (f.exists()) runCatching { f.renameTo(File(dir, "$name.corrupt-${System.currentTimeMillis()}")) }
                emptyList()
            }
            .toMutableList()

    /** Glyphs missing from Android fonts (render as tofu) — remap on load. */
    private fun remapIcon(icon: String): String = when (icon) {
        "⏻" -> "🔌"
        "⏶" -> "▲"
        "⏷" -> "▼"
        "🅼" -> "🔄"
        else -> icon
    }

    /** Sanity-clamp button geometry on load so no drag accident can strand a button off-screen. */
    private fun sanitizeRemotes(list: List<Remote>): List<Remote> = list.map { r ->
        r.copy(pages = r.pages.map { p ->
            p.copy(buttons = p.buttons.map { b ->
                b.copy(
                    icon = remapIcon(b.icon),
                    x = b.x.coerceIn(0f, 1040f),
                    y = b.y.coerceIn(0f, 5000f),
                    w = b.w.coerceIn(50f, 1060f),
                    h = b.h.coerceIn(40f, 900f),
                    opacity = b.opacity.coerceIn(0.05f, 1f),
                    fontSize = b.fontSize.coerceIn(12f, 90f),
                )
            })
        })
    }

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, "${file.name}.${System.nanoTime()}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    private fun saveAsync(name: String, content: () -> String) {
        writeExecutor.execute {
            runCatching { writeAtomic(File(dir, name), content()) }
        }
    }

    fun newId(): String = UUID.randomUUID().toString().take(12)

    fun saveCodes() = saveAsync("codes.json") { json.encodeToString(_codes.value) }
    fun saveRemotes() = saveAsync("remotes.json") { json.encodeToString(_remotes.value) }
    fun saveMacros() = saveAsync("macros.json") { json.encodeToString(_macros.value) }
    fun saveSettings() = saveAsync("settings.json") { json.encodeToString(_settings.value) }
    fun saveWidgetPrefs() = saveAsync("widgets.json") { json.encodeToString(_widgetPrefs.value) }

    private fun notifyWidgets() = runCatching {
        io.github.hackyday.irforge.widget.RemoteWidgetProvider.updateAll(ctx)
    }

    // ---- codes ----
    fun upsertCode(code: Code) {
        val list = _codes.value.toMutableList()
        val i = list.indexOfFirst { it.id == code.id }
        if (i >= 0) list[i] = code else list.add(code)
        _codes.value = list; saveCodes(); notifyWidgets()
    }

    /** Batch insert (imports): one save + one widget refresh for the whole list. */
    fun addCodes(list: List<Code>) {
        if (list.isEmpty()) return
        _codes.value = (_codes.value + list).toMutableList()
        saveCodes(); notifyWidgets()
    }

    fun deleteCode(id: String) { _codes.value = _codes.value.filterNot { it.id == id }.toMutableList(); saveCodes(); notifyWidgets() }
    fun codeById(id: String): Code? = _codes.value.firstOrNull { it.id == id }
    fun codeByPattern(p: List<Int>): Code? = _codes.value.firstOrNull { it.pattern == p }

    // ---- remotes ----
    fun upsertRemote(remote: Remote) {
        val list = _remotes.value.toMutableList()
        val i = list.indexOfFirst { it.id == remote.id }
        if (i >= 0) list[i] = remote else list.add(remote)
        _remotes.value = list; saveRemotes(); notifyWidgets()
    }
    fun deleteRemote(id: String) { _remotes.value = _remotes.value.filterNot { it.id == id }.toMutableList(); saveRemotes(); notifyWidgets() }
    fun remoteById(id: String): Remote? = _remotes.value.firstOrNull { it.id == id }

    fun updateRemote(id: String, transform: (Remote) -> Remote) {
        remoteById(id)?.let { upsertRemote(transform(it).copy(updatedAt = System.currentTimeMillis())) }
    }
    fun updatePage(remoteId: String, pageId: String, transform: (Page) -> Page) =
        updateRemote(remoteId) { r -> r.copy(pages = r.pages.map { if (it.id == pageId) transform(it) else it }) }

    fun updateButton(remoteId: String, pageId: String, buttonId: String, transform: (RButton) -> RButton) =
        updatePage(remoteId, pageId) { p ->
            p.copy(buttons = p.buttons.map { if (it.id == buttonId) transform(it) else it })
        }

    fun removeButton(remoteId: String, pageId: String, buttonId: String) =
        updatePage(remoteId, pageId) { p -> p.copy(buttons = p.buttons.filterNot { it.id == buttonId }) }

    // ---- macros ----
    fun upsertMacro(macro: Macro) {
        val list = _macros.value.toMutableList()
        val i = list.indexOfFirst { it.id == macro.id }
        if (i >= 0) list[i] = macro else list.add(macro)
        _macros.value = list; saveMacros()
    }
    fun deleteMacro(id: String) { _macros.value = _macros.value.filterNot { it.id == id }.toMutableList(); saveMacros() }

    // ---- settings ----
    fun updateSettings(transform: (Settings) -> Settings) { _settings.value = transform(_settings.value); saveSettings() }

    // ---- widget prefs ----
    fun setWidgetCfg(appWidgetId: Int, cfg: WidgetCfg) {
        val list = _widgetPrefs.value.filterNot { it.appWidgetId == appWidgetId }.toMutableList()
        list.add(WidgetPrefsEntry(appWidgetId, cfg))
        _widgetPrefs.value = list; saveWidgetPrefs()
    }
    fun widgetCfg(appWidgetId: Int): WidgetCfg? = _widgetPrefs.value.firstOrNull { it.appWidgetId == appWidgetId }?.cfg
    fun deleteWidgetCfg(appWidgetId: Int) { _widgetPrefs.value = _widgetPrefs.value.filterNot { it.appWidgetId == appWidgetId }.toMutableList(); saveWidgetPrefs() }

    // ---- backup/restore ----
    @Serializable
    private data class Backup(
        val codes: List<Code> = emptyList(),
        val remotes: List<Remote> = emptyList(),
        val macros: List<Macro> = emptyList(),
        val settings: Settings = Settings(),
        val widgets: List<WidgetPrefsEntry> = emptyList(),
    )

    fun exportAll(): String =
        json.encodeToString(Backup(_codes.value, _remotes.value, _macros.value, _settings.value, _widgetPrefs.value))

    fun restoreAll(text: String): Boolean = runCatching {
        val b = json.decodeFromString<Backup>(text)
        _codes.value = b.codes.toMutableList(); saveCodes()
        _remotes.value = b.remotes.toMutableList(); saveRemotes()
        _macros.value = b.macros.toMutableList(); saveMacros()
        _settings.value = b.settings; saveSettings()
        _widgetPrefs.value = b.widgets.toMutableList(); saveWidgetPrefs()
        true
    }.getOrDefault(false)
}
