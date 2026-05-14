package com.sohanreddy.sevak.map

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val COLLECTION = "disease_reports"
private const val TAG = "FirestoreMapRepo"

class FirestoreMapRepository {

    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection(COLLECTION)

    /**
     * Writes a [DiseaseReport] to Firestore.
     * If [report.id] is blank a new document ID is generated; otherwise the given ID is used.
     */
    suspend fun submitReport(report: DiseaseReport): Result<Unit> {
        return try {
            val docRef = if (report.id.isBlank()) {
                collection.document()
            } else {
                collection.document(report.id)
            }
            docRef.set(report.toFirestore()).await()
            Log.d(TAG, "Report submitted: ${docRef.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "submitReport failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Real-time flow of all disease reports, ordered by timestamp descending.
     * Emits a new list whenever Firestore data changes.
     */
    fun observeReports(): Flow<List<DiseaseReport>> = callbackFlow {
        val registration = collection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeReports error: ${error.message}", error)
                    // Don't close the flow — Firestore will retry automatically
                    return@addSnapshotListener
                }
                val reports = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        DiseaseReport.fromFirestore(doc)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse report ${doc.id}: ${e.message}")
                        null
                    }
                } ?: emptyList()
                trySend(reports)
            }
        awaitClose { registration.remove() }
    }
}
