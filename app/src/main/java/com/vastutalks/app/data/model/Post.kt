package com.vastutalks.app.data.model

/**
 * Stored at posts/{id}: a tip or update written by a Vastu expert and
 * shown in every user's Posts tab. The author's name, photo and
 * specialty are copied in when posting, so the feed doesn't need to
 * look up each expert.
 */
data class Post(
    val id: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    /** Base64 JPEG, as on the expert's profile (see ProfilePhotos); blank if none. */
    val authorPhoto: String = "",
    val authorSpecialty: String = "",
    val text: String = "",
    /** Optional picture, base64 JPEG (see ProfilePhotos.encodePostImage); blank if none. */
    val image: String = "",
    val createdAtMillis: Long = 0L,
    /** Kept up to date by Cloud Functions (onPostLiked / onPostUnliked); clients never write it. */
    val likeCount: Long = 0L,
    /** Kept up to date by Cloud Functions (onCommentCreated / onCommentDeleted). */
    val commentCount: Long = 0L,
    /** The newest few comments, oldest first, copied in by Cloud Functions so feeds can show them. */
    val recentComments: List<CommentPreview> = emptyList()
)

/** A comment as shown under a post in the feed (see Post.recentComments). */
data class CommentPreview(
    val id: String = "",
    val authorName: String = "",
    val authorIsExpert: Boolean = false,
    val text: String = "",
    val replyToName: String = "",
    val createdAtMillis: Long = 0L
)

/**
 * Stored at posts/{postId}/comments/{id}. Replies are one level deep:
 * a reply's [replyToCommentId] is the top-level comment of its thread,
 * and [replyToUid]/[replyToName] say whose comment in that thread was
 * answered (they get a notification when an expert replies).
 */
data class PostComment(
    val id: String = "",
    val postId: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorPhoto: String = "",
    val authorIsExpert: Boolean = false,
    val text: String = "",
    val replyToCommentId: String = "",
    val replyToUid: String = "",
    val replyToName: String = "",
    val createdAtMillis: Long = 0L
)

/**
 * Stored at users/{uid}/notifications/{id}, written by Cloud Functions
 * when someone likes or comments on your post, or an expert replies to
 * your comment. [type] is "like", "comment" or "reply".
 */
data class AppNotification(
    val id: String = "",
    val type: String = "",
    val postId: String = "",
    val actorName: String = "",
    val actorPhoto: String = "",
    val title: String = "",
    val body: String = "",
    val createdAtMillis: Long = 0L,
    val read: Boolean = false
)
