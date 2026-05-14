package com.sohanreddy.sevak.symptom

import java.util.UUID

// ── Conversation turn ────────────────────────────────────────────────────────
data class SymptomTurn(
    val role: String,    // "user" or "assistant"
    val content: String
)

// ── Disease category with brand colour ───────────────────────────────────────
enum class DiseaseCategory(val hexColor: String) {
    FEVER("#FF6B6B"),
    RESPIRATORY("#4FC3F7"),
    GASTROINTESTINAL("#FFB74D"),
    SKIN("#F48FB1"),
    NEUROLOGICAL("#CE93D8"),
    UNKNOWN("#90A4AE");

    companion object {
        /** Parses the string returned by the LLM, defaulting to UNKNOWN on any mismatch. */
        fun fromString(value: String): DiseaseCategory =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

// ── LLM prediction result ────────────────────────────────────────────────────
data class DiseasePrediction(
    val disease: String,
    val accuracy: Int,
    val symptoms: List<String>,
    val reasoning: String,
    val category: DiseaseCategory
)

// ── Active symptom session ───────────────────────────────────────────────────
data class SymptomSession(
    val id: String = UUID.randomUUID().toString(),
    val language: String = "en",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val turns: List<SymptomTurn> = emptyList(),
    val collectedSymptoms: List<String> = emptyList(),
    val prediction: DiseasePrediction? = null
) {
    fun addTurn(role: String, content: String): SymptomSession =
        copy(turns = turns + SymptomTurn(role, content))
}
