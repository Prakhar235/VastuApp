package com.vastutalks.app.ui.components

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.shadow
import com.vastutalks.app.ui.theme.PrimaryButtonGradient
import com.vastutalks.app.data.ai.tr
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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vastutalks.app.ui.theme.VastuPrimary
import com.vastutalks.app.ui.theme.VastuSaffron

enum class ChatSender { AGENT, USER }

data class AgentChatMessage(
    val id: Int,
    val text: String,
    val sender: ChatSender = ChatSender.AGENT,
    val image: ImageBitmap? = null,
    /** Set on a room scan's report; the full report is saved with the chat (AgentChatStore.loadReport). */
    val hasReport: Boolean = false,
    /** When it was sent (epoch ms); 0 for messages saved before times were kept. */
    val time: Long = System.currentTimeMillis()
)

/**
 * Colours for the agent chat surfaces. The call uses the dark [DarkAgentTones];
 * the text chat provides [LightAgentTones] through [LocalAgentTones].
 */
data class AgentTones(
    val surface: Color,
    val surfaceRaised: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val agentBubble: Color,
    val agentText: Color,
    val highlight: Color,
    val isLight: Boolean
)

val DarkAgentTones = AgentTones(
    surface = Color(0xFF1C1927),
    surfaceRaised = Color(0xFF262236),
    hairline = Color.White.copy(alpha = 0.08f),
    textPrimary = Color(0xFFF4F2FA),
    textMuted = Color(0xFF9D98B3),
    agentBubble = Color(0xFF1C1927),
    agentText = Color.White,
    highlight = VastuSaffron,
    isLight = false
)

val LightAgentTones = AgentTones(
    surface = Color(0xFFF7FAFC),
    surfaceRaised = Color(0xFFF1EFF8),
    hairline = Color(0xFFEFEDF8),
    textPrimary = Color(0xFF1A202C),
    textMuted = Color(0xFF718096),
    agentBubble = Color.White,
    agentText = Color(0xFF1A202C),
    highlight = VastuPrimary,
    isLight = true
)

val LocalAgentTones = staticCompositionLocalOf { DarkAgentTones }

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
 * Ways to show Ananya a space: camera photo, a photo from the gallery, a
 * short video, or (when offered) a guided scan of a whole room.
 */
class ShareMediaActions(
    val takePhoto: () -> Unit,
    val pickPhoto: () -> Unit,
    val recordVideo: () -> Unit,
    val scanRoom: (() -> Unit)? = null
)

/** Drop-down with the three [ShareMediaActions]. */
@Composable
fun ShareMediaMenu(expanded: Boolean, onDismiss: () -> Unit, actions: ShareMediaActions) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = LocalAgentTones.current.surfaceRaised,
        shape = RoundedCornerShape(16.dp)
    ) {
        listOfNotNull(
            actions.scanRoom?.let { Triple(tr("Scan home (video walk-through)", "घर स्कैन करें (वीडियो)"), Icons.Outlined.Explore, it) },
            Triple(tr("Take a photo", "फ़ोटो खींचें"), Icons.Outlined.PhotoCamera, actions.takePhoto),
            Triple(tr("Upload a photo from gallery", "गैलरी से फ़ोटो अपलोड करें"), Icons.Outlined.PhotoLibrary, actions.pickPhoto),
            Triple(tr("Record a video (up to 30 s)", "वीडियो रिकॉर्ड करें (30 सेकंड तक)"), Icons.Outlined.Videocam, actions.recordVideo)
        ).forEach { (label, icon, action) ->
            DropdownMenuItem(
                text = { Text(label, color = LocalAgentTones.current.textPrimary) },
                leadingIcon = { Icon(icon, contentDescription = null, tint = LocalAgentTones.current.textPrimary) },
                onClick = { onDismiss(); action() }
            )
        }
    }
}

