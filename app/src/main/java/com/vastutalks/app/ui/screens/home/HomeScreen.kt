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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.vastutalks.app.ui.components.VastuIllustration
import com.vastutalks.app.ui.components.VastuIllustrationType
import com.vastutalks.app.ui.components.illustrationTypeFor
import com.vastutalks.app.data.model.CallRequest
import com.vastutalks.app.data.model.CallRequestStatus
import com.vastutalks.app.data.model.CallType
import com.vastutalks.app.data.model.ExpertProfile
import com.vastutalks.app.data.model.UserRole
import com.vastutalks.app.data.model.VastuPosts
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
    onTryDemoCall: () -> Unit = {},
    homeViewModel: HomeViewModel = viewModel()
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* no-op: if denied, incoming calls just won't post a system notification */ }

    LaunchedEffect(uiState.role) {
        if (uiState.role == UserRole.VASTU_EXPERT &&
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
                IconButton(onClick = onNotificationsClick) {
                    Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = VastuPrimary)
                }
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = 6.dp)
                        .clip(CircleShape)
                        .background(Danger)
                )
            }
        }

        when (uiState.role) {
            null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VastuPrimary)
            }
            UserRole.VASTU_EXPERT -> ExpertDashboard(
                incomingCall = uiState.incomingCall,
                onAccepted = { channelName, callType, peerName -> onAcceptIncomingCall(channelName, callType, peerName) },
                onDecline = { homeViewModel.declineIncomingCall() }
            )
            UserRole.NORMAL_USER -> NormalUserHome(
                liveExperts = uiState.experts,
                expertsError = uiState.expertsError,
                onSearchClick = onSearchClick,
                onCallExpert = onCallExpert,
                onExpertCardClick = onExpertCardClick,
                onTryDemoCall = onTryDemoCall
            )
        }
    }
}

@Composable
private fun ExpertDashboard(
    incomingCall: CallRequest?,
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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        if (incomingCall != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(20.dp)
            ) {
                Column {
                    Text("Incoming call", fontSize = 13.sp, color = TextSecondary)
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

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.SupportAgent,
                    contentDescription = null,
                    tint = VastuPrimary.copy(alpha = 0.4f),
                    modifier = Modifier.size(56.dp)
                )
                Box(modifier = Modifier.height(16.dp))
                Text(
                    "You're listed as a Vastu Expert",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2B2B2B)
                )
                Box(modifier = Modifier.height(6.dp))
                Text(
                    "Normal users can find and call you. Keep the app open to receive calls in real time — incoming calls show up right here.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
}

@Composable
private fun NormalUserHome(
    liveExperts: List<ExpertProfile>,
    expertsError: String?,
    onSearchClick: () -> Unit,
    onCallExpert: (expertId: String, expertName: String, callType: CallType) -> Unit,
    onExpertCardClick: (expertId: String, expertName: String) -> Unit,
    onTryDemoCall: () -> Unit
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
            HomeTab.EXPERTS -> ExpertsTabContent(liveExperts, expertsError, onCallExpert, onExpertCardClick, onTryDemoCall)
            HomeTab.POSTS -> PostsFeed(liveExperts.map { it.name })
        }
    }
}

private enum class HomeTab { EXPERTS, POSTS }

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
    expertsError: String?,
    onCallExpert: (expertId: String, expertName: String, callType: CallType) -> Unit,
    onExpertCardClick: (expertId: String, expertName: String) -> Unit,
    onTryDemoCall: () -> Unit
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
                    Text("${liveExperts.size} online now", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
            }
        }

        Box(modifier = Modifier.height(12.dp))

        // Always-available, fully self-contained demo — no real
        // expert/second device needed to see the calling UX work.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .clickable { onTryDemoCall() }
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
                Icon(Icons.Filled.Videocam, contentDescription = null, tint = VastuPrimary, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Try a Demo Call", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2B2B2B))
                Text("See the calling experience — no setup needed", fontSize = 11.sp, color = TextSecondary)
            }
        }

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
                        "No experts online right now — check back soon.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else {
                items(liveExperts) { expert ->
                    LiveExpertCard(
                        expert = expert,
                        onCardClick = { onExpertCardClick(expert.uid, expert.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PostsFeed(expertNames: List<String>) {
    // Recomputed only when the set of expert names actually changes,
    // not on every recomposition — otherwise authors/likes/timestamps
    // would visibly reshuffle while scrolling.
    val posts = remember(expertNames) {
        val authorPool = expertNames.ifEmpty { listOf("Vastu Talks Expert") }
        val random = kotlin.random.Random(seed = expertNames.hashCode().toLong())
        VastuPosts.texts.map { text ->
            GeneratedPost(
                authorName = authorPool[random.nextInt(authorPool.size)],
                text = text,
                likeCount = random.nextInt(4, 240),
                timeAgo = listOf("1h ago", "3h ago", "5h ago", "Yesterday", "2d ago", "3d ago", "1w ago").random(random),
                // Matched to keywords in the post text (compass/directions,
                // fire/kitchen, water, plants, mirrors, etc.) rather than a
                // stock photo — see VastuIllustrations.kt for the actual
                // hand-drawn, animated Compose Canvas art.
                illustrationType = illustrationTypeFor(text)
            )
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(posts) { post ->
            PostCard(post)
        }
    }
}

private data class GeneratedPost(
    val authorName: String,
    val text: String,
    val likeCount: Int,
    val timeAgo: String,
    val illustrationType: VastuIllustrationType
)

@Composable
private fun PostCard(post: GeneratedPost) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(VastuPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = post.authorName.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString(""),
                        color = VastuPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
                }
                Box(modifier = Modifier.width(10.dp))
                Column {
                    Text(post.authorName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2B2B2B))
                    Text(post.timeAgo, fontSize = 11.sp, color = TextSecondary)
                }
            }

            VastuIllustration(
                type = post.illustrationType,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )

            Column(modifier = Modifier.padding(16.dp)) {
                Text(post.text, fontSize = 13.sp, color = Color(0xFF2B2B2B), lineHeight = 19.sp)
                Box(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Danger, modifier = Modifier.size(14.dp))
                    Text(" ${post.likeCount}", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun LiveExpertCard(
    expert: ExpertProfile,
    onCardClick: () -> Unit
) {
    val extras = remember(expert.uid) { expertDisplayExtrasFor(expert.uid) }

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
                    // Real per-expert photos aren't collected at signup yet —
                    // Pravatar serves free, realistic-looking placeholder
                    // headshots (not actual people, no copyright concern),
                    // seeded per-expert so the same one always shows.
                    AsyncImage(
                        model = "https://i.pravatar.cc/300?u=${extras.avatarSeed}-${expert.uid}",
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(VastuPrimary.copy(alpha = 0.08f))
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(2.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(OnlineGreen))
                    }
                }

                Box(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(expert.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF2B2B2B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(extras.specialty, color = VastuPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Box(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoPill(icon = Icons.Filled.WorkspacePremium, text = "${extras.experienceYears} yrs")
                        InfoPill(icon = Icons.Filled.LocationOn, text = extras.location, tint = Danger)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(WalletGold)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Text(" ${extras.rating}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Box(modifier = Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SurfaceLight))
            Box(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${extras.reviewCount} reviews", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                Text("₹${extras.pricePerSession}", color = VastuPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("/session", color = TextSecondary, fontSize = 12.sp)
            }
        }
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
