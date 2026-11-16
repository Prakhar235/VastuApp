package com.vastutalks.app.ui.screens.expert

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vastutalks.app.call.IncomingCallAlerts
import com.vastutalks.app.data.call.AgoraCallManager
import com.vastutalks.app.data.call.CallConnectionState
import com.vastutalks.app.data.call.TokenRepository
import com.vastutalks.app.data.model.CallHistoryEntry
import com.vastutalks.app.data.model.CallRequest
import com.vastutalks.app.data.model.CallRequestStatus
import com.vastutalks.app.data.model.CallType
import com.vastutalks.app.data.model.ExpertProfile
import com.vastutalks.app.data.model.Post
import com.vastutalks.app.data.model.SampleShop
import com.vastutalks.app.data.repository.CallSignalingRepository
import com.vastutalks.app.data.repository.ProfilePhotos
import com.vastutalks.app.ui.components.ProfileAvatar
import com.vastutalks.app.ui.components.ProfilePhotoEditor
import com.vastutalks.app.ui.screens.auth.PartnerGold
import com.vastutalks.app.ui.screens.auth.PartnerHeroGradient
import com.vastutalks.app.ui.screens.home.EditExpertDetailsDialog
import com.vastutalks.app.ui.screens.home.articleDraftTitle
import com.vastutalks.app.ui.screens.home.hasArticleDraft
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.TextPrimary
import com.vastutalks.app.ui.theme.VastuPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

// The Partner app's palette (design: "Expert App").
private val PageBg = Color(0xFFF6F5FB)
private val Slate = Color(0xFF64748B)
private val Body = Color(0xFF4A5568)
private val Hairline = Color(0xFFF1F0F6)
private val CardShadow = Color(0x591E1650)

/** How long a caller's phone keeps ringing before it gives up (see LiveCallingScreen). */
private const val RING_SECONDS = 45

private enum class ExpertTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    SESSIONS("Sessions", Icons.AutoMirrored.Outlined.EventNote),
    EARNINGS("Earnings", Icons.Outlined.AccountBalanceWallet),
    PROFILE("Profile", Icons.Outlined.Person)
}

/**
 * The design's Partner app for a signed-in Vastu expert: Home (online
 * switch, today's numbers, articles), Sessions (consultation history),
 * Earnings and Profile, with a sheet for a client's incoming call.
 *
 * Everything shown is real: calls are free for now ([com.vastutalks.app.data.model.CallPricing]),
 * so earnings read ₹0, and there's no rating system or scheduling yet, so
 * those show "New" and an empty Upcoming list instead of sample numbers.
 */
@Composable
fun ExpertAppScreen(
    me: ExpertProfile?,
    posts: List<Post>,
    history: List<CallHistoryEntry>,
    incomingCall: CallRequest?,
    unreadNotifications: Int,
    unreadMessages: Long,
    pendingPhoto: android.graphics.Bitmap?,
    photoError: String?,
    onPhotoPicked: (android.graphics.Bitmap) -> Unit,
    onSaveDetails: (city: String, experienceYears: Int, bio: String, onDone: (String?) -> Unit) -> Unit,
    onOnlineChange: (Boolean) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenChats: () -> Unit,
    onWriteArticle: () -> Unit,
    onOpenPost: (String) -> Unit,
    onAccepted: (channelName: String, callType: CallType, peerName: String) -> Unit,
    onDecline: () -> Unit,
    onSignOut: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(ExpertTab.HOME) }
    var sessionsTab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(PageBg)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                when (tab) {
                    ExpertTab.HOME -> HomeTab(
                        me = me,
                        posts = posts,
                        history = history,
                        unreadNotifications = unreadNotifications,
                        unreadMessages = unreadMessages,
                        onOnlineChange = onOnlineChange,
                        onOpenNotifications = onOpenNotifications,
                        onOpenChats = onOpenChats,
                        onWriteArticle = onWriteArticle,
                        onOpenPost = onOpenPost,
                        onSeeAll = { sessionsTab = 1; tab = ExpertTab.SESSIONS }
                    )
                    ExpertTab.SESSIONS -> SessionsTab(history, sessionsTab, onSelect = { sessionsTab = it })
                    ExpertTab.EARNINGS -> EarningsTab(history)
                    ExpertTab.PROFILE -> ProfileTab(
                        me = me,
                        pendingPhoto = pendingPhoto,
                        photoError = photoError,
                        onPhotoPicked = onPhotoPicked,
                        onEdit = { editing = true },
                        onOpenChats = onOpenChats,
                        onOpenNotifications = onOpenNotifications,
                        onSignOut = onSignOut
                    )
                }
            }
            BottomNav(tab, onPick = { tab = it })
        }

        if (incomingCall != null) {
            RequestSheet(incomingCall, onAccepted = onAccepted, onDecline = onDecline)
        }
    }

    if (editing && me != null) {
        EditExpertDetailsDialog(me, onDismiss = { editing = false }, onSave = onSaveDetails)
    }
}

