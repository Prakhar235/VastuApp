package com.vastutalks.app.ui.screens.chat

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vastutalks.app.data.ai.AgentChatStore
import com.vastutalks.app.data.ai.AgentLanguage
import com.vastutalks.app.data.ai.AgentLanguagePref
import com.vastutalks.app.data.ai.AgentMode
import com.vastutalks.app.data.ai.AgentTurn
import com.vastutalks.app.data.ai.PhotoFacing
import com.vastutalks.app.data.ai.RoomScanHandoff
import com.vastutalks.app.data.ai.tr
import com.vastutalks.app.data.ai.VastuAgent
import com.vastutalks.app.data.model.AnanyaAgent
import com.vastutalks.app.ui.components.AgentChatMessage
import com.vastutalks.app.ui.components.AgentChatPage
import com.vastutalks.app.ui.components.CallTones
import com.vastutalks.app.ui.components.CapturedPhoto
import com.vastutalks.app.ui.components.CapturedVideo
import com.vastutalks.app.ui.components.ChatSender
import com.vastutalks.app.ui.components.DarkScreenStatusBar
import com.vastutalks.app.ui.components.PhotoReviewSheet
import com.vastutalks.app.ui.components.ShareMediaActions
import com.vastutalks.app.ui.components.VideoReviewSheet
import com.vastutalks.app.ui.components.rememberPhotoCapture
import com.vastutalks.app.ui.components.rememberPhotoPicker
import com.vastutalks.app.ui.components.rememberVideoCapture
import com.vastutalks.app.ui.theme.OnlineGreen
import com.vastutalks.app.ui.theme.VastuPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The text chat with Ananya: the same agent as the call (VastuAgent in
 * [AgentMode.CHAT], so she writes fuller answers instead of speaking),
 * in English or Hindi (the switch in the header). A photo shared here —
 * from the camera or the gallery — comes back marked up with a numbered
 * list of what she found; a room scan (RoomScanScreen) arrives through
 * [RoomScanHandoff] with its marked photo and read.
 *
 * The chat is saved on the phone (AgentChatStore) as it goes, and picked
 * up again next time — both the messages and what the model remembers —
 * until the user starts a new chat.
 */
class AgentChatViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AgentChatStore(application)
    private var agent = VastuAgent(AgentMode.CHAT)
    private val turnLock = Mutex()
    /** Bumped by [startNewChat], so a reply still on its way to the old chat is dropped. */
    private var chatGeneration = 0
    private var savedMarkable: List<Bitmap> = emptyList()
    private var nextId = 0
    val messages = mutableStateListOf<AgentChatMessage>()
    var pendingTurns by mutableIntStateOf(0)
        private set
    var input by mutableStateOf("")
    /** False until the saved chat has been read, so nothing typed gets mixed into it. */
    var isLoaded by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { store.load() }
            if (saved != null && saved.messages.isNotEmpty()) {
                messages.addAll(saved.messages)
                nextId = saved.messages.maxOf { it.id } + 1
                agent.restore(saved.history, saved.markable)
                savedMarkable = agent.markableImages
                isLoaded = true
            } else {
                isLoaded = true
                ask { agent.greet() }
            }
            RoomScanHandoff.pending.filterNotNull().collect { RoomScanHandoff.take()?.let(::addRoomScan) }
        }
    }

    /** Puts a finished room scan into the chat and into her memory of it. */
    private fun addRoomScan(scan: RoomScanHandoff.Scan) {
        val generation = chatGeneration
        viewModelScope.launch {
            turnLock.withLock {
                if (generation != chatGeneration) return@withLock
                agent.attachRoomScan(scan.walls, scan.analysis)
                val first = scan.walls.first().bitmap
                val scale = 480f / maxOf(first.width, first.height)
                add(
                    tr("I scanned my ${scan.analysis.room.lowercase()} (${scan.walls.size} walls)", "मैंने अपना ${scan.analysis.room} स्कैन किया (${scan.walls.size} दीवारें)"),
                    ChatSender.USER,
                    Bitmap.createScaledBitmap(first, (first.width * scale).toInt(), (first.height * scale).toInt(), true)
                )
                val photo = scan.analysis.findings.firstOrNull()?.photo ?: 0
                val reportId = nextId
                store.saveReport(reportId, scan.walls, scan.analysis)
                add(scan.analysis.asText(), ChatSender.AGENT, scan.analysis.markedPhoto(scan.walls, photo), hasReport = true)
            }
        }
    }

    /** Clears the chat (on screen and on the phone) and has her greet afresh. */
    fun startNewChat() {
        chatGeneration++
        store.clear()
        messages.clear()
        nextId = 0
        savedMarkable = emptyList()
        agent = VastuAgent(AgentMode.CHAT)
        ask { agent.greet() }
    }

    private fun add(text: String, sender: ChatSender, image: Bitmap? = null, hasReport: Boolean = false) {
        messages.add(AgentChatMessage(nextId++, text, sender, image?.asImageBitmap(), hasReport))
        save()
    }

    private fun save() {
        val images = agent.markableImages
        if (images !== savedMarkable) {
            store.saveMarkable(images)
            savedMarkable = images
        }
        store.save(messages, agent.exportHistory(), images.size)
    }

    /** Runs one agent turn at a time, posting her reply (and marked photo) to the chat. */
    private fun ask(request: suspend () -> AgentTurn) {
        val generation = chatGeneration
        pendingTurns++
        viewModelScope.launch {
            try {
                turnLock.withLock {
                    if (generation != chatGeneration) return@withLock
                    val turn = request()
                    if (generation == chatGeneration) add(turn.chatText, ChatSender.AGENT, turn.markedPhoto)
                }
            } finally {
                pendingTurns--
            }
        }
    }

    fun sendTyped() {
        val text = input.trim()
        if (text.isEmpty() || !isLoaded) return
        input = ""
        add(text, ChatSender.USER)
        ask { agent.reply(text) }
    }

    fun sendPhoto(photo: Bitmap, question: String, facing: PhotoFacing?) {
        if (!isLoaded) return
        postMedia(photo, question.ifBlank { defaultQuestion() } + (facing?.let { tr(" · camera facing ${it.direction.label}", " · कैमरा ${it.direction.label} की ओर") } ?: ""))
        ask { agent.reviewPhoto(photo, question, facing) }
    }

    fun sendVideo(video: CapturedVideo, question: String, facing: PhotoFacing?) {
        if (!isLoaded) return
        postMedia(
            video.frames.first(),
            tr("Video (${maxOf(1, video.durationMs / 1000)}s): ", "वीडियो (${maxOf(1, video.durationMs / 1000)} सेकंड): ") + question.ifBlank { defaultQuestion() } +
                (facing?.let { tr(" · started facing ${it.direction.label}", " · शुरुआत ${it.direction.label} की ओर") } ?: "")
        )
        ask { agent.reviewVideo(video.frames, video.durationMs, question, facing) }
    }

    private fun postMedia(preview: Bitmap, caption: String) {
        val scale = 480f / maxOf(preview.width, preview.height)
        add(caption, ChatSender.USER, Bitmap.createScaledBitmap(preview, (preview.width * scale).toInt(), (preview.height * scale).toInt(), true))
    }

    private fun defaultQuestion() = tr("What does Vastu say about this space?", "इस जगह के बारे में वास्तु क्या कहता है?")
}

