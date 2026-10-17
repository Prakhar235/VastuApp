import FirebaseAuth
import FirebaseFirestore
import UIKit

// MARK: - Photos

/// Profile photos (and post / chat pictures) are stored as small JPEGs
/// right on the Firestore documents (users/{uid}.photo, experts/{uid}.photo…)
/// instead of in Firebase Storage, which isn't set up for this project —
/// same format as the Android app, so both read each other's pictures.
enum ProfilePhotos {
    private static let size: CGFloat = 384
    private static let postImageSize: CGFloat = 960
    private static let cache = NSCache<NSString, UIImage>()

    /// Cropped square and kept to ~384 px, so each one is roughly 20–40 KB.
    static func encode(_ image: UIImage) -> String {
        let source = image.scaledDown(toMaxSide: 1024) // also bakes in the orientation
        let side = min(source.size.width, source.size.height)
        let target = min(side, size)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let square = UIGraphicsImageRenderer(size: CGSize(width: target, height: target), format: format).image { _ in
            let scale = target / side
            let drawSize = CGSize(width: source.size.width * scale, height: source.size.height * scale)
            source.draw(in: CGRect(x: (target - drawSize.width) / 2, y: (target - drawSize.height) / 2,
                                   width: drawSize.width, height: drawSize.height))
        }
        return (square.jpegData(compressionQuality: 0.82) ?? Data()).base64EncodedString()
    }

    /// A post's or chat's picture: kept at its own shape, at most 960 px on the long side, ~60–150 KB.
    static func encodePostImage(_ image: UIImage) -> String {
        (image.scaledDown(toMaxSide: postImageSize).jpegData(compressionQuality: 0.75) ?? Data()).base64EncodedString()
    }

    /// The picture for a stored photo, or nil if there isn't one. Decoded images are cached.
    static func decode(_ photo: String) -> UIImage? {
        guard !photo.isEmpty else { return nil }
        let key = "\(photo.count):\(photo.prefix(64)):\(photo.suffix(64))" as NSString
        if let cached = cache.object(forKey: key) { return cached }
        guard let data = Data(base64Encoded: photo, options: .ignoreUnknownCharacters),
              let image = UIImage(data: data) else { return nil }
        cache.setObject(image, forKey: key)
        return image
    }
}

// MARK: - Chats

/// One-to-one chats between users and experts (chats/{id}/messages/{id}).
final class ChatRepository {
    private var chats: CollectionReference { Firestore.firestore().collection("chats") }

    var myUid: String? { Auth.auth().currentUser?.uid }

    /// The one chat between two people: their uids, sorted, joined with "_".
    static func chatId(_ a: String, _ b: String) -> String { [a, b].sorted().joined(separator: "_") }

    /// The chat open on screen right now, so its new messages don't also pop up as notifications.
    static var openChatId: String?

    /// The signed-in user's chats, most recent first, live.
    func listenMyChats(_ onChange: @escaping ([Chat]) -> Void) -> ListenerRegistration? {
        guard let uid = myUid else {
            onChange([])
            return nil
        }
        // Sorted here: array-contains plus orderBy would need a composite index.
        return chats.whereField("participants", arrayContains: uid).addSnapshotListener { snapshot, _ in
            let list = snapshot?.documents.map { Chat(id: $0.documentID, data: $0.data()) } ?? []
            onChange(list.sorted { $0.lastMessageAtMillis > $1.lastMessageAtMillis })
        }
    }

    func listenMessages(chatId: String, limit: Int = 200, _ onChange: @escaping ([ChatMessage]) -> Void) -> ListenerRegistration {
        chats.document(chatId).collection("messages")
            .order(by: "createdAtMillis")
            .limit(toLast: limit)
            .addSnapshotListener { snapshot, _ in
                onChange(snapshot?.documents.map { ChatMessage(data: $0.data()) } ?? [])
            }
    }

