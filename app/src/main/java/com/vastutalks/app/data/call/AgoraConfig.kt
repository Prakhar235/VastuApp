package com.vastutalks.app.data.call

/**
 * Fill in APP_ID with the value from your Agora Console project
 * (console.agora.io -> Project Management -> your project -> App ID).
 * The App ID is safe to keep here (not a secret) — the Agora App
 * Certificate is a different value and must never go in the app; see
 * /functions/index.js's generateAgoraToken Cloud Function for where
 * that lives instead.
 *
 * See README.md's "Wiring up Agora calling" section for the full
 * walkthrough.
 */
object AgoraConfig {
    const val APP_ID: String = "6245c3b5f0ce469aa17aeae3377901a9"

    /**
     * Optional shortcut for testing without deploying the Cloud
     * Function: skips Firestore's expertTokens cache entirely. Key =
     * expert uid (find it in Firestore Console -> experts collection
     * -> document ID), value = a temp token you generated in Agora
     * Console for channel name "expert-<that-uid>".
     *
     * Like any Agora temp token, these expire ~24h after you generate
     * them — when a call starts failing with an "invalid or expired
     * token" error, just generate a fresh one and rebuild the app.
     *
     * Example:
     *   val HARDCODED_EXPERT_TOKENS: Map<String, String> = mapOf(
     *       "AbCd1234exampleUid" to "007eJx..."
     *   )
     */
    val HARDCODED_EXPERT_TOKENS: Map<String, String> = mapOf(
        "1AdALcqpscOJc6oHkJXMp2uKhyG3" to "007eJxTYJib43F+Rd2UGE7HgutthW8tdojcVEmYv/RwrpAa1zfOnbEKDGZGJqbJxkmmaQbJqSZmlomJhuaJqYmpxsbm5pYGhomWp5RDsxoCGRnWO3kwMzJAIIivzJBaUZBaVKJr6Jji6JNcWFCc7O+VbJbvke0V4VtgVOqdUeluzMAAAHGKKMo=",
        "IqjzQ3qjRDcW9MNkdCD0oeoFaMa2" to "007eJxTYDi3q9hiR5T3d7FZPirOUW0PbGfxrf12U79aY7uprH8w2xYFBjMjE9Nk4yTTNIPkVBMzy8REQ/PE1MRUY2Nzc0sDw0RLXZXQrIZARoZfEyazMjJAIIivzJBaUZBaVKLrWZhVFWhcmBXkkhxu6euXneLsYpCfmu+W6JtoxMAAAEM/KHM=",
    )
}