@Composable
fun AgentChatScreen(
    onBack: () -> Unit,
    /** Starts as audio; the camera can be turned on during the call. */
    onCall: () -> Unit = {},
    onScanRoom: () -> Unit = {},
    /** Opens the full room report saved with that chat message. */
    onOpenReport: (messageId: Int) -> Unit = {},
    viewModel: AgentChatViewModel = viewModel()
) {
    val context = LocalContext.current
    var photoToReview by remember { mutableStateOf<CapturedPhoto?>(null) }
    var videoToReview by remember { mutableStateOf<CapturedVideo?>(null) }
    var confirmNewChat by remember { mutableStateOf(false) }
    val toast: (String) -> Unit = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }

    val takePhoto = rememberPhotoCapture(onError = toast, onCaptured = { photoToReview = it })
    val pickPhoto = rememberPhotoPicker(onError = toast, onPicked = { photoToReview = it })
    val recordVideo = rememberVideoCapture(onError = toast, onCaptured = { videoToReview = it })

    DarkScreenStatusBar()
    Box(modifier = Modifier.fillMaxSize().background(CallTones.Ink)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            ChatHeader(
                isTyping = viewModel.pendingTurns > 0,
                onBack = onBack,
                onCall = onCall,
                onScanRoom = onScanRoom,
                onNewChat = { confirmNewChat = true }
            )
            AgentChatPage(
                messages = viewModel.messages,
                isAgentTyping = viewModel.pendingTurns > 0,
                agentName = AnanyaAgent.NAME,
                avatarSeed = AnanyaAgent.AVATAR_SEED,
                inputText = viewModel.input,
                onInputChange = { viewModel.input = it },
                onSend = viewModel::sendTyped,
                media = ShareMediaActions(takePhoto, pickPhoto, recordVideo, scanRoom = onScanRoom),
                onOpenReport = { onOpenReport(it.id) },
                emptyHint = tr(
                    "Ask a question, send a photo of any room, or scan a whole room — ${AnanyaAgent.NAME.substringBefore(' ')} marks her Vastu suggestions right on the photo.",
                    "सवाल पूछें, किसी कमरे की फ़ोटो भेजें या पूरा कमरा स्कैन करें — ${AnanyaAgent.NAME.substringBefore(' ')} फ़ोटो पर ही निशान लगाकर वास्तु सुझाव देंगी।"
                ),
                modifier = Modifier.weight(1f)
            )
        }

        if (confirmNewChat) {
            AlertDialog(
                onDismissRequest = { confirmNewChat = false },
                title = { Text(tr("Start a new chat?", "नई चैट शुरू करें?")) },
                text = { Text(tr("The old chat and ${AnanyaAgent.NAME.substringBefore(' ')}'s memory of it will both be cleared.", "पुरानी चैट और ${AnanyaAgent.NAME.substringBefore(' ')} को उसकी याद, दोनों मिट जाएँगी।")) },
                confirmButton = {
                    TextButton(onClick = { confirmNewChat = false; viewModel.startNewChat() }) { Text(tr("New chat", "नई चैट")) }
                },
                dismissButton = { TextButton(onClick = { confirmNewChat = false }) { Text(tr("Cancel", "रहने दें")) } }
            )
        }

        photoToReview?.let { photo ->
            PhotoReviewSheet(
                photo = photo,
                agentName = AnanyaAgent.NAME.substringBefore(' '),
                onRetake = { photoToReview = null; takePhoto() },
                onDismiss = { photoToReview = null },
                onSend = { question, facing ->
                    photoToReview = null
                    viewModel.sendPhoto(photo.bitmap, question, facing)
                }
            )
        }

        videoToReview?.let { video ->
            VideoReviewSheet(
                video = video,
                agentName = AnanyaAgent.NAME.substringBefore(' '),
                onRetake = { video.file.delete(); videoToReview = null; recordVideo() },
                onDismiss = { video.file.delete(); videoToReview = null },
                onSend = { question, facing ->
                    videoToReview = null
                    viewModel.sendVideo(video, question, facing)
                    video.file.delete() // only the frames are needed from here on
                }
            )
        }
    }
}

/**
 * Back, Ananya, then room scan, call, and a menu with the reply
 * language and "New chat". Kept to one line so it fits narrow phones.
 */
@Composable
private fun ChatHeader(isTyping: Boolean, onBack: () -> Unit, onCall: () -> Unit, onScanRoom: () -> Unit, onNewChat: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "वापस"), tint = CallTones.TextPrimary)
        }
        AsyncImage(
            model = "https://i.pravatar.cc/300?u=${AnanyaAgent.AVATAR_SEED}",
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(CallTones.SurfaceRaised)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp, end = 4.dp)) {
            Text(
                AnanyaAgent.NAME,
                color = CallTones.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(OnlineGreen))
                Text(
                    if (isTyping) tr("Typing…", "लिख रही हैं…") else tr("AI Vastu expert", "AI वास्तु विशेषज्ञ"),
                    color = CallTones.TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        HeaderIconButton(Icons.Outlined.Explore, tr("Scan a room", "कमरा स्कैन करें"), onClick = onScanRoom)
        if (AnanyaAgent.CALL_ENABLED) {
            HeaderIconButton(Icons.Filled.Call, tr("Call ${AnanyaAgent.NAME}", "${AnanyaAgent.NAME} को कॉल करें"), onClick = onCall)
        }
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { menuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = tr("More options", "और विकल्प"), tint = CallTones.TextPrimary)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = CallTones.SurfaceRaised,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    tr("Reply language", "जवाब की भाषा"),
                    color = CallTones.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                listOf(AgentLanguage.ENGLISH to "English", AgentLanguage.HINDI to "हिंदी").forEach { (language, label) ->
                    val selected = AgentLanguagePref.current == language
                    DropdownMenuItem(
                        text = { Text(label, color = CallTones.TextPrimary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = if (selected) VastuPrimary else androidx.compose.ui.graphics.Color.Transparent
                            )
                        },
                        onClick = { menuOpen = false; AgentLanguagePref.set(language) }
                    )
                }
                HorizontalDivider(color = CallTones.Hairline)
                DropdownMenuItem(
                    text = { Text(tr("New chat", "नई चैट"), color = CallTones.TextPrimary) },
                    leadingIcon = { Icon(Icons.Outlined.EditNote, contentDescription = null, tint = CallTones.TextPrimary) },
                    onClick = { menuOpen = false; onNewChat() }
                )
            }
        }
    }
}

@Composable
private fun HeaderIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(CallTones.SurfaceRaised)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = CallTones.TextPrimary, modifier = Modifier.size(20.dp))
    }
}
