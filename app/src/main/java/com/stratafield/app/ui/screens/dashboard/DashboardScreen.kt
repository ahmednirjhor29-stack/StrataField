package com.stratafield.app.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stratafield.app.data.model.Project
import com.stratafield.app.ui.StrataFieldViewModel
import com.stratafield.app.ui.components.*
import com.stratafield.app.ui.theme.*

@Composable
fun DashboardScreen(
    onNavigateToProjects: () -> Unit,
    onNavigateToProject: (Long) -> Unit,
    viewModel: StrataFieldViewModel = viewModel()
) {
    val stats by viewModel.dashboardStats.collectAsState()
    val recentProjects by viewModel.recentProjects.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDashboard()
        viewModel.loadProjects()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header
            Column {
                Text(
                    text = "StrataField",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Archaeological Field Data Collection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            // Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Projects",
                    value = stats?.totalProjects?.toString() ?: "0",
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Folder,
                    accentColor = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    title = "Surveys",
                    value = stats?.totalSurveys?.toString() ?: "0",
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Map,
                    accentColor = NothingRed
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Sites",
                    value = stats?.totalSites?.toString() ?: "0",
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Place,
                    accentColor = NothingInfo
                )
                StatCard(
                    title = "Trenches",
                    value = stats?.totalTrenches?.toString() ?: "0",
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.GridOn,
                    accentColor = NothingWarning
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Images",
                    value = stats?.totalImages?.toString() ?: "0",
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Photo,
                    accentColor = NothingRed
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        item {
            // Weekly Activity
            Card(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ActivityItem(
                            label = "This Week",
                            value = stats?.surveysThisWeek?.toString() ?: "0",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ActivityItem(
                            label = "This Month",
                            value = stats?.surveysThisMonth?.toString() ?: "0",
                            color = NothingRed
                        )
                        ActivityItem(
                            label = "Completed",
                            value = stats?.completedForms?.toString() ?: "0",
                            color = NothingSuccess
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Recent Projects",
                action = {
                    TextButton(onClick = onNavigateToProjects) {
                        Text("View All", color = NothingRed)
                    }
                }
            )
        }

        if (recentProjects.isEmpty()) {
            item {
                EmptyState(
                    title = "No Projects Yet",
                    description = "Create your first research project to start collecting archaeological data."
                )
            }
        } else {
            items(recentProjects) { project ->
                ProjectCard(
                    project = project,
                    onClick = { onNavigateToProject(project.id) }
                )
            }
        }
    }
}

@Composable
private fun ActivityItem(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DonutChart(
    completed: Int,
    draft: Int,
    modifier: Modifier = Modifier
) {
    val total = completed + draft
    val completedFraction = if (total > 0) completed.toFloat() / total else 0f

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val progressColor = MaterialTheme.colorScheme.onSurface
        Canvas(
            modifier = Modifier
                .size(120.dp)
                .padding(8.dp)
        ) {
            val strokeWidth = 16.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2,
                (size.height - diameter) / 2
            )
            val arcSize = Size(diameter, diameter)

            // Background circle
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Completed portion
            if (completedFraction > 0) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * completedFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            LegendItem("Completed", completed, MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            LegendItem("Draft", draft, NothingRed)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Total: $total",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    value: Int,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = project.name.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = project.location.ifEmpty { "No location set" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Standard: ${project.standardType.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingRed
                )
            }
        }
    }
}