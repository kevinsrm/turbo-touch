package com.kevinsrm.turbotouch.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kevinsrm.turbotouch.AppGraph
import com.kevinsrm.turbotouch.TurboApp
import com.kevinsrm.turbotouch.service.TurboService
import com.kevinsrm.turbotouch.ui.screens.AppListScreen
import com.kevinsrm.turbotouch.ui.screens.DashboardScreen
import com.kevinsrm.turbotouch.ui.screens.ProfileEditorScreen
import com.kevinsrm.turbotouch.ui.screens.SettingsScreen
import com.kevinsrm.turbotouch.ui.theme.TurboTouchTheme

class MainActivity : ComponentActivity() {

    private lateinit var graph: AppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        graph = (application as TurboApp).graph
        if (graph.prefs.serviceEnabled) {
            TurboService.start(this)
        }
        setContent {
            TurboTouchTheme {
                TurboRoot(graph)
            }
        }
    }


}

private data class TabItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun TurboRoot(graph: AppGraph) {
    val navController = rememberNavController()
    val viewModel: TurboViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val tabs = listOf(
        TabItem("dashboard", "Painel", Icons.Filled.Home),
        TabItem("apps", "Apps", Icons.AutoMirrored.Filled.List),
        TabItem("settings", "Ajustes", Icons.Filled.Settings)
    )

    var setupComplete by remember { mutableStateOf(graph.prefs.setupComplete) }
    val context = LocalContext.current
    if (!setupComplete) {
        com.kevinsrm.turbotouch.ui.screens.SetupWizard { graph.prefs.setupComplete = true; setupComplete = true }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val current = navController.currentDestination?.route
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding)
        ) {
            composable("dashboard") {
                DashboardScreen(state = state, viewModel = viewModel)
            }
            composable("apps") {
                AppListScreen(
                    state = state,
                    viewModel = viewModel,
                    onOpenProfile = { pkg -> navController.navigate("profile/$pkg") }
                )
            }
            composable("settings") {
                SettingsScreen(state = state, viewModel = viewModel)
            }
            composable(
                route = "profile/{pkg}",
                arguments = listOf(navArgument("pkg") { type = NavType.StringType })
            ) { entry ->
                val pkg = entry.arguments?.getString("pkg").orEmpty()
                ProfileEditorScreen(
                    packageName = pkg,
                    state = state,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSaved = { graph.ads.showAfterAction(context) }
                )
            }
        }
    }
}
