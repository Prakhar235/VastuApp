import Foundation

/// A one-to-one conversation between a user and an expert, stored at
/// chats/{id} where id is the two uids sorted and joined with "_" (see
/// ChatRepository.chatId), so each pair has exactly one chat.
///
/// Each side writes its own name and photo when it sends a message; the
/// last message is written alongside it. `unread` (uid → count) is kept
/// by the onChatMessageCreated Cloud Function, and each side resets its
/// own count when it opens the chat.
struct Chat: Identifiable, Equatable {
    var id = ""
    var participants: [String] = []
    var names: [String: String] = [:]
    /// Base64 JPEGs, as on profiles (see ProfilePhotos).
    var photos: [String: String] = [:]
    var lastMessage = ""
    var lastSenderUid = ""
    var lastMessageAtMillis: Int64 = 0
    var unread: [String: Int64] = [:]

    init(id: String, data: [String: Any]) {
        self.id = id
        participants = data["participants"] as? [String] ?? []
        names = data["names"] as? [String: String] ?? [:]
        photos = data["photos"] as? [String: String] ?? [:]
        lastMessage = data["lastMessage"] as? String ?? ""
        lastSenderUid = data["lastSenderUid"] as? String ?? ""
        lastMessageAtMillis = int64(data["lastMessageAtMillis"])
        unread = (data["unread"] as? [String: Any] ?? [:]).mapValues { int64($0) }
    }

    func otherUid(me: String) -> String { participants.first { $0 != me } ?? "" }
}

/// Stored at chats/{chatId}/messages/{id}.
struct ChatMessage: Identifiable, Equatable {
    var id = ""
    var senderUid = ""
    var text = ""
    /// Optional photo, base64 JPEG (see ProfilePhotos.encodePostImage); blank if none.
    var image = ""
    var createdAtMillis: Int64 = 0

    init(id: String, senderUid: String, text: String, image: String, createdAtMillis: Int64) {
        self.id = id
        self.senderUid = senderUid
        self.text = text
        self.image = image
        self.createdAtMillis = createdAtMillis
    }

    init(data: [String: Any]) {
        id = data["id"] as? String ?? ""
        senderUid = data["senderUid"] as? String ?? ""
        text = data["text"] as? String ?? ""
        image = data["image"] as? String ?? ""
        createdAtMillis = int64(data["createdAtMillis"])
    }

    var dictionary: [String: Any] {
        ["id": id, "senderUid": senderUid, "text": text, "image": image, "createdAtMillis": createdAtMillis]
    }
}

/// Stored at posts/{id}: a tip or update written by a Vastu expert and
/// shown in every user's Posts tab. The author's name, photo and
/// specialty are copied in when posting, so the feed doesn't need to
/// look up each expert.
struct Post: Identifiable, Equatable {
    var id = ""
    var authorUid = ""
    var authorName = ""
    /// Base64 JPEG, as on the expert's profile (see ProfilePhotos); blank if none.
    var authorPhoto = ""
    var authorSpecialty = ""
    var text = ""
    /// Optional picture, base64 JPEG (see ProfilePhotos.encodePostImage); blank if none.
    var image = ""
    var createdAtMillis: Int64 = 0
    /// Kept up to date by Cloud Functions (onPostLiked / onPostUnliked); clients never write it.
    var likeCount: Int64 = 0
    /// Kept up to date by Cloud Functions (onCommentCreated / onCommentDeleted).
    var commentCount: Int64 = 0
    /// The newest few comments, oldest first, copied in by Cloud Functions so feeds can show them.
    var recentComments: [CommentPreview] = []

    init(data: [String: Any]) {
        id = data["id"] as? String ?? ""
        authorUid = data["authorUid"] as? String ?? ""
        authorName = data["authorName"] as? String ?? ""
        authorPhoto = data["authorPhoto"] as? String ?? ""
        authorSpecialty = data["authorSpecialty"] as? String ?? ""
        text = data["text"] as? String ?? ""
        image = data["image"] as? String ?? ""
        createdAtMillis = int64(data["createdAtMillis"])
        likeCount = int64(data["likeCount"])
        commentCount = int64(data["commentCount"])
        recentComments = (data["recentComments"] as? [[String: Any]] ?? []).map(CommentPreview.init(data:))
    }
}

/// A comment as shown under a post in the feed (see Post.recentComments).
struct CommentPreview: Identifiable, Equatable {
    var id = ""
    var authorName = ""
    var authorIsExpert = false
    var text = ""
    var replyToName = ""
    var createdAtMillis: Int64 = 0

    init(data: [String: Any]) {
        id = data["id"] as? String ?? ""
        authorName = data["authorName"] as? String ?? ""
        authorIsExpert = data["authorIsExpert"] as? Bool ?? false
        text = data["text"] as? String ?? ""
        replyToName = data["replyToName"] as? String ?? ""
        createdAtMillis = int64(data["createdAtMillis"])
    }
}

/// Stored at posts/{postId}/comments/{id}. Replies are one level deep:
/// a reply's replyToCommentId is the top-level comment of its thread,
/// and replyToUid/replyToName say whose comment in that thread was
/// answered (they get a notification when an expert replies).
struct PostComment: Identifiable, Equatable {
    var id = ""
    var postId = ""
    var authorUid = ""
    var authorName = ""
    var authorPhoto = ""
    var authorIsExpert = false
    var text = ""
    var replyToCommentId = ""
    var replyToUid = ""
    var replyToName = ""
    var createdAtMillis: Int64 = 0

    init(data: [String: Any]) {
        id = data["id"] as? String ?? ""
        postId = data["postId"] as? String ?? ""
        authorUid = data["authorUid"] as? String ?? ""
        authorName = data["authorName"] as? String ?? ""
        authorPhoto = data["authorPhoto"] as? String ?? ""
        authorIsExpert = data["authorIsExpert"] as? Bool ?? false
        text = data["text"] as? String ?? ""
        replyToCommentId = data["replyToCommentId"] as? String ?? ""
        replyToUid = data["replyToUid"] as? String ?? ""
        replyToName = data["replyToName"] as? String ?? ""
        createdAtMillis = int64(data["createdAtMillis"])
    }
}

/// Stored at users/{uid}/notifications/{id}, written by Cloud Functions
/// when someone likes or comments on your post, or an expert replies to
/// your comment. `type` is "like", "comment" or "reply".
struct AppNotification: Identifiable, Equatable {
    var id = ""
    var type = ""
    var postId = ""
    var actorName = ""
    var actorPhoto = ""
    var title = ""
    var body = ""
    var createdAtMillis: Int64 = 0
    var read = false

    init(id: String, data: [String: Any]) {
        self.id = id
        type = data["type"] as? String ?? ""
        postId = data["postId"] as? String ?? ""
        actorName = data["actorName"] as? String ?? ""
        actorPhoto = data["actorPhoto"] as? String ?? ""
        title = data["title"] as? String ?? ""
        body = data["body"] as? String ?? ""
        createdAtMillis = int64(data["createdAtMillis"])
        read = data["read"] as? Bool ?? false
    }
}
