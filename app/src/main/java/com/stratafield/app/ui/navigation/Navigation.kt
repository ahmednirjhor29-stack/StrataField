package com.stratafield.app.ui.navigation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.stratafield.app.ui.screens.dashboard.DashboardScreen
import com.stratafield.app.ui.screens.datamanagement.DataManagementScreen
import com.stratafield.app.ui.screens.forms.FormBuilderScreen
import com.stratafield.app.ui.screens.protection.ProtectionScreen
import com.stratafield.app.ui.screens.projects.ProjectDetailScreen
import com.stratafield.app.ui.screens.projects.ProjectsScreen
import com.stratafield.app.ui.screens.settings.SettingsScreen
import com.stratafield.app.ui.screens.sync.SyncScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Outlined.Dashboard, Icons.Filled.Dashboard)
    data object Projects : Screen("projects", "Projects", Icons.Outlined.Folder, Icons.Filled.Folder)
    data object DataManagement : Screen("data", "Data", Icons.Outlined.Folder, Icons.Filled.Folder)
    data object Sync : Screen("sync", "Sync", Icons.Outlined.Sync, Icons.Filled.Sync)
    data object Protection : Screen("protection", "Protect", Icons.Outlined.Security, Icons.Filled.Security)
    data object Settings : Screen("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}

object Routes {
    const val DASHBOARD = "dashboard"
    const val PROJECTS = "projects"
    const val PROJECT_DETAIL = "project/{projectId}"
    const val FORM_BUILDER = "project/{projectId}/forms"
    const val DATA_MANAGEMENT = "data"
    const val SYNC = "sync"
    const val PROTECTION = "protection"
    const val SETTINGS = "settings"

    fun projectDetail(projectId: Long) = "project/$projectId"
    fun formBuilder(projectId: Long) = "project/$projectId/forms"
}

@Composable
fun StrataFieldApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { BottomNavBar(navController) }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Routes.DASHBOARD,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        onNavigateToProjects = { navController.navigate(Routes.PROJECTS) },
                        onNavigateToProject = { projectId ->
                            navController.navigate(Routes.projectDetail(projectId))
                        }
                    )
                }
                composable(Routes.PROJECTS) {
                    ProjectsScreen(
                        onProjectClick = { projectId ->
                            navController.navigate(Routes.projectDetail(projectId))
                        }
                    )
                }
                composable(Routes.PROJECT_DETAIL) { backStackEntry ->
                    val projectId = backStackEntry.arguments?.getString("projectId")?.toLongOrNull() ?: 0L
                    ProjectDetailScreen(
                        projectId = projectId,
                        onBack = { navController.popBackStack() },
                        onOpenFormBuilder = { pid ->
                            navController.navigate(Routes.formBuilder(pid))
                        }
                    )
                }
                composable(Routes.FORM_BUILDER) { backStackEntry ->
                    val projectId = backStackEntry.arguments?.getString("projectId")?.toLongOrNull() ?: 0L
                    FormBuilderScreen(
                        projectId = projectId,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.DATA_MANAGEMENT) {
                    DataManagementScreen()
                }
                composable(Routes.SYNC) {
                    SyncScreen()
                }
                composable(Routes.PROTECTION) {
                    ProtectionScreen()
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen()
                }
            }
            CompassOverlay(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
            )
        }
    }
}

@Composable
private fun CompassOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var heading by remember { mutableStateOf(0f) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientation = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                heading = Math.toDegrees(orientation[0].toDouble()).toFloat().let {
                    if (it < 0) it + 360f else it
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    Surface(
        modifier = modifier.size(46.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        tonalElevation = 4.dp,
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .size(34.dp)
                    .rotate(-heading)
            ) {
                drawCircle(Color.Transparent)
                val needle = Path().apply {
                    moveTo(size.width / 2f, 2f)
                    lineTo(size.width / 2f - 5f, size.height / 2f + 4f)
                    lineTo(size.width / 2f + 5f, size.height / 2f + 4f)
                    close()
                }
                drawPath(needle, Color(0xFFD71920))
                drawLine(
                    color = Color(0xFF2C2C2C),
                    start = Offset(size.width / 2f, size.height - 2f),
                    end = Offset(size.width / 2f, size.height / 2f + 5f),
                    strokeWidth = 3f
                )
            }
            Text(
                text = "N",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun BottomNavBar(navController: NavHostController) {
    val items = listOf(
        Screen.Dashboard,
        Screen.Projects,
        Screen.DataManagement,
        Screen.Sync,
        Screen.Protection,
        Screen.Settings
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        items.forEach { screen ->
            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) screen.selectedIcon else screen.icon,
                        contentDescription = screen.label
                    )
                },
                label = {
                    Text(
                        screen.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}
