package com.stratafield.app.ui.screens.projects

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stratafield.app.data.model.*
import com.stratafield.app.ui.StrataFieldViewModel
import com.stratafield.app.ui.components.*
import com.stratafield.app.ui.theme.*
import com.stratafield.app.util.ExportManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: Long,
    onBack: () -> Unit,
    onOpenFormBuilder: ((Long) -> Unit)? = null,
    viewModel: StrataFieldViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val project by viewModel.currentProject.collectAsState()
    val surveys by viewModel.projectSurveys.collectAsState()
    val sites by viewModel.projectSites.collectAsState()
    val trenches by viewModel.projectTrenches.collectAsState()
    val responses by viewModel.projectResponses.collectAsState()
    val trenchDist by viewModel.trenchDistribution.collectAsState()
    val dailyProgress by viewModel.dailyProgress.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var surveySubTab by remember { mutableStateOf(0) } // 0 = Survey Data, 1 = Sites
    var showNewSite by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var activeFormTemplate by remember { mutableStateOf<FormTemplate?>(null) }
    var editingFormResponse by remember { mutableStateOf<FormResponse?>(null) }
    var activeResponse by remember { mutableStateOf<FormResponse?>(null) }
    var activeResponseTemplate by remember { mutableStateOf<FormTemplate?>(null) }

    LaunchedEffect(projectId) {
        viewModel.loadProjectDetail(projectId)
    }

    val surveyResponses = responses.filter { it.formType == FormType.SURVEY }
    val excavationResponses = responses.filter { it.formType == FormType.TRENCH }

    fun viewResponse(response: FormResponse) {
        scope.launch {
            activeResponseTemplate = viewModel.getFormTemplateForResponse(response)
            activeResponse = response
        }
    }

    val tabs = listOf("Dashboard", "Survey", "Excavation")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            StrataTopBar(
                title = project?.project?.name ?: "Project",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Filled.Share, contentDescription = "Export")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 1 || selectedTab == 2) {
                FloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            1 -> if (surveySubTab == 0) {
                                scope.launch {
                                    editingFormResponse = null
                                    activeFormTemplate = viewModel.getOrCreateProjectFormTemplate(FormType.SURVEY)
                                }
                            } else {
                                showNewSite = true
                            }
                            2 -> scope.launch {
                                editingFormResponse = null
                                activeFormTemplate = viewModel.getOrCreateProjectFormTemplate(FormType.TRENCH)
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Project info
            project?.let { p ->
                ProjectHeader(p)
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedTab == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                tab,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Content
            when (selectedTab) {
                0 -> DashboardTab(
                    stats = project?.let {
                        ProjectWithStats(
                            it.project,
                            it.surveyCount,
                            it.siteCount,
                            it.trenchCount,
                            it.formCount
                        )
                    },
                    trenchDist = trenchDist,
                    dailyProgress = dailyProgress
                )
                1 -> SurveyTab(
                    subTab = surveySubTab,
                    onSubTabChange = { surveySubTab = it },
                    surveyResponses = surveyResponses,
                    sites = sites,
                    onAddSite = { showNewSite = true },
                    onResponseClick = ::viewResponse,
                    onResponseEdit = { response ->
                        scope.launch {
                            val template = viewModel.getFormTemplateForResponse(response)
                            if (template == null) Toast.makeText(context, "This form has no editable fields", Toast.LENGTH_SHORT).show()
                            else { editingFormResponse = response; activeFormTemplate = template }
                        }
                    }
                )
                2 -> FormsTab(
                    responses = excavationResponses,
                    onAdd = {
                        scope.launch {
                            editingFormResponse = null
                            activeFormTemplate = viewModel.getOrCreateProjectFormTemplate(FormType.TRENCH)
                        }
                    },
                    onResponseClick = ::viewResponse,
                    onResponseEdit = { response ->
                        scope.launch {
                            val template = viewModel.getFormTemplateForResponse(response)
                            if (template == null) Toast.makeText(context, "This form has no editable fields", Toast.LENGTH_SHORT).show()
                            else { editingFormResponse = response; activeFormTemplate = template }
                        }
                    }
                )
            }
        }
    }

    if (activeFormTemplate != null) {
        com.stratafield.app.ui.screens.forms.FormFillScreen(
            template = activeFormTemplate!!,
            projectId = projectId,
            onDismiss = {
                activeFormTemplate = null
                editingFormResponse = null
                viewModel.loadProjectDetail(projectId)
            },
            viewModel = viewModel,
            responseToEdit = editingFormResponse
        )
    }

    // Dialogs
    if (showNewSite) {
        NewSiteDialog(
            surveys = surveys,
            onDismiss = { showNewSite = false },
            onSave = { surveyId, code, name, desc, lat, lng, elev, type, period ->
                scope.launch {
                    viewModel.createSite(projectId, surveyId, code, name, desc, lat, lng, elev, type, period)
                    viewModel.loadProjectDetail(projectId)
                    showNewSite = false
                }
            }
        )
    }

    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            exporting = exporting,
            onExportCSV = {
                scope.launch {
                    exporting = true
                    val p = project?.project ?: return@launch
                    val files = ExportManager.exportProjectToCSV(
                        context, p, surveys, sites, trenches, responses
                    )
                    exporting = false
                    showExportDialog = false
                    Toast.makeText(
                        context,
                        "Exported ${files.size} CSV files to Documents/StrataField/${p.name}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onExportExcel = {
                scope.launch {
                    exporting = true
                    val p = project?.project ?: return@launch
                    val file = ExportManager.exportProjectToExcel(
                        context, p, surveys, sites, trenches, responses
                    )
                    exporting = false
                    showExportDialog = false
                    Toast.makeText(
                        context,
                        "Exported Excel file to ${file.absolutePath}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    activeResponse?.let { response ->
        FormResponseViewer(
            response = response,
            template = activeResponseTemplate,
            data = viewModel.parseResponseData(response.dataJson),
            onDismiss = { activeResponse = null; activeResponseTemplate = null },
            onEdit = {
                val template = activeResponseTemplate
                if (template != null) {
                    editingFormResponse = response
                    activeFormTemplate = template
                    activeResponse = null
                    activeResponseTemplate = null
                }
            }
        )
    }
}

@Composable
private fun ProjectHeader(p: ProjectWithStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = p.project.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = buildString {
                append(p.project.location.ifEmpty { "No location" })
                if (p.project.director.isNotBlank()) {
                    append(" • ${p.project.director}")
                }
                if (p.project.institution.isNotBlank()) {
                    append(" • ${p.project.institution}")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge("${p.project.standardType.name} Standard")
            StatusBadge(formatDate(p.project.startDate))
        }
    }
}

@Composable
private fun DashboardTab(
    stats: ProjectWithStats?,
    trenchDist: List<TrenchDistribution>,
    dailyProgress: List<DailyProgress>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Surveys",
                    value = stats?.surveyCount?.toString() ?: "0",
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Map,
                    accentColor = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    title = "Sites",
                    value = stats?.siteCount?.toString() ?: "0",
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Place,
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
                    title = "Trenches",
                    value = stats?.trenchCount?.toString() ?: "0",
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.GridOn,
                    accentColor = NothingInfo
                )
                StatCard(
                    title = "Forms",
                    value = stats?.formCount?.toString() ?: "0",
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Description,
                    accentColor = NothingWarning
                )
            }
        }

        if (trenchDist.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Trench Distribution",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        BarChart(
                            data = trenchDist.map { it.siteName to it.trenchCount },
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (dailyProgress.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Daily Progress (7 days)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LineChart(
                            data = dailyProgress.map { it.formsCompleted },
                            labels = dailyProgress.map {
                                SimpleDateFormat("EEE", Locale.getDefault()).format(Date(it.date))
                            }
                        )
                    }
                }
            }
        }

        if (trenchDist.isEmpty() && dailyProgress.isEmpty()) {
            item {
                EmptyState(
                    title = "No Data Yet",
                    description = "Add surveys, sites, and trenches to see analytics."
                )
            }
        }
    }
}

