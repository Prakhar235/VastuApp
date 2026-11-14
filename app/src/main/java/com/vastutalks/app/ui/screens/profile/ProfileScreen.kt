package com.vastutalks.app.ui.screens.profile

import com.vastutalks.app.ui.components.keepAboveKeyboard
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vastutalks.app.data.ai.AgentChatStore
import com.vastutalks.app.data.model.AnanyaAgent
import com.vastutalks.app.data.model.CallHistoryEntry
import com.vastutalks.app.data.model.CallType
import com.vastutalks.app.data.model.Chat
import com.vastutalks.app.data.model.SampleShop
import com.vastutalks.app.ui.components.ChatSender
import com.vastutalks.app.ui.components.DesignLangToggle
import com.vastutalks.app.ui.components.DesignSheet
import com.vastutalks.app.ui.components.DesignSwitch
import com.vastutalks.app.ui.components.DesignToast
import com.vastutalks.app.ui.components.ProfileAvatar
import com.vastutalks.app.ui.components.UnderlineTabs
import com.vastutalks.app.ui.components.SheetHeader
import com.vastutalks.app.ui.components.rememberPhotoCapture
import com.vastutalks.app.ui.components.rememberPhotoPicker
import com.vastutalks.app.ui.theme.CardBorder
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.InputFill
import com.vastutalks.app.ui.theme.Poppins
import com.vastutalks.app.ui.theme.Success
import com.vastutalks.app.ui.theme.SurfaceLight
import com.vastutalks.app.ui.theme.TextBody
import com.vastutalks.app.ui.theme.TextHint
import com.vastutalks.app.ui.theme.TextPrimary
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuIndigo
import com.vastutalks.app.ui.theme.VastuPrimary
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ProfileTab { PROFILE, HISTORY }

