package com.sohanreddy.sevak.symptom

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.sohanreddy.sevak.data.LocalWorkspaceRepository
import com.sohanreddy.sevak.map.DiseaseReport
import com.sohanreddy.sevak.map.FirestoreMapRepository
import com.sohanreddy.sevak.network.GroqApiService
import com.sohanreddy.sevak.network.GroqRequest
import com.sohanreddy.sevak.network.GroqRequestMessage
import com.sohanreddy.sevak.network.GroqResponseMessage
import com.sohanreddy.sevak.network.GroqTool
import com.sohanreddy.sevak.network.GroqToolFunction
import com.sohanreddy.sevak.network.groqTextMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// ── State machine ────────────────────────────────────────────────────────────
sealed class SymptomState {
    object Idle : SymptomState()
    object Collecting : SymptomState()
    object Predicting : SymptomState()
    object Announcing : SymptomState()
    object AwaitingConfirm : SymptomState()
    object Submitting : SymptomState()
    object Done : SymptomState()
    data class Error(val message: String) : SymptomState()
}

// ── ViewModel ────────────────────────────────────────────────────────────────
class SymptomViewModel(
    private val groqApiService: GroqApiService,
    private val firestoreRepo: FirestoreMapRepository,
    private val groqApiKey: String,
    private val localWorkspaceRepository: LocalWorkspaceRepository
) : ViewModel() {

    data class ReportExtraction(
        val patientSummary: String,
        val symptoms: String,
        val medications: String,
        val allergies: String,
        val lifestyleNotes: String,
        val recommendations: String,
        val medicalHistory: String
    )

    private val _state = MutableStateFlow<SymptomState>(SymptomState.Idle)
    val state: StateFlow<SymptomState> = _state.asStateFlow()

    /** Text events that MainViewModel should feed into Sarvam TTS immediately. */
    private val _ttsOutput = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val ttsOutput: SharedFlow<String> = _ttsOutput

    /** Becomes true once the user confirms and the Firestore write is done. */
    private val _navigateToMap = MutableStateFlow(false)
    val navigateToMap: StateFlow<Boolean> = _navigateToMap.asStateFlow()

    private var currentSession: SymptomSession? = null
    private var currentJob: Job? = null

    // Called from MainActivity after the navigation is triggered
    fun resetNavigation() { _navigateToMap.value = false }

    // ── Public API called by MainViewModel ────────────────────────────────────

    fun startSession(initialUserSpeech: String, language: String, lat: Double, lng: Double) {
        if (_state.value !is SymptomState.Idle) return
        val session = SymptomSession(language = language, lat = lat, lng = lng)
            .addTurn("user", initialUserSpeech)
        currentSession = session
        _state.value = SymptomState.Collecting
        launchInitialTurn(session)
    }

    fun onUserSpoke(text: String) {
        val session = currentSession ?: return
        when (_state.value) {
            is SymptomState.Collecting -> {
                val updated = session.addTurn("user", text)
                currentSession = updated
                launchInitialTurn(updated)
            }
            else -> Log.d(TAG, "onUserSpoke ignored in state ${_state.value}")
        }
    }

    // ── Internal coroutine launchers ──────────────────────────────────────────

    private fun launchInitialTurn(session: SymptomSession) {
        currentJob?.cancel()
        currentJob = viewModelScope.launch { runInitialTurn(session) }
    }

    // ── Follow-up questioning loop ────────────────────────────────────────────

    private suspend fun runInitialTurn(session: SymptomSession) {
        try {
            val directPrediction = detectDirectDiseaseReport(session)
            if (directPrediction != null) {
                val updated = session.copy(
                    collectedSymptoms = directPrediction.symptoms.ifEmpty {
                        listOf("known disease reported by user")
                    },
                    prediction = directPrediction
                )
                currentSession = updated
                submitAndAnnounce(updated, directPrediction)
            } else {
                runFollowUpTurn(session)
            }
        } catch (e: Exception) {
            Log.e(TAG, "runInitialTurn: ${e.message}", e)
            runFollowUpTurn(session)
        }
    }

    private suspend fun runFollowUpTurn(session: SymptomSession) {
        try {
            val messages = buildFollowUpMessages(session)
            val raw = callGroq(messages)
            val cleaned = stripMarkdownJson(raw)

            val collectedSymptoms = tryParseReadyToPredict(cleaned)
            if (collectedSymptoms != null) {
                // LLM has enough info — move to prediction
                val updated = session.copy(collectedSymptoms = collectedSymptoms)
                currentSession = updated
                runPredictionFlow(updated)
            } else {
                if (looksLikeJson(cleaned)) {
                    Log.w(TAG, "Unexpected JSON from follow-up turn; moving to prediction without speaking it")
                    val fallbackSymptoms = session.turns
                        .filter { it.role == "user" }
                        .map { it.content }
                        .takeLast(5)
                    val updated = session.copy(collectedSymptoms = fallbackSymptoms)
                    currentSession = updated
                    runPredictionFlow(updated)
                    return
                }
                // LLM is asking another follow-up question
                val updated = session.addTurn("assistant", raw.trim())
                currentSession = updated
                emitSpeech(raw.trim())
            }
        } catch (e: Exception) {
            Log.e(TAG, "runFollowUpTurn: ${e.message}", e)
            val msg = "Sorry, something went wrong. Please try again."
            _state.value = SymptomState.Error(msg)
            emitSpeech(msg)
        }
    }

    // ── Prediction → Announce → Confirm ──────────────────────────────────────

    private suspend fun runPredictionFlow(session: SymptomSession) {
        _state.value = SymptomState.Predicting
        try {
            // 1. Get structured prediction JSON
            val symptomSummary = session.collectedSymptoms.joinToString(", ")
            val predRaw = callGroq(listOf(
                groqTextMessage("system", SymptomPrompts.predictionSystemPrompt()),
                groqTextMessage("user", "Patient symptoms: $symptomSummary")
            ))
            val prediction = parsePrediction(predRaw)
            if (prediction == null) {
                val msg = "I could not fully assess your symptoms. Please visit a nearby doctor."
                _state.value = SymptomState.Error(msg)
                emitSpeech(msg)
                return
            }
            val updatedSession = session.copy(prediction = prediction)
            currentSession = updatedSession

            // 2. Generate announcement text
            _state.value = SymptomState.Announcing
            val announcementText = callGroq(listOf(
                groqTextMessage("system", SymptomPrompts.predictionAnnouncementPrompt(prediction, session.language)),
                groqTextMessage("user", "Announce the result now.")
            ))

            val saved = submitReport(updatedSession, prediction)
            val storageText = if (saved.isSuccess) {
                _navigateToMap.value = true
                "I have also added this anonymously to the community health map."
            } else {
                Log.e(TAG, "submitReport failed: ${saved.exceptionOrNull()?.message}")
                "I could not update the community health map right now."
            }

            emitSpeech("$announcementText $storageText")
            generateAndStoreHealthReport(updatedSession, prediction)
            finishSession()

        } catch (e: Exception) {
            Log.e(TAG, "runPredictionFlow: ${e.message}", e)
            val msg = "Assessment failed. Please try again."
            _state.value = SymptomState.Error(msg)
            emitSpeech(msg)
        }
    }

    // ── Confirmation handler ──────────────────────────────────────────────────

    private suspend fun submitAndAnnounce(session: SymptomSession, prediction: DiseasePrediction) {
        _state.value = SymptomState.Announcing
        val announcementText = callGroq(listOf(
            groqTextMessage("system", SymptomPrompts.predictionAnnouncementPrompt(prediction, session.language)),
            groqTextMessage("user", "Announce the result now.")
        ))
        val saved = submitReport(session, prediction)
        val storageText = if (saved.isSuccess) {
            _navigateToMap.value = true
            "I have updated this anonymously on the community health map."
        } else {
            Log.e(TAG, "submitReport failed: ${saved.exceptionOrNull()?.message}")
            "I could not update the community health map right now."
        }
        emitSpeech("$announcementText $storageText")
        generateAndStoreHealthReport(session, prediction)
        finishSession()
    }

    private suspend fun submitReport(session: SymptomSession, prediction: DiseasePrediction): Result<Unit> {
        _state.value = SymptomState.Submitting
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        val report = DiseaseReport(
            lat = session.lat,
            lng = session.lng,
            disease = prediction.disease,
            accuracy = prediction.accuracy.coerceIn(0, 100),
            symptoms = prediction.symptoms,
            reasoning = prediction.reasoning,
            category = prediction.category.name,
            language = session.language,
            userId = uid,
            timestamp = System.currentTimeMillis(),
            radius = 1000
        )
        return firestoreRepo.submitReport(report)
    }

    private fun finishSession() {
        _state.value = SymptomState.Done
        currentSession = null
        _state.value = SymptomState.Idle
    }

    private suspend fun handleConfirmation(text: String, session: SymptomSession) {
        if (isAffirmative(text) && session.prediction != null) {
            _state.value = SymptomState.Submitting
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
            val prediction = session.prediction
            val report = DiseaseReport(
                lat = session.lat,
                lng = session.lng,
                disease = prediction.disease,
                accuracy = prediction.accuracy,
                symptoms = prediction.symptoms,
                reasoning = prediction.reasoning,
                category = prediction.category.name,
                language = session.language,
                userId = uid,
                timestamp = System.currentTimeMillis()
            )
            val result = firestoreRepo.submitReport(report)
            if (result.isFailure) {
                Log.e(TAG, "submitReport failed: ${result.exceptionOrNull()?.message}")
                emitSpeech("Sorry, could not save your report. Please try again later.")
            } else {
                Log.d(TAG, "Report submitted successfully")
                emitSpeech("Your report has been added to the community health map. Thank you for helping your community stay safe!")
                _navigateToMap.value = true
            }
            // Reset to idle so regular conversation resumes
            _state.value = SymptomState.Done
            currentSession = null
            // Auto-reset to idle after a short delay
            _state.value = SymptomState.Idle
        } else {
            // User declined — acknowledge and finish
            emitSpeech("No problem. Take care and please visit a doctor if you feel worse.")
            _state.value = SymptomState.Done
            currentSession = null
            _state.value = SymptomState.Idle
        }
    }

    // ── Groq helpers ──────────────────────────────────────────────────────────

    private fun buildFollowUpMessages(session: SymptomSession): List<GroqRequestMessage> {
        val msgs = mutableListOf(
            groqTextMessage("system", SymptomPrompts.followUpSystemPrompt(session.language))
        )
        val documentContext = enabledDocumentContextForConversation()
        if (documentContext.isNotBlank()) {
            msgs.add(
                groqTextMessage(
                    "system",
                    "User-selected uploaded medical document context:\n$documentContext"
                )
            )
        }
        session.turns.forEach { msgs.add(groqTextMessage(it.role, it.content)) }
        return msgs
    }

    private suspend fun detectDirectDiseaseReport(session: SymptomSession): DiseasePrediction? {
        val latestUserText = session.turns.lastOrNull { it.role == "user" }?.content ?: return null
        val messages = mutableListOf(
            groqTextMessage("system", SymptomPrompts.directDiseaseReportPrompt(session.language))
        )
        val documentContext = enabledDocumentContextForConversation()
        if (documentContext.isNotBlank()) {
            messages += groqTextMessage(
                "system",
                "User-selected uploaded medical document context:\n$documentContext"
            )
        }
        messages += groqTextMessage("user", latestUserText)
        val raw = callGroq(messages)
        return parseDirectDiseaseReport(raw)
    }

    private fun enabledDocumentContextForConversation(): String {
        return localWorkspaceRepository.buildEnabledDocumentContext(
            maxDocuments = 3,
            maxCharsPerDocument = 900,
            totalMaxChars = 2600
        )
    }

    private suspend fun emitSpeech(text: String) {
        if (text.isNotBlank() && !looksLikeJson(stripMarkdownJson(text))) {
            _ttsOutput.emit(text.trim())
        }
    }

    private suspend fun callGroq(messages: List<GroqRequestMessage>): String {
        val request = GroqRequest(
            model = "llama-3.3-70b-versatile",
            messages = messages,
            temperature = 0.3,
            max_tokens = 400
        )
        return callGroqMessage(request)?.content?.trim() ?: ""
    }

    private suspend fun callGroqMessage(request: GroqRequest): GroqResponseMessage? {
        return groqApiService.chat("Bearer $groqApiKey", request)
            .choices.firstOrNull()
            ?.message
    }

    private suspend fun generateAndStoreHealthReport(session: SymptomSession, prediction: DiseasePrediction) {
        if (!hasEnoughConversationForReport(session)) {
            Log.d(TAG, "Skipping report generation; not enough conversation data")
            return
        }

        runCatching {
            val extraction = requestStructuredReportExtraction(session, prediction)
            val docHistory = localWorkspaceRepository.buildMedicalHistoryFromUploadedDocuments()
            val mergedMedicalHistory = listOf(
                extraction?.medicalHistory.orEmpty(),
                docHistory
            ).filter { it.isNotBlank() }.joinToString("\n\n")

            val fallbackSymptoms = session.collectedSymptoms.ifEmpty { prediction.symptoms }
            val report = localWorkspaceRepository.newBlankReport().copy(
                name = "AI health report - ${prediction.disease}",
                status = "AI Filled",
                patientSummary = extraction?.patientSummary.orEmpty(),
                symptoms = extraction?.symptoms
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackSymptoms.joinToString(", "),
                medications = extraction?.medications.orEmpty(),
                allergies = extraction?.allergies.orEmpty(),
                vitals = localWorkspaceRepository.formatLatestVitalsForReport(),
                medicalHistory = mergedMedicalHistory,
                lifestyleNotes = extraction?.lifestyleNotes.orEmpty(),
                doctorNotes = "",
                recommendations = extraction?.recommendations.orEmpty(),
                followUpPlan = ""
            )
            localWorkspaceRepository.upsertReport(report)
            Log.d(TAG, "Local AI health report saved: ${report.id}")
        }.onFailure {
            Log.e(TAG, "Failed to auto-generate health report: ${it.message}", it)
        }
    }

    private fun hasEnoughConversationForReport(session: SymptomSession): Boolean {
        val userTurns = session.turns.count { it.role == "user" }
        return userTurns >= 2 && session.collectedSymptoms.size >= 2
    }

    private suspend fun requestStructuredReportExtraction(
        session: SymptomSession,
        prediction: DiseasePrediction
    ): ReportExtraction? {
        val transcript = session.turns.joinToString("\n") { turn ->
            "${turn.role}: ${turn.content}"
        }
        val docContext = localWorkspaceRepository.buildEnabledDocumentContext(
            maxDocuments = 4,
            maxCharsPerDocument = 1200,
            totalMaxChars = 3600
        )
        val latestVitals = localWorkspaceRepository.formatLatestVitalsForReport()

        val request = GroqRequest(
            model = "llama-3.3-70b-versatile",
            temperature = 0.1,
            max_tokens = 600,
            messages = listOf(
                groqTextMessage(
                    "system",
                    "You are a structured medical scribe. You MUST call the fill_health_report tool exactly once with best-effort fields. Use empty strings when unknown and never invent facts."
                ),
                groqTextMessage(
                    "user",
                    """
                    Conversation transcript:
                    $transcript

                    Predicted condition: ${prediction.disease}
                    Predicted symptoms: ${prediction.symptoms.joinToString(", ")}
                    Latest vitals summary: $latestVitals

                    Enabled uploaded medical documents (text extract):
                    ${if (docContext.isBlank()) "None" else docContext}
                    """.trimIndent()
                )
            ),
            tools = listOf(reportFillTool()),
            tool_choice = mapOf(
                "type" to "function",
                "function" to mapOf("name" to REPORT_FILL_TOOL_NAME)
            )
        )

        val response = callGroqMessage(request) ?: return null
        val toolCall = response.tool_calls
            ?.firstOrNull { it.function?.name == REPORT_FILL_TOOL_NAME }
            ?.function
            ?.arguments

        val rawPayload = toolCall ?: response.content.orEmpty()
        if (rawPayload.isBlank()) return null
        return parseReportExtraction(rawPayload)
    }

    private fun reportFillTool(): GroqTool {
        val schema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "patientSummary" to mapOf("type" to "string"),
                "symptoms" to mapOf(
                    "description" to "Either a short string or list of symptoms discussed",
                    "oneOf" to listOf(
                        mapOf("type" to "string"),
                        mapOf("type" to "array", "items" to mapOf("type" to "string"))
                    )
                ),
                "medications" to mapOf("type" to "string"),
                "allergies" to mapOf("type" to "string"),
                "lifestyleNotes" to mapOf("type" to "string"),
                "recommendations" to mapOf("type" to "string"),
                "medicalHistory" to mapOf("type" to "string")
            ),
            "required" to listOf(
                "patientSummary",
                "symptoms",
                "medications",
                "allergies",
                "lifestyleNotes",
                "recommendations",
                "medicalHistory"
            ),
            "additionalProperties" to false
        )

        return GroqTool(
            function = GroqToolFunction(
                name = REPORT_FILL_TOOL_NAME,
                description = "Fill structured local health report fields from conversation and document context.",
                parameters = schema
            )
        )
    }

    private fun parseReportExtraction(raw: String): ReportExtraction? {
        return runCatching {
            val obj = JSONObject(stripMarkdownJson(raw))
            ReportExtraction(
                patientSummary = obj.optString("patientSummary").trim(),
                symptoms = parseSymptomsField(obj).trim(),
                medications = obj.optString("medications").trim(),
                allergies = obj.optString("allergies").trim(),
                lifestyleNotes = obj.optString("lifestyleNotes").trim(),
                recommendations = obj.optString("recommendations").trim(),
                medicalHistory = obj.optString("medicalHistory").trim()
            )
        }.getOrElse {
            Log.e(TAG, "parseReportExtraction failed: ${it.message} | raw=$raw")
            null
        }
    }

    private fun parseSymptomsField(obj: JSONObject): String {
        if (!obj.has("symptoms") || obj.isNull("symptoms")) return ""
        val value = obj.get("symptoms")
        return when (value) {
            is JSONArray -> List(value.length()) { idx -> value.optString(idx) }
                .filter { it.isNotBlank() }
                .joinToString(", ")
            is String -> value
            else -> value.toString()
        }
    }

    // ── JSON parsing ──────────────────────────────────────────────────────────

    private fun stripMarkdownJson(raw: String): String {
        val fenceRegex = Regex("^```(?:json)?\\s*\\n?(.*?)\\n?```$", RegexOption.DOT_MATCHES_ALL)
        return fenceRegex.find(raw.trim())?.groupValues?.get(1)?.trim() ?: raw.trim()
    }

    private fun looksLikeJson(text: String): Boolean {
        val trimmed = text.trim()
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"))
    }

    private fun tryParseReadyToPredict(text: String): List<String>? = try {
        val obj = JSONObject(text)
        if (obj.optBoolean("readyToPredict", false)) {
            val arr = obj.optJSONArray("collectedSymptoms") ?: return null
            List(arr.length()) { arr.getString(it) }
        } else null
    } catch (e: JSONException) { null }

    private fun parsePrediction(raw: String): DiseasePrediction? = try {
        val obj = JSONObject(stripMarkdownJson(raw))
        val disease = obj.optString("disease").takeIf { it.isNotBlank() } ?: return null
        val accuracy = obj.optInt("accuracy", 60).coerceIn(0, 100)
        val symptomsArr = obj.optJSONArray("symptoms")
        val symptoms = if (symptomsArr != null) List(symptomsArr.length()) { symptomsArr.getString(it) } else emptyList()
        val reasoning = obj.optString("reasoning", "")
        val category = DiseaseCategory.fromString(obj.optString("category", "UNKNOWN"))
        DiseasePrediction(disease, accuracy, symptoms, reasoning, category)
    } catch (e: Exception) {
        Log.e(TAG, "parsePrediction failed: ${e.message} | raw=$raw")
        null
    }

    private fun parseDirectDiseaseReport(raw: String): DiseasePrediction? {
        return try {
            val obj = JSONObject(stripMarkdownJson(raw))
            if (!obj.optBoolean("directReport", false)) return null
            val disease = obj.optString("disease").takeIf { it.isNotBlank() } ?: return null
            val accuracy = obj.optInt("accuracy", 80).coerceIn(0, 100)
            val symptomsArr = obj.optJSONArray("symptoms")
            val symptoms = if (symptomsArr != null) {
                List(symptomsArr.length()) { symptomsArr.getString(it) }
            } else {
                listOf("known disease reported by user")
            }
            val reasoning = obj.optString("reasoning", "User directly reported this known disease.")
            val category = DiseaseCategory.fromString(obj.optString("category", "UNKNOWN"))
            DiseasePrediction(disease, accuracy, symptoms, reasoning, category)
        } catch (e: Exception) {
            Log.e(TAG, "parseDirectDiseaseReport failed: ${e.message} | raw=$raw")
            null
        }
    }

    // ── Yes/No detection across Indian languages ──────────────────────────────

    private fun isAffirmative(text: String): Boolean {
        val lower = text.lowercase().trim()
        val yesTokens = setOf(
            // English
            "yes", "yeah", "yep", "sure", "ok", "okay", "confirm", "please", "do it", "go ahead",
            // Hindi / Urdu
            "haan", "han", "ha", "haa", "ji", "bilkul", "zaroor", "theek", "accha",
            // Kannada
            "houdu", "haa",
            // Telugu
            "avunu", "aunu",
            // Tamil
            "aamam", "aama", "sari",
            // Marathi
            "ho", "hoy",
            // Bengali
            "hya", "haan",
            // Gujarati
            "ha", "haa", "thik"
        )
        return yesTokens.any { lower.contains(it) }
    }

    companion object {
        private const val TAG = "SymptomVM"
        private const val REPORT_FILL_TOOL_NAME = "fill_health_report"
    }
}
