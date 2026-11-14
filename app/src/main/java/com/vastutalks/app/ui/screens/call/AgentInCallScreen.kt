package com.vastutalks.app.ui.screens.call

import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.ui.graphics.Brush
import com.vastutalks.app.ui.components.AgentHeaderButton
import com.vastutalks.app.ui.components.AiOrb
import com.vastutalks.app.data.ai.tr
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil.compose.AsyncImage
import com.vastutalks.app.data.ai.AgentTurn
import com.vastutalks.app.data.ai.AgentCallRecorder
import com.vastutalks.app.data.ai.PhotoFacing
import com.vastutalks.app.data.ai.VastuAgent
import com.vastutalks.app.data.model.AnanyaAgent
import com.vastutalks.app.ui.components.CallTones
import com.vastutalks.app.ui.components.CallWhiteboard
import com.vastutalks.app.ui.components.ChatSender
import com.vastutalks.app.ui.components.AgentChatMessage
import com.vastutalks.app.ui.components.AgentChatPage
import com.vastutalks.app.ui.components.CapturedPhoto
import com.vastutalks.app.ui.components.CapturedVideo
import com.vastutalks.app.ui.components.VideoReviewSheet
import com.vastutalks.app.ui.components.rememberVideoCapture
import com.vastutalks.app.ui.components.AgentHeader
import com.vastutalks.app.ui.components.agentScreenBackground
import com.vastutalks.app.data.ai.RoomScanHandoff
import com.vastutalks.app.data.ai.AgentChatStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vastutalks.app.ui.screens.scan.RoomScanScreen
import com.vastutalks.app.ui.screens.scan.RoomScanViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vastutalks.app.ui.components.CameraViewfinder
import com.vastutalks.app.ui.components.DarkScreenStatusBar
import com.vastutalks.app.ui.components.ViewfinderState
import com.vastutalks.app.ui.components.CameraHeadingSensor
import com.vastutalks.app.ui.components.DirectionPickerDialog
import com.vastutalks.app.ui.components.HeadingSensor
import com.vastutalks.app.ui.components.rememberCameraPose
import com.vastutalks.app.ui.components.rememberHeadingSensor
import com.vastutalks.app.data.ai.CompassDirection
import com.vastutalks.app.ui.components.PhotoReviewSheet
import com.vastutalks.app.ui.components.ShareMediaActions
import com.vastutalks.app.ui.components.ShareMediaMenu
import com.vastutalks.app.ui.components.rememberPhotoCapture
import com.vastutalks.app.ui.components.rememberPhotoPicker
import com.vastutalks.app.ui.components.WhiteboardPalette
import com.vastutalks.app.ui.components.WhiteboardState
import com.vastutalks.app.ui.components.rememberCallerListener
import com.vastutalks.app.ui.components.rememberAgentVoice
import com.vastutalks.app.ui.components.rememberWhiteboardState
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.OnlineGreen
import com.vastutalks.app.ui.theme.VastuPrimary
import com.vastutalks.app.ui.theme.VastuSaffron
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private enum class CallPage { BOARD, CHAT }

private enum class CallStatus(private val english: String, private val hindi: String, val color: Color) {
    JOINING("Joining…", "जुड़ रही हूँ…", Color.White.copy(alpha = 0.75f)),
    LISTENING("Listening…", "सुन रही हूँ…", Color(0xFF86EFAC)),
    HEARING("Listening…", "सुन रही हूँ…", Color(0xFF86EFAC)),
    THINKING("Thinking…", "सोच रही हूँ…", Color(0xFFFFD93D)),
    SPEAKING("Speaking…", "बोल रही हूँ…", Color.White.copy(alpha = 0.75f)),
    MUTED("You are muted", "आप म्यूट हैं", Color.White.copy(alpha = 0.75f)),
    NO_MIC("Mic access needed", "माइक की अनुमति चाहिए", Danger),
    TEXT_ONLY("Chat to talk", "बात करने के लिए लिखें", CallTones.TextMuted);

    val label: String get() = tr(english, hindi)
}

