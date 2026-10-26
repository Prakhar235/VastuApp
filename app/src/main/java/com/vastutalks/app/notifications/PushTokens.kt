package com.vastutalks.app.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.vastutalks.app.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Keeps this phone's push token tied to whoever is signed in now. Another
 * account used here before may still hold the token in its users/{uid}
 * doc (we can't edit someone else's), so its calls and messages would keep
 * arriving here. When the signed-in account isn't the one the token was
 * last given to, the token is replaced: the old one stops working for
 * everyone, and only the new account gets the new one.
 */
object PushTokens {
    private const val PREFS = "push_tokens"
    private const val KEY_OWNER = "owner_uid"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    /** Watches sign-ins for the life of the app. */
    fun start(context: Context) {
        val app = context.applicationContext
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid ?: return@addAuthStateListener
            scope.launch { claimFor(app, uid) }
        }
    }

    private suspend fun claimFor(context: Context, uid: String) = lock.withLock {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_OWNER, null) == uid) return@withLock
        runCatching {
            val messaging = FirebaseMessaging.getInstance()
            messaging.deleteToken().await()
            val token = messaging.token.await()
            // Right after sign-up the users/{uid} doc may not be written yet: give it a moment.
            val users = UserRepository()
            var saved = users.saveFcmToken(token)
            repeat(5) {
                if (saved.isSuccess) return@repeat
                delay(3_000)
                saved = users.saveFcmToken(token)
            }
            saved.getOrThrow()
            prefs.edit().putString(KEY_OWNER, uid).apply()
        }.onFailure { Log.w("PushTokens", "Couldn't replace the push token", it) }
    }

    /** After signing out: the next account to sign in gets a fresh token. */
    fun forget(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_OWNER).apply()
    }
}