// ---------------------------------------------------------------- Home

@Composable
private fun HomeTab(
    me: ExpertProfile?,
    posts: List<Post>,
    history: List<CallHistoryEntry>,
    unreadNotifications: Int,
    unreadMessages: Long,
    onOnlineChange: (Boolean) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenChats: () -> Unit,
    onWriteArticle: () -> Unit,
    onOpenPost: (String) -> Unit,
    onSeeAll: () -> Unit
) {
    val online = me?.isOnline ?: true
    val today = remember(history) { history.filter { isToday(it.startTimeMillis) } }

    // Dark hero with greeting, bell and the online switch.
    Box(Modifier.fillMaxWidth().background(PartnerHeroGradient)) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 50.dp, y = (-70).dp).size(200.dp).blur(80.dp).clip(CircleShape).background(Color(0x47F59E0B)))
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 70.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpertBadgeAvatar(me, 46.dp, 15.dp, 15.sp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(greeting(), fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.75f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            me?.name.orEmpty(),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(Icons.Filled.Verified, "Verified", tint = Color(0xFFFFD93D), modifier = Modifier.padding(start = 5.dp).size(15.dp))
                    }
                }
                HeroIconButton(Icons.AutoMirrored.Outlined.Chat, "Messages", badge = unreadMessages > 0, onClick = onOpenChats)
                Spacer(Modifier.width(8.dp))
                HeroIconButton(Icons.Outlined.Notifications, "Notifications", badge = unreadNotifications > 0, onClick = onOpenNotifications)
            }
            OnlineSwitchCard(online, onToggle = { onOnlineChange(!online) })
        }
    }

    // 2×2 numbers, overlapping the hero.
    Column(
        Modifier.padding(horizontal = 16.dp).offset(y = (-48).dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Icons.Outlined.AccountBalanceWallet, "Earned today", SampleShop.rupees(today.sumOf { it.cost }), Color(0xFFFEF3C7), Color(0xFFB45309))
            StatCard(Icons.AutoMirrored.Outlined.Chat, "Sessions today", today.size.toString(), Color(0xFFEEEAFE), Color(0xFF5B4BD6))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Icons.Outlined.Star, "Rating", "New", Color(0xFFFFF7ED), Color(0xFFEA580C))
            StatCard(Icons.Outlined.Schedule, "Consultations", (me?.consultationsCompleted ?: 0L).toString(), Color(0xFFECFDF5), Color(0xFF15803D))
        }
    }

    Column(
        Modifier.offset(y = (-28).dp).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        WriteArticleCard(likes = posts.sumOf { it.likeCount }, onWrite = onWriteArticle)

        SectionHeader("Recent consultations", action = if (history.isNotEmpty()) "See all" else null, onAction = onSeeAll)
        WhiteCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            if (history.isEmpty()) {
                EmptyLine("No consultations yet. Stay online — clients can call you any time.")
            } else {
                history.take(3).forEachIndexed { i, h -> HistoryRow(h, divider = i > 0) }
            }
        }

        val context = LocalContext.current
        val draftTitle = remember(posts) { if (hasArticleDraft(context)) articleDraftTitle(context).ifBlank { "Untitled draft" } else null }
        val count = posts.size + if (draftTitle != null) 1 else 0
        SectionHeader("Your articles", trailing = if (count == 1) "1 article" else "$count articles")
        WhiteCard(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            if (draftTitle != null) {
                ArticleRow(draftTitle, null, "Draft", Color(0xFFF1F5F9), Color(0xFF475569), "Tap to continue", divider = false, onClick = onWriteArticle)
            }
            posts.forEachIndexed { i, p ->
                ArticleRow(
                    title = p.title.ifBlank { p.text.lineSequence().firstOrNull().orEmpty() },
                    image = p.image,
                    status = "Published",
                    statusBg = Color(0xFFECFDF5),
                    statusFg = Color(0xFF15803D),
                    meta = listOf(p.category, "♥ ${p.likeCount}", "${p.commentCount} comments").filter { it.isNotBlank() }.joinToString(" · "),
                    divider = i > 0 || draftTitle != null,
                    onClick = { onOpenPost(p.id) }
                )
            }
            if (count == 0) EmptyLine("Nothing published yet. Your articles appear on your profile and in the Explore feed.")
        }
    }
}

