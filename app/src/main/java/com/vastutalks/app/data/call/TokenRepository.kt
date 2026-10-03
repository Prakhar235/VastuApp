package com.vastutalks.app.data.call

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * Calls the "generateAgoraToken" Cloud Function (see /functions in the
 * project root) instead of minting tokens on-device. The function
 * holds the Agora App Certificate as a secret; this class never sees
 * it. Requires the Cloud Function to actually be deployed — see
 * README's "Deploying the token Cloud Function" section.
 */
class TokenRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    /** Buffer before the token's real expiry so we never hand out one that's about to die mid-call. */
    private val expiryBufferMillis = 5 * 60 * 1000L

    suspend fun fetchToken(channelName: String, uid: Int): Result<String> {
        return try {
            val data = hashMapOf(
                "channelName" to channelName,
                "uid" to uid
            )
            val result = functions
                .getHttpsCallable("generateAgoraToken")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val response = result.getData() as? Map<String, Any?>
            val token = response?.get("token") as? String

            if (token.isNullOrBlank()) {
                Result.failure(IllegalStateException("Token function returned no token."))
            } else {
                Result.success(token)
            }
        } catch (e: FirebaseFunctionsException) {
            Result.failure(Exception("Token function error (${e.code}): ${e.message}", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reuses a cached token for this expert's fixed channel if it's
     * still valid for the next 5+ minutes; otherwise mints a fresh
     * one (valid ~23h) and caches it in expertTokens/{expertUid} so
     * the NEXT call to this expert today doesn't need a Cloud
     * Function round-trip at all. Uses uid=0 (Agora "wildcard" uid),
     * so the same token works for the caller and the callee — Agora
     * doesn't authenticate individual uids when you pass 0.
     */
    suspend fun fetchOrCreateExpertToken(expertUid: String, channelName: String): Result<String> {
        AgoraConfig.HARDCODED_EXPERT_TOKENS[expertUid]?.let { hardcodedToken ->
            return Result.success(hardcodedToken)
        }

        val docRef = firestore.collection("expertTokens").document(expertUid)

        return try {
            val existing = docRef.get().await()
            val cachedToken = existing.getString("token")
            val cachedChannel = existing.getString("channelName")
            val expiresAt = existing.getLong("expiresAtMillis") ?: 0L

            if (cachedToken != null && cachedChannel == channelName && expiresAt - System.currentTimeMillis() > expiryBufferMillis) {
                return Result.success(cachedToken)
            }

            val fresh = fetchToken(channelName, 0)
            if (fresh.isFailure) {
                // Most likely cause if you're not using the Cloud Function:
                // no manually-added document at expertTokens/{expertUid} yet,
                // or its channelName doesn't exactly match. See README's
                // "Manually adding a token (no Cloud Function)" section.
                return Result.failure(
                    Exception(
                        "No valid token found for this expert. Add one manually at " +
                            "expertTokens/$expertUid in Firebase Console (see README), " +
                            "or deploy the generateAgoraToken Cloud Function.",
                        fresh.exceptionOrNull()
                    )
                )
            }
            fresh.onSuccess { token ->
                docRef.set(
                    hashMapOf(
                        "token" to token,
                        "channelName" to channelName,
                        // Matches TOKEN_EXPIRATION_SECONDS in functions/index.js (~23h) —
                        // if you change that, update this too.
                        "expiresAtMillis" to System.currentTimeMillis() + 23 * 60 * 60 * 1000L
                    )
                ).await()
            }
            fresh
        } catch (e: Exception) {
            // Cache read/write failed — fall back to minting a token directly rather than failing the call outright.
            fetchToken(channelName, 0)
        }
    }
}
