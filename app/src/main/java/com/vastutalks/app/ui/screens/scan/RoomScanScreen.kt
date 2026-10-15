package com.vastutalks.app.ui.screens.scan

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.vastutalks.app.ui.components.rememberCameraPose
import com.vastutalks.app.ui.components.rememberPhotoPicker
import com.vastutalks.app.ui.theme.VastuPrimary
import kotlinx.coroutines.launch
import kotlin.math.abs
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
 * The guided room scan: photograph each wall with the live compass
 * showing which way the camera faces, then see Ananya's read of the room
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
            BackHandler(onBack = onClose)
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
    var capturing by remember { mutableStateOf(false) }
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    val toast: (String) -> Unit = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
    val wall = viewModel.currentWall

    val pickPhoto = rememberPhotoPicker(onError = toast) { photo ->
        // A gallery photo is taken to show the wall being asked for, unless it saved its own heading.
        val facing = photo.exifHeading?.let { PhotoFacing(it, PhotoFacing.Source.PHOTO) }
            ?: PhotoFacing(wall.degrees, PhotoFacing.Source.CALLER)
        viewModel.onPhoto(WallPhoto(photo.bitmap, facing))
    }

    fun shoot() {
        if (capturing || !viewfinder.isReady) return
        capturing = true
        val heading = pose?.heading
        scope.launch {
            val photo = viewfinder.takePhoto(context)
            capturing = false
            if (photo == null) {
                toast(tr("Couldn't take the photo — try again.", "फ़ोटो नहीं खिंची — फिर से कोशिश करें।"))
                return@launch
            }
            val facing = heading?.let { PhotoFacing(it, PhotoFacing.Source.COMPASS) }
                ?: PhotoFacing(wall.degrees, PhotoFacing.Source.CALLER)
            viewModel.onPhoto(WallPhoto(photo.bitmap, facing))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CallTones.Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "वापस"), tint = CallTones.TextPrimary, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(tr("Room scan", "कमरा स्कैन"), color = CallTones.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    tr("Wall ${viewModel.step + 1} / 4 · one photo of each wall", "दीवार ${viewModel.step + 1} / 4 · हर दीवार की एक फ़ोटो"),
                    color = CallTones.TextMuted,
                    fontSize = 13.sp
                )
            }
            LanguageSwitch()
        }

        WallProgress(viewModel)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CallTones.Surface)
        ) {
            if (hasCamera) {
                CameraViewfinder(viewfinder, Modifier.fillMaxSize())
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        tr("Allow the camera to photograph each wall with its compass direction.", "हर दीवार की फ़ोटो दिशा के साथ लेने के लिए कैमरा चालू करें।"),
                        color = CallTones.TextPrimary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    PillButton(tr("Allow camera", "कैमरा चालू करें"), Modifier.padding(top = 16.dp)) {
                        permission.launch(Manifest.permission.CAMERA)
                    }
                }
            }

            CompassDial(pose?.heading, Modifier.align(Alignment.Center))

            val heading = pose?.heading
            val (aimText, aimColor) = when {
                heading == null -> tr("No compass — face the ${wall.localName} wall", "कम्पास नहीं — ${wall.localName} की दीवार की ओर मुड़ें") to ScanTones.Minor
                else -> {
                    val off = signedDelta(heading, wall.degrees)
                    when {
                        abs(off) <= ON_TARGET -> tr("Compass on target", "कम्पास सटीक") to ScanTones.GoodText
                        off > 0 -> tr("Turn left ${off.roundToInt()}°", "बाएँ मुड़ें ${off.roundToInt()}°") to ScanTones.Minor
                        else -> tr("Turn right ${(-off).roundToInt()}°", "दाएँ मुड़ें ${(-off).roundToInt()}°") to ScanTones.Minor
                    }
                }
            }
            OverlayPill(
                text = aimText,
                color = aimColor,
                showCheck = aimColor == ScanTones.GoodText,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
            )
            pose?.let {
                OverlayPill(
                    text = if (it.isUpright) tr("Phone upright", "फ़ोन सीधा") else tr("Hold phone upright", "फ़ोन सीधा रखें"),
                    color = if (it.isUpright) CallTones.TextPrimary else ScanTones.Minor,
                    showCheck = it.isUpright,
                    modifier = Modifier.align(Alignment.TopEnd).padding(14.dp)
                )
            }
            Text(
                tr("Stand facing the ${wall.localName} wall and fit the whole wall in the frame", "${wall.localName} वाली दीवार के सामने खड़े होकर पूरी दीवार फ़्रेम में लें"),
                color = CallTones.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ScanTones.Scrim)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
            if (capturing) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(56.dp))
            }
        }

        if (viewModel.photos.isNotEmpty()) {
            Text(
                tr("Analyse now with ${viewModel.photos.size} of 4 walls", "${viewModel.photos.size} / 4 दीवारों के साथ अभी विश्लेषण करें"),
                color = Color(0xFFB9A6FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { viewModel.analyze() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SquareButton(onClick = pickPhoto) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = tr("Pick from gallery", "गैलरी से चुनें"), tint = CallTones.TextPrimary, modifier = Modifier.size(22.dp))
            }
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(4.dp, CallTones.TextPrimary, CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(if (hasCamera && viewfinder.isReady) VastuPrimary else ScanTones.Track)
                    .clickable(enabled = hasCamera && viewfinder.isReady && !capturing, role = Role.Button, onClickLabel = tr("Take photo", "फ़ोटो लें")) { shoot() }
            )
            SquareButton(onClick = viewModel::skip) {
                Text(tr("Skip", "छोड़ें"), color = CallTones.TextMuted, fontSize = 13.sp)
            }
        }
    }
}

