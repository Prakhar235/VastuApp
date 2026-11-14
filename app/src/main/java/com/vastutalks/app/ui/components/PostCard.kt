package com.vastutalks.app.ui.components

import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.BookmarkBorder
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
    showComments: Boolean = false,
    following: Boolean = false,
    onFollowClick: (() -> Unit)? = null
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val image = remember(post.image) {
        ProfilePhotos.decode(post.image)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    val openOnTap = if (onOpen != null) Modifier.clickable { onOpen() } else Modifier

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, com.vastutalks.app.ui.theme.CardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .then(if (onAuthorClick != null) Modifier.clickable { onAuthorClick() } else Modifier)
            ) {
                ProfileAvatar(post.authorPhoto, post.authorName, size = 34.dp)
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(post.authorName, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = com.vastutalks.app.ui.theme.TextPrimary, maxLines = 1)
                    Text(timeAgo(post.createdAtMillis), fontSize = 10.5.sp, color = com.vastutalks.app.ui.theme.TextHint, modifier = Modifier.padding(top = 1.dp))
                }
            }
            if (onDelete != null) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).clickable { confirmDelete = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete post", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            } else if (onFollowClick != null) {
                Text(
                    if (following) "Following" else "Follow",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (following) VastuPrimary else Color.White,
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (following) Color.White else VastuPrimary)
                        .border(1.dp, VastuPrimary, RoundedCornerShape(999.dp))
                        .clickable { onFollowClick() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        if (post.text.isNotBlank()) {
            Text(
                post.text,
                fontSize = 12.5.sp,
                color = Color(0xFF2D3748),
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth().then(openOnTap)
            )
        }

        image?.let {
            Image(
                it,
                contentDescription = "Picture in ${post.authorName}'s post",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).then(openOnTap)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            PostAction(
                icon = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = "${post.likeCount}",
                tint = if (liked) Color(0xFFE53E3E) else TextSecondary,
                onClick = onLikeClick
            )
            PostAction(
                icon = Icons.Outlined.ChatBubbleOutline,
                label = "${post.commentCount}",
                tint = TextSecondary,
                onClick = { onOpen?.invoke() },
                iconSize = 15.dp
            )
            Box(Modifier.weight(1f))
            Icon(Icons.Outlined.BookmarkBorder, contentDescription = "Save", tint = TextSecondary, modifier = Modifier.size(15.dp))
        }

        if (showComments && post.recentComments.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(openOnTap)
                    .padding(top = 0.dp)
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
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = com.vastutalks.app.ui.theme.TextPrimary)) {
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
                        color = com.vastutalks.app.ui.theme.TextPrimary,
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
private fun PostAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit, iconSize: androidx.compose.ui.unit.Dp = 16.dp) {
    Row(
        modifier = Modifier.clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        Text(" $label", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

internal fun timeAgo(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
