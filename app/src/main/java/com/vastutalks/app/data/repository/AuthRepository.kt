package com.vastutalks.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around FirebaseAuth's email/password APIs.
 *
 * Kept deliberately small and framework-free (no Compose/ViewModel imports)
 * so it's easy to unit test or swap out later.
 */
class AuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    /** Null when nobody is signed in. */
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: return Result.failure(IllegalStateException("Sign-in returned no user"))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(mapAuthError(e))
        }
    }

    suspend fun signUp(email: String, password: String, displayName: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: return Result.failure(IllegalStateException("Sign-up returned no user"))

            // Best-effort — if setting the display name fails, we still treat
            // account creation as successful since the auth account exists.
            runCatching {
                val profileUpdate = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build()
                user.updateProfile(profileUpdate).await()
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(mapAuthError(e))
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }

    /** Turns Firebase's exception types into short, user-facing messages. */
    private fun mapAuthError(e: Exception): Exception {
        val message = when (e) {
            is FirebaseAuthInvalidUserException -> "No account found with that email."
            is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
            is FirebaseAuthUserCollisionException -> "An account with that email already exists."
            is FirebaseAuthWeakPasswordException -> "Password is too weak. Use at least 6 characters."
            else -> e.message ?: "Something went wrong. Please try again."
        }
        return Exception(message, e)
    }
}
