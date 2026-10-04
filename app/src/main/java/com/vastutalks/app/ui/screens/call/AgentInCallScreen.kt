package com.vastutalks.app.ui.screens.call

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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
import com.vastutalks.app.ui.components.PhotoReviewSheet
import com.vastutalks.app.ui.components.rememberPhotoCapture
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

private enum class CallStatus(val label: String, val color: Color) {
    JOINING("Joining…", CallTones.TextMuted),
    LISTENING("Listening", OnlineGreen),
    HEARING("Hearing you", OnlineGreen),
    THINKING("Thinking", VastuSaffron),
    SPEAKING("Speaking", VastuPrimary),
    MUTED("You're muted", CallTones.TextMuted),
    NO_MIC("Mic access needed", Danger),
    TEXT_ONLY("Chat to talk", CallTones.TextMuted)
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
fun AgentInCallScreen(onEndCall: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val agent = remember { VastuAgent() }
    val agentTurnLock = remember { Mutex() }
    val recorder = remember { AgentCallRecorder(context) }
    val board = rememberWhiteboardState()

    var page by remember { mutableStateOf(CallPage.BOARD) }
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
    val chatMessages = remember { mutableStateListOf<AgentChatMessage>() }
    val isAgentTyping = pendingTurns > 0
    val isResumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)

    var hasMicPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
    }

    val voice by rememberUpdatedState(rememberAgentVoice())

    /** Saves whatever is on the board right now. */
    fun saveBoard(who: String, caption: String): android.graphics.Bitmap? {
        if (board.isEmpty) return null
        val bitmap = board.render()
        recorder.saveDrawing(bitmap, who, caption)
        board.markSaved()
        return bitmap
    }

    fun addMessage(message: AgentChatMessage) {
        chatMessages.add(message)
        if (page != CallPage.CHAT && message.sender == ChatSender.AGENT) unread++
    }

    fun postAgentTurn(turn: AgentTurn) {
        voice.speak(turn.say)
        addMessage(AgentChatMessage(id = chatMessages.size, text = turn.say, sender = ChatSender.AGENT))
        recorder.logLine(AnanyaAgent.NAME, turn.say)
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
        addMessage(AgentChatMessage(id = chatMessages.size, text = text, sender = ChatSender.USER))
        recorder.logLine("You", text)
        askAgent { agent.reply(text) }
    }

    fun sendBoardToAgent() {
        val bitmap = saveBoard("You", "Sent the board to ${AnanyaAgent.NAME}") ?: board.render()
        addMessage(
            AgentChatMessage(
                id = chatMessages.size,
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

    val recordVideo = rememberVideoCapture(
        onError = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() },
        onCaptured = { videoToReview = it }
    )

    /** Posts a shared photo or video in the chat as a thumbnail with its caption. */
    fun postMedia(preview: android.graphics.Bitmap, caption: String) {
        val thumbScale = 480f / maxOf(preview.width, preview.height)
        addMessage(
            AgentChatMessage(
                id = chatMessages.size,
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
        val caption = question.ifBlank { "What does Vastu say about this space?" } +
            (facing?.let { " · camera facing ${it.direction.label}" } ?: "")
        recorder.savePhoto(photo, "Shared a photo: $caption")
        postMedia(photo, caption)
        askAgent { agent.reviewPhoto(photo, question, facing) }
    }

    fun sendVideo(video: CapturedVideo, question: String, facing: PhotoFacing?) {
        videoToReview = null
        val caption = "Video (${maxOf(1, video.durationMs / 1000)}s): " +
            question.ifBlank { "What does Vastu say about this space?" } +
            (facing?.let { " · started facing ${it.direction.label}" } ?: "")
        recorder.saveVideo(video.file, "Shared a video: $caption")
        postMedia(video.frames.first(), caption)
        askAgent { agent.reviewVideo(video.frames, video.durationMs, question, facing) }
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
        isResumed && photoToReview == null && videoToReview == null // not while the camera app or a preview is up
    var micOpen by remember { mutableStateOf(false) }
    LaunchedEffect(callerTurn) {
        if (callerTurn) delay(500) // let the speaker's last syllable die away first
        micOpen = callerTurn
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
        if (!hasMicPermission) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
    val callCost = (elapsedSeconds / 60.0) * (AnanyaAgent.PRICE_PER_SESSION / 60.0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CallTones.Ink)
            .background(
                Brush.radialGradient(
                    colors = listOf(VastuPrimary.copy(alpha = 0.22f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(0f, 0f),
                    radius = 1400f
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            CallHeader(
                status = status,
                isSpeaking = voice.isSpeaking,
                timer = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
                cost = "₹%.0f".format(callCost)
            )

            PageSwitch(
                page = page,
                unread = unread,
                onSelect = {
                    page = it
                    if (it == CallPage.CHAT) unread = 0
                },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 12.dp)
            )

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
                    CallPage.CHAT -> AgentChatPage(
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
                onTakePhoto = takePhoto,
                onRecordVideo = recordVideo,
                onEndCall = onEndCall
            )
        }

        photoToReview?.let { photo ->
            PhotoReviewSheet(
                photo = photo,
                agentName = AnanyaAgent.NAME.substringBefore(' '),
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
                agentName = AnanyaAgent.NAME.substringBefore(' '),
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

@Composable
private fun CallHeader(status: CallStatus, isSpeaking: Boolean, timer: String, cost: String) {
    val ringColor by animateColorAsState(if (isSpeaking) VastuPrimary else Color.Transparent, label = "ring")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(46.dp).border(2.dp, ringColor, CircleShape).padding(4.dp)
        ) {
            AsyncImage(
                model = "https://i.pravatar.cc/300?u=${AnanyaAgent.AVATAR_SEED}",
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape).background(CallTones.SurfaceRaised)
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(AnanyaAgent.NAME, color = CallTones.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                StatusDot(status)
                Text(
                    status.label,
                    color = CallTones.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(CallTones.Surface)
                .border(1.dp, CallTones.Hairline, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(timer, color = CallTones.TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
            Box(modifier = Modifier.padding(horizontal = 8.dp).size(3.dp).clip(CircleShape).background(CallTones.TextMuted))
            Text(cost, color = CallTones.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                        if (line.sender == ChatSender.AGENT) AnanyaAgent.NAME.substringBefore(' ').uppercase() else "YOU",
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
    onTakePhoto: () -> Unit,
    onRecordVideo: () -> Unit,
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
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
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
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = CallTones.SurfaceRaised,
                shape = RoundedCornerShape(16.dp)
            ) {
                DropdownMenuItem(
                    text = { Text("Take a photo", color = CallTones.TextPrimary) },
                    leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = CallTones.TextPrimary) },
                    onClick = { menuOpen = false; onTakePhoto() }
                )
                DropdownMenuItem(
                    text = { Text("Record a video (up to 30s)", color = CallTones.TextPrimary) },
                    leadingIcon = { Icon(Icons.Outlined.Videocam, contentDescription = null, tint = CallTones.TextPrimary) },
                    onClick = { menuOpen = false; onRecordVideo() }
                )
            }
        }
    }
}
