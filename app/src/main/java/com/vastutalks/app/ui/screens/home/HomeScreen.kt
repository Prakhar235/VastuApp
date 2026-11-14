package com.vastutalks.app.ui.screens.home

import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Videocam
import com.vastutalks.app.data.model.SampleShop
import com.vastutalks.app.ui.components.DesignToast
import com.vastutalks.app.ui.theme.StarGold
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.ui.text.withStyle
import com.vastutalks.app.ui.components.AiOrb
import com.vastutalks.app.ui.components.DesignCard
import com.vastutalks.app.ui.components.OutlineButton
import com.vastutalks.app.ui.components.PrimaryButton
import com.vastutalks.app.ui.components.SquareIconButton
import com.vastutalks.app.ui.components.UnderlineTabs
import com.vastutalks.app.ui.theme.CardBorder
import com.vastutalks.app.ui.theme.Poppins
import com.vastutalks.app.ui.theme.TextBody
import com.vastutalks.app.ui.theme.TextHint
import com.vastutalks.app.ui.theme.TextPrimary
import com.vastutalks.app.ui.theme.VastuCoral
import com.vastutalks.app.ui.theme.VastuPrimarySoft
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.runtime.DisposableEffect
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
import com.vastutalks.app.call.IncomingCallAlerts
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
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    onCallExpert: (expertId: String, expertName: String, callType: CallType) -> Unit = { _, _, _ -> },
    onAcceptIncomingCall: (channelName: String, callType: CallType, peerName: String) -> Unit = { _, _, _ -> },
    onExpertCardClick: (expertId: String, expertName: String) -> Unit = { _, _ -> },
    onCallAgent: () -> Unit = {},
    onChatAgent: () -> Unit = {},
    onScanHome: () -> Unit = {},
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

    var showNotifications by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceLight)
            .navigationBarsPadding()
    ) {
        // Top bar: menu (profile), greeting, wallet (or messages for experts) and notifications.
        val firstName = remember {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName
                ?.trim()?.substringBefore(' ')?.ifBlank { null } ?: "there"
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SquareIconButton(Icons.Filled.Menu, "Menu", onProfileClick)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Namaste,", fontSize = 11.5.sp, color = TextSecondary)
                Text(
                    firstName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (uiState.role == UserRole.NORMAL_USER) {
                // Wallet balance pill (sample wallet, see SampleShop) — opens Profile.
                Row(
                    Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White)
                        .border(1.dp, CardBorder, RoundedCornerShape(999.dp))
                        .clickable(onClick = onProfileClick)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AccountBalanceWallet, null, tint = VastuPrimary, modifier = Modifier.size(15.dp))
                    Text(" " + SampleShop.balanceText, color = VastuPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                // Experts reply to users from their chat list.
                Box {
                    SquareIconButton(Icons.AutoMirrored.Outlined.Chat, "Messages", onOpenChats)
                    if (uiState.unreadMessages > 0) CountBadge(uiState.unreadMessages, Modifier.align(Alignment.TopEnd))
                }
            }
            Box(Modifier.width(8.dp))
            SquareIconButton(
                Icons.Outlined.Notifications, "Notifications", { showNotifications = true },
                badge = uiState.unreadNotifications > 0
            )
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
                onCallExpert = onCallExpert,
                onExpertCardClick = onExpertCardClick,
                onScanHome = onScanHome,
                onChatAgent = onChatAgent,
                onChatExpert = onChatExpert
            )
        }
    }
    if (showNotifications) {
        com.vastutalks.app.ui.screens.post.NotificationsSheet(onDismiss = { showNotifications = false }, onOpenPost = onOpenPost)
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

    // While this screen is in front it shows and rings for incoming calls itself,
    // so the push notification doesn't ring as well (see IncomingCallAlerts).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> IncomingCallAlerts.inAppRingerVisible = true
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> IncomingCallAlerts.inAppRingerVisible = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            IncomingCallAlerts.inAppRingerVisible = false
            IncomingCallAlerts.stopRinging(context)
        }
    }
    // One ringer: the card rings while it's waiting for an answer, and takes over
    // from the push notification if that arrived first.
    val ringingCallId = incomingCall?.callId?.takeIf { !isAccepting }
    LaunchedEffect(ringingCallId) {
        if (ringingCallId != null) {
            IncomingCallAlerts.cancelNotification(context, ringingCallId)
            IncomingCallAlerts.startRinging(context)
        } else {
            IncomingCallAlerts.stopRinging(context)
        }
    }

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
    onCallExpert: (expertId: String, expertName: String, callType: CallType) -> Unit,
    onExpertCardClick: (expertId: String, expertName: String) -> Unit,
    onScanHome: () -> Unit,
    onChatAgent: () -> Unit,
    onChatExpert: (expertId: String, expertName: String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(HomeTab.EXPERTS) }
    var category by remember { mutableStateOf(HOME_CATEGORIES.first()) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(1800); toast = null } }

    val shownExperts = liveExperts.filter { category.matches(it) }
    // "Top rated & most loved": the six best by (placeholder) rating, then reviews.
    val topRated = remember(liveExperts) {
        liveExperts.sortedWith(
            compareByDescending<ExpertProfile> { expertDisplayExtrasFor(it.uid).rating }
                .thenByDescending { expertDisplayExtrasFor(it.uid).reviewCount }
        ).take(6)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { AnayraCard(onScan = onScanHome, onChat = onChatAgent) }
            item {
                UnderlineTabs(
                    labels = listOf("Experts", "Posts"),
                    selected = selectedTab.ordinal,
                    onSelect = { selectedTab = HomeTab.entries[it] },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)
                )
            }

            when (selectedTab) {
                HomeTab.EXPERTS -> {
                    if (topRated.isNotEmpty()) {
                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Top rated & most loved", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
                                StatusLegend()
                            }
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                items(topRated, key = { it.uid }) { expert ->
                                    TopRatedItem(expert, now) { onExpertCardClick(expert.uid, expert.name) }
                                }
                            }
                        }
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(HOME_CATEGORIES) { c -> FilterChip(c.label, c == category) { category = c } }
                        }
                    }
                    when {
                        expertsError != null -> item {
                            Text("Couldn't load experts: $expertsError", fontSize = 12.5.sp, color = Danger, modifier = Modifier.padding(20.dp))
                        }
                        shownExperts.isEmpty() -> item {
                            Text(
                                if (liveExperts.isNotEmpty()) "No experts match your search." else "No experts have joined yet. Check back soon.",
                                fontSize = 12.5.sp,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 30.dp)
                            )
                        }
                        else -> items(shownExperts, key = { it.uid }) { expert ->
                            ExpertRow(
                                expert = expert,
                                now = now,
                                onOpen = { onExpertCardClick(expert.uid, expert.name) },
                                onCall = { onCallExpert(expert.uid, expert.name, CallType.VIDEO) },
                                onChat = { onChatExpert(expert.uid, expert.name) }
                            )
                        }
                    }
                }
                HomeTab.POSTS -> {
                    if (posts.isEmpty()) {
                        item {
                            Text(
                                "No posts yet. Tips and updates from Vastu experts will show up here.",
                                fontSize = 12.5.sp,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(30.dp)
                            )
                        }
                    } else {
                        item { Box(Modifier.height(2.dp)) }
                        items(posts, key = { it.id }) { post ->
                            PostCard(
                                post,
                                liked = post.id in likedPostIds,
                                onLikeClick = { onLike(post.id) },
                                onOpen = { onOpenPost(post.id) },
                                onAuthorClick = { onExpertCardClick(post.authorUid, post.authorName) },
                                following = post.authorUid in SampleShop.following,
                                onFollowClick = { SampleShop.toggleFollow(post.authorUid) },
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)
                            )
                        }
                    }
                }
            }
        }
        toast?.let { DesignToast(it, Modifier.align(Alignment.TopCenter).padding(top = 14.dp)) }
    }
}

