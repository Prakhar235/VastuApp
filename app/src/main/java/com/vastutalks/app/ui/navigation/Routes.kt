package com.vastutalks.app.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val SIGN_IN = "sign_in"
    const val CREATE_ACCOUNT = "create_account"
    const val HOME = "home"
    const val PROFILE = "profile"
    /** Vastu experts write a post for the users' Posts tab. */
    const val CREATE_POST = "create_post"
    const val POST_DETAIL = "post/{postId}"
    const val NOTIFICATIONS = "notifications"
    /** One-to-one chat with a user or expert, and the list of all chats. */
    const val CONVERSATION = "conversation/{otherUid}/{otherName}"
    const val CHATS = "chats"
    fun conversation(otherUid: String, otherName: String) =
        "conversation/${java.net.URLEncoder.encode(otherUid, "UTF-8")}/${java.net.URLEncoder.encode(otherName.ifBlank { "Chat" }, "UTF-8")}"
    fun postDetail(postId: String) = "post/${java.net.URLEncoder.encode(postId, "UTF-8")}"

    // Real (Firestore-signaled) calling flow between a normal user and a signed-up expert.
    const val LIVE_CALLING = "live_calling/{calleeUid}/{calleeName}/{callType}"
    const val LIVE_IN_CALL = "live_in_call/{channelName}/{peerName}/{callType}"
    const val REAL_EXPERT_PROFILE = "real_expert_profile/{expertUid}/{expertName}"

    // Call with the AI agent Ananya — runs on the device plus the OpenAI
    // API; no Firestore signaling or Agora token. See AnanyaAgent.kt /
    // AgentCallingScreen.kt.
    /** {video}: true to start with the camera on; switchable during the call either way. */
    const val AGENT_CALLING = "agent_calling/{video}"
    const val AGENT_IN_CALL = "agent_in_call/{video}"
    fun agentCalling(video: Boolean) = "agent_calling/$video"
    fun agentInCall(video: Boolean) = "agent_in_call/$video"
    /** Text chat with Ananya (same agent, no voice). */
    const val AGENT_CHAT = "agent_chat"
    /** Guided room scan: a photo of each wall, then Ananya's read of the room. */
    const val ROOM_SCAN = "room_scan"

    fun liveCalling(calleeUid: String, calleeName: String, callType: String) =
        "live_calling/${java.net.URLEncoder.encode(calleeUid, "UTF-8")}/${java.net.URLEncoder.encode(calleeName, "UTF-8")}/$callType"
    fun liveInCall(channelName: String, peerName: String, callType: String) =
        "live_in_call/${java.net.URLEncoder.encode(channelName, "UTF-8")}/${java.net.URLEncoder.encode(peerName, "UTF-8")}/$callType"
    fun realExpertProfile(expertUid: String, expertName: String) =
        "real_expert_profile/${java.net.URLEncoder.encode(expertUid, "UTF-8")}/${java.net.URLEncoder.encode(expertName, "UTF-8")}"
}
