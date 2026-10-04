package com.vastutalks.app.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vastutalks.app.ui.theme.VastuPrimary

enum class ChatSender { AGENT, USER }

data class AgentChatMessage(
    val id: Int,
    val text: String,
    val sender: ChatSender = ChatSender.AGENT,
    val image: ImageBitmap? = null
)

/** Shared tones for the agent call's dark surfaces. */
object CallTones {
    val Ink = Color(0xFF110F18)
    val Surface = Color(0xFF1C1927)
    val SurfaceRaised = Color(0xFF262236)
    val Hairline = Color.White.copy(alpha = 0.08f)
    val TextPrimary = Color(0xFFF4F2FA)
    val TextMuted = Color(0xFF9D98B3)
}

/**
 * The agent call's chat page: the full conversation with Ananya (spoken
 * lines are transcribed in here too) and a box to type a question.
 */
@Composable
fun AgentChatPage(
    messages: List<AgentChatMessage>,
    isAgentTyping: Boolean,
    agentName: String,
    avatarSeed: String,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isAgentTyping) {
        val last = messages.size - 1 + if (isAgentTyping) 1 else 0
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (messages.isEmpty() && !isAgentTyping) {
                item {
                    Text(
                        "Everything you and $agentName say shows up here.",
                        color = CallTones.TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            items(messages, key = { it.id }) { message ->
                if (message.sender == ChatSender.AGENT) AgentMessage(message, agentName, avatarSeed) else UserMessage(message)
            }
            if (isAgentTyping) {
                item { AgentMessage(AgentChatMessage(-1, "…"), agentName, avatarSeed, isTyping = true) }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(CallTones.Surface)
                .border(1.dp, CallTones.Hairline, RoundedCornerShape(26.dp))
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                if (inputText.isEmpty()) {
                    Text("Message $agentName…", color = CallTones.TextMuted, fontSize = 14.sp)
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    textStyle = TextStyle(color = CallTones.TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(VastuPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            val canSend = inputText.isNotBlank()
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (canSend) VastuPrimary else CallTones.SurfaceRaised)
                    .clickable(enabled = canSend) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) Color.White else CallTones.TextMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun AgentMessage(message: AgentChatMessage, agentName: String, avatarSeed: String, isTyping: Boolean = false) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = "https://i.pravatar.cc/300?u=$avatarSeed",
            contentDescription = null,
            modifier = Modifier.size(30.dp).clip(CircleShape).background(CallTones.SurfaceRaised)
        )
        Column(modifier = Modifier.padding(start = 10.dp).widthIn(max = 290.dp)) {
            Text(agentName, color = CallTones.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(CallTones.Surface)
                    .border(1.dp, CallTones.Hairline, RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (isTyping) {
                    Text("typing…", color = CallTones.TextMuted, fontSize = 14.sp)
                } else {
                    MessageBody(message)
                }
            }
        }
    }
}

@Composable
private fun UserMessage(message: AgentChatMessage) {
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(VastuPrimary)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            MessageBody(message)
        }
    }
}

@Composable
private fun MessageBody(message: AgentChatMessage) {
    Column {
        message.image?.let {
            Image(
                bitmap = it,
                contentDescription = "Sketch",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .width(210.dp)
                    // Photos keep their shape (portrait/landscape); sketches are square.
                    .aspectRatio((it.width.toFloat() / it.height).coerceIn(0.6f, 1.6f))
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        if (message.text.isNotBlank()) {
            Text(message.text, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}
