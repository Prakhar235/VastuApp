package com.vastutalks.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.vastutalks.app.data.model.CallRequest
import com.vastutalks.app.data.model.ExpertProfile
import com.vastutalks.app.data.model.UserRole
import com.vastutalks.app.data.repository.CallSignalingRepository
import com.vastutalks.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val role: UserRole? = null, // null while loading
    val experts: List<ExpertProfile> = emptyList(),
    val expertsError: String? = null,
    val incomingCall: CallRequest? = null
)

class HomeViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val callSignalingRepository: CallSignalingRepository = CallSignalingRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val role = userRepository.getCurrentUserRole().getOrDefault(UserRole.NORMAL_USER)
            _uiState.value = _uiState.value.copy(role = role)

            if (role == UserRole.NORMAL_USER) {
                launch {
                    try {
                        userRepository.listenOnlineExperts().collect { experts ->
                            _uiState.value = _uiState.value.copy(experts = experts, expertsError = null)
                        }
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(expertsError = e.message ?: "Couldn't load the expert directory.")
                    }
                }
            } else {
                val myUid = auth.currentUser?.uid
                if (myUid != null) {
                    launch {
                        callSignalingRepository.listenForIncomingCalls(myUid).collect { call ->
                            _uiState.value = _uiState.value.copy(incomingCall = call)
                        }
                    }
                }
            }
        }
    }

    fun declineIncomingCall() {
        val call = _uiState.value.incomingCall ?: return
        viewModelScope.launch {
            callSignalingRepository.updateStatus(call.callId, com.vastutalks.app.data.model.CallRequestStatus.DECLINED)
        }
    }
}
