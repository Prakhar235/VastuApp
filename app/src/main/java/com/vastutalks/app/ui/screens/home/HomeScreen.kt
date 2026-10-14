package com.vastutalks.app.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vastutalks.app.data.call.AgoraCallManager
import com.vastutalks.app.data.call.CallConnectionState
import com.vastutalks.app.data.call.TokenRepository
import com.vastutalks.app.ui.components.ProfileAvatar
import com.vastutalks.app.ui.components.ProfilePhotoEditor
import com.vastutalks.app.ui.components.PostCard
import com.vastutalks.app.ui.components.VastuTextField
import com.vastutalks.app.data.model.CallRequest
import com.vastutalks.app.data.model.CallRequestStatus
import com.vastutalks.app.data.model.AnanyaAgent
import com.vastutalks.app.data.model.CallPricing
import com.vastutalks.app.data.model.Post
import com.vastutalks.app.data.model.sortedForDirectory
import com.vastutalks.app.data.model.CallType
import com.vastutalks.app.data.model.ExpertProfile
import com.vastutalks.app.data.model.UserRole
import com.vastutalks.app.data.model.expertDisplayExtrasFor
import com.vastutalks.app.data.repository.CallSignalingRepository
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.OnlineGreen
import com.vastutalks.app.ui.theme.Success
import com.vastutalks.app.ui.theme.SurfaceLight
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuPrimary
import com.vastutalks.app.ui.theme.VastuPrimaryDark
import com.vastutalks.app.ui.theme.VastuSaffron
import com.vastutalks.app.ui.theme.WalletGold
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    onCallExpert: (expertId: String, expertName: String, callType: CallType) -> Unit = { _, _, _ -> },
    onAcceptIncomingCall: (channelName: String, callType: CallType, peerName: String) -> Unit = { _, _, _ -> },
    onExpertCardClick: (expertId: String, expertName: String) -> Unit = { _, _ -> },
    onCallAgent: () -> Unit = {},
    onChatAgent: () -> Unit = {},
    onScanRoom: () -> Unit = {},
    onCreatePost: () -> Unit = {},
    onOpenPost: (postId: String) -> Unit = {},
    onChatExpert: (expertId: String, expertName: String) -> Unit = { _, _ -> },
    onOpenChats: () -> Unit = {},
    homeViewModel: HomeViewModel = viewModel()
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* no-op: if denied, calls and post activity just won't show a system notification (still in the app's list) */ }

    // Everyone gets notifications now: experts for calls, likes and comments;
    // users when an expert replies to their comment.
    LaunchedEffect(uiState.role) {
        if (uiState.role != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceLight)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onProfileClick) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Color(0xFF2B2B2B))
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (uiState.role == UserRole.VASTU_EXPERT) "Vastu Experts" else "Vastu Experts",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = VastuPrimary
                )
                Text(
                    if (uiState.role == UserRole.VASTU_EXPERT) "Your expert dashboard" else "Connect with certified consultants",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Box {
                IconButton(onClick = onOpenChats) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = "Messages", tint = VastuPrimary)
                }
                if (uiState.unreadMessages > 0) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-2).dp, y = 2.dp)
                            .clip(CircleShape)
                            .background(Danger),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (uiState.unreadMessages > 9) "9+" else "${uiState.unreadMessages}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = onNotificationsClick) {
                    Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = VastuPrimary)
                }
                if (uiState.unreadNotifications > 0) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-2).dp, y = 2.dp)
                            .clip(CircleShape)
                            .background(Danger),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (uiState.unreadNotifications > 9) "9+" else "${uiState.unreadNotifications}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        when (uiState.role) {
            null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VastuPrimary)
            }
            UserRole.VASTU_EXPERT -> ExpertDashboard(
                incomingCall = uiState.incomingCall,
                me = uiState.myExpertProfile,
                pendingPhoto = uiState.pendingPhoto,
                photoError = uiState.photoError,
                onPhotoPicked = homeViewModel::updatePhoto,
                onSaveDetails = homeViewModel::updateDetails,
                myPosts = uiState.posts,
                likedPostIds = uiState.likedPostIds,
                onLike = homeViewModel::toggleLike,
                onOpenPost = onOpenPost,
                onCreatePost = onCreatePost,
                onDeletePost = homeViewModel::deletePost,
                isOnline = uiState.myExpertProfile?.isOnline ?: true,
                onOnlineChange = homeViewModel::setOnline,
                onAccepted = { channelName, callType, peerName -> onAcceptIncomingCall(channelName, callType, peerName) },
                onDecline = { homeViewModel.declineIncomingCall() }
            )
            UserRole.NORMAL_USER -> NormalUserHome(
                liveExperts = uiState.experts.sortedForDirectory(uiState.nowMillis),
                now = uiState.nowMillis,
                posts = uiState.posts,
                likedPostIds = uiState.likedPostIds,
                onLike = homeViewModel::toggleLike,
                onOpenPost = onOpenPost,
                expertsError = uiState.expertsError,
                onSearchClick = onSearchClick,
                onCallExpert = onCallExpert,
                onExpertCardClick = onExpertCardClick,
                onCallAgent = onCallAgent,
                onChatAgent = onChatAgent,
                onScanRoom = onScanRoom,
                onChatExpert = onChatExpert
            )
        }
    }
}

