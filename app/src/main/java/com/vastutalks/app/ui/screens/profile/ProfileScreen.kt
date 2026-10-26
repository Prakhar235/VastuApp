package com.vastutalks.app.ui.screens.profile

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import com.vastutalks.app.data.ai.AgentChatStore
import com.vastutalks.app.data.ai.AgentLanguage
import com.vastutalks.app.data.ai.AgentLanguagePref
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vastutalks.app.data.model.CallHistoryEntry
import com.vastutalks.app.ui.components.ProfilePhotoEditor
import com.vastutalks.app.data.model.CallPricing
import com.vastutalks.app.data.model.CallType
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.SurfaceLight
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuPrimary
import com.vastutalks.app.ui.theme.WalletGold
import com.vastutalks.app.ui.theme.WalletGoldDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ProfileTab { PROFILE, HISTORY }

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSignOut: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    profileViewModel: ProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    var editingDetails by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val uiState by profileViewModel.uiState.collectAsState()
    val user = profileViewModel.currentUser
    var selectedTab by remember { mutableStateOf(ProfileTab.PROFILE) }

    val displayName = uiState.profile?.name?.takeIf { it.isNotBlank() }
        ?: user?.displayName?.takeIf { it.isNotBlank() } ?: "Vastu Talks user"
    val email = user?.email ?: "No email on file"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceLight)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = Color(0xFF2B2B2B))
            }
            Text("My Profile", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Firebase credential data — name, email, avatar.
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfilePhotoEditor(
                        photo = uiState.profile?.photo.orEmpty(),
                        name = displayName,
                        size = 64.dp,
                        picked = uiState.pendingPhoto,
                        onPicked = profileViewModel::updatePhoto,
                        shape = RoundedCornerShape(18.dp)
                    )
                    Box(modifier = Modifier.padding(start = 14.dp)) {
                        Column {
                            Text(displayName, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
                            Text(email, fontSize = 13.sp, color = TextSecondary)
                            uiState.profile?.phone?.takeIf { it.isNotBlank() }?.let {
                                Text(it, fontSize = 13.sp, color = TextSecondary)
                            }
                            Text(
                                if (uiState.pendingPhoto != null) "Saving photo…" else "Tap the photo to change it",
                                fontSize = 12.sp,
                                color = VastuPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Available Credits — simulated wallet, matching Home/in-call's
            // hardcoded per-expert rate. No real credits/ledger backend yet,
            // and hidden while calls are free.
            if (CallPricing.PAID_CALLS) item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.horizontalGradient(listOf(WalletGold, WalletGoldDark)))
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Wallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Text("  Available Credits", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Box(modifier = Modifier.height(6.dp))
                            Text("₹2,450", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                            Box(modifier = Modifier.height(4.dp))
                            Text("Lifetime earned: ₹5,000", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF2B2B2B))
                                .clickable { /* TODO: no payment/add-credits flow yet — see README */ }
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Text(" Add", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // Profile / History toggle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .padding(4.dp)
                ) {
                    ProfileTabChip(label = "Profile", selected = selectedTab == ProfileTab.PROFILE, modifier = Modifier.weight(1f)) {
                        selectedTab = ProfileTab.PROFILE
                    }
                    ProfileTabChip(label = "History", selected = selectedTab == ProfileTab.HISTORY, modifier = Modifier.weight(1f)) {
                        selectedTab = ProfileTab.HISTORY
                    }
                }
            }

            if (selectedTab == ProfileTab.PROFILE) {
                item { SectionHeader("ACCOUNT") }
                item {
                    SettingsGroup {
                        SettingsRow(Icons.Filled.Person, "Edit Profile") { editingDetails = true }
                        SettingsDivider()
                        SettingsRow(
                            Icons.Filled.Notifications,
                            "Notifications",
                            badgeCount = uiState.unreadNotifications.takeIf { it > 0 },
                            onClick = onOpenNotifications
                        )
                        SettingsDivider()
                        SettingsRow(Icons.Filled.Settings, "Settings") { showSettings = true }
                    }
                }
                item { SectionHeader(if (CallPricing.PAID_CALLS) "PAYMENT & BILLING" else "ACTIVITY") }
                item {
                    SettingsGroup {
                        if (CallPricing.PAID_CALLS) {
                            SettingsRow(Icons.Filled.CreditCard, "Payment Methods") {
                                android.widget.Toast.makeText(context, "Payments aren't set up yet — calls are free for now.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            SettingsDivider()
                        }
                        SettingsRow(Icons.Filled.History, if (CallPricing.PAID_CALLS) "Transaction History" else "Call History") {
                            selectedTab = ProfileTab.HISTORY
                        }
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .clickable { onSignOut() }
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Danger.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Logout, contentDescription = null, tint = Danger, modifier = Modifier.size(18.dp))
                            }
                            Text("Log Out", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Danger, modifier = Modifier.padding(start = 14.dp))
                        }
                    }
                }
                item { Box(modifier = Modifier.height(24.dp)) }
            } else {
                item {
                    Text("Call history", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
                }
                when {
                    uiState.isLoading -> {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = VastuPrimary)
                            }
                        }
                    }
                    uiState.errorMessage != null -> {
                        item {
                            Text(uiState.errorMessage ?: "", fontSize = 13.sp, color = Danger, modifier = Modifier.padding(vertical = 16.dp))
                        }
                    }
                    uiState.history.isEmpty() -> {
                        item {
                            Text(
                                "No calls yet — your consultations will show up here once you finish one.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                    else -> {
                        items(uiState.history) { entry -> CallHistoryRow(entry) }
                    }
                }
                item { Box(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    if (editingDetails) {
        EditDetailsDialog(
            name = displayName,
            phone = uiState.profile?.phone.orEmpty(),
            saving = uiState.savingDetails,
            onDismiss = { editingDetails = false },
            onSave = { name, phone ->
                profileViewModel.updateDetails(name, phone) { error ->
                    if (error == null) editingDetails = false
                    android.widget.Toast.makeText(context, error ?: "Profile saved", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    if (showSettings) SettingsDialog(onDismiss = { showSettings = false })
}

@Composable
private fun ProfileTabChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) VastuPrimary else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, badgeCount: Int? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
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
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B), modifier = Modifier.padding(start = 14.dp).weight(1f))
        if (badgeCount != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Danger)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text("$badgeCount", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Box(modifier = Modifier.width(8.dp))
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
}

/** Name and phone. */
@Composable
private fun EditDetailsDialog(name: String, phone: String, saving: Boolean, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var newName by remember { mutableStateOf(name) }
    var newPhone by remember { mutableStateOf(phone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = newPhone,
                    onValueChange = { newPhone = it },
                    label = { Text("Phone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = { onSave(newName, newPhone) }) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** The Vastu Expert's reply language, phone notification settings, and clearing the AI chat. */
@Composable
private fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Vastu Expert replies in", fontSize = 13.sp, color = TextSecondary)
                listOf(AgentLanguage.ENGLISH to "English", AgentLanguage.HINDI to "हिंदी (Hindi)").forEach { (language, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { AgentLanguagePref.set(language) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = AgentLanguagePref.current == language, onClick = { AgentLanguagePref.set(language) })
                        Text(label, fontSize = 15.sp)
                    }
                }
                Box(modifier = Modifier.height(8.dp))
                TextButton(onClick = {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    runCatching { context.startActivity(intent) }
                }) { Text("Notification settings") }
                TextButton(onClick = { confirmClear = true }) { Text("Clear Vastu Expert chat", color = Danger) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the chat?") },
            text = { Text("Your chat with the Vastu Expert, and what she remembers of it, will be deleted from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    AgentChatStore(context).clear()
                    confirmClear = false
                    android.widget.Toast.makeText(context, "Chat cleared", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Clear", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SurfaceLight))
}

@Composable
private fun CallHistoryRow(entry: CallHistoryEntry) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(VastuPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (entry.callType == CallType.VIDEO.name) Icons.Filled.Videocam else Icons.Filled.CallMade,
                    contentDescription = null,
                    tint = VastuPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(modifier = Modifier.padding(start = 12.dp)) {
                Column {
                    Text(entry.expertName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
                    Text(formatDateTime(entry.startTimeMillis), fontSize = 12.sp, color = TextSecondary)
                }
            }

            Box(modifier = Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "%d:%02d".format(entry.durationSeconds / 60, entry.durationSeconds % 60),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2B2B2B)
                )
                if (CallPricing.PAID_CALLS) Text("₹${entry.cost}", fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

private fun formatDateTime(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(millis))
