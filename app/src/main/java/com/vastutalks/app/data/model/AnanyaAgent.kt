package com.vastutalks.app.data.model

/**
 * Profile of Ananya, VastuTalks' always-available AI Vastu agent
 * (VastuAgent gives her a mind, AgentVoice her voice). No second
 * account, Firestore signaling or Agora token is needed to call her;
 * she picks up after a short ring.
 */
object AnanyaAgent {
    const val ID = "ai-agent-ananya"
    const val NAME = "Ananya Verma"
    const val SPECIALTY = "Residential Vastu"
    /** Seeds her pravatar.cc picture; changing it changes her face. */
    const val AVATAR_SEED = "demo-expert-ananya"
    const val RATING = 4.9
    const val PRICE_PER_SESSION = 1200
}
