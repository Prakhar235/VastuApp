package com.vastutalks.app.ui.screens.scan

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import com.vastutalks.app.ui.components.AiOrb
import com.vastutalks.app.ui.theme.VastuLilac
import com.vastutalks.app.ui.theme.VastuSun
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.filled.Cameraswitch
import com.vastutalks.app.ui.components.loadPhoto
import com.vastutalks.app.ui.components.loadVideoFrames
import com.vastutalks.app.ui.components.scaleDown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vastutalks.app.data.ai.CompassDirection
import com.vastutalks.app.data.ai.PhotoFacing
import com.vastutalks.app.data.ai.WallPhoto
import com.vastutalks.app.data.ai.tr
import com.vastutalks.app.data.model.AnanyaAgent
import com.vastutalks.app.ui.components.CallTones
import com.vastutalks.app.ui.components.CameraViewfinder
import com.vastutalks.app.ui.components.DarkScreenStatusBar
import com.vastutalks.app.ui.components.LanguageSwitch
import com.vastutalks.app.ui.components.ViewfinderState
import com.vastutalks.app.ui.components.CameraHeadingSensor
import com.vastutalks.app.ui.components.DirectionPickerDialog
import com.vastutalks.app.ui.components.HeadingSensor
import com.vastutalks.app.ui.components.rememberCameraPose
import com.vastutalks.app.ui.components.rememberHeadingSensor
import com.vastutalks.app.ui.theme.VastuPrimary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Colours of the scan screens, beyond CallTones. */
internal object ScanTones {
    val Major = Color(0xFFFF8A4C)
    val Minor = Color(0xFFF5C451)
    val Good = Color(0xFF57C7A0)
    val GoodText = Color(0xFF7FE0BC)
    val Track = Color(0xFF2E2A42)
    val Body = Color(0xFFC9C3DE)
    val Scrim = Color(0xD112101C)
}

/**
 * The home scan: record a walk-through video with the live compass
 * showing which way the camera faces, then see Ananya's read of it
 * ([RoomResultScreen]) and, for anything that should move, where to
 * ([RoomMapScreen]).
 */
@Composable
fun RoomScanScreen(
    onClose: () -> Unit,
    onAskAgent: () -> Unit,
    viewModel: RoomScanViewModel = viewModel()
) {
    DarkScreenStatusBar()
    when (viewModel.stage) {
        RoomScanViewModel.Stage.CAPTURE -> {
            BackHandler { if (viewModel.recording) viewModel.cancelRecording() else onClose() }
            CaptureStage(viewModel, onClose)
        }
        RoomScanViewModel.Stage.ANALYZING, RoomScanViewModel.Stage.FAILED -> {
            BackHandler(onBack = viewModel::backToCapture)
            AnalyzingStage(viewModel)
        }
        RoomScanViewModel.Stage.RESULT -> {
            BackHandler(onBack = onClose)
            RoomResultScreen(
                viewModel = viewModel,
                onBack = onClose,
                onAskAgent = { if (viewModel.handOffToChat()) onAskAgent() }
            )
        }
        RoomScanViewModel.Stage.MAP -> {
            BackHandler(onBack = viewModel::closeMap)
            RoomMapScreen(viewModel)
        }
    }
}

