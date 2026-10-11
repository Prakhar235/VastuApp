package com.vastutalks.app.ui.components

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vastutalks.app.data.model.Post
import com.vastutalks.app.data.repository.ProfilePhotos
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuPrimary

/**
 * One expert post: author (tap to open their profile, when
 * [onAuthorClick] is set), when it was posted, the text and the optional
 * picture, then Like and Comment buttons with their counts. With
 * [onDelete] (the author's own list), a delete button that asks first.
 * [onOpen] opens the post with its comments (tapping the text or
 * Comment button). With [showComments], the latest comments are listed
 * under the post (off on the post's own screen, which lists them all).
 */
@Composable
fun PostCard(
    post: Post,
    modifier: Modifier = Modifier,
    liked: Boolean = false,
    onLikeClick: () -> Unit = {},
    onOpen: (() -> Unit)? = null,
    onAuthorClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    showComments: Boolean = true
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val image = remember(post.image) {
        ProfilePhotos.decode(post.image)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onAuthorClick != null) Modifier.clickable { onAuthorClick() } else Modifier)
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 10.dp)
        ) {
            ProfileAvatar(post.authorPhoto, post.authorName, size = 38.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(post.authorName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2B2B2B))
                Text(
                    "${post.authorSpecialty} · ${timeAgo(post.createdAtMillis)}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            if (onDelete != null) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable { confirmDelete = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete post", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }

        val openOnTap = if (onOpen != null) Modifier.clickable { onOpen() } else Modifier
        if (post.text.isNotBlank()) {
            Text(
                post.text,
                fontSize = 14.sp,
                color = Color(0xFF2B2B2B),
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth().then(openOnTap).padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }

        image?.let {
            Image(
                it,
                contentDescription = "Picture in ${post.authorName}'s post",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).then(openOnTap)
            )
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F0F6)))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            PostAction(
                icon = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = if (post.likeCount > 0) "${post.likeCount}" else "Like",
                tint = if (liked) Danger else TextSecondary,
                onClick = onLikeClick,
                modifier = Modifier.weight(1f)
            )
            PostAction(
                icon = Icons.Outlined.ChatBubbleOutline,
                label = when (post.commentCount) {
                    0L -> "Comment"
                    1L -> "1 comment"
                    else -> "${post.commentCount} comments"
                },
                tint = TextSecondary,
                onClick = { onOpen?.invoke() },
                modifier = Modifier.weight(1f)
            )
        }

        if (showComments && post.recentComments.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(openOnTap)
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            ) {
                if (post.commentCount > post.recentComments.size) {
                    Text(
                        "View all ${post.commentCount} comments",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                post.recentComments.forEach { comment ->
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))) {
                                append(comment.authorName)
                            }
                            if (comment.authorIsExpert) {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = VastuPrimary, fontSize = 11.sp)) {
                                    append(if (comment.authorName == post.authorName) " · Author" else " · Expert")
                                }
                            }
                            append("  ")
                            if (comment.replyToName.isNotBlank()) {
                                withStyle(SpanStyle(color = VastuPrimary)) { append("@${comment.replyToName} ") }
                            }
                            append(comment.text)
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF2B2B2B),
                        lineHeight = 18.sp,
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
                Text(
                    "Add a comment…",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Color.White,
            title = { Text("Delete this post?") },
            text = { Text("It will be removed from every user's feed.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = Danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = VastuPrimary) } }
        )
    }
}

@Composable
private fun PostAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        Text(" $label", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

internal fun timeAgo(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