@Composable
private fun ExpertDashboard(
    incomingCall: CallRequest?,
    me: ExpertProfile?,
    pendingPhoto: android.graphics.Bitmap?,
    photoError: String?,
    onPhotoPicked: (android.graphics.Bitmap) -> Unit,
    onSaveDetails: (city: String, experienceYears: Int, bio: String, onDone: (String?) -> Unit) -> Unit,
    myPosts: List<Post>,
    likedPostIds: Set<String>,
    onLike: (String) -> Unit,
    onOpenPost: (String) -> Unit,
    onCreatePost: () -> Unit,
    onDeletePost: (String) -> Unit,
    isOnline: Boolean,
    onOnlineChange: (Boolean) -> Unit,
    onAccepted: (channelName: String, callType: CallType, peerName: String) -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val signalingRepository = remember { CallSignalingRepository() }
    val tokenRepository = remember { TokenRepository() }

    var isAccepting by remember { mutableStateOf(false) }
    var acceptError by remember { mutableStateOf<String?>(null) }
    var acceptedCall by remember { mutableStateOf<CallRequest?>(null) }

    val connectionState by AgoraCallManager.connectionState.collectAsState()
    LaunchedEffect(connectionState) {
        val call = acceptedCall
        if (connectionState == CallConnectionState.JOINED && call != null) {
            val callType = runCatching { CallType.valueOf(call.callType) }.getOrDefault(CallType.AUDIO)
            onAccepted(call.channelName, callType, call.callerName)
            acceptedCall = null
            isAccepting = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        // The expert as users see them; tap the photo to change it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfilePhotoEditor(
                photo = me?.photo.orEmpty(),
                name = me?.name.orEmpty(),
                size = 64.dp,
                picked = pendingPhoto,
                onPicked = onPhotoPicked
            )
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(me?.name.orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(me?.displaySpecialty ?: "", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VastuPrimary)
                Text(
                    when {
                        pendingPhoto != null -> "Saving your photo…"
                        photoError != null -> photoError
                        me?.photo.isNullOrBlank() -> "Tap the photo to add one — users see it when choosing an expert"
                        else -> "Tap the photo to change it"
                    },
                    fontSize = 12.sp,
                    color = if (photoError != null) Danger else TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (me != null) {
            Box(modifier = Modifier.height(8.dp))
            ExpertDetailsCard(me, onSave = onSaveDetails)
        }
        Box(modifier = Modifier.height(12.dp))

        // Availability switch: shows users whether the expert is around. Offline
        // experts can still be called — their phone rings through a push.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (isOnline) OnlineGreen else TextSecondary))
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    if (isOnline) "You're online" else "You're offline",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2B2B2B)
                )
                Text(
                    if (isOnline) "Users can call you for audio and video consultations" else "Users can still call you — your phone will ring",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Switch(
                checked = isOnline,
                onCheckedChange = onOnlineChange,
                colors = SwitchDefaults.colors(checkedTrackColor = OnlineGreen)
            )
        }
        Box(modifier = Modifier.height(16.dp))

        if (incomingCall != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        if (incomingCall.callType == CallType.VIDEO.name) "Incoming video call" else "Incoming audio call",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Text(incomingCall.callerName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
                    if (acceptError != null) {
                        Box(modifier = Modifier.height(8.dp))
                        Text(acceptError ?: "", fontSize = 12.sp, color = Danger)
                    }
                    Box(modifier = Modifier.height(16.dp))
                    if (isAccepting) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VastuPrimary, modifier = Modifier.size(28.dp))
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Danger)
                                    .clickable {
                                        coroutineScope.launch { signalingRepository.updateStatus(incomingCall.callId, CallRequestStatus.DECLINED) }
                                        onDecline()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Decline", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Success)
                                    .clickable {
                                        isAccepting = true
                                        acceptError = null
                                        coroutineScope.launch {
                                            val statusResult = signalingRepository.updateStatus(incomingCall.callId, CallRequestStatus.ACCEPTED)
                                            if (statusResult.isFailure) {
                                                acceptError = statusResult.exceptionOrNull()?.message ?: "Couldn't accept the call."
                                                isAccepting = false
                                                return@launch
                                            }
                                            val tokenResult = tokenRepository.fetchOrCreateExpertToken(incomingCall.calleeUid, incomingCall.channelName)
                                            tokenResult.fold(
                                                onSuccess = { token ->
                                                    acceptedCall = incomingCall
                                                    val isVideo = runCatching { CallType.valueOf(incomingCall.callType) }.getOrDefault(CallType.AUDIO) == CallType.VIDEO
                                                    AgoraCallManager.joinChannelWithToken(context, incomingCall.channelName, token, isVideo)
                                                },
                                                onFailure = { e ->
                                                    acceptError = e.message ?: "Couldn't fetch a call token. Is the Cloud Function deployed?"
                                                    isAccepting = false
                                                }
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Accept", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Box(modifier = Modifier.height(20.dp))
        }

        // The expert's posts, shown in every user's Posts tab.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Your posts", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B), modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(VastuPrimary)
                    .clickable { onCreatePost() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(" New post", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Box(modifier = Modifier.height(12.dp))
        if (myPosts.isEmpty()) {
            Text(
                "Share Vastu tips and updates — your posts appear in every user's Posts tab.",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            myPosts.forEach { post ->
                PostCard(
                    post,
                    liked = post.id in likedPostIds,
                    onLikeClick = { onLike(post.id) },
                    onOpen = { onOpenPost(post.id) },
                    onDelete = { onDeletePost(post.id) },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
        Box(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun NormalUserHome(
    liveExperts: List<ExpertProfile>,
    now: Long,
    posts: List<Post>,
    likedPostIds: Set<String>,
    onLike: (String) -> Unit,
    onOpenPost: (String) -> Unit,
    expertsError: String?,
    onSearchClick: () -> Unit,
    onCallExpert: (expertId: String, expertName: String, callType: CallType) -> Unit,
    onExpertCardClick: (expertId: String, expertName: String) -> Unit,
    onCallAgent: () -> Unit,
    onChatAgent: () -> Unit,
    onScanRoom: () -> Unit,
    onChatExpert: (expertId: String, expertName: String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(HomeTab.EXPERTS) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .clickable { onSearchClick() }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Box(modifier = Modifier.padding(start = 10.dp)) {
                Text("Search by name or specialty", color = TextSecondary, fontSize = 14.sp)
            }
        }

        Box(modifier = Modifier.height(16.dp))

        // Experts / Posts tab switcher
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(4.dp)
        ) {
            HomeTabChip(
                label = "Experts",
                selected = selectedTab == HomeTab.EXPERTS,
                modifier = Modifier.weight(1f)
            ) { selectedTab = HomeTab.EXPERTS }
            HomeTabChip(
                label = "Posts",
                selected = selectedTab == HomeTab.POSTS,
                modifier = Modifier.weight(1f)
            ) { selectedTab = HomeTab.POSTS }
        }

        Box(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            HomeTab.EXPERTS -> ExpertsTabContent(liveExperts, now, expertsError, onCallExpert, onExpertCardClick, onCallAgent, onChatAgent, onScanRoom, onChatExpert)
            HomeTab.POSTS -> PostsFeed(
                posts,
                likedPostIds = likedPostIds,
                onLike = onLike,
                onOpenPost = onOpenPost,
                onAuthorClick = { post -> onExpertCardClick(post.authorUid, post.authorName) }
            )
        }
    }
}

private enum class HomeTab { EXPERTS, POSTS }

/** Ananya, the AI Vastu expert, with her two ways in: chat and call. */
@Composable
private fun AnanyaCard(onChat: () -> Unit, onCall: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VastuPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = VastuPrimary, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Ananya — AI Vastu expert", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2B2B2B))
                Text("Available anytime · English or Hindi · share room photos", fontSize = 11.sp, color = TextSecondary)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CallChip(Icons.AutoMirrored.Outlined.Chat, "Chat", enabled = true, modifier = Modifier.weight(1f), onClick = onChat)
            if (AnanyaAgent.CALL_ENABLED) {
                CallChip(Icons.Filled.Call, "Call", enabled = true, modifier = Modifier.weight(1f), onClick = onCall)
            }
        }
    }
}

@Composable
private fun AgentCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(VastuPrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = VastuPrimary, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2B2B2B))
            Text(subtitle, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun HomeTabChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) VastuPrimary else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFF6B6878),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ExpertsTabContent(
    liveExperts: List<ExpertProfile>,
    now: Long,
    expertsError: String?,
    onCallExpert: (expertId: String, expertName: String, callType: CallType) -> Unit,
    onExpertCardClick: (expertId: String, expertName: String) -> Unit,
    onCallAgent: () -> Unit,
    onChatAgent: () -> Unit,
    onScanRoom: () -> Unit,
    onChatExpert: (expertId: String, expertName: String) -> Unit
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(listOf(VastuPrimary, VastuPrimaryDark)))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Box(modifier = Modifier.width(14.dp))
                Column {
                    Text("Top Rated Consultants", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${liveExperts.count { it.isPresentAt(now) }} online now", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
            }
        }

        Box(modifier = Modifier.height(12.dp))

        // Ananya, the AI Vastu agent — always available, no human
        // expert needs to be online. Chat or call her (the call can
        // switch between audio and video).
        AnanyaCard(onChat = onChatAgent, onCall = onCallAgent)
        Box(modifier = Modifier.height(10.dp))
        AgentCard(
            icon = Icons.Outlined.Explore,
            title = "Scan a room — Vastu check",
            subtitle = "Photograph each wall with the compass; get a score and what to fix, marked on your photos",
            onClick = onScanRoom
        )

        Box(modifier = Modifier.height(20.dp))

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (expertsError != null) {
                item {
                    Text(
                        "Couldn't load experts: $expertsError",
                        fontSize = 13.sp,
                        color = Danger,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else if (liveExperts.isEmpty()) {
                item {
                    Text(
                        "No experts have joined yet — check back soon.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else {
                items(liveExperts) { expert ->
                    LiveExpertCard(
                        expert = expert,
                        now = now,
                        onCardClick = { onExpertCardClick(expert.uid, expert.name) },
                        onCall = { type -> onCallExpert(expert.uid, expert.name, type) },
                        onChat = { onChatExpert(expert.uid, expert.name) }
                    )
                }
            }
        }
    }
}

/** Every expert's posts, newest first; tapping an author opens their profile. */
@Composable
private fun PostsFeed(
    posts: List<Post>,
    likedPostIds: Set<String>,
    onLike: (String) -> Unit,
    onOpenPost: (String) -> Unit,
    onAuthorClick: (Post) -> Unit
) {
    if (posts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "No posts yet. Tips and updates from Vastu experts will show up here.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(posts, key = { it.id }) { post ->
            PostCard(
                post,
                liked = post.id in likedPostIds,
                onLikeClick = { onLike(post.id) },
                onOpen = { onOpenPost(post.id) },
                onAuthorClick = { onAuthorClick(post) }
            )
        }
    }
}

@Composable
private fun LiveExpertCard(
    expert: ExpertProfile,
    now: Long,
    onCardClick: () -> Unit,
    onCall: (CallType) -> Unit,
    onChat: () -> Unit
) {
    val extras = remember(expert.uid) { expertDisplayExtrasFor(expert.uid) } // only for the hidden price

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .clickable { onCardClick() }
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Box {
                    ProfileAvatar(expert.photo, expert.name, size = 64.dp, shape = RoundedCornerShape(16.dp))
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(2.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(expertStatusColor(expert, now)))
                    }
                }

                Box(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(expert.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF2B2B2B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(expert.displaySpecialty, color = VastuPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (expert.experienceYears > 0 || expert.city.isNotBlank()) {
                        Box(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (expert.experienceYears > 0) InfoPill(icon = Icons.Filled.WorkspacePremium, text = "${expert.experienceYears} yrs")
                            if (expert.city.isNotBlank()) InfoPill(icon = Icons.Filled.LocationOn, text = expert.city, tint = Danger)
                        }
                    }
                }
            }

            Box(modifier = Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SurfaceLight))
            Box(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(expertStatusLabel(expert, now), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = expertStatusColor(expert, now))
                Text(
                    "  ·  " + if (CallPricing.PAID_CALLS) "₹${extras.pricePerSession}/session" else "${expert.consultationsLabel} · Free",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(modifier = Modifier.height(10.dp))
            // The three ways to reach an expert. All work while they're offline
            // (a call rings their phone through a push); only a call they're
            // already on blocks a new one.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CallChip(Icons.AutoMirrored.Outlined.Chat, "Chat", enabled = true, modifier = Modifier.weight(1f), onClick = onChat)
                CallChip(Icons.Filled.Call, "Audio", enabled = !expert.isBusyAt(now), modifier = Modifier.weight(1f)) { onCall(CallType.AUDIO) }
                CallChip(Icons.Filled.Videocam, "Video", enabled = !expert.isBusyAt(now), modifier = Modifier.weight(1f)) { onCall(CallType.VIDEO) }
            }
        }
    }
}

/**
 * What users see on the expert's card and profile — city, experience and
 * About — with an Edit button that opens [EditExpertDetailsDialog].
 */
@Composable
private fun ExpertDetailsCard(
    me: ExpertProfile,
    onSave: (city: String, experienceYears: Int, bio: String, onDone: (String?) -> Unit) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                InfoPill(icon = Icons.Filled.WorkspacePremium, text = if (me.experienceYears > 0) "${me.experienceYears} yrs" else "Experience not set")
                InfoPill(icon = Icons.Filled.LocationOn, text = me.city.ifBlank { "City not set" }, tint = Danger)
            }
            Text(
                "Edit",
                color = VastuPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { editing = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Text(
            me.bio.ifBlank { "Add a short introduction so users know how you can help." },
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 19.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
    if (editing) {
        EditExpertDetailsDialog(me, onDismiss = { editing = false }, onSave = onSave)
    }
}

@Composable
private fun EditExpertDetailsDialog(
    me: ExpertProfile,
    onDismiss: () -> Unit,
    onSave: (city: String, experienceYears: Int, bio: String, onDone: (String?) -> Unit) -> Unit
) {
    var city by remember { mutableStateOf(me.city) }
    var experience by remember { mutableStateOf(if (me.experienceYears > 0) me.experienceYears.toString() else "") }
    var bio by remember { mutableStateOf(me.bio) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val canSave = city.isNotBlank() && experience.toIntOrNull() != null && bio.isNotBlank() && !saving

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = Color.White,
        title = { Text("Edit your details", fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("City", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B), modifier = Modifier.padding(bottom = 6.dp))
                VastuTextField(value = city, onValueChange = { city = it }, placeholder = "e.g. Mumbai", leadingIcon = Icons.Filled.LocationOn)
                Text("Years of Experience", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B), modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                VastuTextField(
                    value = experience,
                    onValueChange = { experience = it.filter(Char::isDigit).take(2) },
                    placeholder = "e.g. 10",
                    leadingIcon = Icons.Filled.WorkspacePremium,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
                Text("About You", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B), modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                VastuTextField(
                    value = bio,
                    onValueChange = { bio = it.take(400) },
                    placeholder = "Your experience, approach and what you help clients with",
                    leadingIcon = Icons.Filled.Info,
                    minLines = 4
                )
                error?.let { Text(it, color = Danger, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    saving = true
                    error = null
                    onSave(city, experience.toInt(), bio) { failure ->
                        saving = false
                        if (failure == null) onDismiss() else error = failure
                    }
                }
            ) { Text(if (saving) "Saving…" else "Save", color = if (canSave) VastuPrimary else TextSecondary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

internal fun expertStatusLabel(expert: ExpertProfile, now: Long): String = when {
    !expert.isPresentAt(now) -> "Offline"
    expert.isBusyAt(now) -> "On another call"
    else -> "Online now"
}

internal fun expertStatusColor(expert: ExpertProfile, now: Long): Color = when {
    !expert.isPresentAt(now) -> TextSecondary
    expert.isBusyAt(now) -> WalletGold
    else -> OnlineGreen
}

/** "Chat" / "Audio" / "Video" button on an expert card; greyed out when they can't be reached that way. */
@Composable
private fun CallChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) VastuPrimary else SurfaceLight)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Color.White else TextSecondary, modifier = Modifier.size(15.dp))
        Text(" $label", color = if (enabled) Color.White else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun InfoPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color = VastuPrimary) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceLight)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
            Text(" $text", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
        }
    }
}
