package com.vastutalks.app.ui.screens.home

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.VerifiedUser
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.vastutalks.app.data.repository.PostRepository
import com.vastutalks.app.data.repository.UserRepository
import com.vastutalks.app.ui.components.rememberPhotoPicker
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.Poppins
import com.vastutalks.app.ui.theme.TextPrimary
import com.vastutalks.app.ui.theme.VastuPrimary
import kotlinx.coroutines.launch

/** The design's article categories. */
val ARTICLE_CATEGORIES = listOf("Home", "Office", "Kitchen", "Bedroom", "Remedies", "Plot & Land")

private const val MIN_TITLE = 8
private const val MIN_WORDS = 30
private const val MAX_BODY = 4000

private val Slate = Color(0xFF64748B)
private val Ink = Color(0xFF1A1440)

/** One unfinished article, kept on this device (the cover photo isn't kept). */
private object ArticleDraft {
    private fun prefs(context: Context) = context.getSharedPreferences("article_draft", Context.MODE_PRIVATE)
    fun load(context: Context): Triple<String, String, String> = prefs(context).let {
        Triple(it.getString("title", "").orEmpty(), it.getString("body", "").orEmpty(), it.getString("cat", ARTICLE_CATEGORIES[0]).orEmpty())
    }
    fun save(context: Context, title: String, body: String, cat: String) =
        prefs(context).edit().putString("title", title).putString("body", body).putString("cat", cat).apply()
    fun clear(context: Context) = prefs(context).edit().clear().apply()
    fun exists(context: Context) = load(context).let { it.first.isNotBlank() || it.second.isNotBlank() }
}

/**
 * The design's "New article" screen, where a Vastu expert writes for the
 * Explore feed: cover photo, title, category and text. Closing with
 * anything written keeps it as a draft on this device.
 */