/**
 * The conversation with Ananya — the chat page of a call (spoken lines
 * are transcribed in here too) or the whole text-chat screen — and a box
 * to type a question. With [media], the box also gets a button to share
 * a photo or video.
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
    modifier: Modifier = Modifier,
    media: ShareMediaActions? = null,
    /** Opens the full room report saved with [AgentChatMessage.hasReport]. */
    onOpenReport: (AgentChatMessage) -> Unit = {},
    /** One-tap questions shown above the input box. */
    quickPrompts: List<String> = emptyList(),
    onQuickPrompt: (String) -> Unit = {},
    emptyHint: String = tr("Everything you and $agentName say will show up here.", "आप और $agentName जो भी कहेंगे, वह यहाँ दिखेगा।")
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
                        emptyHint,
                        color = LocalAgentTones.current.textMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            items(messages, key = { it.id }) { message ->
                if (message.sender == ChatSender.AGENT) AgentMessage(message, agentName, avatarSeed, onOpenReport = { onOpenReport(message) }) else UserMessage(message)
            }
            if (isAgentTyping) {
                item { AgentMessage(AgentChatMessage(-1, "…"), agentName, avatarSeed, isTyping = true) }
            }
        }

        if (quickPrompts.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                items(quickPrompts) { prompt ->
                    Text(
                        prompt,
                        color = VastuPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (LocalAgentTones.current.isLight) Color(0xFFFAF5FF) else LocalAgentTones.current.surface)
                            .border(1.dp, if (LocalAgentTones.current.isLight) Color(0xFFE9D5FF) else LocalAgentTones.current.hairline, RoundedCornerShape(999.dp))
                            .clickable { onQuickPrompt(prompt) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(LocalAgentTones.current.surface)
                .border(1.dp, LocalAgentTones.current.hairline, RoundedCornerShape(26.dp))
                .padding(start = if (media != null) 6.dp else 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (media != null) {
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(LocalAgentTones.current.surfaceRaised)
                            .clickable { menuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = "Share a photo or video",
                            tint = LocalAgentTones.current.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    ShareMediaMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, actions = media)
                }
            }
            Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                if (inputText.isEmpty()) {
                    Text(tr("Ask $agentName…", "$agentName से पूछें…"), color = LocalAgentTones.current.textMuted, fontSize = 14.sp)
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    textStyle = TextStyle(color = LocalAgentTones.current.textPrimary, fontSize = 14.sp, fontFamily = com.vastutalks.app.ui.theme.Poppins),
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
                    .then(if (canSend) Modifier.background(PrimaryButtonGradient) else Modifier.background(LocalAgentTones.current.surfaceRaised))
                    .clickable(enabled = canSend) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) Color.White else LocalAgentTones.current.textMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun AgentMessage(message: AgentChatMessage, agentName: String, avatarSeed: String, isTyping: Boolean = false, onOpenReport: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        AiOrb(30.dp, ringWidth = 2.dp, iconSize = 13.dp, animated = false)
        Column(modifier = Modifier.padding(start = 10.dp).widthIn(max = 290.dp)) {
            Text(agentName, color = LocalAgentTones.current.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .then(
                        if (LocalAgentTones.current.isLight) Modifier.shadow(8.dp, RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp), ambientColor = Color(0x1AA855F7), spotColor = Color(0x33A855F7))
                        else Modifier
                    )
                    .background(LocalAgentTones.current.agentBubble, RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .border(1.dp, LocalAgentTones.current.hairline, RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (isTyping) {
                    Text(tr("Typing…", "लिख रही हैं…"), color = LocalAgentTones.current.textMuted, fontSize = 14.sp)
                } else {
                    MessageBody(message)
                }
            }
            if (message.hasReport) {
                Text(
                    tr("View full report", "पूरी रिपोर्ट देखें"),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(VastuPrimary)
                        .clickable(onClick = onOpenReport)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
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
                .background(PrimaryButtonGradient)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            MessageBody(message)
        }
    }
}

@Composable
internal fun MessageBody(message: AgentChatMessage, textSize: androidx.compose.ui.unit.TextUnit = 14.sp) {
    var fullScreen by remember { mutableStateOf(false) }
    Column {
        message.image?.let {
            Image(
                bitmap = it,
                contentDescription = if (message.sender == ChatSender.AGENT) "Photo with suggestions marked" else "Shared image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    // Her marked-up photos are bigger so the labels are readable.
                    .width(if (message.sender == ChatSender.AGENT) 262.dp else 210.dp)
                    // Photos keep their shape (portrait/landscape); sketches are square.
                    .aspectRatio((it.width.toFloat() / it.height).coerceIn(0.6f, 1.6f))
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { fullScreen = true }
            )
            if (fullScreen) FullScreenImage(it) { fullScreen = false }
        }
        if (message.text.isNotBlank()) {
            Text(
                if (message.sender == ChatSender.AGENT) highlightSuggestions(message.text, LocalAgentTones.current.highlight) else AnnotatedString(message.text),
                color = if (message.sender == ChatSender.AGENT) LocalAgentTones.current.agentText else Color.White,
                fontSize = textSize,
                lineHeight = 20.sp
            )
        }
    }
}

/**
 * Ananya wraps each suggestion in **double asterisks** (see VastuAgent's
 * prompt); show those parts bold in saffron on a soft saffron wash so
 * the advice stands out from the explanation. An unclosed ** is shown
 * as is.
 */
private fun highlightSuggestions(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val start = text.indexOf("**", i)
        val end = if (start >= 0) text.indexOf("**", start + 2) else -1
        if (start < 0 || end < 0) {
            append(text.substring(i))
            break
        }
        append(text.substring(i, start))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold, background = color.copy(alpha = 0.1f))) {
            append(text.substring(start + 2, end))
        }
        i = end + 2
    }
}

/** Tap-to-close full-screen view of a chat image, so marks and labels can be read. */
@Composable
private fun FullScreenImage(image: ImageBitmap, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = image,
                contentDescription = "Full-screen image",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
