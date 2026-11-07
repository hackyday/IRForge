package io.github.hackyday.irforge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.hackyday.irforge.data.Code
import io.github.hackyday.irforge.data.newId
import io.github.hackyday.irforge.importer.Importers
import io.github.hackyday.irforge.ui.AppNav
import io.github.hackyday.irforge.ui.ForgeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ForgeApp
        handleImportIntent(intent)
        setContent {
            ForgeTheme(app.store) { AppNav(app) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleImportIntent(intent)
    }

    /** Opens .ir files shared into the app (file managers, Flipper share, USB attach). */
    private fun handleImportIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri: Uri = intent.data ?: return
        val app = application as ForgeApp
        app.appScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            } ?: run {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Could not read file", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val parsed = withContext(Dispatchers.IO) { Importers.parse(uri.lastPathSegment ?: "signal.ir", text) }
            val fresh = parsed.filter { c -> app.store.codes.value.none { it.pattern == c.pattern } }
                .map { c ->
                    Code(
                        id = newId(), name = c.name, freqHz = c.freqHz, dutyCycle = c.dutyCycle,
                        pattern = c.pattern, source = "import", sourceFormat = c.sourceFormat,
                        protocol = c.protocol, createdAt = System.currentTimeMillis(),
                    )
                }
            app.store.addCodes(fresh)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "Imported ${fresh.size} signal(s) (${parsed.size - fresh.size} already present)",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
