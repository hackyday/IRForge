package io.github.hackyday.irforge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.hackyday.irforge.ForgeApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunMacroScreen(app: ForgeApp, nav: NavController, mid: String) {
    val macros by app.store.macros.collectAsState()
    val codes by app.store.codes.collectAsState()
    val macro = macros.firstOrNull { it.id == mid }
    var progress by remember { mutableStateOf(0 to (macro?.steps?.size ?: 0)) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(mid) {
        if (macro != null) {
            val err = app.irEngine.runMacro(macro, onProgress = { i, total -> progress = i to total })
            if (err != null && err != "Cancelled") error = err
            done = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run · ${macro?.name ?: ""}") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (macro == null) {
                Text("Macro not found")
                return@Column
            }
            val (step, total) = progress
            Text("${macro.icon} ${macro.name}", style = MaterialTheme.typography.headlineSmall)
            if (!done) LinearProgressIndicator(progress = { if (total == 0) 0f else step.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            macro.steps.forEachIndexed { i, s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            when (s.type) {
                                "delay" -> "⏱ Wait ${s.ms} ms"
                                else -> "🔌 ${codes.firstOrNull { it.id == s.id }?.name ?: "(deleted code)"}"
                            },
                            color = if (i < step || done) MaterialTheme.colorScheme.onSurfaceVariant
                            else if (i == step && !done) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (done) {
                Text(
                    error ?: "✅ Macro finished",
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Button(onClick = { nav.popBackStack() }) { Text("Back") }
            } else {
                OutlinedButton(onClick = { nav.popBackStack() }) { Text("Stop") }
            }
        }
    }
}