/** Which bottom sheet is open — the design's profile modals. */
private enum class ProfileSheet(val title: String) {
    ADD("Add Credits"), EDIT("Edit Profile"), SETTINGS("Settings"), TXN("Transactions"),
    PAYMENT("Payment Methods"), HELP("Help & Support"), PRIVACY("Privacy Policy"), TERMS("Terms & Conditions")
}

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSignOut: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenAgentChat: () -> Unit = {},
    onOpenChat: (otherUid: String, otherName: String) -> Unit = { _, _ -> },
    onOpenPost: (postId: String) -> Unit = {},
    profileViewModel: ProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by profileViewModel.uiState.collectAsState()
    val user = profileViewModel.currentUser
    var selectedTab by remember { mutableStateOf(ProfileTab.PROFILE) }
    var sheet by remember { mutableStateOf<ProfileSheet?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showNotifications by remember { mutableStateOf(false) }
    LaunchedEffect(toast) { if (toast != null) { delay(1800); toast = null } }

    val displayName = uiState.profile?.name?.takeIf { it.isNotBlank() }
        ?: user?.displayName?.takeIf { it.isNotBlank() } ?: "Vastu Talks user"
    val email = user?.email ?: "No email on file"

    // Last thing Anayra said, for the "Anayra AI" history card.
    var agentPreview by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        agentPreview = withContext(Dispatchers.IO) {
            AgentChatStore(context).load()?.messages?.lastOrNull { it.sender == ChatSender.AGENT && it.text.isNotBlank() }?.text
        }
    }

    Box(Modifier.fillMaxSize().background(SurfaceLight)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(26.dp))
                }
                Text("Profile", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(start = 6.dp))
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(CircleShape).clickable { sheet = ProfileSheet.EDIT }) {
                            if (uiState.pendingPhoto != null) {
                                val img = remember(uiState.pendingPhoto) { uiState.pendingPhoto!!.asImageBitmap() }
                                androidx.compose.foundation.Image(img, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(60.dp).clip(CircleShape))
                            } else {
                                ProfileAvatar(uiState.profile?.photo.orEmpty(), displayName, size = 60.dp)
                            }
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(displayName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(email, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, CardBorder, CircleShape)
                                .clickable { sheet = ProfileSheet.EDIT },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit profile", tint = TextBody, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Wallet — 0 until there's a payment backend (see SampleShop).
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Wallet balance", fontSize = 11.5.sp, color = TextSecondary)
                            Text(SampleShop.balanceText, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary, letterSpacing = (-0.24).sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        Row(
                            Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VastuPrimary)
                                .clickable { sheet = ProfileSheet.ADD }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Text(" Add money", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                item {
                    UnderlineTabs(
                        labels = listOf("Account", "History"),
                        selected = selectedTab.ordinal,
                        onSelect = { selectedTab = ProfileTab.entries[it] }
                    )
                }

                if (selectedTab == ProfileTab.PROFILE) {
                    item {
                        MenuSection("Account") {
                            MenuRow(Icons.Outlined.Person, "Edit Profile") { sheet = ProfileSheet.EDIT }
                            MenuRow(Icons.Outlined.Notifications, "Notifications", badgeCount = uiState.unreadNotifications.takeIf { it > 0 }) { showNotifications = true }
                            MenuRow(Icons.Outlined.Settings, "Settings", last = true) { sheet = ProfileSheet.SETTINGS }
                        }
                    }
                    item {
                        MenuSection("Payment & Billing") {
                            MenuRow(Icons.Outlined.CreditCard, "Payment Methods") { sheet = ProfileSheet.PAYMENT }
                            MenuRow(Icons.Outlined.History, "Transaction History", last = true) { sheet = ProfileSheet.TXN }
                        }
                    }
                    item {
                        MenuSection("Support & Legal") {
                            MenuRow(Icons.Outlined.HelpOutline, "Help & Support") { sheet = ProfileSheet.HELP }
                            MenuRow(Icons.Outlined.Shield, "Privacy Policy") { sheet = ProfileSheet.PRIVACY }
                            MenuRow(Icons.Outlined.Description, "Terms & Conditions", last = true) { sheet = ProfileSheet.TERMS }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSignOut() },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = Color(0xFFE53E3E), modifier = Modifier.size(17.dp))
                            Text("Log out", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE53E3E), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                } else {
                    // Anayra AI
                    item { HistoryTitle(Icons.Filled.AutoAwesome, "${AnanyaAgent.NAME} AI") }
                    item {
                        if (agentPreview == null) {
                            EmptyHistory("No chats with ${AnanyaAgent.NAME} yet.")
                        } else {
                            HistoryCard(
                                title = "Chat with ${AnanyaAgent.NAME}",
                                date = "Saved on this phone",
                                cost = "Free",
                                costColor = VastuPrimary,
                                preview = agentPreview,
                                footer = "Duration: —",
                                action = { PillAction("Open chat", onOpenAgentChat) }
                            )
                        }
                    }
                    // Chats with experts
                    item { HistoryTitle(Icons.AutoMirrored.Outlined.Chat, "Chat History") }
                    val myUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                    if (uiState.chats.isEmpty()) {
                        item { EmptyHistory("No chats yet.") }
                    } else {
                        items(uiState.chats, key = { it.id }) { chat -> ChatHistoryCard(chat, myUid, onOpenChat) }
                    }
                    // Calls
                    val (video, audio) = uiState.history.partition { it.callType == CallType.VIDEO.name }
                    item { HistoryTitle(Icons.Outlined.Videocam, "Video Call History") }
                    when {
                        uiState.isLoading -> item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = VastuPrimary)
                            }
                        }
                        uiState.errorMessage != null -> item {
                            Text(uiState.errorMessage ?: "", fontSize = 12.5.sp, color = Danger)
                        }
                        else -> {
                            if (video.isEmpty()) item { EmptyHistory("No video calls yet.") }
                            items(video) { CallHistoryCard(it, Color(0xFFFF6B6B)) }
                            if (audio.isNotEmpty()) {
                                item { HistoryTitle(Icons.Outlined.History, "Audio Call History") }
                                items(audio) { CallHistoryCard(it, VastuPrimary) }
                            }
                        }
                    }
                }
            }
        }

        when (val open = sheet) {
            null -> {}
            ProfileSheet.ADD -> AddMoneySheet(onDismiss = { sheet = null }) { amount ->
                // No payment gateway yet, so nothing is credited.
                sheet = null
                toast = "Payments are coming soon"
            }
            ProfileSheet.EDIT -> EditProfileSheet(
                name = displayName,
                email = email,
                phone = uiState.profile?.phone.orEmpty(),
                photo = uiState.profile?.photo.orEmpty(),
                pendingPhoto = uiState.pendingPhoto,
                saving = uiState.savingDetails,
                onPhoto = profileViewModel::updatePhoto,
                onRemovePhoto = profileViewModel::removePhoto,
                onDismiss = { sheet = null },
                onSave = { name, phone ->
                    profileViewModel.updateDetails(name, phone) { error ->
                        if (error == null) sheet = null
                        toast = error ?: "Profile saved"
                    }
                }
            )
            ProfileSheet.SETTINGS -> SettingsSheet(onDismiss = { sheet = null })
            ProfileSheet.TXN -> TransactionsSheet(onDismiss = { sheet = null })
            else -> DesignSheet(onDismiss = { sheet = null }) {
                SheetHeader(open.title, onClose = { sheet = null }, gradientTitle = true)
                Text("Content for this section will be displayed here.", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 21.dp))
            }
        }

        if (showNotifications) {
            com.vastutalks.app.ui.screens.post.NotificationsSheet(onDismiss = { showNotifications = false }, onOpenPost = onOpenPost)
        }
        toast?.let { DesignToast(it, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 14.dp)) }
    }
}

