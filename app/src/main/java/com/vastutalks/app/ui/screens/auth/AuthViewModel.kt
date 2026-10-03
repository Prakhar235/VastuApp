package com.vastutalks.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.vastutalks.app.data.model.UserRole
import com.vastutalks.app.data.repository.AuthRepository
import com.vastutalks.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Shared by SignInScreen and CreateAccountScreen. One instance per screen
 * (Compose's default viewModel() scopes it to the NavHost back-stack entry),
 * so each screen gets its own isLoading/error state.
 */
class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Checked by the splash screen to skip Sign In when a session already exists. */
    fun isSignedIn(): Boolean = authRepository.currentUser != null

    fun signIn(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState(errorMessage = "Enter both email and password.")
            return
        }

        _uiState.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            val result = authRepository.signIn(email, password)
            result.fold(
                onSuccess = {
                    _uiState.value = AuthUiState(isLoading = false)
                    saveFcmTokenBestEffort()
                    onSuccess()
                },
                onFailure = { e ->
                    _uiState.value = AuthUiState(isLoading = false, errorMessage = e.message)
                }
            )
        }
    }

    fun signUp(fullName: String, email: String, password: String, role: UserRole, onSuccess: () -> Unit) {
        _uiState.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            val authResult = authRepository.signUp(email, password, fullName)
            val user = authResult.getOrNull()
            if (user == null) {
                _uiState.value = AuthUiState(isLoading = false, errorMessage = authResult.exceptionOrNull()?.message)
                return@launch
            }

            // The Firebase Auth account exists even if this profile write
            // fails, so don't block the user on it — just surface a message
            // if it happens. Worst case they land on Home with a default role.
            val profileResult = userRepository.createProfileForNewUser(user.uid, fullName, email, role)
            _uiState.value = AuthUiState(
                isLoading = false,
                errorMessage = profileResult.exceptionOrNull()?.let {
                    "Account created, but saving your profile failed: ${it.message}"
                }
            )
            saveFcmTokenBestEffort()
            onSuccess()
        }
    }

    /** Best-effort — a missed token save just means this device won't ring for incoming calls until the next sign-in. */
    private suspend fun saveFcmTokenBestEffort() {
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            userRepository.saveFcmToken(token)
        }
    }

    fun signOut() {
        authRepository.signOut()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
