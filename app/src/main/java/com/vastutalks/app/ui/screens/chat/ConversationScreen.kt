package com.vastutalks.app.ui.screens.chat

import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import com.vastutalks.app.ui.components.softWashBackground
import com.vastutalks.app.ui.theme.Poppins
import com.vastutalks.app.ui.theme.PrimaryButtonGradient
import com.vastutalks.app.ui.theme.TextPrimary
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vastutalks.app.data.model.ChatMessage
import com.vastutalks.app.data.model.ExpertProfile
import com.vastutalks.app.data.model.UserProfile
import com.vastutalks.app.data.repository.ChatRepository
import com.vastutalks.app.data.repository.ProfilePhotos
import com.vastutalks.app.data.repository.UserRepository
import com.vastutalks.app.ui.components.ProfileAvatar
import com.vastutalks.app.ui.components.rememberPhotoCapture
import com.vastutalks.app.ui.components.rememberPhotoPicker
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.InputFill
import com.vastutalks.app.ui.theme.SurfaceLight
import com.vastutalks.app.ui.theme.TextHint
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuPrimary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One chat between the signed-in user and [otherUid] (a user or an expert). */
class ConversationViewModel(savedState: SavedStateHandle) : ViewModel() {
    private val chats = ChatRepository()
    private val users = UserRepository()
    val otherUid: String = checkNotNull(savedState["otherUid"])
    /** As passed in; filled from their expert entry or the chat when that was blank. */
    var otherName by mutableStateOf(URLDecoder.decode(savedState.get<String>("otherName").orEmpty(), "UTF-8"))
        private set
    val myUid: String = chats.myUid.orEmpty()
    val chatId: String = ChatRepository.chatIdFor(myUid, otherUid)

    val messages: StateFlow<List<ChatMessage>> = chats.listenMessages(chatId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The other person's photo: from the expert directory, or from the chat (users' profiles are private). */
    val chatPhoto: StateFlow<String> = chats.listenMyChats()
        .map { list -> list.firstOrNull { it.id == chatId }?.photos?.get(otherUid).orEmpty() }
        .catch { emit("") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val me: StateFlow<UserProfile?> = users.listenMyProfile()
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Set if the other person is an expert (then calls are offered too). */
    var otherExpert by mutableStateOf<ExpertProfile?>(null)
        private set

    var input by mutableStateOf("")
    var image by mutableStateOf<Bitmap?>(null)
    var sending by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            otherExpert = users.getExpert(otherUid).getOrNull()
            if (otherName.isBlank()) otherName = otherExpert?.name.orEmpty()
            if (otherName.isBlank()) {
                otherName = runCatching { chats.listenMyChats().first() }.getOrNull()
                    ?.firstOrNull { it.id == chatId }?.names?.get(otherUid).orEmpty()
                    .ifBlank { "VastuTalks user" }
            }
        }
    }

    fun markRead() {
        viewModelScope.launch { chats.markRead(chatId) }
    }

    fun send() {
        val profile = me.value
        if ((input.isBlank() && image == null) || sending) return
        if (profile == null) {
            error = "Still loading your profile — try again in a moment."
            return
        }
        sending = true
        error = null
        val text = input
        val picture = image
        viewModelScope.launch {
            chats.send(
                otherUid = otherUid,
                me = profile.name to profile.photo,
                other = otherName to (otherExpert?.photo.orEmpty()),
                text = text,
                image = picture
            ).fold(
                onSuccess = {
                    input = ""
                    image = null
                },
                onFailure = { error = "Couldn't send: ${it.message}" }
            )
            sending = false
        }
    }
}

