package com.vastutalks.app.data.repository

import android.graphics.Bitmap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.vastutalks.app.data.model.ExpertProfile
import com.vastutalks.app.data.model.Post
import com.vastutalks.app.data.model.PostComment
import com.vastutalks.app.data.model.UserProfile
import com.vastutalks.app.data.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Posts written by Vastu experts (posts/{id}). */
class PostRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private fun collection() = firestore.collection("posts")

    /** The newest [limit] posts from all experts, live. */
    fun listenFeed(limit: Long = 50): Flow<List<Post>> = callbackFlow {
        val registration = collection()
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toObject(Post::class.java) } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    /**
     * One expert's own posts, newest first. Sorted here rather than with
     * orderBy, which together with the authorUid filter would need a
     * composite index set up in Firestore.
     */
    fun listenByAuthor(uid: String): Flow<List<Post>> = callbackFlow {
        val registration = collection()
            .whereEqualTo("authorUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val posts = snapshot?.documents?.mapNotNull { it.toObject(Post::class.java) } ?: emptyList()
                trySend(posts.sortedByDescending { it.createdAtMillis })
            }
        awaitClose { registration.remove() }
    }

    /** Publishes a post as the signed-in expert ([author] is their directory entry). */
    suspend fun create(author: ExpertProfile, text: String, image: Bitmap?): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("No signed-in user"))
        return try {
            val encoded = image?.let { withContext(Dispatchers.Default) { ProfilePhotos.encodePostImage(it) } }.orEmpty()
            val doc = collection().document()
            doc.set(
                Post(
                    id = doc.id,
                    authorUid = uid,
                    authorName = author.name,
                    authorPhoto = author.photo,
                    authorSpecialty = author.displaySpecialty,
                    text = text.trim(),
                    image = encoded,
                    createdAtMillis = System.currentTimeMillis()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** One post, live (its like/comment counts change as people react). Emits null once it's deleted. */
    fun listenPost(postId: String): Flow<Post?> = callbackFlow {
        val registration = collection().document(postId)
            .addSnapshotListener { snapshot, _ -> trySend(snapshot?.toObject(Post::class.java)) }
        awaitClose { registration.remove() }
    }

    // --- Likes: users/{uid}/likes/{postId}; the post's likeCount is kept by Cloud Functions.

    private fun myLikes() = auth.currentUser?.uid?.let { firestore.collection("users").document(it).collection("likes") }

    /** Ids of the posts the signed-in user has liked, live. */
    fun listenMyLikes(): Flow<Set<String>> = callbackFlow {
        val likes = myLikes()
        if (likes == null) {
            trySend(emptySet())
            awaitClose { }
            return@callbackFlow
        }
        val registration = likes.addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.documents?.map { it.id }?.toSet() ?: emptySet())
        }
        awaitClose { registration.remove() }
    }

    suspend fun setLiked(postId: String, liked: Boolean): Result<Unit> {
        val likes = myLikes() ?: return Result.failure(IllegalStateException("No signed-in user"))
        return try {
            val doc = likes.document(postId)
            if (liked) doc.set(mapOf("postId" to postId, "createdAtMillis" to System.currentTimeMillis())).await()
            else doc.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Comments: posts/{postId}/comments/{id}; commentCount and notifications come from Cloud Functions.

    private fun comments(postId: String) = collection().document(postId).collection("comments")

    /** A post's comments and replies, oldest first. */
    fun listenComments(postId: String): Flow<List<PostComment>> = callbackFlow {
        val registration = comments(postId)
            .orderBy("createdAtMillis", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toObject(PostComment::class.java) } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    /** Adds a comment as [me], or a reply when [replyTo] is set. */
    suspend fun addComment(postId: String, me: UserProfile, text: String, replyTo: PostComment?): Result<Unit> {
        return try {
            val doc = comments(postId).document()
            doc.set(
                PostComment(
                    id = doc.id,
                    postId = postId,
                    authorUid = me.uid,
                    authorName = me.name,
                    authorPhoto = me.photo,
                    authorIsExpert = me.role == UserRole.VASTU_EXPERT.name,
                    text = text.trim(),
                    // Replies stay one level deep: replying to a reply joins its thread.
                    replyToCommentId = replyTo?.let { it.replyToCommentId.ifBlank { it.id } }.orEmpty(),
                    replyToUid = replyTo?.authorUid.orEmpty(),
                    replyToName = replyTo?.authorName.orEmpty(),
                    createdAtMillis = System.currentTimeMillis()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteComment(postId: String, commentId: String): Result<Unit> {
        return try {
            comments(postId).document(commentId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun delete(postId: String): Result<Unit> {
        return try {
            collection().document(postId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
