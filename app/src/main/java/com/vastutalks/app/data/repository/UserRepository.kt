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

/** What an expert fills in at sign-up, on top of what every user gives. */
data class ExpertDetails(
    val specialty: String,
    val experienceYears: Int,
    val city: String,
    val bio: String
)

class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    /**
     * Called right after Firebase Auth account creation. Writes the
     * users/{uid} profile (with role, phone and photo) and, for experts,
     * an experts/{uid} entry with their public details so they show up
     * in the live directory.
     */
    suspend fun createProfileForNewUser(
        uid: String,
        name: String,
        email: String,
        role: UserRole,
        phone: String,
        photo: String,
        expert: ExpertDetails?
    ): Result<Unit> {
        return try {
            val profile = UserProfile(
                uid = uid,
                name = name,
                email = email,
                phone = phone,
                photo = photo,
                role = role.name,
                createdAtMillis = System.currentTimeMillis()
            )
            firestore.collection("users").document(uid).set(profile).await()

            if (role == UserRole.VASTU_EXPERT) {
                val expertProfile = ExpertProfile(
                    uid = uid,
                    name = name,
                    email = email,
                    photo = photo,
                    specialty = expert?.specialty.orEmpty(),
                    experienceYears = expert?.experienceYears ?: 0,
                    city = expert?.city.orEmpty(),
                    bio = expert?.bio.orEmpty(),
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

    /** The signed-in user's own profile (name, phone, photo…), live. */
    fun listenMyProfile(): Flow<UserProfile?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val registration = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ -> trySend(snapshot?.toObject(UserProfile::class.java)) }
        awaitClose { registration.remove() }
    }

    /**
     * Replaces the signed-in user's profile photo — on users/{uid}, and on
     * experts/{uid} too for experts, so the directory shows the new one.
     */
    suspend fun updatePhoto(photo: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("No signed-in user"))
        return try {
            firestore.collection("users").document(uid).update("photo", photo).await()
            val expertDoc = firestore.collection("experts").document(uid)
            if (expertDoc.get().await().exists()) expertDoc.update("photo", photo).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Expert edits their city, experience and About from their Home. */
    suspend fun updateExpertDetails(city: String, experienceYears: Int, bio: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("No signed-in user"))
        return try {
            firestore.collection("experts").document(uid)
                .update(mapOf("city" to city, "experienceYears" to experienceYears, "bio" to bio))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Expert's online/offline switch. The field is "online", not
     * "isOnline" — that's how Firestore stores ExpertProfile.isOnline.
     */
    suspend fun setOnline(online: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("No signed-in user"))
        return try {
            firestore.collection("experts").document(uid).update("online", online).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * The expert's app checking in (see ExpertProfile.lastSeenMillis);
     * [present] false clears it, e.g. on sign-out, so they show offline
     * straight away instead of after the timeout.
     */
    suspend fun heartbeat(present: Boolean = true): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("No signed-in user"))
        return try {
            firestore.collection("experts").document(uid)
                .update("lastSeenMillis", if (present) System.currentTimeMillis() else 0L)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Marks the signed-in expert as on a call (or free again), so callers see them as busy. */
    suspend fun setInCall(inCall: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("No signed-in user"))
        return try {
            firestore.collection("experts").document(uid)
                .update(mapOf("inCall" to inCall, "inCallSinceMillis" to System.currentTimeMillis()))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getExpert(uid: String): Result<ExpertProfile?> {
        return try {
            Result.success(firestore.collection("experts").document(uid).get().await().toObject(ExpertProfile::class.java))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Live view of one expert (online, busy, consultations). Emits null if they have no expert entry. */
    fun listenExpert(uid: String): Flow<ExpertProfile?> = callbackFlow {
        val registration = firestore.collection("experts").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(ExpertProfile::class.java))
            }
        awaitClose { registration.remove() }
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

    /** Live list of every signed-up expert — shows regardless of online/offline status (see sortedForDirectory). */
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