@Composable
fun ConversationScreen(
    onBack: () -> Unit,
    /** Starts as audio; either side can turn the camera on during the call. */
    onCall: (expertUid: String, expertName: String) -> Unit,
    /** False when the chat is opened from inside a call (no call button then). */
    showCallButton: Boolean = true,
    viewModel: ConversationViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val chatPhoto by viewModel.chatPhoto.collectAsState()
    val otherPhoto = viewModel.otherExpert?.photo?.ifBlank { null } ?: chatPhoto
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val toast: (String) -> Unit = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
    val takePhoto = rememberPhotoCapture(onError = toast, onCaptured = { viewModel.image = it.bitmap })
    val pickPhoto = rememberPhotoPicker(onError = toast, onPicked = { viewModel.image = it.bitmap })

    // While this chat is open its messages are read, and don't also pop up as notifications.
    DisposableEffect(viewModel.chatId) {
        ChatRepository.openChatId = viewModel.chatId
        onDispose { if (ChatRepository.openChatId == viewModel.chatId) ChatRepository.openChatId = null }
    }
    LaunchedEffect(messages.size) {
        viewModel.markRead()
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .softWashBackground()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RectangleShape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
                .background(Color.White.copy(alpha = 0.92f))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = TextPrimary)
            }
            ProfileAvatar(otherPhoto, viewModel.otherName, size = 38.5.dp, shape = RoundedCornerShape(14.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 10.5.dp)) {
                Text(viewModel.otherName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                val expert = viewModel.otherExpert
                if (expert != null) {
                    val now = System.currentTimeMillis()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.25.dp).clip(CircleShape).background(if (expert.isPresentAt(now)) Color(0xFF51CF66) else com.vastutalks.app.ui.screens.home.expertStatusColor(expert, now)))
                        Text(
                            " " + if (expert.isPresentAt(now)) "Online" else com.vastutalks.app.ui.screens.home.expertStatusLabel(expert, now),
                            fontSize = 10.5.sp, color = TextSecondary, fontWeight = FontWeight.Medium, maxLines = 1
                        )
                    }
                }
            }
            // Chatting with an expert: the other way to reach them.
            viewModel.otherExpert?.takeIf { showCallButton }?.let { expert ->
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(35.5.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(VastuPrimary.copy(alpha = 0.1f))
                        .clickable { onCall(expert.uid, expert.name) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Videocam, contentDescription = "Video call", tint = VastuPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Session bar: time in this chat, cost (free for now) and the sample wallet.
        if (viewModel.otherExpert != null) {
            val started = remember { System.currentTimeMillis() }
            val elapsed by produceState(0L) {
                while (true) { value = (System.currentTimeMillis() - started) / 1000; kotlinx.coroutines.delay(1000) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF6C5CE7), Color(0xFF9333EA), Color(0xFF4F46E5))))
                    .padding(horizontal = 17.5.dp, vertical = 10.5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SessionChip {
                    Icon(Icons.Outlined.Schedule, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(" %02d:%02d".format(elapsed / 60, elapsed % 60), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.25.sp)
                }
                Box(Modifier.width(10.5.dp))
                SessionChip {
                    Text("Cost: ", color = Color.White.copy(alpha = 0.8f), fontSize = 10.5.sp)
                    Text(if (com.vastutalks.app.data.model.CallPricing.PAID_CALLS) "₹0.00" else "Free", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.25.sp)
                }
                Box(Modifier.weight(1f))
                SessionChip {
                    Icon(Icons.Outlined.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(" " + com.vastutalks.app.data.model.SampleShop.balanceText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.25.sp)
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(17.5.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Say hello to ${viewModel.otherName.substringBefore(' ')} — you can also send photos of your space.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 24.dp, end = 24.dp)
                    )
                }
            }
            items(messages, key = { it.id }) { MessageBubble(it, mine = it.senderUid == viewModel.myUid) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RectangleShape, ambientColor = Color(0x0D000000), spotColor = Color(0x0D000000))
                .background(Color.White.copy(alpha = 0.92f))
                .padding(17.5.dp)
        ) {
            viewModel.image?.let { picked ->
                Box(modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                    Image(
                        remember(picked) { picked.asImageBitmap() },
                        contentDescription = "Photo to send",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { viewModel.image = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            viewModel.error?.let { Text(it, fontSize = 12.sp, color = Danger, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.5.dp)) {
                ComposerIcon(Icons.Outlined.Image, "Choose a photo") { pickPhoto() }
                ComposerIcon(Icons.Outlined.AttachFile, "Take a photo") { takePhoto() }
                ComposerIcon(Icons.Outlined.SentimentSatisfied, "Emoji") { viewModel.input += "🙏" }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = viewModel.input,
                    onValueChange = { viewModel.input = it.take(2000) },
                    placeholder = { Text("Type your message...", color = TextHint, fontSize = 13.sp) },
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, fontFamily = Poppins),
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFFF7FAFC),
                        focusedContainerColor = Color(0xFFF7FAFC),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = VastuPrimary.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.weight(1f)
                )
                val canSend = (viewModel.input.isNotBlank() || viewModel.image != null) && !viewModel.sending
                Box(
                    modifier = Modifier
                        .padding(start = 7.dp)
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .then(if (canSend) Modifier.background(PrimaryButtonGradient) else Modifier.background(VastuPrimary.copy(alpha = 0.35f)))
                        .clickable(enabled = canSend) { viewModel.send() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, mine: Boolean) {
    val image = remember(message.image) {
        ProfilePhotos.decode(message.image)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    val shape = if (mine) RoundedCornerShape(21.dp, 21.dp, 6.dp, 21.dp) else RoundedCornerShape(21.dp, 21.dp, 21.dp, 6.dp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .wrapContentWidth(if (mine) Alignment.End else Alignment.Start)
                .widthIn(max = 280.dp)
                .shadow(8.dp, shape, ambientColor = Color(0x1AA855F7), spotColor = Color(0x33A855F7))
                .clip(shape)
                .then(if (mine) Modifier.background(PrimaryButtonGradient) else Modifier.background(Color.White))
                .padding(if (image != null) 4.dp else 0.dp)
        ) {
            image?.let {
                Image(
                    it,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(14.dp))
                )
            }
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.5.dp)) {
                if (message.text.isNotBlank()) {
                    Text(message.text, fontSize = 12.25.sp, lineHeight = 20.sp, color = if (mine) Color.White else TextPrimary, fontWeight = if (mine) FontWeight.Medium else FontWeight.Normal)
                }
                Text(
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.createdAtMillis)),
                    fontSize = 10.5.sp,
                    color = if (mine) Color.White.copy(alpha = 0.7f) else TextSecondary,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun SessionChip(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun ComposerIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(end = 7.dp)
            .size(32.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = VastuPrimary, modifier = Modifier.size(18.dp))
    }
}
