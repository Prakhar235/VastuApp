package com.vastutalks.app.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val SIGN_IN = "sign_in"
    const val CREATE_ACCOUNT = "create_account"
    const val HOME = "home"
    const val PROFILE = "profile"

    // Real (Firestore-signaled) calling flow between a normal user and a signed-up expert.
    const val LIVE_CALLING = "live_calling/{calleeUid}/{calleeName}/{callType}"
    const val LIVE_IN_CALL = "live_in_call/{channelName}/{peerName}/{callType}"
    const val REAL_EXPERT_PROFILE = "real_expert_profile/{expertUid}/{expertName}"

    // Fully self-contained demo call — no Firestore signaling, no Agora
    // token, no second device. See DemoExpert.kt / DemoCallingScreen.kt.
    const val DEMO_CALLING = "demo_calling"
    const val DEMO_IN_CALL = "demo_in_call"

    fun liveCalling(calleeUid: String, calleeName: String, callType: String) =
        "live_calling/${java.net.URLEncoder.encode(calleeUid, "UTF-8")}/${java.net.URLEncoder.encode(calleeName, "UTF-8")}/$callType"
    fun liveInCall(channelName: String, peerName: String, callType: String) =
        "live_in_call/${java.net.URLEncoder.encode(channelName, "UTF-8")}/${java.net.URLEncoder.encode(peerName, "UTF-8")}/$callType"
    fun realExpertProfile(expertUid: String, expertName: String) =
        "real_expert_profile/${java.net.URLEncoder.encode(expertUid, "UTF-8")}/${java.net.URLEncoder.encode(expertName, "UTF-8")}"
}
