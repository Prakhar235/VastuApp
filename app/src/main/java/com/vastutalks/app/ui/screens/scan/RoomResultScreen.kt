package com.vastutalks.app.ui.screens.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vastutalks.app.data.ai.CompassDirection
import com.vastutalks.app.data.ai.BoardShape
import com.vastutalks.app.data.ai.RoomFinding
import com.vastutalks.app.data.ai.RoomZone
import com.vastutalks.app.data.ai.Severity
import com.vastutalks.app.data.ai.tr
import com.vastutalks.app.data.model.AnanyaAgent
import com.vastutalks.app.ui.components.CallTones
import com.vastutalks.app.ui.theme.VastuPrimary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Severity.color: Color
    get() = when (this) {
        Severity.MAJOR -> ScanTones.Major
        Severity.MINOR -> ScanTones.Minor
        Severity.GOOD -> ScanTones.Good
    }

/**
 * Ananya's read of the scanned room: the score, each wall's photo with
 * her findings numbered on it, and the list of what to fix, most
 * important first. Tapping a finding spotlights it on its photo.
 */
@Composable
internal fun RoomResultScreen(viewModel: RoomScanViewModel, onBack: () -> Unit, onAskAgent: () -> Unit) {
    val analysis = viewModel.analysis ?: return
    val walls = viewModel.walls
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var shownPhoto by remember { mutableStateOf(analysis.findings.firstOrNull()?.photo ?: 0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var photoTop by remember { mutableStateOf(0) }

    fun spotlight(finding: RoomFinding) {
        selected = if (selected == finding.number) null else finding.number
        shownPhoto = finding.photo
        scope.launch { scroll.animateScrollTo(photoTop) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CallTones.Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scroll)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "वापस"), tint = CallTones.TextPrimary, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text("${analysis.room} · ${tr("analysis", "विश्लेषण")}", color = CallTones.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    tr("${walls.size} walls · directions from the compass", "${walls.size} दीवारें · कम्पास से मापी दिशा"),
                    color = CallTones.TextMuted,
                    fontSize = 13.sp
                )
            }
        }

        ScoreCard(analysis.score, analysis.verdict, analysis.summary)

        if (walls.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                walls.forEachIndexed { i, wall ->
                    val active = i == shownPhoto
                    Text(
                        wall.facing.direction.localName,
                        color = if (active) Color.White else ScanTones.Body,
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .then(if (active) Modifier.background(VastuPrimary) else Modifier.border(1.dp, ScanTones.Track, RoundedCornerShape(18.dp)))
                            .selectable(selected = active, role = Role.Tab) { shownPhoto = i; selected = null }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        walls.getOrNull(shownPhoto)?.let { wall ->
            Box(modifier = Modifier.onGloballyPositioned { photoTop = it.positionInParent().y.roundToInt() }) {
                MarkedPhoto(
                    wall = wall,
                    findings = analysis.findings.filter { it.photo == shownPhoto },
                    selected = selected,
                    onSelect = { n -> selected = if (selected == n) null else n }
                )
            }
        }

        if (analysis.findings.isNotEmpty()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(tr("What to fix", "क्या ठीक करें"), color = CallTones.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(tr("Most important first", "ज़रूरी पहले"), color = CallTones.TextMuted, fontSize = 13.sp)
            }
        }
        analysis.findings.forEach { finding ->
            FindingCard(
                finding = finding,
                isSelected = selected == finding.number,
                onClick = { spotlight(finding) },
                onShowMap = { viewModel.showOnMap(finding) }
            )
        }

        Text(
            tr(
                "Directions come from the phone's compass (about ±10°). If one looks wrong, scan again holding the phone away from metal and electronics.",
                "दिशा फ़ोन के कम्पास से ली गई है (सटीकता लगभग ±10°)। गलत लगे तो फ़ोन को धातु और इलेक्ट्रॉनिक्स से दूर रखकर फिर से स्कैन करें।"
            ),
            color = Color(0xFF8D87A6),
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        PillButton(
            tr("Ask ${AnanyaAgent.NAME.substringBefore(' ')} about this room", "${AnanyaAgent.NAME.substringBefore(' ')} से इस कमरे के बारे में पूछें"),
            modifier = Modifier.fillMaxWidth(),
            outlined = true,
            onClick = onAskAgent
        )
        PillButton(tr("Scan again", "फिर से स्कैन करें"), modifier = Modifier.fillMaxWidth(), onClick = viewModel::rescan)
    }
}

@Composable
private fun ScoreCard(score: Int, verdict: String, summary: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CallTones.Surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                val inset = stroke.width / 2
                val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                drawArc(ScanTones.Track, 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
                drawArc(VastuPrimary, -90f, 360f * score / 100f, false, Offset(inset, inset), arcSize, style = stroke)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$score", color = CallTones.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp)
                Text("/ 100", color = CallTones.TextMuted, fontSize = 11.sp)
            }
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                tr("Vastu score", "वास्तु स्कोर") + if (verdict.isNotBlank()) " · $verdict" else "",
                color = CallTones.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (summary.isNotBlank()) {
                Text(summary, color = ScanTones.Body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/**
 * The wall photo at its own shape, with each finding outlined in its
 * severity colour and numbered. With a [selected] finding, the others
 * fade so it stands out.
 */
@Composable
private fun MarkedPhoto(
    wall: com.vastutalks.app.data.ai.WallPhoto,
    findings: List<RoomFinding>,
    selected: Int?,
    onSelect: (Int) -> Unit
) {
    val image = remember(wall) { wall.bitmap.asImageBitmap() }
    val ratio = wall.bitmap.width.toFloat() / wall.bitmap.height
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CallTones.Surface),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(modifier = Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)) {
            val w = maxWidth
            val h = maxHeight
            Image(image, contentDescription = tr("Wall photo", "दीवार की फ़ोटो"), contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(modifier = Modifier.fillMaxSize()) {
                findings.forEach { f ->
                    val alpha = if (selected == null || selected == f.number) 1f else 0.3f
                    val stroke = Stroke(width = (if (selected == f.number) 4f else 2.5f).dp.toPx())
                    when (val m = f.mark) {
                        is BoardShape.Rect -> drawRoundRect(
                            color = f.severity.color.copy(alpha = alpha),
                            topLeft = Offset(m.x / 100f * size.width, m.y / 100f * size.height),
                            size = Size(m.w / 100f * size.width, m.h / 100f * size.height),
                            cornerRadius = CornerRadius(6.dp.toPx()),
                            style = stroke
                        )
                        is BoardShape.Circle -> drawCircle(
                            color = f.severity.color.copy(alpha = alpha),
                            radius = maxOf(m.r / 100f * size.width, 14.dp.toPx()),
                            center = Offset(m.x / 100f * size.width, m.y / 100f * size.height),
                            style = stroke
                        )
                        else -> Unit
                    }
                }
            }
            findings.forEach { f ->
                val (cx, cy) = when (val m = f.mark) {
                    is BoardShape.Rect -> (m.x + m.w / 2) to (m.y + m.h / 2)
                    is BoardShape.Circle -> (m.x + m.r * 0.7f) to (m.y + m.r * 0.7f * ratio)
                    else -> return@forEach
                }
                val alpha = if (selected == null || selected == f.number) 1f else 0.4f
                Box(
                    modifier = Modifier
                        .offset(x = w * (cx / 100f) - 15.dp, y = h * (cy / 100f) - 15.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(f.severity.color.copy(alpha = alpha))
                        .border(2.dp, Color.White.copy(alpha = alpha), CircleShape)
                        .clickable(role = Role.Button, onClickLabel = f.title) { onSelect(f.number) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("${f.number}", color = Color(0xFF1A0E06), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ScanTones.Scrim)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Explore, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(
                    tr("This wall: ", "यह दीवार: ") + "${wall.facing.direction.localName} · ${wall.facing.degrees.roundToInt() % 360}°",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun FindingCard(finding: RoomFinding, isSelected: Boolean, onClick: () -> Unit, onShowMap: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CallTones.Surface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = when {
                    isSelected -> finding.severity.color
                    finding.severity == Severity.MAJOR -> Color(0xFF3A2B24)
                    else -> Color.Transparent
                },
                shape = shape
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberBadge(finding)
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(finding.title, color = CallTones.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (finding.severity == Severity.GOOD) {
                    Text(tr("Well placed · no change needed", "सही जगह · कोई बदलाव नहीं"), color = CallTones.TextMuted, fontSize = 13.sp)
                }
            }
            when (finding.severity) {
                Severity.GOOD -> Icon(Icons.Filled.Check, contentDescription = null, tint = ScanTones.Good, modifier = Modifier.size(20.dp))
                else -> Text(
                    if (finding.severity == Severity.MAJOR) tr("Major", "बड़ी") else tr("Minor", "छोटी"),
                    color = finding.severity.color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(finding.severity.color.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
        }
        if (finding.why.isNotBlank() && (finding.severity != Severity.GOOD || isSelected)) {
            Text(finding.why, color = ScanTones.Body, fontSize = 14.sp, lineHeight = 21.sp)
        }
        if (finding.severity == Severity.MAJOR && finding.remedies.isNotEmpty()) {
            RemedyBox(finding.remedies)
        }
        if (finding.betterZone != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (finding.severity == Severity.MAJOR) Modifier.background(VastuPrimary)
                        else Modifier.border(1.dp, VastuPrimary, RoundedCornerShape(12.dp))
                    )
                    .clickable(role = Role.Button, onClick = onShowMap),
                contentAlignment = Alignment.Center
            ) {
                Text(tr("See the new spot on the map", "नक्शे पर नई जगह देखें"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NumberBadge(finding: RoomFinding, size: Int = 28) {
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(finding.severity.color),
        contentAlignment = Alignment.Center
    ) {
        Text("${finding.number}", color = Color(0xFF1A0E06), fontWeight = FontWeight.ExtraBold, fontSize = (size / 2).sp)
    }
}

@Composable
private fun RemedyBox(remedies: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CallTones.Ink)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(tr("Fixes without moving it", "बिना शिफ्ट किए उपाय"), color = CallTones.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        remedies.forEach { Text("• $it", color = CallTones.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp) }
    }
}

/**
 * Top-down map of the room as the nine Vastu zones, North up: every
 * finding in its zone, and for [RoomScanViewModel.mapFinding] a dashed
 * arrow to the zone it should move to, plus questions to check the move
 * is practical and what to do if it isn't.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoomMapScreen(viewModel: RoomScanViewModel) {
    val analysis = viewModel.analysis ?: return
    val focus = viewModel.mapFinding ?: return
    val target = focus.betterZone ?: return
    val answers = remember(focus) { mutableStateMapOf<Int, Boolean>() }
    val cantMove = answers.values.any { !it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CallTones.Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(onClick = viewModel::closeMap) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "वापस"), tint = CallTones.TextPrimary, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(tr("New spot for the ${focus.objectName}", "${focus.objectName} की नई जगह"), color = CallTones.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(tr("Top-down map of the room", "कमरे का ऊपर से नक्शा"), color = CallTones.TextMuted, fontSize = 13.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CallTones.Surface)
                .padding(top = 14.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SideLabel("${CompassDirection.N.localName} ↑")
            Row(verticalAlignment = Alignment.CenterVertically) {
                SideLabel(CompassDirection.W.localName.take(1), Modifier.width(26.dp))
                ZoneGrid(analysis.findings, focus, target, Modifier.weight(1f))
                SideLabel(CompassDirection.E.localName.take(1), Modifier.width(26.dp))
            }
            SideLabel(CompassDirection.S.localName)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            analysis.findings.filter { it.zone != null }.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(f.severity.color))
                    Text("${f.number} ${f.objectName}", color = ScanTones.Body, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }

        if (focus.checks.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CallTones.Surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(tr("Is this possible?", "क्या यह संभव है?"), color = CallTones.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                focus.checks.forEachIndexed { i, check ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(check, color = ScanTones.Body, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                        AnswerChip(tr("Yes", "हाँ"), answers[i] == true) { answers[i] = true }
                        AnswerChip(tr("No", "नहीं"), answers[i] == false) { answers[i] = false }
                    }
                }
            }
        }

        if (focus.remedies.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (cantMove) CallTones.Surface else CallTones.Ink)
                    .border(if (cantMove) 2.dp else 1.dp, if (cantMove) VastuPrimary else ScanTones.Track, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(tr("Can't move it?", "शिफ्ट नहीं कर सकते?"), color = CallTones.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    tr("Leave the ${focus.objectName} where it is and:", "${focus.objectName} जहाँ है वहीं रहने दें और:"),
                    color = ScanTones.Body,
                    fontSize = 14.sp
                )
                focus.remedies.forEach { Text("• $it", color = CallTones.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp) }
            }
        }

        PillButton(tr("Scan again after the changes", "बदलाव के बाद फिर से स्कैन करें"), modifier = Modifier.fillMaxWidth(), onClick = viewModel::rescan)
    }
}

/** The 3×3 zone grid with each finding in its zone and the suggested move drawn as a dashed arrow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZoneGrid(findings: List<RoomFinding>, focus: RoomFinding, target: RoomZone, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .border(4.dp, Color(0xFF8C86A6), RoundedCornerShape(6.dp))
            .background(CallTones.Ink)
    ) {
        val cell = maxWidth / 3
        RoomZone.entries.forEach { zone ->
            val isTarget = zone == target
            Column(
                modifier = Modifier
                    .offset(x = cell * zone.col, y = cell * zone.row)
                    .size(cell)
                    .border(0.5.dp, ScanTones.Track)
                    .background(if (isTarget) VastuPrimary.copy(alpha = 0.14f) else Color.Transparent)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    zone.label,
                    color = if (isTarget) Color(0xFFC9B8FF) else Color(0xFF6E6889),
                    fontSize = 10.sp,
                    fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal,
                    lineHeight = 12.sp
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    findings.filter { it.zone == zone }.forEach { f ->
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(f.severity.color.copy(alpha = if (f == focus) 1f else 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${f.number}", color = Color(0xFF1A0E06), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    if (isTarget) {
                        Text(
                            tr("New spot", "नई जगह"),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(VastuPrimary)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
        focus.zone?.let { from ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = size.width / 3
                val start = Offset(c * (from.col + 0.5f), c * (from.row + 0.62f))
                val end = Offset(c * (target.col + 0.5f), c * (target.row + 0.62f))
                drawLine(
                    VastuPrimary,
                    start,
                    end,
                    strokeWidth = 2.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                )
                drawCircle(VastuPrimary, radius = 5.dp.toPx(), center = end)
            }
        }
    }
}

@Composable
private fun SideLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = CallTones.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = modifier)
}

@Composable
private fun AnswerChip(label: String, chosen: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (chosen) Color.White else ScanTones.Body,
        fontSize = 14.sp,
        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(if (chosen) Modifier.background(VastuPrimary) else Modifier.border(1.dp, ScanTones.Track, RoundedCornerShape(18.dp)))
            .selectable(selected = chosen, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}
