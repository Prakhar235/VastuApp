package com.vastutalks.app.ui.screens.chat

import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.draw.alpha
import com.vastutalks.app.ui.theme.TextHint
import com.vastutalks.app.ui.theme.TextSecondary
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vastutalks.app.ui.components.LightAgentTones
import com.vastutalks.app.ui.components.LocalAgentTones
import com.vastutalks.app.ui.components.softWashBackground
import com.vastutalks.app.ui.theme.TextPrimary
import com.vastutalks.app.ui.theme.VastuPrimary
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vastutalks.app.data.ai.AgentChatStore
import com.vastutalks.app.data.ai.AgentMode
import com.vastutalks.app.data.ai.AgentTurn
import com.vastutalks.app.data.ai.PhotoFacing
import com.vastutalks.app.data.ai.RoomScanHandoff
import com.vastutalks.app.data.ai.tr
import com.vastutalks.app.data.ai.VastuAgent
import com.vastutalks.app.data.model.AnanyaAgent
import com.vastutalks.app.ui.components.AgentChatMessage
import com.vastutalks.app.ui.components.AgentChatPage
import com.vastutalks.app.ui.components.AgentHeader
import com.vastutalks.app.ui.components.AgentHeaderButton
import com.vastutalks.app.ui.components.agentScreenBackground
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
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
                show(saved)
                isLoaded = true
            } else {
                isLoaded = true
                ask { agent.greet() }
            }
            RoomScanHandoff.pending.filterNotNull().collect { RoomScanHandoff.take()?.let(::addRoomScan) }
        }
        // A call started from this chat added to it: pick that up for when they come back.
        viewModelScope.launch {
            AgentChatStore.callSaves.drop(1).collect {
                val saved = withContext(Dispatchers.IO) { store.load() } ?: return@collect
                turnLock.withLock {
                    chatGeneration++ // drop a reply still on its way to the chat as it was
                    show(saved)
                }
            }
        }
    }

    private fun show(saved: AgentChatStore.Saved) {
        messages.clear()
        messages.addAll(saved.messages)
        nextId = (saved.messages.maxOfOrNull { it.id } ?: -1) + 1
        agent.restore(saved.history, saved.markable)
        savedMarkable = agent.markableImages
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
                    tr("I scanned my ${scan.analysis.room.lowercase()} with a video", "मैंने वीडियो से अपना ${scan.analysis.room} स्कैन किया"),
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

    /** Turns a spoken question (16 kHz WAV from the mic) into text, then sends it like a typed one. */
    fun sendVoice(wav: ByteArray, onFail: (String) -> Unit) {
        if (!isLoaded) return
        pendingTurns++
        viewModelScope.launch {
            val text = try { agent.transcribe(wav) } finally { pendingTurns-- }
            if (text.isNullOrBlank()) onFail(tr("Couldn't catch that — try again.", "सुनाई नहीं दिया — फिर से बोलें।"))
            else { input = text; sendTyped() }
        }
    }

    /** The full room report saved with a scan message, for its score card. */
    suspend fun loadReport(messageId: Int) = withContext(Dispatchers.IO) { store.loadReport(messageId) }

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
    val toast: (String) -> Unit = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }

    val takePhoto = rememberPhotoCapture(onError = toast, onCaptured = { photoToReview = it })
    val pickPhoto = rememberPhotoPicker(onError = toast, onPicked = { photoToReview = it })
    val recordVideo = rememberVideoCapture(onError = toast, onCaptured = { videoToReview = it })

    var attachOpen by remember { mutableStateOf(false) }
    var micOn by remember { mutableStateOf(false) }
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) micOn = true else toast(tr("Allow the microphone to speak your question.", "सवाल बोलने के लिए माइक की अनुमति दें।")) }
    val listener = com.vastutalks.app.ui.components.rememberCallerListener(active = micOn) { wav ->
        micOn = false
        viewModel.sendVoice(wav, toast)
    }
    val toggleMic = {
        if (micOn) micOn = false
        else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) micOn = true
        else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    CompositionLocalProvider(LocalAgentTones provides LightAgentTones) {
    Box(modifier = Modifier.fillMaxSize().softWashBackground()) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RectangleShape, ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000))
                    .background(Color.White.copy(alpha = 0.8f))
                    .statusBarsPadding()
            ) {
                ChatHeader(onBack = onBack, onCall = onCall)
            }
            FreeMinutesBar()
            DesignAgentChat(
                viewModel = viewModel,
                isTyping = viewModel.pendingTurns > 0,
                listening = micOn,
                hearingSpeech = listener.isHearingSpeech,
                onAttach = { attachOpen = true },
                onToggleMic = toggleMic,
                onOpenReport = onOpenReport,
                modifier = Modifier.weight(1f)
            )
        }

        if (attachOpen) {
            AttachSheet(
                onDismiss = { attachOpen = false },
                onPhoto = { attachOpen = false; takePhoto() },
                onVideo = { attachOpen = false; recordVideo() },
                onGallery = { attachOpen = false; pickPhoto() },
                onScanRoom = { attachOpen = false; onScanRoom() }
            )
        }

        photoToReview?.let { photo ->
            PhotoReviewSheet(
                photo = photo,
                agentName = AnanyaAgent.NAME,
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
                agentName = AnanyaAgent.NAME,
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
}

/** Anayra's square avatar from the design: violet gradient tile with the compass mark. */
@Composable
internal fun AnayraAvatar(size: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp = 14.dp) {
    Box(
        Modifier
            .size(size)
            .shadow(6.dp, RoundedCornerShape(radius))
            .clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(listOf(Color(0xFFC084FC), Color(0xFF4F46E5)))),
        contentAlignment = Alignment.Center
    ) {
        com.vastutalks.app.ui.components.CompassMark(size * 0.62f)
    }
}