@Composable
private fun BarChart(
    data: List<Pair<String, Int>>,
    color: Color
) {
    if (data.isEmpty()) return
    val maxValue = data.maxOf { it.second }.coerceAtLeast(1)

    Column {
        data.forEachIndexed { index, (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(120.dp),
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                ) {
                    val fraction = value.toFloat() / maxValue
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(color, RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun LineChart(
    data: List<Int>,
    labels: List<String>,
    lineColor: Color = NothingRed
) {
    if (data.size < 2) {
        // Single point fallback
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEachIndexed { index, value ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = labels.getOrElse(index) { "" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    val maxValue = data.max().coerceAtLeast(1).toFloat()
    val gridLineColor = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        val strokeWidth = 2.dp.toPx()
        val pointRadius = 4.dp.toPx()
        val padding = 16.dp.toPx()
        val chartWidth = size.width - padding * 2
        val chartHeight = size.height - padding * 2 - 20.dp.toPx()

        // Grid lines
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = padding + (chartHeight / gridLines) * i
            drawLine(
                color = gridLineColor.copy(alpha = 0.3f),
                start = Offset(padding, y),
                end = Offset(size.width - padding, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val stepX = chartWidth / (data.size - 1)

        // Draw line
        val points = data.mapIndexed { index, value ->
            Offset(
                padding + stepX * index,
                padding + chartHeight - (value / maxValue) * chartHeight
            )
        }

        // Fill under line
        val fillPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(points.first().x, size.height - padding - 20.dp.toPx())
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height - padding - 20.dp.toPx())
            close()
        }
        drawPath(
            path = fillPath,
            color = lineColor.copy(alpha = 0.1f)
        )

        // Draw path
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Draw points
        points.forEach { point ->
            drawCircle(
                color = lineColor,
                radius = pointRadius,
                center = point
            )
        }
    }

    // Labels
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SurveysTab(
    surveys: List<Survey>,
    onAdd: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (surveys.isEmpty()) {
            item {
                EmptyState(
                    title = "No Surveys",
                    description = "Add a survey to begin field data collection."
                )
            }
        } else {
            items(surveys) { survey ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = survey.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            StatusBadge(survey.status.name)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${survey.surveyType} • Grid: ${survey.gridSize.ifEmpty { "N/A" }} • Spacing: ${survey.transectSpacing.ifEmpty { "N/A" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Started: ${formatDate(survey.startDate)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SurveyTab(
    subTab: Int,
    onSubTabChange: (Int) -> Unit,
    surveyResponses: List<FormResponse>,
    sites: List<Site>,
    onAddSite: () -> Unit,
    onResponseClick: (FormResponse) -> Unit,
    onResponseEdit: (FormResponse) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Survey Data", "Sites").forEachIndexed { index, label ->
                FilterChip(
                    selected = subTab == index,
                    onClick = { onSubTabChange(index) },
                    label = { Text(label) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        if (subTab == 0) {
            FormsTab(responses = surveyResponses, onAdd = {}, onResponseClick = onResponseClick, onResponseEdit = onResponseEdit)
        } else {
            SitesTab(sites = sites, onAdd = onAddSite)
        }
    }
}

@Composable
private fun SitesTab(
    sites: List<Site>,
    onAdd: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (sites.isEmpty()) {
            item {
                EmptyState(
                    title = "No Sites",
                    description = "Add a site discovered during survey."
                )
            }
        } else {
            items(sites) { site ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${site.siteCode} - ${site.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            StatusBadge(site.status.name)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${site.siteType.ifEmpty { "Type N/A" }} • ${site.period.ifEmpty { "Period N/A" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (site.latitude != null && site.longitude != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%.8f° %s, %.8f° %s",
                                    kotlin.math.abs(site.latitude), if (site.latitude >= 0.0) "N" else "S",
                                    kotlin.math.abs(site.longitude), if (site.longitude >= 0.0) "E" else "W"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrenchesTab(
    trenches: List<Trench>,
    onAdd: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (trenches.isEmpty()) {
            item {
                EmptyState(
                    title = "No Trenches",
                    description = "Add excavation trenches to record stratigraphy."
                )
            }
        } else {
            items(trenches) { trench ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${trench.trenchCode} - ${trench.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            StatusBadge(trench.status.name)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buildString {
                                if (trench.length != null && trench.width != null) {
                                    append("${trench.length}m × ${trench.width}m")
                                }
                                if (trench.depth != null) {
                                    append(" • depth ${trench.depth}m")
                                }
                                if (trench.orientation.isNotBlank()) {
                                    append(" • ${trench.orientation}")
                                }
                                if (trench.gridReference.isNotBlank()) {
                                    append(" • ${trench.gridReference}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Opened: ${formatDate(trench.openedDate)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormsTab(
    responses: List<FormResponse>,
    onAdd: () -> Unit,
    onResponseClick: (FormResponse) -> Unit = {},
    onResponseEdit: (FormResponse) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (responses.isEmpty()) {
            item {
                EmptyState(
                    title = "No Forms",
                    description = "Start filling forms to record your archaeological data."
                )
            }
        } else {
            items(responses) { response ->
                Card(
                    onClick = { onResponseClick(response) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = response.formName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                StatusBadge(response.status.name)
                                if (response.status == DataStatus.DRAFT || response.status == DataStatus.COMPLETE) {
                                    TextButton(onClick = { onResponseEdit(response) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                        Text("Edit")
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${response.formType.name} • Updated: ${formatDateTime(response.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormResponseViewDialog(
    response: FormResponse,
    data: Map<String, String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text(response.formName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "${response.formType.name} • ${response.status.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (data.isEmpty()) {
                    item { Text("No field data saved.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(data.entries.toList()) { (key, value) ->
                        Column {
                            Text(key, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun NewSurveyDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Systematic") }
    var grid by remember { mutableStateOf("") }
    var spacing by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("New Survey", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StrataTextField(value = name, onValueChange = { name = it }, label = "Survey Name *")
                StrataTextField(value = desc, onValueChange = { desc = it }, label = "Description", singleLine = false, minLines = 2)
                StrataTextField(value = type, onValueChange = { type = it }, label = "Survey Type")
                StrataTextField(value = grid, onValueChange = { grid = it }, label = "Grid Size")
                StrataTextField(value = spacing, onValueChange = { spacing = it }, label = "Transect Spacing")
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(name, desc, type, grid, spacing) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = NothingRed) }
        }
    )
}

@Composable
private fun NewSiteDialog(
    surveys: List<Survey>,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String, String, Double?, Double?, Double?, String, String) -> Unit
) {
    var surveyId by remember { mutableStateOf<Long?>(null) }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }
    var elev by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("New Site", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Survey selector
                if (surveys.isNotEmpty()) {
                    Text("Associated Survey", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        surveys.take(5).forEach { survey ->
                            FilterChip(
                                selected = surveyId == survey.id,
                                onClick = { surveyId = if (surveyId == survey.id) null else survey.id },
                                label = { Text(survey.name.take(12)) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
                StrataTextField(value = code, onValueChange = { code = it }, label = "Site Code *")
                StrataTextField(value = name, onValueChange = { name = it }, label = "Site Name *")
                StrataTextField(value = type, onValueChange = { type = it }, label = "Site Type")
                StrataTextField(value = period, onValueChange = { period = it }, label = "Period")
                StrataTextField(value = lat, onValueChange = { lat = it }, label = "Latitude")
                StrataTextField(value = lng, onValueChange = { lng = it }, label = "Longitude")
                StrataTextField(value = elev, onValueChange = { elev = it }, label = "Elevation (m)")
                StrataTextField(value = desc, onValueChange = { desc = it }, label = "Description", singleLine = false, minLines = 2)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (code.isNotBlank() && name.isNotBlank()) {
                        onSave(
                            surveyId, code, name, desc,
                            lat.toDoubleOrNull(), lng.toDoubleOrNull(), elev.toDoubleOrNull(),
                            type, period
                        )
                    }
                },
                enabled = code.isNotBlank() && name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = NothingRed) }
        }
    )
}

@Composable
private fun NewTrenchDialog(
    sites: List<Site>,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, Double?, Double?, Double?, String, String, String) -> Unit
) {
    var siteId by remember { mutableStateOf<Long?>(null) }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }
    var width by remember { mutableStateOf("") }
    var depth by remember { mutableStateOf("") }
    var orient by remember { mutableStateOf("") }
    var grid by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("New Trench", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Associated Site *", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                if (sites.isEmpty()) {
                    Text(
                        "Create a site first before adding trenches.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingRed
                    )
                } else {
                    sites.forEach { site ->
                        FilterChip(
                            selected = siteId == site.id,
                            onClick = { siteId = site.id },
                            label = { Text("${site.siteCode} - ${site.name.take(15)}") },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
                StrataTextField(value = code, onValueChange = { code = it }, label = "Trench Code *")
                StrataTextField(value = name, onValueChange = { name = it }, label = "Trench Name")
                StrataTextField(value = length, onValueChange = { length = it }, label = "Length (m)")
                StrataTextField(value = width, onValueChange = { width = it }, label = "Width (m)")
                StrataTextField(value = depth, onValueChange = { depth = it }, label = "Depth (m)")
                StrataTextField(value = orient, onValueChange = { orient = it }, label = "Orientation")
                StrataTextField(value = grid, onValueChange = { grid = it }, label = "Grid Reference")
                StrataTextField(value = desc, onValueChange = { desc = it }, label = "Description", singleLine = false, minLines = 2)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (siteId != null && code.isNotBlank()) {
                        onSave(
                            siteId!!, code, name,
                            length.toDoubleOrNull(), width.toDoubleOrNull(), depth.toDoubleOrNull(),
                            orient, grid, desc
                        )
                    }
                },
                enabled = siteId != null && code.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = NothingRed) }
        }
    )
}

@Composable
private fun FormPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (FormTemplate, FormType) -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Select Form Template", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(
                    text = "Choose from standard templates or create custom forms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("US", "UK", "Indian", "Bangladeshi").forEach { std ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                Toast.makeText(context, "Form Builder: $std templates coming in full version", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text(std) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Form filling with all US, UK, Indian & Bangladeshi standard templates is available. Select a standard above to begin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = NothingRed) }
        }
    )
}

@Composable
private fun ExportDialog(
    onDismiss: () -> Unit,
    exporting: Boolean,
    onExportCSV: () -> Unit,
    onExportExcel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Export Project Data", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Export all surveys, sites, trenches, and form data for this project.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (exporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Exporting...",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onExportCSV,
                    enabled = !exporting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("CSV") }
                Button(
                    onClick = onExportExcel,
                    enabled = !exporting,
                    colors = ButtonDefaults.buttonColors(containerColor = NothingSuccess, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Excel") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = NothingRed) }
        }
    )
}
