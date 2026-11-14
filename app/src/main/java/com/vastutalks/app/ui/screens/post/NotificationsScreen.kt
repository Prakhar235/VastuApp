package com.vastutalks.app.ui.screens.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Close
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vastutalks.app.data.model.AppNotification
import com.vastutalks.app.data.repository.NotificationRepository
import com.vastutalks.app.ui.components.ProfileAvatar
import com.vastutalks.app.ui.components.timeAgo
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.SurfaceLight
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuPrimary

/**
 * Likes and comments on your posts, and experts' replies to your
 * comments, newest first. Opening the list marks them read; tapping one
 * opens the post.
 */
@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpenPost: (String) -> Unit) {
    val repository = remember { NotificationRepository() }
    val notifications by remember { repository.listen() }.collectAsState(initial = null)

    // What was new when the list opened stays highlighted for this visit,
    // while everything shown is marked read (which clears the bell's badge).
    var newThisVisit by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(notifications) {
        val list = notifications ?: return@LaunchedEffect
        val unread = list.filter { !it.read }.map { it.id }
        newThisVisit = newThisVisit.orEmpty() + unread
        repository.markRead(unread)
    }

    Column(modifier = Modifier.fillMaxSize().background(SurfaceLight).statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = Color(0xFF2B2B2B))
            }
            Text("Notifications", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
        }

        val list = notifications
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing yet. Likes and comments on your posts, and replies to your comments, show up here.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(list, key = { it.id }) {
                    NotificationRow(it, isNew = it.id in newThisVisit.orEmpty(), onClick = { onOpenPost(it.postId) })
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, isNew: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isNew) VastuPrimary.copy(alpha = 0.07f) else Color.White)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            ProfileAvatar(notification.actorPhoto, notification.actorName, size = 44.dp)
            val (icon, tint) = when (notification.type) {
                "like" -> Icons.Filled.Favorite to Danger
                "reply" -> Icons.AutoMirrored.Filled.Reply to VastuPrimary
                else -> Icons.Filled.ChatBubble to VastuPrimary
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint)
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(notification.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
            if (notification.body.isNotBlank()) {
                Text("“${notification.body}”", fontSize = 13.sp, color = TextSecondary, maxLines = 2)
            }
            Text(timeAgo(notification.createdAtMillis), fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        if (isNew) {
            Box(modifier = Modifier.padding(start = 8.dp).size(9.dp).clip(CircleShape).background(VastuPrimary))
        }
    }
}

/**
 * The design's notifications sheet (from the bell): "N new", Mark all read,
 * and one row per notification with a tinted icon, title, time and
 * message. Tapping a row marks it read and opens its post.
 */
@Composable
fun NotificationsSheet(onDismiss: () -> Unit, onOpenPost: (String) -> Unit) {
    val repository = remember { NotificationRepository() }
    val notifications by remember { repository.listen() }.collectAsState(initial = null)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val list = notifications.orEmpty()
    val unread = list.filter { !it.read }

    com.vastutalks.app.ui.components.DesignSheet(
        onDismiss = onDismiss,
        radius = 24.dp,
        scrim = Color(0x730F0B2E),
        contentPadding = 0.dp,
        showHandle = false
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp, bottom = 4.dp)
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFE2E8F0))
        )
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Notifications", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = com.vastutalks.app.ui.theme.TextPrimary)
            if (unread.isNotEmpty()) Text("  ${unread.size} new", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = VastuPrimary)
            Box(Modifier.weight(1f))
            if (unread.isNotEmpty()) {
                Text(
                    "Mark all read", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VastuPrimary,
                    modifier = Modifier.padding(end = 10.dp).clickable { scope.launch { repository.markRead(unread.map { it.id }) } }
                )
            }
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFF4F4F8)).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, "Close", tint = com.vastutalks.app.ui.theme.TextBody, modifier = Modifier.size(16.dp))
            }
        }
        Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 24.dp)) {
            if (notifications != null && list.isEmpty()) {
                Text(
                    "Nothing yet. Likes and comments on your posts, and replies to your comments, show up here.",
                    fontSize = 12.sp, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                )
            }
            list.forEach { n ->
                val (icon, tint) = when (n.type) {
                    "like" -> Icons.Filled.Favorite to Color(0xFFE53E3E)
                    "reply" -> Icons.AutoMirrored.Filled.Reply to VastuPrimary
                    else -> Icons.Filled.ChatBubble to VastuPrimary
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (!n.read) Color(0xFFF7F5FF) else Color.White)
                        .clickable {
                            scope.launch { repository.markRead(listOf(n.id)) }
                            onDismiss()
                            onOpenPost(n.postId)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(n.title, fontSize = 13.sp, fontWeight = if (n.read) FontWeight.Medium else FontWeight.Bold, color = com.vastutalks.app.ui.theme.TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(timeAgo(n.createdAtMillis), fontSize = 10.5.sp, color = com.vastutalks.app.ui.theme.TextHint, modifier = Modifier.padding(start = 8.dp))
                        }
                        if (n.body.isNotBlank()) Text(n.body, fontSize = 12.sp, lineHeight = 17.sp, color = TextSecondary, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (!n.read) Box(Modifier.padding(start = 8.dp, top = 6.dp).size(8.dp).clip(CircleShape).background(VastuPrimary))
                }
            }
        }
    }
}
