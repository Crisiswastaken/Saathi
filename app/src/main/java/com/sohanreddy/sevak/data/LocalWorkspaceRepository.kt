package com.sohanreddy.sevak.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

data class SaathiDocument(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String,
    val createdAt: String,
    val updatedAt: String
)

data class HealthReport(
    val id: String,
    val name: String,
    val templateName: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val patientSummary: String,
    val symptoms: String,
    val medications: String,
    val allergies: String,
    val vitals: String,
    val medicalHistory: String,
    val lifestyleNotes: String,
    val doctorNotes: String,
    val recommendations: String,
    val followUpPlan: String
)

class LocalWorkspaceRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("saathi_local_workspace", Context.MODE_PRIVATE)
    private val documentsDir = File(context.filesDir, "saathi_documents").apply { mkdirs() }

    fun getDocuments(): List<SaathiDocument> {
        return prefs.getString(KEY_DOCUMENTS, null)
            ?.let(::decodeDocuments)
            ?: emptyList()
    }

    fun importDocument(uri: Uri): SaathiDocument? {
        val resolver = context.contentResolver
        val sourceName = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        } ?: "Health document"
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val id = UUID.randomUUID().toString()
        val safeName = sourceName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(documentsDir, "$id-$safeName")

        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null

        val now = nowIso()
        val document = SaathiDocument(
            id = id,
            name = sourceName,
            mimeType = mimeType,
            sizeBytes = target.length(),
            localPath = target.absolutePath,
            createdAt = now,
            updatedAt = now
        )
        saveDocuments(getDocuments() + document)
        return document
    }

    fun renameDocument(id: String, name: String) {
        saveDocuments(
            getDocuments().map { document ->
                if (document.id == id) document.copy(name = name, updatedAt = nowIso()) else document
            }
        )
    }

    fun deleteDocument(id: String) {
        val current = getDocuments()
        current.firstOrNull { it.id == id }?.localPath?.let { path ->
            runCatching { File(path).delete() }
        }
        saveDocuments(current.filterNot { it.id == id })
    }

    fun getReports(): List<HealthReport> {
        val stored = prefs.getString(KEY_REPORTS, null)
        if (stored == null) {
            val reports = demoReports()
            saveReports(reports)
            return reports
        }
        return decodeReports(stored)
    }

    fun getReport(id: String): HealthReport? = getReports().firstOrNull { it.id == id }

    fun upsertReport(report: HealthReport) {
        val reports = getReports()
        val exists = reports.any { it.id == report.id }
        val next = if (exists) {
            reports.map { if (it.id == report.id) report.copy(updatedAt = nowIso()) else it }
        } else {
            reports + report
        }
        saveReports(next)
    }

    fun deleteReport(id: String) {
        saveReports(getReports().filterNot { it.id == id })
    }

    fun newBlankReport(): HealthReport {
        val now = nowIso()
        return HealthReport(
            id = UUID.randomUUID().toString(),
            name = "Untitled health report",
            templateName = "General health summary",
            status = "Draft",
            createdAt = now,
            updatedAt = now,
            patientSummary = "",
            symptoms = "",
            medications = "",
            allergies = "",
            vitals = "",
            medicalHistory = "",
            lifestyleNotes = "",
            doctorNotes = "",
            recommendations = "",
            followUpPlan = ""
        )
    }

    private fun saveDocuments(documents: List<SaathiDocument>) {
        prefs.edit().putString(KEY_DOCUMENTS, encodeDocuments(documents).toString()).apply()
    }

    private fun saveReports(reports: List<HealthReport>) {
        prefs.edit().putString(KEY_REPORTS, encodeReports(reports).toString()).apply()
    }

    private fun demoReports(): List<HealthReport> {
        val now = nowIso()
        return listOf(
            HealthReport(
                id = UUID.randomUUID().toString(),
                name = "Weekly wellness summary",
                templateName = "General health summary",
                status = "Ready",
                createdAt = now,
                updatedAt = now,
                patientSummary = "Patient reports mild fatigue after long workdays, otherwise stable.",
                symptoms = "Occasional headache, disturbed sleep, mild body ache.",
                medications = "No regular medication recorded.",
                allergies = "No known allergies recorded.",
                vitals = "Blood pressure and temperature not captured yet.",
                medicalHistory = "No chronic condition recorded in this report.",
                lifestyleNotes = "Sleep is irregular. Water intake appears low.",
                doctorNotes = "Monitor fatigue pattern and hydration.",
                recommendations = "Increase fluids, keep a simple sleep log, seek care if headache worsens.",
                followUpPlan = "Review symptoms in 7 days."
            ),
            HealthReport(
                id = UUID.randomUUID().toString(),
                name = "Fever conversation notes",
                templateName = "General health summary",
                status = "Draft",
                createdAt = now,
                updatedAt = now,
                patientSummary = "Short fever-related intake generated as a future report placeholder.",
                symptoms = "Fever, chills, reduced appetite.",
                medications = "Paracetamol mentioned, dose not verified.",
                allergies = "Not discussed.",
                vitals = "Temperature not entered.",
                medicalHistory = "Not discussed.",
                lifestyleNotes = "Rest and food intake reduced.",
                doctorNotes = "Needs confirmation before sharing with a clinician.",
                recommendations = "Hydration, rest, and medical review if symptoms persist.",
                followUpPlan = "Check fever trend tomorrow."
            )
        )
    }

    private fun encodeDocuments(documents: List<SaathiDocument>) = JSONArray().apply {
        documents.forEach { document ->
            put(
                JSONObject()
                    .put("id", document.id)
                    .put("name", document.name)
                    .put("mimeType", document.mimeType)
                    .put("sizeBytes", document.sizeBytes)
                    .put("localPath", document.localPath)
                    .put("createdAt", document.createdAt)
                    .put("updatedAt", document.updatedAt)
            )
        }
    }

    private fun decodeDocuments(raw: String): List<SaathiDocument> {
        val array = JSONArray(raw)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            SaathiDocument(
                id = item.getString("id"),
                name = item.getString("name"),
                mimeType = item.optString("mimeType", "application/octet-stream"),
                sizeBytes = item.optLong("sizeBytes"),
                localPath = item.optString("localPath"),
                createdAt = item.optString("createdAt"),
                updatedAt = item.optString("updatedAt")
            )
        }
    }

    private fun encodeReports(reports: List<HealthReport>) = JSONArray().apply {
        reports.forEach { report ->
            put(
                JSONObject()
                    .put("id", report.id)
                    .put("name", report.name)
                    .put("templateName", report.templateName)
                    .put("status", report.status)
                    .put("createdAt", report.createdAt)
                    .put("updatedAt", report.updatedAt)
                    .put("patientSummary", report.patientSummary)
                    .put("symptoms", report.symptoms)
                    .put("medications", report.medications)
                    .put("allergies", report.allergies)
                    .put("vitals", report.vitals)
                    .put("medicalHistory", report.medicalHistory)
                    .put("lifestyleNotes", report.lifestyleNotes)
                    .put("doctorNotes", report.doctorNotes)
                    .put("recommendations", report.recommendations)
                    .put("followUpPlan", report.followUpPlan)
            )
        }
    }

    private fun decodeReports(raw: String): List<HealthReport> {
        val array = JSONArray(raw)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            HealthReport(
                id = item.getString("id"),
                name = item.optString("name"),
                templateName = item.optString("templateName", "General health summary"),
                status = item.optString("status", "Draft"),
                createdAt = item.optString("createdAt"),
                updatedAt = item.optString("updatedAt"),
                patientSummary = item.optString("patientSummary"),
                symptoms = item.optString("symptoms"),
                medications = item.optString("medications"),
                allergies = item.optString("allergies"),
                vitals = item.optString("vitals"),
                medicalHistory = item.optString("medicalHistory"),
                lifestyleNotes = item.optString("lifestyleNotes"),
                doctorNotes = item.optString("doctorNotes"),
                recommendations = item.optString("recommendations"),
                followUpPlan = item.optString("followUpPlan")
            )
        }
    }

    companion object {
        private const val KEY_DOCUMENTS = "documents"
        private const val KEY_REPORTS = "reports"

        fun nowIso(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
    }
}
