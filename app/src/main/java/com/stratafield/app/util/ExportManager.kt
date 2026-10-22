package com.stratafield.app.util

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stratafield.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportManager {

    private val gson = Gson()

    /**
     * Export all project data to CSV files
     * Returns a list of exported file paths
     */
    suspend fun exportProjectToCSV(
        context: Context,
        project: Project,
        surveys: List<Survey>,
        sites: List<Site>,
        trenches: List<Trench>,
        responses: List<FormResponse>
    ): List<File> = withContext(Dispatchers.IO) {
        val exportDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "StrataField/${sanitizeFileName(project.name)}"
        )
        exportDir.mkdirs()

        val files = mutableListOf<File>()

        // Export surveys
        val surveyFile = File(exportDir, "surveys.csv")
        writeCSV(surveyFile, listOf(
            listOf("ID", "Name", "Description", "Survey Type", "Grid Size", "Transect Spacing", "Start Date", "End Date", "Status", "Notes"),
            *surveys.map { survey ->
                listOf(
                    survey.id.toString(),
                    survey.name,
                    survey.description,
                    survey.surveyType,
                    survey.gridSize,
                    survey.transectSpacing,
                    formatDate(survey.startDate),
                    survey.endDate?.let { formatDate(it) } ?: "",
                    survey.status.name,
                    survey.notes
                )
            }.toTypedArray()
        ))
        files.add(surveyFile)

        // Export sites
        val siteFile = File(exportDir, "sites.csv")
        writeCSV(siteFile, listOf(
            listOf("ID", "Site Code", "Name", "Description", "Latitude", "Longitude", "Elevation", "Site Type", "Period", "Condition", "Discovered Date", "Status", "Notes"),
            *sites.map { site ->
                listOf(
                    site.id.toString(),
                    site.siteCode,
                    site.name,
                    site.description,
                    site.latitude?.toString() ?: "",
                    site.longitude?.toString() ?: "",
                    site.elevation?.toString() ?: "",
                    site.siteType,
                    site.period,
                    site.condition,
                    formatDate(site.discoveredDate),
                    site.status.name,
                    site.notes
                )
            }.toTypedArray()
        ))
        files.add(siteFile)

        // Export trenches
        val trenchFile = File(exportDir, "trenches.csv")
        writeCSV(trenchFile, listOf(
            listOf("ID", "Trench Code", "Name", "Length (m)", "Width (m)", "Depth (m)", "Orientation", "Grid Reference", "Description", "Opened Date", "Closed Date", "Status", "Notes"),
            *trenches.map { trench ->
                listOf(
                    trench.id.toString(),
                    trench.trenchCode,
                    trench.name,
                    trench.length?.toString() ?: "",
                    trench.width?.toString() ?: "",
                    trench.depth?.toString() ?: "",
                    trench.orientation,
                    trench.gridReference,
                    trench.description,
                    formatDate(trench.openedDate),
                    trench.closedDate?.let { formatDate(it) } ?: "",
                    trench.status.name,
                    trench.notes
                )
            }.toTypedArray()
        ))
        files.add(trenchFile)

        // Export form responses
        val formFile = File(exportDir, "form_responses.csv")
        val allFieldNames = mutableSetOf<String>()
        val parsedResponses = responses.map { response ->
            val data = parseResponseData(response.dataJson)
            allFieldNames.addAll(data.keys)
            response to data
        }

        val headers = listOf("ID", "Form Name", "Form Type", "Status", "Created", "Updated", "Submitted") + allFieldNames.sorted()
        val rows = parsedResponses.map { (response, data) ->
            listOf(
                response.id.toString(),
                response.formName,
                response.formType.name,
                response.status.name,
                formatDateTime(response.createdAt),
                formatDateTime(response.updatedAt),
                response.submittedAt?.let { formatDateTime(it) } ?: ""
            ) + allFieldNames.sorted().map { field -> data[field] ?: "" }
        }

        writeCSV(formFile, listOf(headers) + rows)
        files.add(formFile)

        // Export summary
        val summaryFile = File(exportDir, "summary.csv")
        writeCSV(summaryFile, listOf(
            listOf("Project", project.name),
            listOf("Location", project.location),
            listOf("Director", project.director),
            listOf("Institution", project.institution),
            listOf("Standard", project.standardType.name),
            listOf("Start Date", formatDate(project.startDate)),
            listOf(""),
            listOf("Metric", "Count"),
            listOf("Surveys", surveys.size.toString()),
            listOf("Sites", sites.size.toString()),
            listOf("Trenches", trenches.size.toString()),
            listOf("Form Responses", responses.size.toString()),
            listOf("Completed Forms", responses.count { it.status != DataStatus.DRAFT }.toString())
        ))
        files.add(summaryFile)

        files
    }

    /**
     * Export all project data to a single Excel file
     */
    suspend fun exportProjectToExcel(
        context: Context,
        project: Project,
        surveys: List<Survey>,
        sites: List<Site>,
        trenches: List<Trench>,
        responses: List<FormResponse>
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "StrataField/${sanitizeFileName(project.name)}"
        )
        exportDir.mkdirs()

        val excelFile = File(exportDir, "${sanitizeFileName(project.name)}_data.xlsx")

        try {
            val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook()

            // Summary sheet
            val summarySheet = workbook.createSheet("Summary")
            val summaryData = listOf(
                listOf("Project", project.name),
                listOf("Location", project.location),
                listOf("Director", project.director),
                listOf("Institution", project.institution),
                listOf("Standard", project.standardType.name),
                listOf("Start Date", formatDate(project.startDate)),
                listOf(""),
                listOf("Metric", "Count"),
                listOf("Surveys", surveys.size.toString()),
                listOf("Sites", sites.size.toString()),
                listOf("Trenches", trenches.size.toString()),
                listOf("Form Responses", responses.size.toString()),
                listOf("Completed Forms", responses.count { it.status != DataStatus.DRAFT }.toString())
            )
            writeExcelSheet(workbook, summarySheet, summaryData)

            // Surveys sheet
            val surveySheet = workbook.createSheet("Surveys")
            writeExcelSheet(workbook, surveySheet, listOf(
                listOf("ID", "Name", "Description", "Survey Type", "Grid Size", "Transect Spacing", "Start Date", "End Date", "Status", "Notes"),
                *surveys.map { survey ->
                    listOf(
                        survey.id.toString(),
                        survey.name,
                        survey.description,
                        survey.surveyType,
                        survey.gridSize,
                        survey.transectSpacing,
                        formatDate(survey.startDate),
                        survey.endDate?.let { formatDate(it) } ?: "",
                        survey.status.name,
                        survey.notes
                    )
                }.toTypedArray()
            ))

            // Sites sheet
            val siteSheet = workbook.createSheet("Sites")
            writeExcelSheet(workbook, siteSheet, listOf(
                listOf("ID", "Site Code", "Name", "Description", "Latitude", "Longitude", "Elevation", "Site Type", "Period", "Condition", "Discovered Date", "Status", "Notes"),
                *sites.map { site ->
                    listOf(
                        site.id.toString(),
                        site.siteCode,
                        site.name,
                        site.description,
                        site.latitude?.toString() ?: "",
                        site.longitude?.toString() ?: "",
                        site.elevation?.toString() ?: "",
                        site.siteType,
                        site.period,
                        site.condition,
                        formatDate(site.discoveredDate),
                        site.status.name,
                        site.notes
                    )
                }.toTypedArray()
            ))

            // Trenches sheet
            val trenchSheet = workbook.createSheet("Trenches")
            writeExcelSheet(workbook, trenchSheet, listOf(
                listOf("ID", "Trench Code", "Name", "Length (m)", "Width (m)", "Depth (m)", "Orientation", "Grid Reference", "Description", "Opened Date", "Closed Date", "Status", "Notes"),
                *trenches.map { trench ->
                    listOf(
                        trench.id.toString(),
                        trench.trenchCode,
                        trench.name,
                        trench.length?.toString() ?: "",
                        trench.width?.toString() ?: "",
                        trench.depth?.toString() ?: "",
                        trench.orientation,
                        trench.gridReference,
                        trench.description,
                        formatDate(trench.openedDate),
                        trench.closedDate?.let { formatDate(it) } ?: "",
                        trench.status.name,
                        trench.notes
                    )
                }.toTypedArray()
            ))

            // Form Responses sheet
            val formSheet = workbook.createSheet("Form Responses")
            val allFieldNames = mutableSetOf<String>()
            val parsedResponses = responses.map { response ->
                val data = parseResponseData(response.dataJson)
                allFieldNames.addAll(data.keys)
                response to data
            }

            val headers = listOf("ID", "Form Name", "Form Type", "Status", "Created", "Updated", "Submitted") + allFieldNames.sorted()
            val rows = parsedResponses.map { (response, data) ->
                listOf(
                    response.id.toString(),
                    response.formName,
                    response.formType.name,
                    response.status.name,
                    formatDateTime(response.createdAt),
                    formatDateTime(response.updatedAt),
                    response.submittedAt?.let { formatDateTime(it) } ?: ""
                ) + allFieldNames.sorted().map { field -> data[field] ?: "" }
            }
            writeExcelSheet(workbook, formSheet, listOf(headers) + rows)

            // Write to file
            FileOutputStream(excelFile).use { output ->
                workbook.write(output)
            }
            workbook.close()
        } catch (e: Exception) {
            // Fallback to CSV if Excel fails
            val csvFile = File(exportDir, "${sanitizeFileName(project.name)}_data.csv")
            val allData = mutableListOf<List<String>>()
            allData.add(listOf("Project", project.name))
            allData.add(listOf("Location", project.location))
            allData.add(listOf("Director", project.director))
            allData.add(listOf("Institution", project.institution))
            allData.add(listOf("Standard", project.standardType.name))
            allData.add(listOf(""))
            allData.add(listOf("Surveys", surveys.size.toString()))
            allData.add(listOf("Sites", sites.size.toString()))
            allData.add(listOf("Trenches", trenches.size.toString()))
            allData.add(listOf("Form Responses", responses.size.toString()))
            writeCSV(csvFile, allData)
            return@withContext csvFile
        }

        excelFile
    }

    private fun writeCSV(file: File, rows: List<List<String>>) {
        FileWriter(file).use { writer ->
            rows.forEach { row ->
                writer.append(row.joinToString(",") { escapeCSV(it) })
                writer.append("\n")
            }
        }
    }

    private fun escapeCSV(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun writeExcelSheet(
        workbook: org.apache.poi.xssf.usermodel.XSSFWorkbook,
        sheet: org.apache.poi.xssf.usermodel.XSSFSheet,
        rows: List<List<String>>
    ) {
        val headerStyle = workbook.createCellStyle().apply {
            setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.BLACK.index)
            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
        }
        val headerFont = workbook.createFont().apply {
            color = org.apache.poi.ss.usermodel.IndexedColors.WHITE.index
            bold = true
        }
        headerStyle.setFont(headerFont)

        rows.forEachIndexed { rowIndex, row ->
            val excelRow = sheet.createRow(rowIndex)
            row.forEachIndexed { colIndex, value ->
                val cell = excelRow.createCell(colIndex)
                cell.setCellValue(value)
                if (rowIndex == 0) {
                    cell.cellStyle = headerStyle
                }
            }
        }

        // Auto-size columns
        if (rows.isNotEmpty()) {
            for (i in 0 until rows[0].size) {
                sheet.autoSizeColumn(i)
            }
        }
    }

    private fun parseResponseData(dataJson: String): Map<String, String> {
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(dataJson, type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9-_ ]"), "_").trim()
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}