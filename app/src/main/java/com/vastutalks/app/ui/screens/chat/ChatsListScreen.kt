package com.vastutalks.app.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vastutalks.app.data.model.Chat
import com.vastutalks.app.data.repository.ChatRepository
import com.vastutalks.app.ui.components.ProfileAvatar
import com.vastutalks.app.ui.components.timeAgo
import com.vastutalks.app.ui.theme.SurfaceLight
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuPrimary

/** All of the signed-in user's chats, most recent first, with unread counts. */
@Composable
fun ChatsListScreen(onBack: () -> Unit, onOpenChat: (otherUid: String, otherName: String) -> Unit) {
    val repository = remember { ChatRepository() }
    val chats by remember { repository.listenMyChats() }.collectAsState(initial = null)
    val myUid = repository.myUid.orEmpty()

    Column(modifier = Modifier.fillMaxSize().background(SurfaceLight).statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = Color(0xFF2B2B2B))
            }
            Text("Messages", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
        }

        val list = chats
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No messages yet. Open an expert's profile and tap Chat to start a conversation.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(list, key = { it.id }) { chat ->
                    val other = chat.otherUid(myUid)
                    val name = chat.names[other].orEmpty().ifBlank { "Unknown" }
                    ChatRow(chat, myUid, other, name, onClick = { onOpenChat(other, name) })
                }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: Chat, myUid: String, otherUid: String, name: String, onClick: () -> Unit) {
    val unread = chat.unread[myUid] ?: 0L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileAvatar(chat.photos[otherUid].orEmpty(), name, size = 48.dp)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (chat.lastSenderUid == myUid) "You: " else "") + chat.lastMessage,
                fontSize = 13.sp,
                color = if (unread > 0) Color(0xFF2B2B2B) else TextSecondary,
                fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            Text(timeAgo(chat.lastMessageAtMillis), fontSize = 11.sp, color = TextSecondary)
            if (unread > 0) {
                Box(
                    modifier = Modifier.padding(top = 4.dp).size(20.dp).clip(CircleShape).background(VastuPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (unread > 9) "9+" else "$unread", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