private enum class HomeTab { EXPERTS, POSTS }

/** The design's category chips, matched against the expert's specialty. */
private data class HomeCategory(val label: String, val keywords: List<String>) {
    fun matches(e: ExpertProfile): Boolean =
        keywords.isEmpty() || keywords.any { it in e.specialty.lowercase() }
}

private val HOME_CATEGORIES = listOf(
    HomeCategory("All", emptyList()),
    HomeCategory("Home", listOf("residential", "home", "interior", "feng")),
    HomeCategory("Office", listOf("commercial", "office", "industrial")),
    HomeCategory("Astrology", listOf("astrology", "spiritual")),
    HomeCategory("Land", listOf("plot", "land", "traditional", "temple")),
)

/** Likes shown beside the rating: "0" until calls are rated, "1.6k" from a thousand up. */
internal fun likesLabel(likes: Int): String =
    if (likes < 1000) "$likes" else String.format(java.util.Locale.US, "%.1fk", likes / 1000.0)

/** Round photo with status dot, first name, then ★ rating and ♥ likes. */
@Composable
private fun TopRatedItem(expert: ExpertProfile, now: Long, onOpen: () -> Unit) {
    val extras = remember(expert.uid) { expertDisplayExtrasFor(expert.uid) }
    Column(
        Modifier
            .width(64.dp)
            .clickable(onClick = onOpen),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(60.dp)) {
            ProfileAvatar(expert.photo, expert.name, size = 60.dp, shape = CircleShape)
            StatusDot(directoryDotColor(expert, now), Modifier.align(Alignment.BottomEnd).offset((-2).dp, (-2).dp), size = 12.dp)
        }
        Text(
            expert.name.removePrefix("Dr. ").substringBefore(' '),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, null, tint = StarGold, modifier = Modifier.size(9.dp))
            Text(" ${extras.ratingLabel}", fontSize = 10.sp, color = TextSecondary, maxLines = 1)
            Box(Modifier.width(6.dp))
            Icon(Icons.Filled.Favorite, null, tint = Color(0xFFF87171), modifier = Modifier.size(9.dp))
            Text(" ${likesLabel(extras.likes)}", fontSize = 10.sp, color = TextSecondary, maxLines = 1)
        }
    }
}