/** Back, Anayra · Online, then Talk and the EN | हिं toggle. */
@Composable
private fun ChatHeader(onBack: () -> Unit, onCall: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 17.5.dp, vertical = 10.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = tr("Back", "वापस"), tint = TextPrimary, modifier = Modifier.size(24.dp))
        }
        Box(Modifier.padding(start = 6.dp)) {
            AnayraAvatar(38.5.dp)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(2.5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
            )
        }
        Column(Modifier.weight(1f).padding(start = 10.5.dp)) {
            Text(tr(AnanyaAgent.NAME, AnanyaAgent.NAME_HI), fontSize = 12.25.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.25.dp).clip(CircleShape).background(Color(0xFF51CF66)))
                Text(tr(" Online", " ऑनलाइन"), fontSize = 10.5.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            }
        }
        if (AnanyaAgent.CALL_ENABLED) {
            Row(
                Modifier
                    .height(34.dp)
                    .shadow(6.dp, RoundedCornerShape(999.dp), spotColor = VastuPrimary)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), VastuPrimary)))
                    .clickable(onClickLabel = tr("Talk to ${AnanyaAgent.NAME}", "${AnanyaAgent.NAME_HI} से बात करें"), onClick = onCall)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Text(tr(" Talk", " बात करें"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        com.vastutalks.app.ui.components.DesignLangToggle(Modifier.padding(start = 8.dp))
    }
}

private const val FREE_SECONDS = 10 * 60
private const val RATE_PER_MIN = 5

/**
 * The design's dark bar under the header: time in this chat, the free
 * 10 minutes counting down (then ₹5/min), the sample wallet and a progress
 * bar. Nothing is actually charged — there's no payment backend yet.
 */
