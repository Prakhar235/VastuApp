package com.vastutalks.app.data.model

/**
 * A one-to-one conversation between a user and an expert, stored at
 * chats/{id} where id is the two uids sorted and joined with "_" (see
 * ChatRepository.chatIdFor), so each pair has exactly one chat.
 *
 * Each side writes its own name and photo when it sends a message; the
 * last message is written alongside it. [unread] (uid -> count) is kept
 * by the onChatMessageCreated Cloud Function, and each side resets its
 * own count when it opens the chat.
 */
data class Chat(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    /** Base64 JPEGs, as on profiles (see ProfilePhotos). */
    val photos: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastSenderUid: String = "",
    val lastMessageAtMillis: Long = 0L,
    val unread: Map<String, Long> = emptyMap()
) {
    fun otherUid(me: String): String = participants.firstOrNull { it != me }.orEmpty()
}

/** Stored at chats/{chatId}/messages/{id}. */
data class ChatMessage(
    val id: String = "",
    val senderUid: String = "",
    val text: String = "",
    /** Optional photo, base64 JPEG (see ProfilePhotos.encodePostImage); blank if none. */
    val image: String = "",
    val createdAtMillis: Long = 0L
)