@Composable
private fun OnlineSwitchCard(online: Boolean, onToggle: () -> Unit) {
    val knob by animateDpAsState(if (online) 20.dp else 0.dp, label = "knob")
    Row(
        Modifier
            .padding(top = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .clickable(onClickLabel = if (online) "Go offline" else "Go online", onClick = onToggle)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (online) Color(0x404ADE80) else Color(0x3394A3B8))
                .padding(4.dp)
                .clip(CircleShape)
                .background(if (online) Color(0xFF4ADE80) else Color(0xFF94A3B8))
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(if (online) "You’re online" else "You’re offline", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(
                if (online) "Clients can send you chat & video requests" else "Clients can still call you — your phone will ring",
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f)
            )
        }
        Box(
            Modifier
                .size(width = 46.dp, height = 26.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (online) Color(0xFF22C55E) else Color.White.copy(alpha = 0.25f))
                .padding(3.dp)
        ) {
            Box(Modifier.offset(x = knob).size(20.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun RowScope.StatCard(icon: ImageVector, label: String, value: String, bg: Color, fg: Color) {
    Column(
        Modifier
            .weight(1f)
            .shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = CardShadow, spotColor = CardShadow)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(label, fontSize = 11.sp, color = Slate, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
private fun WriteArticleCard(likes: Long, onWrite: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFF3F0FF), Color(0xFFFDF4FF))))
            .border(1.dp, Color(0xFFE9E3FF), RoundedCornerShape(20.dp))
    ) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-30).dp).size(120.dp).blur(40.dp).clip(CircleShape).background(Color(0x73C4B5FD)))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFFA855F7)))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Edit, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Share your Vastu knowledge", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Articles appear on your profile and in the Explore feed.", fontSize = 11.5.sp, color = Body, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(VastuPrimary)
                        .clickable(onClick = onWrite),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Write article", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Column(Modifier.padding(start = 14.dp, end = 4.dp), horizontalAlignment = Alignment.End) {
                    Text(likes.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("likes on your articles", fontSize = 11.sp, color = Body)
                }
            }
        }
    }
}

