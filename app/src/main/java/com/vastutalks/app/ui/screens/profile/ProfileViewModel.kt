package com.vastutalks.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.vastutalks.app.data.model.CallHistoryEntry
import com.vastutalks.app.data.model.UserProfile
import com.vastutalks.app.data.repository.CallHistoryRepository
import com.vastutalks.app.data.repository.ProfilePhotos
import com.vastutalks.app.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val history: List<CallHistoryEntry> = emptyList(),
    val errorMessage: String? = null,
    /** users/{uid}: phone and photo (name/email come from Firebase Auth). */
    val profile: UserProfile? = null,
    /** A photo just picked, shown while it saves. */
    val pendingPhoto: android.graphics.Bitmap? = null
)

class ProfileViewModel(
    private val callHistoryRepository: CallHistoryRepository = CallHistoryRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    /** The Firebase "credential data" the Profile screen displays — name, email, uid, account age. */
    val currentUser: FirebaseUser? get() = auth.currentUser

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
        viewModelScope.launch {
            runCatching {
                userRepository.listenMyProfile().collect { _uiState.value = _uiState.value.copy(profile = it) }
            }
        }
    }

    fun updatePhoto(bitmap: android.graphics.Bitmap) {
        _uiState.value = _uiState.value.copy(pendingPhoto = bitmap)
        viewModelScope.launch {
            val encoded = withContext(Dispatchers.Default) { ProfilePhotos.encode(bitmap) }
            val result = userRepository.updatePhoto(encoded)
            _uiState.value = _uiState.value.copy(
                pendingPhoto = null,
                errorMessage = result.exceptionOrNull()?.let { "Couldn't save your photo: ${it.message}" } ?: _uiState.value.errorMessage
            )
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            callHistoryRepository.fetchHistory().fold(
                onSuccess = { list -> _uiState.value = _uiState.value.copy(isLoading = false, history = list) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Couldn't load call history."
                    )
                }
            )
        }
    }
}
