package io.github.hackyday.irforge.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.hackyday.irforge.ForgeApp
import io.github.hackyday.irforge.ui.ForgeTheme

/** Widget setup: pick a remote, then up to 4 buttons for the home screen. */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        val app = application as ForgeApp
        setContent {
            ForgeTheme(app.store) {
                WidgetConfigUi(app, appWidgetId, onDone = { finishOk(appWidgetId) }, onCancel = { finish() })
            }
        }
    }

    private fun finishOk(appWidgetId: Int) {
        RemoteWidgetProvider.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigUi(app: ForgeApp, appWidgetId: Int, onDone: () -> Unit, onCancel: () -> Unit) {
    val remotes by app.store.remotes.collectAsState()
    var remoteId by remember { mutableStateOf(app.store.widgetCfg(appWidgetId)?.remoteId ?: (remotes.firstOrNull()?.id ?: "")) }
    val remote = remotes.firstOrNull { it.id == remoteId }
    val allButtons: List<io.github.hackyday.irforge.data.RButton> = remote?.pages?.flatMap { it.buttons } ?: emptyList()
    var picked by remember(remoteId) {
        mutableStateOf<List<String>>(app.store.widgetCfg(appWidgetId)?.buttonIds ?: emptyList())
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Configure widget") }) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
            Text("Remote", style = MaterialTheme.typography.titleSmall)
            for (r in remotes) {
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        .clickable { remoteId = r.id; picked = emptyList() },
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = if (r.id == remoteId) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surface,
                    ),
                ) { Text(r.name, Modifier.padding(12.dp)) }
            }
            if (remote != null) {
                Text("Buttons (max 4)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                for (b in allButtons) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = b.id in picked,
                            onCheckedChange = { on ->
                                picked = if (on) {
                                    if (picked.size < 4) picked + b.id else picked
                                } else picked - b.id
                            },
                        )
                        Text("${b.icon} ${b.label}")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                Button(onClick = {
                    app.store.setWidgetCfg(appWidgetId, io.github.hackyday.irforge.data.WidgetCfg(remoteId, picked.toList()))
                    onDone()
                }, enabled = remoteId.isNotEmpty() && picked.isNotEmpty()) { Text("Save widget") }
                androidx.compose.material3.OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