@Composable
private fun CaptureStage(viewModel: RoomScanViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewfinder = remember { ViewfinderState() }
    val pose by rememberCameraPose()
    // A gyroscope-only phone needs to be told one direction before it can track the walk.
    val headingSensor = rememberHeadingSensor()
    val needsReference = headingSensor == HeadingSensor.GYRO
    var askDirection by remember { mutableStateOf(needsReference) }
    var loadingUpload by remember { mutableStateOf(false) }
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    val toast: (String) -> Unit = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
    val recording = viewModel.recording

    /** Which way the camera faces now (the front camera looks the opposite way to the phone's back). */
    fun facingNow(): PhotoFacing? = pose?.let {
        val degrees = if (viewfinder.useFrontCamera) (it.heading + 180f) % 360f else it.heading
        PhotoFacing(degrees, it.source)
    }

    // While recording: tick the timer, and take a frame from the preview every few
    // seconds (preferring moments the phone is held upright), with its direction.
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        val started = System.currentTimeMillis()
        var lastFrame = -FRAME_EVERY_MS
        while (true) {
            val elapsed = System.currentTimeMillis() - started
            viewModel.recordSeconds = (elapsed / 1000).toInt()
            if (elapsed >= MAX_RECORD_MS) {
                viewModel.stopRecording()
                break
            }
            val every = FRAME_EVERY_MS shl viewModel.thinnings
            val due = elapsed - lastFrame >= every
            val upright = pose?.isUpright != false
            if (due && (upright || elapsed - lastFrame >= every * 2)) {
                viewfinder.snapshot()?.let { shot ->
                    viewModel.addFrame(WallPhoto(scaleDown(shot, FRAME_MAX_SIDE), facingNow()))
                    lastFrame = elapsed
                }
            }
            delay(250)
        }
    }

    fun toggleRecording() {
        when {
            recording -> viewModel.stopRecording()
            !hasCamera -> permission.launch(Manifest.permission.CAMERA)
            viewfinder.isReady -> viewModel.startRecording()
        }
    }

    // Upload: a video (frames pulled from it, directions unknown) or a single photo.
    val upload = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loadingUpload = true
        scope.launch {
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video") == true
            val frames = withContext(Dispatchers.IO) {
                runCatching {
                    if (isVideo) {
                        loadVideoFrames(context, uri, RoomScanViewModel.MAX_SENT, FRAME_MAX_SIDE)?.map { WallPhoto(it, null) }
                    } else {
                        loadPhoto(context, uri, 1600)?.let { listOf(WallPhoto(it.bitmap, it.exifHeading?.let { h -> PhotoFacing(h, PhotoFacing.Source.PHOTO) })) }
                    }
                }.getOrNull()
            }
            loadingUpload = false
            if (frames.isNullOrEmpty()) toast(tr("Couldn't open that file — pick another one.", "यह फ़ाइल खुल नहीं पाई — कोई दूसरी चुनें।"))
            else viewModel.analyzeFrames(frames)
        }
    }
    fun pickUpload() {
        if (recording || loadingUpload) return
        try {
            upload.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        } catch (e: android.content.ActivityNotFoundException) {
            toast(tr("No gallery app found on this device.", "इस फ़ोन में गैलरी ऐप नहीं मिला।"))
        }
    }

    // Full-bleed camera as in the design: the title (or REC timer), a "Facing …" chip,
    // frame corners, the hint and Upload / record / Flip at the bottom.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0A14))
            .background(Brush.radialGradient(listOf(Color(0xFF2A2547), Color(0xFF0B0A14)), center = androidx.compose.ui.geometry.Offset(540f, 500f), radius = 1300f))
    ) {
        if (hasCamera) {
            CameraViewfinder(viewfinder, Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(26.dp))
                Text(
                    tr("Camera unavailable here. Allow camera access, or upload a video of your home.", "कैमरा उपलब्ध नहीं। कैमरा की अनुमति दें, या अपने घर का वीडियो अपलोड करें।"),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                PillButton(tr("Allow camera", "कैमरा चालू करें"), Modifier.padding(top = 16.dp)) {
                    permission.launch(Manifest.permission.CAMERA)
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f), 0.22f to Color.Transparent,
                        0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)
                    )
                )
        )

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIconButton(onClick = { viewModel.cancelRecording(); onClose() }) {
                    Icon(Icons.Filled.Close, contentDescription = tr("Close", "बंद करें"), tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (recording) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFEF4444))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
                            val s = viewModel.recordSeconds
                            Text(
                                "REC %02d:%02d".format(s / 60, s % 60),
                                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 7.dp)
                            )
                        }
                    } else {
                        Text(tr("Scan your home", "अपना घर स्कैन करें"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Box(Modifier.size(38.dp))
            }
            val heading = facingNow()?.degrees
            Row(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 22.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (needsReference && heading == null) VastuPrimary else Color.White.copy(alpha = 0.14f))
                    .then(if (needsReference) Modifier.clickable(role = Role.Button) { askDirection = true } else Modifier)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Explore, null, tint = VastuSun, modifier = Modifier.size(14.dp))
                Text(
                    heading?.let {
                        tr("Facing ", "दिशा: ") + CompassDirection.of(it).localName + " · ${it.roundToInt() % 360}°" +
                            if (needsReference) tr(" · tap to reset", " · फिर सेट करें") else ""
                    } ?: if (needsReference) tr("No compass — tap to set your direction", "कम्पास नहीं — अपनी दिशा चुनें")
                        else tr("No compass — ${AnanyaAgent.NAME} will judge from the video", "कम्पास नहीं — ${AnanyaAgent.NAME_HI} वीडियो से अंदाज़ा लगाएँगी"),
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            if (askDirection) {
                DirectionPickerDialog(
                    sensor = headingSensor,
                    current = heading?.let { CompassDirection.of(it) },
                    onPick = {
                        // The picker asks about the camera; with the front camera the phone's back faces the other way.
                        CameraHeadingSensor.setDirection(if (viewfinder.useFrontCamera) it.turn(4) else it)
                        askDirection = false
                    },
                    onDismiss = { askDirection = false }
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 36.dp, end = 36.dp, top = 24.dp, bottom = 24.dp)
            ) {
                ScanCorners(Modifier.fillMaxSize())
                if (loadingUpload) CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(56.dp))
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    if (recording) tr("Keep going — walk slowly through every room. Tap stop when done.", "चलते रहें — हर कमरे में धीरे-धीरे घूमें। हो जाने पर रोकें दबाएँ।")
                    else tr("Start at the main door and walk slowly through each room.", "मुख्य दरवाज़े से शुरू करें और हर कमरे में धीरे-धीरे घूमें।"),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 270.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SquareButton(onClick = ::pickUpload, label = tr("Upload", "अपलोड"), enabled = !recording) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = tr("Upload a video or photo", "वीडियो या फ़ोटो अपलोड करें"), tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    val inner by animateDpAsState(if (recording) 28.dp else 60.dp, label = "rec_inner")
                    val corner by animateDpAsState(if (recording) 8.dp else 30.dp, label = "rec_corner")
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .border(4.dp, Color.White, CircleShape)
                            .clickable(
                                enabled = !loadingUpload && (!hasCamera || viewfinder.isReady),
                                role = Role.Button,
                                onClickLabel = if (recording) tr("Stop and analyse", "रोकें और विश्लेषण करें") else tr("Record", "रिकॉर्ड करें")
                            ) { toggleRecording() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(inner).clip(RoundedCornerShape(corner)).background(Color(0xFFEF4444)))
                    }
                    SquareButton(onClick = { viewfinder.useFrontCamera = !viewfinder.useFrontCamera }, label = tr("Flip", "पलटें"), enabled = hasCamera) {
                        Icon(Icons.Filled.Cameraswitch, contentDescription = tr("Switch camera", "कैमरा बदलें"), tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Text(
                    if (recording) tr("Tap to stop and analyse", "रोककर विश्लेषण के लिए टैप करें")
                    else tr("Record a video or upload one · First 10 min free", "वीडियो रिकॉर्ड या अपलोड करें · पहले 10 मिनट मुफ़्त"),
                    color = Color.White.copy(alpha = 0.55f), fontSize = 10.5.sp
                )
            }
        }
    }
}


