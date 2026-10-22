package com.stratafield.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

// ============ ENUMS ============

enum class StandardType {
    US, UK, INDIAN, BANGLADESHI, CUSTOM
}

enum class FormType {
    SURVEY, SITE, TRENCH, ARTIFACT, CUSTOM
}

enum class FieldType {
    TEXT, TEXTAREA, NUMBER, DATE, TIME, DROPDOWN, CHECKBOX, RADIO,
    IMAGE, GPS, DRAWING, GRAPH_PAPER, CALCULATOR, SIGNATURE, SECTION_HEADER, MATRIX
}

enum class DataStatus {
    DRAFT, COMPLETE, SUBMITTED, SYNCED
}

enum class SortField {
    DATE, NAME, STATUS, TYPE, PROJECT
}

enum class SortOrder {
    ASCENDING, DESCENDING
}

// ============ PROJECT ============

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val location: String = "",
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val director: String = "",
    val institution: String = "",
    val standardType: StandardType = StandardType.CUSTOM,
    val colorHex: String = "#000000",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ============ SURVEY ============

@Entity(
    tableName = "surveys",
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class Survey(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val description: String = "",
    val surveyType: String = "Systematic",
    val gridSize: String = "",
    val transectSpacing: String = "",
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val status: DataStatus = DataStatus.DRAFT,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ============ SITE ============

@Entity(
    tableName = "sites",
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId"), Index("surveyId")]
)
data class Site(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val surveyId: Long? = null,
    val siteCode: String,
    val name: String,
    val description: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val elevation: Double? = null,
    val siteType: String = "",
    val period: String = "",
    val condition: String = "",
    val discoveredDate: Long = System.currentTimeMillis(),
    val status: DataStatus = DataStatus.DRAFT,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ============ TRENCH ============

@Entity(
    tableName = "trenches",
    foreignKeys = [ForeignKey(
        entity = Site::class,
        parentColumns = ["id"],
        childColumns = ["siteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("siteId"), Index("projectId")]
)
data class Trench(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val siteId: Long,
    val trenchCode: String,
    val name: String = "",
    val length: Double? = null,
    val width: Double? = null,
    val depth: Double? = null,
    val orientation: String = "",
    val gridReference: String = "",
    val description: String = "",
    val openedDate: Long = System.currentTimeMillis(),
    val closedDate: Long? = null,
    val status: DataStatus = DataStatus.DRAFT,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ============ FORM TEMPLATE ============

@Entity(tableName = "form_templates")
data class FormTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val formType: FormType = FormType.CUSTOM,
    val standardType: StandardType = StandardType.CUSTOM,
    val isBuiltIn: Boolean = false,
    val fieldsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis()
)

// ============ FORM FIELD DEFINITION ============

data class FormFieldDefinition(
    val id: String,
    val label: String,
    val fieldType: FieldType,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    val placeholder: String = "",
    val defaultValue: String = "",
    val helpText: String = "",
    val section: String = "",
    val order: Int = 0,
    val validation: String = "",
    val dependsOnFieldId: String = "",
    val dependsOnValue: String = ""
)

// ============ FORM RESPONSE ============

@Entity(
    tableName = "form_responses",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId"), Index("surveyId"), Index("siteId"), Index("trenchId")]
)
data class FormResponse(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val surveyId: Long? = null,
    val siteId: Long? = null,
    val trenchId: Long? = null,
    val templateId: Long? = null,
    val formName: String,
    val formType: FormType = FormType.CUSTOM,
    val dataJson: String = "{}",
    val status: DataStatus = DataStatus.DRAFT,
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val submittedAt: Long? = null
)

// ============ IMAGE ATTACHMENT ============

@Entity(
    tableName = "image_attachments",
    foreignKeys = [ForeignKey(
        entity = FormResponse::class,
        parentColumns = ["id"],
        childColumns = ["responseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("responseId")]
)
data class ImageAttachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val responseId: Long,
    val filePath: String,
    val caption: String = "",
    val fieldId: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// ============ DRAWING ============

@Entity(
    tableName = "drawings",
    foreignKeys = [ForeignKey(
        entity = FormResponse::class,
        parentColumns = ["id"],
        childColumns = ["responseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("responseId")]
)
data class Drawing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val responseId: Long,
    val fieldId: String = "",
    val title: String = "",
    val strokesJson: String = "[]",
    val backgroundType: String = "plain", // plain, graph, grid
    val createdAt: Long = System.currentTimeMillis()
)

// ============ SYNC LOG ============

@Entity(tableName = "sync_logs")
data class SyncLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceName: String,
    val deviceId: String,
    val action: String,
    val entityType: String,
    val entityId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean = true,
    val message: String = ""
)

// ============ TYPE CONVERTERS ============

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStandardType(value: StandardType): String = value.name

    @TypeConverter
    fun toStandardType(value: String): StandardType = StandardType.valueOf(value)

    @TypeConverter
    fun fromFormType(value: FormType): String = value.name

    @TypeConverter
    fun toFormType(value: String): FormType = FormType.valueOf(value)

    @TypeConverter
    fun fromFieldType(value: FieldType): String = value.name

    @TypeConverter
    fun toFieldType(value: String): FieldType = FieldType.valueOf(value)

    @TypeConverter
    fun fromDataStatus(value: DataStatus): String = value.name

    @TypeConverter
    fun toDataStatus(value: String): DataStatus = DataStatus.valueOf(value)

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(value, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromFormFieldList(value: List<FormFieldDefinition>): String = gson.toJson(value)

    @TypeConverter
    fun toFormFieldList(value: String): List<FormFieldDefinition> {
        return try {
            val type = object : TypeToken<List<FormFieldDefinition>>() {}.type
            gson.fromJson(value, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// ============ DASHBOARD DATA ============

data class DashboardStats(
    val totalProjects: Int = 0,
    val totalSurveys: Int = 0,
    val totalSites: Int = 0,
    val totalTrenches: Int = 0,
    val totalForms: Int = 0,
    val completedForms: Int = 0,
    val draftForms: Int = 0,
    val totalImages: Int = 0,
    val surveysThisWeek: Int = 0,
    val surveysThisMonth: Int = 0
)

data class TrenchDistribution(
    val siteName: String,
    val trenchCount: Int
)

data class DailyProgress(
    val date: Long,
    val formsCompleted: Int,
    val surveysCompleted: Int
)

data class ProjectWithStats(
    val project: Project,
    val surveyCount: Int = 0,
    val siteCount: Int = 0,
    val trenchCount: Int = 0,
    val formCount: Int = 0
)