/**
 * The call with the AI agent Ananya (VastuAgent, an OpenAI model), once she picks up.
 * Two pages, switched from the header: the Board (default) — a shared
 * whiteboard both of them draw on, with live captions — and the Chat,
 * the full conversation plus a box to type.
 *
 * She greets the caller, then waits as long as it takes for them to
 * speak (CallerListener) and answers out loud. The camera button lets
 * the caller photograph or film a room for her to give Vastu suggestions
 * on. Everything said, every board snapshot, photo and video is saved as
 * the call goes (AgentCallRecorder).
 */
@Composable
fun AgentInCallScreen(startWithVideo: Boolean = false, onEndCall: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val agent = remember { VastuAgent() }
    val agentTurnLock = remember { Mutex() }
    val recorder = remember { AgentCallRecorder(context) }
    val board = rememberWhiteboardState()

    var page by remember { mutableStateOf(if (VastuAgent.BOARD_ENABLED) CallPage.BOARD else CallPage.CHAT) }
    var unread by remember { mutableIntStateOf(0) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var penColor by remember { mutableStateOf(WhiteboardPalette.first()) }
    var chatInput by remember { mutableStateOf("") }
    var pendingTurns by remember { mutableIntStateOf(0) }
    var isTranscribing by remember { mutableStateOf(false) }
    var hasGreeted by remember { mutableStateOf(false) }
    var photoToReview by remember { mutableStateOf<CapturedPhoto?>(null) }
    var videoToReview by remember { mutableStateOf<CapturedVideo?>(null) }
    /** Counts room scans started in this call; non-zero while one is on screen. */
    var scanNumber by remember { mutableIntStateOf(0) }
    var isScanning by remember { mutableStateOf(false) }
    val chatMessages = remember { mutableStateListOf<AgentChatMessage>() }
    // The call continues the saved chat with her and saves back into it,
    // so the chat shows what was said here (and she remembers it there).
    val chatStore = remember { AgentChatStore(context) }
    var nextMessageId by remember { mutableIntStateOf(0) }
    /** False until the saved chat is read, so it's never overwritten before then. */
    var chatLoaded by remember { mutableStateOf(false) }
    var savedMarkable by remember { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    val isAgentTyping = pendingTurns > 0
    val isResumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)

    var hasMicPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
    }

    val voice by rememberUpdatedState(rememberAgentVoice())

    // Video mode: the caller's camera is live on screen and every turn carries
    // the current frame and compass direction, so Ananya sees what they see.
    // Switchable both ways during the call.
    var isVideo by remember { mutableStateOf(false) }
    val viewfinder = remember { ViewfinderState() }
    val cameraPose by rememberCameraPose()
    val headingSensor = rememberHeadingSensor()
    // Phones without a compass: the direction the caller picked (no gyroscope
    // either), and whether the picker is open / has been offered this call.
    var pickedFacing by remember { mutableStateOf<CompassDirection?>(null) }
    var askDirection by remember { mutableStateOf(false) }
    var offeredDirection by remember { mutableStateOf(false) }
    val cameraFacing: PhotoFacing? = cameraPose?.let { PhotoFacing(it.heading, it.source) }
        ?: pickedFacing?.let { PhotoFacing(it.degrees, PhotoFacing.Source.CALLER) }
    // The first time video starts on such a phone, ask which way the camera faces.
    LaunchedEffect(isVideo) {
        if (isVideo && headingSensor != HeadingSensor.COMPASS && cameraFacing == null && !offeredDirection) {
            offeredDirection = true
            askDirection = true
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) isVideo = true
        else android.widget.Toast.makeText(context, tr("Camera permission is needed for video.", "वीडियो के लिए कैमरा की अनुमति चाहिए।"), android.widget.Toast.LENGTH_SHORT).show()
    }
    fun toggleVideo() {
        when {
            isVideo -> isVideo = false
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> isVideo = true
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /** Saves whatever is on the board right now. */
    fun saveBoard(who: String, caption: String): android.graphics.Bitmap? {
        if (board.isEmpty) return null
        val bitmap = board.render()
        recorder.saveDrawing(bitmap, who, caption)
        board.markSaved()
        return bitmap
    }

    /** Writes the call so far into the saved chat. */
    fun saveToChat() {
        if (!chatLoaded) return
        val history = runCatching { agent.exportHistory() }.getOrNull() ?: return
        val images = agent.markableImages
        if (images !== savedMarkable) {
            chatStore.saveMarkable(images)
            savedMarkable = images
        }
        chatStore.save(chatMessages, history, images.size, fromCall = true)
    }

    fun addMessage(message: AgentChatMessage) {
        chatMessages.add(message)
        if (page != CallPage.CHAT && message.sender == ChatSender.AGENT) unread++
        // Her replies come once her history has the turn, so save then (the caller's line goes with it).
        if (message.sender == ChatSender.AGENT) saveToChat()
    }

    /** Puts the saved chat ahead of the call and gives her its memory. */
    suspend fun loadChat() {
        val saved = withContext(Dispatchers.IO) { chatStore.load() }
        if (saved != null) {
            agentTurnLock.withLock { agent.restore(saved.history, saved.markable) }
            savedMarkable = agent.markableImages
            val early = chatMessages.toList() // anything typed while it loaded
            chatMessages.clear()
            chatMessages.addAll(saved.messages)
            nextMessageId = (saved.messages.maxOfOrNull { it.id } ?: -1) + 1
            early.forEach { chatMessages.add(it.copy(id = nextMessageId++)) }
        }
        chatLoaded = true
    }

    fun postAgentTurn(turn: AgentTurn) {
        voice.speak(turn.say)
        addMessage(
            AgentChatMessage(
                id = nextMessageId++,
                text = turn.chatText,
                sender = ChatSender.AGENT,
                image = turn.markedPhoto?.asImageBitmap()
            )
        )
        recorder.logLine(AnanyaAgent.NAME, turn.chatText)
        turn.markedPhoto?.let { marked ->
            recorder.savePhoto(marked, "Marked up the photo", who = AnanyaAgent.NAME)
            page = CallPage.CHAT // her marks are on the photo in the chat
        }
        turn.board?.let { drawing ->
            board.showAgentDrawing(drawing)
            page = CallPage.BOARD // she's explaining on the board — bring it into view
            saveBoard(AnanyaAgent.NAME, "Drew on the board: ${drawing.title.ifBlank { "sketch" }}")
        }
    }

    /** Runs one agent turn at a time. */
    fun askAgent(request: suspend () -> AgentTurn) {
        pendingTurns++
        scope.launch {
            try {
                agentTurnLock.withLock { postAgentTurn(request()) }
            } finally {
                pendingTurns--
            }
        }
    }

    fun onUserSaid(text: String) {
        addMessage(AgentChatMessage(id = nextMessageId++, text = text, sender = ChatSender.USER))
        recorder.logLine("You", text)
        val view = if (isVideo) viewfinder.snapshot() else null
        if (view != null) {
            val facing = cameraFacing
            askAgent { agent.replyWithView(text, view, facing) }
        } else {
            askAgent { agent.reply(text) }
        }
    }

    fun sendBoardToAgent() {
        val bitmap = saveBoard("You", "Sent the board to ${AnanyaAgent.NAME}") ?: board.render()
        addMessage(
            AgentChatMessage(
                id = nextMessageId++,
                text = "",
                sender = ChatSender.USER,
                image = android.graphics.Bitmap.createScaledBitmap(bitmap, 360, 360, true).asImageBitmap()
            )
        )
        askAgent { agent.reviewSketch(bitmap) }
    }

    val takePhoto = rememberPhotoCapture(
        onError = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() },
        onCaptured = { photoToReview = it }
    )

    val pickPhoto = rememberPhotoPicker(
        onError = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() },
        onPicked = { photoToReview = it }
    )

    val recordVideo = rememberVideoCapture(
        onError = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() },
        onCaptured = { videoToReview = it }
    )

    /** Posts a shared photo or video in the chat as a thumbnail with its caption. */
    fun postMedia(preview: android.graphics.Bitmap, caption: String) {
        val thumbScale = 480f / maxOf(preview.width, preview.height)
        addMessage(
            AgentChatMessage(
                id = nextMessageId++,
                text = caption,
                sender = ChatSender.USER,
                image = android.graphics.Bitmap.createScaledBitmap(
                    preview, (preview.width * thumbScale).toInt(), (preview.height * thumbScale).toInt(), true
                ).asImageBitmap()
            )
        )
        page = CallPage.CHAT // show it with her answer; a drawing would bring the board back
    }

    fun sendPhoto(photo: android.graphics.Bitmap, question: String, facing: PhotoFacing?) {
        photoToReview = null
        val caption = question.ifBlank { tr("What does Vastu say about this space?", "इस जगह के बारे में वास्तु क्या कहता है?") } +
            (facing?.let { tr(" · camera facing ${it.direction.label}", " · कैमरा ${it.direction.label} की ओर") } ?: "")
        recorder.savePhoto(photo, "Shared a photo: $caption")
        postMedia(photo, caption)
        askAgent { agent.reviewPhoto(photo, question, facing) }
    }

    fun sendVideo(video: CapturedVideo, question: String, facing: PhotoFacing?) {
        videoToReview = null
        val caption = "Video (${maxOf(1, video.durationMs / 1000)}s): " +
            question.ifBlank { tr("What does Vastu say about this space?", "इस जगह के बारे में वास्तु क्या कहता है?") } +
            (facing?.let { " · started facing ${it.direction.label}" } ?: "")
        recorder.saveVideo(video.file, "Shared a video: $caption")
        postMedia(video.frames.first(), caption)
        askAgent { agent.reviewVideo(video.frames, video.durationMs, question, facing) }
    }

    /** Puts a finished room scan into the call's chat and into her memory of it. */
    fun addRoomScan(scan: RoomScanHandoff.Scan) {
        scope.launch {
            agentTurnLock.withLock {
                agent.attachRoomScan(scan.walls, scan.analysis)
                postMedia(scan.walls.first().bitmap, tr("I scanned my ${scan.analysis.room.lowercase()} with a video", "मैंने वीडियो से अपना ${scan.analysis.room} स्कैन किया"))
                val marked = scan.analysis.markedPhoto(scan.walls, scan.analysis.findings.firstOrNull()?.photo ?: 0)
                val reportId = nextMessageId++
                chatStore.saveReport(reportId, scan.walls, scan.analysis) // opens from the chat later
                addMessage(AgentChatMessage(id = reportId, text = scan.analysis.asText(), sender = ChatSender.AGENT, image = marked?.asImageBitmap(), hasReport = true))
                recorder.logLine(AnanyaAgent.NAME, scan.analysis.asText())
                marked?.let { recorder.savePhoto(it, "Room scan", who = AnanyaAgent.NAME) }
                voice.speak(tr("I've looked at your room. My notes are in the chat — ask me anything about it.", "मैंने आपका कमरा देख लिया। मेरे सुझाव चैट में हैं — इसके बारे में कुछ भी पूछिए।"))
            }
        }
    }

    // The scan needs the camera, so the call's own camera goes off while it's up.
    fun startRoomScan() {
        isVideo = false
        scanNumber++
        isScanning = true
    }

    fun clearBoard() {
        if (board.hasUnsavedChanges) saveBoard("You", "Board before clearing")
        board.clear()
    }

    fun sendTyped() {
        val text = chatInput.trim()
        if (text.isEmpty()) return
        chatInput = ""
        onUserSaid(text)
    }

    // Hands-free conversation: the mic is open whenever it's the caller's
    // turn, and stays open until they actually say something. Closed while
    // Ananya thinks or speaks (so she doesn't hear herself) and when muted.
    val callerTurn = hasGreeted && hasMicPermission && agent.isConfigured && !isMuted &&
        !voice.isSpeaking && !isAgentTyping && !isTranscribing &&
        isResumed && !isScanning && photoToReview == null && videoToReview == null // not while the camera app or a preview is up
    var micOpen by remember { mutableStateOf(false) }
    LaunchedEffect(callerTurn) {
        if (callerTurn) delay(500) // let the speaker's last syllable die away first
        micOpen = callerTurn
        android.util.Log.d(
            "CallerListener",
            if (callerTurn) "Mic open" else "Mic closed: speaking=${voice.isSpeaking} thinking=$isAgentTyping " +
                "transcribing=$isTranscribing muted=$isMuted resumed=$isResumed greeted=$hasGreeted " +
                "media=${photoToReview != null || videoToReview != null}"
        )
    }
    val listener = rememberCallerListener(active = micOpen) { wav ->
        isTranscribing = true
        scope.launch {
            val text = try { agent.transcribe(wav) } finally { isTranscribing = false }
            if (text != null) onUserSaid(text)
        }
    }

    // Ananya picks up: wait briefly for TTS so her greeting is spoken.
    LaunchedEffect(Unit) {
        if (startWithVideo) toggleVideo()
        if (!hasMicPermission) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        loadChat()
        withTimeoutOrNull(2500) { snapshotFlow { voice.isReady }.first { it } }
        askAgent { agent.greet().also { hasGreeted = true } }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSeconds++
        }
    }

    // Leaving the screen (End call, back, app closed) saves the last board and closes the transcript.
    DisposableEffect(Unit) {
        onDispose {
            if (board.hasUnsavedChanges) saveBoard("Board", "Board at end of call")
            saveToChat()
            recorder.finish()
        }
    }

    val status = when {
        isAgentTyping || isTranscribing -> CallStatus.THINKING
        voice.isSpeaking -> CallStatus.SPEAKING
        !hasGreeted -> CallStatus.JOINING
        listener.isHearingSpeech -> CallStatus.HEARING
        listener.isListening -> CallStatus.LISTENING
        isMuted -> CallStatus.MUTED
        !hasMicPermission -> CallStatus.NO_MIC
        !agent.isConfigured -> CallStatus.TEXT_ONLY
        else -> CallStatus.LISTENING
    }
    var showTranscript by remember { mutableStateOf(false) }
    DarkScreenStatusBar()
    Box(modifier = Modifier.fillMaxSize().agentScreenBackground()) {
        if (!isVideo && !isScanning) {
            TalkToAnayra(
                status = status,
                speaking = voice.isSpeaking,
                elapsedSeconds = elapsedSeconds,
                lastUser = chatMessages.lastOrNull { it.sender == ChatSender.USER && it.text.isNotBlank() }?.text,
                lastAgent = chatMessages.lastOrNull { it.sender == ChatSender.AGENT && it.text.isNotBlank() }?.text,
                isMuted = isMuted,
                onToggleMute = {
                    if (!hasMicPermission) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    else isMuted = !isMuted
                },
                onInterrupt = { if (voice.isSpeaking) voice.stop() },
                onEnd = {
                    android.widget.Toast.makeText(context, tr("Conversation saved to chat", "बातचीत चैट में सेव हो गई"), android.widget.Toast.LENGTH_SHORT).show()
                    onEndCall()
                },
                onToChat = onEndCall
            )
        } else
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            AgentHeader(
                subtitle = "${status.label} · ${if (isVideo) tr("Video", "वीडियो") else tr("Audio", "ऑडियो")} · " +
                    "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
                statusDot = { StatusDot(status) },
                onScanRoom = ::startRoomScan,
                isSpeaking = voice.isSpeaking
            ) {
                if (!isVideo) {
                    AgentHeaderButton(
                        if (showTranscript) Icons.Filled.GraphicEq else Icons.AutoMirrored.Outlined.Chat,
                        if (showTranscript) tr("Show voice view", "आवाज़ वाला दृश्य") else tr("Show transcript", "बातचीत दिखाएँ"),
                        onClick = { showTranscript = !showTranscript }
                    )
                }
            }

            if (isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CallTones.Surface)
                ) {
                    CameraViewfinder(viewfinder, Modifier.fillMaxSize())
                    // Without a compass the chip is how the caller sets (or corrects) the direction.
                    val canSetDirection = headingSensor != HeadingSensor.COMPASS
                    Text(
                        cameraFacing?.let {
                            tr("Camera facing ", "कैमरा की दिशा: ") + it.direction.localName +
                                if (canSetDirection) tr(" · tap to change", " · बदलें") else ""
                        } ?: if (canSetDirection) tr("Tap to set camera direction", "कैमरे की दिशा चुनें")
                        else tr("${AnanyaAgent.NAME} sees your camera", "${AnanyaAgent.NAME_HI} आपका कैमरा देख रही हैं"),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (canSetDirection && cameraFacing == null) VastuPrimary else Color.Black.copy(alpha = 0.55f))
                            .then(if (canSetDirection) Modifier.clickable { askDirection = true } else Modifier)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    if (askDirection) {
                        DirectionPickerDialog(
                            sensor = headingSensor,
                            current = cameraFacing?.direction,
                            onPick = {
                                if (headingSensor == HeadingSensor.GYRO) CameraHeadingSensor.setDirection(it) else pickedFacing = it
                                askDirection = false
                            },
                            onDismiss = { askDirection = false }
                        )
                    }
                }
            }

            if (VastuAgent.BOARD_ENABLED) {
                PageSwitch(
                    page = page,
                    unread = unread,
                    onSelect = {
                        page = it
                        if (it == CallPage.CHAT) unread = 0
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 12.dp)
                )
            }

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val dir = if (targetState == CallPage.CHAT) 1 else -1
                    (slideInHorizontally(tween(280)) { it / 6 * dir } + fadeIn(tween(280)))
                        .togetherWith(slideOutHorizontally(tween(220)) { -it / 6 * dir } + fadeOut(tween(200)))
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = "call_page"
            ) { current ->
                when (current) {
                    CallPage.BOARD -> BoardPage(
                        board = board,
                        penColor = penColor,
                        onPenColor = { penColor = it },
                        latest = chatMessages.lastOrNull { it.text.isNotBlank() },
                        onSend = { sendBoardToAgent() },
                        onClear = { clearBoard() }
                    )
                    CallPage.CHAT -> if (!isVideo && !showTranscript) OrbStage(
                        status = status,
                        speaking = voice.isSpeaking,
                        lastUser = chatMessages.lastOrNull { it.sender == ChatSender.USER && it.text.isNotBlank() }?.text,
                        lastAgent = chatMessages.lastOrNull { it.sender == ChatSender.AGENT && it.text.isNotBlank() }?.text,
                        onOpenTranscript = { showTranscript = true }
                    ) else AgentChatPage(
                        messages = chatMessages,
                        isAgentTyping = isAgentTyping,
                        agentName = AnanyaAgent.NAME,
                        avatarSeed = AnanyaAgent.AVATAR_SEED,
                        inputText = chatInput,
                        onInputChange = { chatInput = it },
                        onSend = { sendTyped() }
                    )
                }
            }

            CallControls(
                isMuted = isMuted,
                isHearing = listener.isHearingSpeech,
                isListening = listener.isListening,
                onToggleMic = {
                    if (!hasMicPermission) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    else isMuted = !isMuted
                },
                media = ShareMediaActions(takePhoto, pickPhoto, recordVideo, scanRoom = ::startRoomScan),
                isVideo = isVideo,
                onToggleVideo = ::toggleVideo,
                onEndCall = onEndCall
            )
        }

        if (isScanning) {
            val scanViewModel: RoomScanViewModel = viewModel(key = "call_room_scan_$scanNumber")
            scanViewModel.onHandOff = ::addRoomScan
            RoomScanScreen(
                onClose = { isScanning = false },
                onAskAgent = { isScanning = false; page = CallPage.CHAT },
                viewModel = scanViewModel
            )
        }

        photoToReview?.let { photo ->
            PhotoReviewSheet(
                photo = photo,
                agentName = AnanyaAgent.NAME,
                onRetake = {
                    photoToReview = null
                    takePhoto()
                },
                onDismiss = { photoToReview = null },
                onSend = { question, facing -> sendPhoto(photo.bitmap, question, facing) }
            )
        }

        videoToReview?.let { video ->
            VideoReviewSheet(
                video = video,
                agentName = AnanyaAgent.NAME,
                onRetake = {
                    video.file.delete()
                    videoToReview = null
                    recordVideo()
                },
                onDismiss = {
                    video.file.delete()
                    videoToReview = null
                },
                onSend = { question, facing -> sendVideo(video, question, facing) }
            )
        }
    }
}

