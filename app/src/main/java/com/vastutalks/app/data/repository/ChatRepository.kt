package com.vastutalks.app.data.repository

import android.graphics.Bitmap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.vastutalks.app.data.model.Chat
import com.vastutalks.app.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** One-to-one chats between users and experts (chats/{id}/messages/{id}). */
class ChatRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private fun chats() = firestore.collection("chats")

    val myUid: String? get() = auth.currentUser?.uid

    /** The signed-in user's chats, most recent first, live. */
    fun listenMyChats(): Flow<List<Chat>> = callbackFlow {
        val uid = myUid
        if (uid == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        // Sorted here: array-contains plus orderBy would need a composite index.
        val registration = chats().whereArrayContains("participants", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(Chat::class.java)?.copy(id = it.id) } ?: emptyList()
                trySend(list.sortedByDescending { it.lastMessageAtMillis })
            }
        awaitClose { registration.remove() }
    }

    fun listenMessages(chatId: String, limit: Long = 200): Flow<List<ChatMessage>> = callbackFlow {
        val registration = chats().document(chatId).collection("messages")
            .orderBy("createdAtMillis", Query.Direction.ASCENDING)
            .limitToLast(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toObject(ChatMessage::class.java) } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    /**
     * Sends a message to [otherUid], creating the chat on the first one.
     * [me] / [other] are (name, photo) pairs; [other]'s photo may be blank
     * when it isn't known (users' profiles are private).
     */
    suspend fun send(
        otherUid: String,
        me: Pair<String, String>,
        other: Pair<String, String>,
        text: String,
        image: Bitmap?
    ): Result<Unit> {
        val uid = myUid ?: return Result.failure(IllegalStateException("No signed-in user"))
        return try {
            val encoded = image?.let { withContext(Dispatchers.Default) { ProfilePhotos.encodePostImage(it) } }.orEmpty()
            val chatId = chatIdFor(uid, otherUid)
            val chatRef = chats().document(chatId)
            val messageRef = chatRef.collection("messages").document()
            val now = System.currentTimeMillis()
            val names = mutableMapOf(uid to me.first)
            if (other.first.isNotBlank()) names[otherUid] = other.first
            val photos = mutableMapOf(uid to me.second)
            if (other.second.isNotBlank()) photos[otherUid] = other.second

            firestore.batch().apply {
                set(
                    chatRef,
                    mapOf(
                        "participants" to listOf(uid, otherUid).sorted(),
                        "names" to names,
                        "photos" to photos,
                        "lastMessage" to text.trim().ifBlank { "📷 Photo" },
                        "lastSenderUid" to uid,
                        "lastMessageAtMillis" to now
                    ),
                    SetOptions.merge()
                )
                set(messageRef, ChatMessage(messageRef.id, uid, text.trim(), encoded, now))
            }.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Clears the signed-in user's unread count for [chatId] (when they open it). */
    suspend fun markRead(chatId: String) {
        val uid = myUid ?: return
        runCatching {
            chats().document(chatId).update(mapOf("unread.$uid" to 0L)).await()
        }
    }

    companion object {
        /** The one chat between two people: their uids, sorted, joined with "_". */
        fun chatIdFor(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

        /** The chat open on screen right now, so its new messages don't also pop up as notifications. */
        @Volatile var openChatId: String? = null
    }
}
