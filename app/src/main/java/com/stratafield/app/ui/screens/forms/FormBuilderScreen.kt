package com.stratafield.app.ui.screens.forms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stratafield.app.data.model.*
import com.stratafield.app.ui.StrataFieldViewModel
import com.stratafield.app.ui.components.*
import com.stratafield.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

private val FormDataSaver: Saver<androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>, String> = Saver(
    save = { Gson().toJson(it.toMap()) },
    restore = { json ->
        val map = mutableStateMapOf<String, String>()
        try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val restored: Map<String, String>? = Gson().fromJson(json, type)
            restored?.let { map.putAll(it) }
        } catch (e: Exception) { /* ignore corrupt draft */ }
        map
    }
)

private val ExtraFieldsSaver: Saver<List<FormFieldDefinition>, String> = Saver(
    save = { Gson().toJson(it) },
    restore = { json ->
        try {
            val type = object : TypeToken<List<FormFieldDefinition>>() {}.type
            Gson().fromJson<List<FormFieldDefinition>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
)

@Composable
fun FormBuilderScreen(
    projectId: Long,
    onBack: () -> Unit,
    viewModel: StrataFieldViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val templates by viewModel.templates.collectAsState()

    var showCreateTemplate by remember { mutableStateOf(false) }
    var selectedTemplateId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedTemplate = templates.firstOrNull { it.id == selectedTemplateId }

    LaunchedEffect(Unit) {
        viewModel.loadTemplates()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            StrataTopBar(
                title = "Form Builder",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showCreateTemplate = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "New Template")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Form Templates",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Select a template to fill, or create a custom form",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (templates.isEmpty()) {
                item {
                    EmptyState(
                        title = "No Templates",
                        description = "Create a custom form template to get started."
                    )
                }
            } else {
                items(templates) { template ->
                    TemplateCard(
                        template = template,
                        onClick = { selectedTemplateId = template.id }
                    )
                }
            }
        }
    }

    if (showCreateTemplate) {
        CreateTemplateDialog(
            onDismiss = { showCreateTemplate = false },
            onSave = { name, desc, formType, standard, fields ->
                scope.launch {
                    viewModel.createTemplate(name, desc, formType, standard, fields)
                    viewModel.loadTemplates()
                    showCreateTemplate = false
                    Toast.makeText(context, "Template created", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    selectedTemplate?.let { template ->
        FormFillScreen(
            template = template,
            projectId = projectId,
            onDismiss = { selectedTemplateId = null },
            viewModel = viewModel
        )
    }
}

@Composable
private fun TemplateCard(
    template: FormTemplate,
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
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = template.name.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(template.formType.name)
                    StatusBadge(template.standardType.name)
                    if (template.isBuiltIn) {
                        StatusBadge("Built-in")
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateTemplateDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, FormType, StandardType, List<FormFieldDefinition>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var formType by remember { mutableStateOf(FormType.CUSTOM) }
    var standard by remember { mutableStateOf(StandardType.CUSTOM) }
    var fields by remember { mutableStateOf<List<FormFieldDefinition>>(emptyList()) }
    var showAddField by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Create Form Template", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StrataTextField(value = name, onValueChange = { name = it }, label = "Template Name *")
                StrataTextField(value = desc, onValueChange = { desc = it }, label = "Description", singleLine = false, minLines = 2)

                Text("Form Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormType.entries.forEach { type ->
                        FilterChip(
                            selected = formType == type,
                            onClick = { formType = type },
                            label = { Text(type.name) },
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

                Text("Standard", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StandardType.entries.forEach { std ->
                        FilterChip(
                            selected = standard == std,
                            onClick = { standard = std },
                            label = { Text(std.name) },
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

                Text("Fields (${fields.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                fields.forEachIndexed { index, field ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}. ${field.label} (${field.fieldType.name})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { fields = fields.filterIndexed { i, _ -> i != index } }
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = NothingRed)
                        }
                    }
                }

                Button(
                    onClick = { showAddField = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Field")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && fields.isNotEmpty()) {
                        onSave(name, desc, formType, standard, fields)
                    }
                },
                enabled = name.isNotBlank() && fields.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = NothingRed) }
        }
    )

    if (showAddField) {
        AddFieldDialog(
            onDismiss = { showAddField = false },
            onAdd = { field ->
                fields = fields + field
                showAddField = false
            }
        )
    }
}

@Composable
internal fun AddFieldDialog(
    onDismiss: () -> Unit,
    onAdd: (FormFieldDefinition) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var fieldType by remember { mutableStateOf(FieldType.TEXT) }
    var required by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf("") }
    var placeholder by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Add Field", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StrataTextField(value = label, onValueChange = { label = it }, label = "Field Label *")
                StrataTextField(value = placeholder, onValueChange = { placeholder = it }, label = "Placeholder")

                Text("Field Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(FieldType.entries.filterNot { it == FieldType.CALCULATOR }) { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { fieldType = type }
                                .background(
                                    if (fieldType == type) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = fieldType == type,
                                onClick = { fieldType = type },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = type.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (fieldType == FieldType.DROPDOWN || fieldType == FieldType.RADIO) {
                    StrataTextField(
                        value = options,
                        onValueChange = { options = it },
                        label = "Options (comma separated)"
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Required field",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = required,
                        onCheckedChange = { required = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (label.isNotBlank()) {
                        onAdd(
                            FormFieldDefinition(
                                id = "${label.lowercase().replace(" ", "_")}_${java.util.UUID.randomUUID().toString().take(8)}",
                                label = label,
                                fieldType = fieldType,
                                required = required,
                                options = if (options.isNotBlank()) options.split(",").map { it.trim() } else emptyList(),
                                placeholder = placeholder
                            )
                        )
                    }
                },
                enabled = label.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = NothingRed) }
        }
    )
}

@Composable
internal fun FormFillScreen(
    template: FormTemplate,
    projectId: Long,
    onDismiss: () -> Unit,
    viewModel: StrataFieldViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val templateFields = remember(template) { viewModel.parseFields(template.fieldsJson) }
    var extraFields by rememberSaveable(stateSaver = ExtraFieldsSaver) { mutableStateOf(emptyList()) }
    val formData = rememberSaveable(saver = FormDataSaver) { mutableStateMapOf() }
    var showDrawing by rememberSaveable { mutableStateOf(false) }
    var showGraphPaper by rememberSaveable { mutableStateOf(false) }
    var showImagePicker by rememberSaveable { mutableStateOf(false) }
    var showMatrix by rememberSaveable { mutableStateOf(false) }
    var showAddField by rememberSaveable { mutableStateOf(false) }
    var showCompleteConfirm by rememberSaveable { mutableStateOf(false) }
    var activeFieldId by rememberSaveable { mutableStateOf<String?>(null) }
    val allFields = templateFields + extraFields
    val darkMode = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val sectionBackgrounds = if (darkMode) listOf(
        Color(0xFF202B27), Color(0xFF222A36), Color(0xFF30291F), Color(0xFF2D2532),
        Color(0xFF302326), Color(0xFF202D32), Color(0xFF2B2D22), Color(0xFF242B32)
    ) else listOf(
        Color(0xFFF3F7F1), Color(0xFFF1F5FA), Color(0xFFFBF5EC), Color(0xFFF7F1F7),
        Color(0xFFFCEFF0), Color(0xFFEEF6F7), Color(0xFFF5F6EA), Color(0xFFF0F3F7)
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            StrataTopBar(title = template.name, onBack = onDismiss)
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                viewModel.saveFormResponse(
                                    projectId = projectId,
                                    surveyId = null,
                                    siteId = null,
                                    trenchId = null,
                                    templateId = template.id,
                                    formName = template.name,
                                    formType = template.formType,
                                    data = formData.toMap(),
                                    status = DataStatus.DRAFT
                                )
                                Toast.makeText(context, "Saved as draft", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Draft")
                    }
                    Button(
                        onClick = { showCompleteConfirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Complete")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Group fields by section, filtering out conditional fields whose condition isn't met
            val visibleFields = allFields.filter { field -> field.fieldType != FieldType.CALCULATOR && (
                field.dependsOnFieldId.isBlank() || formData[field.dependsOnFieldId] == field.dependsOnValue
            ) }
            val sections = visibleFields.groupBy { it.section.ifEmpty { "General" } }

            sections.forEach { (section, sectionFields) ->
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(
                            sectionBackgrounds[sections.keys.indexOf(section).mod(sectionBackgrounds.size)],
                            RoundedCornerShape(16.dp)
                        ).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    Text(text = section, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    sectionFields.forEach { field ->
                    FormFieldInput(
                        field = field,
                        value = formData[field.id] ?: "",
                        onValueChange = { formData[field.id] = it },
                        onOpenCalculator = {},
                        onOpenDrawing = { activeFieldId = field.id; showDrawing = true },
                        onOpenGraphPaper = { activeFieldId = field.id; showGraphPaper = true },
                        onOpenImagePicker = { activeFieldId = field.id; showImagePicker = true },
                        onOpenMatrix = { activeFieldId = field.id; showMatrix = true },
                        onDelete = if (extraFields.any { it.id == field.id }) {
                            { extraFields = extraFields.filterNot { it.id == field.id } }
                        } else null
                    )
                    if (field.fieldType == FieldType.GRAPH_PAPER) {
                        TextButton(onClick = {
                            val count = allFields.count { it.fieldType == FieldType.GRAPH_PAPER } + 1
                            extraFields = extraFields + FormFieldDefinition(
                                id = "graph_paper_${java.util.UUID.randomUUID().toString().take(8)}",
                                label = "Graph Paper $count",
                                fieldType = FieldType.GRAPH_PAPER,
                                section = "Documentation"
                            )
                        }) {
                            Icon(Icons.Filled.GridOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add another graph paper")
                        }
                    }
                    }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { showAddField = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Field")
                }
            }
        }
    }

    if (showAddField) {
        AddFieldDialog(
            onDismiss = { showAddField = false },
            onAdd = { field ->
                extraFields = extraFields + field
                showAddField = false
            }
        )
    }

    if (showCompleteConfirm) {
        AlertDialog(
            onDismissRequest = { showCompleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Complete form?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    text = "After pressing Complete, this form will be added to the data list and you will not be able to edit it further.",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompleteConfirm = false
                        scope.launch {
                            viewModel.saveFormResponse(
                                projectId = projectId,
                                surveyId = null,
                                siteId = null,
                                trenchId = null,
                                templateId = template.id,
                                formName = template.name,
                                formType = template.formType,
                                data = formData.toMap(),
                                status = DataStatus.COMPLETE
                            )
                            Toast.makeText(context, "Form completed", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Complete") }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteConfirm = false }) {
                    Text("Cancel", color = NothingRed)
                }
            }
        )
    }

    if (showDrawing) {
        val fieldId = activeFieldId
        Dialog(
            onDismissRequest = { showDrawing = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize()) {
                DrawingTool(
                    modifier = Modifier.weight(1f),
                    initialStrokes = fieldId?.let { deserializeStrokes(formData[it] ?: "") } ?: emptyList(),
                    backgroundType = "graph",
                    onStrokesChange = { strokes ->
                        fieldId?.let { formData[it] = serializeStrokes(strokes) }
                    }
                )
                TextButton(onClick = { showDrawing = false }, modifier = Modifier.align(Alignment.End)) { Text("Done") }
                }
            }
        }
    }

    if (showGraphPaper) {
        val fieldId = activeFieldId
        Dialog(
            onDismissRequest = { showGraphPaper = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    GraphPointTool(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        initialData = fieldId?.let { deserializeGraphPaper(formData[it] ?: "") } ?: GraphPaperData(),
                        onClose = { showGraphPaper = false },
                        onDataChange = { data ->
                            fieldId?.let { formData[it] = serializeGraphPaper(data) }
                        }
                    )
                }
            }
        }
    }

    if (showMatrix) {
        val fieldId = activeFieldId
        AlertDialog(
            onDismissRequest = { showMatrix = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Stratigraphic Matrix", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                StratigraphicMatrixTool(
                    initialValue = fieldId?.let { formData[it] ?: "" } ?: "",
                    onChange = { serialized ->
                        fieldId?.let { formData[it] = serialized }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showMatrix = false }) {
                    Text("Done", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    if (showImagePicker) {
        val fieldId = activeFieldId
        AlertDialog(
            onDismissRequest = { showImagePicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Media", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                MediaGalleryTool(
                    initialUris = fieldId?.let { deserializeMedia(formData[it] ?: "").uris } ?: emptyList(),
                    initialImageNumbers = fieldId?.let { deserializeMedia(formData[it] ?: "").imageNumbers } ?: "",
                    onChange = { uris, imageNumbers ->
                        fieldId?.let { formData[it] = serializeMedia(uris, imageNumbers) }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showImagePicker = false }) {
                    Text("Done", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormFieldInput(
    field: FormFieldDefinition,
    value: String,
    onValueChange: (String) -> Unit,
    onOpenCalculator: () -> Unit,
    onOpenDrawing: () -> Unit,
    onOpenGraphPaper: () -> Unit = onOpenDrawing,
    onOpenImagePicker: () -> Unit,
    onOpenMatrix: () -> Unit = {},
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = field.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (field.required) {
                    Text(
                        text = "*",
                        style = MaterialTheme.typography.titleSmall,
                        color = NothingRed
                    )
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove field", tint = NothingRed)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (field.fieldType) {
                FieldType.TEXT -> {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = { Text(field.placeholder) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                FieldType.TEXTAREA -> {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = { Text(field.placeholder) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                FieldType.NUMBER -> {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = { Text(field.placeholder) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                FieldType.DATE -> {
                    var showDatePicker by remember { mutableStateOf(false) }
                    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
                    LaunchedEffect(field.id) {
                        if (value.isBlank()) onValueChange(dateFormatter.format(Date()))
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = {},
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = "Choose date")
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    if (showDatePicker) {
                        val initialMillis = remember(value) {
                            runCatching { dateFormatter.parse(value)?.time }.getOrNull() ?: System.currentTimeMillis()
                        }
                        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    val selected = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                                    onValueChange(dateFormatter.format(Date(selected)))
                                    showDatePicker = false
                                }) { Text("OK") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                            }
                        ) {
                            DatePicker(state = datePickerState)
                        }
                    }
                }
                FieldType.DROPDOWN -> {
                    if (field.label.contains("texture", true)) {
                        OutlinedTextField(value = value, onValueChange = onValueChange, placeholder = { Text(field.placeholder.ifBlank { "Enter texture description" }) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp))
                    } else {
                    var expanded by remember { mutableStateOf(false) }
                    var manualOption by remember { mutableStateOf("") }
                    val dropdownOptions = if (field.label.contains("compact", true)) (field.options + "Add").distinct() else field.options
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text(
                                text = value.ifEmpty { "Select..." },
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            dropdownOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        if (option == "Add") manualOption = "__add__" else onValueChange(option)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (manualOption == "__add__") {
                        OutlinedTextField(value = if (value.startsWith("Add: ")) value.removePrefix("Add: ") else "", onValueChange = { onValueChange("Add: $it") }, label = { Text("Specify compactness") }, modifier = Modifier.fillMaxWidth())
                    }
                    }
                }
                FieldType.CHECKBOX -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = value == "true",
                            onCheckedChange = { onValueChange(it.toString()) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = "Yes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                FieldType.RADIO -> {
                    field.options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onValueChange(option) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = value == option,
                                onClick = { onValueChange(option) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(option, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                FieldType.IMAGE -> {
                    OutlinedButton(
                        onClick = onOpenImagePicker,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Icon(Icons.Filled.Photo, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        val count = remember(value) { deserializeMedia(value).uris.size }
                        Text(if (count > 0) "$count media attached" else "Attach Media")
                    }
                }
                FieldType.GPS -> {
                    SurveyLocationField(fieldId = field.id, value = value, onValueChange = onValueChange)
                }
                FieldType.DRAWING -> {
                    OutlinedButton(
                        onClick = onOpenDrawing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (value.isNotBlank()) "Drawing saved" else "Open Drawing Tool")
                    }
                }
                FieldType.GRAPH_PAPER -> {
                    OutlinedButton(
                        onClick = onOpenGraphPaper,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Icon(Icons.Filled.GridOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (value.isNotBlank()) "Graph paper saved" else "Open Graph Paper")
                    }
                }
                FieldType.CALCULATOR -> {
                    OutlinedButton(
                        onClick = onOpenCalculator,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Icon(Icons.Filled.Calculate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (value.isNotBlank()) "Result: $value" else "Open Calculator")
                    }
                }
                FieldType.SIGNATURE -> {
                    OutlinedButton(
                        onClick = onOpenDrawing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Icon(Icons.Filled.Draw, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (value.isNotBlank()) "Signature captured" else "Capture Signature")
                    }
                }
                FieldType.MATRIX -> {
                    OutlinedButton(
                        onClick = onOpenMatrix,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Icon(Icons.Filled.GridOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (value.isNotBlank()) "Stratigraphic matrix saved" else "Open Stratigraphic Matrix")
                    }
                }
                FieldType.SECTION_HEADER -> {
                    Text(
                        text = field.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                FieldType.TIME -> {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = { Text("HH:MM") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            if (field.helpText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = field.helpText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class SurveyLocationData(
    val crs: String = "EPSG:4326",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gnssElevation: Double? = null,
    val seaLevelElevation: String = ""
)

@Composable
private fun SurveyLocationField(
    fieldId: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    var location by remember(fieldId) { mutableStateOf(parseLocation(value)) }
    val initialCoordinates = remember(fieldId) { coordinatesInCrs(location) }
    var northSouthText by remember(fieldId) { mutableStateOf(initialCoordinates.first) }
    var eastWestText by remember(fieldId) { mutableStateOf(initialCoordinates.second) }
    var gnssElevationText by remember(fieldId) { mutableStateOf(location.gnssElevation?.let(::formatMeters) ?: "") }
    var seaLevelElevationText by remember(fieldId) { mutableStateOf(location.seaLevelElevation) }
    var crsExpanded by remember { mutableStateOf(false) }
    var coordinateFormat by rememberSaveable(fieldId) { mutableStateOf("Decimal degrees") }
    var formatExpanded by remember { mutableStateOf(false) }
    var gpsStatus by remember { mutableStateOf("") }

    fun save(updated: SurveyLocationData) {
        location = updated
        onValueChange(Gson().toJson(updated))
    }

    fun captureGps() {
        gpsStatus = if (location.latitude != null && location.longitude != null) "Refreshing location…" else "Getting location…"
        requestCurrentLocation(context) { fix ->
            if (fix == null) {
                if (location.latitude == null || location.longitude == null) gpsStatus = "No location fix. Check location services and try again."
            } else {
                val updated = location.copy(
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    gnssElevation = if (fix.hasAltitude()) fix.altitude else location.gnssElevation
                )
                save(updated)
                val coordinates = coordinatesInCrs(updated, coordinateFormat)
                northSouthText = coordinates.first
                eastWestText = coordinates.second
                gnssElevationText = updated.gnssElevation?.let(::formatMeters) ?: ""
                gpsStatus = "Location accuracy: ${fix.accuracy.toInt()} m"
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) captureGps()
        else gpsStatus = "Location permission is needed to capture GPS."
    }

    LaunchedEffect(fieldId) {
        if (location.latitude == null || location.longitude == null) {
            val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fineGranted || coarseGranted) captureGps()
            else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (location.crs == "EPSG:4326") Box {
            OutlinedButton(onClick={formatExpanded=true}, modifier=Modifier.fillMaxWidth()) { Text("Coordinate format: $coordinateFormat", modifier=Modifier.weight(1f)); Icon(Icons.Filled.ArrowDropDown, null) }
            DropdownMenu(expanded=formatExpanded,onDismissRequest={formatExpanded=false}) {
                listOf("Degrees, minutes, seconds", "Decimal degrees").forEach { option -> DropdownMenuItem(text={Text(option)},onClick={ coordinateFormat=option; val c=coordinatesInCrs(location,option); northSouthText=c.first; eastWestText=c.second; formatExpanded=false }) }
            }
        }
        Box {
            OutlinedButton(
                onClick = { crsExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("CRS: ${crsLabel(location.crs)}", modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose CRS")
            }
            DropdownMenu(expanded = crsExpanded, onDismissRequest = { crsExpanded = false }) {
                (listOf("EPSG:4326" to "WGS 84 · EPSG:4326", "EPSG:3857" to "Web Mercator · EPSG:3857") + (1..60).flatMap { zone -> listOf("EPSG:${32600 + zone}" to "UTM Zone ${zone}N · EPSG:${32600 + zone}", "EPSG:${32700 + zone}" to "UTM Zone ${zone}S · EPSG:${32700 + zone}") }).forEach { (crs, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        val updated = location.copy(crs = crs)
                        save(updated)
                        val coordinates = coordinatesInCrs(updated, coordinateFormat)
                        northSouthText = coordinates.first
                        eastWestText = coordinates.second
                        crsExpanded = false
                    })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = northSouthText,
                onValueChange = { input ->
                    northSouthText = input
                    val first = parseCoordinate(input)
                    val second = parseCoordinate(eastWestText)
                    if (first != null && second != null) {
                        val latLon = coordinatesToLatLon(first, second, location.crs)
                        save(location.copy(latitude = latLon.first, longitude = latLon.second))
                    }
                },
                label = { Text(if (location.crs == "EPSG:4326") "Latitude" else if (location.crs == "EPSG:3857" || location.crs.startsWith("EPSG:32")) "Northing (m)" else "Y coordinate") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = eastWestText,
                onValueChange = { input ->
                    eastWestText = input
                    val first = parseCoordinate(northSouthText)
                    val second = parseCoordinate(input)
                    if (first != null && second != null) {
                        val latLon = coordinatesToLatLon(first, second, location.crs)
                        save(location.copy(latitude = latLon.first, longitude = latLon.second))
                    }
                },
                label = { Text(if (location.crs == "EPSG:4326") "Longitude" else "Easting (m)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = gnssElevationText,
                onValueChange = { input ->
                    gnssElevationText = input
                    save(location.copy(gnssElevation = input.toDoubleOrNull()))
                },
                label = { Text("GNSS Native Elevation (m)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = seaLevelElevationText,
                onValueChange = { input ->
                    seaLevelElevationText = input
                    save(location.copy(seaLevelElevation = input))
                },
                label = { Text("Sea Level Elevation (m)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fineGranted || coarseGranted) captureGps()
                else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) {
                Icon(Icons.Filled.MyLocation, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Refresh GPS")
            }
            if (gpsStatus.isNotBlank()) Text(gpsStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
    }
}

private fun parseLocation(value: String): SurveyLocationData = try {
    Gson().fromJson(value, SurveyLocationData::class.java) ?: SurveyLocationData()
} catch (_: Exception) {
    SurveyLocationData()
}

private fun formatMeters(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun coordinatesInCrs(data: SurveyLocationData, format: String = "Decimal degrees"): Pair<String, String> {
    val latitude = data.latitude ?: return "" to ""
    val longitude = data.longitude ?: return "" to ""
    return if (data.crs.startsWith("EPSG:326") || data.crs.startsWith("EPSG:327")) {
        val epsg = data.crs.substringAfter(':').toIntOrNull() ?: 32646
        val (northing, easting) = toUtm(latitude, longitude, epsg % 100, epsg < 32700)
        formatMeters(northing) to formatMeters(easting)
    } else if (data.crs == "EPSG:3857") {
        val radius = 6378137.0
        val safeLatitude = latitude.coerceIn(-85.05112878, 85.05112878)
        val x = radius * Math.toRadians(longitude)
        val y = radius * kotlin.math.ln(kotlin.math.tan(Math.PI / 4.0 + Math.toRadians(safeLatitude) / 2.0))
        formatMeters(y) to formatMeters(x)
    } else {
        if (format == "Degrees, minutes, seconds") formatDms(latitude) to formatDms(longitude)
        else String.format(Locale.US, "%.4f", latitude) to String.format(Locale.US, "%.4f", longitude)
    }
}

private fun formatDms(value: Double): String {
    val absolute=kotlin.math.abs(value); val degrees=absolute.toInt(); val minutesFloat=(absolute-degrees)*60.0; val minutes=minutesFloat.toInt(); val seconds=(minutesFloat-minutes)*60.0
    val direction=if(value<0) "S/W" else "N/E"
    return String.format(Locale.US,"%d° %02d′ %05.2f″ %s",degrees,minutes,seconds,direction)
}

private fun parseCoordinate(value: String): Double? = value.toDoubleOrNull() ?: run {
    val numbers=Regex("[-+]?\\d+(?:\\.\\d+)?").findAll(value).mapNotNull { it.value.toDoubleOrNull() }.toList()
    if(numbers.isEmpty()) null else {
        val sign=if(value.contains('S',true)||value.contains('W',true)||numbers.first()<0) -1 else 1
        sign*(kotlin.math.abs(numbers[0])+(numbers.getOrNull(1)?:0.0)/60.0+(numbers.getOrNull(2)?:0.0)/3600.0)
    }
}

private fun coordinatesToLatLon(first: Double, second: Double, crs: String): Pair<Double, Double> {
    if (crs.startsWith("EPSG:326") || crs.startsWith("EPSG:327")) {
        val epsg = crs.substringAfter(':').toIntOrNull() ?: 32646
        return fromUtm(second, first, epsg % 100, epsg < 32700)
    }
    if (crs != "EPSG:3857") return first to second
    val radius = 6378137.0
    val longitude = Math.toDegrees(second / radius)
    val latitude = Math.toDegrees(2.0 * kotlin.math.atan(kotlin.math.exp(first / radius)) - Math.PI / 2.0)
    return latitude to longitude
}

private fun crsLabel(crs: String) = when {
    crs == "EPSG:4326" -> "WGS 84 · EPSG:4326"
    crs == "EPSG:3857" -> "Web Mercator · EPSG:3857"
    crs.startsWith("EPSG:326") -> "UTM Zone ${crs.substringAfter(':').takeLast(2)}N · $crs"
    crs.startsWith("EPSG:327") -> "UTM Zone ${crs.substringAfter(':').takeLast(2)}S · $crs"
    else -> crs
}

private const val UTM_A = 6378137.0
private const val UTM_E2 = 0.0066943799901413165
private const val UTM_K0 = 0.9996
private fun toUtm(latitude: Double, longitude: Double, zone: Int, north: Boolean): Pair<Double, Double> {
    val lat = Math.toRadians(latitude); val lon = Math.toRadians(longitude)
    val lon0 = Math.toRadians((zone - 1) * 6.0 - 180.0 + 3.0)
    val ep2 = UTM_E2 / (1 - UTM_E2); val n = UTM_A / kotlin.math.sqrt(1 - UTM_E2 * kotlin.math.sin(lat).let { it * it })
    val t = kotlin.math.tan(lat).let { it * it }; val c = ep2 * kotlin.math.cos(lat).let { it * it }; val aa = kotlin.math.cos(lat) * (lon - lon0)
    val m = UTM_A * ((1 - UTM_E2 / 4 - 3 * UTM_E2 * UTM_E2 / 64 - 5 * UTM_E2 * UTM_E2 * UTM_E2 / 256) * lat - (3 * UTM_E2 / 8 + 3 * UTM_E2 * UTM_E2 / 32 + 45 * UTM_E2 * UTM_E2 * UTM_E2 / 1024) * kotlin.math.sin(2 * lat) + (15 * UTM_E2 * UTM_E2 / 256 + 45 * UTM_E2 * UTM_E2 * UTM_E2 / 1024) * kotlin.math.sin(4 * lat) - 35 * UTM_E2 * UTM_E2 * UTM_E2 / 3072 * kotlin.math.sin(6 * lat))
    val easting = UTM_K0 * n * (aa + (1 - t + c) * aa.pow(3.0) / 6 + (5 - 18 * t + t * t + 72 * c - 58 * ep2) * aa.pow(5.0) / 120) + 500000
    var northing = UTM_K0 * (m + n * kotlin.math.tan(lat) * (aa * aa / 2 + (5 - t + 9 * c + 4 * c * c) * aa.pow(4.0) / 24 + (61 - 58 * t + t * t + 600 * c - 330 * ep2) * aa.pow(6.0) / 720))
    if (!north) northing += 10000000
    return northing to easting
}
private fun fromUtm(easting: Double, northingInput: Double, zone: Int, north: Boolean): Pair<Double, Double> {
    val x = easting - 500000; val y = if (north) northingInput else northingInput - 10000000
    val ep2 = UTM_E2 / (1 - UTM_E2); val m = y / UTM_K0
    val mu = m / (UTM_A * (1 - UTM_E2 / 4 - 3 * UTM_E2 * UTM_E2 / 64 - 5 * UTM_E2 * UTM_E2 * UTM_E2 / 256))
    val e1 = (1 - kotlin.math.sqrt(1 - UTM_E2)) / (1 + kotlin.math.sqrt(1 - UTM_E2))
    val phi1 = mu + (3 * e1 / 2 - 27 * e1.pow(3.0) / 32) * kotlin.math.sin(2 * mu) + (21 * e1 * e1 / 16 - 55 * e1.pow(4.0) / 32) * kotlin.math.sin(4 * mu) + 151 * e1.pow(3.0) / 96 * kotlin.math.sin(6 * mu) + 1097 * e1.pow(4.0) / 512 * kotlin.math.sin(8 * mu)
    val n1 = UTM_A / kotlin.math.sqrt(1 - UTM_E2 * kotlin.math.sin(phi1).let { it * it }); val t1 = kotlin.math.tan(phi1).let { it * it }; val c1 = ep2 * kotlin.math.cos(phi1).let { it * it }; val r1 = UTM_A * (1 - UTM_E2) / (1 - UTM_E2 * kotlin.math.sin(phi1).let { it * it }).pow(1.5); val d = x / (n1 * UTM_K0)
    val lat = phi1 - n1 * kotlin.math.tan(phi1) / r1 * (d * d / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * ep2) * d.pow(4.0) / 24 + (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * ep2 - 3 * c1 * c1) * d.pow(6.0) / 720)
    val lon = Math.toRadians((zone - 1) * 6.0 - 180.0 + 3.0) + (d - (1 + 2 * t1 + c1) * d.pow(3.0) / 6 + (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * ep2 + 24 * t1 * t1) * d.pow(5.0) / 120) / kotlin.math.cos(phi1)
    return Math.toDegrees(lat) to Math.toDegrees(lon)
}

@Suppress("MissingPermission")
private fun requestCurrentLocation(context: Context, onResult: (Location?) -> Unit) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
    var bestFix: Location? = null

    fun deliverIfBetter(fix: Location?) {
        if (fix == null) return
        val previous = bestFix
        if (previous == null || fix.accuracy < previous.accuracy ||
            (fix.time > previous.time + 15_000L && fix.accuracy <= previous.accuracy + 100f)) {
            bestFix = fix
            onResult(fix)
        }
    }

    // Show a recent cached fix immediately, then refine it with live providers.
    try {
        val cached = providers.mapNotNull { provider ->
            try { manager.getLastKnownLocation(provider) } catch (_: IllegalArgumentException) { null }
        }.maxByOrNull { it.time }
        deliverIfBetter(cached)
    } catch (_: SecurityException) {
        onResult(null)
        return
    }

    var requestedProvider = false
    providers.forEach { provider ->
        try {
            if (!manager.isProviderEnabled(provider)) return@forEach
            requestedProvider = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.getCurrentLocation(provider, null, context.mainExecutor) { fix -> deliverIfBetter(fix) }
            } else {
                manager.requestSingleUpdate(provider, object : LocationListener {
                    override fun onLocationChanged(location: Location) = deliverIfBetter(location)
                    @Deprecated("Deprecated by Android") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            // The permission may have been revoked while the request was starting.
        } catch (_: IllegalArgumentException) {
            // A device may omit a provider; continue with the other one.
        }
    }
    if (bestFix == null && !requestedProvider) onResult(null)
}


