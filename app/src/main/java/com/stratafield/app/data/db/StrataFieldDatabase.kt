package com.stratafield.app.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Update
import com.stratafield.app.data.model.*
import kotlinx.coroutines.flow.Flow

// ============ DAOs ============

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun getById(id: Long): Flow<Project?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getByIdOnce(id: Long): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: Project): Long

    @Update
    suspend fun update(project: Project)

    @Delete
    suspend fun delete(project: Project)

    @Query("SELECT COUNT(*) FROM projects")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun getCountOnce(): Int
}

@Dao
interface SurveyDao {
    @Query("SELECT * FROM surveys WHERE projectId = :projectId ORDER BY startDate DESC")
    fun getByProject(projectId: Long): Flow<List<Survey>>

    @Query("SELECT * FROM surveys WHERE id = :id")
    fun getById(id: Long): Flow<Survey?>

    @Query("SELECT * FROM surveys WHERE id = :id")
    suspend fun getByIdOnce(id: Long): Survey?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(survey: Survey): Long

    @Update
    suspend fun update(survey: Survey)

    @Delete
    suspend fun delete(survey: Survey)

    @Query("SELECT COUNT(*) FROM surveys WHERE projectId = :projectId")
    fun getCountByProject(projectId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM surveys WHERE projectId = :projectId")
    suspend fun getCountByProjectOnce(projectId: Long): Int

    @Query("SELECT COUNT(*) FROM surveys")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM surveys WHERE startDate >= :startOfWeek")
    suspend fun getCountSince(startOfWeek: Long): Int

    @Query("SELECT COUNT(*) FROM surveys WHERE startDate >= :startOfMonth")
    suspend fun getCountSinceMonth(startOfMonth: Long): Int
}

@Dao
interface SiteDao {
    @Query("SELECT * FROM sites WHERE projectId = :projectId ORDER BY siteCode")
    fun getByProject(projectId: Long): Flow<List<Site>>

    @Query("SELECT * FROM sites WHERE surveyId = :surveyId ORDER BY siteCode")
    fun getBySurvey(surveyId: Long): Flow<List<Site>>

    @Query("SELECT * FROM sites WHERE id = :id")
    fun getById(id: Long): Flow<Site?>

    @Query("SELECT * FROM sites WHERE id = :id")
    suspend fun getByIdOnce(id: Long): Site?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(site: Site): Long

    @Update
    suspend fun update(site: Site)

    @Delete
    suspend fun delete(site: Site)

    @Query("SELECT COUNT(*) FROM sites WHERE projectId = :projectId")
    fun getCountByProject(projectId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM sites WHERE projectId = :projectId")
    suspend fun getCountByProjectOnce(projectId: Long): Int

    @Query("SELECT COUNT(*) FROM sites")
    suspend fun getTotalCount(): Int
}

@Dao
interface TrenchDao {
    @Query("SELECT * FROM trenches WHERE projectId = :projectId ORDER BY trenchCode")
    fun getByProject(projectId: Long): Flow<List<Trench>>

    @Query("SELECT * FROM trenches WHERE siteId = :siteId ORDER BY trenchCode")
    fun getBySite(siteId: Long): Flow<List<Trench>>

    @Query("SELECT * FROM trenches WHERE id = :id")
    fun getById(id: Long): Flow<Trench?>

    @Query("SELECT * FROM trenches WHERE id = :id")
    suspend fun getByIdOnce(id: Long): Trench?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trench: Trench): Long

    @Update
    suspend fun update(trench: Trench)

    @Delete
    suspend fun delete(trench: Trench)

    @Query("SELECT COUNT(*) FROM trenches WHERE projectId = :projectId")
    fun getCountByProject(projectId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM trenches WHERE projectId = :projectId")
    suspend fun getCountByProjectOnce(projectId: Long): Int

    @Query("SELECT COUNT(*) FROM trenches")
    suspend fun getTotalCount(): Int

    @Query("SELECT sites.name as siteName, COUNT(trenches.id) as trenchCount FROM trenches INNER JOIN sites ON trenches.siteId = sites.id WHERE trenches.projectId = :projectId GROUP BY sites.id")
    suspend fun getTrenchDistribution(projectId: Long): List<TrenchDistribution>
}

@Dao
interface FormTemplateDao {
    @Query("SELECT * FROM form_templates ORDER BY isBuiltIn DESC, name")
    fun getAll(): Flow<List<FormTemplate>>

    @Query("SELECT * FROM form_templates WHERE formType = :formType ORDER BY name")
    fun getByType(formType: FormType): Flow<List<FormTemplate>>

    @Query("SELECT * FROM form_templates WHERE id = :id")
    fun getById(id: Long): Flow<FormTemplate?>

    @Query("SELECT * FROM form_templates WHERE id = :id")
    suspend fun getByIdOnce(id: Long): FormTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: FormTemplate): Long

    @Update
    suspend fun update(template: FormTemplate)

    @Delete
    suspend fun delete(template: FormTemplate)