    /// Sends a message to `otherUid`, creating the chat on the first one.
    /// `me` / `other` are (name, photo) pairs; the other person's photo may
    /// be blank when it isn't known (users' profiles are private).
    func send(otherUid: String, me: (name: String, photo: String), other: (name: String, photo: String),
              text: String, image: UIImage?) async throws {
        guard let uid = myUid else { throw AppError("No signed-in user") }
        let encoded = image.map(ProfilePhotos.encodePostImage) ?? ""
        let chatRef = chats.document(Self.chatId(uid, otherUid))
        let messageRef = chatRef.collection("messages").document()
        let now = millisNow()
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        var names = [uid: me.name]
        if !other.name.isEmpty { names[otherUid] = other.name }
        var photos = [uid: me.photo]
        if !other.photo.isEmpty { photos[otherUid] = other.photo }

        let batch = Firestore.firestore().batch()
        batch.setData([
            "participants": [uid, otherUid].sorted(),
            "names": names,
            "photos": photos,
            "lastMessage": trimmed.isEmpty ? "📷 Photo" : trimmed,
            "lastSenderUid": uid,
            "lastMessageAtMillis": now
        ], forDocument: chatRef, merge: true)
        batch.setData(ChatMessage(id: messageRef.documentID, senderUid: uid, text: trimmed, image: encoded,
                                  createdAtMillis: now).dictionary, forDocument: messageRef)
        try await batch.commit()
    }

    /// Clears the signed-in user's unread count for `chatId` (when they open it).
    func markRead(chatId: String) async {
        guard let uid = myUid else { return }
        try? await chats.document(chatId).updateData(["unread.\(uid)": 0])
    }
}

// MARK: - Posts

/// Posts written by Vastu experts (posts/{id}).
final class PostRepository {
    private var db: Firestore { Firestore.firestore() }
    private var posts: CollectionReference { db.collection("posts") }

    /// The newest `limit` posts from all experts, live.
    func listenFeed(limit: Int = 50, _ onChange: @escaping ([Post]) -> Void) -> ListenerRegistration {
        posts.order(by: "createdAtMillis", descending: true).limit(to: limit).addSnapshotListener { snapshot, _ in
            onChange(snapshot?.documents.map { Post(data: $0.data()) } ?? [])
        }
    }

    /// One expert's own posts, newest first. Sorted here rather than with
    /// orderBy, which with the authorUid filter would need a composite index.
    func listenByAuthor(_ uid: String, _ onChange: @escaping ([Post]) -> Void) -> ListenerRegistration {
        posts.whereField("authorUid", isEqualTo: uid).addSnapshotListener { snapshot, _ in
            let list = snapshot?.documents.map { Post(data: $0.data()) } ?? []
            onChange(list.sorted { $0.createdAtMillis > $1.createdAtMillis })
        }
    }

    /// Publishes a post as the signed-in expert (`author` is their directory entry).
    func create(author: ExpertProfile, text: String, image: UIImage?) async throws {
        guard let uid = Auth.auth().currentUser?.uid else { throw AppError("No signed-in user") }
        let encoded = image.map(ProfilePhotos.encodePostImage) ?? ""
        let doc = posts.document()
        try await doc.setData([
            "id": doc.documentID,
            "authorUid": uid,
            "authorName": author.name,
            "authorPhoto": author.photo,
            "authorSpecialty": author.displaySpecialty,
            "text": text.trimmingCharacters(in: .whitespacesAndNewlines),
            "image": encoded,
            "createdAtMillis": millisNow(),
            "likeCount": 0,
            "commentCount": 0,
            "recentComments": [Any]()
        ])
    }

    /// One post, live (its like/comment counts change as people react). Gives nil once it's deleted.
    func listenPost(_ postId: String, _ onChange: @escaping (Post?) -> Void) -> ListenerRegistration {
        posts.document(postId).addSnapshotListener { snapshot, error in
            onChange(error == nil ? snapshot?.data().map(Post.init(data:)) : nil)
        }
    }

