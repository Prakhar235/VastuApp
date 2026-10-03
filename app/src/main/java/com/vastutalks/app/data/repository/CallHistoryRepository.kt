package com.vastutalks.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.vastutalks.app.data.model.CallHistoryEntry
import kotlinx.coroutines.tasks.await

/**
 * Stores call history under users/{uid}/callHistory/{autoId} — each
 * user only ever reads/writes their own subcollection. Remember to
 * set matching Firestore security rules in the Firebase Console
 * (see README's "Wiring up call history" section) so that holds
 * server-side too, not just in this client code.
 */
class CallHistoryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private fun historyCollection() = auth.currentUser?.uid?.let { uid ->
        firestore.collection("users").document(uid).collection("callHistory")
    }

    suspend fun logCall(entry: CallHistoryEntry): Result<Unit> {
        val collection = historyCollection()
            ?: return Result.failure(IllegalStateException("No signed-in user"))
        return try {
            collection.add(entry).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchHistory(): Result<List<CallHistoryEntry>> {
        val collection = historyCollection()
            ?: return Result.failure(IllegalStateException("No signed-in user"))
        return try {
            val snapshot = collection
                .orderBy("startTimeMillis", Query.Direction.DESCENDING)
                .get()
                .await()
            val entries = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CallHistoryEntry::class.java)?.copy(id = doc.id)
            }
            Result.success(entries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