/** The four walls, done / current / to do; tap one to (re)take it. */
@Composable
private fun WallProgress(viewModel: RoomScanViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RoomScanViewModel.WALLS.forEach { direction ->
            val done = direction in viewModel.photos
            val current = direction == viewModel.currentWall
            val skipped = viewModel.skipped[direction] == true
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Tab) { viewModel.goToWall(direction) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when {
                                current -> VastuPrimary
                                done -> ScanTones.Good
                                else -> ScanTones.Track
                            }
                        )
                )
                Text(
                    direction.localName + when {
                        done && !current -> " ✓"
                        skipped && !current -> " –"
                        else -> ""
                    },
                    color = when {
                        current -> CallTones.TextPrimary
                        done -> ScanTones.Good
                        else -> CallTones.TextMuted
                    },
                    fontSize = 12.sp,
                    fontWeight = if (current || done) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/** Big round readout of where the camera points: degrees and direction name. */
@Composable
private fun CompassDial(heading: Float?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .size(150.dp)
            .clip(CircleShape)
            .background(Color(0x8C12101C))
            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.Explore, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        Text(
            heading?.let { "${it.roundToInt() % 360}°" } ?: "–",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            heading?.let { CompassDirection.of(it).localName } ?: tr("No compass", "कम्पास नहीं"),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AnalyzingStage(viewModel: RoomScanViewModel) {
    val failed = viewModel.stage == RoomScanViewModel.Stage.FAILED
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CallTones.Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            viewModel.walls.forEach { wall ->
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
                PillButton(tr("Back to photos", "फ़ोटो पर वापस"), outlined = true, onClick = viewModel::backToCapture)
                PillButton(tr("Try again", "फिर कोशिश करें"), onClick = viewModel::analyze)
            }
        } else {
            CircularProgressIndicator(color = VastuPrimary, modifier = Modifier.padding(top = 28.dp))
            Text(
                tr("${AnanyaAgent.NAME.substringBefore(' ')} is reading your room…", "${AnanyaAgent.NAME.substringBefore(' ')} आपके कमरे को देख रही हैं…"),
                color = CallTones.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                tr("Finding each object, its direction, and what to fix", "हर चीज़, उसकी दिशा और क्या ठीक करना है — देखा जा रहा है"),
                color = CallTones.TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun OverlayPill(text: String, color: Color, showCheck: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ScanTones.Scrim)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCheck) Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.padding(end = 6.dp).size(14.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun RoundIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(CallTones.Surface)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun SquareButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CallTones.Surface)
            .border(1.dp, ScanTones.Track, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
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
            .clip(RoundedCornerShape(24.dp))
            .then(
                if (outlined) Modifier.border(1.dp, VastuPrimary, RoundedCornerShape(24.dp))
                else Modifier.background(VastuPrimary)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** How far [heading] is clockwise of [target], from -180 to 180 (positive = turned too far right). */
private fun signedDelta(heading: Float, target: Float): Float = ((heading - target + 540f) % 360f) - 180f

/** Within this many degrees of the wall's direction counts as facing it. */
private const val ON_TARGET = 20f

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
