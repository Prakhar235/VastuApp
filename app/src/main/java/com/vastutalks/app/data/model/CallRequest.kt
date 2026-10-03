package com.vastutalks.app.data.model

enum class CallRequestStatus {
    RINGING,
    ACCEPTED,
    DECLINED,
    ENDED,
    CANCELLED
}

/**
 * Stored at callRequests/{callId} — this is the whole real-time
 * signaling mechanism. The caller writes one of these; the callee's
 * app has a live Firestore listener watching for new ones addressed
 * to their uid, which is what makes the "expert gets a call in
 * real-time" part work without any push-notification setup.
 */
data class CallRequest(
    val callId: String = "",
    val channelName: String = "",
    val callerUid: String = "",
    val callerName: String = "",
    val calleeUid: String = "",
    val calleeName: String = "",
    val callType: String = "", // CallType.name
    val status: String = CallRequestStatus.RINGING.name,
    val createdAtMillis: Long = 0L
)
