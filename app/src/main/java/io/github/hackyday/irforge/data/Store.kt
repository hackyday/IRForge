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

    private val _codes = MutableStateFlow(loadList<Code>("codes.json"))
    val codes = _codes.asStateFlow()

    private val _remotes = MutableStateFlow(loadList<Remote>("remotes.json"))
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
            .getOrDefault(emptyList()).toMutableList()

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }

    fun newId(): String = UUID.randomUUID().toString().take(12)

    fun saveCodes() = writeAtomic(File(dir, "codes.json"), json.encodeToString(_codes.value))
    fun saveRemotes() = writeAtomic(File(dir, "remotes.json"), json.encodeToString(_remotes.value))
    fun saveMacros() = writeAtomic(File(dir, "macros.json"), json.encodeToString(_macros.value))
    fun saveSettings() = writeAtomic(File(dir, "settings.json"), json.encodeToString(_settings.value))
    fun saveWidgetPrefs() = writeAtomic(File(dir, "widgets.json"), json.encodeToString(_widgetPrefs.value))

    // ---- codes ----
    fun upsertCode(code: Code) {
        val list = _codes.value.toMutableList()
        val i = list.indexOfFirst { it.id == code.id }
        if (i >= 0) list[i] = code else list.add(code)
        _codes.value = list; saveCodes()
    }
    fun deleteCode(id: String) { _codes.value = _codes.value.filterNot { it.id == id }.toMutableList(); saveCodes() }
    fun codeById(id: String): Code? = _codes.value.firstOrNull { it.id == id }
    fun codeByPattern(p: List<Int>): Code? = _codes.value.firstOrNull { it.pattern == p }

    // ---- remotes ----
    fun upsertRemote(remote: Remote) {
        val list = _remotes.value.toMutableList()
        val i = list.indexOfFirst { it.id == remote.id }
        if (i >= 0) list[i] = remote else list.add(remote)
        _remotes.value = list; saveRemotes()
    }
    fun deleteRemote(id: String) { _remotes.value = _remotes.value.filterNot { it.id == id }.toMutableList(); saveRemotes() }
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
    fun exportAll(): String {
        @Serializable data class Backup(val codes: List<Code>, val remotes: List<Remote>, val macros: List<Macro>, val settings: Settings)
        return json.encodeToString(Backup(_codes.value, _remotes.value, _macros.value, _settings.value))
    }

    fun restoreAll(text: String): Boolean {
        @Serializable data class Backup(val codes: List<Code> = emptyList(), val remotes: List<Remote> = emptyList(), val macros: List<Macro> = emptyList(), val settings: Settings = Settings())
        return runCatching {
            val b = json.decodeFromString<Backup>(text)
            _codes.value = b.codes.toMutableList(); saveCodes()
            _remotes.value = b.remotes.toMutableList(); saveRemotes()
            _macros.value = b.macros.toMutableList(); saveMacros()
            _settings.value = b.settings; saveSettings()
            true
        }.getOrDefault(false)
    }
}
