package com.vastutalks.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.vastutalks.app.data.model.AppNotification
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** The signed-in user's notifications (users/{uid}/notifications), written by Cloud Functions. */
class NotificationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private fun collection() = auth.currentUser?.uid?.let { firestore.collection("users").document(it).collection("notifications") }

    /** Newest first, live. */
    fun listen(limit: Long = 100): Flow<List<AppNotification>> = callbackFlow {
        val notifications = collection()
        if (notifications == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = notifications
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.documents?.mapNotNull { doc -> doc.toObject(AppNotification::class.java)?.copy(id = doc.id) } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    /** Marks [ids] as read (when the list is opened). */
    suspend fun markRead(ids: List<String>) {
        val notifications = collection() ?: return
        if (ids.isEmpty()) return
        runCatching {
            val batch = firestore.batch()
            ids.forEach { batch.update(notifications.document(it), "read", true) }
            batch.commit().await()
        }
    }
}
