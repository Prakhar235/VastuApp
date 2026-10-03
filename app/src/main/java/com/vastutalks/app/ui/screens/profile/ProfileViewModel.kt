package com.vastutalks.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.vastutalks.app.data.model.CallHistoryEntry
import com.vastutalks.app.data.repository.CallHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val history: List<CallHistoryEntry> = emptyList(),
    val errorMessage: String? = null
)

class ProfileViewModel(
    private val callHistoryRepository: CallHistoryRepository = CallHistoryRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    /** The Firebase "credential data" the Profile screen displays — name, email, uid, account age. */
    val currentUser: FirebaseUser? get() = auth.currentUser

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            callHistoryRepository.fetchHistory().fold(
                onSuccess = { list -> _uiState.value = ProfileUiState(isLoading = false, history = list) },
                onFailure = { e ->
                    _uiState.value = ProfileUiState(
                        isLoading = false,
                        errorMessage = e.message ?: "Couldn't load call history."
                    )
                }
            )
        }
    }
}
