package com.sohanreddy.sevak.map

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiseaseMapUiState(
    val reports: List<DiseaseReport> = emptyList(),
    val userLocation: LatLng? = null,
    val selectedReport: DiseaseReport? = null,
    val isLoading: Boolean = true
)

class DiseaseMapViewModel(
    private val repo: FirestoreMapRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DiseaseMapUiState())
    val uiState: StateFlow<DiseaseMapUiState> = _uiState.asStateFlow()

    // ── Static dummy data for Bangalore ──────────────────────────────────────
    private val bangaloreDummies = listOf(
        DiseaseReport(
            id = "dummy-1",
            lat = 12.9716, lng = 77.5946,
            disease = "Dengue Fever", accuracy = 87,
            symptoms = listOf("high fever", "body pain", "rash", "headache"),
            reasoning = "Classic dengue triad with rash in monsoon season.",
            category = "FEVER",
            language = "en", userId = "demo",
            timestamp = System.currentTimeMillis() - 3_600_000, radius = 600
        ),
        DiseaseReport(
            id = "dummy-2",
            lat = 12.9352, lng = 77.6245,
            disease = "Gastroenteritis", accuracy = 74,
            symptoms = listOf("vomiting", "diarrhoea", "stomach cramps"),
            reasoning = "Acute onset GI symptoms likely due to contaminated water.",
            category = "GASTROINTESTINAL",
            language = "en", userId = "demo",
            timestamp = System.currentTimeMillis() - 7_200_000, radius = 500
        ),
        DiseaseReport(
            id = "dummy-3",
            lat = 12.9850, lng = 77.6056,
            disease = "Viral Fever", accuracy = 91,
            symptoms = listOf("fever", "chills", "fatigue", "body ache"),
            reasoning = "Typical viral fever pattern with seasonal correlation.",
            category = "FEVER",
            language = "en", userId = "demo",
            timestamp = System.currentTimeMillis() - 1_800_000, radius = 450
        ),
        DiseaseReport(
            id = "dummy-4",
            lat = 12.9560, lng = 77.5730,
            disease = "Common Cold", accuracy = 68,
            symptoms = listOf("runny nose", "sore throat", "sneezing"),
            reasoning = "Mild upper respiratory symptoms consistent with rhinovirus.",
            category = "RESPIRATORY",
            language = "en", userId = "demo",
            timestamp = System.currentTimeMillis() - 14_400_000, radius = 500
        ),
        DiseaseReport(
            id = "dummy-5",
            lat = 12.9783, lng = 77.6408,
            disease = "Skin Allergy", accuracy = 62,
            symptoms = listOf("itching", "red patches", "swelling"),
            reasoning = "Contact dermatitis likely triggered by environmental allergen.",
            category = "SKIN",
            language = "en", userId = "demo",
            timestamp = System.currentTimeMillis() - 28_800_000, radius = 400
        )
    )

    init {
        // Real-time Firestore subscription — merge with static Bangalore dummies
        viewModelScope.launch {
            repo.observeReports().collect { firestoreReports ->
                val allReports = bangaloreDummies + buildNearbyDummies() + firestoreReports
                _uiState.update { it.copy(reports = allReports, isLoading = false) }
            }
        }
    }

    /**
     * Called from the composable AFTER location permission is confirmed and GPS returns a fix.
     * Generates 2 dummy zones near the user's actual location.
     */
    fun setUserLocation(latLng: LatLng) {
        Log.d("DiseaseMapVM", "setUserLocation: ${latLng.latitude}, ${latLng.longitude}")
        _uiState.update { it.copy(userLocation = latLng) }

        // Rebuild reports to include nearby dummies
        viewModelScope.launch {
            val current = _uiState.value.reports
                .filter { !it.id.startsWith("nearby-") } // Remove old nearby dummies
            _uiState.update { it.copy(reports = current + buildNearbyDummies()) }
        }
    }

    /** Generates 2 dynamic dummy zones near the user's current position. */
    private fun buildNearbyDummies(): List<DiseaseReport> {
        val loc = _uiState.value.userLocation ?: return emptyList()
        return listOf(
            DiseaseReport(
                id = "nearby-1",
                lat = loc.latitude + 0.008,   // ~900m north
                lng = loc.longitude + 0.005,  // ~500m east
                disease = "Seasonal Flu", accuracy = 78,
                symptoms = listOf("fever", "cough", "runny nose", "body ache"),
                reasoning = "Influenza-like illness consistent with seasonal H1N1 pattern.",
                category = "FEVER",
                language = "en", userId = "nearby-demo",
                timestamp = System.currentTimeMillis() - 5_400_000, radius = 500
            ),
            DiseaseReport(
                id = "nearby-2",
                lat = loc.latitude - 0.006,   // ~670m south
                lng = loc.longitude - 0.007,  // ~700m west
                disease = "Food Poisoning", accuracy = 83,
                symptoms = listOf("nausea", "vomiting", "abdominal pain", "diarrhoea"),
                reasoning = "Acute gastroenteritis from suspected bacterial contamination.",
                category = "GASTROINTESTINAL",
                language = "en", userId = "nearby-demo",
                timestamp = System.currentTimeMillis() - 10_800_000, radius = 450
            )
        )
    }

    fun onReportSelected(report: DiseaseReport?) {
        _uiState.update { it.copy(selectedReport = report) }
    }

    // ── Factory ──────────────────────────────────────────────────────────────
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DiseaseMapViewModel(FirestoreMapRepository(), application) as T
    }
}