    @Query("SELECT COUNT(*) FROM form_templates")
    suspend fun getCount(): Int

    @Query("SELECT name FROM form_templates WHERE isBuiltIn = 1")
    suspend fun getBuiltInNames(): List<String>
}

@Dao
interface FormResponseDao {
    @Query("SELECT * FROM form_responses WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun getByProject(projectId: Long): Flow<List<FormResponse>>

    @Query("SELECT * FROM form_responses WHERE surveyId = :surveyId ORDER BY updatedAt DESC")
    fun getBySurvey(surveyId: Long): Flow<List<FormResponse>>

    @Query("SELECT * FROM form_responses WHERE siteId = :siteId ORDER BY updatedAt DESC")
    fun getBySite(siteId: Long): Flow<List<FormResponse>>

    @Query("SELECT * FROM form_responses WHERE trenchId = :trenchId ORDER BY updatedAt DESC")
    fun getByTrench(trenchId: Long): Flow<List<FormResponse>>

    @Query("SELECT * FROM form_responses WHERE id = :id")
    fun getById(id: Long): Flow<FormResponse?>

    @Query("SELECT * FROM form_responses WHERE id = :id")
    suspend fun getByIdOnce(id: Long): FormResponse?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(response: FormResponse): Long

    @Update
    suspend fun update(response: FormResponse)

    @Delete
    suspend fun delete(response: FormResponse)

    @Query("SELECT COUNT(*) FROM form_responses WHERE projectId = :projectId")
    fun getCountByProject(projectId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM form_responses WHERE projectId = :projectId")
    suspend fun getCountByProjectOnce(projectId: Long): Int

    @Query("SELECT COUNT(*) FROM form_responses")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM form_responses WHERE status = 'COMPLETE' OR status = 'SUBMITTED' OR status = 'SYNCED'")
    suspend fun getCompletedCount(): Int

    @Query("SELECT COUNT(*) FROM form_responses WHERE status = 'DRAFT'")
    suspend fun getDraftCount(): Int

    @Query("SELECT COUNT(*) FROM form_responses WHERE projectId = :projectId AND (status = 'COMPLETE' OR status = 'SUBMITTED' OR status = 'SYNCED')")
    suspend fun getCompletedCountByProject(projectId: Long): Int

    @Query("SELECT * FROM form_responses WHERE projectId = :projectId AND (status = 'COMPLETE' OR status = 'SUBMITTED' OR status = 'SYNCED') ORDER BY updatedAt DESC")
    suspend fun getCompletedByProject(projectId: Long): List<FormResponse>

    @Query("SELECT * FROM form_responses WHERE projectId = :projectId ORDER BY updatedAt DESC")
    suspend fun getAllByProjectOnce(projectId: Long): List<FormResponse>

    @Query("SELECT * FROM form_responses WHERE projectId = :projectId AND updatedAt >= :startOfDay AND updatedAt < :endOfDay")
    suspend fun getByDateRange(projectId: Long, startOfDay: Long, endOfDay: Long): List<FormResponse>
}

@Dao
interface ImageAttachmentDao {
    @Query("SELECT * FROM image_attachments WHERE responseId = :responseId ORDER BY timestamp DESC")
    fun getByResponse(responseId: Long): Flow<List<ImageAttachment>>

    @Query("SELECT * FROM image_attachments WHERE responseId = :responseId")
    suspend fun getByResponseOnce(responseId: Long): List<ImageAttachment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: ImageAttachment): Long

    @Delete
    suspend fun delete(attachment: ImageAttachment)

    @Query("SELECT COUNT(*) FROM image_attachments")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM image_attachments WHERE responseId = :responseId")
    suspend fun getCountByResponse(responseId: Long): Int
}

@Dao
interface DrawingDao {
    @Query("SELECT * FROM drawings WHERE responseId = :responseId ORDER BY createdAt")
    fun getByResponse(responseId: Long): Flow<List<Drawing>>

    @Query("SELECT * FROM drawings WHERE responseId = :responseId")
    suspend fun getByResponseOnce(responseId: Long): List<Drawing>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(drawing: Drawing): Long

    @Update
    suspend fun update(drawing: Drawing)

    @Delete
    suspend fun delete(drawing: Drawing)
}

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecent(): Flow<List<SyncLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SyncLog): Long

    @Query("DELETE FROM sync_logs WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

// ============ DATABASE ============

@Database(
    entities = [
        Project::class,
        Survey::class,
        Site::class,
        Trench::class,
        FormTemplate::class,
        FormResponse::class,
        ImageAttachment::class,
        Drawing::class,
        SyncLog::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class StrataFieldDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun surveyDao(): SurveyDao
    abstract fun siteDao(): SiteDao
    abstract fun trenchDao(): TrenchDao
    abstract fun formTemplateDao(): FormTemplateDao
    abstract fun formResponseDao(): FormResponseDao
    abstract fun imageAttachmentDao(): ImageAttachmentDao
    abstract fun drawingDao(): DrawingDao
    abstract fun syncLogDao(): SyncLogDao
}