@Composable
fun CreatePostScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draft = remember { ArticleDraft.load(context) }
    val editingDraft = remember { draft.first.isNotBlank() || draft.second.isNotBlank() }
    var title by remember { mutableStateOf(draft.first) }
    var body by remember { mutableStateOf(draft.second) }
    var category by remember { mutableStateOf(draft.third.takeIf { it in ARTICLE_CATEGORIES } ?: ARTICLE_CATEGORIES[0]) }
    var cover by remember { mutableStateOf<Bitmap?>(null) }
    var posting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val toast: (String) -> Unit = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
    val pickCover = rememberPhotoPicker(onError = toast, onPicked = { cover = it.bitmap })

    val words = body.trim().let { if (it.isEmpty()) 0 else it.split(Regex("\\s+")).size }
    val ready = title.trim().length >= MIN_TITLE && words >= MIN_WORDS
    val hasText = title.isNotBlank() || body.isNotBlank()
    val hint = when {
        ready -> "Ready to publish"
        title.trim().length < MIN_TITLE -> "Add a title"
        else -> "Write at least $MIN_WORDS words"
    }

    fun close() {
        if (posting) return
        if (hasText) {
            ArticleDraft.save(context, title, body, category)
            toast("Saved to drafts")
        } else {
            ArticleDraft.clear(context)
        }
        onDone()
    }

    fun publish() {
        if (!ready) {
            toast(if (title.trim().length < MIN_TITLE) "Add a title ($MIN_TITLE+ characters)" else "Write at least $MIN_WORDS words")
            return
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        posting = true
        error = null
        scope.launch {
            val author = UserRepository().getExpert(uid).getOrNull()
            val result = if (author == null) Result.failure(IllegalStateException("Only Vastu experts can publish articles."))
            else PostRepository().create(author, body, cover, title = title, category = category)
            posting = false
            result.fold(
                onSuccess = {
                    ArticleDraft.clear(context)
                    toast("Article published")
                    onDone()
                },
                onFailure = { error = "Couldn't publish: ${it.message}" }
            )
        }
    }

    BackHandler(onBack = ::close)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF1F1F6)).clickable(onClick = ::close),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Close, "Close", tint = Color(0xFF334155), modifier = Modifier.size(18.dp)) }
            Text(
                if (editingDraft) "Edit draft" else "New article",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(36.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F0F6)))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CoverPicker(cover, onPick = pickCover)

            Column {
                Box {
                    if (title.isEmpty()) Text("Article title", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA0AEC0))
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it.replace("\n", "").take(90) },
                        textStyle = TextStyle(fontFamily = Poppins, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
                        cursorBrush = SolidColor(VastuPrimary),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFECEBF3)))
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                CategoryChips(category, onPick = { category = it })
            }

            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 190.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF6F6F9))
                        .padding(14.dp)
                ) {
                    if (body.isEmpty()) Text(
                        "Share your Vastu insight… Start with the problem, then give 2–3 simple remedies readers can try at home.",
                        fontSize = 13.5.sp, lineHeight = 21.sp, color = Color(0xFFA0AEC0)
                    )
                    BasicTextField(
                        value = body,
                        onValueChange = { body = it.take(MAX_BODY) },
                        textStyle = TextStyle(fontFamily = Poppins, fontSize = 13.5.sp, lineHeight = 21.sp, color = TextPrimary),
                        cursorBrush = SolidColor(VastuPrimary),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 162.dp)
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("$words words · ${maxOf(1, Math.round(words / 200f))} min read", fontSize = 11.sp, color = Slate, modifier = Modifier.weight(1f))
                    Text(hint, fontSize = 11.sp, color = Slate)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF0FDF4))
                    .border(1.dp, Color(0xFFDCFCE7), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Icon(Icons.Outlined.VerifiedUser, null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                Text(
                    "Your article goes live in the Explore feed as soon as you publish. Please avoid phone numbers and promotional links.",
                    fontSize = 11.5.sp, lineHeight = 17.sp, color = Color(0xFF14532D),
                    modifier = Modifier.padding(start = 10.dp)
                )
            }

            error?.let { Text(it, color = Danger, fontSize = 12.5.sp) }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F0F6)))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFFE4E2EE), RoundedCornerShape(14.dp))
                    .clickable(enabled = !posting) {
                        if (!hasText) toast("Nothing to save yet")
                        else {
                            ArticleDraft.save(context, title, body, category)
                            toast("Saved to drafts")
                            onDone()
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text("Save draft", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary) }
            Box(
                Modifier
                    .weight(1.5f)
                    .height(48.dp)
                    .alpha(if (ready) 1f else 0.45f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VastuPrimary)
                    .clickable(enabled = !posting, onClick = ::publish),
                contentAlignment = Alignment.Center
            ) {
                if (posting) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Text("Publish article", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

/** The dashed 150dp "Add cover photo" box, or the picked photo with "Change cover". */
@Composable
private fun CoverPicker(cover: Bitmap?, onPick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(shape)
            .background(Color(0xFFFAF9FE))
            .then(
                if (cover == null) Modifier.dashedBorder(Color(0xFFC9C1F2)) else Modifier
            )
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center
    ) {
        if (cover != null) {
            Image(
                remember(cover) { cover.asImageBitmap() },
                contentDescription = "Article cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                "Change cover",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xBF0F0B2E))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.AddPhotoAlternate, null, tint = Color(0xFF5B4BD6), modifier = Modifier.size(24.dp))
                Text("Add cover photo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B4BD6))
                Text("Optional · a wide photo works best", fontSize = 11.sp, color = Slate)
            }
        }
    }
}

private fun Modifier.dashedBorder(color: Color): Modifier = drawBehind {
    val stroke = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ARTICLE_CATEGORIES.forEach { c ->
            val on = c == selected
            Text(
                c,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (on) Color.White else Color(0xFF334155),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) Ink else Color.White)
                    .border(1.dp, if (on) Ink else Color(0xFFE4E2EE), RoundedCornerShape(999.dp))
                    .clickable { onPick(c) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

/** True when an unfinished article is waiting on this device (Home shows it as a draft). */
fun hasArticleDraft(context: Context): Boolean = ArticleDraft.exists(context)

/** The draft's title, for the "Your articles" list. */
fun articleDraftTitle(context: Context): String = ArticleDraft.load(context).first
