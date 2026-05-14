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
import kotlin.math.*

// ── Aggregated zone shown on the map ─────────────────────────────────────────
data class DiseaseZone(
    val center: LatLng,
    val disease: String,
    val avgAccuracy: Int,
    val count: Int,
    val category: String,
    val radiusMeters: Double,
    val reports: List<DiseaseReport>
)

data class DiseaseMapUiState(
    val reports: List<DiseaseReport> = emptyList(),
    val zones: List<DiseaseZone> = emptyList(),
    val userLocation: LatLng? = null,
    val selectedReport: DiseaseReport? = null,
    val selectedZone: DiseaseZone? = null,
    val isLoading: Boolean = true
)

class DiseaseMapViewModel(
    private val repo: FirestoreMapRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DiseaseMapUiState())
    val uiState: StateFlow<DiseaseMapUiState> = _uiState.asStateFlow()

    init {
        // Real-time Firestore subscription
        viewModelScope.launch {
            repo.observeReports().collect { firestoreReports ->
                val allReports = firestoreReports
                val zones = aggregateToZones(allReports)
                _uiState.update { it.copy(reports = allReports, zones = zones, isLoading = false) }
            }
        }
    }

    /**
     * Called from the composable AFTER location permission is confirmed and GPS returns a fix.
     */
    fun setUserLocation(latLng: LatLng) {
        Log.d("DiseaseMapVM", "setUserLocation: ${latLng.latitude}, ${latLng.longitude}")
        _uiState.update { it.copy(userLocation = latLng) }
    }

    fun onZoneSelected(zone: DiseaseZone?) {
        _uiState.update { it.copy(selectedZone = zone) }
    }

    fun onReportSelected(report: DiseaseReport?) {
        _uiState.update { it.copy(selectedReport = report) }
    }

    // ── Zone aggregation ─────────────────────────────────────────────────────
    // Groups reports by disease + proximity (~1km).
    // Same disease within 1km of each other = same zone.

    private fun aggregateToZones(reports: List<DiseaseReport>): List<DiseaseZone> {
        val zones = mutableListOf<DiseaseZone>()
        val remaining = reports.toMutableList()

        while (remaining.isNotEmpty()) {
            val seed = remaining.removeFirst()
            val cluster = mutableListOf(seed)

            // Find all reports with the same disease within 1km
            val iter = remaining.iterator()
            while (iter.hasNext()) {
                val other = iter.next()
                if (other.disease.equals(seed.disease, ignoreCase = true)) {
                    val dist = haversineMeters(seed.lat, seed.lng, other.lat, other.lng)
                    if (dist < 1000.0) {
                        cluster.add(other)
                        iter.remove()
                    }
                }
            }

            val avgLat = cluster.map { it.lat }.average()
            val avgLng = cluster.map { it.lng }.average()
            val avgAccuracy = cluster.map { it.accuracy }.average().roundToInt()

            zones.add(
                DiseaseZone(
                    center = LatLng(avgLat, avgLng),
                    disease = seed.disease,
                    avgAccuracy = avgAccuracy,
                    count = cluster.size,
                    category = seed.category,
                    radiusMeters = 1000.0,  // Fixed 1km radius
                    reports = cluster
                )
            )
        }
        return zones
    }

    /** Haversine distance in meters between two lat/lng points */
    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6_371_000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    // ── Factory ──────────────────────────────────────────────────────────────
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DiseaseMapViewModel(FirestoreMapRepository(), application) as T
    }
}
