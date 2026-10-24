package com.stratafield.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stratafield.app.data.db.StrataFieldDatabase
import com.stratafield.app.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class StrataFieldRepository private constructor(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        StrataFieldDatabase::class.java,
        "stratafield.db"
    ).addMigrations(MIGRATION_1_2).build()

    private val gson = Gson()

    // ============ PROJECTS ============
    fun getAllProjects(): Flow<List<Project>> = db.projectDao().getAll()
    fun getProject(id: Long): Flow<Project?> = db.projectDao().getById(id)
    suspend fun getProjectOnce(id: Long): Project? = db.projectDao().getByIdOnce(id)
    suspend fun insertProject(project: Project): Long = db.projectDao().insert(project)
    suspend fun updateProject(project: Project) = db.projectDao().update(project)
    suspend fun deleteProject(project: Project) = db.projectDao().delete(project)
    fun getProjectCount(): Flow<Int> = db.projectDao().getCount()

    // ============ SURVEYS ============
    fun getSurveysByProject(projectId: Long): Flow<List<Survey>> = db.surveyDao().getByProject(projectId)
    fun getSurvey(id: Long): Flow<Survey?> = db.surveyDao().getById(id)
    suspend fun getSurveyOnce(id: Long): Survey? = db.surveyDao().getByIdOnce(id)
    suspend fun insertSurvey(survey: Survey): Long = db.surveyDao().insert(survey)
    suspend fun updateSurvey(survey: Survey) = db.surveyDao().update(survey)
    suspend fun deleteSurvey(survey: Survey) = db.surveyDao().delete(survey)
    suspend fun getSurveyCountByProject(projectId: Long): Int = db.surveyDao().getCountByProjectOnce(projectId)

    // ============ SITES ============
    fun getSitesByProject(projectId: Long): Flow<List<Site>> = db.siteDao().getByProject(projectId)
    fun getSitesBySurvey(surveyId: Long): Flow<List<Site>> = db.siteDao().getBySurvey(surveyId)
    fun getSite(id: Long): Flow<Site?> = db.siteDao().getById(id)
    suspend fun getSiteOnce(id: Long): Site? = db.siteDao().getByIdOnce(id)
    suspend fun insertSite(site: Site): Long = db.siteDao().insert(site)
    suspend fun updateSite(site: Site) = db.siteDao().update(site)
    suspend fun deleteSite(site: Site) = db.siteDao().delete(site)
    suspend fun getSiteCountByProject(projectId: Long): Int = db.siteDao().getCountByProjectOnce(projectId)

    // ============ TRENCHES ============
    fun getTrenchesByProject(projectId: Long): Flow<List<Trench>> = db.trenchDao().getByProject(projectId)
    fun getTrenchesBySite(siteId: Long): Flow<List<Trench>> = db.trenchDao().getBySite(siteId)
    fun getTrench(id: Long): Flow<Trench?> = db.trenchDao().getById(id)
    suspend fun getTrenchOnce(id: Long): Trench? = db.trenchDao().getByIdOnce(id)
    suspend fun insertTrench(trench: Trench): Long = db.trenchDao().insert(trench)
    suspend fun updateTrench(trench: Trench) = db.trenchDao().update(trench)
    suspend fun deleteTrench(trench: Trench) = db.trenchDao().delete(trench)
    suspend fun getTrenchCountByProject(projectId: Long): Int = db.trenchDao().getCountByProjectOnce(projectId)
    suspend fun getTrenchDistribution(projectId: Long): List<TrenchDistribution> = db.trenchDao().getTrenchDistribution(projectId)

    // ============ FORM TEMPLATES ============
    fun getAllTemplates(): Flow<List<FormTemplate>> = db.formTemplateDao().getAll()
    fun getTemplatesByType(formType: FormType): Flow<List<FormTemplate>> = db.formTemplateDao().getByType(formType)
    fun getTemplate(id: Long): Flow<FormTemplate?> = db.formTemplateDao().getById(id)
    suspend fun getTemplateOnce(id: Long): FormTemplate? = db.formTemplateDao().getByIdOnce(id)
    suspend fun insertTemplate(template: FormTemplate): Long = db.formTemplateDao().insert(template)
    suspend fun updateTemplate(template: FormTemplate) = db.formTemplateDao().update(template)
    suspend fun deleteTemplate(template: FormTemplate) = db.formTemplateDao().delete(template)

    // ============ FORM RESPONSES ============
    fun getResponsesByProject(projectId: Long): Flow<List<FormResponse>> = db.formResponseDao().getByProject(projectId)
    fun getResponsesBySurvey(surveyId: Long): Flow<List<FormResponse>> = db.formResponseDao().getBySurvey(surveyId)
    fun getResponsesBySite(siteId: Long): Flow<List<FormResponse>> = db.formResponseDao().getBySite(siteId)
    fun getResponsesByTrench(trenchId: Long): Flow<List<FormResponse>> = db.formResponseDao().getByTrench(trenchId)
    fun getResponse(id: Long): Flow<FormResponse?> = db.formResponseDao().getById(id)
    suspend fun getResponseOnce(id: Long): FormResponse? = db.formResponseDao().getByIdOnce(id)
    suspend fun insertResponse(response: FormResponse): Long = db.formResponseDao().insert(response)
    suspend fun updateResponse(response: FormResponse) = db.formResponseDao().update(response)
    suspend fun deleteResponse(response: FormResponse) = db.formResponseDao().delete(response)
    suspend fun getResponseCountByProject(projectId: Long): Int = db.formResponseDao().getCountByProjectOnce(projectId)
    suspend fun getCompletedResponsesByProject(projectId: Long): List<FormResponse> = db.formResponseDao().getCompletedByProject(projectId)
    suspend fun getAllResponsesByProject(projectId: Long): List<FormResponse> = db.formResponseDao().getAllByProjectOnce(projectId)

    // ============ IMAGES ============
    fun getImagesByResponse(responseId: Long): Flow<List<ImageAttachment>> = db.imageAttachmentDao().getByResponse(responseId)
    suspend fun getImagesByResponseOnce(responseId: Long): List<ImageAttachment> = db.imageAttachmentDao().getByResponseOnce(responseId)
    suspend fun insertImage(attachment: ImageAttachment): Long = db.imageAttachmentDao().insert(attachment)
    suspend fun deleteImage(attachment: ImageAttachment) = db.imageAttachmentDao().delete(attachment)

    // ============ DRAWINGS ============
    fun getDrawingsByResponse(responseId: Long): Flow<List<Drawing>> = db.drawingDao().getByResponse(responseId)
    suspend fun getDrawingsByResponseOnce(responseId: Long): List<Drawing> = db.drawingDao().getByResponseOnce(responseId)
    suspend fun insertDrawing(drawing: Drawing): Long = db.drawingDao().insert(drawing)
    suspend fun updateDrawing(drawing: Drawing) = db.drawingDao().update(drawing)
    suspend fun deleteDrawing(drawing: Drawing) = db.drawingDao().delete(drawing)

    // ============ GEOLOGICAL MAP LAYER ============
    fun getGeologicalMapLayer(projectId: Long): Flow<GeologicalMapLayer?> = db.geologicalMapLayerDao().get(projectId)
    suspend fun saveGeologicalMapLayer(layer: GeologicalMapLayer) = db.geologicalMapLayerDao().save(layer)

    suspend fun ensureGeologicalMapLayer(projectId: Long): GeologicalMapLayer {
        val existing = db.geologicalMapLayerDao().getOnce(projectId)
        if (existing != null) return existing
        return GeologicalMapLayer(projectId).also { db.geologicalMapLayerDao().save(it) }
    }

    // ============ SYNC LOGS ============
    fun getRecentSyncLogs(): Flow<List<SyncLog>> = db.syncLogDao().getRecent()
    suspend fun insertSyncLog(log: SyncLog): Long = db.syncLogDao().insert(log)

    // ============ DASHBOARD ============
    suspend fun getDashboardStats(): DashboardStats {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val today = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val weekStart = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, -23)
        val monthStart = calendar.timeInMillis

        return DashboardStats(
            totalProjects = db.projectDao().getCountOnce(),
            totalSurveys = db.surveyDao().getTotalCount(),
            totalSites = db.siteDao().getTotalCount(),
            totalTrenches = db.trenchDao().getTotalCount(),
            totalForms = db.formResponseDao().getTotalCount(),
            completedForms = db.formResponseDao().getCompletedCount(),
            draftForms = db.formResponseDao().getDraftCount(),
            totalImages = db.imageAttachmentDao().getTotalCount(),
            surveysThisWeek = db.surveyDao().getCountSince(weekStart),
            surveysThisMonth = db.surveyDao().getCountSinceMonth(monthStart)
        )
    }

    suspend fun getProjectWithStats(projectId: Long): ProjectWithStats? {
        val project = db.projectDao().getByIdOnce(projectId) ?: return null
        return ProjectWithStats(
            project = project,
            surveyCount = db.surveyDao().getCountByProjectOnce(projectId),
            siteCount = db.siteDao().getCountByProjectOnce(projectId),
            trenchCount = db.trenchDao().getCountByProjectOnce(projectId),
            formCount = db.formResponseDao().getCountByProjectOnce(projectId)
        )
    }

    suspend fun getDailyProgress(projectId: Long, days: Int = 7): List<DailyProgress> {
        val result = mutableListOf<DailyProgress>()
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        for (i in days - 1 downTo 0) {
            val startOfDay = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val endOfDay = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, -2)

            val responses = db.formResponseDao().getByDateRange(projectId, startOfDay, endOfDay)
            val completed = responses.count { it.status != DataStatus.DRAFT }
            val surveys = db.surveyDao().getCountSince(startOfDay)

            result.add(
                DailyProgress(
                    date = startOfDay,
                    formsCompleted = completed,
                    surveysCompleted = surveys
                )
            )
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        return result
    }

    // ============ JSON HELPERS ============
    fun parseFields(fieldsJson: String): List<FormFieldDefinition> {
        return try {
            val type = object : TypeToken<List<FormFieldDefinition>>() {}.type
            gson.fromJson(fieldsJson, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseResponseData(dataJson: String): Map<String, String> {
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(dataJson, type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun serializeResponseData(data: Map<String, String>): String = gson.toJson(data)

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS geological_map_layers (projectId INTEGER NOT NULL, geoJson TEXT NOT NULL, visible INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(projectId), FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }
        @Volatile
        private var instance: StrataFieldRepository? = null

        fun getInstance(context: Context): StrataFieldRepository {
            return instance ?: synchronized(this) {
                instance ?: StrataFieldRepository(context).also { instance = it }
            }
        }
    }
}