@Composable
private fun ArticleRow(
    title: String,
    image: String?,
    status: String,
    statusBg: Color,
    statusFg: Color,
    meta: String,
    divider: Boolean,
    onClick: () -> Unit
) {
    val cover = remember(image) {
        image?.let { ProfilePhotos.decode(it) }?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEEEAFE)),
                contentAlignment = Alignment.Center
            ) {
                if (cover != null) Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.AutoMirrored.Outlined.Article, null, tint = Color(0xFF5B4BD6), modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, lineHeight = 17.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        status, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = statusFg,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(statusBg).padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                    Text(meta, fontSize = 10.5.sp, color = Slate, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Sessions

@Composable
private fun SessionsTab(history: List<CallHistoryEntry>, selected: Int, onSelect: (Int) -> Unit) {
    Column(
        Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Consultations", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Segmented(listOf("Upcoming · 0", "Completed"), selected, onSelect)
        if (selected == 0) {
            WhiteCard(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFEEEAFE)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.CalendarMonth, null, tint = Color(0xFF5B4BD6), modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("No scheduled consultations", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Clients reach you instantly by call or chat while you’re online.", fontSize = 11.5.sp, color = Slate, lineHeight = 16.sp)
                    }
                }
            }
        } else {
            WhiteCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                if (history.isEmpty()) EmptyLine("Your finished calls will show here.")
                else history.forEachIndexed { i, h -> HistoryRow(h, divider = i > 0) }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: CallHistoryEntry, divider: Boolean) {
    val (bg, fg) = tintFor(entry.expertName)
    val video = entry.callType == CallType.VIDEO.name
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
                Text(initials(entry.expertName), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = fg)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(entry.expertName.ifBlank { "Client" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${if (video) "Video call" else "Audio call"} · ${durationLabel(entry.durationSeconds)} · ${dateLabel(entry.startTimeMillis)}",
                    fontSize = 11.sp, color = Slate, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                if (entry.cost > 0) SampleShop.rupees(entry.cost) else "Free",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A)
            )
        }
    }
}

// ---------------------------------------------------------------- Earnings

