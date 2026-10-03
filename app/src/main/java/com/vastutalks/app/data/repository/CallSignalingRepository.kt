package com.vastutalks.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.vastutalks.app.data.model.CallRequest
import com.vastutalks.app.data.model.CallRequestStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CallSignalingRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun collection() = firestore.collection("callRequests")

    /**
     * Caller creates this. channelName is fixed per expert (not per
     * call) so a single Agora token can be cached and reused for a
     * whole day of calls instead of minted fresh every time — see
     * TokenRepository.fetchOrCreateExpertToken. The trade-off: only
     * one call can be active on an expert's channel at a time, which
     * is fine since an expert can only take one call at a time anyway.
     */
    suspend fun createCallRequest(
        callerUid: String,
        callerName: String,
        calleeUid: String,
        calleeName: String,
        callType: String
    ): Result<CallRequest> {
        return try {
            val callId = UUID.randomUUID().toString()
            val request = CallRequest(
                callId = callId,
                channelName = "expert-$calleeUid",
                callerUid = callerUid,
                callerName = callerName,
                calleeUid = calleeUid,
                calleeName = calleeName,
                callType = callType,
                status = CallRequestStatus.RINGING.name,
                createdAtMillis = System.currentTimeMillis()
            )
            collection().document(callId).set(request).await()
            Result.success(request)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateStatus(callId: String, status: CallRequestStatus): Result<Unit> {
        return try {
            collection().document(callId).update("status", status.name).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Expert side: live stream of calls ringing for this uid. This is the "real-time" part — no polling, no push setup needed. */
    fun listenForIncomingCalls(myUid: String): Flow<CallRequest?> = callbackFlow {
        val registration = collection()
            .whereEqualTo("calleeUid", myUid)
            .whereEqualTo("status", CallRequestStatus.RINGING.name)
            .addSnapshotListener { snapshot, _ ->
                val call = snapshot?.documents?.firstOrNull()?.toObject(CallRequest::class.java)
                trySend(call)
            }
        awaitClose { registration.remove() }
    }

    /** Caller side: watch a specific call request for accept/decline so LiveCallingScreen knows when to proceed or bail. */
    fun listenForCallStatus(callId: String): Flow<CallRequest?> = callbackFlow {
        val registration = collection().document(callId)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.toObject(CallRequest::class.java))
            }
        awaitClose { registration.remove() }
    }
}