/** The voice view: Ananya's orb, what she's doing, and the latest line from each side. */
@Composable
private fun OrbStage(status: CallStatus, speaking: Boolean, lastUser: String?, lastAgent: String?, onOpenTranscript: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "orb_pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(if (speaking) 700 else 1800), RepeatMode.Reverse),
        label = "orb_scale"
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0x73C084FC), Color.Transparent)))
            )
            AiOrb(138.dp, ringWidth = 5.dp, iconSize = 54.dp, spinMillis = if (speaking) 2500 else 7000)
        }
        Text(AnanyaAgent.NAME, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp))
        Text(status.label, color = status.color, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 118.dp)
                .padding(top = 22.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpenTranscript() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (lastUser != null) {
                Text("“$lastUser”", color = Color.White.copy(alpha = 0.6f), fontSize = 12.5.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Text(
                (lastAgent ?: tr("Ask anything about your home's Vastu.", "अपने घर के वास्तु के बारे में कुछ भी पूछें।")).replace("**", ""),
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatusDot(status: CallStatus) {
    val pulses = status == CallStatus.LISTENING || status == CallStatus.HEARING || status == CallStatus.THINKING
    val transition = rememberInfiniteTransition(label = "status")
    val pulse by transition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (status == CallStatus.HEARING) 350 else 900), RepeatMode.Reverse),
        label = "status_pulse"
    )
    val color by animateColorAsState(status.color, label = "status_color")
    Box(
        modifier = Modifier
            .size(7.dp)
            .alpha(if (pulses) pulse else 1f)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun PageSwitch(page: CallPage, unread: Int, onSelect: (CallPage) -> Unit, modifier: Modifier = Modifier) {
    val segment = 104.dp
    val indicatorOffset by animateDpAsState(if (page == CallPage.BOARD) 0.dp else segment, tween(250), label = "switch")
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(CallTones.Surface)
            .border(1.dp, CallTones.Hairline, RoundedCornerShape(50))
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segment)
                .height(34.dp)
                .clip(RoundedCornerShape(50))
                .background(CallTones.SurfaceRaised)
        )
        Row {
            CallPage.entries.forEach { p ->
                Row(
                    modifier = Modifier
                        .width(segment)
                        .height(34.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(p) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (p == CallPage.BOARD) "Board" else "Chat",
                        color = if (p == page) CallTones.TextPrimary else CallTones.TextMuted,
                        fontSize = 13.sp,
                        fontWeight = if (p == page) FontWeight.SemiBold else FontWeight.Medium
                    )
                    if (p == CallPage.CHAT && unread > 0) {
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(VastuPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (unread > 9) "9+" else "$unread", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardPage(
    board: WhiteboardState,
    penColor: Color,
    onPenColor: (Color) -> Unit,
    latest: AgentChatMessage?,
    onSend: () -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CallWhiteboard(
            state = board,
            penColor = penColor,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)
        )

        // Live caption: the latest line, so the board page never needs the chat open.
        AnimatedContent(
            targetState = latest,
            transitionSpec = {
                (slideInVertically { it / 3 } + fadeIn()).togetherWith(slideOutVertically { -it / 3 } + fadeOut())
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 28.dp, vertical = 12.dp),
            label = "caption"
        ) { line ->
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (line != null) {
                    Text(
                        if (line.sender == ChatSender.AGENT) AnanyaAgent.NAME.uppercase() else "YOU",
                        color = if (line.sender == ChatSender.AGENT) VastuSaffron else CallTones.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        line.text,
                        color = CallTones.TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        BoardToolbar(
            penColor = penColor,
            onPenColor = onPenColor,
            canUndo = board.userStrokes.isNotEmpty(),
            canClear = !board.isEmpty,
            canSend = board.userStrokes.isNotEmpty(),
            onUndo = { board.undo() },
            onClear = onClear,
            onSend = onSend
        )
    }
}

@Composable
private fun BoardToolbar(
    penColor: Color,
    onPenColor: (Color) -> Unit,
    canUndo: Boolean,
    canClear: Boolean,
    canSend: Boolean,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(CallTones.Surface)
            .border(1.dp, CallTones.Hairline, RoundedCornerShape(28.dp))
            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colors take whatever width is left, so the buttons on the right never get squeezed.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WhiteboardPalette.forEach { color ->
                val selected = color == penColor
                val ring by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "pen_ring")
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { onPenColor(color) }
                        .border(2.dp, ring, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
        ToolIcon(Icons.AutoMirrored.Filled.Undo, "Undo", enabled = canUndo, onClick = onUndo)
        ToolIcon(Icons.Filled.DeleteOutline, "Clear board", enabled = canClear, onClick = onClear)
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(if (canSend) VastuPrimary else CallTones.SurfaceRaised)
                .clickable(enabled = canSend) { onSend() }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                "Share",
                color = if (canSend) Color.White else CallTones.TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) CallTones.TextPrimary else CallTones.TextMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CallControls(
    isMuted: Boolean,
    isHearing: Boolean,
    isListening: Boolean,
    onToggleMic: () -> Unit,
    media: ShareMediaActions,
    isVideo: Boolean,
    onToggleVideo: () -> Unit,
    onEndCall: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "mic")
    val halo by transition.animateFloat(
        initialValue = 1f, targetValue = if (isHearing) 1.35f else 1.15f,
        animationSpec = infiniteRepeatable(tween(if (isHearing) 420 else 1200), RepeatMode.Reverse),
        label = "mic_halo"
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            if (isListening && !isMuted) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(halo)
                        .clip(CircleShape)
                        .background(OnlineGreen.copy(alpha = if (isHearing) 0.28f else 0.14f))
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isMuted) CallTones.TextPrimary else CallTones.SurfaceRaised)
                    .border(1.dp, CallTones.Hairline, CircleShape)
                    .clickable { onToggleMic() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = if (isMuted) CallTones.Ink else CallTones.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isVideo) CallTones.TextPrimary else CallTones.SurfaceRaised)
                .border(1.dp, CallTones.Hairline, CircleShape)
                .clickable { onToggleVideo() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isVideo) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                contentDescription = if (isVideo) tr("Switch to audio call", "ऑडियो कॉल पर जाएँ") else tr("Switch to video call", "वीडियो कॉल पर जाएँ"),
                tint = if (isVideo) CallTones.Ink else CallTones.TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Danger)
                .clickable { onEndCall() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.CallEnd, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(CallTones.SurfaceRaised)
                    .border(1.dp, CallTones.Hairline, CircleShape)
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = "Share a photo or video for Vastu advice", tint = CallTones.TextPrimary, modifier = Modifier.size(22.dp))
            }
            ShareMediaMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, actions = media)
        }
    }
}

/**
 * The design's "Talk to Anayra" voice screen: time and free-minutes chips
 * with the language toggle on top, the spinning orb (tap it to interrupt
 * her), the latest line from each side, then mute, end and back-to-chat.
 */
@Composable
private fun TalkToAnayra(
    status: CallStatus,
    speaking: Boolean,
    elapsedSeconds: Int,
    lastUser: String?,
    lastAgent: String?,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onInterrupt: () -> Unit,
    onEnd: () -> Unit,
    onToChat: () -> Unit
) {
    val freeLeft = (10 * 60 - elapsedSeconds).coerceAtLeast(0)
    fun mmss(x: Int) = "%02d:%02d".format(x / 60, x % 60)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                mmss(elapsedSeconds), color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Text(
                if (freeLeft > 0) tr("Free · ${mmss(freeLeft)} left", "मुफ़्त · ${mmss(freeLeft)} बाकी") else tr("₹5/min", "₹5/मिनट"),
                color = if (freeLeft > 0) Color(0xFF86EFAC) else Color(0xFFFFD93D), fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (freeLeft > 0) Color(0x2E22C55E) else Color(0x2EFFD93D))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Spacer(Modifier.weight(1f))
            com.vastutalks.app.ui.components.DesignLangToggle(dark = true, height = 26.dp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onInterrupt)
        ) {
            OrbStage(status = status, speaking = speaking, lastUser = lastUser, lastAgent = lastAgent, onOpenTranscript = onInterrupt)
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 30.dp, end = 30.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (speaking) tr("Tap the orb to interrupt", "बीच में बोलने के लिए ऑर्ब पर टैप करें") else tr("Speak naturally — in English or Hindi", "स्वाभाविक रूप से बोलें — हिंदी या अंग्रेज़ी में"),
                color = Color.White.copy(alpha = 0.5f), fontSize = 10.5.sp
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(if (isMuted) Color.White else Color.White.copy(alpha = 0.12f)).clickable(onClick = onToggleMute),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic, if (isMuted) "Unmute" else "Mute", tint = if (isMuted) Color(0xFF1A202C) else Color.White, modifier = Modifier.size(22.dp))
                }
                Box(
                    Modifier.size(70.dp).shadow(14.dp, CircleShape, spotColor = Color(0xB3EF4444)).clip(CircleShape).background(Color(0xFFEF4444)).clickable(onClick = onEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CallEnd, "End", tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onToChat),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Keyboard, tr("Switch to chat", "चैट पर जाएँ"), tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
