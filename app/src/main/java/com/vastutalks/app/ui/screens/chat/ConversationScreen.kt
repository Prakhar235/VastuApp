package com.vastutalks.app.ui.screens.chat

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
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
    val otherName: String = URLDecoder.decode(savedState.get<String>("otherName").orEmpty(), "UTF-8")
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
        viewModelScope.launch { otherExpert = users.getExpert(otherUid).getOrNull() }
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
    onCall: (expertUid: String, expertName: String, video: Boolean) -> Unit,
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
            .background(SurfaceLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = Color(0xFF2B2B2B))
            }
            ProfileAvatar(otherPhoto, viewModel.otherName, size = 40.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(viewModel.otherName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
                viewModel.otherExpert?.let { Text(it.displaySpecialty, fontSize = 12.sp, color = VastuPrimary) }
            }
            // Chatting with an expert: the other two ways to reach them.
            viewModel.otherExpert?.let { expert ->
                IconButton(onClick = { onCall(expert.uid, expert.name, false) }) {
                    Icon(Icons.Filled.Call, contentDescription = "Audio call", tint = VastuPrimary)
                }
                IconButton(onClick = { onCall(expert.uid, expert.name, true) }) {
                    Icon(Icons.Filled.Videocam, contentDescription = "Video call", tint = VastuPrimary)
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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

        Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 10.dp, vertical = 8.dp)) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "Add a photo", tint = VastuPrimary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = Color.White) {
                        DropdownMenuItem(
                            text = { Text("Take a photo") },
                            leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                            onClick = { menuOpen = false; takePhoto() }
                        )
                        DropdownMenuItem(
                            text = { Text("Choose from gallery") },
                            leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                            onClick = { menuOpen = false; pickPhoto() }
                        )
                    }
                }
                OutlinedTextField(
                    value = viewModel.input,
                    onValueChange = { viewModel.input = it.take(2000) },
                    placeholder = { Text("Message…", color = TextHint) },
                    textStyle = TextStyle(color = Color(0xFF2B2B2B), fontSize = 14.sp),
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = InputFill,
                        focusedContainerColor = InputFill,
                        unfocusedBorderColor = InputFill,
                        focusedBorderColor = VastuPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                val canSend = (viewModel.input.isNotBlank() || viewModel.image != null) && !viewModel.sending
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (canSend) VastuPrimary else VastuPrimary.copy(alpha = 0.35f))
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
    val shape = if (mine) RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp) else RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(if (mine) VastuPrimary else Color.White)
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
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.text.isNotBlank()) {
                    Text(message.text, fontSize = 14.sp, lineHeight = 20.sp, color = if (mine) Color.White else Color(0xFF2B2B2B))
                }
                Text(
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.createdAtMillis)),
                    fontSize = 10.sp,
                    color = if (mine) Color.White.copy(alpha = 0.75f) else TextSecondary,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
