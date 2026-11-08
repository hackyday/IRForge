package io.github.hackyday.irforge.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.hackyday.irforge.ForgeApp
import io.github.hackyday.irforge.ui.screens.EditorScreen
import io.github.hackyday.irforge.ui.screens.HardwareScreen
import io.github.hackyday.irforge.ui.screens.HomeScreen
import io.github.hackyday.irforge.ui.screens.ImportScreen
import io.github.hackyday.irforge.ui.screens.LibraryScreen
import io.github.hackyday.irforge.ui.screens.MacrosScreen
import io.github.hackyday.irforge.ui.screens.RemoteViewScreen
import io.github.hackyday.irforge.ui.screens.RunMacroScreen
import io.github.hackyday.irforge.ui.screens.SettingsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Remotes", Icons.Filled.Home),
    Tab("library", "Codes", Icons.Filled.List),
    Tab("macros", "Macros", Icons.Filled.PlayArrow),
    Tab("hardware", "Hardware", Icons.Filled.Usb),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun AppNav(app: ForgeApp) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    val showBars = route in TABS.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBars) {
                NavigationBar {
                    for (tab in TABS) {
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(pad),
        ) {
            composable("home") { HomeScreen(app, nav) }
            composable("library") { LibraryScreen(app, nav) }
            composable("macros") { MacrosScreen(app, nav) }
            composable("hardware") { HardwareScreen(app, nav) }
            composable("settings") { SettingsScreen(app, nav) }
            composable("remote/{rid}") { entry ->
                RemoteViewScreen(app, nav, entry.arguments?.getString("rid") ?: "")
            }
            composable(
                "editor/{rid}?btn={btn}",
                arguments = listOf(androidx.navigation.navArgument("btn") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                }),
            ) { entry ->
                EditorScreen(app, nav, entry.arguments?.getString("rid") ?: "", entry.arguments?.getString("btn") ?: "")
            }
            composable("import") { ImportScreen(app, nav) }
            composable("runmacro/{mid}") { entry ->
                RunMacroScreen(app, nav, entry.arguments?.getString("mid") ?: "")
            }
        }
    }
}
