package com.vastutalks.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.vastutalks.app.data.model.CallRequest
import com.vastutalks.app.data.model.ExpertProfile
import com.vastutalks.app.data.model.UserRole
import com.vastutalks.app.data.repository.CallSignalingRepository
import com.vastutalks.app.data.model.Post
import com.vastutalks.app.data.repository.ChatRepository
import com.vastutalks.app.data.repository.NotificationRepository
import com.vastutalks.app.data.repository.PostRepository
import com.vastutalks.app.data.repository.ProfilePhotos
import com.vastutalks.app.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val role: UserRole? = null, // null while loading
    val experts: List<ExpertProfile> = emptyList(),
    val expertsError: String? = null,
    val incomingCall: CallRequest? = null,
    /** Expert accounts: their own directory entry (for the online/offline switch). */
    val myExpertProfile: ExpertProfile? = null,
    /** A photo the expert just picked, shown straight away while it saves. */
    val pendingPhoto: android.graphics.Bitmap? = null,
    val photoError: String? = null,
    /** Normal users: every expert's posts, newest first. Experts: their own posts. */
    val posts: List<Post> = emptyList(),
    /**
     * Ticks every [CLOCK_TICK_MILLIS], so "online" (which depends on how long
     * ago an expert's app checked in) updates even when nothing in Firestore
     * changes.
     */
    val nowMillis: Long = System.currentTimeMillis(),
    /** Ids of posts the signed-in user has liked. */
    val likedPostIds: Set<String> = emptySet(),
    /** Unread notifications, for the badge on the bell. */
    val unreadNotifications: Int = 0,
    /** Unread chat messages across all chats, for the badge on the messages icon. */
    val unreadMessages: Long = 0L
)

private const val CLOCK_TICK_MILLIS = 30_000L

class HomeViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val callSignalingRepository: CallSignalingRepository = CallSignalingRepository(),
    private val postRepository: PostRepository = PostRepository(),
    private val notificationRepository: NotificationRepository = NotificationRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val role = userRepository.getCurrentUserRole().getOrDefault(UserRole.NORMAL_USER)
            _uiState.value = _uiState.value.copy(role = role)

            // Both roles can like posts and get notifications.
            launch {
                runCatching {
                    postRepository.listenMyLikes().collect { _uiState.value = _uiState.value.copy(likedPostIds = it) }
                }
            }
            launch {
                runCatching {
                    val chats = ChatRepository()
                    val me = chats.myUid.orEmpty()
                    chats.listenMyChats().collect { list ->
                        _uiState.value = _uiState.value.copy(unreadMessages = list.sumOf { it.unread[me] ?: 0L })
                    }
                }
            }
            launch {
                runCatching {
                    notificationRepository.listen().collect { list ->
                        _uiState.value = _uiState.value.copy(unreadNotifications = list.count { !it.read })
                    }
                }
            }

            if (role == UserRole.NORMAL_USER) {
                launch {
                    while (true) {
                        delay(CLOCK_TICK_MILLIS)
                        _uiState.value = _uiState.value.copy(nowMillis = System.currentTimeMillis())
                    }
                }
                launch {
                    runCatching {
                        postRepository.listenFeed().collect { _uiState.value = _uiState.value.copy(posts = it) }
                    }
                }
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
                    // Check in while the app is running, so users see this expert as online.
                    launch {
                        while (true) {
                            userRepository.heartbeat()
                            delay(ExpertProfile.HEARTBEAT_MILLIS)
                        }
                    }
                    launch {
                        runCatching {
                            postRepository.listenByAuthor(myUid).collect { _uiState.value = _uiState.value.copy(posts = it) }
                        }
                    }
                    launch {
                        runCatching {
                            userRepository.listenExpert(myUid).collect { me ->
                                _uiState.value = _uiState.value.copy(myExpertProfile = me)
                            }
                        }
                    }
                    launch {
                        callSignalingRepository.listenForIncomingCalls(myUid).collect { call ->
                            _uiState.value = _uiState.value.copy(incomingCall = call)
                        }
                    }
                }
            }
        }
    }

    /** Expert changed their photo on Home: save it to their profile and directory entry. */
    fun updatePhoto(bitmap: android.graphics.Bitmap) {
        _uiState.value = _uiState.value.copy(pendingPhoto = bitmap, photoError = null)
        viewModelScope.launch {
            val encoded = withContext(Dispatchers.Default) { ProfilePhotos.encode(bitmap) }
            val result = userRepository.updatePhoto(encoded)
            _uiState.value = _uiState.value.copy(
                pendingPhoto = null,
                photoError = result.exceptionOrNull()?.let { "Couldn't save your photo: ${it.message}" }
            )
        }
    }

    /** Saves the expert's edited details; [onDone] gets an error message, or null on success. */
    fun updateDetails(city: String, experienceYears: Int, bio: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val result = userRepository.updateExpertDetails(city.trim(), experienceYears, bio.trim())
            onDone(result.exceptionOrNull()?.let { "Couldn't save: ${it.message}" })
        }
    }

    fun toggleLike(postId: String) {
        val liked = postId in _uiState.value.likedPostIds
        viewModelScope.launch { postRepository.setLiked(postId, !liked) }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch { postRepository.delete(postId) }
    }

    fun setOnline(online: Boolean) {
        viewModelScope.launch { userRepository.setOnline(online) }
    }

    fun declineIncomingCall() {
        val call = _uiState.value.incomingCall ?: return
        viewModelScope.launch {
            callSignalingRepository.updateStatus(call.callId, com.vastutalks.app.data.model.CallRequestStatus.DECLINED)
        }
    }
}
