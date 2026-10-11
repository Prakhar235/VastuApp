package com.vastutalks.app.ui.screens.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

    Column(modifier = Modifier.fillMaxSize().background(SurfaceLight).statusBarsPadding()) {
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
