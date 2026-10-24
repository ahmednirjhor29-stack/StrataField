package com.stratafield.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stratafield.app.data.model.*
import com.stratafield.app.data.repository.StrataFieldRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StrataFieldViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StrataFieldRepository.getInstance(application)

    // ============ DASHBOARD ============
    private val _dashboardStats = MutableStateFlow<DashboardStats?>(null)
    val dashboardStats: StateFlow<DashboardStats?> = _dashboardStats.asStateFlow()

    private val _recentProjects = MutableStateFlow<List<Project>>(emptyList())
    val recentProjects: StateFlow<List<Project>> = _recentProjects.asStateFlow()

    fun loadDashboard() {
        viewModelScope.launch {
            _dashboardStats.value = repository.getDashboardStats()
        }
    }

    // ============ PROJECTS ============
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    fun loadProjects() {
        viewModelScope.launch {
            repository.getAllProjects().collect { list ->
                _projects.value = list
                _recentProjects.value = list.take(5)
            }
        }
    }

    suspend fun createProject(
        name: String,
        description: String,
        location: String,
        director: String,
        institution: String,
        standardType: StandardType
    ): Long {
        val project = Project(
            name = name,
            description = description,
            location = location,
            director = director,
            institution = institution,
            standardType = standardType
        )
        return repository.insertProject(project)
    }

    suspend fun updateProject(project: Project) {
        repository.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(project: Project) {
        repository.deleteProject(project)
    }

    // ============ PROJECT DETAIL ============
    private val _currentProject = MutableStateFlow<ProjectWithStats?>(null)
    val currentProject: StateFlow<ProjectWithStats?> = _currentProject.asStateFlow()

    private val _projectSurveys = MutableStateFlow<List<Survey>>(emptyList())
    val projectSurveys: StateFlow<List<Survey>> = _projectSurveys.asStateFlow()

    private val _projectSites = MutableStateFlow<List<Site>>(emptyList())
    val projectSites: StateFlow<List<Site>> = _projectSites.asStateFlow()

    private val _projectTrenches = MutableStateFlow<List<Trench>>(emptyList())
    val projectTrenches: StateFlow<List<Trench>> = _projectTrenches.asStateFlow()

    private val _projectResponses = MutableStateFlow<List<FormResponse>>(emptyList())
    val projectResponses: StateFlow<List<FormResponse>> = _projectResponses.asStateFlow()

    private val _geologicalMapLayer = MutableStateFlow<GeologicalMapLayer?>(null)
    val geologicalMapLayer: StateFlow<GeologicalMapLayer?> = _geologicalMapLayer.asStateFlow()

    private val _trenchDistribution = MutableStateFlow<List<TrenchDistribution>>(emptyList())
    val trenchDistribution: StateFlow<List<TrenchDistribution>> = _trenchDistribution.asStateFlow()

    private val _dailyProgress = MutableStateFlow<List<DailyProgress>>(emptyList())
    val dailyProgress: StateFlow<List<DailyProgress>> = _dailyProgress.asStateFlow()

    fun loadProjectDetail(projectId: Long) {
        viewModelScope.launch {
            _currentProject.value = repository.getProjectWithStats(projectId)
            _trenchDistribution.value = repository.getTrenchDistribution(projectId)
            _dailyProgress.value = repository.getDailyProgress(projectId, 7)
        }
        viewModelScope.launch {
            repository.getSurveysByProject(projectId).collect { _projectSurveys.value = it }
        }
        viewModelScope.launch {
            repository.getSitesByProject(projectId).collect { _projectSites.value = it }
        }
        viewModelScope.launch {
            repository.getTrenchesByProject(projectId).collect { _projectTrenches.value = it }
        }
        viewModelScope.launch {
            repository.getResponsesByProject(projectId).collect { _projectResponses.value = it }
        }
        viewModelScope.launch {
            repository.ensureGeologicalMapLayer(projectId)
            repository.getGeologicalMapLayer(projectId).collect { _geologicalMapLayer.value = it }
        }
    }

    fun saveGeologicalMapLayer(projectId: Long, geoJson: String, visible: Boolean) {
        viewModelScope.launch {
            repository.saveGeologicalMapLayer(GeologicalMapLayer(projectId, geoJson, visible))
        }
    }

    // ============ SURVEYS ============
    suspend fun createSurvey(
        projectId: Long,
        name: String,
        description: String,
        surveyType: String,
        gridSize: String,
        transectSpacing: String
    ): Long {
        val survey = Survey(
            projectId = projectId,
            name = name,
            description = description,
            surveyType = surveyType,
            gridSize = gridSize,
            transectSpacing = transectSpacing
        )
        return repository.insertSurvey(survey)
    }

    suspend fun updateSurvey(survey: Survey) {
        repository.updateSurvey(survey.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteSurvey(survey: Survey) {
        repository.deleteSurvey(survey)
    }

    // ============ SITES ============
    suspend fun createSite(
        projectId: Long,
        surveyId: Long?,
        siteCode: String,
        name: String,
        description: String,
        latitude: Double?,
        longitude: Double?,
        elevation: Double?,
        siteType: String,
        period: String
    ): Long {
        val site = Site(
            projectId = projectId,
            surveyId = surveyId,
            siteCode = siteCode,
            name = name,
            description = description,
            latitude = latitude,
            longitude = longitude,
            elevation = elevation,
            siteType = siteType,
            period = period
        )
        return repository.insertSite(site)
    }

    suspend fun updateSite(site: Site) {
        repository.updateSite(site.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteSite(site: Site) {
        repository.deleteSite(site)
    }

    // ============ TRENCHES ============
    suspend fun createTrench(
        projectId: Long,
        siteId: Long,
        trenchCode: String,
        name: String,
        length: Double?,
        width: Double?,
        depth: Double?,
        orientation: String,
        gridReference: String,
        description: String
    ): Long {
        val trench = Trench(
            projectId = projectId,
            siteId = siteId,
            trenchCode = trenchCode,
            name = name,
            length = length,
            width = width,
            depth = depth,
            orientation = orientation,
            gridReference = gridReference,
            description = description
        )
        return repository.insertTrench(trench)
    }

    suspend fun updateTrench(trench: Trench) {
        repository.updateTrench(trench.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTrench(trench: Trench) {
        repository.deleteTrench(trench)
    }

    // ============ FORM TEMPLATES ============
    private val _templates = MutableStateFlow<List<FormTemplate>>(emptyList())
    val templates: StateFlow<List<FormTemplate>> = _templates.asStateFlow()

    fun loadTemplates() {
        viewModelScope.launch {
            repository.getAllTemplates().collect { _templates.value = it }
        }
    }

    suspend fun getFormTemplate(id: Long): FormTemplate? = repository.getTemplateOnce(id)

    suspend fun getFormTemplateForResponse(response: FormResponse): FormTemplate? {
        response.templateId?.let { repository.getTemplateOnce(it)?.let { template -> return template } }
        val savedFields = parseResponseData(response.dataJson).keys.map { fieldId ->
            FormFieldDefinition(fieldId, fieldId, FieldType.TEXTAREA, section = "Saved Fields")
        }
        if (savedFields.isEmpty()) return null
        return FormTemplate(
            id = response.templateId ?: 0L,
            name = response.formName,
            formType = response.formType,
            fieldsJson = com.google.gson.Gson().toJson(savedFields)
        )
    }

    suspend fun getOrCreateProjectFormTemplate(formType: FormType): FormTemplate {
        val name = when (formType) {
            FormType.SURVEY -> "Survey Data Form"
            FormType.TRENCH -> "Excavation Context Form"
            else -> error("Unsupported project form type: $formType")
        }
        val fields = when (formType) {
            FormType.SURVEY -> listOf(
                FormFieldDefinition("survey_name", "Survey Name", FieldType.TEXT, required = true, section = "General"),
                FormFieldDefinition("surveyor_name", "Surveyor's Name", FieldType.TEXT, section = "General"),
                FormFieldDefinition("survey_type", "Survey Type", FieldType.TEXT, placeholder = "e.g. pedestrian, geophysical", section = "General"),
                FormFieldDefinition("survey_date", "Survey Date", FieldType.DATE, required = true, section = "General"),
                FormFieldDefinition("survey_location", "Location and Coordinates", FieldType.GPS, section = "Location"),
                FormFieldDefinition("survey_notes", "Notes", FieldType.TEXTAREA, section = "General"),
                FormFieldDefinition("survey_media", "Photos and Media", FieldType.IMAGE, section = "Documentation"),
                FormFieldDefinition("survey_drawing", "Freehand Drawing", FieldType.DRAWING, section = "Documentation"),
                FormFieldDefinition("survey_graph", "Graph Paper", FieldType.GRAPH_PAPER, section = "Documentation"),
                FormFieldDefinition("survey_matrix", "Harris Matrix", FieldType.MATRIX, section = "Stratigraphy"),
                FormFieldDefinition("soil_depth_from", "Depth From (m)", FieldType.NUMBER, section = "Soil Profile"),
                FormFieldDefinition("soil_depth_to", "Depth To (m)", FieldType.NUMBER, section = "Soil Profile"),
                FormFieldDefinition("soil_texture", "Texture", FieldType.TEXT, placeholder = "Describe soil texture", section = "Soil Profile"),
                FormFieldDefinition("soil_compactness", "Compactness", FieldType.DROPDOWN, options = listOf("Loose", "Soft", "Friable", "Firm", "Compact", "Very compact", "Hard", "Add"), section = "Soil Profile")
            )
            FormType.TRENCH -> listOf(
                FormFieldDefinition("trench_code", "Excavation Code", FieldType.TEXT, required = true, section = "General"),
                FormFieldDefinition("surveyor_name", "Surveyor's Name", FieldType.TEXT, section = "General"),
                FormFieldDefinition("trench_name", "Excavation Name", FieldType.TEXT, section = "General"),
                FormFieldDefinition("excavation_date", "Excavation Date", FieldType.DATE, required = true, section = "General"),
                FormFieldDefinition("excavation_area", "Area", FieldType.TEXT, placeholder = "e.g. Trench 1", section = "Excavation"),
                FormFieldDefinition("excavation_depth", "Maximum Depth (m)", FieldType.NUMBER, section = "Excavation"),
                FormFieldDefinition("excavation_location", "Location and Coordinates", FieldType.GPS, section = "Location"),
                FormFieldDefinition("excavation_description", "Description", FieldType.TEXTAREA, section = "Description"),
                FormFieldDefinition("excavation_notes", "Notes", FieldType.TEXTAREA, section = "General"),
                FormFieldDefinition("excavation_media", "Photos and Media", FieldType.IMAGE, section = "Documentation"),
                FormFieldDefinition("excavation_drawing", "Freehand Drawing", FieldType.DRAWING, section = "Documentation"),
                FormFieldDefinition("excavation_graph", "Graph Paper", FieldType.GRAPH_PAPER, section = "Documentation"),
                FormFieldDefinition("excavation_matrix", "Harris Matrix", FieldType.MATRIX, section = "Stratigraphy"),
                FormFieldDefinition("excavation_texture", "Texture", FieldType.TEXT, placeholder = "Describe soil texture", section = "Soil Profile"),
                FormFieldDefinition("excavation_compactness", "Compactness", FieldType.DROPDOWN, options = listOf("Loose", "Soft", "Friable", "Firm", "Compact", "Very compact", "Hard", "Add"), section = "Soil Profile")
            )
            else -> emptyList()
        }
        val existing = repository.getAllTemplates().first().firstOrNull { it.formType == formType && it.name == name }
        val gson = com.google.gson.Gson()
        val template = if (existing == null) {
            val created = FormTemplate(
                name = name,
                description = if (formType == FormType.SURVEY) "Survey field record" else "Excavation context field record",
                formType = formType,
                standardType = StandardType.CUSTOM,
                fieldsJson = gson.toJson(fields)
            )
            created.copy(id = repository.insertTemplate(created))
        } else {
            val existingFields = parseFields(existing.fieldsJson).filterNot { it.fieldType == FieldType.CALCULATOR }
            val missingFields = fields.filterNot { default -> existingFields.any { it.id == default.id } }
            val upgradedFields = existingFields.map { saved ->
                val currentDefault = fields.firstOrNull { it.id == saved.id } ?: return@map saved
                currentDefault.copy(
                    label = saved.label.ifBlank { currentDefault.label },
                    required = saved.required || currentDefault.required,
                    placeholder = saved.placeholder.ifBlank { currentDefault.placeholder }
                )
            } + missingFields
            val updatedJson = gson.toJson(upgradedFields)
            if (updatedJson == existing.fieldsJson) existing else existing.copy(fieldsJson = updatedJson).also { repository.updateTemplate(it) }
        }
        loadTemplates()
        return template
    }

    suspend fun createTemplate(
        name: String,
        description: String,
        formType: FormType,
        standardType: StandardType,
        fields: List<FormFieldDefinition>
    ): Long {
        val template = FormTemplate(
            name = name,
            description = description,
            formType = formType,
            standardType = standardType,
            isBuiltIn = false,
            fieldsJson = com.google.gson.Gson().toJson(fields)
        )
        return repository.insertTemplate(template)
    }

    // ============ FORM RESPONSES ============
    suspend fun saveFormResponse(
        projectId: Long,
        surveyId: Long?,
        siteId: Long?,
        trenchId: Long?,
        templateId: Long?,
        formName: String,
        formType: FormType,
        data: Map<String, String>,
        status: DataStatus = DataStatus.DRAFT
    ): Long {
        val response = FormResponse(
            projectId = projectId,
            surveyId = surveyId,
            siteId = siteId,
            trenchId = trenchId,
            templateId = templateId,
            formName = formName,
            formType = formType,
            dataJson = repository.serializeResponseData(data),
            status = status,
            submittedAt = if (status != DataStatus.DRAFT) System.currentTimeMillis() else null
        )
        return repository.insertResponse(response)
    }

    suspend fun updateFormResponse(response: FormResponse) {
        repository.updateResponse(response.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteFormResponse(response: FormResponse) {
        repository.deleteResponse(response)
    }

    // ============ IMAGES ============
    suspend fun addImage(responseId: Long, filePath: String, caption: String, fieldId: String): Long {
        return repository.insertImage(
            ImageAttachment(
                responseId = responseId,
                filePath = filePath,
                caption = caption,
                fieldId = fieldId
            )
        )
    }

    // ============ DRAWINGS ============
    suspend fun saveDrawing(
        responseId: Long,
        fieldId: String,
        title: String,
        strokesJson: String,
        backgroundType: String
    ): Long {
        return repository.insertDrawing(
            Drawing(
                responseId = responseId,
                fieldId = fieldId,
                title = title,
                strokesJson = strokesJson,
                backgroundType = backgroundType
            )
        )
    }

    // ============ HELPERS ============
    fun parseFields(fieldsJson: String): List<FormFieldDefinition> = repository.parseFields(fieldsJson)
    fun parseResponseData(dataJson: String): Map<String, String> = repository.parseResponseData(dataJson)
    fun serializeResponseData(data: Map<String, String>): String = repository.serializeResponseData(data)
}