@Composable
private fun MenuSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 2.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextHint, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
        ) { content() }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, badgeCount: Int? = null, last: Boolean = false, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = TextBody, modifier = Modifier.size(19.dp))
            Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.padding(start = 12.dp).weight(1f))
            if (badgeCount != null) {
                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(VastuPrimary)
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$badgeCount", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Box(modifier = Modifier.width(12.dp))
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFFCBD5E0), modifier = Modifier.size(17.dp))
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFFAF5FF)))
    }
}

@Composable
private fun HistoryTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, top = 2.dp)) {
        Icon(icon, contentDescription = null, tint = VastuPrimary, modifier = Modifier.size(20.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun EmptyHistory(text: String) {
    Text(text, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(start = 2.dp))
}

@Composable
private fun PillAction(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFF3F0FF))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = VastuPrimary)
        Icon(Icons.Filled.ChevronRight, null, tint = VastuPrimary, modifier = Modifier.size(12.dp).padding(start = 2.dp))
    }
}

/** The design's history card: title + date, cost on the right, optional preview, then duration and an action. */
@Composable
private fun HistoryCard(
    title: String,
    date: String,
    cost: String,
    costColor: Color,
    preview: String? = null,
    footer: String,
    action: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(date, fontSize = 10.5.sp, color = TextSecondary, modifier = Modifier.padding(top = 3.5.dp))
            }
            Text(cost, fontSize = 12.25.sp, fontWeight = FontWeight.Bold, color = costColor)
        }
        if (!preview.isNullOrBlank()) {
            Text(
                preview.replace("**", ""),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = TextBody,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.5.dp)
            )
        }
        Box(Modifier.padding(top = 10.5.dp).fillMaxWidth().height(1.dp).background(Color(0xFFFAF5FF)))
        Row(Modifier.fillMaxWidth().padding(top = 10.5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(footer, fontSize = 10.5.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            action()
        }
    }
}

@Composable
private fun ChatHistoryCard(chat: Chat, myUid: String, onOpenChat: (String, String) -> Unit) {
    val other = chat.otherUid(myUid)
    val name = chat.names[other].orEmpty().ifBlank { "Chat" }
    HistoryCard(
        title = name,
        date = relative(chat.lastMessageAtMillis),
        cost = "Free",
        costColor = VastuPrimary,
        preview = chat.lastMessage,
        footer = "Last message",
        action = { PillAction("Open chat") { onOpenChat(other, name) } }
    )
}

@Composable
private fun CallHistoryCard(entry: CallHistoryEntry, color: Color) {
    HistoryCard(
        title = entry.expertName,
        date = relative(entry.startTimeMillis),
        cost = if (com.vastutalks.app.data.model.CallPricing.PAID_CALLS) SampleShop.rupees(entry.cost) else "Free",
        costColor = color,
        footer = "Duration: ${formatDuration(entry.durationSeconds)}",
        action = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Download, null, tint = color, modifier = Modifier.size(13.dp))
                Text(" ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(entry.startTimeMillis))}", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    )
}

private fun formatDuration(seconds: Int): String = when {
    seconds < 60 -> "$seconds sec"
    else -> "${seconds / 60} min"
}

