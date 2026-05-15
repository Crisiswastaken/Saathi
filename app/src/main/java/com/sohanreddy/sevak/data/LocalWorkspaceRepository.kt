package com.sohanreddy.sevak.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class SaathiDocument(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String,
    val aiEnabled: Boolean,
    val aiIndexPath: String,
    val createdAt: String,
    val updatedAt: String
)

data class VitalScan(
    val id: String,
    val mode: String,
    val heartRate: Int?,
    val spo2: Int?,
    val hrvRmssd: Double?,
    val hrvSdnn: Double?,
    val stressIndex: Int?,
    val signalQuality: Int,
    val durationSec: Int,
    val measuredAt: String,
    val timestampMs: Long
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
    private val documentsIndexDir = File(context.filesDir, "saathi_documents_index").apply { mkdirs() }

    init {
        runCatching { PDFBoxResourceLoader.init(context) }
    }

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
            aiEnabled = true,
            aiIndexPath = buildDocumentIndex(id, mimeType, target),
            createdAt = now,
            updatedAt = now
        )
        saveDocuments(getDocuments() + document)
        return document
    }

    fun setDocumentAiEnabled(id: String, enabled: Boolean) {
        saveDocuments(
            getDocuments().map { document ->
                if (document.id == id) document.copy(aiEnabled = enabled, updatedAt = nowIso()) else document
            }
        )
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
        current.firstOrNull { it.id == id }?.let { document ->
            runCatching { File(document.localPath).delete() }
            if (document.aiIndexPath.isNotBlank()) {
                runCatching { File(document.aiIndexPath).delete() }
            }
        }
        saveDocuments(current.filterNot { it.id == id })
    }

    fun getVitalScans(): List<VitalScan> {
        return prefs.getString(KEY_VITAL_SCANS, null)
            ?.let(::decodeVitalScans)
            ?: emptyList()
    }

    fun saveVitalScan(scan: VitalScan) {
        val deduped = getVitalScans()
            .filterNot { it.id == scan.id }
            .plus(scan)
            .sortedByDescending { it.timestampMs }
            .take(MAX_STORED_VITAL_SCANS)
        saveVitalScans(deduped)
    }

    fun getLatestVitalScan(): VitalScan? = getVitalScans().maxByOrNull { it.timestampMs }

    fun formatLatestVitalsForReport(): String {
        val latest = getLatestVitalScan() ?: return "No heart rate or SpO2 scans captured yet."
        val segments = mutableListOf<String>()
        latest.heartRate?.let { segments += "Heart rate: $it bpm" }
        latest.spo2?.let { segments += "SpO2: $it%" }
        latest.hrvRmssd?.let { segments += "HRV RMSSD: ${"%.1f".format(it)} ms" }
        latest.hrvSdnn?.let { segments += "HRV SDNN: ${"%.1f".format(it)} ms" }
        latest.stressIndex?.let { segments += "Stress index: $it" }
        segments += "Signal quality: ${latest.signalQuality}/100"
        segments += "Duration: ${latest.durationSec}s"
        segments += "Measured at: ${formatIsoDate(latest.measuredAt)}"
        return segments.joinToString(" | ")
    }

    fun buildEnabledDocumentContext(
        maxDocuments: Int = 4,
        maxCharsPerDocument: Int = 1600,
        totalMaxChars: Int = 5000
    ): String {
        val enabled = getDocuments().filter { it.aiEnabled }.take(maxDocuments)
        if (enabled.isEmpty()) return ""

        val sections = mutableListOf<String>()
        var consumed = 0
        enabled.forEach { document ->
            val text = getDocumentExtractForAi(document, maxCharsPerDocument)
            if (text.isBlank()) return@forEach
            val section = "Document: ${document.name}\n$text"
            if (consumed + section.length > totalMaxChars) return@forEach
            sections += section
            consumed += section.length
        }
        return sections.joinToString("\n\n")
    }

    fun buildMedicalHistoryFromUploadedDocuments(
        maxDocuments: Int = 5,
        maxCharsPerDocument: Int = 500
    ): String {
        val uploaded = getDocuments().take(maxDocuments)
        if (uploaded.isEmpty()) return "No uploaded medical documents available yet."

        return uploaded.joinToString("\n\n") { document ->
            val excerpt = getDocumentExtractForAi(document, maxCharsPerDocument)
            val body = if (excerpt.isBlank()) {
                "No text could be extracted from this file yet."
            } else {
                excerpt
            }
            "${document.name}: $body"
        }
    }

    fun buildMedicalHistoryFromEnabledDocuments(
        maxDocuments: Int = 5,
        maxCharsPerDocument: Int = 500
    ): String = buildMedicalHistoryFromUploadedDocuments(maxDocuments, maxCharsPerDocument)

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
            vitals = formatLatestVitalsForReport(),
            medicalHistory = buildMedicalHistoryFromUploadedDocuments(),
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

    private fun saveVitalScans(scans: List<VitalScan>) {
        prefs.edit().putString(KEY_VITAL_SCANS, encodeVitalScans(scans).toString()).apply()
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
                    .put("aiEnabled", document.aiEnabled)
                    .put("aiIndexPath", document.aiIndexPath)
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
                aiEnabled = item.optBoolean("aiEnabled", true),
                aiIndexPath = item.optString("aiIndexPath", ""),
                createdAt = item.optString("createdAt"),
                updatedAt = item.optString("updatedAt")
            )
        }
    }

    private fun encodeVitalScans(scans: List<VitalScan>) = JSONArray().apply {
        scans.forEach { scan ->
            put(
                JSONObject()
                    .put("id", scan.id)
                    .put("mode", scan.mode)
                    .put("heartRate", scan.heartRate)
                    .put("spo2", scan.spo2)
                    .put("hrvRmssd", scan.hrvRmssd)
                    .put("hrvSdnn", scan.hrvSdnn)
                    .put("stressIndex", scan.stressIndex)
                    .put("signalQuality", scan.signalQuality)
                    .put("durationSec", scan.durationSec)
                    .put("measuredAt", scan.measuredAt)
                    .put("timestampMs", scan.timestampMs)
            )
        }
    }

    private fun decodeVitalScans(raw: String): List<VitalScan> {
        val array = JSONArray(raw)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            VitalScan(
                id = item.optString("id", UUID.randomUUID().toString()),
                mode = item.optString("mode"),
                heartRate = item.optNullableInt("heartRate"),
                spo2 = item.optNullableInt("spo2"),
                hrvRmssd = item.optNullableDouble("hrvRmssd"),
                hrvSdnn = item.optNullableDouble("hrvSdnn"),
                stressIndex = item.optNullableInt("stressIndex"),
                signalQuality = item.optInt("signalQuality", 0),
                durationSec = item.optInt("durationSec", 0),
                measuredAt = item.optString("measuredAt", nowIso()),
                timestampMs = item.optLong("timestampMs", Instant.now().toEpochMilli())
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

    private fun getDocumentExtractForAi(document: SaathiDocument, maxChars: Int): String {
        val raw = ensureIndexAndRead(document)
        return raw.replace(Regex("\\s+"), " ").trim().take(maxChars)
    }

    private fun ensureIndexAndRead(document: SaathiDocument): String {
        if (document.aiIndexPath.isNotBlank()) {
            val cached = runCatching { File(document.aiIndexPath).readText() }.getOrNull()
            if (!cached.isNullOrBlank()) return cached
        }

        val source = File(document.localPath)
        if (!source.exists()) return ""

        val extracted = extractDocumentText(document.mimeType, source)
        if (extracted.isBlank()) return ""

        val newIndexPath = writeIndexFile(document.id, extracted)
        if (newIndexPath.isNotBlank()) {
            saveDocuments(
                getDocuments().map {
                    if (it.id == document.id) it.copy(aiIndexPath = newIndexPath, updatedAt = nowIso()) else it
                }
            )
        }
        return extracted
    }

    private fun buildDocumentIndex(id: String, mimeType: String, source: File): String {
        val extracted = extractDocumentText(mimeType, source)
        if (extracted.isBlank()) return ""
        return writeIndexFile(id, extracted)
    }

    private fun writeIndexFile(id: String, text: String): String {
        return runCatching {
            val file = File(documentsIndexDir, "$id.txt")
            file.writeText(text.take(MAX_DOC_INDEX_CHARS))
            file.absolutePath
        }.getOrDefault("")
    }

    private fun extractDocumentText(mimeType: String, source: File): String {
        return when {
            mimeType.startsWith("text/") || source.extension.lowercase() in SUPPORTED_TEXT_EXTENSIONS -> readTextSafely(source)
            mimeType == "application/pdf" || source.extension.equals("pdf", ignoreCase = true) -> extractPdfText(source)
            else -> ""
        }
    }

    private fun extractPdfText(source: File): String {
        return runCatching {
            PDDocument.load(source).use { document ->
                PDFTextStripper().getText(document)
            }
        }.getOrDefault("")
    }

    private fun readTextSafely(source: File): String {
        return runCatching { source.readText(Charset.forName("UTF-8")) }
            .recoverCatching { source.readText() }
            .getOrDefault("")
    }

    private fun formatIsoDate(raw: String): String {
        return runCatching {
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(Instant.parse(raw))
        }.getOrDefault(raw)
    }

    private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key)) null else optInt(key)
    private fun JSONObject.optNullableDouble(key: String): Double? = if (isNull(key)) null else optDouble(key)

    companion object {
        private const val KEY_DOCUMENTS = "documents"
        private const val KEY_REPORTS = "reports"
        private const val KEY_VITAL_SCANS = "vital_scans"
        private const val MAX_DOC_INDEX_CHARS = 32_000
        private const val MAX_STORED_VITAL_SCANS = 60
        private val SUPPORTED_TEXT_EXTENSIONS = setOf("txt", "md", "csv", "json", "xml", "log")

        fun nowIso(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
    }
}
