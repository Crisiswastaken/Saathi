package com.sohanreddy.sevak.symptom

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.sohanreddy.sevak.map.DiseaseReport
import com.sohanreddy.sevak.map.FirestoreMapRepository
import com.sohanreddy.sevak.network.GroqApiService
import com.sohanreddy.sevak.network.GroqRequest
import com.sohanreddy.sevak.network.GroqRequestMessage
import com.sohanreddy.sevak.network.groqTextMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val groqApiKey: String
) : ViewModel() {

    private val _state = MutableStateFlow<SymptomState>(SymptomState.Idle)
    val state: StateFlow<SymptomState> = _state.asStateFlow()

    /** Text that MainViewModel should feed into Sarvam TTS immediately on emission. */
    private val _ttsOutput = MutableStateFlow<String?>(null)
    val ttsOutput: StateFlow<String?> = _ttsOutput.asStateFlow()

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
        launchCollecting(session)
    }

    fun onUserSpoke(text: String) {
        val session = currentSession ?: return
        when (_state.value) {
            is SymptomState.Collecting -> {
                val updated = session.addTurn("user", text)
                currentSession = updated
                launchCollecting(updated)
            }
            is SymptomState.AwaitingConfirm -> {
                currentJob?.cancel()
                currentJob = viewModelScope.launch { handleConfirmation(text, session) }
            }
            else -> Log.d(TAG, "onUserSpoke ignored in state ${_state.value}")
        }
    }

    // ── Internal coroutine launchers ──────────────────────────────────────────

    private fun launchCollecting(session: SymptomSession) {
        currentJob?.cancel()
        currentJob = viewModelScope.launch { runFollowUpTurn(session) }
    }

    // ── Follow-up questioning loop ────────────────────────────────────────────

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
                // LLM is asking another follow-up question
                val updated = session.addTurn("assistant", raw.trim())
                currentSession = updated
                _ttsOutput.value = raw.trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "runFollowUpTurn: ${e.message}", e)
            val msg = "Sorry, something went wrong. Please try again."
            _state.value = SymptomState.Error(msg)
            _ttsOutput.value = msg
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
                _ttsOutput.value = msg
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

            // 3. Generate map-confirmation question
            val confirmText = callGroq(listOf(
                groqTextMessage("system", SymptomPrompts.mapConfirmationPrompt(prediction.disease, prediction.accuracy, session.language)),
                groqTextMessage("user", "Ask the user now.")
            ))

            // 4. Emit both as one TTS segment, then await user response
            _state.value = SymptomState.AwaitingConfirm
            _ttsOutput.value = "$announcementText $confirmText"

        } catch (e: Exception) {
            Log.e(TAG, "runPredictionFlow: ${e.message}", e)
            val msg = "Assessment failed. Please try again."
            _state.value = SymptomState.Error(msg)
            _ttsOutput.value = msg
        }
    }

    // ── Confirmation handler ──────────────────────────────────────────────────

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
            }
            _state.value = SymptomState.Done
            _navigateToMap.value = true
        } else {
            // User declined — silently finish
            _state.value = SymptomState.Done
        }
    }

    // ── Groq helpers ──────────────────────────────────────────────────────────

    private fun buildFollowUpMessages(session: SymptomSession): List<GroqRequestMessage> {
        val msgs = mutableListOf(
            groqTextMessage("system", SymptomPrompts.followUpSystemPrompt(session.language))
        )
        session.turns.forEach { msgs.add(groqTextMessage(it.role, it.content)) }
        return msgs
    }

    private suspend fun callGroq(messages: List<GroqRequestMessage>): String {
        val request = GroqRequest(
            model = "llama-3.3-70b-versatile",
            messages = messages,
            temperature = 0.3,
            max_tokens = 400
        )
        return groqApiService.chat("Bearer $groqApiKey", request)
            .choices.firstOrNull()?.message?.content?.trim() ?: ""
    }

    // ── JSON parsing ──────────────────────────────────────────────────────────

    private fun stripMarkdownJson(raw: String): String {
        val fenceRegex = Regex("^```(?:json)?\\s*\\n?(.*?)\\n?```$", RegexOption.DOT_MATCHES_ALL)
        return fenceRegex.find(raw.trim())?.groupValues?.get(1)?.trim() ?: raw.trim()
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
        val accuracy = obj.optInt("accuracy", 60)
        val symptomsArr = obj.optJSONArray("symptoms")
        val symptoms = if (symptomsArr != null) List(symptomsArr.length()) { symptomsArr.getString(it) } else emptyList()
        val reasoning = obj.optString("reasoning", "")
        val category = DiseaseCategory.fromString(obj.optString("category", "UNKNOWN"))
        DiseasePrediction(disease, accuracy, symptoms, reasoning, category)
    } catch (e: Exception) {
        Log.e(TAG, "parsePrediction failed: ${e.message} | raw=$raw")
        null
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

    companion object { private const val TAG = "SymptomVM" }
}