private fun relative(millis: Long): String =
    if (millis <= 0) "" else android.text.format.DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()

@Composable
private fun AddMoneySheet(onDismiss: () -> Unit, onPay: (Int) -> Unit) {
    var amount by remember { mutableStateOf(1000) }
    DesignSheet(onDismiss = onDismiss) {
        SheetHeader("Add Credits", onClose = onDismiss, gradientTitle = true)
        Column(Modifier.padding(top = 21.dp, bottom = 21.dp), verticalArrangement = Arrangement.spacedBy(10.5.dp)) {
            SampleShop.topUps.forEach { (value, bonus) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFAF5FF), Color(0xFFEEF2FF))))
                        .border(2.dp, if (amount == value) VastuPrimary else Color(0xFFE9D5FF), RoundedCornerShape(14.dp))
                        .clickable { amount = value }
                        .padding(horizontal = 21.dp, vertical = 17.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("₹$value", fontSize = 17.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        if (bonus > 0) Text("+ ₹$bonus bonus", fontSize = 10.5.sp, color = Color(0xFF51CF66), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.5.dp))
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = VastuPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(VastuPrimary, VastuIndigo)))
                .clickable { onPay(amount) }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Proceed to Payment", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EditProfileSheet(
    name: String,
    email: String,
    phone: String,
    photo: String,
    pendingPhoto: Bitmap?,
    saving: Boolean,
    onPhoto: (Bitmap) -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val context = LocalContext.current
    val error: (String) -> Unit = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
    val takePhoto = rememberPhotoCapture(maxSide = 640, onError = error, onCaptured = { onPhoto(it.bitmap) })
    val pickPhoto = rememberPhotoPicker(maxSide = 640, onError = error, onPicked = { onPhoto(it.bitmap) })
    var newName by remember { mutableStateOf(name) }
    var newPhone by remember { mutableStateOf(phone) }
    val canSave = !saving && newName.isNotBlank()

    DesignSheet(onDismiss = onDismiss) {
        SheetHeader("Edit profile", onClose = onDismiss, modifier = Modifier.offset(y = (-6).dp))
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(92.dp).clickable { pickPhoto() }) {
                if (pendingPhoto != null) {
                    val img = remember(pendingPhoto) { pendingPhoto.asImageBitmap() }
                    androidx.compose.foundation.Image(img, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(92.dp).clip(CircleShape))
                } else {
                    ProfileAvatar(photo, name, size = 92.dp)
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(y = (-2).dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(VastuPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.CameraAlt, "Change photo", tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton(Icons.Outlined.CameraAlt, "Camera") { takePhoto() }
                ChipButton(Icons.Outlined.Image, "Gallery") { pickPhoto() }
                if (photo.isNotBlank()) {
                    Text(
                        "Remove",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE53E3E),
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { onRemovePhoto() }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EditField("Full name", newName, { newName = it })
            EditField("Email", email, {}, enabled = false, keyboardType = KeyboardType.Email)
            EditField("Phone number", newPhone, { newPhone = it }, keyboardType = KeyboardType.Phone)
        }
        Box(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(VastuPrimary.copy(alpha = if (canSave) 1f else 0.5f))
                .clickable(enabled = canSave) { onSave(newName, newPhone) },
            contentAlignment = Alignment.Center
        ) {
            Text(if (saving) "Saving…" else "Save changes", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChipButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White)
            .border(1.dp, CardBorder, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(14.dp))
        Text(" $label", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

@Composable
private fun EditField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text) {
    Column {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = TextSecondary, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = if (enabled) TextPrimary else TextSecondary, fontFamily = Poppins),
            cursorBrush = SolidColor(VastuPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.keepAboveKeyboard(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(InputFill)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) { inner() }
            }
        )
    }
}

/** Sample notification/preference switches (saved on this phone) and Anayra's language. */
@Composable
private fun SettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sample_settings", android.content.Context.MODE_PRIVATE) }
    val items = listOf(
        Triple("push", "Push notifications", "Session reminders and ${AnanyaAgent.NAME} updates") to true,
        Triple("email", "Email", "Receipts and weekly Vastu tips") to true,
        Triple("sms", "SMS alerts", "Important alerts by text") to false,
    )
    var values by remember { mutableStateOf(items.associate { (t, d) -> t.first to prefs.getBoolean(t.first, d) } + ("dark" to prefs.getBoolean("dark", false))) }
    val toggle: (String) -> Unit = { key ->
        val v = !(values[key] ?: false)
        values = values + (key to v)
        prefs.edit().putBoolean(key, v).apply()
    }

    DesignSheet(onDismiss = onDismiss) {
        SheetHeader("Settings", onClose = onDismiss, modifier = Modifier.offset(y = (-6).dp))
        Text("Notifications", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextHint, modifier = Modifier.padding(top = 4.dp))
        items.forEachIndexed { i, (t, _) ->
            SettingRow(t.second, t.third, topBorder = i > 0) {
                DesignSwitch(values[t.first] == true, { toggle(t.first) }, t.second)
            }
        }
        Text("Preferences", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextHint, modifier = Modifier.padding(top = 16.dp))
        SettingRow("Language", "Used by ${AnanyaAgent.NAME} and the app", topBorder = false) { DesignLangToggle(height = 26.dp) }
        SettingRow("Dark mode", "Easier on the eyes at night", topBorder = true) {
            DesignSwitch(values["dark"] == true, { toggle("dark") }, "Dark mode")
        }
        var confirmClear by remember { mutableStateOf(false) }
        SettingRow("Clear ${AnanyaAgent.NAME} chat", "Deletes the chat and what she remembers of it", topBorder = true) {
            Text(
                if (confirmClear) "Confirm" else "Clear",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE53E3E),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable {
                        if (!confirmClear) confirmClear = true
                        else {
                            AgentChatStore(context).clear()
                            confirmClear = false
                            android.widget.Toast.makeText(context, "Chat cleared", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun SettingRow(label: String, sub: String, topBorder: Boolean, trailing: @Composable () -> Unit) {
    Column {
        if (topBorder) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F1F5)))
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(sub, fontSize = 11.5.sp, color = TextHint, modifier = Modifier.padding(top = 2.dp))
            }
            Box(Modifier.padding(start = 12.dp)) { trailing() }
        }
    }
}

private data class Txn(val group: String, val type: String, val amount: Int, val date: String, val method: String)

/** No payment ledger yet, so there are no transactions to show. */
private val TRANSACTIONS = emptyList<Txn>()

@Composable
private fun TransactionsSheet(onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("all") }
    val list = TRANSACTIONS.filter { filter == "all" || (filter == "in") == (it.amount > 0) }
    val month = TRANSACTIONS.filter { it.group == "This month" }
    DesignSheet(onDismiss = onDismiss) {
        SheetHeader("Transactions", onClose = onDismiss, modifier = Modifier.offset(y = (-6).dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SumTile("Added this month", SampleShop.rupees(month.filter { it.amount > 0 }.sumOf { it.amount }), Success, Modifier.weight(1f))
            SumTile("Spent this month", SampleShop.rupees(month.filter { it.amount < 0 }.sumOf { -it.amount }), TextPrimary, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            listOf("all" to "All", "in" to "Added", "out" to "Spent").forEach { (k, label) ->
                val on = filter == k
                Text(
                    label,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) Color.White else TextBody,
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (on) TextPrimary else Color.White)
                        .border(1.dp, if (on) TextPrimary else CardBorder, RoundedCornerShape(999.dp))
                        .clickable { filter = k }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        if (list.isEmpty()) {
            Text(
                "No transactions yet.",
                fontSize = 12.5.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp)
            )
        }
        list.groupBy { it.group }.forEach { (group, txns) ->
            Text(group, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextHint, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
            txns.forEachIndexed { i, t ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F1F5)))
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    val added = t.amount > 0
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(if (added) Color(0xFFECFDF5) else Color(0xFFF4F4F8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (added) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, null, tint = if (added) Success else TextBody, modifier = Modifier.size(16.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(t.type, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${t.date} · ${t.method}", fontSize = 11.5.sp, color = TextHint, modifier = Modifier.padding(top = 1.dp))
                    }
                    Text(
                        (if (added) "+" else "−") + SampleShop.rupees(kotlin.math.abs(t.amount)),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (added) Success else TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun SumTile(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(SurfaceLight).padding(12.dp)) {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(top = 2.dp))
    }
}
