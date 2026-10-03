package com.vastutalks.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.vastutalks.app.data.model.ExpertProfile
import com.vastutalks.app.data.model.UserProfile
import com.vastutalks.app.data.model.UserRole
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    /**
     * Called right after Firebase Auth account creation. Writes the
     * users/{uid} profile (with role) and, for experts, an
     * experts/{uid} entry so they show up in the live directory.
     */
    suspend fun createProfileForNewUser(uid: String, name: String, email: String, role: UserRole): Result<Unit> {
        return try {
            val profile = UserProfile(
                uid = uid,
                name = name,
                email = email,
                role = role.name,
                createdAtMillis = System.currentTimeMillis()
            )
            firestore.collection("users").document(uid).set(profile).await()

            if (role == UserRole.VASTU_EXPERT) {
                val expertProfile = ExpertProfile(
                    uid = uid,
                    name = name,
                    email = email,
                    isOnline = true,
                    createdAtMillis = System.currentTimeMillis()
                )
                firestore.collection("experts").document(uid).set(expertProfile).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Called from VastuMessagingService.onNewToken and once after sign-in, so calls can reach this device. */
    suspend fun saveFcmToken(token: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("No signed-in user"))
        return try {
            firestore.collection("users").document(uid).update("fcmToken", token).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Called (only from the expert's own device — see VastuNavGraph) when a call they were on ends. */
    suspend fun incrementConsultationCount(expertUid: String): Result<Unit> {
        return try {
            firestore.collection("experts").document(expertUid)
                .update("consultationsCompleted", FieldValue.increment(1))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentUserRole(): Result<UserRole> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("No signed-in user"))
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            val profile = doc.toObject(UserProfile::class.java)
            val role = profile?.role?.let { runCatching { UserRole.valueOf(it) }.getOrNull() } ?: UserRole.NORMAL_USER
            Result.success(role)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchOnlineExperts(): Result<List<ExpertProfile>> {
        return try {
            val snapshot = firestore.collection("experts")
                .get()
                .await()
            val experts = snapshot.documents.mapNotNull { it.toObject(ExpertProfile::class.java) }
            Result.success(experts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Live list of every signed-up expert — shows regardless of online/offline status. */
    fun listenOnlineExperts(): Flow<List<ExpertProfile>> = callbackFlow {
        val registration = firestore.collection("experts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val experts = snapshot?.documents?.mapNotNull { it.toObject(ExpertProfile::class.java) } ?: emptyList()
                trySend(experts)
            }
        awaitClose { registration.remove() }
    }
}
