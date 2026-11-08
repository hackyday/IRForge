package io.github.hackyday.irforge.importer

import android.content.Context
import io.github.hackyday.irforge.data.Code
import io.github.hackyday.irforge.data.Store
import io.github.hackyday.irforge.data.newId
import io.github.hackyday.irforge.editor.assignCodesToRemote
import io.github.hackyday.irforge.editor.buildFromTemplate
import io.github.hackyday.irforge.editor.templateById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One-tap "Load SmartLim demo": import bundled codes + auto-build the remote. */
object DemoImporter {

    /** Returns (newly imported count, created remote id). */
    suspend fun load(ctx: Context, store: Store): Pair<Int, String?> = withContext(Dispatchers.IO) {
        val text = ctx.assets.open("smartlim_combined.ir").bufferedReader().use { it.readText() }
        val parsed = Importers.parse("smartlim_combined.ir", text)
        val fresh = parsed
            .filter { c -> store.codes.value.none { it.pattern == c.pattern } }
            .map { c ->
                Code(
                    id = newId(), name = c.name, freqHz = c.freqHz, dutyCycle = c.dutyCycle,
                    pattern = c.pattern, source = "import", sourceFormat = c.sourceFormat,
                    protocol = c.protocol, createdAt = System.currentTimeMillis(),
                )
            }
        store.addCodes(fresh)
        // Wire every parsed code (fresh or already in the library) into a new remote.
        val wired = parsed.mapNotNull { p -> store.codes.value.firstOrNull { it.pattern == p.pattern } }
        var remoteId: String? = null
        if (wired.isNotEmpty()) {
            val remote = buildFromTemplate(templateById("tv"), "SmartLim TV")
            val (assigned, _) = assignCodesToRemote(remote, wired)
            store.upsertRemote(assigned)
            remoteId = assigned.id
        }
        fresh.size to remoteId
    }
}