@Composable
private fun EarningsTab(history: List<CallHistoryEntry>) {
    val weekStart = remember { startOfWeek() }
    val week = remember(history) {
        (0 until 7).map { d ->
            val from = weekStart + d * DAY_MILLIS
            history.filter { it.startTimeMillis in from until from + DAY_MILLIS }.sumOf { it.cost }
        }
    }
    val weekTotal = week.sum()
    val thisWeek = history.filter { it.startTimeMillis >= weekStart }
    val chat = thisWeek.filter { it.callType != CallType.VIDEO.name }.sumOf { it.cost }
    val video = thisWeek.filter { it.callType == CallType.VIDEO.name }.sumOf { it.cost }
    val balance = 0 // no payments yet: nothing has been earned to withdraw
    val context = LocalContext.current

    Column(
        Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Earnings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(PartnerHeroGradient)) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-60).dp).size(170.dp).blur(70.dp).clip(CircleShape).background(Color(0x59F59E0B)))
            Column(Modifier.padding(20.dp)) {
                Text("Available to withdraw", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.75f))
                Text(SampleShop.rupees(balance), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 4.dp))
                Text(
                    "Consultations are free for clients right now. Payouts start once paid sessions go live.",
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f), lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .alpha(if (balance > 0) 1f else 0.5f)
                        .clip(RoundedCornerShape(13.dp))
                        .background(PartnerGold)
                        .clickable {
                            android.widget.Toast.makeText(context, "Nothing to withdraw yet", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) { Text("Withdraw", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }
            }
        }

        WhiteCard(Modifier.padding(16.dp)) {
            Text("This week", fontSize = 11.5.sp, color = Slate)
            Text(SampleShop.rupees(weekTotal), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(top = 2.dp))
            WeekBars(week, todayIndex = ((System.currentTimeMillis() - weekStart) / DAY_MILLIS).toInt().coerceIn(0, 6))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallFigure("Chat & audio", SampleShop.rupees(chat))
            SmallFigure("Video", SampleShop.rupees(video))
        }

        Text("Payouts", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(top = 4.dp))
        WhiteCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AccountBalance, null, tint = Color(0xFF475569), modifier = Modifier.size(18.dp))
                }
                Text("No payouts yet", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun WeekBars(values: List<Int>, todayIndex: Int) {
    val max = maxOf(values.maxOrNull() ?: 0, 1)
    Row(
        Modifier.padding(top = 16.dp).fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, d ->
            val today = i == todayIndex
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height((96f * values[i] / max).coerceAtLeast(4f).dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(
                            when {
                                today -> PartnerGold
                                values[i] > 0 -> Brush.linearGradient(listOf(Color(0xFFC7BFF5), Color(0xFFC7BFF5)))
                                else -> Brush.linearGradient(listOf(Color(0xFFEEEDF4), Color(0xFFEEEDF4)))
                            }
                        )
                )
                Text(
                    d, fontSize = 10.5.sp,
                    fontWeight = if (today) FontWeight.Bold else FontWeight.Medium,
                    color = if (today) TextPrimary else Slate,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun RowScope.SmallFigure(label: String, value: String) {
    Column(
        Modifier
            .weight(1f)
            .shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = CardShadow, spotColor = CardShadow)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(14.dp)
    ) {
        Text(label, fontSize = 11.sp, color = Slate)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(top = 3.dp))
    }
}

// ---------------------------------------------------------------- Profile

@Composable
private fun ProfileTab(
    me: ExpertProfile?,
    pendingPhoto: android.graphics.Bitmap?,
    photoError: String?,
    onPhotoPicked: (android.graphics.Bitmap) -> Unit,
    onEdit: () -> Unit,
    onOpenChats: () -> Unit,
    onOpenNotifications: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = CardShadow, spotColor = CardShadow)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfilePhotoEditor(
                photo = me?.photo.orEmpty(),
                name = me?.name.orEmpty(),
                size = 62.dp,
                picked = pendingPhoto,
                onPicked = onPhotoPicked,
                shape = RoundedCornerShape(20.dp)
            )
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(me?.name.orEmpty(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(me?.displaySpecialty, me?.experienceYears?.takeIf { it > 0 }?.let { "$it yrs" }).joinToString(" · "),
                    fontSize = 11.5.sp, color = Slate, modifier = Modifier.padding(top = 2.dp)
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("★ ${me?.consultationsLabel ?: "New"}", Color(0xFFFEF3C7), Color(0xFF92400E))
                    Pill("Verified", Color(0xFFECFDF5), Color(0xFF15803D))
                }
            }
        }
        Text(
            when {
                pendingPhoto != null -> "Saving your photo…"
                photoError != null -> photoError
                else -> "Expert ID VT-${me?.uid?.take(6)?.uppercase().orEmpty()}"
            },
            fontSize = 11.5.sp,
            color = if (photoError != null) Danger else Slate
        )

        SectionHeader("Services & rates", modifier = Modifier.padding(top = 6.dp))
        WhiteCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            ServiceRow(Icons.AutoMirrored.Outlined.Chat, "Chat consultations", divider = false)
            ServiceRow(Icons.Outlined.Videocam, "Video & audio consultations", divider = true)
        }

        SectionHeader("About you", action = "Edit", onAction = onEdit, modifier = Modifier.padding(top = 6.dp))
        WhiteCard(Modifier.padding(16.dp)) {
            Text(
                me?.city?.ifBlank { null } ?: "City not set",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
            )
            Text(
                me?.bio?.ifBlank { null } ?: "Add a short introduction so clients know how you can help.",
                fontSize = 12.5.sp, color = Body, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }

        WhiteCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            MenuRow("Edit profile details", divider = false, onClick = onEdit)
            MenuRow("Messages", divider = true, onClick = onOpenChats)
            MenuRow("Notifications", divider = true, onClick = onOpenNotifications)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(14.dp))
                .clickable(onClick = onSignOut),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Log out", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626))
        }
    }
}

@Composable
private fun ServiceRow(icon: ImageVector, label: String, divider: Boolean) {
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFEEEAFE)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = VastuPrimary, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("Free for clients for now", fontSize = 11.sp, color = Slate)
            }
            Pill("Active", Color(0xFFECFDF5), Color(0xFF15803D))
        }
    }
}

@Composable
private fun MenuRow(label: String, divider: Boolean, onClick: () -> Unit) {
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
        }
    }
}

// ---------------------------------------------------------------- Incoming request

/**
 * The design's "New consultation request" sheet for a client's call:
 * a countdown until the caller's phone stops ringing, Decline and Accept.
 * Accepting joins the Agora channel and opens the call once connected.
 */