@Composable
private fun FreeMinutesBar() {
    val started = remember { System.currentTimeMillis() }
    val elapsed by androidx.compose.runtime.produceState(0) {
        while (true) { value = ((System.currentTimeMillis() - started) / 1000).toInt(); kotlinx.coroutines.delay(1000) }
    }
    val left = (FREE_SECONDS - elapsed).coerceAtLeast(0)
    val free = left > 0
    val cost = if (free) 0.0 else (elapsed - FREE_SECONDS) / 60.0 * RATE_PER_MIN
    fun mmss(s: Int) = "%02d:%02d".format(s / 60, s % 60)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Color(0xFF1A202C), Color(0xFF312E81))))
            .padding(start = 17.5.dp, end = 17.5.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BarChip(Color.White.copy(alpha = 0.12f)) {
                Icon(Icons.Outlined.Schedule, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(" " + mmss(elapsed), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.width(8.dp))
            BarChip(if (free) Color(0x2E22C55E) else Color(0x2EFFD93D)) {
                Text(
                    if (free) tr("Free · ${mmss(left)} left", "मुफ़्त · ${mmss(left)} बाकी")
                    else tr("₹$RATE_PER_MIN/min · ₹%.2f", "₹$RATE_PER_MIN/मिनट · ₹%.2f").format(cost),
                    color = if (free) Color(0xFF86EFAC) else Color(0xFFFFD93D),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(Modifier.weight(1f))
            BarChip(Color.White.copy(alpha = 0.2f)) {
                Icon(Icons.Outlined.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(" ₹%.2f".format(com.vastutalks.app.data.model.SampleShop.balance.toDouble()), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.14f))) {
            Box(
                Modifier
                    .fillMaxWidth(if (free) left / FREE_SECONDS.toFloat() else 1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (free) Color(0xFF22C55E) else Color(0xFFFFD93D))
            )
        }
        Text(
            if (free) tr("First 10 min free, then ₹$RATE_PER_MIN/min from your balance", "पहले 10 मिनट मुफ़्त, फिर ₹$RATE_PER_MIN/मिनट आपके बैलेंस से कटेगा")
            else tr("Now charging ₹$RATE_PER_MIN/min from your balance", "₹$RATE_PER_MIN/मिनट शुल्क लागू — बैलेंस से कट रहा है"),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.5.sp
        )
    }
}

@Composable
private fun BarChip(background: Color, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(background).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Messages, quick prompts, the listening banner and the composer — styled like the design's chat. */
@Composable
private fun DesignAgentChat(
    viewModel: AgentChatViewModel,
    isTyping: Boolean,
    listening: Boolean,
    hearingSpeech: Boolean,
    onAttach: () -> Unit,
    onToggleMic: () -> Unit,
    onOpenReport: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val messages = viewModel.messages
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(messages.size, isTyping) {
        val last = messages.size - 1 + if (isTyping) 1 else 0
        if (last >= 0) listState.animateScrollToItem(last)
    }
    Column(modifier.fillMaxWidth()) {
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(17.5.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(messages.size, key = { messages[it].id }) { i ->
                val m = messages[i]
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (m.hasReport) ScoreCard(viewModel, m.id, onRemedies = {
                        viewModel.input = tr("Remedies for the problems in this room", "इस कमरे की समस्याओं के उपाय बताएं"); viewModel.sendTyped()
                    }, onOpenReport = { onOpenReport(m.id) })
                    DesignBubble(m)
                }
            }
            if (isTyping) item { TypingRow() }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .shadow(10.dp, RectangleShape, ambientColor = Color(0x0D000000), spotColor = Color(0x0D000000))
                .background(Color.White.copy(alpha = 0.8f))
                .padding(vertical = 17.5.dp)
        ) {
            if (!isTyping) {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 17.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    val prompts = listOf(
                        tr("Best direction for main door?", "मुख्य दरवाज़े की दिशा?"),
                        tr("Bedroom Vastu tips", "बेडरूम वास्तु टिप्स"),
                        tr("Kitchen placement", "रसोई की जगह"),
                        tr("Home office desk", "होम ऑफिस डेस्क")
                    )
                    items(prompts.size) { i ->
                        Text(
                            prompts[i],
                            color = VastuPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFFAF5FF))
                                .border(1.dp, Color(0xFFE9D5FF), RoundedCornerShape(999.dp))
                                .clickable { viewModel.input = prompts[i]; viewModel.sendTyped() }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
            if (listening) {
                Row(
                    Modifier
                        .padding(start = 17.5.dp, end = 17.5.dp, bottom = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFEF2F2))
                        .border(1.dp, Color(0xFFFEE2E2), RoundedCornerShape(14.dp))
                        .clickable(onClick = onToggleMic)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WaveBars(active = hearingSpeech)
                    Text(
                        tr("Listening… speak in English", "सुन रही हूँ… हिंदी में बोलें"),
                        fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB91C1C),
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                    Text(tr("Tap mic to stop", "रोकने के लिए टैप करें"), fontSize = 10.5.sp, color = Color(0xFFEF4444))
                }
            }
            Row(Modifier.padding(horizontal = 17.5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF7FAFC))
                        .padding(horizontal = 14.dp, vertical = 10.5.dp)
                ) {
                    if (viewModel.input.isEmpty()) Text(tr("Type or speak your question…", "सवाल लिखें या बोलें…"), color = TextHint, fontSize = 12.25.sp)
                    androidx.compose.foundation.text.BasicTextField(
                        value = viewModel.input,
                        onValueChange = { viewModel.input = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 12.25.sp, fontFamily = com.vastutalks.app.ui.theme.Poppins),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(VastuPrimary),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { viewModel.sendTyped() }),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ComposerButton(Icons.Outlined.PhotoCamera, tr("Camera or upload", "कैमरा या अपलोड"), Color(0xFFF1EFF8), VastuPrimary, onAttach)
                ComposerButton(Icons.Outlined.Mic, tr("Speak", "बोलें"), if (listening) Color(0xFFEF4444) else Color(0xFFF1EFF8), if (listening) Color.White else VastuPrimary, onToggleMic)
                val canSend = viewModel.input.isNotBlank()
                Box(
                    Modifier
                        .padding(start = 7.dp)
                        .size(39.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(VastuPrimary, Color(0xFF4F46E5))))
                        .alpha(if (canSend) 1f else 0.5f)
                        .clickable(enabled = canSend) { viewModel.sendTyped() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, tr("Send", "भेजें"), tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ComposerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bg: Color, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 7.dp)
            .size(39.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** Twelve red bars that bounce while the mic hears speech. */
@Composable
private fun WaveBars(active: Boolean) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(
        0f, 6.2832f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)),
        label = "phase"
    )
    Row(Modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(12) { i ->
            val k: Float = if (active) 0.3f + 0.7f * ((kotlin.math.sin(phase + i * 0.6f) + 1f) / 2f) else 0.3f
            Box(Modifier.width(3.dp).height(20.dp * k).clip(RoundedCornerShape(2.dp)).background(Color(0xFFEF4444)))
        }
    }
}

@Composable
private fun DesignBubble(m: AgentChatMessage) {
    val mine = m.sender == ChatSender.USER
    val shape = if (mine) RoundedCornerShape(21.dp, 21.dp, 6.dp, 21.dp) else RoundedCornerShape(21.dp, 21.dp, 21.dp, 6.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier
                .fillMaxWidth(0.75f)
                .wrapContentWidth(if (mine) Alignment.End else Alignment.Start)
                .shadow(8.dp, shape, ambientColor = Color(0x1AA855F7), spotColor = if (mine) Color(0x33A855F7) else Color(0x1AA855F7))
                .clip(shape)
                .then(if (mine) Modifier.background(Brush.linearGradient(listOf(VastuPrimary, Color(0xFF4F46E5)))) else Modifier.background(Color.White))
                .padding(horizontal = 14.dp, vertical = 10.5.dp)
        ) {
            com.vastutalks.app.ui.components.MessageBody(m, textSize = 12.25.sp)
        }
    }
}

@Composable
private fun TypingRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .shadow(8.dp, RoundedCornerShape(21.dp, 21.dp, 21.dp, 6.dp), ambientColor = Color(0x1AA855F7), spotColor = Color(0x1AA855F7))
                .clip(RoundedCornerShape(21.dp, 21.dp, 21.dp, 6.dp))
                .background(Color.White)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(Color(0xFFC084FC), Color(0xFFA78BFA), Color(0xFF6366F1)).forEach { Box(Modifier.size(6.dp).clip(CircleShape).background(it)) }
        }
        Text(tr("${AnanyaAgent.NAME} is analysing…", "${AnanyaAgent.NAME_HI} विश्लेषण कर रही है…"), fontSize = 10.5.sp, color = TextSecondary, modifier = Modifier.padding(start = 8.dp))
    }
}