    func delete(_ postId: String) async throws {
        try await posts.document(postId).delete()
    }

    // Likes: users/{uid}/likes/{postId}; the post's likeCount is kept by Cloud Functions.

    private func myLikes() -> CollectionReference? {
        Auth.auth().currentUser.map { db.collection("users").document($0.uid).collection("likes") }
    }

    /// Ids of the posts the signed-in user has liked, live.
    func listenMyLikes(_ onChange: @escaping (Set<String>) -> Void) -> ListenerRegistration? {
        guard let likes = myLikes() else {
            onChange([])
            return nil
        }
        return likes.addSnapshotListener { snapshot, _ in
            onChange(Set(snapshot?.documents.map(\.documentID) ?? []))
        }
    }

    func setLiked(_ postId: String, _ liked: Bool) async throws {
        guard let likes = myLikes() else { throw AppError("No signed-in user") }
        let doc = likes.document(postId)
        if liked {
            try await doc.setData(["postId": postId, "createdAtMillis": millisNow()])
        } else {
            try await doc.delete()
        }
    }

    // Comments: posts/{postId}/comments/{id}; commentCount and notifications come from Cloud Functions.

    private func comments(_ postId: String) -> CollectionReference { posts.document(postId).collection("comments") }

    /// A post's comments and replies, oldest first.
    func listenComments(_ postId: String, _ onChange: @escaping ([PostComment]) -> Void) -> ListenerRegistration {
        comments(postId).order(by: "createdAtMillis").addSnapshotListener { snapshot, _ in
            onChange(snapshot?.documents.map { PostComment(data: $0.data()) } ?? [])
        }
    }

    /// Adds a comment as `me`, or a reply when `replyTo` is set.
    func addComment(postId: String, me: UserProfile, text: String, replyTo: PostComment?) async throws {
        let doc = comments(postId).document()
        // Replies stay one level deep: replying to a reply joins its thread.
        let thread = replyTo.map { $0.replyToCommentId.isEmpty ? $0.id : $0.replyToCommentId } ?? ""
        try await doc.setData([
            "id": doc.documentID,
            "postId": postId,
            "authorUid": me.uid,
            "authorName": me.name,
            "authorPhoto": me.photo,
            "authorIsExpert": me.role == UserRole.vastuExpert.rawValue,
            "text": text.trimmingCharacters(in: .whitespacesAndNewlines),
            "replyToCommentId": thread,
            "replyToUid": replyTo?.authorUid ?? "",
            "replyToName": replyTo?.authorName ?? "",
            "createdAtMillis": millisNow()
        ])
    }

    func deleteComment(postId: String, commentId: String) async throws {
        try await comments(postId).document(commentId).delete()
    }
}

// MARK: - Notifications

/// The signed-in user's notifications (users/{uid}/notifications), written by Cloud Functions.
final class NotificationRepository {
    private func collection() -> CollectionReference? {
        Auth.auth().currentUser.map {
            Firestore.firestore().collection("users").document($0.uid).collection("notifications")
        }
    }

    /// Newest first, live.
    func listen(limit: Int = 100, _ onChange: @escaping ([AppNotification]) -> Void) -> ListenerRegistration? {
        guard let notifications = collection() else {
            onChange([])
            return nil
        }
        return notifications.order(by: "createdAtMillis", descending: true).limit(to: limit)
            .addSnapshotListener { snapshot, _ in
                onChange(snapshot?.documents.map { AppNotification(id: $0.documentID, data: $0.data()) } ?? [])
            }
    }

    /// Marks `ids` as read (when the list is opened).
    func markRead(_ ids: [String]) async {
        guard let notifications = collection(), !ids.isEmpty else { return }
        let batch = Firestore.firestore().batch()
        ids.forEach { batch.updateData(["read": true], forDocument: notifications.document($0)) }
        try? await batch.commit()
    }
}
