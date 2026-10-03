package com.vastutalks.app.data.model

/**
 * A scripted, always-available "expert" for demoing the call flow
 * end-to-end on a single device — no second account, no Firestore
 * signaling, no Agora token needed. Picks up automatically after a
 * short "ringing" delay. Not a real person, not a real AI voice/video
 * integration — just a convincing static stand-in so the calling ->
 * connected UX can be shown reliably every time.
 */
object DemoExpert {
    const val ID = "demo-expert"
    const val NAME = "Ananya Verma"
    const val SPECIALTY = "Residential Vastu"
    const val AVATAR_SEED = "demo-expert-ananya"
    const val RATING = 4.9
    const val PRICE_PER_SESSION = 1200
}