@Composable
private fun RequestSheet(
    call: CallRequest,
    onAccepted: (channelName: String, callType: CallType, peerName: String) -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signaling = remember { CallSignalingRepository() }
    val tokens = remember { TokenRepository() }
    var accepting by remember(call.callId) { mutableStateOf(false) }
    var error by remember(call.callId) { mutableStateOf<String?>(null) }
    var accepted by remember { mutableStateOf<CallRequest?>(null) }
    val isVideo = runCatching { CallType.valueOf(call.callType) }.getOrDefault(CallType.AUDIO) == CallType.VIDEO

    // While this sheet is up it rings itself, so the push notification doesn't ring as well.
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
        IncomingCallAlerts.inAppRingerVisible = true
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            IncomingCallAlerts.inAppRingerVisible = false
            IncomingCallAlerts.stopRinging(context)
        }
    }
    LaunchedEffect(call.callId, accepting) {
        if (!accepting) {
            IncomingCallAlerts.cancelNotification(context, call.callId)
            IncomingCallAlerts.startRinging(context)
        } else {
            IncomingCallAlerts.stopRinging(context)
        }
    }

    val connectionState by AgoraCallManager.connectionState.collectAsState()
    LaunchedEffect(connectionState) {
        val c = accepted
        if (connectionState == CallConnectionState.JOINED && c != null) {
            accepted = null
            onAccepted(c.channelName, runCatching { CallType.valueOf(c.callType) }.getOrDefault(CallType.AUDIO), c.callerName)
        }
    }

    // Seconds until the caller's side gives up.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call.callId) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    val started = if (call.createdAtMillis > 0) call.createdAtMillis else now
    val left = (RING_SECONDS - ((now - started) / 1000).toInt()).coerceIn(0, RING_SECONDS)

    fun decline() {
        scope.launch { signaling.updateStatus(call.callId, CallRequestStatus.DECLINED) }
        onDecline()
    }

    fun accept() {
        accepting = true
        error = null
        scope.launch {
            val status = signaling.updateStatus(call.callId, CallRequestStatus.ACCEPTED)
            if (status.isFailure) {
                error = status.exceptionOrNull()?.message ?: "Couldn't accept the call."
                accepting = false
                return@launch
            }
            tokens.fetchOrCreateExpertToken(call.calleeUid, call.channelName).fold(
                onSuccess = { token ->
                    accepted = call
                    AgoraCallManager.joinChannelWithToken(context, call.channelName, token, isVideo)
                },
                onFailure = { e ->
                    error = e.message ?: "Couldn't fetch a call token. Is the Cloud Function deployed?"
                    accepting = false
                }
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x990B0920))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color.White)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                    0.35f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dot"
                )
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFECFDF5)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).alpha(pulse).clip(CircleShape).background(Color(0xFF22C55E)))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isVideo) "New video call request" else "New audio call request", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                }
                Spacer(Modifier.weight(1f))
                CountdownRing(left)
                Text("Expires", fontSize = 11.5.sp, color = Slate, modifier = Modifier.padding(start = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (bg, fg) = tintFor(call.callerName)
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(bg), contentAlignment = Alignment.Center) {
                    Text(initials(call.callerName), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg)
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(call.callerName.ifBlank { "A client" }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(if (isVideo) "Wants a video consultation" else "Wants an audio consultation", fontSize = 12.sp, color = Slate, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF7F6FC)).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Chip(if (isVideo) "Video call" else "Audio call", dark = false)
                Chip("Free consultation", dark = true)
            }
            error?.let { Text(it, color = Danger, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE4E2EE), RoundedCornerShape(16.dp))
                        .clickable(enabled = !accepting, onClick = ::decline),
                    contentAlignment = Alignment.Center
                ) { Text("Decline", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary) }
                Box(
                    Modifier
                        .weight(1.6f)
                        .height(52.dp)
                        .shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = Color(0xCC16A34A), spotColor = Color(0xCC16A34A))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF22C55E), Color(0xFF16A34A))))
                        .clickable(enabled = !accepting, onClick = ::accept),
                    contentAlignment = Alignment.Center
                ) {
                    if (accepting) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    else Text("Accept", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CountdownRing(left: Int) {
    val color = if (left <= 10) Color(0xFFEF4444) else Color(0xFF22C55E)
    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 3.dp.toPx()
            val arc = Size(size.width - w, size.height - w)
            val tl = Offset(w / 2, w / 2)
            drawArc(Color(0xFFE2E8F0), 0f, 360f, false, tl, arc, style = Stroke(w))
            drawArc(color, -90f, 360f * left / RING_SECONDS, false, tl, arc, style = Stroke(w))
        }
        Text(left.toString(), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

@Composable
private fun Chip(text: String, dark: Boolean) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = if (dark) FontWeight.SemiBold else FontWeight.Medium,
        color = if (dark) Color(0xFFFFD93D) else Color(0xFF334155),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (dark) Color(0xFF14103A) else Color.White)
            .then(if (dark) Modifier else Modifier.border(1.dp, Color(0xFFE7E5F0), RoundedCornerShape(999.dp)))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

// ---------------------------------------------------------------- Shared bits

@Composable
private fun BottomNav(selected: ExpertTab, onPick: (ExpertTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEEDF4)))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            ExpertTab.entries.forEach { t ->
                val on = t == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = t.label) { onPick(t) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(t.icon, null, tint = if (on) VastuPrimary else Slate, modifier = Modifier.size(22.dp))
                    Text(t.label, fontSize = 10.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) VastuPrimary else Slate)
                }
            }
        }
    }
}

