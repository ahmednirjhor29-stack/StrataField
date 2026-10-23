package com.stratafield.app.ui.screens.datamanagement

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.stratafield.app.data.model.*
import com.stratafield.app.ui.StrataFieldViewModel
import com.stratafield.app.ui.components.*
import com.stratafield.app.ui.screens.forms.FormFillScreen
import com.stratafield.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DataManagementScreen(
    viewModel: StrataFieldViewModel = viewModel()
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsState()
    val responses by viewModel.projectResponses.collectAsState()

    var selectedProjectId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortField by remember { mutableStateOf(SortField.DATE) }
    var sortOrder by remember { mutableStateOf(SortOrder.DESCENDING) }
    var statusFilter by remember { mutableStateOf<DataStatus?>(null) }
    var typeFilter by remember { mutableStateOf<FormType?>(null) }
    var viewingResponse by remember { mutableStateOf<FormResponse?>(null) }
    var viewingTemplate by remember { mutableStateOf<FormTemplate?>(null) }
    var editingResponse by remember { mutableStateOf<FormResponse?>(null) }
    var editingTemplate by remember { mutableStateOf<FormTemplate?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.loadProjects()
    }

    LaunchedEffect(selectedProjectId) {
        selectedProjectId?.let { viewModel.loadProjectDetail(it) }
    }

    // Filter and sort responses
    val filteredResponses = remember(responses, searchQuery, statusFilter, typeFilter, sortField, sortOrder) {
        var result = responses

        if (searchQuery.isNotBlank()) {
            result = result.filter { response ->
                response.formName.contains(searchQuery, ignoreCase = true) ||
                response.dataJson.contains(searchQuery, ignoreCase = true)
            }
        }

        statusFilter?.let { status ->
            result = result.filter { it.status == status }
        }

        typeFilter?.let { type ->
            result = result.filter { it.formType == type }
        }

        when (sortField) {
            SortField.DATE -> result.sortedBy { it.updatedAt }
            SortField.NAME -> result.sortedBy { it.formName }
            SortField.STATUS -> result.sortedBy { it.status.name }
            SortField.TYPE -> result.sortedBy { it.formType.name }
            SortField.PROJECT -> result.sortedBy { it.projectId }
        }.let { sorted ->
            if (sortOrder == SortOrder.DESCENDING) sorted.reversed() else sorted
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Data Management",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Browse, sort, and filter your collected data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Project selector
        if (projects.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 100.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(projects) { project ->
                    FilterChip(
                        selected = selectedProjectId == project.id,
                        onClick = {
                            selectedProjectId = if (selectedProjectId == project.id) null else project.id
                        },
                        label = { Text(project.name) },
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

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search forms...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )

        // Filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status filter
            var showStatusMenu by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = statusFilter != null,
                    onClick = { showStatusMenu = true },
                    label = { Text(statusFilter?.name ?: "Status") },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                DropdownMenu(
                    expanded = showStatusMenu,
                    onDismissRequest = { showStatusMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All") },
                        onClick = { statusFilter = null; showStatusMenu = false }
                    )
                    DataStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.name) },
                            onClick = { statusFilter = status; showStatusMenu = false }
                        )
                    }
                }
            }

            // Type filter
            var showTypeMenu by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = typeFilter != null,
                    onClick = { showTypeMenu = true },
                    label = { Text(typeFilter?.name ?: "Type") },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                DropdownMenu(
                    expanded = showTypeMenu,
                    onDismissRequest = { showTypeMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All") },
                        onClick = { typeFilter = null; showTypeMenu = false }
                    )
                    FormType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.name) },
                            onClick = { typeFilter = type; showTypeMenu = false }
                        )
                    }
                }
            }

            // Sort
            var showSortMenu by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = true,
                    onClick = { showSortMenu = true },
                    label = { Text("Sort: ${sortField.name}") },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    SortField.entries.forEach { field ->
                        DropdownMenuItem(
                            text = { Text(field.name) },
                            onClick = {
                                sortField = field
                                sortOrder = if (sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Results count
        Text(
            text = "${filteredResponses.size} records",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Results list
        if (filteredResponses.isEmpty()) {
            EmptyState(
                title = "No Data Found",
                description = "Select a project or adjust filters to see your data."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredResponses) { response ->
                    DataRecordCard(
                        response,
                        onClick = {
                            scope.launch {
                                viewingTemplate = viewModel.getFormTemplateForResponse(response)
                                viewingResponse = response
                            }
                        },
                        onEdit = {
                            scope.launch {
                                val template = viewModel.getFormTemplateForResponse(response)
                                if (template == null) {
                                    Toast.makeText(context, "This form has no editable fields", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewingResponse = null
                                    viewingTemplate = null
                                    editingResponse = response
                                    editingTemplate = template
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    viewingResponse?.let { response ->
        FormResponseViewer(
            response = response,
            template = viewingTemplate,
            data = viewModel.parseResponseData(response.dataJson),
            onDismiss = { viewingResponse = null; viewingTemplate = null },
            onEdit = {
                val template = viewingTemplate
                if (template != null) {
                    editingResponse = response
                    editingTemplate = template
                    viewingResponse = null
                    viewingTemplate = null
                }
            }
        )
    }

    val responseBeingEdited = editingResponse
    val templateBeingEdited = editingTemplate
    if (responseBeingEdited != null && templateBeingEdited != null) {
        FormFillScreen(
            template = templateBeingEdited,
            projectId = responseBeingEdited.projectId,
            responseToEdit = responseBeingEdited,
            viewModel = viewModel,
            onDismiss = {
                editingResponse = null
                editingTemplate = null
                viewModel.loadProjectDetail(responseBeingEdited.projectId)
            }
        )
    }
}

@Composable
private fun DataRecordCard(response: FormResponse, onClick: () -> Unit, onEdit: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = response.formType.name.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = response.formName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${response.formType.name} • ${formatDateTime(response.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusBadge(response.status.name)
                if (response.status == DataStatus.DRAFT || response.status == DataStatus.COMPLETE) {
                    TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Edit")
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponseDialog(
    response: FormResponse,
    viewModel: StrataFieldViewModel,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    val readOnly = response.status != DataStatus.DRAFT
    val data = remember(response) { viewModel.parseResponseData(response.dataJson) }
    val editableData = remember(response) { mutableStateMapOf<String, String>().apply { putAll(data) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = if (readOnly) response.formName else "Edit ${response.formName}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "${response.formType.name} • ${response.status.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (editableData.isEmpty()) {
                    item {
                        Text("No field data saved.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(editableData.keys.toList()) { key ->
                        OutlinedTextField(
                            value = editableData[key] ?: "",
                            onValueChange = { editableData[key] = it },
                            label = { Text(key) },
                            readOnly = readOnly,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (readOnly) {
                TextButton(onClick = onDismiss) { Text("Close") }
            } else {
                Button(
                    onClick = { onSave(editableData.toMap()) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Save") }
            }
        },
        dismissButton = {
            if (!readOnly) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = NothingRed) }
            }
        }
    )
}