/** Anayra, the AI Vastu guide: scan the home, or ask her in chat. */
@Composable
private fun AnayraCard(onScan: () -> Unit, onChat: () -> Unit) {
    DesignCard(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp),
        radius = 20.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AiOrb(48.dp, ringWidth = 2.5.dp, iconSize = 22.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(AnanyaAgent.NAME, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        "AI",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = VastuPrimary,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(VastuPrimarySoft)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        append("Your Vastu guide · ")
                        withStyle(androidx.compose.ui.text.SpanStyle(color = Success, fontWeight = FontWeight.SemiBold)) { append("10 min free") }
                    },
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Scan home", onScan, Modifier.weight(1f), icon = Icons.Outlined.CropFree, height = 44.dp, radius = 12.dp, fontSize = 12.5.sp, iconSize = 15.dp)
            OutlineButton(
                "Ask ${AnanyaAgent.NAME}", onChat, Modifier.weight(1f),
                icon = Icons.AutoMirrored.Outlined.Chat, height = 44.dp, borderColor = CardBorder, radius = 12.dp, fontSize = 12.5.sp, iconSize = 15.dp
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) Color.White else TextBody,
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) TextPrimary else Color(0xFFEEEEF4))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun StatusLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("Online" to OnlineGreen, "Offline" to TextHint).forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                Text(" $label", fontSize = 10.5.sp, color = TextHint)
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 13.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(SurfaceLight)
            .padding(2.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun CountBadge(count: Long, modifier: Modifier = Modifier) {
    Box(
        modifier
            .offset(x = 4.dp, y = (-4).dp)
            .size(18.dp)
            .clip(CircleShape)
            .background(Color.White)
            .padding(1.5.dp)
            .clip(CircleShape)
            .background(VastuCoral),
        contentAlignment = Alignment.Center
    ) {
        Text(if (count > 9) "9+" else "$count", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

/** One expert in the directory list: photo with status, name, specialty, ★ rating · rate, video call and chat. */
@Composable
private fun ExpertRow(
    expert: ExpertProfile,
    now: Long,
    onOpen: () -> Unit,
    onCall: () -> Unit,
    onChat: () -> Unit
) {
    val extras = remember(expert.uid) { expertDisplayExtrasFor(expert.uid) }
    val canCall = !expert.isBusyAt(now)
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onOpen() }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(52.dp)) {
                ProfileAvatar(expert.photo, expert.name, size = 52.dp, shape = RoundedCornerShape(14.dp))
                StatusDot(directoryDotColor(expert, now), Modifier.align(Alignment.BottomEnd).offset(2.dp, 2.dp), size = 12.dp)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(expert.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    expert.displaySpecialty,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = StarGold, modifier = Modifier.size(11.dp))
                    Text(" ${extras.ratingLabel}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("  ·  ", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        if (CallPricing.PAID_CALLS) SampleShop.rupees(extras.pricePerSession) else "Free",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }
            // Both work while they're offline (a call rings their phone through a
            // push); only a call they're already on blocks a new one.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, CardBorder, CircleShape)
                        .clickable(enabled = canCall) { onCall() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Videocam, "Video call", tint = if (canCall) TextBody else TextHint, modifier = Modifier.size(16.dp))
                }
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(VastuPrimary)
                        .clickable { onChat() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, "Chat", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F0F5)))
    }
}

/** The design's two-state dot: green online, grey offline (busy counts as online). */
private fun directoryDotColor(expert: ExpertProfile, now: Long): Color =
    if (expert.isPresentAt(now)) OnlineGreen else TextHint

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
            Text(" $text", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
        }
    }
}