@Composable
private fun ExpertBadgeAvatar(me: ExpertProfile?, size: Dp, radius: Dp, fontSize: androidx.compose.ui.unit.TextUnit) {
    if (!me?.photo.isNullOrBlank()) {
        ProfileAvatar(me?.photo.orEmpty(), me?.name.orEmpty(), size, shape = RoundedCornerShape(radius))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(PartnerGold), contentAlignment = Alignment.Center) {
            Text(initials(me?.name.orEmpty()), fontSize = fontSize, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}

@Composable
private fun HeroIconButton(icon: ImageVector, description: String, badge: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(13.dp))
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(19.dp))
        if (badge) Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 9.dp, end = 10.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1650))
                .padding(1.5.dp)
                .clip(CircleShape)
                .background(Color(0xFFF87171))
        )
    }
}

@Composable
private fun WhiteCard(inner: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(20.dp), ambientColor = CardShadow, spotColor = CardShadow)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .then(inner),
        content = content
    )
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    trailing: String? = null,
    onAction: () -> Unit = {}
) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
        if (action != null) Text(action, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VastuPrimary, modifier = Modifier.clickable(onClick = onAction))
        if (trailing != null) Text(trailing, fontSize = 11.5.sp, color = Slate)
    }
}

@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFECEAF4)).padding(4.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .then(if (on) Modifier.shadow(3.dp, RoundedCornerShape(11.dp)) else Modifier)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) Color.White else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) { Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (on) TextPrimary else Slate) }
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, fontSize = 12.sp, color = Slate, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 14.dp))
}

private val TINTS = listOf(
    Color(0xFFEEEAFE) to Color(0xFF5B4BD6),
    Color(0xFFFEF3C7) to Color(0xFFB45309),
    Color(0xFFFCE7F3) to Color(0xFFBE185D),
    Color(0xFFDCFCE7) to Color(0xFF15803D),
    Color(0xFFE0F2FE) to Color(0xFF0369A1),
    Color(0xFFFFEDD5) to Color(0xFFC2410C)
)

private fun tintFor(name: String): Pair<Color, Color> = TINTS[Math.floorMod(name.hashCode(), TINTS.size)]

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun isToday(millis: Long): Boolean = startOfDay(millis) == startOfDay(System.currentTimeMillis())

/** Monday 00:00 of this week. */
private fun startOfWeek(): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(System.currentTimeMillis())
    val back = (get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon → 0 … Sun → 6
    add(Calendar.DAY_OF_YEAR, -back)
}.timeInMillis

private fun durationLabel(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    else -> "${(seconds + 30) / 60} min"
}

private fun dateLabel(millis: Long): String {
    val today = startOfDay(System.currentTimeMillis())
    val day = startOfDay(millis)
    return when (day) {
        today -> "Today"
        today - DAY_MILLIS -> "Yesterday"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale("en", "IN")).format(java.util.Date(millis))
    }
}