@Composable
private fun AnalyzingStage(viewModel: RoomScanViewModel) {
    val failed = viewModel.stage == RoomScanViewModel.Stage.FAILED
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF0F0B2E), Color(0xFF241A5C), Color(0xFF3B1D6E))))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!failed) {
            AiOrb(120.dp, spinMillis = 2500, iconSize = 46.dp, modifier = Modifier.padding(bottom = 28.dp))
        }
        if (failed) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            viewModel.walls.take(5).forEach { wall ->
                val image = remember(wall) { wall.bitmap.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                )
            }
        }
        if (failed) {
            Text(
                tr("Couldn't analyse the room right now.", "अभी कमरे का विश्लेषण नहीं हो पाया।"),
                color = CallTones.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                tr("Check your internet connection and try again.", "इंटरनेट कनेक्शन देखें और फिर से कोशिश करें।"),
                color = CallTones.TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row(modifier = Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(tr("Record again", "फिर रिकॉर्ड करें"), outlined = true, onClick = viewModel::backToCapture)
                PillButton(tr("Try again", "फिर कोशिश करें"), onClick = viewModel::analyze)
            }
        } else {
            Text(
                tr("${AnanyaAgent.NAME} is analysing your home", "${AnanyaAgent.NAME_HI} आपके घर का विश्लेषण कर रही है"),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                tr("This takes a few seconds", "इसमें कुछ सेकंड लगते हैं"),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
            )
            AnalysisSteps()
        }
    }
}