/** The design's "Your Vastu Score" card for a room scan: ring, grade, zone list, Get remedies. */
@Composable
private fun ScoreCard(viewModel: AgentChatViewModel, messageId: Int, onRemedies: () -> Unit, onOpenReport: () -> Unit) {
    val scan by androidx.compose.runtime.produceState<com.vastutalks.app.data.ai.RoomScanHandoff.Scan?>(null, messageId) { value = viewModel.loadReport(messageId) }
    val analysis = scan?.analysis ?: return
    val score = analysis.score.coerceIn(0, 100)
    val grade = when {
        score >= 85 -> tr("Excellent", "उत्कृष्ट")
        score >= 70 -> tr("Good", "अच्छा")
        score >= 50 -> tr("Fair", "ठीक")
        else -> tr("Needs work", "सुधार ज़रूरी")
    }
    val gradeColor = if (score >= 70) Color(0xFF16A34A) else if (score >= 50) Color(0xFFCA8A04) else Color(0xFFE53E3E)
    val zones = analysis.findings.take(4)
    val balanced = analysis.findings.count { it.severity == com.vastutalks.app.data.ai.Severity.GOOD }
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(20.dp), spotColor = Color(0x736C5CE7), ambientColor = Color(0x1A6C5CE7))
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFEFEDF8), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.size(76.dp)) {
                    val stroke = 7.dp.toPx()
                    drawArc(Color(0xFFEEEDF4), 0f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    drawArc(VastuPrimary, -90f, 360f * score / 100f, false, topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$score", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 21.sp)
                    Text("/100", fontSize = 9.5.sp, color = TextHint)
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(tr("Your Vastu Score", "आपका वास्तु स्कोर"), fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                Text(grade, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = gradeColor, modifier = Modifier.padding(top = 2.dp))
                Text(
                    tr("$balanced of ${analysis.findings.size} key points balanced", "${analysis.findings.size} में से $balanced मुख्य बिंदु संतुलित"),
                    fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Column {
            zones.forEach { f ->
                val ok = f.severity == com.vastutalks.app.data.ai.Severity.GOOD
                val c = if (ok) Color(0xFF16A34A) else Color(0xFFE53E3E)
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF3F2F8)))
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(f.objectName.ifBlank { f.title }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
                        f.zone?.let { Text(it.label, fontSize = 10.5.sp, color = TextHint, modifier = Modifier.padding(top = 1.dp)) }
                    }
                    Text(if (ok) tr("Balanced", "संतुलित") else tr("Needs remedy", "उपाय ज़रूरी"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(VastuPrimary).clickable(onClick = onRemedies),
                contentAlignment = Alignment.Center
            ) { Text(tr("Get remedies", "उपाय देखें"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3F0FF)).clickable(onClick = onOpenReport),
                contentAlignment = Alignment.Center
            ) { Text(tr("Full report", "पूरी रिपोर्ट"), color = VastuPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** "Add photo or video": camera, video, gallery, plus the wall-by-wall room scan; the compass note at the bottom. */
@Composable
private fun AttachSheet(onDismiss: () -> Unit, onPhoto: () -> Unit, onVideo: () -> Unit, onGallery: () -> Unit, onScanRoom: () -> Unit) {
    com.vastutalks.app.ui.components.DesignSheet(
        onDismiss = onDismiss,
        radius = 24.dp,
        scrim = Color(0x730F0B2E),
        contentPadding = 20.dp,
        showHandle = true
    ) {
        Text(tr("Add photo or video", "फ़ोटो या वीडियो जोड़ें"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AttachOption(Icons.Outlined.PhotoCamera, Color(0xFFF3F0FF), VastuPrimary, tr("Take photo", "फ़ोटो लें"), tr("Open camera and capture a room", "कैमरा खोलें और कमरे की फ़ोटो लें"), onPhoto)
            AttachOption(Icons.Outlined.Videocam, Color(0xFFFEF2F2), Color(0xFFEF4444), tr("Record video", "वीडियो रिकॉर्ड करें"), tr("Walk slowly around the room", "कमरे में धीरे-धीरे घूमें"), onVideo)
            AttachOption(Icons.Outlined.PhotoLibrary, Color(0xFFF0FDF4), Color(0xFF16A34A), tr("Upload from gallery", "गैलरी से अपलोड"), tr("Choose a photo or video", "फ़ोटो या वीडियो चुनें"), onGallery)
            AttachOption(Icons.Outlined.Explore, Color(0xFFFFFBEB), Color(0xFFCA8A04), tr("Scan home", "घर स्कैन करें"), tr("Walk through with a video, then the full report", "वीडियो में घर दिखाएँ, फिर पूरी रिपोर्ट"), onScanRoom)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF7F7FB)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Explore, null, tint = VastuPrimary, modifier = Modifier.size(16.dp))
                Text(
                    tr("Direction is detected automatically using your phone’s compass", "आपके फ़ोन के कम्पास से दिशा अपने आप पहचानी जाएगी"),
                    fontSize = 11.sp, color = Color(0xFF4A5568), lineHeight = 15.sp, modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachOption(icon: androidx.compose.ui.graphics.vector.ImageVector, iconBg: Color, tint: Color, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFEFEDF8), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(sub, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Filled.ChevronRight, null, tint = TextHint, modifier = Modifier.size(18.dp))
    }
}
