package com.stratafield.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stratafield.app.ui.StrataFieldViewModel
import com.stratafield.app.ui.components.*
import com.stratafield.app.ui.theme.*
import com.stratafield.app.util.ExportManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: StrataFieldViewModel = viewModel()
) {
    val context = LocalContext.current
    val darkMode by ThemePreferences.isDarkMode(context).collectAsState()
    var autoSync by remember { mutableStateOf(true) }
    var highQualityImages by remember { mutableStateOf(true) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val projects by viewModel.projects.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val surveys by viewModel.projectSurveys.collectAsState()
    val sites by viewModel.projectSites.collectAsState()
    val trenches by viewModel.projectTrenches.collectAsState()
    val responses by viewModel.projectResponses.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.loadProjects() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Configure your StrataField experience",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsSection("Appearance") {
                    SettingsSwitchItem(
                        title = "Dark Mode",
                        subtitle = "Use dark theme throughout the app",
                        checked = darkMode,
                        onCheckedChange = { ThemePreferences.setDarkMode(context, it) }
                    )
                }
            }

            item {
                SettingsSection("Data Collection") {
                    SettingsSwitchItem(
                        title = "Auto Sync",
                        subtitle = "Automatically sync data when on a network",
                        checked = autoSync,
                        onCheckedChange = { autoSync = it }
                    )
                    SettingsSwitchItem(
                        title = "High Quality Images",
                        subtitle = "Store full resolution images",
                        checked = highQualityImages,
                        onCheckedChange = { highQualityImages = it }
                    )
                }
            }

            item {
                SettingsSection("Storage") {
                    SettingsActionItem(
                        title = "Export All Data",
                        subtitle = "Export all projects to CSV/Excel",
                        icon = Icons.Filled.Share,
                        onClick = { showExportDialog = true }
                    )
                    SettingsActionItem(
                        title = "Clear Cache",
                        subtitle = "Remove temporary files",
                        icon = Icons.Filled.DeleteSweep,
                        onClick = {
                            Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            item {
                SettingsSection("About") {
                    SettingsActionItem(
                        title = "About StrataField",
                        subtitle = "Version 1.0.0",
                        icon = Icons.Filled.Info,
                        onClick = { showAboutDialog = true }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "StrataField v1.0.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    text = "Archaeological Field Data Collection",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("About StrataField", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "StrataField is a comprehensive archaeological field data collection app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Features:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    listOf(
                        "• Custom form builder",
                        "• Custom forms",
                        "• Image attachments with GPS",
                        "• Drawing & graph paper tools",
                        "• Calculator for field calculations",
                        "• CSV/Excel export",
                        "• WiFi network sync for team collaboration",
                        "• Dashboard with analytics"
                    ).forEach { feature ->
                        Text(
                            text = feature,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close", color = NothingRed)
                }
            }
        )
    }

    if (showExportDialog) {
        var selectedType by remember { mutableStateOf("xlsx") }
        var selectedProjectId by remember { mutableStateOf(projects.firstOrNull()?.id) }
        var projectMenuExpanded by remember { mutableStateOf(false) }
        var directory by remember { mutableStateOf("Documents/StrataField") }

        LaunchedEffect(selectedProjectId) {
            selectedProjectId?.let { viewModel.loadProjectDetail(it) }
        }

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Export Data", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("xlsx", "csv").forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type.uppercase()) }
                            )
                        }
                    }

                    Box {
                        OutlinedButton(
                            onClick = { projectMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(projects.firstOrNull { it.id == selectedProjectId }?.name ?: "Select project", modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = projectMenuExpanded, onDismissRequest = { projectMenuExpanded = false }) {
                            projects.forEach { project ->
                                DropdownMenuItem(
                                    text = { Text(project.name) },
                                    onClick = {
                                        selectedProjectId = project.id
                                        projectMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    StrataTextField(
                        value = directory,
                        onValueChange = { directory = it },
                        label = "Directory"
                    )
                    Text(
                        "Exports are saved under the app documents directory. Directory text is used as your target label.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val project = currentProject?.project ?: projects.firstOrNull { it.id == selectedProjectId }
                        if (project == null) {
                            Toast.makeText(context, "Select a project first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            if (selectedType == "csv") {
                                ExportManager.exportProjectToCSV(context, project, surveys, sites, trenches, responses)
                            } else {
                                ExportManager.exportProjectToExcel(context, project, surveys, sites, trenches, responses)
                            }
                            Toast.makeText(context, "Exported ${project.name} as ${selectedType.uppercase()}", Toast.LENGTH_LONG).show()
                            showExportDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel", color = NothingRed)
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}

@Composable
private fun SettingsActionItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