/**
 * The analysis steps, ticked off as time passes (the model doesn't report
 * progress), with a bar that eases towards full but waits for the result.
 */
@Composable
private fun AnalysisSteps() {
    val steps = listOf(
        tr("Detecting rooms & layout", "कमरे और बनावट पहचाने जा रहे हैं"),
        tr("Mapping directions", "दिशाएँ तय की जा रही हैं"),
        tr("Checking the five elements", "पंच तत्व जाँचे जा रहे हैं"),
        tr("Calculating your Vastu score", "आपका वास्तु स्कोर निकाला जा रहा है")
    )
    val elapsed by produceState(0) {
        while (true) {
            kotlinx.coroutines.delay(500)
            value += 500
        }
    }
    val done = (elapsed / 4000).coerceAtMost(steps.size - 1)
    val fraction = 1f - kotlin.math.exp(-elapsed / 12_000f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        steps.forEachIndexed { i, label ->
            val ticked = i < done
            val active = i == done
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (ticked || active) 1f else 0.4f)) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (ticked) VastuSun else Color.Transparent)
                        .border(1.5.dp, if (ticked) VastuSun else Color.White.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (ticked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF14103A), modifier = Modifier.size(13.dp))
                }
                Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp))
            }
        }
        Box(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(listOf(VastuLilac, VastuSun)))
            )
        }
        Text(
            tr("${(fraction * 100).toInt()}% complete", "${(fraction * 100).toInt()}% पूरा"),
            color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).offset(y = (-4).dp)
        )
    }
}

/** White corner brackets framing the shot. */
@Composable
private fun ScanCorners(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val len = 28.dp.toPx()
        val w = 3.dp.toPx()
        val r = 14.dp.toPx()
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        fun corner(x: Float, y: Float, sx: Float, sy: Float) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(x, y + sy * len)
                lineTo(x, y + sy * r)
                quadraticTo(x, y, x + sx * r, y)
                lineTo(x + sx * len, y)
            }
            drawPath(path, Color.White, style = stroke)
        }
        corner(0f, 0f, 1f, 1f)
        corner(size.width, 0f, -1f, 1f)
        corner(0f, size.height, 1f, -1f)
        corner(size.width, size.height, -1f, -1f)
    }
}


@Composable
internal fun RoundIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun SquareButton(onClick: () -> Unit, label: String, enabled: Boolean = true, content: @Composable () -> Unit) {
    Column(
        Modifier
            .width(64.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { content() }
        Text(label, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
    }
}

/** Filled (or outlined) rounded button with a text label. */
@Composable
internal fun PillButton(
    label: String,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (outlined) Modifier.border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                else Modifier.background(VastuPrimary)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** A frame from the live camera every this often while recording (doubling on long walks). */
private const val FRAME_EVERY_MS = 2_500L
/** Recording stops by itself after this long. */
private const val MAX_RECORD_MS = 3 * 60_000L
/** Frames are kept at most this many pixels on their long side. */
private const val FRAME_MAX_SIDE = 1024

/**
 * A room report saved with the chat with Ananya, opened again from its
 * message. Same screens as a fresh scan; "Scan again" starts a new one.
 */
@Composable
fun RoomReportScreen(
    messageId: Int,
    onClose: () -> Unit,
    viewModel: RoomScanViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(messageId) {
        if (viewModel.analysis != null) return@LaunchedEffect
        val scan = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.vastutalks.app.data.ai.AgentChatStore(context).loadReport(messageId)
        }
        if (scan != null) {
            viewModel.showSaved(scan)
        } else {
            android.widget.Toast.makeText(context, tr("Couldn't open this report.", "यह रिपोर्ट नहीं खुल सकी।"), android.widget.Toast.LENGTH_SHORT).show()
            onClose()
        }
    }
    if (viewModel.analysis == null && viewModel.stage == RoomScanViewModel.Stage.CAPTURE) {
        DarkScreenStatusBar()
        Box(modifier = Modifier.fillMaxSize().background(CallTones.Ink))
    } else {
        RoomScanScreen(onClose = onClose, onAskAgent = onClose, viewModel = viewModel)
    }
}
