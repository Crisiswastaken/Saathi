package com.sohanreddy.sevak.map

import com.google.firebase.firestore.DocumentSnapshot

data class DiseaseReport(
    val id: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val disease: String = "",
    val accuracy: Int = 0,
    val symptoms: List<String> = emptyList(),
    val reasoning: String = "",
    val category: String = "",
    val language: String = "",
    val userId: String = "",
    val timestamp: Long = 0L,
    val radius: Int = 500
) {
    companion object {
        fun fromFirestore(doc: DocumentSnapshot): DiseaseReport {
            @Suppress("UNCHECKED_CAST")
            return DiseaseReport(
                id = doc.id,
                lat = doc.getDouble("lat") ?: 0.0,
                lng = doc.getDouble("lng") ?: 0.0,
                disease = doc.getString("disease") ?: "",
                accuracy = (doc.getLong("accuracy") ?: 0L).toInt(),
                symptoms = (doc.get("symptoms") as? List<String>) ?: emptyList(),
                reasoning = doc.getString("reasoning") ?: "",
                category = doc.getString("category") ?: "",
                language = doc.getString("language") ?: "",
                userId = doc.getString("userId") ?: "",
                timestamp = doc.getLong("timestamp") ?: 0L,
                radius = (doc.getLong("radius") ?: 500L).toInt()
            )
        }
    }

    fun toFirestore(): Map<String, Any> = mapOf(
        "lat" to lat,
        "lng" to lng,
        "disease" to disease,
        "accuracy" to accuracy,
        "symptoms" to symptoms,
        "reasoning" to reasoning,
        "category" to category,
        "language" to language,
        "userId" to userId,
        "timestamp" to timestamp,
        "radius" to radius
    )
}
